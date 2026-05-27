package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.config.CandidateProfile;
import com.jobtracker.jobtracker.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SkillMatcherService {

    private static final Logger log = LoggerFactory.getLogger(SkillMatcherService.class);

    @Autowired
    private CandidateProfile candidateProfile;

    // Tier 1 — Core skills (weight 3x)
    private static final Set<String> TIER1 = Set.of(
            "java", "spring boot", "microservices", "rest api", "spring framework"
    );

    // Tier 2 — Important skills (weight 2x)
    private static final Set<String> TIER2 = Set.of(
            "kafka", "jpa", "hibernate", "spring mvc"
    );

    // Tier 3 — Supporting skills (weight 1x) — everything else

    private int getWeight(String skill) {
        String s = skill.toLowerCase().trim();
        if (TIER1.contains(s)) return 3;
        if (TIER2.contains(s)) return 2;
        return 1;
    }

    public int calculateMatchScore(String title, String description, List<String> candidateSkills) {
        if (candidateSkills == null || candidateSkills.isEmpty()) return 0;

        String text = (title + " " + description).toLowerCase();

        int totalWeight = 0;
        int matchedWeight = 0;

        for (String skill : candidateSkills) {
            int weight = getWeight(skill);
            totalWeight += weight;
            if (matchesSkill(text, skill)) {
                matchedWeight += weight;
            }
        }

        if (totalWeight == 0) return 0;
        return (int) ((matchedWeight * 100.0) / totalWeight);
    }

    private boolean matchesSkill(String text, String skill) {
        String s = skill.toLowerCase().trim();

        if (text.contains(s)) return true;

        switch (s) {
            case "java":
                return text.contains("java");
            case "spring boot":
                return text.contains("spring boot") || text.contains("springboot") ||
                        text.contains("spring boot 3") || text.contains("spring framework") ||
                        text.contains("spring boot 4");
            case "rest api":
                return text.contains("rest api") || text.contains("restful") ||
                        text.contains("rest services") || text.contains("api development") ||
                        text.contains("api's") || text.contains("apis");
            case "spring mvc":
                return text.contains("spring mvc") || text.contains("spring framework") ||
                        text.contains("spring web");
            case "spring framework":
                return text.contains("spring framework") || text.contains("spring boot") ||
                        text.contains("spring mvc") || text.contains("spring web");
            case "microservices":
                return text.contains("microservice") || text.contains("micro service") ||
                        text.contains("micro-service") || text.contains("distributed system") ||
                        text.contains("event-driven") || text.contains("event driven");
            case "kafka":
                return text.contains("kafka") || text.contains("confluent");
            case "jpa":
                return text.contains("jpa") || text.contains("hibernate") ||
                        text.contains("orm") || text.contains("spring data");
            case "hibernate":
                return text.contains("hibernate") || text.contains("jpa") ||
                        text.contains("orm");
            case "sql":
                return text.contains("sql") || text.contains("rdbms") ||
                        text.contains("relational database") || text.contains("mysql") ||
                        text.contains("postgresql") || text.contains("oracle") ||
                        text.contains("database") || text.contains("db");
            case "nosql":
                return text.contains("nosql") || text.contains("mongodb") ||
                        text.contains("cassandra") || text.contains("redis");
            case "git":
                return text.contains("git") || text.contains("github") ||
                        text.contains("gitlab") || text.contains("bitbucket");
            case "docker":
                return text.contains("docker") || text.contains("container") ||
                        text.contains("kubernetes") || text.contains("k8s") ||
                        text.contains("containerized") || text.contains("openshift");
            case "aws":
                return text.contains("aws") || text.contains("amazon web services") ||
                        text.contains("cloud") || text.contains("ec2") ||
                        text.contains("s3") || text.contains("lambda") ||
                        text.contains("gcp") || text.contains("azure");
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

    /**
     * Match skills using AI score if available, structured data as secondary,
     * otherwise fall back to fuzzy text matching.
     */
    public Job matchStructured(Job job) {
        // Priority 1: If extraction succeeded and AI already calculated score
        if (Boolean.TRUE.equals(job.getExtractionSuccess())
                && job.getAiMatchScore() != null) {

            log.info("Using AI-calculated score for {}: {}% ({})",
                job.getTitle(),
                job.getAiMatchScore().intValue(),
                job.getApplyRecommendation());

            // AI already populated matchScore, matchedSkills, missingSkills, scoreReason
            return job;
        }

        // Priority 2: If extraction succeeded but no AI score, use structured skills
        if (Boolean.TRUE.equals(job.getExtractionSuccess())
                && job.getRequiredSkills() != null
                && !job.getRequiredSkills().isEmpty()) {

            log.info("AI score missing, using structured skill matching for: {}", job.getTitle());
            return matchStructuredSkills(job);
        }

        // Priority 3: Fallback to fuzzy matching
        log.info("Using fallback fuzzy matching for: {}", job.getTitle());
        return match(job);
    }

    private Job matchStructuredSkills(Job job) {
        List<String> candidateSkills = candidateProfile.getSkills();

        // Combine all job skills (required + preferred + nice-to-have)
        List<String> allJobSkills = new ArrayList<>();
        if (job.getRequiredSkills() != null) {
            allJobSkills.addAll(job.getRequiredSkills());
        }
        if (job.getPreferredSkills() != null) {
            allJobSkills.addAll(job.getPreferredSkills());
        }
        if (job.getNiceToHaveSkills() != null) {
            allJobSkills.addAll(job.getNiceToHaveSkills());
        }

        // Calculate match (exact skill name matching, case-insensitive)
        List<String> matched = new ArrayList<>();
        for (String candidateSkill : candidateSkills) {
            boolean found = allJobSkills.stream()
                .anyMatch(jobSkill -> jobSkill.equalsIgnoreCase(candidateSkill)
                    || areSkillsSimilar(jobSkill, candidateSkill));
            if (found) {
                matched.add(candidateSkill);
            }
        }

        List<String> missing = new ArrayList<>(candidateSkills);
        missing.removeAll(matched);

        // Calculate weighted score using tier system
        int totalWeight = 0;
        int matchedWeight = 0;

        for (String skill : candidateSkills) {
            int weight = getWeight(skill);
            totalWeight += weight;
            if (matched.contains(skill)) {
                matchedWeight += weight;
            }
        }

        int score = totalWeight == 0 ? 0 : (int) ((matchedWeight * 100.0) / totalWeight);

        job.setMatchScore(score);
        job.setMatchedSkills(matched);
        job.setMissingSkills(missing);

        log.info("Structured skill match for {}: {}% ({} matched, {} missing)",
            job.getTitle(), score, matched.size(), missing.size());

        return job;
    }

    /**
     * Check if two skill names are similar (handles common variations).
     */
    private boolean areSkillsSimilar(String skill1, String skill2) {
        String s1 = skill1.toLowerCase().trim();
        String s2 = skill2.toLowerCase().trim();

        // Exact match
        if (s1.equals(s2)) return true;

        // Common variations
        if (s1.contains(s2) || s2.contains(s1)) return true;

        // Specific mappings
        if ((s1.equals("spring boot") || s1.equals("springboot"))
                && (s2.equals("spring boot") || s2.equals("springboot"))) {
            return true;
        }

        if ((s1.equals("rest api") || s1.equals("restful") || s1.equals("rest"))
                && (s2.equals("rest api") || s2.equals("restful") || s2.equals("rest"))) {
            return true;
        }

        return false;
    }
}