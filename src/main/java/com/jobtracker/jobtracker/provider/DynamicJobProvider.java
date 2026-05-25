package com.jobtracker.jobtracker.provider;

import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.provider.config.JobProviderConfig;
import com.jobtracker.jobtracker.provider.config.Platform;
import com.jobtracker.jobtracker.provider.platform.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DynamicJobProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(DynamicJobProvider.class);

    private final JobProviderConfig config;
    private final OracleHcmPlatformHandler oracleHcmHandler;
    private final MicrosoftPlatformHandler microsoftHandler;
    private final BarclaysPlatformHandler barclaysHandler;
    private final GoldmanSachsPlatformHandler goldmanHandler;
    private final WorkdayPlatformHandler workdayHandler;

    public DynamicJobProvider(
            JobProviderConfig config,
            OracleHcmPlatformHandler oracleHcmHandler,
            MicrosoftPlatformHandler microsoftHandler,
            BarclaysPlatformHandler barclaysHandler,
            GoldmanSachsPlatformHandler goldmanHandler,
            WorkdayPlatformHandler workdayHandler) {
        this.config = config;
        this.oracleHcmHandler = oracleHcmHandler;
        this.microsoftHandler = microsoftHandler;
        this.barclaysHandler = barclaysHandler;
        this.goldmanHandler = goldmanHandler;
        this.workdayHandler = workdayHandler;
    }

    @Override
    public String getCompanyName() {
        return config.getCompanyName();
    }

    @Override
    public List<Job> fetchJobs() {
        return getHandler().fetchJobs(config);
    }

    @Override
    public String fetchJobDescription(String externalId) {
        return getHandler().fetchJobDescription(config, externalId);
    }

    // Returns right handler based on platform type
    public PlatformHandler getHandler() {
        Platform platform = config.getPlatform();
        return switch (platform) {
            case ORACLE_HCM -> oracleHcmHandler;
            case MICROSOFT_CAREERS -> microsoftHandler;
            case TALENTBREW -> barclaysHandler;
            case GOLDMAN_GRAPHQL -> goldmanHandler;
            case WORKDAY -> workdayHandler;
        };
    }

    // Expose config for JobService to read delay/thread settings
    public JobProviderConfig getConfig() {
        return config;
    }
}