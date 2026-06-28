# Step 1: Iterative ATS Improvement (85% Target) - Complete Implementation Context

## Overview

**Goal:** Modify `ResumeTailoringService.java` to implement iterative resume tailoring that automatically improves resumes until ATS score ≥ 85% (max 4 iterations).

**Single File Modified:** `src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java`

**No other files touched** - this is a self-contained change.

---

## Current State Analysis

### File: `ResumeTailoringService.java`

**Current Line Count:** 376 lines

**Current Structure:**
```
Lines 1-30:    Package, imports, class declaration
Lines 31-67:   Constants, fields, constructor
Lines 68-117:  tailorResume() - main entry point
Lines 118-182: callClaudeForTailoring() - single API call (NO iteration)
Lines 183-246: buildOptimizedPrompt() - builds initial prompt
Lines 247-296: injectContentIntoTemplate() - LaTeX injection
Lines 297-328: escapeLatex() - LaTeX escaping
Lines 329-341: loadBaseLatexTemplate() - loads base resume
Lines 342-346: truncate() - utility method
Lines 347-376: getMockTailoringResponse() - dev mock mode
```

**Current Behavior:**
1. User calls `tailorResume(job)`
2. Calls `callClaudeForTailoring(job)` - **makes ONE API call**
3. Gets result with ATS score (could be 65, 70, 75, etc.)
4. **Does NOT retry** if score < 85
5. Saves resume with whatever score it got
6. Done

**Problem:** No iteration, no improvement loop, accepts low scores.

---

## What Will Change

### Constants (Lines 32-34)

**BEFORE:**
```java
private static final String PROMPT_VERSION = "3.0-optimized";
private static final int MAX_RETRIES = 2;
```

**AFTER:**
```java
private static final String PROMPT_VERSION = "4.0-iterative-85";
private static final int MAX_ITERATIONS = 4;
private static final int TARGET_ATS_SCORE = 85;
```

**Why:**
- Update version to track prompt changes
- Rename `MAX_RETRIES` → `MAX_ITERATIONS` (clearer semantics)
- Add `TARGET_ATS_SCORE` constant (85%)

---

### Method 1: `callClaudeForTailoring()` (Lines 123-182)

**Current Implementation (Single-shot):**
```java
private JsonNode callClaudeForTailoring(Job job) {
    if (devMockEnabled) {
        return getMockTailoringResponse(job);
    }

    String prompt = buildOptimizedPrompt(job);
    
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        try {
            // ... API call code ...
            return tailoredContent;
        } catch (Exception e) {
            // ... retry with backoff ...
        }
    }
    throw new RuntimeException("Tailoring failed after retries");
}
```

**Problems:**
1. Only ONE prompt, no iteration
2. No ATS threshold check
3. Retries are for API errors, not score improvement
4. No improvement prompt

**NEW Implementation (Iterative):**
```java
private JsonNode callClaudeForTailoring(Job job) {
    if (devMockEnabled) {
        log.info("🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)");
        return getMockTailoringResponse(job);
    }

    JsonNode result = null;
    
    // ITERATION LOOP (NEW!)
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
            
            // CHECK THRESHOLD (NEW!)
            if (atsScore >= TARGET_ATS_SCORE) {
                log.info("✅ Target ATS score reached: {} (iterations: {})", 
                         atsScore, iteration);
                return result;  // STOP EARLY
            }
            
            log.warn("⚠️ ATS score {} below target ({}). Requesting improvement (iteration {}/{})", 
                     atsScore, TARGET_ATS_SCORE, iteration, MAX_ITERATIONS);
            
        } catch (Exception e) {
            log.error("Iteration {} failed: {}", iteration, e.getMessage());
            if (iteration == MAX_ITERATIONS) {
                throw new RuntimeException("All tailoring iterations failed", e);
            }
        }
    }
    
    // Return best attempt even if < 85%
    int finalScore = result != null ? result.path("atsScore").asInt(0) : 0;
    log.warn("⚠️ Could not reach {}% ATS after {} iterations. Final score: {}", 
             TARGET_ATS_SCORE, MAX_ITERATIONS, finalScore);
    return result;
}
```

**Key Changes:**
1. ✅ Iteration loop (1 to 4)
2. ✅ Dynamic prompt selection (initial vs improvement)
3. ✅ ATS threshold check (`if (atsScore >= 85)`)
4. ✅ Early exit on success
5. ✅ Clear logging at each step
6. ✅ Return best attempt if can't reach 85%

