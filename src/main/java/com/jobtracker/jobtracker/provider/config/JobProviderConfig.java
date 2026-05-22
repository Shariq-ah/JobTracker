package com.jobtracker.jobtracker.provider.config;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class JobProviderConfig {

    // Company name shown in Telegram notification
    private String companyName;

    // Platform type — determines which handler to use
    private Platform platform;

    // Base URL for list API
    private String listUrl;

    // Base URL for JD fetch API
    private String jdUrl;

    // Site number for Oracle HCM companies (AmEx=CX_1, JPMorgan=CX_1001)
    private String siteNumber;

    // Country code filter (IN, US etc)
    private String countryCode;

    // Location filter string (India, IN etc)
    private String locationFilter;

    // Keyword for search (Software Engineer etc)
    private String keyword;

    // Max jobs to fetch per run
    @Builder.Default
    private int limit = 25;

    // Days to look back for new jobs
    @Builder.Default
    private int lookbackDays = 2;

    // Delay in ms between JD fetch requests
    @Builder.Default
    private long jdFetchDelayMs = 300;

    // Max parallel threads for JD fetching
    @Builder.Default
    private int jdFetchThreads = 3;

    // Extra params specific to platform — key/value pairs
    private Map<String, String> extraParams;

    // Category filter IDs for Barclays style platforms
    private List<String> categoryIds;

    // Sort by field
    @Builder.Default
    private String sortBy = "POSTING_DATES_DESC";

    // Job page URL template — use {id} as placeholder
    private String jobUrlTemplate;
}