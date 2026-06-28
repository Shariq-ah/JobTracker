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

    private static final String PROMPT_VERSION = "4.0-iterative-85";
    private static final int MAX_ITERATIONS = 4;
    private static final int TARGET_ATS_SCORE = 85;

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
     * Calls Claude API iteratively to achieve target ATS score (85%).
     * Loops up to MAX_ITERATIONS times, stopping early if target reached.
     */
    private JsonNode callClaudeForTailoring(Job job) {
        // DEV MODE: Return mock response to avoid API costs
        if (devMockEnabled) {
            log.info("🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)");
            return getMockTailoringResponse(job);
        }

        // PRODUCTION: Iterative improvement loop
        JsonNode result = null;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            try {
                // Build prompt (initial or improvement)
                String prompt = (iteration == 1)
                    ? buildInitialPrompt(job)
                    : buildImprovementPrompt(job, result, iteration);

                // Call Claude API
                result = invokeClaude(prompt, iteration);

                int atsScore = result.path("atsScore").asInt(0);
                log.info("Iteration {} - ATS Score: {}", iteration, atsScore);

                // Check if target reached
                if (atsScore >= TARGET_ATS_SCORE) {
                    log.info("✅ Target ATS score reached: {} (iterations: {})", atsScore, iteration);
                    return result;
                }

                // Log improvement needed
                log.warn("⚠️ ATS score {} below target ({}). Requesting improvement (iteration {}/{})",
                         atsScore, TARGET_ATS_SCORE, iteration, MAX_ITERATIONS);

            } catch (Exception e) {
                log.error("Iteration {} failed: {}", iteration, e.getMessage());
                if (iteration == MAX_ITERATIONS) {
                    throw new RuntimeException("All tailoring iterations failed", e);
                }
                // Continue to next iteration on error
            }
        }

        // Return best attempt even if < 85%
        int finalScore = result != null ? result.path("atsScore").asInt(0) : 0;
        log.warn("⚠️ Could not reach {}% ATS after {} iterations. Final score: {}",
                 TARGET_ATS_SCORE, MAX_ITERATIONS, finalScore);
        return result;
    }

    /**
     * Invokes Claude API with given prompt.
     * Handles retries with exponential backoff for network errors.
     */
    private JsonNode invokeClaude(String prompt, int iteration) throws Exception {
        int maxRetries = 2;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
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

                // Log response length for debugging truncation
                log.debug("Claude response length: {} chars, iteration: {}", contentText.length(), iteration);
                log.debug("Claude response preview: {}", contentText.substring(0, Math.min(200, contentText.length())));

                // Strip markdown code fences
                String cleaned = contentText
                        .replaceAll("```json", "")
                        .replaceAll("```", "")
                        .replaceAll("`", "")
                        .trim();

                // EXTRACT JSON ONLY: Claude often adds explanatory text after the JSON
                // Find the first '{' and last '}' to extract only the JSON object
                int firstBrace = cleaned.indexOf('{');
                int lastBrace = cleaned.lastIndexOf('}');

                if (firstBrace == -1 || lastBrace == -1 || firstBrace >= lastBrace) {
                    throw new RuntimeException("No valid JSON object found in response");
                }

                // Extract ONLY the JSON object (ignore everything before/after)
                String cleanedJson = cleaned.substring(firstBrace, lastBrace + 1).trim();

                // Debug: Log cleaned length and preview
                log.debug("After cleaning: {} chars (extracted from {} total)", cleanedJson.length(), cleaned.length());
                log.debug("First 200: {}", cleanedJson.substring(0, Math.min(200, cleanedJson.length())));
                if (cleanedJson.length() > 200) {
                    log.debug("Last 100: {}", cleanedJson.substring(Math.max(0, cleanedJson.length() - 100)));
                }

                // PRE-VALIDATION: Verify JSON structure
                if (!cleanedJson.trim().endsWith("}")) {
                    String ending = cleanedJson.substring(Math.max(0, cleanedJson.length() - 50));
                    throw new RuntimeException(String.format("TRUNCATED JSON: doesn't end with '}'. Length: %d chars. Ending: ...%s",
                        cleanedJson.length(), ending));
                }

                // Count braces to ensure balanced JSON
                long openBraces = cleanedJson.chars().filter(ch -> ch == '{').count();
                long closeBraces = cleanedJson.chars().filter(ch -> ch == '}').count();
                if (openBraces != closeBraces) {
                    throw new RuntimeException(String.format("Unbalanced JSON: %d open vs %d close braces (truncated)",
                        openBraces, closeBraces));
                }

                // Parse JSON from cleaned response
                JsonNode tailoredContent = objectMapper.readTree(cleanedJson);

                // POST-VALIDATION: Validate response has required fields
                if (!tailoredContent.has("atsScore") || !tailoredContent.has("experienceBullets")) {
                    throw new RuntimeException("Incomplete response: missing required fields (possible truncation)");
                }

                // Validate experienceBullets is an array with content
                JsonNode bullets = tailoredContent.path("experienceBullets");
                if (!bullets.isArray() || bullets.size() == 0) {
                    throw new RuntimeException("Invalid experienceBullets: must be non-empty array");
                }

                int atsScore = tailoredContent.path("atsScore").asInt(0);
                log.info("✅ Claude API success (iteration {}, attempt {}) - ATS Score: {}, response: {} chars",
                    iteration, attempt, atsScore, cleanedJson.length());
                return tailoredContent;

            } catch (Exception e) {
                lastException = e;
                log.warn("Claude API attempt {} failed: {}", attempt, e.getMessage());
                log.debug("Full error details", e);
                if (attempt < maxRetries) {
                    Thread.sleep(2000 * attempt); // Exponential backoff
                }
            }
        }

        throw lastException;
    }

    /**
     * Builds initial prompt that sends only content (not full LaTeX).
     * Target: <1000 input tokens
     */
    private String buildInitialPrompt(Job job) {
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

            CRITICAL OUTPUT RULES:
            - Output ONLY valid JSON - NO explanatory text before or after
            - NO markdown formatting (**bold**, *italic*) anywhere
            - Start response with { and end with }
            - Do not add comments or explanations outside the JSON

            OUTPUT FORMAT (JSON only, nothing else):
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
     * Builds improvement prompt with feedback from previous iteration.
     * Tells Claude what score it got and how to improve.
     */
    private String buildImprovementPrompt(Job job, JsonNode previousResult, int iteration) {
        int previousScore = previousResult.path("atsScore").asInt(0);
        String previousReasoning = previousResult.path("atsReasoning").asText("");

        // Extract previous bullets for reference
        JsonNode previousBullets = previousResult.path("experienceBullets");
        StringBuilder bulletsList = new StringBuilder();
        if (previousBullets.isArray()) {
            for (int i = 0; i < previousBullets.size(); i++) {
                bulletsList.append(i + 1).append(". \"")
                          .append(previousBullets.get(i).asText())
                          .append("\"\n");
            }
        }

        String currentSkills = String.join(", ", candidateProfile.getSkills());

        return String.format("""
            Your previous resume tailoring scored %d/100 ATS. TARGET: %d+ to maximize shortlist chances.

            PREVIOUS ATTEMPT:
            ATS Score: %d
            Reasoning: "%s"

            Experience Bullets You Generated:
            %s

            WHY SCORE IS LOW - ANALYSIS:
            - Score is %d but target is %d+. Need better keyword placement and exact terminology.
            - Required skills to emphasize: %s
            - Missing skills to address: %s
            - If Docker/K8s mentioned, use terms: 'containerization', 'container orchestration', 'deployment automation'

            TARGET JOB DETAILS:
            Company: %s
            Title: %s
            Level: %s
            Required Skills: %s
            Preferred Skills: %s
            Matched Skills: %s
            Missing Skills: %s
            Responsibilities: %s
            Qualifications: %s

            IMPROVEMENT INSTRUCTIONS (Iteration %d/%d - BE AGGRESSIVE):
            1. INCREASE keyword density - add MORE exact keywords from required/preferred skills
            2. Front-load bullets - put most important keywords in first 3-5 words
            3. Use EXACT terminology from qualifications (word-for-word matches boost ATS)
            4. Add technical acronyms (RESTful, AWS, K8s, CI/CD, SDE, API)
            5. Quantify EVERYTHING - add metrics, percentages, scale numbers
            6. Mirror job title keywords in title line
            7. If missing Docker/K8s, emphasize related: "containerization", "deployment automation", "orchestration"
            8. Remove generic words, add specific technical terms
            9. Look at job responsibilities and echo their exact phrasing
            10. Target job level is %s - adjust seniority signals accordingly

            CRITICAL TARGET: %d+ ATS score. Be VERY AGGRESSIVE with keyword optimization.
            You scored %d last time. You MUST improve this iteration.

            CRITICAL OUTPUT RULES:
            1. Output ONLY valid JSON - NO explanatory text before or after
            2. NO markdown formatting (**bold**, *italic*) anywhere
            3. Start response with { and end with }
            4. Do not add comments or explanations outside the JSON

            OUTPUT FORMAT (JSON only, nothing else):
            {
              "titleLine": "Software Engineer | Java | Spring Boot | ...",
              "experienceBullets": ["improved bullet 1", "improved bullet 2", ...],
              "projectBullets": ["improved project description"],
              "atsScore": 90,
              "atsReasoning": "Improved by adding Docker, Kubernetes keywords..."
            }
            """,
                previousScore, TARGET_ATS_SCORE,
                previousScore, previousReasoning,
                bulletsList.toString(),
                previousScore, TARGET_ATS_SCORE,
                job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "",
                job.getMissingSkills() != null ? String.join(", ", job.getMissingSkills()) : "",
                job.getCompany(),
                job.getTitle(),
                job.getJobLevel() != null ? job.getJobLevel() : "Not specified",
                job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "",
                job.getPreferredSkills() != null ? String.join(", ", job.getPreferredSkills()) : "",
                job.getMatchedSkills() != null ? String.join(", ", job.getMatchedSkills()) : "",
                job.getMissingSkills() != null ? String.join(", ", job.getMissingSkills()) : "",
                job.getResponsibilities() != null ? truncate(String.join(" ", job.getResponsibilities()), 300) : "",
                job.getQualifications() != null ? truncate(String.join(" ", job.getQualifications()), 300) : "",
                iteration, MAX_ITERATIONS,
                job.getJobLevel() != null ? job.getJobLevel() : "SDE2",
                TARGET_ATS_SCORE,
                previousScore
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
                    escapeLatex(titleLine)
            );
        }

        // Replace experience bullets (if provided)
        JsonNode experienceBullets = tailoredContent.path("experienceBullets");
        if (experienceBullets.isArray() && experienceBullets.size() > 0) {
            // Find and replace each \resumeItem{...} block with tailored version
            // Use String manipulation instead of regex to avoid escaping issues
            String marker = "\\resumeItem{";

            for (int i = 0; i < Math.min(4, experienceBullets.size()); i++) {
                int startIndex = tailoredLatex.indexOf(marker);
                if (startIndex == -1) break;  // No more \resumeItem to replace

                // Find matching closing brace
                int openBraces = 1;
                int endIndex = startIndex + marker.length();
                while (endIndex < tailoredLatex.length() && openBraces > 0) {
                    char c = tailoredLatex.charAt(endIndex);
                    if (c == '{' && (endIndex == 0 || tailoredLatex.charAt(endIndex - 1) != '\\')) {
                        openBraces++;
                    } else if (c == '}' && (endIndex == 0 || tailoredLatex.charAt(endIndex - 1) != '\\')) {
                        openBraces--;
                    }
                    endIndex++;
                }

                // Replace the old content with new tailored content
                String escapedBullet = escapeLatex(experienceBullets.get(i).asText());
                String replacement = marker + escapedBullet + "}";
                tailoredLatex = tailoredLatex.substring(0, startIndex) + replacement + tailoredLatex.substring(endIndex);
            }
        }

        return tailoredLatex;
    }

    /**
     * Escapes special LaTeX characters to prevent compilation errors.
     * Characters like %, #, &, _, etc. need to be escaped in LaTeX.
     *
     * IMPORTANT: Process in specific order to avoid double-escaping:
     * 1. Backslash first (but unlikely in resume text)
     * 2. Braces next (important for structure)
     * 3. Other special chars last
     */
    private String escapeLatex(String text) {
        if (text == null) return "";

        return text
            // Backslash must be first, but wrap in {} to avoid issues
            .replace("\\", "\\textbackslash ")
            // Braces next (structural)
            .replace("{", "\\{")
            .replace("}", "\\}")
            // Common special characters
            .replace("%", "\\%")
            .replace("$", "\\$")
            .replace("&", "\\&")
            .replace("#", "\\#")
            .replace("_", "\\_")
            // Less common (use commands)
            .replace("~", "\\textasciitilde ")
            .replace("^", "\\textasciicircum ")
            .replace("<", "\\textless ")
            .replace(">", "\\textgreater ")
            .replace("|", "\\textbar ");
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
            // Now target 85+ instead of 75+
            int atsScore = 80 + (Math.abs(job.getCompany().hashCode()) % 15);  // 80-94

            String mockJson = String.format("""
                {
                  "titleLine": "Software Engineer III | Java | Spring Boot | Microservices | Kafka",
                  "experienceBullets": [
                    "Architected distributed Docker-containerized microservices handling 500K+ transactions daily using Java, Spring Boot, and Kafka with P99 latency of 120ms",
                    "Reduced transaction processing latency by 40%% (2.5s → 1.5s P99) by implementing Redis caching and optimizing SQL queries with connection pooling",
                    "Designed event-driven Kafka pipeline processing 10K QPS with guaranteed delivery, automatic retry mechanisms, and Kubernetes orchestration",
                    "Designed RESTful APIs serving 100M+ users with 99.95%% uptime using Spring Boot, Docker containerization, and CI/CD deployment automation"
                  ],
                  "atsScore": %d,
                  "atsReasoning": "Mock ATS analysis - strong keyword alignment with Java, Spring Boot, Microservices, Docker, Kubernetes. Quantified achievements present. Technical acronyms included."
                }
                """, atsScore);

            return objectMapper.readTree(mockJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock response", e);
        }
    }
}
