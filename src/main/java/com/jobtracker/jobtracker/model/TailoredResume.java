package com.jobtracker.jobtracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entity representing a tailored resume generated for a specific job posting.
 * Stores both the LaTeX source and compiled PDF for resume tracking.
 */
@Data
@Document(collection = "tailored_resumes")
public class TailoredResume {

    @Id
    private String id;  // Format: {job_id}_resume

    private String jobId;  // Reference to Job document
    private String companyName;
    private String jobTitle;

    private String latexSource;  // Tailored LaTeX code
    private byte[] pdfBytes;     // Compiled PDF document

    private LocalDateTime tailoredAt;
    private boolean sentToUser;
    private boolean applied;

    // ATS Score (NEW)
    private Integer atsScore;  // 0-100
    private String atsReasoning;  // Why this score

    // Metadata
    private String claudePromptVersion;
    private int latexCompilationAttempts;
}
