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
    GOLDMAN_GRAPHQL
}