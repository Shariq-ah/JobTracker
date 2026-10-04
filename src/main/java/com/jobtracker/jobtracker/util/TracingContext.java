package com.jobtracker.jobtracker.util;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * SLF4J MDC helpers for correlating logs per scheduler cycle and per job.
 */
public final class TracingContext {

    public static final String STAGE_JD_FETCH = "JD_FETCH";
    public static final String STAGE_LOCAL_MATCH = "LOCAL_MATCH";
    public static final String STAGE_BEDROCK = "BEDROCK";
    public static final String STAGE_TAILOR = "TAILOR";
    public static final String STAGE_TELEGRAM = "TELEGRAM";

    private TracingContext() {
    }

    public static String newCycleId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static void setCycleId(String cycleId) {
        if (cycleId != null) {
            MDC.put("cycleId", cycleId);
        }
    }

    public static void setJobContext(String jobId, String provider) {
        if (jobId != null) {
            MDC.put("jobId", jobId);
        }
        if (provider != null) {
            MDC.put("provider", provider);
        }
    }

    public static void setStage(String stage) {
        if (stage != null) {
            MDC.put("stage", stage);
        }
    }

    public static void clearJobContext() {
        MDC.remove("jobId");
        MDC.remove("provider");
        MDC.remove("stage");
    }

    public static void clear() {
        MDC.clear();
    }

    public static void runWithJob(String cycleId, String jobId, String provider, String stage, Runnable work) {
        try {
            setCycleId(cycleId);
            setJobContext(jobId, provider);
            setStage(stage);
            work.run();
        } finally {
            clear();
        }
    }

    public static <T> T callWithJob(String cycleId, String jobId, String provider, String stage, Callable<T> work)
            throws Exception {
        try {
            setCycleId(cycleId);
            setJobContext(jobId, provider);
            setStage(stage);
            return work.call();
        } finally {
            clear();
        }
    }
}