---

### Method 2: Extract `invokeClaude()` (NEW METHOD)

**Why extract?**
- Separate API invocation from iteration logic
- Handles retries for network errors (not score improvement)
- Makes `callClaudeForTailoring()` cleaner

**Insert after `callClaudeForTailoring()` (around line 183):**

```java
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

            // Strip markdown code fences
            String cleanedJson = contentText
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("\\s*```$", "")
                    .trim();

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

**This is essentially the EXISTING code** from `callClaudeForTailoring()`, just extracted into its own method.

---

### Method 3: Rename `buildOptimizedPrompt()` → `buildInitialPrompt()`

**Simple rename for clarity:**

**Line 188 BEFORE:**
```java
private String buildOptimizedPrompt(Job job) {
```

**Line 188 AFTER:**
```java
private String buildInitialPrompt(Job job) {
```

**No other changes** to this method - the prompt content stays the same.

---

### Method 4: Add `buildImprovementPrompt()` (NEW METHOD)

**Insert after `buildInitialPrompt()` (around line 247):**

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
        7. If missing Docker/K8s, emphasize related: "containerization", "deployment automation"
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

**What this does:**
1. Shows Claude its previous score and bullets
2. Explains why score is low
3. Gives specific improvement instructions
4. References job details again
5. Sets aggressive tone to push for 85+

---

### Method 5: Update `getMockTailoringResponse()` (Lines 352-375)

**Change mock to return 85+ scores for testing:**

**BEFORE:**
```java
// Vary ATS score based on job company hash for realistic testing
int atsScore = 75 + (Math.abs(job.getCompany().hashCode()) % 20);  // 75-94
```

**AFTER:**
```java
// Vary ATS score based on job company hash for realistic testing
// Now target 85+ instead of 75+
int atsScore = 80 + (Math.abs(job.getCompany().hashCode()) % 15);  // 80-94
```

**Also update mock bullets to include more keywords:**

```java
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
```

---

## Summary of Changes

### Single File Modified
- ✅ `src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java`

### Changes Breakdown

| Change Type | Method/Constant | Action | Lines |
|------------|----------------|--------|-------|
| **Constants** | `PROMPT_VERSION` | Update to "4.0-iterative-85" | Line 32 |
| **Constants** | `MAX_RETRIES` | Rename to `MAX_ITERATIONS`, value 4 | Line 33 |
| **Constants** | `TARGET_ATS_SCORE` | Add new constant, value 85 | Line 34 |
| **Method** | `callClaudeForTailoring()` | **Complete rewrite** with iteration loop | Lines 123-182 |
| **Method** | `invokeClaude()` | **New method** - extracted API call logic | Insert ~183 |
| **Method** | `buildOptimizedPrompt()` | **Rename** to `buildInitialPrompt()` | Line 188 |
| **Method** | `buildImprovementPrompt()` | **New method** - builds improvement prompt | Insert ~247 |
| **Method** | `getMockTailoringResponse()` | Update mock score range (80-94) | Lines 352-375 |

**Estimated new file size:** ~520 lines (+144 lines)

---

## Testing Approach

### Phase 1: Dev Mock Mode (No API Cost)

**Enable mock:**
```properties
# application-dev.properties
ai.dev.mock.enabled=true
```

**Test 1: Single job with high mock score (should pass iteration 1)**
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

**Verify:**
- ✅ Only 1 iteration
- ✅ Stops at 87 (≥ 85)
- ✅ No API cost

---

**Test 2: Force low score (test iteration loop)**

**Temporarily modify mock** (line ~357):
```java
int atsScore = 70;  // Force low score
```

**Run again, expected logs:**
```
Iteration 1 - ATS Score: 70
⚠️ ATS score 70 below target (85). Requesting improvement (iteration 2/4)
Iteration 2 - ATS Score: 70
⚠️ ATS score 70 below target (85). Requesting improvement (iteration 3/4)
Iteration 3 - ATS Score: 70
⚠️ ATS score 70 below target (85). Requesting improvement (iteration 4/4)
Iteration 4 - ATS Score: 70
⚠️ Could not reach 85% ATS after 4 iterations. Final score: 70
Tailored resume saved with ATS score: 70
```

**Verify:**
- ✅ All 4 iterations run
- ✅ Doesn't give up after 1 try
- ✅ Logs show progression
- ✅ Final warning about not reaching target
- ✅ Still saves resume (best attempt)

