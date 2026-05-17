package com.jobtracker.jobtracker.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
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
    private String providerType;

}