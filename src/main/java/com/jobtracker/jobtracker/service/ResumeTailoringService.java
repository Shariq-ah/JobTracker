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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for AI-powered resume tailoring using AWS Bedrock (Claude).
 * Optimized for minimal token usage by sending only content, not full LaTeX template.
 */
@Service
@Slf4j
public class ResumeTailoringService {

    private static final String PROMPT_VERSION = "4.0-iterative-improvement";
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
     * Calls Claude API to get tailored resume content with iterative improvement.
     * If ATS score < 80%, asks Claude to improve until target reached (max 4 iterations).
     */
    private JsonNode callClaudeForTailoring(Job job) {
        // DEV MODE: Return mock response to avoid API costs
        if (devMockEnabled) {
            log.info("🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)");
            return getMockTailoringResponse(job);
        }

        // PRODUCTION: Iterative improvement loop
        int maxImprovements = 4;
        JsonNode result = null;
        String initialPrompt = buildOptimizedPrompt(job);

        for (int iteration = 1; iteration <= maxImprovements; iteration++) {
            String prompt = (iteration == 1) ? initialPrompt : buildImprovementPrompt(job, result);

            result = callClaudeAPI(prompt, iteration);

            if (result == null) {
                return null; // API call failed
            }

            int atsScore = result.path("atsScore").asInt(0);
            log.info("Iteration {} - ATS Score: {}", iteration, atsScore);

            if (atsScore >= 80) {
                log.info("✅ Target ATS score reached: {} (iterations: {})", atsScore, iteration);
                return result;
            }

            if (iteration < maxImprovements) {
                log.info("⚠️ ATS score {} below target (80%). Requesting improvement (iteration {}/{})",
                    atsScore, iteration + 1, maxImprovements);
            }
        }

        // Return best attempt even if below 80%
        int finalScore = result.path("atsScore").asInt(0);
        log.warn("⚠️ Could not reach 80% ATS after {} iterations. Final score: {}", maxImprovements, finalScore);
        return result;
    }

