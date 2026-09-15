package com.jobtracker.jobtracker.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @Value("${git.commit:unknown}")
    private String gitCommit;

    @GetMapping("/health")
    public String health() {
        Runtime rt = Runtime.getRuntime();
        long heapUsedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapMaxMB = rt.maxMemory() / (1024 * 1024);

        long nonHeapUsedMB = java.lang.management.ManagementFactory
                .getMemoryMXBean().getNonHeapMemoryUsage().getUsed() / (1024 * 1024);

        int threadCount = Thread.activeCount();
        String shortCommit = gitCommit.length() >= 7 ? gitCommit.substring(0, 7) : gitCommit;

        return String.format(
                "JobTracker is running | Commit: %s | Heap: %dMB/%dMB | NonHeap: %dMB | Threads: %d",
                shortCommit, heapUsedMB, heapMaxMB, nonHeapUsedMB, threadCount
        );
    }
}
