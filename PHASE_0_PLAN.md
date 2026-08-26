# JobTracker — Phase 0: Foundation & Trust (Revised)

**Goal:** Fix config drift, fail fast on bad configuration, make the pipeline observable, and lock in regression coverage — **before** building RecruiterFit (Phase 1).

**Scope:** Backend only. No matching logic changes. No RecruiterFit. No skipped-job TTL.

**Status:** Approved for implementation  
**Last updated:** 2026-08-16

---

## Why Phase 0 exists

Phase 0 is not "cleanup for cleanup's sake." It creates a **stable, trustworthy baseline** so that when RecruiterFit ships, you can tell the difference between:

- **Intentional change** — fewer false positives, alerts drop by design
- **Accidental breakage** — config typo, wrong threshold, silent misconfig

Without Phase 0, regression tests encode bugs, logs are unreadable under concurrency, and bad env vars only show up as "why are alerts weird?"

---

## What changed from the original plan

| Original | Revised | Reason |
|----------|---------|--------|
| 0.7 Skipped-job TTL (90 days) | **Deferred** to post–Phase 1 | Need skip history to compare old pipeline vs RecruiterFit |
| 0.8 Structured logging | **0.8 MDC correlation logging** | JD fetch runs 1–3 threads per provider; logs interleave without `cycleId` / `jobId` |
| — | **0.9 Skip audit trail** | Stable `skipReason` constants, query index, `pipelineVersion` |
| — | **0.11 Configuration validation** | Fail at startup on invalid thresholds / missing secrets |
| — | **0.10 Baseline regression tests** | After config fixes; narrow unit tests, not full golden dataset |
| Tests in Phase 6 only | **Tests in Phase 0** | Catch alert-drop regressions before RecruiterFit |

---

## Build order (strict)

Do tasks in this order. Later steps depend on earlier ones.

```
Step 1 — Config unification (0.1 → 0.4)
Step 2 — Externalize scheduler interval (0.5)
Step 3 — Security & hygiene (0.6, 0.7)
Step 4 — Configuration validation (0.11)
Step 5 — Skip audit trail (0.9)
Step 6 — Correlation logging (0.8)
Step 7 — Telegram consolidation (0.10)
Step 8 — Sanity check (manual one cycle)
Step 9 — Baseline regression tests (0.12)
```

**Rule:** Do not write regression tests until Steps 1–4 are done. Tests must target **correct** behavior, not today's bugs.

---

## Task checklist

### 0.1 — Unify experience config

**Problem:** `ExperienceFilterService` hardcodes `CANDIDATE_EXPERIENCE = 3` while `candidate.experience=3.6` in properties.

**Fix:**
- Inject `CandidateProfile` into `ExperienceFilterService`
- Use `candidateProfile.getExperience()` for all experience checks
- Remove hardcoded `3` and `MAX_ACCEPTABLE_MIN = 4` — derive from profile or new properties (see 0.3)

**Files:**
- `ExperienceFilterService.java`
- `CandidateProfile.java` (if adding `maxMinExperience`)
- `application.properties`, `application-dev.properties`, `application-prod.properties`

**Verify:** Job requiring 4 years min → pass for 3.6 yrs. Job requiring 5+ years → reject.

---

### 0.2 — Wire resume tailoring properties

**Problem:** `JobService` hardcodes `50.0` for tailoring threshold. Properties define `resume.tailoring.min-score=70` and `resume.tailoring.enabled` but code ignores them.

**Fix:**
- `@Value("${resume.tailoring.enabled:true}")` — skip tailoring entirely when false
- `@Value("${resume.tailoring.min-score:70}")` — use for tailored vs general resume branch
- Remove all hardcoded `50.0` tailoring thresholds in `JobService`
- Update `TelegramService` / `GeneralResumeService` comments that reference 50%

**Files:**
- `JobService.java`
- `TelegramService.java`
- `GeneralResumeService.java`

**Verify:** With `min-score=70`, score 65 → general resume only. Score 75 → tailored resume.

---

### 0.3 — Single notify threshold

**Problem:** Multiple overlapping thresholds (`ai.bedrock.min-local-score`, hardcoded 50, future `notify-min-score`) with unclear semantics.

