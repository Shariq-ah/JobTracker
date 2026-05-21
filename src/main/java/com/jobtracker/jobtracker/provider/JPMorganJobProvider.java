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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class JPMorganJobProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(JPMorganJobProvider.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://jpmc.fa.oraclecloud.com/hcmRestApi/resources/latest/recruitingCEJobRequisitions";

    @Override
    public String getCompanyName() {
        return "JPMorgan Chase";
    }

    @Override
    public List<Job> fetchJobs() {
        try {
            log.info("Calling JPMorgan API...");

            String url = "https://jpmc.fa.oraclecloud.com/hcmRestApi/resources/latest/recruitingCEJobRequisitions" +
                    "?onlyData=true" +
                    "&expand=requisitionList.workLocation,requisitionList.otherWorkLocations," +
                    "requisitionList.secondaryLocations,flexFieldsFacet.values," +
                    "requisitionList.requisitionFlexFields" +
                    "&finder=findReqs;siteNumber=CX_1001," +
                    "facetsList=LOCATIONS;WORK_LOCATIONS;WORKPLACE_TYPES;TITLES;CATEGORIES;" +
                    "ORGANIZATIONS;POSTING_DATES;FLEX_FIELDS," +
                    "limit=25," +
                    "lastSelectedFacet=POSTING_DATES," +
                    "selectedCategoriesFacet=300000086152753," +
                    "selectedPostingDatesFacet=7," +
                    "sortBy=POSTING_DATES_DESC";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            headers.set("Referer", "https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/jobs");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            log.info("JPMorgan response status: {}", response.getStatusCode());
            return parse(response.getBody());

        } catch (Exception e) {
            log.error("Error fetching JPMorgan jobs: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Job> parse(String json) throws Exception {
        List<Job> jobs = new ArrayList<>();

        JsonNode root = objectMapper.readTree(json);
        JsonNode items = root.path("items");

        if (items.isEmpty()) {
            log.warn("No items in JPMorgan response");
            return jobs;
        }

        JsonNode requisitionList = items.get(0).path("requisitionList");
        log.info("JPMorgan total jobs: {}", requisitionList.size());

        for (JsonNode node : requisitionList) {

            String country = node.path("PrimaryLocationCountry").asText();

            // Filter India only
            if (!"IN".equals(country)) continue;

            String id = node.path("Id").asText();
            String title = node.path("Title").asText();
            String location = node.path("PrimaryLocation").asText();
            String shortDesc = node.path("ShortDescriptionStr").asText("");
            String postedDate = node.path("PostedDate").asText("");

            Job job = new Job();
            job.setId("jpmc_" + id);
            job.setExternalId(id);
            job.setCompany("JPMorgan Chase");
            job.setTitle(title);
            if (!postedDate.isEmpty()) {
                job.setPostedAt(LocalDate.parse(postedDate)
                        .atStartOfDay());
            }
            job.setLocation(location);
            job.setDescription(shortDesc);
            job.setUrl("https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/job/" + id);

            jobs.add(job);
            log.info("JPMorgan job: {} | {} | posted: {}", title, location, postedDate);
        }

        log.info("JPMorgan India jobs after filter: {}", jobs.size());
        return jobs;
    }

    @Override
    public String fetchJobDescription(String externalId) {
        try {
            String url = "https://jpmc.fa.oraclecloud.com/hcmRestApi/resources/latest/recruitingCEJobRequisitionDetails" +
                    "?expand=all&onlyData=true" +
                    "&finder=ById;Id=\"" + externalId + "\",siteNumber=CX_1001";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            headers.set("Referer", "https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/jobs");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("items");

            if (items.isEmpty()) {
                log.warn("No JD found for JPMorgan job: {}", externalId);
                return "";
            }

            String html = items.get(0).path("ExternalDescriptionStr").asText("");

            String cleanJD = Jsoup.parse(html).text()
                    .replaceAll("\\s+", " ")
                    .trim();

            log.info("JPMorgan JD length for {}: {}", externalId, cleanJD.length());

            return cleanJD;

        } catch (Exception e) {
            log.error("Failed to fetch JPMorgan JD for {}: {}", externalId, e.getMessage());
            return "";
        }
    }
}