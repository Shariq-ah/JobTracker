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

            // GraphQL query
            String query = """
                    {
                      "operationName": "GetRoles",
                      "variables": {
                        "filters": {
                          "level": ["Associate"],
                          "division": ["Software Engineering"],
                          "location": ["Bengaluru","Mumbai","Hyderabad"],
                          "region": ["India"]
                        },
                        "order": "POSTED_DATE_DESC",
                        "page": 0,
                        "pageSize": %d
                      },
                      "query": "query GetRoles($filters: RoleFiltersInput, $order: String, $page: Int, $pageSize: Int) { roles(filters: $filters, order: $order, page: $page, pageSize: $pageSize) { hits { roleId title primaryLocation division level externalSource { sourceId } } total } }"
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
        JsonNode hits = root.path("data").path("roles").path("hits");

        log.info("Goldman Sachs items size: {}", hits.size());

        for (JsonNode node : hits) {

            String roleId = node.path("roleId").asText("");
            if (roleId.isEmpty()) continue;

            // Extract numeric ID
            String id = roleId.contains("_")
                    ? roleId.split("_")[0] : roleId;

            String externalSourceId = node.path("externalSource")
                    .path("sourceId").asText("");

            String title = node.path("title").asText();
            String location = node.path("primaryLocation").asText();

            Job job = new Job();
            job.setId("gs_" + id);
            job.setExternalId(externalSourceId);
            job.setCompany("Goldman Sachs");
            job.setTitle(title);
            job.setLocation(location);
            job.setUrl(BASE_URL + "/roles/" + id);
            // Goldman API has no date field — rely on DB dedup

            jobs.add(job);
            log.info("Goldman Sachs job: {} | {} ({})", title, location, id);
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
                        "externalSourceId": "%s"
                      },
                      "query": "query GetRoleById($externalSourceId: String) { role(externalSourceId: $externalSourceId) { title description qualifications } }"
                    }
                    """.formatted(externalId);

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

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode role = root.path("data").path("role");

            String description = role.path("description").asText("");
            String qualifications = role.path("qualifications").asText("");

            String combined = description + " " + qualifications;

            String cleanJD = Jsoup.parse(combined).text()
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