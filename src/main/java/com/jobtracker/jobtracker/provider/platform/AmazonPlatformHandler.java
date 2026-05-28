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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AmazonPlatformHandler implements PlatformHandler {

    private static final Logger log = LoggerFactory.getLogger(AmazonPlatformHandler.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Job> fetchJobs(JobProviderConfig config) {
        try {
            log.info("Calling Amazon Jobs API...");

            String url = config.getListUrl();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            log.info("Amazon response status: {}", response.getStatusCode());
            return parse(response.getBody(), config);

        } catch (Exception e) {
            log.error("Error fetching Amazon jobs: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Job> parse(String json, JobProviderConfig config) throws Exception {
        List<Job> jobs = new ArrayList<>();

        JsonNode root = objectMapper.readTree(json);
        JsonNode jobsNode = root.path("jobs");

        log.info("Amazon total jobs: {}", jobsNode.size());

        // Calculate cutoff date based on lookbackDays
        LocalDateTime cutoffDate = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
                .minusDays(config.getLookbackDays());

        for (JsonNode node : jobsNode) {
            String id = node.path("id_icims").asText();
            String title = node.path("title").asText();
            String city = node.path("city").asText();
            String state = node.path("state").asText();
            String countryCode = node.path("country_code").asText();
            String normalizedLocation = node.path("normalized_location").asText();
            String applyUrl = node.path("url_next_step").asText();
            String postedDateStr = node.path("posted_date").asText();
            String updatedTime = node.path("updated_time").asText();

            // Safety filter: Skip if not India
            if (!countryCode.equalsIgnoreCase("IND")) {
                log.info("Skipping non-India Amazon job: {} | Country: {}", title, countryCode);
                continue;
            }

            // Extract full description from the search response (Amazon includes it!)
            String descriptionHtml = node.path("description").asText("");
            String basicQualifications = node.path("basic_qualifications").asText("");
            String preferredQualifications = node.path("preferred_qualifications").asText("");

            // Parse posted date (flexible formats: "May 27, 2026", "1 day", "about 1 month")
            LocalDateTime postedAt = parseAmazonDate(postedDateStr);

            // Skip if older than lookback period
            if (postedAt != null && postedAt.isBefore(cutoffDate)) {
                log.info("Skipping old Amazon job: {} | posted: {}", title, postedDateStr);
                continue;
            }

            // Build location string
            String location = normalizedLocation.isEmpty()
                    ? (city + ", " + state).replaceAll("^,\\s*|,\\s*$", "")
                    : normalizedLocation;

            // Combine all description parts and clean HTML
            String combinedDesc = descriptionHtml + " " + basicQualifications + " " + preferredQualifications;
            String cleanJD = Jsoup.parse(combinedDesc).text()
                    .replaceAll("\\s+", " ")
                    .trim();

            Job job = new Job();
            job.setId("amazon_" + id);
            job.setExternalId(id);
            job.setCompany("Amazon");
            job.setTitle(title);
            job.setLocation(location);
            job.setUrl(applyUrl);
            job.setDescription(cleanJD);  // Full description already available!
            job.setPostedAt(postedAt);

            jobs.add(job);
            log.info("Amazon job: {} | {} | {}", title, location, id);
        }

        log.info("Amazon jobs after date filter: {}", jobs.size());
        return jobs;
    }

    /**
     * Parse Amazon's flexible date formats:
     * - "May 27, 2026" (full date)
     * - "1 day" (relative)
     * - "14 minutes" (relative)
     * - "about 1 month" (relative)
     * - "2 days" (relative)
     */
    private LocalDateTime parseAmazonDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        try {
            // Try parsing absolute date first: "May 27, 2026" or "April 23, 2026"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
            LocalDate date = LocalDate.parse(dateStr, formatter);
            return date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
        } catch (DateTimeParseException e) {
            // Not an absolute date, try relative patterns
        }

        // Parse relative time: "1 day", "14 minutes", "about 1 month", "2 days", "about 3 hours"
        Pattern relativePattern = Pattern.compile("(?:about\\s+)?(\\d+)\\s+(minute|hour|day|week|month|year)s?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = relativePattern.matcher(dateStr);

        if (matcher.find()) {
            int amount = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

            return switch (unit) {
                case "minute" -> now.minusMinutes(amount);
                case "hour" -> now.minusHours(amount);
                case "day" -> now.minusDays(amount);
                case "week" -> now.minusWeeks(amount);
                case "month" -> now.minusMonths(amount);
                case "year" -> now.minusYears(amount);
                default -> now;
            };
        }

        log.warn("Could not parse Amazon date: {}", dateStr);
        return LocalDateTime.now(ZoneId.of("Asia/Kolkata"));  // Default to now if parsing fails
    }

    @Override
    public String fetchJobDescription(JobProviderConfig config, String externalId) {
        // Amazon includes full job description in the search response!
        // No separate JD fetch needed - this method won't be called
        log.info("Amazon JD already fetched in search response for: {}", externalId);
        return "";
    }
}