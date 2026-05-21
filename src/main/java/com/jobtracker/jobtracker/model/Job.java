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
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private LocalDateTime firstSeenAt;
    private LocalDateTime postedAt;
    private String providerType;

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
}