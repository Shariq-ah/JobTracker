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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class OracleHcmPlatformHandler implements PlatformHandler {

    private static final Logger log = LoggerFactory.getLogger(OracleHcmPlatformHandler.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache to store exact posted times fetched during JD retrieval
    private final java.util.concurrent.ConcurrentHashMap<String, LocalDateTime> postedTimeCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public List<Job> fetchJobs(JobProviderConfig config) {
        try {
            log.info("Calling {} API...", config.getCompanyName());

            // Build base URL with common parameters
            StringBuilder urlBuilder = new StringBuilder(config.getListUrl());
            urlBuilder.append("?onlyData=true")
                    .append("&expand=requisitionList.workLocation,requisitionList.otherWorkLocations,")
                    .append("requisitionList.secondaryLocations,flexFieldsFacet.values,")
                    .append("requisitionList.requisitionFlexFields")
                    .append("&finder=findReqs;siteNumber=").append(config.getSiteNumber()).append(",")
                    .append("facetsList=LOCATIONS;WORK_LOCATIONS;WORKPLACE_TYPES;TITLES;CATEGORIES;")
                    .append("ORGANIZATIONS;POSTING_DATES;FLEX_FIELDS,")
                    .append("limit=").append(config.getLimit()).append(",")
                    .append("keyword=\"").append(config.getKeyword()).append("\",");

            // Add lastSelectedFacet (different for AmEx vs JPMC)
            if (config.getSelectedFlexFieldsFacets() != null) {
                urlBuilder.append("lastSelectedFacet=AttributeChar6,");
            } else {
                urlBuilder.append("lastSelectedFacet=POSTING_DATES,");
            }

            // Add optional location filters
            if (config.getLocationFilter() != null && !config.getLocationFilter().isEmpty()) {
                urlBuilder.append("location=").append(config.getLocationFilter()).append(",");
            }
            if (config.getLocationId() != null) {
                urlBuilder.append("locationId=").append(config.getLocationId()).append(",");
            }
            if (config.getSelectedLocationsFacet() != null) {
                urlBuilder.append("selectedLocationsFacet=").append(config.getSelectedLocationsFacet()).append(",");
            }

            // Add optional category/flex field filters
            if (config.getSelectedCategoriesFacet() != null) {
                urlBuilder.append("selectedCategoriesFacet=").append(config.getSelectedCategoriesFacet()).append(",");
            }
            if (config.getSelectedFlexFieldsFacets() != null) {
                urlBuilder.append("selectedFlexFieldsFacets=").append(config.getSelectedFlexFieldsFacets()).append(",");
            }

            // Add posting date and sort
            urlBuilder.append("selectedPostingDatesFacet=7,")
                    .append("sortBy=").append(config.getSortBy());

            String url = urlBuilder.toString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

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
        JsonNode items = root.path("items");

        if (items.isEmpty()) {
            log.warn("No items in {} response", config.getCompanyName());
            return jobs;
        }

        JsonNode requisitionList = items.get(0).path("requisitionList");
        log.info("{} total jobs: {}", config.getCompanyName(), requisitionList.size());

        for (JsonNode node : requisitionList) {

            // Date filter
            String postedDate = node.path("PostedDate").asText("");
            if (!postedDate.isEmpty()) {
                LocalDate posted = LocalDate.parse(postedDate);
                LocalDate cutoff = LocalDate.now(ZoneOffset.UTC)
                        .minusDays(config.getLookbackDays());
                if (posted.isBefore(cutoff)) {
                    log.info("Skipping old {} job: {} | posted: {}",
                            config.getCompanyName(),
                            node.path("Title").asText(),
                            postedDate);
                    continue;
                }
            }

            // Country filter
            String country = node.path("PrimaryLocationCountry").asText("");
            if (!country.isEmpty() && !country.equals(config.getCountryCode())) {
                continue;
            }

            String id = node.path("Id").asText();
            String title = node.path("Title").asText();
            String location = node.path("PrimaryLocation").asText();

            Job job = new Job();
            job.setId(config.getCompanyName().toLowerCase()
                    .replaceAll("\\s+", "_") + "_" + id);
            job.setExternalId(id);
            job.setCompany(config.getCompanyName());
            job.setTitle(title);
            job.setLocation(location);
            job.setUrl(config.getJobUrlTemplate().replace("{id}", id));

            // Set postedAt in IST
            if (!postedDate.isEmpty()) {
                job.setPostedAt(LocalDate.parse(postedDate)
                        .atStartOfDay(ZoneId.of("Asia/Kolkata"))
                        .toLocalDateTime());
            }

            jobs.add(job);
            log.info("{} job: {} | {} | posted: {}",
                    config.getCompanyName(), title, location, postedDate);
        }

        log.info("{} jobs after date filter: {}", config.getCompanyName(), jobs.size());
        return jobs;
    }

    @Override
    public String fetchJobDescription(JobProviderConfig config, String externalId) {
        try {
            String url = config.getJdUrl() +
                    "?expand=all&onlyData=true" +
                    "&finder=ById;Id=\"" + externalId + "\"" +
                    ",siteNumber=" + config.getSiteNumber();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("items");

            if (items.isEmpty()) {
                log.warn("No JD found for {} job: {}",
                        config.getCompanyName(), externalId);
                return "";
            }

            JsonNode item = items.get(0);

            // Extract and cache exact posted time while we have the response
            String postedAtStr = item.path("ExternalPostedStartDate").asText("");
            if (!postedAtStr.isEmpty()) {
                try {
                    LocalDateTime exactPostedAt = OffsetDateTime.parse(postedAtStr)
                            .atZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                            .toLocalDateTime();
                    postedTimeCache.put(externalId, exactPostedAt);
                    log.info("{} cached exact postedAt for {}: {}",
                            config.getCompanyName(), externalId, exactPostedAt);
                } catch (Exception e) {
                    log.debug("Could not parse posted time: {}", postedAtStr);
                }
            }

            // Combine all JD sections
            String descHtml = item.path("ExternalDescriptionStr").asText("");
            String respHtml = item.path("ExternalResponsibilitiesStr").asText("");
            String qualHtml = item.path("ExternalQualificationsStr").asText("");

            String combined = descHtml + " " + respHtml + " " + qualHtml;

            String cleanJD = Jsoup.parse(combined).text()
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
     * Retrieves the exact posted time from cache (populated during fetchJobDescription).
     * Returns null if not available. Cache entry is removed after retrieval.
     *
     * @param externalId The job's external ID
     * @return Exact posted timestamp in IST, or null if not cached
     */
    public LocalDateTime getAndClearCachedPostedTime(String externalId) {
        return postedTimeCache.remove(externalId);
    }
}