**Fix:**
- Add `candidate.notify-min-score` (default `65`) to properties and `CandidateProfile`
- Define clear semantics:

| Property | Purpose |
|----------|---------|
| `ai.bedrock.min-local-score` | Gate **before** Bedrock (cost control) |
| `candidate.notify-min-score` | Gate **Telegram alert** (user-facing) |
| `resume.tailoring.min-score` | Gate **tailored resume** (cost control) |

- Align `application.properties` with dev/prod (`candidate.experience=3.6`)
- Document the three thresholds in a comment block in `application.properties`

**Files:**
- `CandidateProfile.java`
- `application*.properties`
- `JobService.java` (use `notify-min-score` when deciding to call `sendTelegram`)

**Verify:** Job with AI score 60 → saved to DB, no Telegram. Score 70 → Telegram sent.

---

### 0.4 — Fix mock AI response consistency

**Problem:** `JdExtractionService` mock path may use inconsistent field names (`score` vs `matchScore` / `aiMatchScore`), causing flaky dev behavior.

**Fix:**
- Audit mock response in `JdExtractionService` and `ResumeTailoringService`
- Ensure mock sets the same fields the real Bedrock path sets
- `aiMatchScore` populated consistently on `Job` after extraction

**Files:**
- `JdExtractionService.java`
- `ResumeTailoringService.java` (mock path only)

**Verify:** With `ai.dev.mock.enabled=true`, a job flows through to `saveMatchedJob` with a valid `aiMatchScore`.

---

### 0.5 — Externalize scheduler interval

**Problem:** Polling interval is hardcoded in `JobScheduler` (`fixedDelay = 300000`). Cannot validate or change without recompile.

**Fix:**
- Add `job.scheduler.fixed-delay-ms` (default `300000`)
- Add `job.scheduler.initial-delay-ms` (default `10000`)
- Use `@Scheduled(fixedDelayString = "${job.scheduler.fixed-delay-ms:300000}", ...)`
- Enables validation in 0.11

**Files:**
- `JobScheduler.java`
- `application*.properties`

**Verify:** App starts; scheduler runs at configured interval.

---

### 0.6 — Remove secrets from committed config

**Problem:** `application.properties` has dummy AWS keys. Dev file is mostly clean; prod should never embed real secrets.

**Fix:**
- Ensure all AWS/Telegram values use `${ENV_VAR:}` with **empty** defaults in committed files
- Confirm `.env.example` documents all required variables
- No real tokens or keys in git

**Files:**
- `application.properties`
- `application-dev.properties`
- `application-prod.properties`
- `.env.example`

**Verify:** Fresh clone without env vars → validation fails with clear message (after 0.11), not silent dummy-key Bedrock calls.

---

### 0.7 — Align Bedrock model IDs

**Problem:** Dev and prod may use different model IDs or token limits without documentation.

**Fix:**
- Use same default model ID across `application-dev.properties` and `application-prod.properties`
- Document in comment if prod intentionally differs (e.g. cost vs quality)
- Align `max.tokens` defaults or document why they differ

**Files:**
- `application-dev.properties`
- `application-prod.properties`

**Verify:** Same model ID in both unless explicitly documented exception.

---

### 0.8 — Correlation logging (MDC)

**Problem:** Per-provider JD fetch uses 1–3 threads. Logs from `processJob`, Bedrock, tailoring, and Telegram interleave. Impossible to trace one job end-to-end.

**Fix:**

Introduce two-level correlation via SLF4J MDC:

| MDC key | Scope | Example |
|---------|-------|---------|
| `cycleId` | One scheduler run (`checkJobs()`) | `a1b2c3d4` |
| `jobId` | One job through pipeline | `visa_R12345` |
| `provider` | Company name | `Visa` |
| `stage` | Current pipeline step | `JD_FETCH`, `LOCAL_MATCH`, `BEDROCK`, `TAILOR`, `TELEGRAM` |

**Implementation:**
1. Create `TracingContext` helper:

```java
public final class TracingContext {
    public static void setCycleId(String cycleId) { MDC.put("cycleId", cycleId); }
    public static void setJobContext(String jobId, String provider) { ... }
    public static void setStage(String stage) { MDC.put("stage", stage); }
    public static void clear() { MDC.clear(); }
    public static <T> T runWithJob(String jobId, String provider, String stage, Callable<T> work) { ... }
}
```

