package com.jobtracker.jobtracker.provider;

import com.jobtracker.jobtracker.model.Job;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class MicrosoftJobProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftJobProvider.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://apply.careers.microsoft.com";

    @Override
    public String getCompanyName() {
        return "Microsoft";
    }

    @Override
    public List<Job> fetchJobs() {
        try {
            log.info("Calling Microsoft API...");

            String url = BASE_URL + "/api/pcsx/search" +
                    "?domain=microsoft.com" +
                    "&query=" +
                    "&location=India" +
                    "&start=0" +
                    "&num_items=25" +
                    "&sort_by=timestamp" +
                    "&filter_include_remote=1" +
                    "&filter_profession=software engineering" +
                    "&hl=en";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            headers.set("Referer", "https://jobs.microsoft.com/en/jobs/search");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            log.info("Microsoft response status: {}", response.getStatusCode());
            return parse(response.getBody());

        } catch (Exception e) {
            log.error("Error fetching Microsoft jobs: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Job> parse(String json) throws Exception {
        List<Job> jobs = new ArrayList<>();

        JsonNode root = objectMapper.readTree(json);
        JsonNode positions = root.path("data").path("positions");

        log.info("Microsoft total jobs: {}", positions.size());

        long cutoffTs = Instant.now()
                .minus(48, ChronoUnit.HOURS)
                .getEpochSecond();

        for (JsonNode node : positions) {

            long postedTs = node.path("postedTs").asLong();

            if (postedTs < cutoffTs) {
                log.info("Skipping old Microsoft job: {} | postedTs: {}",
                        node.path("name").asText(), postedTs);
                continue;
            }

            String id = node.path("id").asText();
            String title = node.path("name").asText();
            String positionUrl = node.path("positionUrl").asText();

            JsonNode locationsNode = node.path("locations");
            String location = locationsNode.isEmpty() ? "India"
                    : locationsNode.get(0).asText();

            Job job = new Job();
            job.setId("msft_" + id);
            job.setExternalId(id);
            job.setCompany("Microsoft");
            job.setTitle(title);
            job.setPostedAt(
                    java.time.Instant.ofEpochSecond(postedTs)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDateTime()
            );
            job.setLocation(location);
            job.setUrl("https://apply.careers.microsoft.com/careers/job/" + id);

            jobs.add(job);
            log.info("Microsoft job: {} | {} | {}", title, location, id);
        }

        log.info("Microsoft jobs after date filter: {}", jobs.size());
        return jobs;
    }

    @Override
    public String fetchJobDescription(String externalId) {
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Increasing delay per attempt
                Thread.sleep(attempt * 1000L);

                String url = BASE_URL + "/api/pcsx/position_details" +
                        "?position_id=" + externalId +
                        "&domain=microsoft.com&hl=en";

                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0");
                headers.set("Accept", "application/json");
                headers.set("Referer", "https://jobs.microsoft.com/en/jobs/search");

                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                JsonNode root = objectMapper.readTree(response.getBody());
                String html = root.path("data").path("jobDescription").asText("");

                if (html.isEmpty()) {
                    log.warn("No JD found for Microsoft job: {}", externalId);
                    return "";
                }

                String cleanJD = Jsoup.parse(html).text()
                        .replaceAll("\\s+", " ")
                        .trim();

                log.info("Microsoft JD length for {}: {}", externalId, cleanJD.length());
                return cleanJD;

            } catch (Exception e) {
                log.warn("Attempt {}/{} failed for Microsoft JD {}: {}",
                        attempt, maxRetries, externalId, e.getMessage());
                if (attempt == maxRetries) {
                    log.error("All retries failed for Microsoft JD: {}", externalId);
                    return "";
                }
            }
        }
        return "";
    }
}