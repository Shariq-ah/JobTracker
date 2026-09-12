package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.config.CandidateProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobNotifyThresholdTest {

    private CandidateProfile candidateProfile;

    @BeforeEach
    void setUp() {
        candidateProfile = new CandidateProfile();
        candidateProfile.setNotifyMinScore(50);
    }

    @Test
    void notifiesAtOrAboveThreshold() {
        assertTrue(shouldNotify(50));
        assertTrue(shouldNotify(60));
        assertTrue(shouldNotify(75));
    }

    @Test
    void skipsBelowThreshold() {
        assertFalse(shouldNotify(49));
        assertFalse(shouldNotify(45));
    }

    private boolean shouldNotify(double aiScore) {
        return aiScore >= candidateProfile.getNotifyMinScore();
    }
}