2. `JobService.checkJobs()` — generate `cycleId` at start, clear at end
3. JD fetch executor — wrap each task with `runWithJob()`; **always `finally { MDC.clear() }`**
4. Set `stage` at each pipeline transition in `processJob` and `saveMatchedJob`
5. Update `logback-spring.xml` (or `application.properties` logging pattern):

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] [%X{cycleId}] [%X{jobId}] [%X{stage}] %-5level %logger{36} - %msg%n</pattern>
```

**Files:**
- New: `config/TracingContext.java` or `util/TracingContext.java`
- `JobService.java`
- `JdExtractionService.java`, `ResumeTailoringService.java`, `TelegramService.java` (set `stage` at entry)
- `src/main/resources/logback-spring.xml` (create if missing)

**Do not:** Add OpenTelemetry, JSON logging, or external tracing backends in Phase 0.

**Verify:** Run one cycle with 2+ new jobs. `grep cycleId=xxx` shows ordered stages per job without cross-contamination.

---

### 0.9 — Skip audit trail (replaces TTL)

**Problem:** We need to compare skip behavior before and after RecruiterFit. TTL would delete evidence.

**Fix:**

1. **Stable skip reason constants** — create `SkipReason` enum or constants class:

```java
public final class SkipReason {
    public static final String TITLE_FILTER = "TITLE_FILTER";
    public static final String EMPTY_DESCRIPTION = "EMPTY_DESCRIPTION";
    public static final String REGEX_EXPERIENCE_FILTER = "REGEX_EXPERIENCE_FILTER";
    public static final String LOW_LOCAL_SCORE = "LOW_LOCAL_SCORE";
    public static final String STRUCTURED_EXPERIENCE_FILTER = "STRUCTURED_EXPERIENCE_FILTER";
    // Reserved for Phase 1:
    public static final String RECRUITER_GATE = "RECRUITER_GATE";
    public static final String AI_REJECT = "AI_REJECT";
}
```

2. **Pipeline version on Job** — add field `pipelineVersion` (e.g. `"skill-matcher-v1"`). Set on every save (matched and skipped). After RecruiterFit ships, new jobs get `"recruiter-fit-v1"`.

3. **MongoDB index for analysis** (not TTL):

```javascript
db.jobs.createIndex({ "skipped": 1, "skipReason": 1, "skippedAt": -1 })
db.jobs.createIndex({ "pipelineVersion": 1, "skipped": 1 })
```

4. **Deferred:** Skipped-job TTL → add in Phase 6 (post-calibration) or when collection size becomes a problem.

**Files:**
- New: `model/SkipReason.java`
- `Job.java` — add `pipelineVersion`
- `JobService.java` — use constants; set `pipelineVersion`
- `MongoIndexConfig.java` — add query indexes

**Verify:**
```javascript
db.jobs.aggregate([
  { $match: { skipped: true } },
  { $group: { _id: "$skipReason", count: { $sum: 1 } } }
])
```

---

### 0.10 — Consolidate Telegram

**Problem:** Alert logic split between `JobService.sendTelegram()` and `TelegramService` (PDF delivery).

**Fix:**
- Move `JobService.sendTelegram()` into `TelegramService` as `sendJobAlert(Job job)`
- `JobService` calls `telegramService.sendJobAlert(matched)` only
- Set `TailoredResume.sentToUser = true` when PDF is successfully sent
- No message format redesign yet — that's Phase 1

**Files:**
- `JobService.java`
- `TelegramService.java`
- `TailoredResume.java` / `TelegramService.java`

**Verify:** New matched job still receives text alert + resume PDF. `sentToUser` is true after send.

---

### 0.11 — Configuration validation

**Problem:** Invalid config causes silent misbehavior (zero alerts, all alerts, wrong filtering).

**Fix:**

1. Add dependency: `spring-boot-starter-validation`

2. Add `@Validated` + constraints on `CandidateProfile`:

```java
@Validated
@ConfigurationProperties(prefix = "candidate")
public class CandidateProfile {
    @Positive
    private double experience;

