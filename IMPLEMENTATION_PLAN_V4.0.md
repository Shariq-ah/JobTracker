# Implementation Plan: v4.0 Iterative ATS Improvement (85% Target)

## Overview

Implement iterative resume tailoring where Claude automatically improves resumes until ATS score ≥ 85%, with a maximum of 4 iterations.

---

## Changes Required

### 1. Update `ResumeTailoringService.java`

**File:** `src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java`

#### Change 1.1: Update Constants

```java
// Line 32-33 (BEFORE)
private static final String PROMPT_VERSION = "3.0-optimized";
private static final int MAX_RETRIES = 2;

// (AFTER)
private static final String PROMPT_VERSION = "4.0-iterative-85";
private static final int MAX_ITERATIONS = 4;
private static final int TARGET_ATS_SCORE = 85;
```

#### Change 1.2: Modify `callClaudeForTailoring()` Method

**Current method** (lines 123-182):
- Single call to Claude
- No iteration loop
- No ATS threshold check

**New method** (replace entire method):

```java
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
```

#### Change 1.3: Extract `invokeClaude()` Method

**Add new method** (after `callClaudeForTailoring()`):

```java
/**
 * Invokes Claude API with given prompt.
 * Handles retries with exponential backoff.
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

            // Strip markdown code fences if present
            String cleanedJson = contentText
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("\\s*```$", "")
                    .trim();

            // Parse JSON from cleaned response
            JsonNode tailoredContent = objectMapper.readTree(cleanedJson);

            log.info("Claude API success (iteration {}, attempt {})", iteration, attempt);
            return tailoredContent;

        } catch (Exception e) {
            lastException = e;
            log.warn("Claude API attempt {} failed: {}", attempt, e.getMessage());
            if (attempt < maxRetries) {
                Thread.sleep(2000 * attempt); // Exponential backoff
            }
        }
    }
    
    throw lastException;
}
```

#### Change 1.4: Rename `buildOptimizedPrompt()` to `buildInitialPrompt()`

```java
// Line 188 (BEFORE)
private String buildOptimizedPrompt(Job job) {

// (AFTER)
private String buildInitialPrompt(Job job) {
```

**Keep the same prompt content** - no changes needed.

#### Change 1.5: Add `buildImprovementPrompt()` Method

**Add new method** (after `buildInitialPrompt()`):

```java
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
        
        OUTPUT (JSON only):
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
```

#### Change 1.6: Update Mock Response (for dev testing)

```java
// Line 352-375 (update getMockTailoringResponse method)
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
```

---

## Testing Plan

### Step 1: Enable Dev Mock Mode

**File:** `src/main/resources/application-dev.properties`

```properties
# Enable mock mode for testing (FREE - no AWS API calls)
ai.dev.mock.enabled=true
```

### Step 2: Test Iteration Logic

Run the application and check logs:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Expected logs:**
```
Tailoring resume for: Software Engineer II at Amazon
🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)
Iteration 1 - ATS Score: 87
✅ Target ATS score reached: 87 (iterations: 1)
Tailored resume saved with ATS score: 87
```

### Step 3: Test with Low Mock Score

**Temporarily modify mock** to return low score (e.g., 70):

```java
// In getMockTailoringResponse()
int atsScore = 70;  // Force low score for testing
```

**Expected logs:**
```
Iteration 1 - ATS Score: 70
⚠️ ATS score 70 below target (85). Requesting improvement (iteration 2/4)
Iteration 2 - ATS Score: 70
⚠️ ATS score 70 below target (85). Requesting improvement (iteration 3/4)
Iteration 3 - ATS Score: 70
⚠️ ATS score 70 below target (85). Requesting improvement (iteration 4/4)
Iteration 4 - ATS Score: 70
⚠️ Could not reach 85% ATS after 4 iterations. Final score: 70
```

### Step 4: Disable Mock Mode (Production)

**File:** `src/main/resources/application-dev.properties`

```properties
# Disable mock mode - use real AWS Bedrock API
ai.dev.mock.enabled=false
```

### Step 5: Test with Real API

Run with real AWS Bedrock and monitor:
- Iteration count per resume
- Final ATS scores
- Cost per resume
- Total processing time

---

## Rollback Plan

If issues occur, revert to v3.0:

```bash
git diff HEAD src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java > v4.0-changes.patch
git checkout HEAD -- src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java
```

---

## Monitoring

### Key Metrics to Track

**MongoDB Query** (after 1 week):

```javascript
db.tailored_resumes.aggregate([
  {
    $match: { 
      claudePromptVersion: "4.0-iterative-85",
      tailoredAt: { $gte: ISODate("2026-06-22T00:00:00Z") }
    }
  },
  {
    $group: {
      _id: null,
      count: { $sum: 1 },
      avgATS: { $avg: "$atsScore" },
      minATS: { $min: "$atsScore" },
      maxATS: { $max: "$atsScore" },
      above85: { $sum: { $cond: [{ $gte: ["$atsScore", 85] }, 1, 0] } },
      above90: { $sum: { $cond: [{ $gte: ["$atsScore", 90] }, 1, 0] } }
    }
  },
  {
    $project: {
      count: 1,
      avgATS: { $round: ["$avgATS", 1] },
      minATS: 1,
      maxATS: 1,
      percentAbove85: { $multiply: [{ $divide: ["$above85", "$count"] }, 100] },
      percentAbove90: { $multiply: [{ $divide: ["$above90", "$count"] }, 100] }
    }
  }
])
```

**Expected results:**
- `avgATS`: 87-92
- `percentAbove85`: 75-85%
- `percentAbove90`: 30-40%

---

## Cost Tracking

### AWS Billing Alert

Set up AWS CloudWatch billing alert for Bedrock usage:

**Threshold:** $5/month (generous buffer)

**Alert at:** $3/month (early warning)

### Manual Tracking

**After first week**, check AWS Cost Explorer:
- Bedrock service costs
- Compare to expected $0.53/month for resume tailoring
- Verify total < $2.50/month

---

## Success Criteria

✅ **Feature complete** when:
1. Iteration loop works correctly
2. Stops at 85% or after 4 iterations
3. Logs show iteration progress
4. ATS scores improved vs v3.0
5. Cost within budget ($2.50/month)

✅ **Production ready** when:
1. Tested with dev mock mode
2. Tested with real API (5+ resumes)
3. No errors in logs
4. MongoDB data looks correct
5. Telegram notifications working

---

## Timeline

| Phase | Duration | Tasks |
|-------|----------|-------|
| **Day 1** | 2-3 hours | Implement code changes |
| **Day 2** | 1 hour | Test with mock mode |
| **Day 3** | 1 hour | Test with real API (5 resumes) |
| **Week 1** | Ongoing | Monitor production, collect metrics |
| **Week 2** | 1 hour | Review metrics, adjust if needed |

**Total effort:** ~5-6 hours spread over 2 weeks

---

## Next Steps (After v4.0 Complete)

1. ✅ v4.0 complete (iterative ATS improvement)
2. 🔜 v4.1: Split notification flow (job alert + PDF follow-up)
3. 🔜 v4.2: Add more providers (Google, Meta, Oracle)
4. 🔜 v4.3: Web dashboard for resume management

---

**Ready to implement?** Let me know if you want me to:
1. Create the actual code changes
2. Generate test scripts
3. Set up monitoring queries
