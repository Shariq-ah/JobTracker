package com.jobtracker.jobtracker.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TracingContextTest {

    @AfterEach
    void tearDown() {
        TracingContext.clear();
    }

    @Test
    void runWithJobSetsAndClearsMdc() {
        TracingContext.runWithJob("cycle1", "job-1", "Visa", TracingContext.STAGE_JD_FETCH, () -> {
            assertEquals("cycle1", MDC.get("cycleId"));
            assertEquals("job-1", MDC.get("jobId"));
            assertEquals("Visa", MDC.get("provider"));
            assertEquals(TracingContext.STAGE_JD_FETCH, MDC.get("stage"));
        });

        assertNull(MDC.get("cycleId"));
        assertNull(MDC.get("jobId"));
    }
}