    @Min(0) @Max(100)
    private int notifyMinScore;
    // ...
}
```

3. Create `JobPipelineProperties` (or extend existing config) for:

```java
@Min(0) @Max(100) int minLocalScoreForBedrock;
@Min(0) @Max(100) int resumeTailoringMinScore;
@Min(1) long schedulerFixedDelayMs;
```

4. Create `ConfigurationValidator` (`@Component`) for conditional rules:

| Rule | Error message |
|------|---------------|
| `candidate.experience > 0` | `candidate.experience must be positive` |
| `candidate.notify-min-score` in 0–100 | `candidate.notify-min-score must be between 0 and 100` |
| `resume.tailoring.min-score` in 0–100 | `resume.tailoring.min-score must be between 0 and 100` |
| `ai.bedrock.min-local-score` in 0–100 | `ai.bedrock.min-local-score must be between 0 and 100` |
| `job.scheduler.fixed-delay-ms > 0` | `job.scheduler.fixed-delay-ms must be positive` |
| `ai.bedrock.enabled && !ai.dev.mock.enabled` → model ID non-blank | `aws.bedrock.model.id required when Bedrock enabled` |
| Same condition → AWS keys non-blank, not `dummy-*` | `AWS credentials required when Bedrock enabled` |
| `telegram.bot.token` non-blank | `telegram.bot.token is required` |
| `telegram.chat.id` non-blank | `telegram.chat.id is required` |
| Optional: `resume.tailoring.min-score >= candidate.notify-min-score` | Warn or fail — tailoring threshold should not exceed notify threshold |

5. Fail at startup with clear message. No silent fallbacks.

**Files:**
- `pom.xml`
- `CandidateProfile.java`
- New: `config/JobPipelineProperties.java`
- New: `config/ConfigurationValidator.java`

**Verify:**
- `candidate.notify-min-score=150` → app fails to start
- `candidate.experience=-1` → app fails to start
- Valid config → app starts normally

---

### 0.12 — Baseline regression tests

**Problem:** Only `contextLoads()` exists. RecruiterFit could drop alerts 70% with no safety net.

**Scope:** Narrow unit tests on current pipeline logic. **Not** a 20–30 job golden dataset.

**Prerequisite:** 0.1–0.4 and 0.11 complete.

#### Test classes

| Class | Cases | What it protects |
|-------|-------|------------------|
| `ExperienceFilterServiceTest` | ~8–10 | Title gate, regex experience, structured experience |
| `SkillMatcherServiceTest` | ~6–8 | Score ordering: Java backend JD > frontend JD |
| `JobServicePipelineTest` | ~4–6 | Notify/skip branches with mocked repo, Bedrock, Telegram |
| `ConfigurationValidatorTest` | ~4–6 | Invalid properties → startup failure |
| `SkipReasonTest` (optional) | ~2 | Constants used consistently |

#### JobServicePipelineTest approach

Mock:
- `JobRepository`
- `JdExtractionService`
- `TelegramService`
- `ResumeTailoringService`
- `ExperienceFilterService` / `SkillMatcherService` (or use real ones for integration-style tests)

Assert:
- Already-seen job → not saved again
- Low local score → `saveSkippedJob(LOW_LOCAL_SCORE)`, no Telegram
- High score, above notify threshold → Telegram called
- Score above tailoring min → `tailorResume` called
- `resume.tailoring.enabled=false` → no tailoring

#### Optional smoke counter

5–10 inline job fixtures in one test:

```text
Given 8 fixture jobs + test profile → expect 3 NOTIFY, 5 SKIP
```

Update this counter deliberately when RecruiterFit ships.

#### Test config

`src/test/resources/application-test.properties`:

```properties
candidate.experience=3.6
candidate.notify-min-score=65
resume.tailoring.min-score=70
ai.bedrock.enabled=false
ai.dev.mock.enabled=true
```

**Files:**
- `src/test/java/.../ExperienceFilterServiceTest.java`
- `src/test/java/.../SkillMatcherServiceTest.java`
- `src/test/java/.../JobServicePipelineTest.java`
- `src/test/java/.../ConfigurationValidatorTest.java`
- `src/test/resources/application-test.properties`
- `src/test/resources/fixtures/` — small inline JSON snippets only

**Verify:** `mvn test` passes. Breaking notify logic fails CI.

---

## Manual sanity check (between 0.11 and 0.12)

Before writing tests, run one real cycle:

1. Set `ai.dev.mock.enabled=true` locally
2. Trigger `checkJobs()` (wait for scheduler or call manually)
3. Confirm in logs:
   - `cycleId` present on all lines
   - `jobId` present per job
   - Stages progress logically
4. Confirm in MongoDB:
   - Skipped jobs have `skipReason` + `pipelineVersion`
   - Matched jobs respect notify/tailoring thresholds
5. Fix any surprises **before** locking in test expectations

---

## Properties reference (target state)

```properties
# Candidate
candidate.experience=3.6
candidate.notify-min-score=65
candidate.skills=Java,Spring Boot,...
candidate.roles=Software Engineer,Backend Engineer,...

