package com.jobtracker.jobtracker.provider.platform;

import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.provider.config.JobProviderConfig;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WorkdayPlatformHandler implements PlatformHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkdayPlatformHandler.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache external paths for JD fetching
    private final java.util.concurrent.ConcurrentHashMap<String, String> externalPathCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Cache exact start dates from JD API
    private final java.util.concurrent.ConcurrentHashMap<String, LocalDateTime> startDateCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public List<Job> fetchJobs(JobProviderConfig config) {
        try {
            log.info("Calling {} API...", config.getCompanyName());

            // Construct POST payload
            Map<String, Object> payload = new HashMap<>();

            // Applied facets for filtering
            Map<String, Object> appliedFacets = new HashMap<>();
            appliedFacets.put("locationCountry", List.of(config.getLocationCountryId()));
            appliedFacets.put("timeType", List.of(config.getTimeTypeId()));
            appliedFacets.put("jobFamily", List.of(config.getJobFamilyId()));

            payload.put("appliedFacets", appliedFacets);
            payload.put("limit", config.getLimit());
            payload.put("offset", 0);
            payload.put("searchText", config.getKeyword());

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    config.getListUrl(), HttpMethod.POST, request, String.class);

            log.info("{} response status: {}", config.getCompanyName(), response.getStatusCode());
            return parse(response.getBody(), config);

        } catch (Exception e) {
            log.error("Error fetching {} jobs: {}", config.getCompanyName(), e.getMessage());
            return List.of();
        }
    }

    private List<Job> parse(String json, JobProviderConfig config) throws Exception {
        List<Job> jobs = new ArrayList<>();

        JsonNode root = objectMapper.readTree(json);
        JsonNode jobPostings = root.path("jobPostings");

        if (jobPostings.isEmpty()) {
            log.warn("No job postings in {} response", config.getCompanyName());
            return jobs;
        }

        int total = root.path("total").asInt(0);
        log.info("{} total jobs in response: {}", config.getCompanyName(), total);

        LocalDate cutoffDate = LocalDate.now().minusDays(config.getLookbackDays());

        for (JsonNode node : jobPostings) {
            String title = node.path("title").asText();
            String externalPath = node.path("externalPath").asText();
            String location = node.path("locationsText").asText();
            String postedOn = node.path("postedOn").asText("");

            // Extract job reference ID from bulletFields
            JsonNode bulletFields = node.path("bulletFields");
            if (bulletFields.isEmpty() || !bulletFields.isArray()) {
                log.warn("No bulletFields found for job: {}", title);
                continue;
            }
            String externalId = bulletFields.get(0).asText();

            // Parse relative date and apply 2-day filter BEFORE adding to list
            LocalDateTime postedAt = parseRelativeDate(postedOn);
            if (postedAt != null && postedAt.toLocalDate().isBefore(cutoffDate)) {
                log.info("Skipping old {} job: {} | posted: {} | parsed: {}",
                        config.getCompanyName(), title, postedOn, postedAt.toLocalDate());
                continue;
            }

            // Create job object
            Job job = new Job();
            job.setId(config.getCompanyName().toLowerCase()
                    .replaceAll("\\s+", "_") + "_" + externalId);
            job.setExternalId(externalId);
            job.setCompany(config.getCompanyName());
            job.setTitle(title);
            job.setLocation(location);
            job.setUrl(config.getJdUrl().replace("/wday/cxs", "") + externalPath);
            job.setPostedAt(postedAt); // Temporary relative date

            // Cache external path for JD fetching
            externalPathCache.put(externalId, externalPath);

            jobs.add(job);
            log.info("{} job: {} | {} | posted: {} | parsed: {}",
                    config.getCompanyName(), title, location, postedOn,
                    postedAt != null ? postedAt.toLocalDate() : "null");
        }

        log.info("{} jobs after date filter: {}", config.getCompanyName(), jobs.size());
        return jobs;
    }

    @Override
    public String fetchJobDescription(JobProviderConfig config, String externalId) {
        try {
            // Retrieve cached external path
            String externalPath = externalPathCache.get(externalId);
            if (externalPath == null) {
                log.warn("No cached externalPath for {} job: {}",
                        config.getCompanyName(), externalId);
                return "";
            }

            String url = config.getJdUrl() + externalPath;

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode jobPostingInfo = root.path("jobPostingInfo");

            if (jobPostingInfo.isMissingNode()) {
                log.warn("No jobPostingInfo found for {} job: {}",
                        config.getCompanyName(), externalId);
                return "";
            }

            // Extract and cache exact start date
            String startDateStr = jobPostingInfo.path("startDate").asText("");
            if (!startDateStr.isEmpty()) {
                try {
                    LocalDateTime exactDate = parseStartDate(startDateStr);
                    startDateCache.put(externalId, exactDate);
                    log.info("{} cached exact startDate for {}: {}",
                            config.getCompanyName(), externalId, exactDate);
                } catch (Exception e) {
                    log.debug("Could not parse start date: {}", startDateStr);
                }
            }

            // Extract and parse job description HTML
            String jobDescHtml = jobPostingInfo.path("jobDescription").asText("");

            String cleanJD = Jsoup.parse(jobDescHtml).text()
                    .replaceAll("\\s+", " ")
                    .trim();

            log.info("{} JD length for {}: {}",
                    config.getCompanyName(), externalId, cleanJD.length());
            return cleanJD;

        } catch (Exception e) {
            log.error("Failed to fetch {} JD for {}: {}",
                    config.getCompanyName(), externalId, e.getMessage());
            return "";
        }
    }

    /**
     * Parses relative date strings like "Posted Today" or "Posted 3 Days Ago"
     * into LocalDateTime at midnight IST.
     *
     * @param postedOn Relative date string from API
     * @return LocalDateTime at midnight IST, or null if parsing fails
     */
    private LocalDateTime parseRelativeDate(String postedOn) {
        if (postedOn == null || postedOn.isEmpty()) {
            return null;
        }

        try {
            // Handle "Posted Today"
            if (postedOn.equalsIgnoreCase("Posted Today")) {
                return LocalDate.now()
                        .atStartOfDay(ZoneId.of("Asia/Kolkata"))
                        .toLocalDateTime();
            }

            // Handle "Posted X Days Ago" or "Posted X Day Ago"
            Pattern pattern = Pattern.compile("Posted (\\d+) Days? Ago", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(postedOn);

            if (matcher.find()) {
                int daysAgo = Integer.parseInt(matcher.group(1));
                return LocalDate.now()
                        .minusDays(daysAgo)
                        .atStartOfDay(ZoneId.of("Asia/Kolkata"))
                        .toLocalDateTime();
            }

            log.warn("Could not parse relative date: {}", postedOn);
            return null;

        } catch (Exception e) {
            log.error("Error parsing relative date '{}': {}", postedOn, e.getMessage());
            return null;
        }
    }

    /**
     * Parses exact date string in YYYY-MM-DD format to LocalDateTime at midnight IST.
     *
     * @param startDate Date string in YYYY-MM-DD format
     * @return LocalDateTime at midnight IST
     */
    private LocalDateTime parseStartDate(String startDate) {
        LocalDate date = LocalDate.parse(startDate);
        return date.atStartOfDay(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime();
    }

    /**
     * Retrieves the exact start date from cache (populated during fetchJobDescription).
     * Returns null if not available. Cache entry is removed after retrieval.
     *
     * @param externalId The job's external ID
     * @return Exact start date in IST, or null if not cached
     */
    public LocalDateTime getAndClearCachedStartDate(String externalId) {
        return startDateCache.remove(externalId);
    }
}