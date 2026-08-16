# JobTracker — Backend Master Plan

**Goal:** Help users get interview calls faster than applying blindly on LinkedIn, Naukri, or other job portals.

**Scope:** Backend only. Frontend/UI is out of scope for this plan and will be built later.

**Status:** Draft for review — read, challenge, and adjust before implementation.

**Last updated:** 2026-08-16 (Phase 0 revised)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Why This Can Beat Portals](#2-why-this-can-beat-portals)
3. [Current State (As-Is)](#3-current-state-as-is)
4. [North-Star Metrics](#4-north-star-metrics)
5. [Target Architecture](#5-target-architecture)
6. [Phased Implementation Plan](#6-phased-implementation-plan)
7. [Recruiter-Centric Matching (Deep Dive)](#7-recruiter-centric-matching-deep-dive)
8. [Telegram Notification Redesign](#8-telegram-notification-redesign)
9. [REST API (Future UI)](#9-rest-api-future-ui)
10. [Data Model Extensions](#10-data-model-extensions)
11. [Provider Expansion Strategy](#11-provider-expansion-strategy)
12. [Cost Model](#12-cost-model)
13. [What NOT to Build (Yet)](#13-what-not-to-build-yet)
14. [Technical Debt to Fix First](#14-technical-debt-to-fix-first)
15. [Risks & Honest Limitations](#15-risks--honest-limitations)
16. [6-Month Success Vision](#16-6-month-success-vision)
17. [Decision Checklist (Before You Commit)](#17-decision-checklist-before-you-commit)
18. [Recommended Build Order](#18-recommended-build-order)

---

## 1. Executive Summary

JobTracker today is a **personal job scraping + AI matching + Telegram notification** system. It works for 7 enterprise employers but stops at **alerting** — it does not close the loop to **apply → track → interview**.

This plan transforms it into an **interview-acceleration backend** by focusing on three advantages portals cannot match easily:

| Advantage | How |
|-----------|-----|
| **Precision** | Recruiter-centric scoring (not keyword matching) |
| **Speed** | Direct career-site APIs → alert within hours of posting |
| **Apply quality** | Tailored resume + cover letter + application CRM |

**If you only build 3 things first:**

1. `RecruiterFitService` — precision matching
2. Application + Interview CRM — track the funnel
3. 10+ more Workday/Oracle companies — volume

---

## 2. Why This Can Beat Portals

### What LinkedIn / Naukri do well

- Massive job volume (thousands of employers)
- Easy Apply / saved profiles
- Recruiter InMail and profile visibility
- Brand recognition and mobile apps
- Application tracking UI (basic)

### Where JobTracker can win

| Portal weakness | JobTracker strength |
|-----------------|---------------------|
| Spray & pray — too many irrelevant jobs | **Curated alerts** — only roles worth applying to |
| Jobs appear hours/days late on aggregators | **Direct employer APIs** — often first to see postings |
| Same generic resume for every application | **Per-job tailored resume** + ATS score |
| No fit signal until you waste time applying | **Recruiter-style verdict** before you apply |
| High competition (100s of applicants on popular roles) | Enterprise career pages often have **fewer early applicants** |
| No pipeline after "Apply" click | **Application + interview tracker** (backend CRM) |

### Reality check (read this before committing)

JobTracker will **never** beat LinkedIn on:

- Total job volume
- Recruiter inbound / InMail
- Network effects and brand

JobTracker wins for a **defined profile** (e.g. Java backend, 3–4 years, India, SDE2) on **speed + precision + apply quality** — not on being a general-purpose job portal.

---

## 3. Current State (As-Is)

### Pipeline today

```
JobScheduler (every 5 min)
    → JobService.checkJobs()
        → For each of 7 providers:
            → fetchJobs()
            → Skip if already in MongoDB (existsById)
            → STAGE 1: ExperienceFilterService.isTitleSuitable() → skip senior/manager titles
            → Fetch JD (concurrent, rate-limited) — except Amazon (JD in search response)
            → processJob():
                → Skip empty JD
                → STAGE 2a: regex experience filter (blocks 5+ years min)
                → SkillMatcherService.match() → localMatchScore
                → If Bedrock disabled → save + Telegram (local score only)
                → If localScore < minLocalScoreForBedrock → skip (save as LOW_LOCAL_SCORE)
                → JdExtractionService.extractStructuredData() → Bedrock AI
                → STAGE 2b: structured experience filter
                → SkillMatcherService.matchStructured()
                → saveMatchedJob():
                    → sendTelegram() (text alert)
                    → If score >= 50: ResumeTailoringService → tailored PDF via Telegram
                    → Else: GeneralResumeService → general PDF via Telegram
```

### Supported employers (7)

| Company | Platform | JD fetch | Notes |
|---------|----------|----------|-------|
| American Express | Oracle HCM | 300ms, 3 threads | |
| JPMorgan Chase | Oracle HCM | 300ms, 3 threads | Technology category |
| Barclays | TalentBrew | 300ms, 3 threads | HTML parsing |
| Goldman Sachs | GraphQL | 300ms, 3 threads | No posting date — DB dedup only |
| Microsoft | Careers API | 10s, 1 thread | Very strict rate limits |
| Visa | Workday | 500ms, 2 threads | |
| Amazon | Amazon Jobs API | None (instant) | Full JD in search response |

### Key services

| Service | Responsibility |
|---------|----------------|
| `JobScheduler` | Triggers pipeline every 5 minutes |
| `JobService` | Main orchestration, Telegram text alerts |
| `ExperienceFilterService` | Title + experience filtering |
| `SkillMatcherService` | Tier-weighted fuzzy skill matching (local + fallback) |
| `JdExtractionService` | Bedrock JD extraction + AI match score (prompt v2.0) |
| `ResumeTailoringService` | Iterative ATS resume tailoring (prompt v4.0) |
| `TelegramService` | PDF document delivery |
| `LaTeXCompilerService` | Local pdflatex + online fallback |
| `GeneralResumeService` | Cached general resume PDF |
| `DynamicJobProvider` | Routes to platform handlers (strategy pattern) |
| `ProviderRegistry` | All company configurations |

### MongoDB collections

| Collection | Purpose |
|------------|---------|
| `jobs` | All matched and skipped jobs with scores, skills, AI fields |
| `tailored_resumes` | LaTeX source, PDF bytes, ATS score (30-day TTL) |

### REST API today

- `GET /health` only — no job or application APIs yet

### Known gaps (from codebase audit)

| Gap | Impact |
|-----|--------|
| Matching is **technology-centric** (skill keyword overlap) | False positives: wrong role/level still alert |
| Stops at **notification** — no apply/interview loop | No interview acceleration after alert |
| `applied`, `sentToUser` fields exist but **unused** | No pipeline tracking |
| `ExperienceFilterService` hardcodes experience = 3 | Profile says 3.6 — mismatch |
| `resume.tailoring.min-score=70` in properties | Code hardcodes 50 — config ignored |
| `resume.tailoring.enabled` in properties | Not referenced in Java |
| Only 7 companies | Narrow funnel vs portals |
| No user feedback loop | Cannot improve precision over time |
| Telegram split across `JobService` and `TelegramService` | Maintenance burden |
| Skipped jobs stored forever | MongoDB bloat (TTL deferred until post–RecruiterFit calibration) |
| No integration tests for matching/filters | Regressions likely |
| `docs/` folder empty; CLAUDE.md partially stale | Planning drift |

---

## 4. North-Star Metrics

Track in MongoDB from Phase 1 onward:

| Metric | Definition | Target |
|--------|------------|--------|
| **Alert precision** | % of Telegram alerts user would consider applying to | > 70% |
| **Time-to-alert** | `postedAt` → `firstSeenAt` (where date known) | < 2 hours median |
| **Apply rate** | % of STRONG_SHORTLIST alerts → marked Applied | > 40% |
| **Interview rate** | % of Applied → at least one interview round | > 10% |
| **False positive rate** | Alerts immediately dismissed as irrelevant | < 15% |
| **Bedrock cost per notified job** | AWS spend / alerts sent | < ₹0.10 |
| **Local filter rejection rate** | % jobs rejected before Bedrock | > 60% (cost savings) |

---

## 5. Target Architecture

### High-level system diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         INGESTION LAYER                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ Oracle   │ │ Workday  │ │ GraphQL  │ │ Microsoft│ │ Amazon   │   │
│  │ HCM      │ │          │ │ (GS)     │ │ Careers  │ │ Jobs     │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘   │
│       └────────────┴────────────┴────────────┴────────────┘           │
│                              │                                           │
│                    ┌─────────▼─────────┐                                │
│                    │  Priority Queue    │  ← fresh jobs first          │
│                    │  (posted today)    │                                │
│                    └─────────┬─────────┘                                │
└──────────────────────────────┼──────────────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────────────┐
│                    RECRUITER FIT ENGINE (NEW)                            │
│                                                                          │
│  ┌─────────────┐   ┌──────────────────┐   ┌─────────────────────────┐  │
│  │ Role/Title  │──▶│ RecruiterFit     │──▶│ Bedrock Recruiter       │  │
│  │ Gate        │   │ Service (local)  │   │ Analysis v3.0           │  │
│  └─────────────┘   └──────────────────┘   └─────────────────────────┘  │
│         │                    │                         │               │
│         │ hard reject        │ score < 35 → skip        │ REJECT/HOLD  │
│         ▼                    ▼                         ▼               │
│     saveSkippedJob()     saveSkippedJob()          saveSkippedJob()     │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ SHORTLIST / STRONG_SHORTLIST
┌──────────────────────────────▼──────────────────────────────────────────┐
│                         ACTION LAYER                                     │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────────┐  │
│  │ AlertService │  │ ResumeService│  │ ApplicationPrepService     │  │
│  │ (Telegram)   │  │ tailor/general│  │ cover letter, bullets,     │  │
│  └──────┬───────┘  └──────┬───────┘  │ apply checklist            │  │
│         │                 │          └─────────────┬──────────────┘  │
└─────────┼─────────────────┼────────────────────────┼──────────────────┘
          │                 │                        │
┌─────────▼─────────────────▼────────────────────────▼──────────────────┐
│                      PIPELINE CRM (NEW)                                  │
│                                                                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │
│  │ Application     │  │ Interview       │  │ Feedback Loop       │   │
│  │ Tracker         │  │ Tracker         │  │ (good/false alert)  │   │
│  └────────┬────────┘  └────────┬────────┘  └──────────┬──────────┘   │
│           │                    │                       │               │
│           └────────────────────┴───────────────────────┘               │
│                              │                                           │
│                    tunes RecruiterFitService weights                     │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────────────┐
│                    REST API (for future UI)                              │
│  GET /api/jobs  POST /api/applications  GET /api/pipeline/stats         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component responsibilities (target state)

| Component | New/Existing | Responsibility |
|-----------|--------------|----------------|
| `JobScheduler` | Existing | Trigger ingestion on schedule |
| `ProviderRegistry` + handlers | Existing | Fetch jobs from employer APIs |
| `PriorityJobQueue` | **New** | Process fresh postings before backlog |
| `ExperienceFilterService` | Refactor | Hard gates only; delegate soft scoring to RecruiterFit |
| `RecruiterFitService` | **New** | Composite recruiter-centric local scoring |
| `SkillMatcherService` | Refactor | Sub-component of RecruiterFit (skill dimension only) |
| `JdExtractionService` | Upgrade | Prompt v3.0 — recruiter persona |
| `AlertService` | **New** (extract from JobService) | Recruiter-style Telegram messages |
| `ApplicationPrepService` | **New** | Cover letter, fit bullets, checklist |
| `ApplicationService` | **New** | Apply status CRUD |
| `InterviewService` | **New** | Interview rounds, outcomes |
| `FeedbackService` | **New** | User labels on alert quality |
| `PipelineAnalyticsService` | **New** | Funnel metrics |
| `JobController` | **New** | REST API for jobs |
| `ApplicationController` | **New** | REST API for pipeline |

### Decision flow (per job)

```
New Job scraped
    │
    ▼
Already in DB? ──yes──▶ skip
    │ no
    ▼
Title/role hard gate (senior, manager, frontend, etc.)
    │ fail ──▶ saveSkippedJob(TITLE_OR_ROLE_GATE)
    ▼ pass
Empty JD? ──yes──▶ saveSkippedJob(EMPTY_DESCRIPTION)
    │ no
    ▼
RecruiterFitService.computeLocalFit()
    │ gate failures ──▶ saveSkippedJob(RECRUITER_GATE)
    │ local score < 35 ──▶ saveSkippedJob(LOW_LOCAL_SCORE)
    ▼ pass
Bedrock enabled?
    │ no ──▶ if local score >= notify threshold → alert + general resume
    ▼ yes
JdExtractionService (prompt v3.0)
    │
    ▼
AI verdict = REJECT or HOLD? ──yes──▶ saveSkippedJob(AI_REJECT)
    │ no (SHORTLIST / STRONG_SHORTLIST)
    ▼
Composite score >= notify threshold (default 65)?
    │ no ──▶ save job silently (no Telegram)
    ▼ yes
sendRecruiterAlert() via Telegram
    │
    ▼
score >= 75? ──yes──▶ tailored resume + cover letter prep
    │ no ──▶ general resume only
    ▼
Create Application record (status: SAVED)
```

---

## 6. Phased Implementation Plan

### Phase 0 — Foundation & Trust (Revised)

**Goal:** Fix config drift, fail fast on bad configuration, make the pipeline observable, and lock in regression coverage — **before** RecruiterFit (Phase 1).

**Full detail:** See `PHASE_0_PLAN.md` at repo root.

#### What changed from the original Phase 0

| Original | Revised | Reason |
|----------|---------|--------|
| 0.7 Skipped-job TTL (90 days) | **Deferred** to Phase 6 | Need skip history to compare old pipeline vs RecruiterFit |
| 0.8 Structured logging | **0.8 MDC correlation logging** | JD fetch uses 1–3 threads; logs interleave without `cycleId` / `jobId` |
| — | **0.9 Skip audit trail** | Stable `skipReason`, query indexes, `pipelineVersion` on `Job` |
| — | **0.11 Configuration validation** | Fail at startup on invalid thresholds / missing secrets |
| — | **0.12 Baseline regression tests** | After config fixes; narrow unit tests, not full golden dataset |

#### Build order (strict)

```
Step 1 — Config unification (0.1 → 0.4)
Step 2 — Externalize scheduler interval (0.5)
Step 3 — Security & hygiene (0.6, 0.7)
Step 4 — Configuration validation (0.11)
Step 5 — Skip audit trail (0.9)
Step 6 — Correlation logging (0.8)
Step 7 — Telegram consolidation (0.10)
Step 8 — Manual sanity check (one cycle)
Step 9 — Baseline regression tests (0.12)
```

**Rule:** Do not write regression tests until Steps 1–4 are done.

#### Task checklist

| ID | Task | Details |
|----|------|---------|
| 0.1 | Unify experience config | `ExperienceFilterService` reads `candidate.experience` from `CandidateProfile` (3.6, not hardcoded `3`) |
| 0.2 | Wire tailoring properties | Respect `resume.tailoring.enabled` and `resume.tailoring.min-score` in `JobService`; remove hardcoded `50` |
| 0.3 | Single notify threshold | Add `candidate.notify-min-score` (default 65) for Telegram; keep `ai.bedrock.min-local-score` for pre-Bedrock cost gate |
| 0.4 | Fix mock AI | `JdExtractionService` mock sets `aiMatchScore` consistently with real Bedrock path |
| 0.5 | Externalize scheduler | `job.scheduler.fixed-delay-ms` and `initial-delay-ms` (replace hardcoded 300000 in `JobScheduler`) |
| 0.6 | Remove secrets | Empty defaults for AWS/Telegram keys in all committed property files |
| 0.7 | Align model IDs | Same Bedrock model in dev and prod (or document intentional difference) |
| 0.8 | MDC correlation logging | `cycleId` per `checkJobs()` run; `jobId` + `provider` + `stage` per job; propagate in JD fetch thread pool; update Logback pattern |
| 0.9 | Skip audit trail | `SkipReason` constants; `pipelineVersion` on `Job` (e.g. `skill-matcher-v1`); query indexes on `skipped` + `skipReason` — **no TTL yet** |
| 0.10 | Consolidate Telegram | Move `JobService.sendTelegram()` → `TelegramService.sendJobAlert()`; set `TailoredResume.sentToUser` on send |
| 0.11 | Configuration validation | `@Validated` on properties; fail startup if scores out of range, scheduler ≤ 0, Bedrock enabled without model/credentials, Telegram missing |
| 0.12 | Baseline regression tests | Unit tests for `ExperienceFilterService`, `SkillMatcherService`, mocked `JobService` pipeline, `ConfigurationValidator`; optional 5–10 job smoke counter — **not** full golden dataset |

#### Three thresholds (after 0.3)

| Property | Purpose |
|----------|---------|
| `ai.bedrock.min-local-score` | Gate before Bedrock (cost control) |
| `candidate.notify-min-score` | Gate Telegram alert (user-facing) |
| `resume.tailoring.min-score` | Gate tailored resume (cost control) |

#### Deferred out of Phase 0

| Item | When |
|------|------|
| Skipped-job TTL | Phase 6 or post–RecruiterFit calibration |
| Full golden dataset (20–30 labeled jobs) | Phase 6 |
| RecruiterFitService | Phase 1 |
| OpenTelemetry / distributed tracing | Not needed (MDC sufficient) |

**Exit criteria:** No hardcoded thresholds; invalid config fails at startup; logs traceable per `jobId`; skipped jobs retained with `skipReason` + `pipelineVersion`; `mvn test` passes; one manual cycle confirms behavior.

---

### Phase 1 — Recruiter-Centric Matching (Week 3–5) ⭐ Highest ROI

**Goal:** Replace technology-centric skill matching with recruiter-style fit scoring.

#### 1.1 Extend CandidateProfile

```properties
# application.properties / per-user profile later
candidate.target-level=SDE2
candidate.min-acceptable-level=SDE1
candidate.max-acceptable-level=SDE2
candidate.must-have-skills=Java,Spring Boot
candidate.nice-to-have-skills=Kafka,Docker,AWS,Microservices
candidate.avoid-titles=Senior,Lead,Principal,Staff,Manager,Director,Frontend,DevOps,Data Engineer,QA
candidate.preferred-work-mode=HYBRID,REMOTE,FLEXIBLE
candidate.preferred-locations=Bengaluru,Hyderabad,Remote,Pune,Gurgaon,Noida
candidate.max-min-experience=4
candidate.notify-min-score=65
candidate.primary-stack-keywords=java,backend,software engineer,sde,developer
```

#### 1.2 New service: RecruiterFitService

```java
public class RecruiterFitResult {
    int compositeScore;           // 0-100
    int roleFitScore;
    int levelFitScore;
    int mustHaveCoverage;         // % of job must-haves satisfied
    int experienceFitScore;
    int contextFitScore;          // location, work mode
    List<String> gateFailures;    // hard reject reasons
    List<String> redFlags;
    List<String> mustHaveGaps;
    String recruiterSummary;      // 1-2 sentences
    Verdict verdict;              // STRONG_SHORTLIST | SHORTLIST | HOLD | REJECT
}
```

#### 1.3 Composite score weights

| Dimension | Weight | Evaluates |
|-----------|--------|-----------|
| Role alignment | 25% | Title vs `candidate.roles`; backend vs frontend |
| Level fit | 20% | SDE1/SDE2 sweet spot; penalize Senior/Lead |
| Must-have skills | 25% | Job requirements satisfied by candidate (not reverse) |
| Experience fit | 15% | Soft curve: 3yr req=100%, 4yr=85%, 5+=reject |
| Context fit | 10% | India location, preferred work mode |
| Red-flag penalty | 5% | Wrong primary stack, contract role, etc. |

#### 1.4 Hard gates (before Bedrock)

- Missing 2+ must-have skills → REJECT
- Title contains blocked keyword → REJECT
- Min experience > `candidate.max-min-experience` → REJECT
- Primary stack clearly not Java/backend → REJECT

#### 1.5 Bedrock prompt v3.0

Upgrade `JdExtractionService` prompt from skill-matcher to recruiter screener:

- Persona: "You are a technical recruiter screening for backend Java roles in India"
- Output: `recruiterVerdict`, `redFlags`, `mustHaveGaps`, `whyShortlist` / `whyReject`
- Scoring guide aligned with composite dimensions

#### 1.6 New Job fields

```java
private String recruiterVerdict;
private Integer roleFitScore;
private Integer levelFitScore;
private Integer mustHaveCoverage;
private List<String> redFlags;
private List<String> mustHaveGaps;
private String recruiterSummary;
```

**Exit criteria:** Telegram only fires for SHORTLIST + score ≥ 65. Measurable drop in false positives.

---

### Phase 2 — Speed Advantage (Week 6–7)

**Goal:** Alert before jobs spread to LinkedIn/Naukri aggregators.

| ID | Task | Details |
|----|------|---------|
| 2.1 | Freshness score | `hoursSincePosted`; boost score for jobs < 6 hours old |
| 2.2 | Priority queue | Process `postedAt = today` before older backlog |
| 2.3 | Provider tuning | Amazon: every cycle; Microsoft: keep conservative 10s delay |
| 2.4 | Goldman date fix | Stricter handling — no date means lower priority or shorter lookback |
| 2.5 | SLA logging | Log and store `postedAt → firstSeenAt` delta per job |
| 2.6 | Telegram freshness line | "⏱ Posted 2h ago — apply early" |

**Exit criteria:** Median time-to-alert < 2 hours for jobs with known post date.

---

### Phase 3 — Application Acceleration (Week 8–10)

**Goal:** User can apply in < 5 minutes from alert with all materials ready.

| ID | Service | Output |
|----|---------|--------|
| 3.1 | `ApplicationPrepService` | 3 tailored "why I fit" bullet points |
| 3.2 | `CoverLetterService` | Short cover letter (Bedrock, only if score ≥ 75) |
| 3.3 | `ApplyChecklistService` | Gaps to address, questions for recruiter, red flags |
| 3.4 | `DocxExportService` (optional) | DOCX resume for ATS portals that reject PDF |

**New collection:** `application_prep` (jobId, bullets, coverLetter, checklist, createdAt)

**Exit criteria:** Alert includes copy-paste ready fit bullets; cover letter for top matches.

---

### Phase 4 — Application & Interview CRM (Week 11–13)

**Goal:** Track full funnel: Alert → Applied → Interview → Offer.

#### Application model

```java
@Document(collection = "applications")
public class Application {
    @Id String id;                    // jobId
    String jobId;
    LocalDateTime appliedAt;
    String channel;                   // DIRECT, NAUKRI, LINKEDIN, OTHER
    String resumeType;                // TAILORED, GENERAL
    ApplicationStatus status;         // SAVED, APPLIED, REJECTED, GHOSTED, OFFER
    String notes;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

#### Interview model

```java
@Document(collection = "interviews")
public class Interview {
    @Id String id;
    String applicationId;
    String jobId;
    InterviewRound round;             // OA, PHONE, TECHNICAL, ONSITE, HR, FINAL
    LocalDateTime scheduledAt;
    InterviewOutcome outcome;         // PENDING, PASSED, FAILED, CANCELLED
    String feedback;
    String nextStep;
}
```

#### Wire existing unused fields

- `TailoredResume.sentToUser = true` when PDF sent
- `TailoredResume.applied = true` when user marks applied

#### Telegram bot commands (backend webhook)

```
/applied {jobId}
/reject {jobId}
/interview {jobId} phone 2026-03-20
/notes {jobId} Recruiter said they'll call next week
```

**Exit criteria:** Full pipeline visible via API; funnel metrics calculable.

---

### Phase 5 — Provider Expansion (Week 14–18, can run in parallel)

**Goal:** 20+ employers, 50+ relevant new jobs per week.

#### Tier 1 — Same platform (low effort)

| Company | Platform | Est. effort |
|---------|----------|-------------|
| Google | Workday | 4h |
| Oracle | Workday | 3h |
| Adobe | Workday | 3h |
| Salesforce | Workday | 3h |
| More banks (Citi, HSBC, etc.) | Oracle HCM | 2h each |

Use existing docs: `ADD_WORKDAY_COMPANIES.md`, `WORKDAY_FACET_FINDER.md`

#### Tier 2 — New platforms (medium effort)

| Company type | Platform | Handler needed |
|--------------|----------|----------------|
| Flipkart, Swiggy, Razorpay | Greenhouse / Lever | New handler |
| PhonePe, Paytm | Custom API | New handler |

#### Tier 3 — Supplementary sources

- Naukri alert email/RSS parser (review ToS first)
- Wellfound / Instahyre for startup roles

#### Do NOT build

- LinkedIn scraping (ToS violation, account ban risk)
- Use LinkedIn only as manual apply channel tracked in CRM

#### New: ProviderHealthService

- Detect broken facet IDs / empty responses
- Auto-disable provider + alert admin
- Health metrics per provider

**Exit criteria:** 20+ active providers; provider health dashboard in logs/API.

---

### Phase 6 — Feedback Loop (Week 19–21)

**Goal:** System improves precision over time from user input.

| ID | Task |
|----|------|
| 6.1 | `JobFeedback` model — GOOD_ALERT, FALSE_POSITIVE, MISSED |
| 6.2 | Telegram inline buttons: ✅ Good / ❌ False alert / Skip |
| 6.3 | Weekly calibration report (top false positive patterns) |
| 6.4 | Auto-tune weights from feedback (config file, not ML) |
| 6.5 | Golden set: `docs/calibration/labeled-jobs.json` for regression tests |
| 6.6 | Integration tests for RecruiterFitService with golden set |

**Exit criteria:** Precision metric improves month-over-month; tests prevent regressions.

---

### Phase 7 — Multi-User Backend (Week 22–24)

**Goal:** Anyone can use the system with their own profile.

| ID | Task |
|----|------|
| 7.1 | `User` model — id, telegramChatId, email, createdAt |
| 7.2 | Per-user `CandidateProfile` snapshot |
| 7.3 | Scoped jobs, applications, interviews per user |
| 7.4 | API authentication (API key or JWT) |
| 7.5 | Per-user Bedrock budget caps |

**Exit criteria:** Two independent users on one deployment without data leakage.

---

## 7. Recruiter-Centric Matching (Deep Dive)

### Problem with current SkillMatcherService

Current logic: *"What % of MY profile skills appear in the JD?"*

Recruiter logic: *"Does THIS candidate satisfy THIS job's requirements at THIS level?"*

| Scenario | Current system | Recruiter view |
|----------|----------------|----------------|
| JD needs Java + Spring only; you have 10 skills, match 2 | ~40% — may skip | Strong fit — all must-haves met |
| JD is Senior Java Lead, 8+ years | May pass title filter | Reject — level mismatch |
| JD is "React Frontend Developer" with Java in nice-to-have | High score (Java found) | Reject — wrong role |
| JD requires 4 years; you have 3.6 | Pass (threshold 4) | Borderline — note in alert |

### Bidirectional skill scoring

```
requirementCoverage = matched job must-haves / total job must-haves × 100
profileRelevance    = matched profile skills in JD / total profile skills × 100

finalSkillScore = 0.7 × requirementCoverage + 0.3 × profileRelevance
```

### Experience fit curve

| Job min experience | Candidate 3.6 yrs | Score |
|--------------------|-------------------|-------|
| 0–2 years | Overqualified slightly | 90% |
| 3 years | Perfect | 100% |
| 4 years | Slightly stretch | 85% |
| 5+ years | Hard reject (gate) | 0% |

### Role alignment examples

| Job title | Score | Reason |
|-----------|-------|--------|
| Software Development Engineer II - Java | 95 | Exact target |
| Backend Engineer | 90 | Strong match |
| Java Full Stack Developer | 75 | Acceptable |
| Senior Software Engineer | 20 | Level mismatch — gate |
| Frontend Engineer React | 10 | Wrong role — gate |
| DevOps Engineer | 5 | Wrong track — gate |

### Calibration with real data

Before tuning weights, label 20–30 real jobs:

| Label | Meaning |
|-------|---------|
| ✅ WOULD_APPLY | Good alert |
| 🤔 MAYBE | Borderline |
| ❌ FALSE_POSITIVE | Should not have notified |
| 😞 MISSED | Wish we had notified |

Store in `docs/calibration/labeled-jobs.json`:

```json
{
  "id": "amazon_123456",
  "title": "SDE II - Java Backend",
  "company": "Amazon",
  "verdict": "WOULD_APPLY",
  "reason": "Perfect SDE2 Java role, 3-5 yrs, Bengaluru",
  "jdExcerpt": "...",
  "telegramMessage": "..."
}
```

---

## 8. Telegram Notification Redesign

### Current format (technology-centric)

```
🚀 New Job Alert
🏢 Company | 💼 Role | ⭐ Match Score: 77%
✅ Matched Skills: Java, Spring Boot
❌ Missing Skills: Kafka
```

### Target format (recruiter-centric)

```
🎯 STRONG SHORTLIST — Apply within 24h

🏢 Amazon | SDE II - Java Backend
📍 Bengaluru | Hybrid | Posted 3h ago

👤 Recruiter view:
Backend IC role at right level. Core Java/Spring match.
3-5 yr band fits your 3.6 yrs. Kafka preferred, not required.

📊 Fit: 84/100
   Role 92 | Level 88 | Must-haves 90 | Experience 85

⚠️ Risks: High applicant volume — apply early
✅ Must-haves met: Java, Spring Boot, REST API
❌ Gaps: None critical

📄 Tailored resume + cover letter attached

/applied amazon_123456 | /skip | /why
```

### Notification rules

| Condition | Action |
|-----------|--------|
| verdict = REJECT or HOLD | No Telegram |
| verdict = SHORTLIST, score < 65 | Save to DB, no Telegram |
| verdict = SHORTLIST, score ≥ 65 | Telegram alert + general resume |
| verdict = STRONG_SHORTLIST, score ≥ 75 | Telegram + tailored resume + cover letter |
| verdict = STRONG_SHORTLIST, score 65–74 | Telegram + tailored resume only |

---

## 9. REST API (Future UI)

Backend APIs to build in Phase 4. No frontend in this plan — these endpoints enable UI later.

### Jobs

```
GET    /api/jobs                          # List matched jobs (paginated, filterable)
GET    /api/jobs/{id}                     # Job detail with recruiter analysis
GET    /api/jobs/skipped                  # Skipped jobs with reasons
GET    /api/jobs/stats                    # Counts: today, this week, by company
POST   /api/jobs/{id}/feedback            # GOOD_ALERT | FALSE_POSITIVE
```

### Applications

```
GET    /api/applications                  # All applications
GET    /api/applications/{id}             # Application detail
POST   /api/applications                  # Create from jobId
PATCH  /api/applications/{id}/status      # SAVED → APPLIED → etc.
POST   /api/applications/{id}/notes       # Add note
```

### Interviews

```
GET    /api/interviews                    # All interviews
GET    /api/interviews/upcoming           # Next 7 days
POST   /api/interviews                    # Schedule interview round
PATCH  /api/interviews/{id}/outcome       # PENDING → PASSED | FAILED
```

### Pipeline analytics

```
GET    /api/pipeline/stats                # Funnel: alerts → applied → interview → offer
GET    /api/pipeline/metrics              # Precision, time-to-alert, etc.
```

### System

```
GET    /health                            # Existing
GET    /api/providers/health              # Provider status, last fetch, error count
```

---

## 10. Data Model Extensions

### Job (additions to existing model)

```java
// Phase 0 — skip audit trail
private String pipelineVersion;        // e.g. "skill-matcher-v1", "recruiter-fit-v1"

// Recruiter fit (Phase 1)
private String recruiterVerdict;
private Integer roleFitScore;
private Integer levelFitScore;
private Integer mustHaveCoverage;
private List<String> redFlags;
private List<String> mustHaveGaps;
private String recruiterSummary;

// Speed (Phase 2)
private Integer freshnessScore;
private Long alertLatencyMinutes;      // postedAt → firstSeenAt

// Application prep (Phase 3)
private List<String> fitBullets;
private String coverLetter;
private List<String> applyChecklist;
```

### New collections

| Collection | Phase | Purpose |
|------------|-------|---------|
| `applications` | 4 | Apply tracking |
| `interviews` | 4 | Interview rounds |
| `job_feedback` | 6 | User feedback on alert quality |
| `application_prep` | 3 | Cover letter, bullets, checklist |
| `users` | 7 | Multi-user support |

### Indexes to add

```javascript
// jobs — Phase 0 (skip audit trail)
db.jobs.createIndex({ "skipped": 1, "skipReason": 1, "skippedAt": -1 })
db.jobs.createIndex({ "pipelineVersion": 1, "skipped": 1 })

// jobs — Phase 1
db.jobs.createIndex({ "recruiterVerdict": 1, "firstSeenAt": -1 })

// jobs — Phase 6 (deferred: skipped-job TTL)
// db.jobs.createIndex({ "skipped": 1, "skippedAt": 1 }, { expireAfterSeconds: 7776000 })

// applications
db.applications.createIndex({ "status": 1, "appliedAt": -1 })
db.applications.createIndex({ "userId": 1, "status": 1 })
```

---

## 11. Provider Expansion Strategy

### Selection criteria for new companies

| Criteria | Weight | Why |
|----------|--------|-----|
| Has public career API | Required | No scraping fragility |
| Posts Java/backend roles in India | High | Profile fit |
| Known platform (Workday, Oracle HCM) | High | Reuse existing handlers |
| High interview conversion historically | Medium | Quality over quantity |
| Less competition than Naukri listings | Medium | Speed advantage |

### Platform handler roadmap

| Platform | Status | Companies to add |
|----------|--------|------------------|
| Oracle HCM | ✅ Built | Citi, HSBC, Deutsche Bank |
| Workday | ✅ Built | Google, Oracle, Adobe, Salesforce, Walmart |
| TalentBrew | ✅ Built | More banks using it |
| Goldman GraphQL | ✅ Built | — |
| Microsoft Careers | ✅ Built | — |
| Amazon Jobs | ✅ Built | — |
| Greenhouse | ❌ New handler | Flipkart, Razorpay, many startups |
| Lever | ❌ New handler | Swiggy, others |
| Ashby | ❌ New handler | Modern startups |

### Provider health monitoring

```java
public class ProviderHealth {
    String companyName;
    LocalDateTime lastSuccessfulFetch;
    int jobsFetchedLastRun;
    int errorsLast24h;
    boolean enabled;
    String lastError;
}
```

---

## 12. Cost Model

### Per-job cost breakdown

| Stage | Bedrock calls | Est. cost (INR) |
|-------|---------------|-----------------|
| Local RecruiterFit only | 0 | ₹0 |
| JD extraction (passes local filter) | 1 | ~₹0.08 |
| Resume tailoring (score ≥ 75) | 1–4 | ~₹0.15–0.30 |
| Cover letter (score ≥ 75) | 1 | ~₹0.08 |

### Monthly estimate (solo user)

| Scenario | Jobs processed | Alerts sent | AWS cost | Cursor |
|----------|----------------|-------------|----------|--------|
| Light (10 alerts/mo) | ~500 | 10 | ~₹5 | ₹649 |
| Medium (30 alerts/mo) | ~1500 | 30 | ~₹20 | ₹649 |
| Heavy (60 alerts/mo) | ~3000 | 60 | ~₹50 | ₹649 |

### Cost control rules (implement in code)

1. Local RecruiterFit rejects ≥ 60% of jobs before Bedrock
2. No Bedrock if `ai.bedrock.enabled=false`
3. No resume tailoring if verdict = HOLD or REJECT
4. No cover letter if score < 75
5. Cap resume tailoring iterations at 2 (not 4) if cost is concern
6. `ai.dev.mock.enabled=true` for all local development

---

## 13. What NOT to Build (Yet)

| Feature | Why defer |
|---------|-----------|
| Frontend / dashboard | Explicitly out of scope — build after backend APIs stable |
| LinkedIn scraping | ToS violation, account ban, legal risk |
| Full auto-apply (form submission bots) | Captchas, legal issues, high maintenance |
| ML model training | Prompt + rules sufficient for v1; revisit at 1000+ labeled jobs |
| Referral network / employee graph | Needs data sources we shouldn't scrape |
| Real-time webhooks from employers | No employer offers this — polling is fine |
| Mobile app | Telegram is sufficient for v1 |
| Multi-language support | India English-only for now |

---

## 14. Technical Debt to Fix First

Priority order before Phase 1 (aligned with revised Phase 0):

| # | Issue | File(s) | Fix | Phase 0 ID |
|---|-------|---------|-----|------------|
| 1 | Experience hardcoded to 3 | `ExperienceFilterService` | Read `CandidateProfile.experience` | 0.1 |
| 2 | Resume min-score ignored | `JobService` | Use `resume.tailoring.min-score` property | 0.2 |
| 3 | `resume.tailoring.enabled` unused | `JobService` | Guard tailoring with property | 0.2 |
| 4 | No single notify threshold | `JobService`, properties | Add `candidate.notify-min-score` | 0.3 |
| 5 | Mock AI field mismatch | `JdExtractionService` | Align `aiMatchScore` in mock path | 0.4 |
| 6 | Scheduler interval hardcoded | `JobScheduler` | Externalize to properties | 0.5 |
| 7 | AWS keys in committed config | `application*.properties` | Empty defaults only | 0.6 |
| 8 | Telegram logic split | `JobService`, `TelegramService` | Consolidate | 0.10 |
| 9 | No baseline tests | `src/test/` | Regression tests before RecruiterFit | 0.12 |
| 10 | Skipped jobs not analyzable | `Job`, `MongoIndexConfig` | `SkipReason`, `pipelineVersion`, indexes — TTL deferred | 0.9 |
| 11 | Logs not traceable under concurrency | `JobService`, Logback | MDC `cycleId` + `jobId` + `stage` | 0.8 |
| 12 | Invalid config fails silently | config classes | Startup validation | 0.11 |
| 13 | `sentToUser` never set | `TelegramService` | Set on send | 0.10 |
| 14 | Goldman no date filter | `GoldmanSachsPlatformHandler` | Lower priority or stricter dedup | Phase 2 |

---

## 15. Risks & Honest Limitations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Employer APIs change facet IDs | High | ProviderHealthService + alerts |
| Bedrock costs spike with more providers | Medium | Local filter gate + budget caps |
| False positives damage trust in alerts | Medium | RecruiterFit + feedback loop |
| Resume tailoring quality inconsistent | Medium | Golden set testing; general resume fallback |
| Microsoft rate limiting blocks fetches | High | Keep 1 thread, 10s delay; don't add more MS load |
| User expects LinkedIn-level volume | High | Set expectations in docs — quality over quantity |
| LaTeX compilation fails on server | Medium | Online API fallback already exists |
| Multi-user complexity delays core features | Medium | Defer Phase 7 until solo flow is proven |

### What success does NOT look like

- Getting 100 Telegram alerts per day (that's Naukri, not JobTracker)
- Replacing LinkedIn for networking/recruiter inbound
- 100% automated apply with zero human effort
- Working for every job type (only optimized for Java backend SDE2 in India)

---

## 16. 6-Month Success Vision

> A user configures their profile once. JobTracker monitors 25+ enterprise career sites every 5 minutes. Within 2 hours of a relevant SDE2 Java role posting, they receive a Telegram alert with:
>
> - Recruiter-style verdict and fit breakdown
> - Tailored resume PDF (ATS score 85+)
> - Cover letter and "why I fit" bullets ready to copy-paste
> - Freshness signal ("Posted 2h ago — apply early")
>
> They apply in under 5 minutes. They tap `/applied` in Telegram. Two weeks later they log a phone screen via `/interview`. The pipeline API shows: 30 alerts → 12 applied → 4 interviews → 1 offer.
>
> Interview rate on STRONG_SHORTLIST alerts: **12%** vs ~2% on mass Naukri applications.

---

## 17. Decision Checklist (Before You Commit)

Use this checklist to challenge the plan. Do not implement blindly.

### Strategy questions

- [ ] Is **Java backend SDE2 in India** still my target profile?
- [ ] Am I OK with **fewer but better** alerts vs high volume?
- [ ] Is **Telegram** still my preferred notification channel?
- [ ] Do I have budget for **~₹20–50/month AWS** at medium usage?
- [ ] Am I willing to **label 20–30 jobs** for calibration (Phase 6)?

### Technical questions

- [ ] Is **MongoDB** still the right database for PDF storage, or should we move to S3/GridFS?
- [ ] Should **Bedrock** remain the AI provider, or evaluate cheaper alternatives?
- [ ] Is **5-minute polling** frequent enough, or do I need faster for specific providers?
- [ ] Do I want **multi-user** (Phase 7) or stay personal tool longer?

### Scope questions

- [ ] Which phase is **must-have** vs nice-to-have for me?
- [ ] Can I defer **provider expansion** until matching is solid?
- [ ] Do I need **cover letter generation** (Phase 3) or is resume enough?
- [ ] Should **Naukri ingestion** (Phase 5) be in scope given ToS concerns?

### Red flags to watch during implementation

- [ ] Alert precision drops below 50% after RecruiterFit changes
- [ ] AWS bill exceeds ₹100/month without proportional interview outcomes
- [ ] More than 2 hours/week spent fixing broken providers
- [ ] Building UI before backend APIs are stable (scope creep)

---

## 18. Recommended Build Order

```
Phase 0    Foundation (config, validation, MDC logging, skip audit, baseline tests) — see PHASE_0_PLAN.md
Phase 1    RecruiterFitService ⭐
Phase 2    Speed (freshness, priority queue)
Phase 3    Application prep (cover letter, bullets)
Phase 4    Application + Interview CRM + REST API
Phase 5    Provider expansion (parallel)
Phase 6    Feedback loop + calibration tests + skipped-job TTL
Phase 7    Multi-user (optional)
```

### Minimum viable interview accelerator (4 weeks)

If time/budget is tight, build only:

1. **Phase 0:** Config fixes + validation + MDC logging + baseline tests (see `PHASE_0_PLAN.md`)
2. **Phase 1:** RecruiterFitService + updated Telegram format
3. **Phase 4 (subset):** Application CRM (basic) + `/applied` bot command
4. **Phase 2 + 5 (subset):** Freshness scoring + 5 Workday companies

This alone should noticeably beat blind Naukri applying for your profile.

---

## Appendix A: Current File Map

```
src/main/java/com/jobtracker/jobtracker/
├── JobtrackerApplication.java
├── config/
│   ├── BedrockConfig.java
│   ├── CandidateProfile.java
│   ├── MongoConfig.java
│   └── MongoIndexConfig.java
├── controller/
│   └── HealthController.java
├── model/
│   ├── Job.java
│   ├── TailoredResume.java
│   └── WorkMode.java
├── repository/
│   ├── JobRepository.java
│   └── TailoredResumeRepository.java
├── scheduler/
│   └── JobScheduler.java
├── service/
│   ├── ExperienceFilterService.java
│   ├── GeneralResumeService.java
│   ├── JdExtractionService.java
│   ├── JobService.java
│   ├── LaTeXCompilerService.java
│   ├── LocalLaTeXCompilerService.java
│   ├── ResumeTailoringService.java
│   ├── SkillMatcherService.java
│   └── TelegramService.java
└── provider/
    ├── DynamicJobProvider.java
    ├── JobProvider.java
    ├── config/
    │   ├── JobProviderConfig.java
    │   ├── Platform.java
    │   └── ProviderRegistry.java
    └── platform/
        ├── AmazonPlatformHandler.java
        ├── BarclaysPlatformHandler.java
        ├── GoldmanSachsPlatformHandler.java
        ├── MicrosoftPlatformHandler.java
        ├── OracleHcmPlatformHandler.java
        ├── PlatformHandler.java
        └── WorkdayPlatformHandler.java
```

## Appendix B: Related Docs in Repo

| File | Topic |
|------|-------|
| `BACKEND_MASTER_PLAN.md` | This document — full backend plan |
| `PHASE_0_PLAN.md` | Phase 0 full implementation plan (revised) |
| `README.md` | Quick start, env vars |
| `CLAUDE.md` | Project overview (partially stale) |
| `ADD_WORKDAY_COMPANIES.md` | Workday provider expansion |
| `WORKDAY_FACET_FINDER.md` | Finding Workday facet IDs |
| `IMPLEMENTATION_PLAN_V4.0.md` | Resume tailoring v4.0 (done) |
| `MONTHLY_COST_ANALYSIS.md` | AWS cost modeling |
| `DEV_MODE_GUIDE.md` | Local testing without Bedrock cost |

---

*This document is the source of truth for backend planning. Update it as decisions change. Challenge every phase before building.*
