package com.jobtracker.jobtracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "candidate")
public class CandidateProfile {

    private List<String> skills;
    private int experience;
    private List<String> roles;
}