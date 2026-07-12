package com.jobtracker.jobtracker.scheduler;

import com.jobtracker.jobtracker.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobScheduler {

    private final JobService service;
    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);


    public JobScheduler(JobService service) {
        this.service = service;
    }

    // Fixed delay = waits X minutes AFTER previous job completes
    // 300000ms = 5 mins (recommended for faster job discovery)
    // Empty runs (no new jobs) complete in 10-30 seconds with zero cost
    @Scheduled(fixedDelay = 300000, initialDelay = 10000) // wait 10s on startup, then every 5 mins after completion
    public void run(){
        logMemory("Before job cycle");

        log.info("Job Started : ");
        service.checkJobs();

        logMemory("After job cycle");
    }

    private void logMemory(String label) {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        log.info("[{}] Heap used: {} MB / {} MB max", label, usedMB, maxMB);
    }

}
