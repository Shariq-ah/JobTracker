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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class AmericanExpressJobProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(AmericanExpressJobProvider.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://egug.fa.us2.oraclecloud.com/hcmRestApi/resources/latest";

    @Override
    public String getCompanyName() {
        return "American Express";
    }

    @Override
    public List<Job> fetchJobs() {
        try {
            log.info("Calling American Express API...");

            String url = BASE_URL + "/recruitingCEJobRequisitions" +
                    "?onlyData=true" +
                    "&expand=requisitionList.workLocation,requisitionList.otherWorkLocations," +
                    "requisitionList.secondaryLocations,flexFieldsFacet.values," +
                    "requisitionList.requisitionFlexFields" +
                    "&finder=findReqs;siteNumber=CX_1," +
                    "facetsList=LOCATIONS;WORK_LOCATIONS;WORKPLACE_TYPES;TITLES;CATEGORIES;" +
                    "ORGANIZATIONS;POSTING_DATES;FLEX_FIELDS," +
                    "limit=25," +
                    "keyword=\"Software Engineer\"," +
                    "lastSelectedFacet=POSTING_DATES," +
                    "location=India," +
                    "selectedPostingDatesFacet=7," +
                    "sortBy=POSTING_DATES_DESC";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            headers.set("Referer", "https://aexp.eightfold.ai/careers");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            log.info("AmEx response status: {}", response.getStatusCode());
            return parse(response.getBody());

        } catch (Exception e) {
            log.error("Error fetching AmEx jobs: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Job> parse(String json) throws Exception {
        List<Job> jobs = new ArrayList<>();

        JsonNode root = objectMapper.readTree(json);
        JsonNode items = root.path("items");

        if (items.isEmpty()) {
            log.warn("No items in AmEx response");
            return jobs;
        }

        JsonNode requisitionList = items.get(0).path("requisitionList");
        log.info("AmEx total jobs: {}", requisitionList.size());

        for (JsonNode node : requisitionList) {

            // Date filter — skip jobs older than yesterday
            String postedDate = node.path("PostedDate").asText("");
            if (!postedDate.isEmpty()) {
                LocalDate posted = LocalDate.parse(postedDate);
                LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(2);
                if (posted.isBefore(cutoff)) {
                    log.info("Skipping old AmEx job: {} | posted: {}",
                            node.path("Title").asText(), postedDate);
                    continue;
                }
            }

            String id = node.path("Id").asText();
            String title = node.path("Title").asText();
            String location = node.path("PrimaryLocation").asText();

            Job job = new Job();
            job.setId("amex_" + id);
            job.setExternalId(id);
            job.setCompany("American Express");
            job.setTitle(title);
            job.setLocation(location);
            job.setUrl("https://egug.fa.us2.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1/job/" + id);

            jobs.add(job);
            log.info("AmEx job: {} | {} | posted: {}", title, location, postedDate);
        }

        log.info("AmEx jobs after date filter: {}", jobs.size());
        return jobs;
    }

    @Override
    public String fetchJobDescription(String externalId) {
        try {
            String url = BASE_URL + "/recruitingCEJobRequisitionDetails" +
                    "?expand=all&onlyData=true" +
                    "&finder=ById;Id=\"" + externalId + "\",siteNumber=CX_1";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            headers.set("Referer", "https://aexp.eightfold.ai/careers");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("items");

            if (items.isEmpty()) {
                log.warn("No JD found for AmEx job: {}", externalId);
                return "";
            }

            JsonNode item = items.get(0);

            // Combine all relevant sections
            String descHtml         = item.path("ExternalDescriptionStr").asText("");
            String respHtml         = item.path("ExternalResponsibilitiesStr").asText("");
            String qualHtml         = item.path("ExternalQualificationsStr").asText("");

            String combined = descHtml + " " + respHtml + " " + qualHtml;

            String cleanJD = Jsoup.parse(combined).text()
                    .replaceAll("\\s+", " ")
                    .trim();

            log.info("AmEx JD length for {}: {}", externalId, cleanJD.length());
            return cleanJD;

        } catch (Exception e) {
            log.error("Failed to fetch AmEx JD for {}: {}", externalId, e.getMessage());
            return "";
        }
    }
}
