package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.config.CandidateProfile;
import com.jobtracker.jobtracker.model.Job;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SkillMatcherService {

    private final CandidateProfile profile;

    public SkillMatcherService(CandidateProfile profile) {
        this.profile = profile;
    }

    public Job match(Job job) {

        String jdLower = job.getDescription() == null ? ""
                : job.getDescription().toLowerCase();

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String skill : profile.getSkills()) {
            if (jdLower.contains(skill.toLowerCase())) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        double score = profile.getSkills().isEmpty() ? 0 :
                (double) matched.size() / profile.getSkills().size() * 100;

        job.setMatchedSkills(matched);
        job.setMissingSkills(missing);
        job.setMatchScore(Math.round(score));

        return job;
    }
}