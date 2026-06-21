# Resume Tailoring Synchronization Fix

## Problem Identified

**Great observation!** You noticed from logs that one job's resume was still being tailored when another job started processing.

### Why This Happened

**Parallel JD Fetching:**
```java
// JobService.java line 105-134
ExecutorService executor = Executors.newFixedThreadPool(3);  // 3 threads

for (Job job : newJobs) {
    executor.submit(() -> {
        // Thread 1: Fetch JD for Job A
        // Thread 2: Fetch JD for Job B  
        // Thread 3: Fetch JD for Job C
        
        processJob(provider, jobRef);  // Each thread processes independently
    });
}
```

**What This Caused:**

```
Time 0s:  Thread 1 → Job A → Resume tailoring starts (AWS Bedrock call)
Time 1s:  Thread 2 → Job B → Resume tailoring starts (AWS Bedrock call) ❌ CONFLICT
Time 2s:  Thread 3 → Job C → Resume tailoring starts (AWS Bedrock call) ❌ CONFLICT
```

---

## Issues Caused by Concurrent Tailoring

### 1. AWS Bedrock Rate Limits

**Problem:**
- AWS Bedrock has rate limits (e.g., 5 requests/second)
- 3 concurrent requests could trigger throttling
- `ThrottlingException` → Tailoring fails → Falls back to general resume

**Impact:**
- Lost opportunity to send tailored resume
- Poor user experience (inconsistent results)

---

### 2. LaTeX Compilation Conflicts

**Problem:**
```bash
# All 3 threads write to the same directory:
/tmp/jobtracker/latex/resume_amazon_12345.tex
/tmp/jobtracker/latex/resume_google_67890.tex
/tmp/jobtracker/latex/resume_microsoft_abcde.tex

# If filenames collide or tmp cleanup races:
pdflatex resume.tex  (Thread 1)
pdflatex resume.tex  (Thread 2)  ❌ FILE CONFLICT
pdflatex resume.tex  (Thread 3)  ❌ FILE CONFLICT
```

**Impact:**
- LaTeX compilation errors
- Corrupted PDF files
- "File in use" errors on Windows

---

### 3. Confusing Interleaved Logs

**Before fix:**
```
Generating AI-tailored resume for: Software Engineer at Amazon (AI Score: 87%)
Generating AI-tailored resume for: Java Developer at Google (AI Score: 82%)
Iteration 1 - ATS Score: 72
Iteration 1 - ATS Score: 78
⚠️ ATS score 72 below target. Requesting improvement...
⚠️ ATS score 78 below target. Requesting improvement...
Iteration 2 - ATS Score: 81
Iteration 2 - ATS Score: 85
✅ Target reached: 81
✅ Target reached: 85
Tailored resume saved with ATS score: 81
Tailored resume saved with ATS score: 85
```

**Problem:** Which job got which score? Impossible to tell! ❌

---

### 4. Memory Spikes

**Problem:**
- Each tailoring process:
  - Loads base resume (~100 KB)
  - Calls Claude API (sends ~1500 tokens)
  - Generates LaTeX (~120 KB)
  - Compiles PDF (~95 KB)
  - Holds PDF in memory for Telegram upload

**3 concurrent processes:**
- Memory usage: 3 × 300 KB = ~900 KB (plus LaTeX temp files)
- On low-memory systems (e.g., 512 MB containers), this could cause swapping

---

## The Solution: Synchronized Resume Tailoring

### Change 1: Added Static Lock

```java
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    // NEW: Lock for synchronizing resume tailoring across threads
    private static final Object RESUME_TAILORING_LOCK = new Object();
    
    ...
}
```

**Why static?**
- Ensures ALL threads across the entire JVM share the same lock
- Even if multiple scheduled jobs run simultaneously, they wait for each other

---

### Change 2: Wrapped Tailoring in Synchronized Block

```java
if (aiScore >= 50.0) {
    // CRITICAL: Synchronize resume tailoring to prevent concurrent Bedrock calls
    // and LaTeX compilation conflicts. This ensures one resume is fully generated
    // before the next one starts.
    synchronized (RESUME_TAILORING_LOCK) {
        try {
            log.info("🔒 Starting resume tailoring for: {} (AI Score: {}%)",
                    matched.getTitle(), aiScore);

            var tailoredResume = resumeTailoringService.tailorResume(matched);
            telegramService.sendTailoredResume(matched, tailoredResume);

            log.info("✅ Tailored resume sent | ATS Score: {} | Cost: $0.004",
                    tailoredResume.getAtsScore());
        } catch (Exception e) {
            log.error("❌ Tailoring failed: {}", e.getMessage());
            telegramService.sendGeneralResume(matched, "AI tailoring failed");
        }
    }
}
```

