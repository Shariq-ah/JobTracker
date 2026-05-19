package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.config.CandidateProfile;
import com.jobtracker.jobtracker.model.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SkillMatcherService {

    @Autowired
    private CandidateProfile candidateProfile;

    public int calculateMatchScore(String title, String description, List<String> candidateSkills) {
        if (candidateSkills == null || candidateSkills.isEmpty()) return 0;

        String text = (title + " " + description).toLowerCase();
        long matched = candidateSkills.stream()
                .filter(skill -> matchesSkill(text, skill))
                .count();

        return (int) ((matched * 100) / candidateSkills.size());
    }

    private boolean matchesSkill(String text, String skill) {
        String s = skill.toLowerCase().trim();

        // Direct match
        if (text.contains(s)) return true;

        // Skill-specific aliases
        switch (s) {
            case "java":
                return text.contains("java");
            case "spring boot":
                return text.contains("spring boot") || text.contains("springboot");
            case "rest api":
                return text.contains("rest api") || text.contains("restful") ||
                        text.contains("rest services") || text.contains("api development");
            case "spring mvc":
                return text.contains("spring mvc") || text.contains("spring framework") ||
                        text.contains("spring web");
            case "microservices":
                return text.contains("microservice") || text.contains("micro service") ||
                        text.contains("micro-service");
            case "kafka":
                return text.contains("kafka") || text.contains("confluent");
            case "jpa":
                return text.contains("jpa") || text.contains("hibernate") ||
                        text.contains("orm") || text.contains("spring data");
            case "hibernate":
                return text.contains("hibernate") || text.contains("jpa") ||
                        text.contains("orm");
            default:
                return text.contains(s);
        }
    }

    public List<String> getMatchedSkills(String title, String description, List<String> candidateSkills) {
        String text = (title + " " + description).toLowerCase();
        return candidateSkills.stream()
                .filter(skill -> matchesSkill(text, skill))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<String> getMissingSkills(String title, String description, List<String> candidateSkills) {
        String text = (title + " " + description).toLowerCase();
        return candidateSkills.stream()
                .filter(skill -> !matchesSkill(text, skill))
                .collect(java.util.stream.Collectors.toList());
    }

    public Job match(Job job) {
        List<String> skills = candidateProfile.getSkills();
        String title = job.getTitle();
        String description = job.getDescription() != null ? job.getDescription() : "";

        int score = calculateMatchScore(title, description, skills);
        List<String> matched = getMatchedSkills(title, description, skills);
        List<String> missing = getMissingSkills(title, description, skills);

        job.setMatchScore(score);
        job.setMatchedSkills(matched);
        job.setMissingSkills(missing);

        return job;
    }

}