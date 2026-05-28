package com.jobtracker.jobtracker.provider.config;

public enum Platform {

    // Oracle HCM platform — used by AmEx, JPMorgan, and many other companies
    // Adding a new Oracle HCM company = just add config, zero code
    ORACLE_HCM,

    // Microsoft Careers platform — custom API
    MICROSOFT_CAREERS,

    // TalentBrew platform — used by Barclays
    // Returns HTML embedded in JSON
    TALENTBREW,

    // Goldman Sachs custom GraphQL API
    GOLDMAN_GRAPHQL,

    // Workday platform — used by Visa
    // Uses POST for list with filters in payload, relative dates converted to exact dates
    WORKDAY,

    // Amazon Jobs platform — custom API
    // Includes full job description in search response (no separate JD fetch needed)
    // Flexible date formats: absolute ("May 27, 2026") and relative ("1 day", "about 1 month")
    AMAZON_JOBS
}