    /**
     * Calls Claude API once (no retry logic, handled by parent).
     */
    private JsonNode callClaudeAPI(String prompt, int iteration) {
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

                // Extract JSON from response (Claude sometimes adds markdown around it)
                String cleanedJson = extractJsonFromText(contentText);

                // Log for debugging on retry attempts
                if (attempt > 1) {
                    log.info("Retry attempt {} - Response length: {} chars, Cleaned JSON length: {} chars",
                            attempt, contentText.length(), cleanedJson.length());
                    log.debug("Full cleaned JSON: {}", cleanedJson);
                }

                // Parse JSON from cleaned response
                JsonNode tailoredContent = objectMapper.readTree(cleanedJson);

                // VALIDATION: Ensure bullet count doesn't exceed 1-page limit
                JsonNode experienceBullets = tailoredContent.path("experienceBullets");
                JsonNode projectBullets = tailoredContent.path("projectBullets");
                int totalBullets = experienceBullets.size() + projectBullets.size();

                if (totalBullets > 12) {
                    log.warn("Claude returned {} bullets (max 12). Truncating to maintain 1-page constraint.", totalBullets);
                    // This shouldn't happen if prompt is followed, but safeguard anyway
                }

                log.info("Claude tailoring successful (attempt {}) - {} bullets returned", attempt, totalBullets);
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
     * Builds optimized prompt that sends actual resume content.
     * Extracts real content from base LaTeX template.
     */
    private String buildOptimizedPrompt(Job job) {
        try {
            // Load and extract content from actual resume
            String baseLatex = loadBaseLatexTemplate();
            String resumeContent = extractResumeContentFromLatex(baseLatex);

            return String.format("""
            You are an expert ATS resume optimizer. Tailor this resume content for the job below.

            CURRENT RESUME CONTENT:
            %s

            TARGET JOB:
            Company: %s
            Title: %s
            Level: %s
            Required Skills: %s
            Preferred Skills: %s
            Nice-to-Have Skills: %s
            Matched Skills: %s
            Missing Skills: %s
            Responsibilities: %s
            Qualifications: %s

            CRITICAL RULES - ZERO TOLERANCE:
            ❌ NEVER fabricate experience, projects, or achievements that don't exist
            ❌ NEVER claim skills or technologies not in the candidate's profile
            ❌ NEVER invent metrics, numbers, or percentages
            ❌ NEVER add fake company names, roles, or time periods
            ❌ NEVER create fictional projects or contributions

            ✅ ONLY rephrase existing experience to emphasize matched skills
            ✅ ONLY reorder bullets to prioritize relevant experience
            ✅ ONLY add keywords that naturally fit into existing achievements
            ✅ ONLY use metrics and numbers that are already present
            ✅ If a required skill is missing, do NOT fake it - just optimize what exists

            INSTRUCTIONS:
            1. Reorder/rephrase experience bullets to emphasize matched skills
            2. Add keywords from required/preferred/nice-to-have skills naturally into existing bullets
            3. Adjust title line to match job level (use matched skills only)
            4. Keep content quantifiable and achievement-focused
            5. 🚨 CRITICAL: Maintain STRICT 1-page constraint - NEVER exceed the number of bullets in the input resume
            6. 🚨 Maximum allowed: 5 eBay bullets + 5 HCLTech bullets + 2 project bullets = 12 total bullets MAX
            7. Front-load bullets with required skills, then preferred, then nice-to-have
            8. Use exact terminology from qualifications if it matches existing experience
            9. Calculate ATS score (0-100) based on keyword density, relevance, quantification
            10. Target: 85+ ATS score (reject if below 75 - better to not apply than send weak resume)

            ATS SCORING CRITERIA:
            - 90-100: All required skills present, preferred skills covered, exact JD terminology, strong metrics
            - 85-89: Required + most preferred skills, good keyword density, quantified achievements
            - 80-84: Required skills present, some preferred, minor gaps
            - 75-79: Core required skills, missing some keywords
            - Below 75: Too many gaps - DO NOT apply with this resume

            CRITICAL OUTPUT FORMAT:
            Return ONLY valid JSON. NO markdown. NO explanations. NO code fences.
            Just the raw JSON object below:

            {
              "titleLine": "Software Engineer | Java | Spring Boot | ...",
              "experienceBullets": ["tailored bullet 1", "tailored bullet 2", ...],
              "projectBullets": ["tailored project description"],
              "atsScore": 92,
              "atsReasoning": "Strong match on required skills (Java, Spring Boot, Microservices). Covered 80%% of preferred skills (Kafka, Redis). Missing nice-to-have: Docker. Added qualifications keywords naturally. No fabrication - all claims verified."
            }

            DO NOT wrap in ```json or ``` or add any text before/after the JSON.
            """,
                resumeContent,
                job.getCompany(),
                job.getTitle(),
                job.getJobLevel() != null ? job.getJobLevel() : "Not specified",
                job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "Not specified",
                job.getPreferredSkills() != null ? String.join(", ", job.getPreferredSkills()) : "Not specified",
                job.getNiceToHaveSkills() != null ? String.join(", ", job.getNiceToHaveSkills()) : "Not specified",
                job.getMatchedSkills() != null ? String.join(", ", job.getMatchedSkills()) : "",
                job.getMissingSkills() != null ? String.join(", ", job.getMissingSkills()) : "",
                job.getResponsibilities() != null ? truncate(String.join(" ", job.getResponsibilities()), 300) : "Not specified",
                job.getQualifications() != null ? truncate(String.join(" ", job.getQualifications()), 300) : "Not specified"
        );
        } catch (Exception e) {
            log.error("Failed to load base resume, using fallback content: {}", e.getMessage());
            return buildFallbackPrompt(job);
        }
    }

    /**
     * Fallback prompt if base resume can't be loaded.
     */
    private String buildFallbackPrompt(Job job) {
        String currentSkills = String.join(", ", candidateProfile.getSkills());

        return String.format("""
            You are an expert ATS resume optimizer. Tailor this resume content for the job below.

            CURRENT RESUME:
            Name: %s
            Skills: %s

            TARGET JOB:
            Company: %s
            Title: %s
            Required Skills: %s
            Matched Skills: %s

            OUTPUT (JSON only):
            {
              "titleLine": "Software Engineer | Java | Spring Boot | ...",
              "experienceBullets": ["bullet 1", "bullet 2", "bullet 3", "bullet 4"],
              "projectBullets": ["project description"],
              "atsScore": 85,
              "atsReasoning": "Analysis here"
            }
            """,
            candidateProfile.getName(),
            currentSkills,
            job.getCompany(),
            job.getTitle(),
            job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "",
            job.getMatchedSkills() != null ? String.join(", ", job.getMatchedSkills()) : ""
        );
    }

    /**
     * Extracts text content from LaTeX resume (removes formatting commands).
     */
    private String extractResumeContentFromLatex(String latex) {
        // Remove everything before \begin{document}
        int beginDoc = latex.indexOf("\\begin{document}");
        if (beginDoc != -1) {
            latex = latex.substring(beginDoc);
        }

        // Remove everything after \end{document}
        int endDoc = latex.indexOf("\\end{document}");
        if (endDoc != -1) {
            latex = latex.substring(0, endDoc);
        }

        // Remove common LaTeX commands but keep content
        String content = latex
            // Remove commands with {}
            .replaceAll("\\\\[a-zA-Z]+\\{([^}]+)\\}", "$1")  // \command{text} -> text
            .replaceAll("\\\\[a-zA-Z]+\\[([^]]+)\\]", "")     // \command[opt] -> empty
            .replaceAll("\\\\[a-zA-Z]+", "")                  // \command -> empty
            // Remove special characters
            .replaceAll("\\$\\|\\$", "|")                     // $|$ -> |
            .replaceAll("\\\\&", "&")                         // \& -> &
            .replaceAll("\\\\%", "%")                         // \% -> %
            // Remove environment markers
            .replaceAll("\\\\begin\\{[^}]+\\}", "")
            .replaceAll("\\\\end\\{[^}]+\\}", "")
            // Clean up whitespace
            .replaceAll("\\s+", " ")
            .trim();

        return content;
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

        // Replace ALL experience + project bullets (if provided)
        JsonNode experienceBullets = tailoredContent.path("experienceBullets");
        JsonNode projectBullets = tailoredContent.path("projectBullets");

        if (experienceBullets.isArray() && experienceBullets.size() > 0) {
            // Combine experience and project bullets for unified replacement
            List<String> allBullets = new ArrayList<>();
            for (JsonNode bullet : experienceBullets) {
                allBullets.add(bullet.asText());
            }
            if (projectBullets.isArray()) {
                for (JsonNode bullet : projectBullets) {
                    allBullets.add(bullet.asText());
                }
            }

            // Safety check: Ensure we don't exceed 1-page limit
            if (allBullets.size() > 12) {
                log.warn("Claude returned {} bullets, truncating to 12 to maintain 1-page constraint", allBullets.size());
                allBullets = allBullets.subList(0, 12);
            }

            // Replace ALL \resumeItem{...} blocks sequentially
            String marker = "\\resumeItem{";

            for (int i = 0; i < allBullets.size(); i++) {
                int startIndex = tailoredLatex.indexOf(marker);
                if (startIndex == -1) {
                    log.warn("Expected {} bullets but only found {} \\resumeItem markers in template", allBullets.size(), i);
                    break;
                }

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
                String escapedBullet = escapeLatex(allBullets.get(i));
                String replacement = marker + escapedBullet + "}";
                tailoredLatex = tailoredLatex.substring(0, startIndex) + replacement + tailoredLatex.substring(endIndex);
            }

            log.info("Successfully replaced {} bullets in LaTeX template", allBullets.size());
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
     * Builds improvement prompt with feedback on current ATS score.
     */
    private String buildImprovementPrompt(Job job, JsonNode previousResult) {
        int previousScore = previousResult.path("atsScore").asInt(0);
        String previousReasoning = previousResult.path("atsReasoning").asText("");

        // Extract current bullets to show Claude what it already generated
        StringBuilder currentBullets = new StringBuilder();
        JsonNode bullets = previousResult.path("experienceBullets");
        if (bullets.isArray()) {
            for (int i = 0; i < bullets.size(); i++) {
                currentBullets.append((i + 1)).append(". \"").append(bullets.get(i).asText()).append("\"\n");
            }
        }

        return String.format("""
            Your previous resume tailoring scored %d/100 ATS. TARGET: 80+ to maximize shortlist chances.

            PREVIOUS ATTEMPT:
            ATS Score: %d
            Reasoning: %s

            Experience Bullets You Generated:
            %s

            WHY SCORE IS LOW - ANALYSIS:
            %s

            TARGET JOB DETAILS:
            Company: %s
            Title: %s
            Required Skills: %s
            Preferred Skills: %s
            Matched Skills: %s
            Missing Skills: %s
            Responsibilities: %s
            Qualifications: %s

            IMPROVEMENT INSTRUCTIONS:
            1. INCREASE keyword density - add MORE exact keywords from required/preferred skills
            2. Front-load bullets - put most important keywords in first 3-5 words of each bullet
            3. Use EXACT terminology from qualifications (word-for-word matches boost ATS)
            4. Add technical acronyms where relevant (RESTful, AWS, K8s, CI/CD, etc.)
            5. Quantify EVERYTHING - add metrics, percentages, scale numbers
            6. Mirror job title keywords in title line
            7. If missing critical skills, emphasize related experience (e.g., Docker → "containerization", K8s → "orchestration")
            8. Remove generic words, add specific technical terms
            9. 🚨 CRITICAL: DO NOT add more bullets. Improve EXISTING bullets only. Keep same bullet count.
            10. 🚨 Maximum allowed: Same number of bullets as previous attempt (must fit in 1 page)

            CRITICAL TARGET: 80+ ATS score. Be AGGRESSIVE with keyword optimization within existing bullets.

            CRITICAL OUTPUT FORMAT:
            Return ONLY valid JSON. NO markdown. NO explanations. NO code fences.
            Just the raw JSON object below:

            {
              "titleLine": "...",
              "experienceBullets": ["improved bullet 1", "improved bullet 2", ...],
              "projectBullets": ["improved project"],
              "atsScore": 85,
              "atsReasoning": "Improved by adding X, Y, Z keywords. Now matches 90%% of required skills..."
            }

            DO NOT wrap in ```json or ``` or add any text before/after the JSON.
            """,
            previousScore,
            previousScore,
            previousReasoning,
            currentBullets.toString(),
            analyzeWhyScoreIsLow(previousScore, previousReasoning, job),
            job.getCompany(),
            job.getTitle(),
            job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "Not specified",
            job.getPreferredSkills() != null ? String.join(", ", job.getPreferredSkills()) : "Not specified",
            job.getMatchedSkills() != null ? String.join(", ", job.getMatchedSkills()) : "",
            job.getMissingSkills() != null ? String.join(", ", job.getMissingSkills()) : "",
            job.getResponsibilities() != null ? truncate(String.join(" ", job.getResponsibilities()), 200) : "Not specified",
            job.getQualifications() != null ? truncate(String.join(" ", job.getQualifications()), 200) : "Not specified"
        );
    }

    /**
     * Analyzes why ATS score is low and provides specific improvement hints.
     */
    private String analyzeWhyScoreIsLow(int score, String reasoning, Job job) {
        StringBuilder analysis = new StringBuilder();

        if (score < 60) {
            analysis.append("- CRITICAL: Score is very low. Missing too many required skills.\n");
        } else if (score < 70) {
            analysis.append("- Score is below acceptable. Need more keyword density.\n");
        } else if (score < 80) {
            analysis.append("- Score is close but not enough. Need better keyword placement and exact terminology.\n");
        }

        // Check for missing required skills
        if (job.getRequiredSkills() != null && !job.getRequiredSkills().isEmpty()) {
            analysis.append("- Required skills to emphasize: ")
                .append(String.join(", ", job.getRequiredSkills())).append("\n");
        }

        // Check for missing preferred skills
        if (job.getMissingSkills() != null && !job.getMissingSkills().isEmpty()) {
            analysis.append("- Missing skills to address (if possible): ")
                .append(String.join(", ", job.getMissingSkills())).append("\n");
        }

        // Check reasoning for hints
        if (reasoning.toLowerCase().contains("missing")) {
            analysis.append("- Your reasoning mentions 'missing' - add those keywords if experience supports it\n");
        }

        if (reasoning.toLowerCase().contains("docker") || reasoning.toLowerCase().contains("kubernetes")) {
            analysis.append("- Docker/K8s mentioned - use terms: 'containerization', 'container orchestration', 'deployment automation'\n");
        }

        return analysis.toString();
    }

    /**
     * Extracts pure JSON from Claude's response (removes markdown, explanations, etc.).
     *
     * Strategy:
     * 1. Remove markdown code fences (```json, ```)
     * 2. Extract content between first { and last } (this is the JSON object)
     * 3. DO NOT modify content inside the JSON boundaries (preserves strings with bullets/dashes)
     */
    private String extractJsonFromText(String text) {
        String cleaned = text.trim();

        // Step 1: Remove markdown code fences BEFORE extracting boundaries
        cleaned = cleaned.replaceAll("```json\\s*\\n?", "");
        cleaned = cleaned.replaceAll("```\\s*\\n?", "");
        cleaned = cleaned.replaceAll("\\n?```", "");

        // Step 2: Find JSON object boundaries and extract ONLY the JSON
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        // Step 3: REMOVED - Do NOT remove bullets/dashes after extraction
        // They may be part of valid JSON string content (e.g., "- item" in experience bullets)

        return cleaned.trim();
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