# Pipeline thresholds (three distinct purposes)
ai.bedrock.min-local-score=20          # Pre-Bedrock cost gate
candidate.notify-min-score=65            # Telegram alert gate
resume.tailoring.min-score=70          # Tailored resume gate
resume.tailoring.enabled=true

# Scheduler
job.scheduler.fixed-delay-ms=300000
job.scheduler.initial-delay-ms=10000

# AI
ai.bedrock.enabled=true
ai.dev.mock.enabled=false                # true in dev

# Pipeline version (set in code, not properties)
# job.pipeline-version=skill-matcher-v1
```

---

## Phase 0 exit criteria

All must be true before starting Phase 1 (RecruiterFit):

- [ ] No hardcoded experience, notify, or tailoring thresholds in Java
- [ ] Invalid config fails at startup with a clear error message
- [ ] Logs traceable per job via `cycleId` + `jobId` + `stage`
- [ ] Skipped jobs retained with stable `skipReason` + `pipelineVersion` + query indexes
- [ ] Telegram consolidated in `TelegramService`; `sentToUser` set on send
- [ ] `mvn test` passes with baseline regression suite
- [ ] One manual cycle confirms behavior matches expectations
- [ ] No secrets in committed property files

---

## Explicitly out of scope for Phase 0

| Item | When |
|------|------|
| `RecruiterFitService` | Phase 1 |
| Skipped-job TTL | Phase 6 or post-calibration |
| Full golden dataset (20–30 labeled jobs) | Phase 6 calibration |
| Recruiter-style Telegram format | Phase 1 |
| REST API beyond `/health` | Phase 4 |
| OpenTelemetry / distributed tracing | Not needed for single JVM |
| Provider expansion | Phase 5 |
| `PriorityJobQueue` | Phase 2 |

---

## Dependency diagram

```
0.1 Config: experience
0.2 Config: tailoring props     ─┐
0.3 Config: notify threshold   ─┼─▶ 0.11 Config validation ─▶ 0.12 Tests
0.4 Mock AI fix               ─┤
0.5 Scheduler externalized    ─┘
0.6 Secrets cleanup
0.7 Model ID alignment
        │
        ▼
0.9 Skip audit trail
0.8 MDC correlation logging
0.10 Telegram consolidation
        │
        ▼
   Manual sanity check
        │
        ▼
0.12 Baseline regression tests
        │
        ▼
   ✅ Phase 0 complete → Phase 1 RecruiterFit
```

---

## Estimated effort

| Group | Tasks | Notes |
|-------|-------|-------|
| Config (0.1–0.5) | Small, focused diffs | Highest priority |
| Hygiene (0.6–0.7) | Trivial | |
| Observability (0.8–0.9) | Medium | MDC + indexes |
| Validation (0.11) | Small–medium | One new dependency |
| Telegram (0.10) | Small refactor | |
| Tests (0.12) | Medium | ~4 test classes, no golden dataset |

---

## After Phase 0

Phase 1 starts with `RecruiterFitService`. Phase 0 gives you:

1. **Config you can trust** — single source of truth, validated at startup
2. **Logs you can read** — grep one `jobId`, see the full story
3. **Skip data you can compare** — `pipelineVersion` + `skipReason` before/after RecruiterFit
4. **Tests that scream** — if alerts drop 70%, CI fails or you update tests on purpose

---

*This document supersedes the original Phase 0 section in BACKEND_MASTER_PLAN for implementation purposes. Update the master plan when Phase 0 is complete.*
