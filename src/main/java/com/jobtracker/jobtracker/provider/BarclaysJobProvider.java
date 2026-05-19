package com.jobtracker.jobtracker.provider;

import com.jobtracker.jobtracker.model.Job;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class BarclaysJobProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(BarclaysJobProvider.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://search.jobs.barclays";

    @Override
    public String getCompanyName() {
        return "Barclays";
    }

    @Override
    public List<Job> fetchJobs() {
        List<Job> allJobs = new ArrayList<>();
        int page = 1;
        int maxPages = 3; // fetch first 3 pages = 48 jobs, enough for recent ones

        while (page <= maxPages) {
            try {
                log.info("Calling Barclays API page {}...", page);

                String url = BASE_URL + "/search-jobs/results" +
                        "?ActiveFacetID=79683" +
                        "&CurrentPage=" + page +
                        "&RecordsPerPage=16" +
                        "&Distance=50" +
                        "&RadiusUnitType=0" +
                        "&Keywords=Java" +
                        "&Location=" +
                        "&ShowRadius=False" +
                        "&IsPagination=" + (page > 1 ? "True" : "False") +
                        "&FacetFilters[0].ID=79683" +
                        "&FacetFilters[0].FacetType=1" +
                        "&FacetFilters[0].Count=229" +
                        "&FacetFilters[0].Display=Development and Engineering" +
                        "&FacetFilters[0].IsApplied=true" +
                        "&FacetFilters[0].FieldName=" +
                        "&FacetFilters[1].ID=44699" +
                        "&FacetFilters[1].FacetType=1" +
                        "&FacetFilters[1].Count=421" +
                        "&FacetFilters[1].Display=Technology" +
                        "&FacetFilters[1].IsApplied=true" +
                        "&FacetFilters[1].FieldName=" +
                        "&FacetFilters[2].ID=1269750" +
                        "&FacetFilters[2].FacetType=2" +
                        "&FacetFilters[2].Count=421" +
                        "&FacetFilters[2].Display=India" +
                        "&FacetFilters[2].IsApplied=true" +
                        "&FacetFilters[2].FieldName=" +
                        "&SearchResultsModuleName=Search Results" +
                        "&SearchFiltersModuleName=Search Filters" +
                        "&SortCriteria=1" +
                        "&SortDirection=1" +
                        "&SearchType=1" +
                        "&OrganizationIds=13015" +
                        "&ResultsType=0";

                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Mozilla/5.0");
                headers.set("Accept", "application/json");
                headers.set("Referer", "https://search.jobs.barclays/search-jobs/India");
                headers.set("X-Requested-With", "XMLHttpRequest");

                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                log.info("Barclays page {} response status: {}", page, response.getStatusCode());

                List<Job> pageJobs = parse(response.getBody());

                // If all jobs on this page were filtered out as old, stop paginating
                if (pageJobs.isEmpty() && page > 1) {
                    log.info("No new jobs on page {}, stopping pagination", page);
                    break;
                }

                allJobs.addAll(pageJobs);
                page++;

                // Small delay between page requests
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("Error fetching Barclays page {}: {}", page, e.getMessage());
                break;
            }
        }

        log.info("Barclays total jobs after all pages: {}", allJobs.size());
        return allJobs;
    }

    private List<Job> parse(String responseBody) throws Exception {
        List<Job> jobs = new ArrayList<>();

        JsonNode root = objectMapper.readTree(responseBody);
        String resultsHtml = root.path("results").asText();

        Document doc = Jsoup.parse(resultsHtml);
        Elements jobCards = doc.select(".list-item--card");

        log.info("Barclays total jobs found: {}", jobCards.size());

        // Date filter — only last 2 days
        LocalDate cutoff = LocalDate.now().minusDays(2);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

        for (Element card : jobCards) {

            // Extract job link and ID
            Element titleLink = card.selectFirst("a.job-title--link");
            if (titleLink == null) continue;

            String href = titleLink.attr("href");
            String title = titleLink.text();
            String jobId = card.selectFirst("a.job-title--link").attr("data-job-id");

            // Extract location
            Element locationEl = card.selectFirst(".job-location");
            String location = locationEl != null ? locationEl.text().trim() : "India";

            // Extract date
            Element dateEl = card.selectFirst(".job-date span");
            String dateStr = dateEl != null ? dateEl.text().trim() : "";

            // Filter by date — skip old jobs
            if (!dateStr.isEmpty()) {
                try {
                    // Date format is "13 May" — add current year
                    String dateWithYear = dateStr + " " + LocalDate.now().getYear();
                    LocalDate postedDate = LocalDate.parse(dateWithYear,
                            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));

                    if (postedDate.isBefore(cutoff)) {
                        log.info("Skipping old Barclays job: {} | posted: {}", title, dateStr);
                        continue;
                    }
                } catch (Exception e) {
                    log.warn("Could not parse date: {}", dateStr);
                }
            }

            Job job = new Job();
            job.setId("barclays_" + jobId);
            job.setExternalId(jobId);
            job.setCompany("Barclays");
            job.setTitle(title);
            job.setLocation(location);
            job.setUrl(BASE_URL + href);

            jobs.add(job);
            log.info("Barclays job: {} | {} | posted: {}", title, location, dateStr);
        }

        log.info("Barclays jobs after date filter: {}", jobs.size());
        return jobs;
    }

    @Override
    public String fetchJobDescription(String externalId) {
        try {
            // Barclays job detail page
            String url = BASE_URL + "/job/pune/software-engineer/13015/" + externalId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "text/html");
            headers.set("Referer", BASE_URL + "/search-jobs/India");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            Document doc = Jsoup.parse(response.getBody());

            // Try common JD selectors
            Element jdSection = doc.selectFirst(".job-description");
            if (jdSection == null) jdSection = doc.selectFirst("#job-description");
            if (jdSection == null) jdSection = doc.selectFirst(".description");

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
            log.error("Failed to fetch Barclays JD for {}: {}", externalId, e.getMessage());
            return "";
        }
    }
}