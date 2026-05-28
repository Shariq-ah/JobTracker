package com.jobtracker.jobtracker.provider.config;

import com.jobtracker.jobtracker.provider.DynamicJobProvider;
import com.jobtracker.jobtracker.provider.JobProvider;
import com.jobtracker.jobtracker.provider.platform.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ProviderRegistry {

    private final OracleHcmPlatformHandler oracleHcmHandler;
    private final MicrosoftPlatformHandler microsoftHandler;
    private final BarclaysPlatformHandler barclaysHandler;
    private final GoldmanSachsPlatformHandler goldmanHandler;
    private final WorkdayPlatformHandler workdayHandler;
    private final AmazonPlatformHandler amazonHandler;

    public ProviderRegistry(
            OracleHcmPlatformHandler oracleHcmHandler,
            MicrosoftPlatformHandler microsoftHandler,
            BarclaysPlatformHandler barclaysHandler,
            GoldmanSachsPlatformHandler goldmanHandler,
            WorkdayPlatformHandler workdayHandler,
            AmazonPlatformHandler amazonHandler) {
        this.oracleHcmHandler = oracleHcmHandler;
        this.microsoftHandler = microsoftHandler;
        this.barclaysHandler = barclaysHandler;
        this.goldmanHandler = goldmanHandler;
        this.workdayHandler = workdayHandler;
        this.amazonHandler = amazonHandler;
    }

    @Bean
    public List<JobProvider> jobProviders() {
        return List.of(
                amex(),
                jpmorgan(),
                barclays(),
                goldman(),
                microsoft(),
                visa(),
                amazon()
        );
    }

    // ─── American Express ───────────────────────────────────────────
    private JobProvider amex() {
        return new DynamicJobProvider(
                JobProviderConfig.builder()
                        .companyName("American Express")
                        .platform(Platform.ORACLE_HCM)
                        .listUrl("https://egug.fa.us2.oraclecloud.com/hcmRestApi/resources/latest/recruitingCEJobRequisitions")
                        .jdUrl("https://egug.fa.us2.oraclecloud.com/hcmRestApi/resources/latest/recruitingCEJobRequisitionDetails")
                        .siteNumber("CX_1")
                        .keyword("Java")
                        .locationFilter("India")
                        .countryCode("IN")
                        .limit(25)
                        .lookbackDays(2)
                        .jobUrlTemplate("https://egug.fa.us2.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1/job/{id}")
                        .jdFetchDelayMs(300)
                        .jdFetchThreads(3)
                        // New Oracle HCM specific filters
                        .selectedLocationsFacet("300000000228786")
                        .selectedFlexFieldsFacets("\"AttributeChar6|Technology\"")
                        .build(),
                oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
    }

    // ─── JPMorgan Chase ─────────────────────────────────────────────
    private JobProvider jpmorgan() {
        return new DynamicJobProvider(
                JobProviderConfig.builder()
                        .companyName("JPMorgan Chase")
                        .platform(Platform.ORACLE_HCM)
                        .listUrl("https://jpmc.fa.oraclecloud.com/hcmRestApi/resources/latest/recruitingCEJobRequisitions")
                        .jdUrl("https://jpmc.fa.oraclecloud.com/hcmRestApi/resources/latest/recruitingCEJobRequisitionDetails")
                        .siteNumber("CX_1001")
                        .keyword("Java")
                        .locationFilter("India")
                        .countryCode("IN")
                        .limit(25)
                        .lookbackDays(2)
                        .jobUrlTemplate("https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001/job/{id}")
                        .jdFetchDelayMs(300)
                        .jdFetchThreads(3)
                        // New Oracle HCM specific filters
                        .locationId("300000000289360")
                        .selectedCategoriesFacet("300000086152753")
                        .build(),
                oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
    }

    // ─── Barclays ───────────────────────────────────────────────────
    private JobProvider barclays() {
        return new DynamicJobProvider(
                JobProviderConfig.builder()
                        .companyName("Barclays")
                        .platform(Platform.TALENTBREW)
                        .listUrl("https://search.jobs.barclays/search-jobs/results" +
                                "?ActiveFacetID=1269750" +
                                "&RecordsPerPage=16" +
                                "&Distance=50" +
                                "&RadiusUnitType=0" +
                                "&Keywords=Java" +
                                "&Location=" +
                                "&ShowRadius=False" +
                                "&FacetFilters[0].ID=79683" +
                                "&FacetFilters[0].FacetType=1" +
                                "&FacetFilters[0].Count=272" +
                                "&FacetFilters[0].Display=Development and Engineering" +
                                "&FacetFilters[0].IsApplied=true" +
                                "&FacetFilters[0].FieldName=" +
                                "&FacetFilters[1].ID=44699" +
                                "&FacetFilters[1].FacetType=1" +
                                "&FacetFilters[1].Count=537" +
                                "&FacetFilters[1].Display=Technology" +
                                "&FacetFilters[1].IsApplied=true" +
                                "&FacetFilters[1].FieldName=" +
                                "&FacetFilters[2].ID=1269750" +
                                "&FacetFilters[2].FacetType=2" +
                                "&FacetFilters[2].Count=414" +
                                "&FacetFilters[2].Display=India" +
                                "&FacetFilters[2].IsApplied=true" +
                                "&FacetFilters[2].FieldName=" +
                                "&SearchResultsModuleName=Search Results" +
                                "&SearchFiltersModuleName=Search Filters" +
                                "&SortCriteria=1" +
                                "&SortDirection=1" +
                                "&SearchType=1" +
                                "&OrganizationIds=13015" +
                                "&ResultsType=0")
                        .jdUrl("https://search.jobs.barclays/job/pune/software-engineer/13015/{id}")
                        .lookbackDays(2)
                        .jobUrlTemplate("https://search.jobs.barclays{href}")
                        .jdFetchDelayMs(300)
                        .jdFetchThreads(3)
                        .build(),
                oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
    }

    // ─── Goldman Sachs ──────────────────────────────────────────────
    private JobProvider goldman() {
        return new DynamicJobProvider(
                JobProviderConfig.builder()
                        .companyName("Goldman Sachs")
                        .platform(Platform.GOLDMAN_GRAPHQL)
                        .listUrl("https://api-higher.gs.com/gateway/api/v1/graphql")
                        .jdUrl("https://api-higher.gs.com/gateway/api/v1/graphql")
                        .limit(20)
                        .lookbackDays(2)
                        .jobUrlTemplate("https://higher.gs.com/roles/{id}")
                        .jdFetchDelayMs(300)
                        .jdFetchThreads(3)
                        .build(),
                oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
    }

    // ─── Microsoft ──────────────────────────────────────────────────
    private JobProvider microsoft() {
        return new DynamicJobProvider(
                JobProviderConfig.builder()
                        .companyName("Microsoft")
                        .platform(Platform.MICROSOFT_CAREERS)
                        .listUrl("https://apply.careers.microsoft.com/api/pcsx/search" +
                                "?domain=microsoft.com" +
                                "&query=Java" +
                                "&location=India" +
                                "&start=0" +
                                "&num_items=25" +
                                "&sort_by=timestamp" +
                                "&filter_include_remote=1" +
                                "&filter_career_discipline=Software Engineering" +
                                "&filter_profession=software engineering" +
                                "&hl=en")
                        .jdUrl("https://apply.careers.microsoft.com/api/pcsx/position_details" +
                                "?position_id={id}" +
                                "&domain=microsoft.com" +
                                "&hl=en" +
                                "&queried_location=India")
                        .limit(25)
                        .lookbackDays(2)
                        .jobUrlTemplate("https://apply.careers.microsoft.com/careers/job/{id}")
                        .jdFetchDelayMs(5000)  // Increased from 3000ms due to strict rate limiting
                        .jdFetchThreads(1)
                        .build(),
                oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
    }

    // ─── Visa ───────────────────────────────────────────────────────
    private JobProvider visa() {
        return new DynamicJobProvider(
                JobProviderConfig.builder()
                        .companyName("Visa")
                        .platform(Platform.WORKDAY)
                        .listUrl("https://visa.wd5.myworkdayjobs.com/wday/cxs/visa/Visa/jobs")
                        .jdUrl("https://visa.wd5.myworkdayjobs.com/wday/cxs/visa/Visa")
                        .keyword("Java")
                        .limit(20)
                        .lookbackDays(2)
                        // Workday facet IDs for Visa
                        .locationCountryId("c4f78be1a8f14da0ab49ce1162348a5e")  // India
                        .timeTypeId("3d32d47be90110109faf15aa8b2200bf")        // Full-time
                        .jobFamilyId("2745bc1368021016a991f65e710a3b1e")       // Software Engineering
                        .jdFetchDelayMs(500)
                        .jdFetchThreads(2)
                        .build(),
                oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
    }

    // ─── Amazon ─────────────────────────────────────────────────
    private JobProvider amazon() {
        return new DynamicJobProvider(
                JobProviderConfig.builder()
                        .companyName("Amazon")
                        .platform(Platform.AMAZON_JOBS)
                        .listUrl("https://www.amazon.jobs/en/search.json" +
                                "?category%5B%5D=software-development" +
                                "&schedule_type_id%5B%5D=Full-Time" +
                                "&normalized_country_code%5B%5D=IND" +
                                "&job_function_id%5B%5D=job_function_corporate_80rdb4" +  // Corporate/Software Dev function
                                "&radius=24km" +
                                "&industry_experience[]=one_to_three_years" +
                                "&facets%5B%5D=normalized_country_code" +
                                "&facets%5B%5D=normalized_state_name" +
                                "&facets%5B%5D=normalized_city_name" +
                                "&facets%5B%5D=location" +
                                "&facets%5B%5D=business_category" +
                                "&facets%5B%5D=category" +
                                "&facets%5B%5D=schedule_type_id" +
                                "&facets%5B%5D=employee_class" +
                                "&facets%5B%5D=normalized_location" +
                                "&facets%5B%5D=job_function_id" +
                                "&facets%5B%5D=is_manager" +
                                "&facets%5B%5D=is_intern" +
                                "&offset=0" +
                                "&result_limit=100" +  // Increased from 50
                                "&sort=recent" +
                                "&latitude=28.63141" +  // Delhi coordinates (central India)
                                "&longitude=77.21676" +
                                "&loc_query=India" +
                                "&base_query=Java" +  // Search for Java keyword
                                "&country=IND")
                        .jdUrl("")  // Not needed - JD included in search response
                        .limit(100)
                        .lookbackDays(2)
                        .jobUrlTemplate("{url}")  // URL comes directly from API (url_next_step field)
                        .jdFetchDelayMs(0)  // No JD fetch needed!
                        .jdFetchThreads(0)  // No JD fetch needed!
                        .build(),
                oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
    }
}