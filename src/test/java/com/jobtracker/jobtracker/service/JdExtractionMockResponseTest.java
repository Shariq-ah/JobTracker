package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.config.CandidateProfile;
import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.model.WorkMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JdExtractionMockResponseTest {

    private JdExtractionService jdExtractionService;

    @BeforeEach
    void setUp() {
        CandidateProfile candidateProfile = new CandidateProfile();
        candidateProfile.setExperience(4);
        candidateProfile.setSkills(List.of("Java", "Spring Boot", "Kafka"));

        jdExtractionService = new JdExtractionService();
        ReflectionTestUtils.setField(jdExtractionService, "bedrockClient", mock(BedrockRuntimeClient.class));
        ReflectionTestUtils.setField(jdExtractionService, "candidateProfile", candidateProfile);
        ReflectionTestUtils.setField(jdExtractionService, "modelId", "mock-model");
        ReflectionTestUtils.setField(jdExtractionService, "maxTokens", 2000);
        ReflectionTestUtils.setField(jdExtractionService, "temperature", 0.0);
        ReflectionTestUtils.setField(jdExtractionService, "devMockEnabled", true);
    }

    @Test
    void mockPathPopulatesAiMatchScoreAndStructuredFields() {
        Job job = new Job();
        job.setTitle("Backend Engineer");
        job.setDescription("""
            We are hiring a Java developer with Spring Boot and microservices experience.
            Kafka knowledge is a plus.
            """);

        Job result = jdExtractionService.extractStructuredData(job);

        assertTrue(result.getExtractionSuccess());
        assertNotNull(result.getAiMatchScore());
        assertTrue(result.getAiMatchScore() >= 60 && result.getAiMatchScore() <= 95);
        assertEquals(result.getAiMatchScore(), result.getMatchScore());
        assertNotNull(result.getApplyRecommendation());
        assertNotNull(result.getScoreReason());
        assertNotNull(result.getRequiredSkills());
        assertNotNull(result.getMatchedSkills());
        assertEquals(3, result.getMinExperienceRequired()); // candidateYears - 1
        assertEquals(5, result.getMaxExperienceRequired()); // candidateYears + 1
        assertEquals(WorkMode.HYBRID, result.getWorkMode());
        assertEquals("SDE2", result.getJobLevel());
        assertNotNull(result.getResponsibilities());
        assertNotNull(result.getQualifications());
    }

    @Test
    void mockScoreIncreasesWithKeywordMatches() {
        Job baselineJob = new Job();
        baselineJob.setTitle("Java Developer");
        baselineJob.setDescription("Basic Java role.");

        Job keywordJob = new Job();
        keywordJob.setTitle("Java Developer");
        keywordJob.setDescription("""
            Java Spring Boot microservices Kafka role.
            """);

        Job baseline = jdExtractionService.extractStructuredData(baselineJob);
        Job withKeywords = jdExtractionService.extractStructuredData(keywordJob);

        assertNotNull(baseline.getAiMatchScore());
        assertNotNull(withKeywords.getAiMatchScore());
        assertTrue(withKeywords.getAiMatchScore() >= baseline.getAiMatchScore());
    }
}
