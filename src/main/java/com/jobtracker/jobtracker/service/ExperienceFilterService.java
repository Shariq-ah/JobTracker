package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.config.CandidateProfile;
import com.jobtracker.jobtracker.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExperienceFilterService {

    private static final Logger log = LoggerFactory.getLogger(ExperienceFilterService.class);

    private final CandidateProfile candidateProfile;

    public ExperienceFilterService(CandidateProfile candidateProfile) {
        this.candidateProfile = candidateProfile;
    }

    /**
     * Maximum job minimum-experience we accept (e.g. 4 yrs → allow jobs asking up to 4 yrs min).
     */
    private int getMaxAcceptableMinExperience() {
        return (int) Math.ceil(candidateProfile.getExperience());
    }

    /**
     * Quick title-only check — no JD needed.
     * Returns false for obvious senior/management roles.
     */
    public boolean isTitleSuitable(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String titleLower = title.toLowerCase();
        return !(titleLower.contains("principal") ||
                titleLower.contains("manager") ||
                titleLower.contains("director") ||
                titleLower.contains("vice president") ||
                titleLower.contains(" vp ") ||
                titleLower.contains("head of") ||
                titleLower.contains("engineering lead") ||
                titleLower.contains("senior lead"));
        // Removed: lead software, software lead, lead engineer
    }

    /**
     * Full check using JD text + title.
     * Call this after fetching JD.
     */
    public boolean isExperienceSuitable(String description, String title) {

        // Title check (catches anything isTitleSuitable missed)
        if (!isTitleSuitable(title)) {
            logSkipSeniorTitle(title, "regex");
            return false;
        }

        String safeDescription = description == null ? "" : description;
        String text = (safeDescription + " " + title).toLowerCase();
        int minExp = extractMinExperience(text);

        if (minExp == -1) {
            log.info("No experience found in JD, allowing: {}", title);
            return true;
        }

        if (minExp > getMaxAcceptableMinExperience()) {
            log.info("Skipping job requiring {}+ years: {}", minExp, title);
            return false;
        }

        log.info("Experience filter passed: {}+ years required, candidate has {}: {}",
                minExp, candidateProfile.getExperience(), title);
        return true;
    }

    private int extractMinExperience(String text) {
        Pattern[] patterns = {
                Pattern.compile("(\\d+)\\s*\\+\\s*years?"),
                Pattern.compile("(\\d+)\\s*\\+\\s*yrs?"),
                Pattern.compile("(\\d+)\\s*[-–to]+\\s*\\d+\\s*years?"),
                Pattern.compile("minimum\\s+(\\d+)\\s*years?"),
                Pattern.compile("at least\\s+(\\d+)\\s*years?"),
                Pattern.compile("(\\d+)\\s*years?\\s*of\\s*(?:relevant\\s*)?experience"),
                Pattern.compile("experience\\s*of\\s*(\\d+)\\s*years?"),
                Pattern.compile("(\\d+)\\s*years?\\s*(?:work|professional|industry|software|development)?\\s*experience"),
        };

        int minFound = Integer.MAX_VALUE;

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                try {
                    int years = Integer.parseInt(matcher.group(1));
                    if (years >= 2 && years <= 20) {
                        minFound = Math.min(minFound, years);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return minFound == Integer.MAX_VALUE ? -1 : minFound;
    }

    /**
     * Check experience suitability using structured data if available,
     * otherwise fall back to regex-based extraction.
     */
    public boolean isExperienceSuitableStructured(Job job) {
        // First, always check title (quick filter)
        if (!isTitleSuitable(job.getTitle())) {
            logSkipSeniorTitle(job.getTitle(), "structured");
            return false;
        }

        // If extraction succeeded and we have structured min experience
        if (Boolean.TRUE.equals(job.getExtractionSuccess())
                && job.getMinExperienceRequired() != null) {
            int minRequired = job.getMinExperienceRequired();

            if (minRequired > getMaxAcceptableMinExperience()) {
                log.info("Skipping job requiring {}+ years (structured): {}",
                    minRequired, job.getTitle());
                return false;
            }

            log.info("Experience filter passed (structured): {}+ years required, candidate has {}: {}",
                    minRequired, candidateProfile.getExperience(), job.getTitle());
            return true;
        }

        // Fallback to regex-based filtering
        return isExperienceSuitable(job.getDescription(), job.getTitle());
    }

    private void logSkipSeniorTitle(String title, String path) {
        log.info("Skipping senior/management role ({}): {}", path, title);
    }
}