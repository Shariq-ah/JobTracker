package com.jobtracker.jobtracker.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        Runtime rt = Runtime.getRuntime();
        long heapUsedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapMaxMB = rt.maxMemory() / (1024 * 1024);

        long nonHeapUsedMB = java.lang.management.ManagementFactory
                .getMemoryMXBean().getNonHeapMemoryUsage().getUsed() / (1024 * 1024);

        int threadCount = Thread.activeCount();

        return String.format(
                "JobTracker is running | Heap: %dMB/%dMB | NonHeap: %dMB | Threads: %d",
                heapUsedMB, heapMaxMB, nonHeapUsedMB, threadCount
        );
    }
}