---

**Test 3: Force progressive improvement**

**Modify mock to simulate improvement:**
```java
// In getMockTailoringResponse()
// Simulate improvement over iterations (if we had iteration counter)
int atsScore = 70 + (iteration * 5);  // 70, 75, 80, 85
```

**Note:** Mock doesn't have access to iteration number, so this requires passing iteration or using a class-level counter. For now, just verify loop logic works.

---

### Phase 2: Real API Test (Small Scale)

**Disable mock:**
```properties
ai.dev.mock.enabled=false
```

**Test with 1-2 real jobs:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Monitor:**
1. AWS Bedrock invocations (check CloudWatch)
2. Iteration counts per job
3. Final ATS scores
4. Cost per resume
5. Total processing time

**Expected:**
- Most jobs: 1-2 iterations
- Some jobs: 3-4 iterations
- Final scores: 85-94 range
- Cost: $0.013-$0.033 per resume

---

### Phase 3: Verify MongoDB Data

**Check saved resumes:**
```javascript
db.tailored_resumes.find({
  claudePromptVersion: "4.0-iterative-85"
}).sort({ tailoredAt: -1 }).limit(5).pretty()
```

**Verify:**
- ✅ `claudePromptVersion` = "4.0-iterative-85"
- ✅ `atsScore` ≥ 85 (or close)
- ✅ `atsReasoning` has detailed explanation
- ✅ `latexSource` has tailored content
- ✅ `pdfBytes` exists

---

### Phase 4: Check Telegram Notifications

**Verify messages show:**
- ✅ ATS score (should be 85+)
- ✅ ATS reasoning
- ✅ PDF attached
- ✅ Job details correct

---

## Rollback Plan

**If anything goes wrong:**

```bash
# Create backup first
cp src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java \
   src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java.backup

# If need to rollback
git checkout HEAD -- src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java

# Rebuild
./mvnw clean install
```

**Or restore from backup:**
```bash
mv src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java.backup \
   src/main/java/com/jobtracker/jobtracker/service/ResumeTailoringService.java
```

---

## Success Criteria

✅ **Code complete** when:
1. All 5 changes implemented
2. Compiles without errors
3. No new warnings

✅ **Testing complete** when:
1. Mock mode test passes (iteration 1 success)
2. Mock mode test passes (4 iterations, low score)
3. Real API test completes (1-2 jobs)
4. MongoDB data correct
5. Telegram notifications working

✅ **Production ready** when:
1. All tests pass
2. Logs show expected behavior
3. ATS scores improved (85+ target hit)
4. Cost within budget ($0.53/month estimate)
5. No errors over 24 hours

---

## Post-Implementation Monitoring

**After 1 week, run analytics:**

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
      above85: { $sum: { $cond: [{ $gte: ["$atsScore", 85] }, 1, 0] } }
    }
  }
])
```

**Expected results:**
- `count`: ~25 resumes
- `avgATS`: 87-92
- `above85`: 19-22 (75-85% success rate)

**Check AWS billing:**
- Bedrock charges: ~$0.50-$0.60
- Total AI cost: ~$2.30-$2.50/month

---

## Timeline

| Phase | Duration | Activity |
|-------|----------|----------|
| **Implementation** | 30 min | Make code changes |
| **Compilation** | 2 min | `mvn clean install` |
| **Mock Test 1** | 5 min | High score test |
| **Mock Test 2** | 5 min | Low score test |
| **Real API Test** | 10 min | 2 real jobs |
| **Verification** | 10 min | Check MongoDB, Telegram |
| **Total** | ~1 hour | End-to-end |

---

## What You'll Review

After I implement, you'll verify:

1. ✅ **Code changes** look correct
2. ✅ **Compilation** succeeds
3. ✅ **Mock tests** pass
4. ✅ **Logs** show iteration behavior
5. ✅ **Real API test** works (1-2 jobs)

Then you decide:
- ✅ Deploy to production
- 🔄 Adjust something
- ❌ Rollback

---

## Questions Before I Start?

Before I implement, confirm:

1. ✅ Only modify `ResumeTailoringService.java`?
2. ✅ Target ATS score = 85% (not 80%)?
3. ✅ Max iterations = 4?
4. ✅ Use dev mock mode for initial testing?
5. ✅ You'll test with real API after mock tests pass?

**Ready to proceed with Option A?** 🚀
