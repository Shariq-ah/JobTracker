package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.config.CandidateProfile;
import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.model.WorkMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class JdExtractionService {

    private static final Logger log = LoggerFactory.getLogger(JdExtractionService.class);
    private static final String PROMPT_VERSION = "2.0";
    private static final int MAX_JD_CHARS = 8000; // ~2000 tokens

    @Autowired
    private BedrockRuntimeClient bedrockClient;

    @Autowired
    private CandidateProfile candidateProfile;

    @Value("${aws.bedrock.model.id}")
    private String modelId;

    @Value("${aws.bedrock.max.tokens}")
    private int maxTokens;

    @Value("${aws.bedrock.temperature}")
    private double temperature;

    @Value("${ai.dev.mock.enabled:false}")
    private boolean devMockEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extract structured data from job description.
     * Falls back gracefully on any error - never throws exceptions.
     */
    public Job extractStructuredData(Job job) {
        String description = job.getDescription();

        // Validate input
        if (description == null || description.trim().isEmpty()) {
            log.warn("Empty JD for job {}, skipping extraction", job.getTitle());
            job.setExtractionSuccess(false);
            return job;
        }

        // Truncate JD if too long to prevent excessive token usage
        String truncatedJd = truncateJd(description);
        if (truncatedJd.length() < description.length()) {
            log.info("Truncated JD from {} to {} chars for {}",
                description.length(), truncatedJd.length(), job.getTitle());
        }

        // Build prompt
        String prompt = buildExtractionPrompt(truncatedJd, job.getTitle());

        try {
            String jsonResponse;

            // DEV MODE: Use mock response to avoid API costs
            if (devMockEnabled) {
                log.info("🎭 DEV MODE: Using mock response (FREE - no API call)");
                jsonResponse = getMockResponse(job.getTitle(), truncatedJd);
            } else {
                // PRODUCTION: Call real Bedrock API
                jsonResponse = callBedrockWithRetry(prompt, 3);
            }

            // Parse response
            parseAndPopulateJob(job, jsonResponse);

            job.setExtractionSuccess(true);
            job.setExtractedAt(LocalDateTime.now());
            job.setPromptVersion(PROMPT_VERSION);

            log.info("Successfully extracted structured data for: {}", job.getTitle());
            return job;

        } catch (Exception e) {
            log.error("Extraction failed for {}: {}", job.getTitle(), e.getMessage());
            job.setExtractionSuccess(false);
            return job;
        }
    }

    private String truncateJd(String jd) {
        if (jd.length() <= MAX_JD_CHARS) {
            return jd;
        }
        return jd.substring(0, MAX_JD_CHARS) + "... [truncated]";
    }

    private String buildExtractionPrompt(String jd, String title) {
        // Build candidate profile section
        String candidateSkills = candidateProfile.getSkills() != null
            ? String.join(", ", candidateProfile.getSkills())
            : "Not specified";

        // Use replace() instead of format() to avoid issues with % characters in JD text
        String prompt = """
            You are an expert job matcher. Analyze how well this candidate fits the job description.

            CANDIDATE PROFILE:
            - Experience: {{EXPERIENCE}} years in Java backend development
            - Skills: {{CANDIDATE_SKILLS}}
            - Target Level: SDE2/Mid-level positions

            JOB DETAILS:
            Title: {{TITLE}}

            Job Description:
            {{JD}}

            TASK: Extract structured information AND calculate match score based on:
            1. Skill overlap (required vs preferred vs nice-to-have)
            2. Experience level fit
            3. Job level alignment (candidate targets SDE2)
            4. Overall role suitability

            Return JSON in this EXACT format:
            {
              "score": 75,
              "matchedSkills": ["Java", "Spring Boot", "REST API"],
              "missingSkills": ["Docker", "Kubernetes"],
              "requiredSkills": ["Java", "Spring Boot", "Docker"],
              "preferredSkills": ["Kafka", "Kubernetes"],
              "niceToHaveSkills": ["GraphQL", "Redis"],
              "minExperience": 3,
              "maxExperience": 6,
              "jobLevel": "SDE2",
              "workMode": "HYBRID",
              "employmentType": "Full-time",
              "salaryMin": 2000000,
              "salaryMax": 3500000,
              "salaryCurrency": "INR",
              "teamDescription": "Brief team description",
              "responsibilities": ["Responsibility 1", "Responsibility 2"],
              "qualifications": ["Qualification 1", "Qualification 2"],
              "applyRecommendation": "Strong Apply",
              "scoreReason": "Strong Java/Spring Boot match, minor gap on containerization"
            }

            SCORING GUIDE:
            - 80-100: Strong Apply (excellent match, all core skills present)
            - 60-79: Apply (good match, minor gaps)
            - 40-59: Consider (moderate match, some key gaps)
            - 0-39: Skip (poor match, major gaps or level mismatch)

            RULES:
            - matchedSkills: Skills from candidate profile that job requires
            - missingSkills: Skills from candidate profile that job doesn't mention
            - score: Your calculated match score (0-100)
            - applyRecommendation: "Strong Apply", "Apply", "Consider", or "Skip"
            - scoreReason: Brief 1-sentence explanation of score
            - Use "Not mentioned" for missing text fields
            - Use null for missing numeric fields
            - Standardize skill names (e.g., "SpringBoot" → "Spring Boot")
            - For experience: "3-5 years" → minExperience: 3, maxExperience: 5
            - For experience: "5+ years" → minExperience: 5, maxExperience: null
            - Salary conversions: 20 LPA = 2000000 INR, $100K USD ≈ 8300000 INR
            - workMode values: REMOTE, HYBRID, ONSITE, FLEXIBLE, or NOT_SPECIFIED
            - jobLevel values: Junior, SDE1, SDE2, Senior, Staff, Principal, or "Not mentioned"

            IMPORTANT: Return ONLY raw JSON. Do NOT wrap in markdown code fences (no ```json or ```). Start directly with { and end with }.
            """;

        return prompt
            .replace("{{TITLE}}", title)
            .replace("{{JD}}", jd)
            .replace("{{EXPERIENCE}}", String.valueOf(candidateProfile.getExperience()))
            .replace("{{CANDIDATE_SKILLS}}", candidateSkills);
    }

    private String callBedrockWithRetry(String prompt, int maxRetries) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = callBedrock(prompt);
                // Strip markdown code fences if present
                return stripMarkdownCodeFence(response);
            } catch (Exception e) {
                lastException = e;
                log.warn("Bedrock attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    long backoffMs = (long) Math.pow(2, attempt) * 2000; // 4s, 8s
                    log.info("Waiting {}ms before retry...", backoffMs);
                    Thread.sleep(backoffMs);
                }
            }
        }

        throw new Exception("All Bedrock retries failed", lastException);
    }

    private String callBedrock(String prompt) throws Exception {
        // Build Bedrock request payload for Claude
        String requestBody = String.format("""
            {
                "anthropic_version": "bedrock-2023-05-31",
                "max_tokens": %d,
                "temperature": %.1f,
                "messages": [
                    {
                        "role": "user",
                        "content": "%s"
                    }
                ]
            }
            """, maxTokens, temperature, escapeJson(prompt));

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();

        InvokeModelResponse response = bedrockClient.invokeModel(request);
        String responseBody = response.body().asUtf8String();

        // Parse Claude response to extract content
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode contentArray = root.path("content");

        if (contentArray.isArray() && !contentArray.isEmpty()) {
            return contentArray.get(0).path("text").asText();
        }

        throw new Exception("Invalid Bedrock response format");
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * Strip markdown code fences from Claude's response.
     * Handles: ```json\n{...}\n``` or ```{...}```
     */
    private String stripMarkdownCodeFence(String response) {
        if (response == null) {
            return null;
        }

        String trimmed = response.trim();

        // Remove opening fence: ```json or ```
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3).trim();
        }

        // Remove closing fence: ```
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }

        return trimmed;
    }

    private void parseAndPopulateJob(Job job, String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);

        // AI Match Score and Analysis (NEW)
        Double aiScore = parseDouble(root.path("score"));
        job.setAiMatchScore(aiScore);
        // If AI score exists, use it as primary match score
        if (aiScore != null) {
            job.setMatchScore(aiScore);
        }
        job.setApplyRecommendation(parseStringOrDefault(root.path("applyRecommendation"), "Not specified"));
        job.setScoreReason(parseStringOrDefault(root.path("scoreReason"), "Not specified"));

        // Matched and Missing Skills (from AI analysis)
        List<String> matchedSkills = parseStringList(root.path("matchedSkills"));
        job.setMatchedSkills(matchedSkills.isEmpty() ? null : matchedSkills);

        List<String> missingSkills = parseStringList(root.path("missingSkills"));
        job.setMissingSkills(missingSkills.isEmpty() ? null : missingSkills);

        // Required skills
        List<String> requiredSkills = parseStringList(root.path("requiredSkills"));
        job.setRequiredSkills(requiredSkills.isEmpty() ? null : requiredSkills);

        // Preferred skills
        List<String> preferredSkills = parseStringList(root.path("preferredSkills"));
        job.setPreferredSkills(preferredSkills.isEmpty() ? null : preferredSkills);

        // Nice-to-have skills
        List<String> niceToHaveSkills = parseStringList(root.path("niceToHaveSkills"));
        job.setNiceToHaveSkills(niceToHaveSkills.isEmpty() ? null : niceToHaveSkills);

        // Experience
        job.setMinExperienceRequired(parseInteger(root.path("minExperience")));
        job.setMaxExperienceRequired(parseInteger(root.path("maxExperience")));

        // Job level
        job.setJobLevel(parseStringOrDefault(root.path("jobLevel"), "Not mentioned"));

        // Work mode
        String workModeStr = parseStringOrDefault(root.path("workMode"), "NOT_SPECIFIED");
        job.setWorkMode(parseWorkMode(workModeStr));

        // Employment type
        job.setEmploymentType(parseStringOrDefault(root.path("employmentType"), "Not mentioned"));

        // Salary
        job.setSalaryMin(parseLong(root.path("salaryMin")));
        job.setSalaryMax(parseLong(root.path("salaryMax")));
        job.setSalaryCurrency(parseStringOrDefault(root.path("salaryCurrency"), "Not mentioned"));

        // Team description
        job.setTeamDescription(parseStringOrDefault(root.path("teamDescription"), "Not mentioned"));

        // Responsibilities
        List<String> responsibilities = parseStringList(root.path("responsibilities"));
        job.setResponsibilities(responsibilities.isEmpty() ? null : responsibilities);

        // Qualifications
        List<String> qualifications = parseStringList(root.path("qualifications"));
        job.setQualifications(qualifications.isEmpty() ? null : qualifications);
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                String value = item.asText().trim();
                if (!value.isEmpty() && !value.equalsIgnoreCase("Not mentioned")) {
                    result.add(value);
                }
            });
        }
        return result;
    }

    private Integer parseInteger(JsonNode node) {
        return node.isNull() || node.isMissingNode() ? null : node.asInt();
    }

    private Long parseLong(JsonNode node) {
        return node.isNull() || node.isMissingNode() ? null : node.asLong();
    }

    private Double parseDouble(JsonNode node) {
        return node.isNull() || node.isMissingNode() ? null : node.asDouble();
    }

    private String parseStringOrDefault(JsonNode node, String defaultValue) {
        if (node.isNull() || node.isMissingNode()) {
            return defaultValue;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private WorkMode parseWorkMode(String value) {
        try {
            return WorkMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WorkMode.NOT_SPECIFIED;
        }
    }

    /**
     * Returns mock response for dev testing (zero cost).
     * Simulates realistic Claude output with varying scores.
     */
    private String getMockResponse(String jobTitle, String jd) {
        // Vary score based on job title hash for realistic testing
        int baseScore = 60 + (Math.abs(jobTitle.hashCode()) % 30);  // 60-89%

        boolean hasSpringBoot = jd.toLowerCase().contains("spring boot");
        boolean hasKafka = jd.toLowerCase().contains("kafka");
        boolean hasMicroservices = jd.toLowerCase().contains("microservice");

        // Adjust score based on keywords
        int finalScore = baseScore;
        if (hasSpringBoot) finalScore += 5;
        if (hasKafka) finalScore += 3;
        if (hasMicroservices) finalScore += 2;
        finalScore = Math.min(finalScore, 95);  // Cap at 95

        String recommendation = finalScore >= 80 ? "Strong Apply" :
                               finalScore >= 60 ? "Apply" :
                               finalScore >= 40 ? "Consider" : "Skip";

        return String.format("""
            {
              "matchScore": %d,
              "recommendation": "%s",
              "scoreReason": "Mock response for testing - strong Java/Spring Boot match with relevant experience",
              "matchedSkills": ["Java", "Spring Boot", "REST API", "SQL"],
              "missingSkills": ["Kafka", "AWS"],
              "requiredSkills": ["Java", "Spring Boot", "Microservices"],
              "preferredSkills": ["Kafka", "Docker", "AWS"],
              "niceToHaveSkills": ["Kubernetes", "MongoDB"],
              "minExperienceRequired": 3,
              "maxExperienceRequired": 5,
              "jobLevel": "SDE2",
              "workMode": "HYBRID",
              "salaryMinINR": 2000000,
              "salaryMaxINR": 3500000,
              "teamDescription": "Mock team description for testing",
              "responsibilities": "Mock responsibilities",
              "qualifications": "Mock qualifications"
            }
            """, finalScore, recommendation);
    }
}