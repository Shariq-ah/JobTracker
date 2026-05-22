package com.jobtracker.jobtracker.provider.platform;

import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.provider.config.JobProviderConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class BarclaysPlatformHandler implements PlatformHandler {

    private static final Logger log = LoggerFactory.getLogger(BarclaysPlatformHandler.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://search.jobs.barclays";

    @Override
    public List<Job> fetchJobs(JobProviderConfig config) {
        List<Job> allJobs = new ArrayList<>();
        int maxPages = 3;
        LocalDate cutoff = LocalDate.now().minusDays(config.getLookbackDays());

        for (int page = 1; page <= maxPages; page++) {
            try {
                log.info("Calling Barclays API page {}...", page);

                String url = config.getListUrl() +
                        "&CurrentPage=" + page +
                        "&IsPagination=" + (page > 1 ? "True" : "False");

                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0");
                headers.set("Accept", "application/json");
                headers.set("Referer", "https://search.jobs.barclays/search-jobs/India");
                headers.set("X-Requested-With", "XMLHttpRequest");

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET,
                        new HttpEntity<>(headers), String.class);

                log.info("Barclays page {} response status: {}", page, response.getStatusCode());

                List<Job> pageJobs = parse(response.getBody(), config, cutoff);

                if (pageJobs.isEmpty() && page > 1) {
                    log.info("No new jobs on page {}, stopping pagination", page);
                    break;
                }

                allJobs.addAll(pageJobs);
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("Error fetching Barclays page {}: {}", page, e.getMessage());
                break;
            }
        }

        log.info("Barclays total jobs after all pages: {}", allJobs.size());
        return allJobs;
    }

    private List<Job> parse(String responseBody, JobProviderConfig config,
                            LocalDate cutoff) throws Exception {
        List<Job> jobs = new ArrayList<>();

        JsonNode root = objectMapper.readTree(responseBody);
        String resultsHtml = root.path("results").asText();

        Document doc = Jsoup.parse(resultsHtml);
        Elements jobCards = doc.select(".list-item--card");

        log.info("Barclays total jobs found: {}", jobCards.size());

        for (Element card : jobCards) {

            Element titleLink = card.selectFirst("a.job-title--link");
            if (titleLink == null) continue;

            String href = titleLink.attr("href");
            String title = titleLink.text();
            String jobId = titleLink.attr("data-job-id");

            Element locationEl = card.selectFirst(".job-location");
            String location = locationEl != null
                    ? locationEl.text().trim() : "India";

            Element dateEl = card.selectFirst(".job-date span");
            String dateStr = dateEl != null ? dateEl.text().trim() : "";

            // Parse and filter by date
            if (!dateStr.isEmpty()) {
                try {
                    String dateWithYear = dateStr + " " + LocalDate.now().getYear();
                    LocalDate postedDate = LocalDate.parse(dateWithYear,
                            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));

                    if (postedDate.isBefore(cutoff)) {
                        log.info("Skipping old Barclays job: {} | posted: {}",
                                title, dateStr);
                        continue;
                    }

                    Job job = new Job();
                    job.setId("barclays_" + jobId);
                    job.setExternalId(jobId);
                    job.setCompany("Barclays");
                    job.setTitle(title);
                    job.setLocation(location);
                    job.setUrl(BASE_URL + href);
                    job.setPostedAt(postedDate
                            .atStartOfDay(ZoneId.of("Asia/Kolkata"))
                            .toLocalDateTime());

                    jobs.add(job);
                    log.info("Barclays job: {} | {} | posted: {}",
                            title, location, dateStr);

                } catch (Exception e) {
                    log.warn("Could not parse Barclays date: {}", dateStr);
                }
            }
        }

        log.info("Barclays jobs after date filter: {}", jobs.size());
        return jobs;
    }

    @Override
    public String fetchJobDescription(JobProviderConfig config, String externalId) {
        try {
            String url = config.getJdUrl().replace("{id}", externalId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "text/html");
            headers.set("Referer", BASE_URL + "/search-jobs/India");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            Document doc = Jsoup.parse(response.getBody());

            // Try common JD selectors
            Element jdSection = doc.selectFirst(".job-description");
            if (jdSection == null) jdSection = doc.selectFirst("#job-description");
            if (jdSection == null) jdSection = doc.selectFirst(".description");
            if (jdSection == null) jdSection = doc.selectFirst("main");

            if (jdSection == null) {
                log.warn("No JD found for Barclays job: {}", externalId);
                return "";
            }

            String cleanJD = jdSection.text()
                    .replaceAll("\\s+", " ")
                    .trim();

            log.info("Barclays JD length for {}: {}", externalId, cleanJD.length());
            return cleanJD;

        } catch (Exception e) {
            log.error("Failed to fetch Barclays JD for {}: {}",
                    externalId, e.getMessage());
            return "";
        }
    }
}