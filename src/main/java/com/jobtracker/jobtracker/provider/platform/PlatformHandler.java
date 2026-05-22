package com.jobtracker.jobtracker.provider.platform;

import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.provider.config.JobProviderConfig;

import java.util.List;

public interface PlatformHandler {

    List<Job> fetchJobs(JobProviderConfig config);

    String fetchJobDescription(JobProviderConfig config, String externalId);
}