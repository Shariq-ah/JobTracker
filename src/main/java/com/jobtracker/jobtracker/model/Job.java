package com.jobtracker.jobtracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Data
@Document(collection = "jobs")
public class Job {

    @Id
    private String id;
    private String externalId;
    private String title;
    private String company;
    private String location;
    private String url;
    private String description;
    private List<String> skills;
    private double matchScore;
    private Double localMatchScore;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private LocalDateTime firstSeenAt;
    private LocalDateTime postedAt;
    private String providerType;
    private Boolean skipped;
    private String skipReason;
    private LocalDateTime skippedAt;
    private Boolean aiAnalyzed;

    // Structured experience requirements
    private Integer minExperienceRequired;
    private Integer maxExperienceRequired;

    // Structured skills (extracted by Bedrock)
    private List<String> requiredSkills;
    private List<String> preferredSkills;
    private List<String> niceToHaveSkills;

    // Job level and type
    private String jobLevel;              // "Junior", "SDE1", "SDE2", "Senior", "Staff", "Principal"
    private WorkMode workMode;            // Enum: REMOTE, HYBRID, ONSITE, FLEXIBLE, NOT_SPECIFIED
    private String employmentType;        // "Full-time", "Contract", "Intern"

    // Salary information
    private Long salaryMin;               // In INR (null if not mentioned)
    private Long salaryMax;               // In INR (null if not mentioned)
    private String salaryCurrency;        // "INR", "USD", "Not mentioned"

    // Description fields
    private String teamDescription;       // What team does (1-2 sentences)
    private List<String> responsibilities; // Key responsibilities
    private List<String> qualifications;   // Required qualifications

    // AI-generated match analysis
    private String applyRecommendation;   // "Strong Apply", "Apply", "Consider", "Skip"
    private String scoreReason;           // Brief explanation of score
    private Double aiMatchScore;          // Claude's calculated match score (0-100)

    // Extraction metadata
    private LocalDateTime extractedAt;
    private Boolean extractionSuccess;
    private String promptVersion;         // e.g., "2.0" for versioning prompts

    // Always store IST time
    public void setFirstSeenAt(LocalDateTime time) {
        this.firstSeenAt = time != null
                ? time.atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime()
                : null;
    }

    public void setPostedAt(LocalDateTime time) {
        this.postedAt = time != null
                ? time.atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime()
                : null;
    }

    public void setSkippedAt(LocalDateTime time) {
        this.skippedAt = time != null
                ? time.atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                .toLocalDateTime()
                : null;
    }
}
