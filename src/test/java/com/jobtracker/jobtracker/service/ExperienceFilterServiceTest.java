package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.config.CandidateProfile;
import com.jobtracker.jobtracker.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceFilterServiceTest {

    private ExperienceFilterService experienceFilterService;

    @BeforeEach
    void setUp() {
        CandidateProfile profile = new CandidateProfile();
        profile.setExperience(4);
        experienceFilterService = new ExperienceFilterService(profile);
    }

    @Test
    void allowsJobRequiringFourYearsMin() {
        String description = "We need someone with 4+ years of Java experience.";
        assertTrue(experienceFilterService.isExperienceSuitable(description, "Backend Engineer"));
    }

    @Test
    void rejectsJobRequiringFiveYearsMin() {
        String description = "Minimum 5 years of professional software development experience.";
        assertFalse(experienceFilterService.isExperienceSuitable(description, "Backend Engineer"));
    }

    @Test
    void structuredExperienceUsesCandidateProfile() {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("Backend role");
        job.setExtractionSuccess(true);
        job.setMinExperienceRequired(4);

        assertTrue(experienceFilterService.isExperienceSuitableStructured(job));

        job.setMinExperienceRequired(5);
        assertFalse(experienceFilterService.isExperienceSuitableStructured(job));
    }
}
