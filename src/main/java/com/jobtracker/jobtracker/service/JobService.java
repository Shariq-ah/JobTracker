package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.provider.JobProvider;
import com.jobtracker.jobtracker.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JobService {

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.chat.id}")
    private String chatId;

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository repository;
    private final List<JobProvider> providers;
    private final SkillMatcherService skillMatcher;

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

                for (Job job : jobs) {
                    if (!repository.existsById(job.getId())) {

                        // fetch JD
                        String jd = provider.fetchJobDescription(job.getExternalId());
                        job.setDescription(jd);

                        // match skills
                        job = skillMatcher.match(job);

                        // set first seen
                        job.setFirstSeenAt(LocalDateTime.now());

                        // save
                        repository.save(job);

                        log.info("NEW JOB: {} | Score: {}%",
                                job.getTitle(), job.getMatchScore());

                        // notify
                        sendTelegram(job);
                    }
                }

            } catch (Exception e) {
                log.error("Error fetching from {}: {}",
                        provider.getCompanyName(), e.getMessage());
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

            String summary = job.getDescription() == null ? "N/A"
                    : job.getDescription()
                    .substring(0, Math.min(200, job.getDescription().length()))
                    .replaceAll("&", "&amp;")
                    .replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;");

            String message = """
                🚀 <b>New Job Alert</b>
                
                🏢 <b>Company:</b> %s
                💼 <b>Role:</b> %s
                📍 <b>Location:</b> %s
                ⭐ <b>Match Score:</b> %s%%
                
                ✅ <b>Matched Skills:</b> %s
                ❌ <b>Missing Skills:</b> %s
                
                🔗 <a href="%s">Click to Apply</a>
                
                📄 <b>Summary:</b>
                %s
                """.formatted(
                    job.getCompany(),
                    job.getTitle(),
                    job.getLocation(),
                    (int) job.getMatchScore(),
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
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("Telegram status: {}", response.getStatusCode());

        } catch (Exception e) {
            log.error("Telegram notification failed: {}", e.getMessage());
        }
    }
}