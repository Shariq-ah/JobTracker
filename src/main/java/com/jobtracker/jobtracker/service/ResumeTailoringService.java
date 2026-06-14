package com.jobtracker.jobtracker.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.jobtracker.jobtracker.config.CandidateProfile;
import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.model.TailoredResume;
import com.jobtracker.jobtracker.repository.TailoredResumeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for AI-powered resume tailoring using AWS Bedrock (Claude).
 * Optimized for minimal token usage by sending only content, not full LaTeX template.
 */
@Service
@Slf4j
public class ResumeTailoringService {

    private static final String PROMPT_VERSION = "3.0-optimized";
    private static final int MAX_RETRIES = 2;

    private final BedrockRuntimeClient bedrockClient;
    private final LaTeXCompilerService latexCompiler;
    private final TailoredResumeRepository repository;
    private final CandidateProfile candidateProfile;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Value("${aws.bedrock.model.id}")
    private String modelId;

    @Value("${aws.bedrock.max.tokens:3000}")
    private int maxTokens;

    @Value("${aws.bedrock.temperature:0.0}")
    private double temperature;

    @Value("${ai.dev.mock.enabled:false}")
    private boolean devMockEnabled;

    public ResumeTailoringService(
            BedrockRuntimeClient bedrockClient,
            LaTeXCompilerService latexCompiler,
            TailoredResumeRepository repository,
            CandidateProfile candidateProfile,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper) {
        this.bedrockClient = bedrockClient;
        this.latexCompiler = latexCompiler;
        this.repository = repository;
        this.candidateProfile = candidateProfile;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * Tailors resume for a specific job using AI.
     * Cost-optimized: sends only content, not full LaTeX template.
     *
     * @param job Job to tailor resume for
     * @return TailoredResume with PDF and ATS score
     */
    public TailoredResume tailorResume(Job job) {
        try {
            log.info("Tailoring resume for: {} at {}", job.getTitle(), job.getCompany());

            // 1. Load base LaTeX template
            String baseLatex = loadBaseLatexTemplate();

            // 2. Call Claude to get tailored content + ATS score
            JsonNode tailoredContent = callClaudeForTailoring(job);

            // 3. Inject tailored content into LaTeX template
            String tailoredLatex = injectContentIntoTemplate(baseLatex, tailoredContent);

            // 4. Compile to PDF
            byte[] pdfBytes = latexCompiler.compileToPDF(tailoredLatex);

            // 5. Save to database
            TailoredResume resume = new TailoredResume();
            resume.setId(job.getId() + "_resume");
            resume.setJobId(job.getId());
            resume.setCompanyName(job.getCompany());
            resume.setJobTitle(job.getTitle());
            resume.setLatexSource(tailoredLatex);
            resume.setPdfBytes(pdfBytes);
            resume.setTailoredAt(LocalDateTime.now());
            resume.setSentToUser(false);
            resume.setApplied(false);
            resume.setAtsScore(tailoredContent.path("atsScore").asInt(0));
            resume.setAtsReasoning(tailoredContent.path("atsReasoning").asText(""));
            resume.setClaudePromptVersion(PROMPT_VERSION);
            resume.setLatexCompilationAttempts(1);

            TailoredResume saved = repository.save(resume);
            log.info("Tailored resume saved with ATS score: {}", saved.getAtsScore());

            return saved;

        } catch (Exception e) {
            log.error("Failed to tailor resume for {}: {}", job.getTitle(), e.getMessage());
            throw new RuntimeException("Resume tailoring failed", e);
        }
    }

    /**
     * Calls Claude API to get tailored resume content.
     * Optimized prompt sends only content, not full LaTeX.
     */
    private JsonNode callClaudeForTailoring(Job job) {
        // DEV MODE: Return mock response to avoid API costs
        if (devMockEnabled) {
            log.info("🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)");
            return getMockTailoringResponse(job);
        }

        // PRODUCTION: Call real Claude API
        String prompt = buildOptimizedPrompt(job);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("anthropic_version", "bedrock-2023-05-31");
                requestBody.put("max_tokens", maxTokens);
                requestBody.put("temperature", temperature);
                requestBody.put("messages", new Object[]{
                        Map.of("role", "user", "content", prompt)
                });

                String requestBodyJson = objectMapper.writeValueAsString(requestBody);

                InvokeModelRequest request = InvokeModelRequest.builder()
                        .modelId(modelId)
                        .body(SdkBytes.fromString(requestBodyJson, StandardCharsets.UTF_8))
                        .build();

                InvokeModelResponse response = bedrockClient.invokeModel(request);
                String responseBody = response.body().asUtf8String();

                JsonNode root = objectMapper.readTree(responseBody);
                String contentText = root.path("content").get(0).path("text").asText();

                // Strip markdown code fences if present (Claude sometimes wraps JSON in ```json blocks)
                String cleanedJson = contentText
                        .replaceAll("^```json\\s*", "")   // Remove opening ```json
                        .replaceAll("^```\\s*", "")        // Remove opening ```
                        .replaceAll("\\s*```$", "")        // Remove closing ```
                        .trim();

                // Parse JSON from cleaned response
                JsonNode tailoredContent = objectMapper.readTree(cleanedJson);

                log.info("Claude tailoring successful (attempt {})", attempt);
                return tailoredContent;

            } catch (Exception e) {
                log.warn("Claude tailoring attempt {} failed: {}", attempt, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("All tailoring attempts failed", e);
                }
                try {
                    Thread.sleep(2000 * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new RuntimeException("Tailoring failed after retries");
    }

    /**
     * Builds optimized prompt that sends only content (not full LaTeX).
     * Target: <1000 input tokens
     */
    private String buildOptimizedPrompt(Job job) {
        // Extract current resume content (not LaTeX formatting)
        String currentSkills = String.join(", ", candidateProfile.getSkills());

        return String.format("""
            You are an expert ATS resume optimizer. Tailor this resume content for the job below.

            CURRENT RESUME:
            Name: %s
            Current Title: Software Engineer III | Java | Spring Boot | Microservices | Kafka

            Skills: %s

            Experience:
            1. "Architected distributed billing microservices handling 500K+ transactions daily with P99 latency of 120ms"
            2. "Reduced transaction processing latency by 40%% (2.5s → 1.5s P99) by implementing Redis caching"
            3. "Designed event-driven Kafka pipeline processing 10K QPS with guaranteed delivery"
            4. "Designed RESTful APIs serving 100M+ users with 99.95%% uptime and sub-200ms P95 latency"

            Projects:
            - "JobTracker - Automated job aggregation system integrating 10+ company APIs"

            TARGET JOB:
            Company: %s
            Title: %s
            Level: %s
            Required Skills: %s
            Matched Skills: %s
            Missing Skills: %s
            Responsibilities: %s

            INSTRUCTIONS:
            1. Reorder/rephrase experience bullets to emphasize matched skills
            2. Add keywords from required skills naturally
            3. Adjust title line to match job level
            4. Keep content quantifiable and achievement-focused
            5. Maintain 1-page constraint (no length increase)
            6. Calculate ATS score (0-100) based on keyword density, relevance, quantification

            OUTPUT (JSON only):
            {
              "titleLine": "Software Engineer | Java | Spring Boot | ...",
              "experienceBullets": ["tailored bullet 1", "tailored bullet 2", ...],
              "projectBullets": ["tailored project description"],
              "atsScore": 92,
              "atsReasoning": "Strong match on Java, Spring Boot... Missing Docker keywords."
            }
            """,
                candidateProfile.getName(),
                currentSkills,
                job.getCompany(),
                job.getTitle(),
                job.getJobLevel() != null ? job.getJobLevel() : "Not specified",
                job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "",
                job.getMatchedSkills() != null ? String.join(", ", job.getMatchedSkills()) : "",
                job.getMissingSkills() != null ? String.join(", ", job.getMissingSkills()) : "",
                job.getResponsibilities() != null ? truncate(String.join(" ", job.getResponsibilities()), 300) : ""
        );
    }

    /**
     * Injects tailored content into base LaTeX template.
     * Replaces placeholders with AI-generated content.
     */
    private String injectContentIntoTemplate(String baseLatex, JsonNode tailoredContent) {
        String tailoredLatex = baseLatex;

        // Replace title line
        String titleLine = tailoredContent.path("titleLine").asText("");
        if (!titleLine.isEmpty()) {
            tailoredLatex = tailoredLatex.replaceFirst(
                    "Software Engineer III \\| Java \\| Spring Boot \\| Microservices \\| Kafka \\| AWS \\| Distributed Systems \\| Payment & Billing",
                    titleLine
            );
        }

        // Replace experience bullets (if provided)
        JsonNode experienceBullets = tailoredContent.path("experienceBullets");
        if (experienceBullets.isArray() && experienceBullets.size() > 0) {
            // Replace first 4 bullets with tailored versions
            for (int i = 0; i < Math.min(4, experienceBullets.size()); i++) {
                String originalPattern = "\\\\resumeItem\\{.*?\\}";
                String replacement = "\\\\resumeItem{" + experienceBullets.get(i).asText() + "}";
                tailoredLatex = tailoredLatex.replaceFirst(originalPattern, replacement);
            }
        }

        return tailoredLatex;
    }

    /**
     * Loads base LaTeX template from resources.
     */
    private String loadBaseLatexTemplate() {
        try {
            Resource resource = resourceLoader.getResource("classpath:resume/base_resume.tex");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to load base LaTeX template: {}", e.getMessage());
            throw new RuntimeException("Could not load base resume template", e);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * Returns mock tailoring response for dev testing (zero cost).
     * Simulates realistic Claude output with varying ATS scores.
     */
    private JsonNode getMockTailoringResponse(Job job) {
        try {
            // Vary ATS score based on job company hash for realistic testing
            int atsScore = 75 + (Math.abs(job.getCompany().hashCode()) % 20);  // 75-94

            String mockJson = String.format("""
                {
                  "titleLine": "Software Engineer III | Java | Spring Boot | Microservices | Kafka",
                  "experienceBullets": [
                    "Architected distributed billing microservices handling 500K+ transactions daily using Java, Spring Boot, and Kafka with P99 latency of 120ms",
                    "Reduced transaction processing latency by 40%% (2.5s → 1.5s P99) by implementing Redis caching and optimizing SQL queries",
                    "Designed event-driven Kafka pipeline processing 10K QPS with guaranteed delivery and automatic retry mechanisms",
                    "Designed RESTful APIs serving 100M+ users with 99.95%% uptime using Spring Boot and Docker containerization"
                  ],
                  "atsScore": %d,
                  "atsReasoning": "Mock ATS analysis - strong keyword alignment with Java, Spring Boot, Microservices. Quantified achievements present."
                }
                """, atsScore);

            return objectMapper.readTree(mockJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock response", e);
        }
    }
}
