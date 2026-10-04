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

    // Fixed delay = waits after previous job completes (see job.scheduler.* in application.properties)
    @Scheduled(
            fixedDelayString = "${job.scheduler.fixed-delay-ms:300000}",
            initialDelayString = "${job.scheduler.initial-delay-ms:10000}")
    public void run(){
        logMemory("Before job cycle");

        log.info("Job Started : ");
        service.checkJobs();

        logMemory("After job cycle");
    }

    private void logMemory(String label) {
        Runtime rt = Runtime.getRuntime();
        long heapUsedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapMaxMB = rt.maxMemory() / (1024 * 1024);

        long nonHeapUsedMB = java.lang.management.ManagementFactory
                .getMemoryMXBean().getNonHeapMemoryUsage().getUsed() / (1024 * 1024);

        int threadCount = Thread.activeCount();

        log.info("[{}] Heap: {}MB/{}MB | NonHeap: {}MB | Threads: {}",
                label, heapUsedMB, heapMaxMB, nonHeapUsedMB, threadCount);
    }

}
