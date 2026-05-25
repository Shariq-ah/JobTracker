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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GoldmanSachsPlatformHandler implements PlatformHandler {

    private static final Logger log = LoggerFactory.getLogger(GoldmanSachsPlatformHandler.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://higher.gs.com";

    @Override
    public List<Job> fetchJobs(JobProviderConfig config) {
        try {
            log.info("Calling Goldman Sachs API...");

            // GraphQL query - updated structure
            String query = """
                    {
                      "operationName": "GetRoles",
                      "variables": {
                        "searchQueryInput": {
                          "page": {
                            "pageSize": %d,
                            "pageNumber": 0
                          },
                          "sort": {
                            "sortStrategy": "POSTED_DATE",
                            "sortOrder": "DESC"
                          },
                          "filters": [
                            {
                              "filterCategoryType": "EXPERIENCE_LEVEL",
                              "filters": [
                                {"filter": "Analyst", "subFilters": []},
                                {"filter": "Associate", "subFilters": []}
                              ]
                            },
                            {
                              "filterCategoryType": "JOB_FUNCTION",
                              "filters": [
                                {"filter": "Software Engineering", "subFilters": []}
                              ]
                            },
                            {
                              "filterCategoryType": "LOCATION",
                              "filters": [
                                {
                                  "filter": "India",
                                  "subFilters": [
                                    {"filter": "Karnataka", "subFilters": [{"filter": "Bengaluru", "subFilters": []}]},
                                    {"filter": "Maharashtra", "subFilters": [{"filter": "Mumbai", "subFilters": []}]},
                                    {"filter": "Telangana", "subFilters": [{"filter": "Hyderabad", "subFilters": []}]}
                                  ]
                                }
                              ]
                            },
                            {
                              "filterCategoryType": "SKILLSET",
                              "filters": [
                                {"filter": "Software Engineering", "subFilters": []}
                              ]
                            }
                          ],
                          "experiences": ["EARLY_CAREER", "PROFESSIONAL"],
                          "searchTerm": "Java"
                        }
                      },
                      "query": "query GetRoles($searchQueryInput: RoleSearchQueryInput!) { roleSearch(searchQueryInput: $searchQueryInput) { totalCount items { roleId corporateTitle jobTitle jobFunction locations { primary state country city __typename } status division skills jobType { code description __typename } externalSource { sourceId __typename } __typename } __typename } }"
                    }
                    """.formatted(config.getLimit());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            headers.set("Referer", "https://higher.gs.com/roles");

            ResponseEntity<String> response = restTemplate.exchange(
                    config.getListUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(query, headers),
                    String.class);

            log.info("Goldman Sachs response status: {}", response.getStatusCode());
            return parse(response.getBody(), config);

        } catch (Exception e) {
            log.error("Error fetching Goldman Sachs jobs: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Job> parse(String json, JobProviderConfig config) throws Exception {
        List<Job> jobs = new ArrayList<>();

        JsonNode root = objectMapper.readTree(json);
        JsonNode items = root.path("data").path("roleSearch").path("items");
        int totalCount = root.path("data").path("roleSearch").path("totalCount").asInt(0);

        log.info("Goldman Sachs total count: {}, items size: {}", totalCount, items.size());

        for (JsonNode node : items) {

            String roleId = node.path("roleId").asText("");
            if (roleId.isEmpty()) continue;

            // Extract numeric ID (first part before underscore)
            String id = roleId.contains("_")
                    ? roleId.split("_")[0] : roleId;

            String externalSourceId = node.path("externalSource")
                    .path("sourceId").asText("");

            String jobTitle = node.path("jobTitle").asText();

            // Extract location from locations array (use primary location)
            JsonNode locations = node.path("locations");
            String location = "";
            if (locations.isArray() && locations.size() > 0) {
                JsonNode primaryLocation = locations.get(0);
                String city = primaryLocation.path("city").asText("");
                String state = primaryLocation.path("state").asText("");
                String country = primaryLocation.path("country").asText("");
                location = city + (state.isEmpty() ? "" : ", " + state) + (country.isEmpty() ? "" : ", " + country);
            }

            Job job = new Job();
            job.setId("gs_" + id);
            job.setExternalId(externalSourceId);
            job.setCompany("Goldman Sachs");
            job.setTitle(jobTitle);
            job.setLocation(location);
            job.setUrl(BASE_URL + "/roles/" + id);
            // Goldman API has no date field — rely on DB dedup

            jobs.add(job);
            log.info("Goldman Sachs job: {} | {} ({})", jobTitle, location, id);
        }

        log.info("Goldman Sachs jobs fetched: {}", jobs.size());
        return jobs;
    }

    @Override
    public String fetchJobDescription(JobProviderConfig config, String externalId) {
        try {
            String query = """
                    {
                      "operationName": "GetRoleById",
                      "variables": {
                        "externalSourceId": "%s",
                        "externalSourceFetch": true
                      },
                      "query": "query GetRoleById($externalSourceId: String!, $externalSourceFetch: Boolean) { role(externalSourceId: $externalSourceId, externalSourceFetch: $externalSourceFetch) { roleId corporateTitle jobTitle jobFunction locations { primary state country city __typename } division descriptionHtml jobType { code description __typename } skillset compensation { minSalary maxSalary currency __typename } applyActive status externalSource { externalApplicationUrl applyInExternalSource sourceId secondarySourceId __typename } __typename } }"
                    }
                    """.formatted(externalId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            headers.set("Referer", "https://higher.gs.com/roles");

            ResponseEntity<String> response = restTemplate.exchange(
                    config.getJdUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(query, headers),
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode role = root.path("data").path("role");

            String descriptionHtml = role.path("descriptionHtml").asText("");

            String cleanJD = Jsoup.parse(descriptionHtml).text()
                    .replaceAll("\\s+", " ")
                    .trim();

            log.info("Goldman JD length for {}: {}", externalId, cleanJD.length());
            return cleanJD;

        } catch (Exception e) {
            log.error("Failed to fetch Goldman JD for {}: {}",
                    externalId, e.getMessage());
            return "";
        }
    }
}