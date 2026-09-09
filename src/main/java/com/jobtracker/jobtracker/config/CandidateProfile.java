package com.jobtracker.jobtracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "candidate")
public class CandidateProfile {

    // Basic info
    private String name;
    private String currentRole;
    private String phone;
    private String email;
    private String location;
    private String linkedin;
    private String github;

    // Skills and experience
    private List<String> skills;
    private double experience;
    private List<String> roles;
}