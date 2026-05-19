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

    @Scheduled(fixedDelay = 900000, initialDelay = 10000) // wait 10s on startup, then every 15 mins
    public void run(){
        System.out.println("Job Started : ");
        service.checkJobs();
    }

}