**How it works:**
1. Thread 1 enters synchronized block → Acquires lock
2. Thread 2 tries to enter → **BLOCKS** (waits for Thread 1 to finish)
3. Thread 3 tries to enter → **BLOCKS** (waits for Thread 1 and 2)
4. Thread 1 finishes → Releases lock
5. Thread 2 acquires lock → Starts tailoring
6. Thread 2 finishes → Releases lock
7. Thread 3 acquires lock → Starts tailoring

---

## New Behavior (After Fix)

### Flow Diagram:

```
Time 0s:  Thread 1 → Job A → 🔒 Acquires lock → Resume tailoring (AWS + LaTeX)
Time 1s:  Thread 2 → Job B → ⏳ Waits for lock
Time 2s:  Thread 3 → Job C → ⏳ Waits for lock
Time 10s: Thread 1 → Job A → ✅ Tailoring done → Releases lock
Time 10s: Thread 2 → Job B → 🔒 Acquires lock → Resume tailoring
Time 15s: Thread 3 → Job C → ⏳ Still waiting
Time 20s: Thread 2 → Job B → ✅ Tailoring done → Releases lock
Time 20s: Thread 3 → Job C → 🔒 Acquires lock → Resume tailoring
Time 30s: Thread 3 → Job C → ✅ Tailoring done → Releases lock
```

**Key points:**
- Only ONE resume tailored at a time ✅
- Other threads wait their turn ✅
- Clean, sequential logs ✅

---

## Impact Analysis

### Pros (Benefits):

1. ✅ **No AWS rate limit issues**
   - Only 1 Bedrock call at a time
   - No throttling exceptions

2. ✅ **No LaTeX conflicts**
   - Only 1 pdflatex process at a time
   - No file collisions

3. ✅ **Clean logs**
   - Clear which job is being tailored
   - Easy to debug if issues occur

4. ✅ **Lower memory usage**
   - Peak memory: 1 × 300 KB instead of 3 × 300 KB
   - Better for low-memory environments

5. ✅ **More reliable**
   - Fewer race conditions
   - Consistent behavior

---

### Cons (Trade-offs):

1. ⚠️ **Slower overall processing**
   - Before: 3 resumes tailored in ~10 seconds (parallel)
   - After: 3 resumes tailored in ~30 seconds (sequential)
   - **Trade-off: Reliability > Speed**

2. ⚠️ **JD fetching still parallel**
   - Threads wait ONLY for resume tailoring, not JD fetching
   - JD fetching remains fast (parallel)

---

## Performance Comparison

### Scenario: 6 Matched Jobs (AI Score ≥ 50%)

**Before (Parallel):**
```
0-10s:   Jobs 1, 2, 3 tailor resumes (parallel)
10-20s:  Jobs 4, 5, 6 tailor resumes (parallel)
Total: 20 seconds ✅ Fast
Risk: 3 concurrent Bedrock calls + LaTeX processes ❌ Risky
```

**After (Sequential):**
```
0-10s:   Job 1 tailors resume
10-20s:  Job 2 tailors resume
20-30s:  Job 3 tailors resume
30-40s:  Job 4 tailors resume
40-50s:  Job 5 tailors resume
50-60s:  Job 6 tailors resume
Total: 60 seconds ⚠️ Slower
Risk: 1 Bedrock call + 1 LaTeX process at a time ✅ Safe
```

**Verdict:** Worth the trade-off! 40 extra seconds every 15 minutes is acceptable for reliability.

---

## Why This Is The Right Approach

### Alternative 1: Keep Parallel, Add Retry Logic

```java
// Retry on throttling
for (int i = 0; i < 3; i++) {
    try {
        return bedrockClient.invokeModel(...);
    } catch (ThrottlingException e) {
        Thread.sleep(2000 * i);  // Exponential backoff
    }
}
```

