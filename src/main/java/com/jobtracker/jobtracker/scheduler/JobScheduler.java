package com.jobtracker.jobtracker.scheduler;

import com.jobtracker.jobtracker.service.JobService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobScheduler {

    private final JobService service;

    public JobScheduler(JobService service) {
        this.service = service;
    }

    // Fixed delay = waits X minutes AFTER previous job completes
    // 300000ms = 5 mins (recommended for faster job discovery)
    // Empty runs (no new jobs) complete in 10-30 seconds with zero cost
    @Scheduled(fixedDelay = 300000, initialDelay = 10000) // wait 10s on startup, then every 5 mins after completion
    public void run(){
        System.out.println("Job Started : ");
        service.checkJobs();
    }

}
