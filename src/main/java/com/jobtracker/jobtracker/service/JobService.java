package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.provider.JobProvider;
import com.jobtracker.jobtracker.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final JobProvider jobProvider;

    public JobService(JobRepository repository, JobProvider jobProvider) {
        this.repository = repository;
        this.jobProvider = jobProvider;
    }

    public void checkJobs(){

        log.info("Checking jobs...");

        List<Job> jobList = jobProvider.fetchJobs();

        log.info("Jobs fetched: {}", jobList.size());

        for(Job job : jobList){
            log.info("Saving job with ID: {}", job.getId());
            if (!repository.existsById(job.getId())){
                log.info("Saving to DB: {}", job.getTitle());
                String jd = jobProvider.fetchJobDescription(job.getExternalId());
                job.setDescription(jd);
                repository.save(job);
                notify(job);
            }
        }
    }

    private void notify(Job job) {
        sendTelegram(job);
    }

    public void sendTelegram(Job job) {
        try {

            String message = """
                🚀 <b>New Job Alert</b>

                🏢 <b>Company:</b> %s
                💼 <b>Role:</b> %s
                📍 <b>Location:</b> %s

                🔗 <b>Apply Here:</b>
                <a href="%s">Click to Apply</a>

                📄 <b>Summary:</b>
                    %s
            """.formatted(
                    job.getCompany(),
                    job.getTitle(),
                    job.getLocation(),
                    job.getUrl(),
                    job.getDescription().substring(0, Math.min(200, job.getDescription().length()))
            );

            String url = "https://api.telegram.org/bot" + token + "/sendMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", message);
            payload.put("parse_mode", "HTML");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            RestTemplate restTemplate = new RestTemplate();

            restTemplate.postForObject(url, request, String.class);

            log.info("Telegram notification sent");

        } catch (Exception e) {
            log.error("Telegram notification failed", e);
        }
    }

}
