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

import java.util.ArrayList;
import java.util.List;



@Component
public class GoldmanSachsJobProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(GoldmanSachsJobProvider.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Job> fetchJobs() {

        try {
            log.info("Calling Goldman API...");
            String url = "https://api-higher.gs.com/gateway/api/v1/graphql";

            String body = """
{
  "operationName": "GetRoles",
  "variables": {
    "searchQueryInput": {
      "page": {
        "pageSize": 20,
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
            {
              "filter": "Associate",
              "subFilters": []
            }
          ]
        },
        {
          "filterCategoryType": "JOB_FUNCTION",
          "filters": [
            {
              "filter": "Software Engineering",
              "subFilters": []
            }
          ]
        },
        {
          "filterCategoryType": "LOCATION",
          "filters": [
            {
              "filter": "India",
              "subFilters": [
                {
                  "filter": "Karnataka",
                  "subFilters": [
                    {
                      "filter": "Bengaluru",
                      "subFilters": []
                    }
                  ]
                },
                {
                  "filter": "Maharashtra",
                  "subFilters": [
                    {
                      "filter": "Mumbai",
                      "subFilters": []
                    }
                  ]
                },
                {
                  "filter": "Telangana",
                  "subFilters": [
                    {
                      "filter": "Hyderabad",
                      "subFilters": []
                    }
                  ]
                }
              ]
            }
          ]
        }
      ],
      "experiences": ["EARLY_CAREER", "PROFESSIONAL"],
      "searchTerm": "software engineering"
    }
  },
  "query": "query GetRoles($searchQueryInput: RoleSearchQueryInput!) { roleSearch(searchQueryInput: $searchQueryInput) { totalCount items { roleId corporateTitle jobTitle jobFunction locations { primary state country city __typename } status division skills jobType { code description __typename } externalSource { sourceId __typename } __typename } __typename } }"
}
""";


            HttpEntity<String> entity = new HttpEntity<>(body, getHeaders());

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            log.info("Raw Response: {}", response.getBody());

            return parse(response.getBody());

        } catch (Exception e){
            e.printStackTrace();
            return List.of();
        }
    }


    public HttpHeaders getHeaders(){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/5.0");
        headers.set("Accept", "*/*");
        headers.set("Origin", "https://higher.gs.com");
        headers.set("Referer", "https://higher.gs.com/results");
        headers.set("Connection", "keep-alive");

        return headers;
    }

    @Override
    public String fetchJobDescription(String externalId) {

        String url = "https://api-higher.gs.com/gateway/api/v1/graphql";

        String body = """
    {
      "operationName": "GetRoleById",
      "variables": {
        "externalSourceId": "%s",
        "externalSourceFetch": true
      },
      "query": "query GetRoleById($externalSourceId: String!, $externalSourceFetch: Boolean) { role(externalSourceId: $externalSourceId, externalSourceFetch: $externalSourceFetch) { descriptionHtml } }"
    }
    """.formatted(externalId);

        HttpEntity<String> entity = new HttpEntity<>(body, getHeaders());

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        log.info("JD RAW RESPONSE: {}", response.getBody());

        try {
            JsonNode root = objectMapper.readTree(response.getBody());

            String html = root.path("data")
                    .path("role")
                    .path("descriptionHtml")
                    .asText();

            String cleanJD = Jsoup.parse(html).text()
                    .replaceAll("\\s+", " ")
                    .trim();

            log.info("JD LENGTH: {}", cleanJD.length());

            return cleanJD;

        } catch (Exception e) {
            log.error("Failed to parse JD for externalId: {}", externalId, e);
            return "";
        }
    }


    private List<Job> parse(String json) throws Exception{

        List<Job> jobs = new ArrayList<>();

        log.info("Parsing response...");

        JsonNode items = objectMapper.readTree(json)
                .path("data")
                .path("roleSearch")
                .path("items");

        log.info("Items size: {}", items.size());

        for (JsonNode item : items){
            String rawId = item.path("roleId").asText();
            String title = item.path("jobTitle").asText();
            String city = item.path("locations").get(0).path("city").asText();
            String id = rawId.split("_")[0];
            log.info("Job found: {} ({})", title, id);

            String externalId = item.path("externalSource")
                    .path("sourceId")
                    .asText();

            Job job = new Job();
            job.setId(id);
            job.setExternalId(externalId);
            job.setCompany("Goldman Sachs");
            job.setTitle(title);
            job.setLocation(city);
            job.setUrl("https://higher.gs.com/roles/" + id);

            jobs.add(job);
        }
        return jobs;
    }
}
