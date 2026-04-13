package com.jobtracker.jobtracker.provider;

import com.jobtracker.jobtracker.model.Job;
import java.util.List;

public interface JobProvider {

    List<Job> fetchJobs();

    String fetchJobDescription(String externalId);


}