**Problems:**
- Still wastes Bedrock API quota on failed attempts
- Adds complexity (retry logic everywhere)
- Doesn't solve LaTeX conflicts

**Verdict:** ❌ Not as clean

---

### Alternative 2: Use Separate Temp Directories per Job

```java
String tempDir = "/tmp/jobtracker/latex/" + job.getId();
```

**Problems:**
- Solves LaTeX conflicts ✅
- Doesn't solve AWS rate limiting ❌
- More complex cleanup logic
- More disk I/O

**Verdict:** ⚠️ Partial solution, not complete

---

### Alternative 3: Synchronization (Our Choice)

```java
synchronized (RESUME_TAILORING_LOCK) {
    // Tailor one resume at a time
}
```

**Benefits:**
- ✅ Solves AWS rate limiting
- ✅ Solves LaTeX conflicts
- ✅ Clean logs
- ✅ Simple implementation (5 lines of code)
- ✅ Easy to understand
- ⚠️ Slightly slower (acceptable trade-off)

**Verdict:** ✅ Best solution

---

## Testing

### Test 1: Verify Synchronization Works

**Run the app with multiple jobs:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Check logs for:**
```
🔒 Starting resume tailoring for: Software Engineer at Amazon (AI Score: 87%)
Iteration 1 - ATS Score: 72
Iteration 2 - ATS Score: 81
✅ Target reached: 81
✅ Tailored resume sent | ATS Score: 81 | Cost: $0.004
🔒 Starting resume tailoring for: Java Developer at Google (AI Score: 82%)
Iteration 1 - ATS Score: 78
Iteration 2 - ATS Score: 85
✅ Target reached: 85
✅ Tailored resume sent | ATS Score: 85 | Cost: $0.004
```

**Expected:** Clean sequential logs, no interleaving ✅

---

### Test 2: Verify No Rate Limiting

**Before fix:**
```bash
grep "ThrottlingException\|Too Many Requests" jobtracker.log
# Expected: Some occurrences ❌
```

**After fix:**
```bash
grep "ThrottlingException\|Too Many Requests" jobtracker.log
# Expected: Zero occurrences ✅
```

---

### Test 3: Check Processing Time

**Before fix:**
- 6 jobs with AI score ≥ 50% → ~20-30 seconds total

**After fix:**
- 6 jobs with AI score ≥ 50% → ~60 seconds total

**Acceptable?** ✅ YES
- This happens every 15 minutes
- 40 extra seconds out of 900 seconds = 4.4% overhead
- Worth it for reliability

---

## Monitoring

### What to Monitor:

1. **Total tailoring time:**
   ```bash
   grep "Starting resume tailoring\|Tailored resume sent" jobtracker.log | \
     awk '{print $1, $2}' | \
     # Calculate time difference
   ```

   **Expected:** ~10 seconds per resume (sequential)

2. **Throttling errors:**
   ```bash
   grep -i "throttl" jobtracker.log
   ```

   **Expected:** Zero after fix ✅

3. **Lock contention:**
   ```bash
   grep "🔒 Starting resume tailoring" jobtracker.log | wc -l
   ```

   **Expected:** Number of tailored resumes (all sequential)

---

## Rollback

If synchronization causes issues:

**Step 1: Remove synchronized block**
```java
// Remove synchronized wrapper
if (aiScore >= 50.0) {
    try {
        var tailoredResume = resumeTailoringService.tailorResume(matched);
        // ...
    }
}
```

**Step 2: Recompile**
```bash
./mvnw compile
```

**Not recommended** - synchronization is the correct approach.

---

## Summary

✅ **Problem:** Multiple threads calling `tailorResume()` concurrently
✅ **Impact:** AWS rate limits, LaTeX conflicts, confusing logs, memory spikes
✅ **Solution:** Synchronized resume tailoring with static lock
✅ **Trade-off:** 40 seconds slower per run (6 jobs × 10s = 60s vs 20s)
✅ **Worth it?** YES - Reliability > Speed (4% overhead acceptable)

**Result:** Resume tailoring is now **sequential and reliable** ✅

---

**Your observation was spot-on!** This fix will make the system much more stable. 🎯

---

**Last Updated**: 2026-06-21  
**Version**: 4.1.3 (Resume Tailoring Synchronization)  
**Status**: ✅ Implemented and compiled successfully
