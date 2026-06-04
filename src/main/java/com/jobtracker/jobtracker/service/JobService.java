package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.provider.DynamicJobProvider;
import com.jobtracker.jobtracker.provider.JobProvider;
import com.jobtracker.jobtracker.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class JobService {

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.chat.id}")
    private String chatId;

    @Value("${ai.bedrock.enabled:true}")
    private boolean bedrockEnabled;

    @Value("${ai.bedrock.min-local-score:50}")
    private int minLocalScoreForBedrock;

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository repository;
    private final List<JobProvider> providers;
    private final SkillMatcherService skillMatcher;

    @Autowired
    private ExperienceFilterService experienceFilterService;

    @Autowired
    private JdExtractionService jdExtractionService;

    public JobService(JobRepository repository,
                      List<JobProvider> providers,
                      SkillMatcherService skillMatcher) {
        this.repository = repository;
        this.providers = providers;
        this.skillMatcher = skillMatcher;
    }

    public void checkJobs() {
        log.info("Checking jobs across {} providers...", providers.size());

        for (JobProvider provider : providers) {
            log.info("Fetching from: {}", provider.getCompanyName());

            try {
                List<Job> jobs = provider.fetchJobs();
                log.info("{} jobs fetched from {}", jobs.size(), provider.getCompanyName());

                // Filter already seen + obvious title mismatches before threading
                List<Job> newJobs = new ArrayList<>();
                for (Job job : jobs) {
                    if (!repository.existsById(job.getId())) {
                        if (!experienceFilterService.isTitleSuitable(job.getTitle())) {
                            log.info("Skipping by title (no JD fetch): {}", job.getTitle());
                            saveSkippedJob(job, "TITLE_FILTER", false);
                        } else {
                            newJobs.add(job);
                        }
                    }
                }

                log.info("{} new jobs to process from {}", newJobs.size(), provider.getCompanyName());
                if (newJobs.isEmpty()) continue;

                long delayMs;
                int threadCount = 3;

                if (provider instanceof DynamicJobProvider dynamicProvider) {
                    delayMs = dynamicProvider.getConfig().getJdFetchDelayMs();
                    threadCount = dynamicProvider.getConfig().getJdFetchThreads();
                } else {
                    delayMs = 300;
                }

                // Check if JD fetching is needed (threadCount=0 means JD already in search response)
                boolean needsJdFetch = threadCount > 0;

                ExecutorService executor = needsJdFetch ? Executors.newFixedThreadPool(threadCount) : null;
                List<Future<?>> futures = new ArrayList<>();

                for (Job job : newJobs) {
                    final Job jobRef = job;

                    if (needsJdFetch) {
                        Future<?> future = executor.submit(() -> {
                            try {
                                Thread.sleep(delayMs);

                                String jd = provider.fetchJobDescription(jobRef.getExternalId());

                                // Skip if JD is empty — rate limited or not available
    //                            if (jd == null || jd.trim().isEmpty()) {
    //                                log.info("Skipping job with empty JD: {}", jobRef.getTitle());
    //                                return;
    //                            }

                                jobRef.setDescription(jd);

                                processJob(provider, jobRef);

                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                log.error("Thread interrupted for: {}", jobRef.getTitle());
                            } catch (Exception e) {
                                log.error("Error processing {}: {}", jobRef.getTitle(), e.getMessage());
                            }
                        });
                        futures.add(future);
                    } else {
                        // Description already in job object (e.g., Amazon), process directly
                        try {
                            processJob(provider, jobRef);
                        } catch (Exception e) {
                            log.error("Error processing {}: {}", jobRef.getTitle(), e.getMessage());
                        }
                    }
                }

                // Wait for all threads to complete (only if JD fetching was needed)
                if (needsJdFetch) {
                    executor.shutdown();
                    boolean finished = executor.awaitTermination(2, TimeUnit.MINUTES);
                    if (!finished) {
                        log.warn("Timeout waiting for JD fetches from {}", provider.getCompanyName());
                        executor.shutdownNow();
                    }
                }

            } catch (Exception e) {
                log.error("Error processing provider {}: {}", provider.getCompanyName(), e.getMessage());
            }
        }

        log.info("Job check completed.");
    }

    private void processJob(JobProvider provider, Job jobRef) {
        // Update postedAt with exact time if available (for Oracle HCM and Workday providers)
        if (provider instanceof DynamicJobProvider dynamicProvider) {
            var handler = dynamicProvider.getHandler();
            if (handler instanceof com.jobtracker.jobtracker.provider.platform.OracleHcmPlatformHandler oracleHandler) {
                var exactTime = oracleHandler.getAndClearCachedPostedTime(jobRef.getExternalId());
                if (exactTime != null) {
                    jobRef.setPostedAt(exactTime);
                }
            } else if (handler instanceof com.jobtracker.jobtracker.provider.platform.WorkdayPlatformHandler workdayHandler) {
                var exactDate = workdayHandler.getAndClearCachedStartDate(jobRef.getExternalId());
                if (exactDate != null) {
                    jobRef.setPostedAt(exactDate);
                }
            }
        }

        if (jobRef.getDescription() == null || jobRef.getDescription().trim().isEmpty()) {
            log.info("Skipping job with empty JD: {}", jobRef.getTitle());
            saveSkippedJob(jobRef, "EMPTY_DESCRIPTION", false);
            return;
        }

        // Run cheap local filters before spending Bedrock tokens.
        if (!experienceFilterService.isExperienceSuitable(jobRef.getDescription(), jobRef.getTitle())) {
            log.info("Skipping by regex experience before Bedrock: {}", jobRef.getTitle());
            saveSkippedJob(jobRef, "REGEX_EXPERIENCE_FILTER", false);
            return;
        }

        Job locallyMatched = skillMatcher.match(jobRef);
        locallyMatched.setLocalMatchScore(locallyMatched.getMatchScore());

        if (!bedrockEnabled) {
            locallyMatched.setAiAnalyzed(false);
            saveMatchedJob(locallyMatched);
            return;
        }

        if (locallyMatched.getMatchScore() < minLocalScoreForBedrock) {
            log.info("Skipping by local score before Bedrock: {} | Score: {}% | Threshold: {}%",
                    locallyMatched.getTitle(), locallyMatched.getMatchScore(), minLocalScoreForBedrock);
            saveSkippedJob(locallyMatched, "LOW_LOCAL_SCORE", false);
            return;
        }

        // Extract structured data only for jobs that passed cheap local checks.
        jdExtractionService.extractStructuredData(jobRef);
        jobRef.setAiAnalyzed(true);

        if (!experienceFilterService.isExperienceSuitableStructured(jobRef)) {
            log.info("Skipping by experience: {}", jobRef.getTitle());
            saveSkippedJob(jobRef, "STRUCTURED_EXPERIENCE_FILTER", true);
            return;
        }

        Job matched = skillMatcher.matchStructured(jobRef);
        matched.setSkipped(false);
        matched.setSkipReason(null);
        matched.setAiAnalyzed(true);
        saveMatchedJob(matched);
    }

    private void saveMatchedJob(Job matched) {
        matched.setSkipped(false);
        matched.setSkipReason(null);
        matched.setFirstSeenAt(LocalDateTime.now());
        synchronized (repository) {
            if (!repository.existsById(matched.getId())) {
                repository.save(matched);
                log.info("NEW JOB: {} | Score: {}%",
                        matched.getTitle(), matched.getMatchScore());
                sendTelegram(matched);
            }
        }
    }

    private void saveSkippedJob(Job job, String reason, boolean aiAnalyzed) {
        job.setSkipped(true);
        job.setSkipReason(reason);
        job.setSkippedAt(LocalDateTime.now());
        job.setAiAnalyzed(aiAnalyzed);
        job.setFirstSeenAt(LocalDateTime.now());

        synchronized (repository) {
            if (!repository.existsById(job.getId())) {
                repository.save(job);
                log.info("SKIPPED JOB SAVED: {} | Reason: {}", job.getTitle(), reason);
            }
        }
    }

    public void sendTelegram(Job job) {
        try {
            String matched = job.getMatchedSkills() == null ||
                    job.getMatchedSkills().isEmpty()
                    ? "None"
                    : String.join(", ", job.getMatchedSkills());

            String missing = job.getMissingSkills() == null ||
                    job.getMissingSkills().isEmpty()
                    ? "None"
                    : String.join(", ", job.getMissingSkills());

            String experience = formatExperience(
                job.getMinExperienceRequired(),
                job.getMaxExperienceRequired()
            );

            String salary = formatSalary(
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency()
            );

            String summary = job.getDescription() == null ? "N/A"
                    : job.getDescription()
                    .substring(0, Math.min(200, job.getDescription().length()))
                    .replaceAll("&", "&amp;")
                    .replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;");

            // Build recommendation emoji
            String recommendationEmoji = getRecommendationEmoji(job.getApplyRecommendation());
            String recommendation = job.getApplyRecommendation() != null
                ? recommendationEmoji + " " + job.getApplyRecommendation()
                : "Not specified";

            String scoreReason = job.getScoreReason() != null
                ? job.getScoreReason()
                : "Match calculated based on skills and experience";

            String message = """
                🚀 <b>New Job Alert</b>

                🏢 <b>Company:</b> %s
                💼 <b>Role:</b> %s
                📊 <b>Level:</b> %s
                📍 <b>Location:</b> %s
                🏠 <b>Work Mode:</b> %s

                ⭐ <b>Match Score:</b> %s%% %s
                💡 <b>Why:</b> %s

                📈 <b>Experience:</b> %s
                💰 <b>Salary:</b> %s

                ✅ <b>Matched Skills:</b> %s
                ❌ <b>Missing Skills:</b> %s

                🔗 <a href="%s">Click to Apply</a>

                📄 <b>Summary:</b>
                %s
                """.formatted(
                    job.getCompany(),
                    job.getTitle(),
                    job.getJobLevel() != null ? job.getJobLevel() : "Not specified",
                    job.getLocation(),
                    job.getWorkMode() != null ? formatWorkMode(job.getWorkMode()) : "Not specified",
                    (int) job.getMatchScore(),
                    recommendation,
                    scoreReason,
                    experience,
                    salary,
                    matched,
                    missing,
                    job.getUrl(),
                    summary
            );

            String url = "https://api.telegram.org/bot" + token + "/sendMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", message);
            payload.put("parse_mode", "HTML");
            payload.put("disable_web_page_preview", true);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            log.info("Telegram status: {}", response.getStatusCode());

        } catch (Exception e) {
            log.error("Telegram notification failed: {}", e.getMessage());
        }
    }

    private String formatExperience(Integer min, Integer max) {
        if (min == null && max == null) {
            return "Not specified";
        }
        if (min != null && max != null) {
            return min + "-" + max + " years";
        }
        if (min != null) {
            return min + "+ years";
        }
        return "Up to " + max + " years";
    }

    private String formatSalary(Long min, Long max, String currency) {
        if (min == null && max == null) {
            return "Not mentioned";
        }

        String curr = currency != null ? currency : "INR";

        if (min != null && max != null) {
            return formatAmount(min, curr) + " - " + formatAmount(max, curr);
        }
        if (min != null) {
            return formatAmount(min, curr) + "+";
        }
        return "Up to " + formatAmount(max, curr);
    }

    private String formatAmount(long amount, String currency) {
        if ("INR".equals(currency)) {
            double lpa = amount / 100000.0;
            return String.format("%.1f LPA", lpa);
        }
        return String.format("%s %,d", currency, amount);
    }

    private String formatWorkMode(com.jobtracker.jobtracker.model.WorkMode mode) {
        return switch (mode) {
            case REMOTE -> "🏠 Remote";
            case HYBRID -> "🔄 Hybrid";
            case ONSITE -> "🏢 On-site";
            case FLEXIBLE -> "✨ Flexible";
            case NOT_SPECIFIED -> "Not specified";
        };
    }

    private String getRecommendationEmoji(String recommendation) {
        if (recommendation == null) return "";
        return switch (recommendation.toLowerCase()) {
            case "strong apply" -> "🔥";
            case "apply" -> "✅";
            case "consider" -> "🤔";
            case "skip" -> "⛔";
            default -> "";
        };
    }
}
