package com.jobtracker.jobtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExperienceFilterService {

    private static final Logger log = LoggerFactory.getLogger(ExperienceFilterService.class);

    private static final int CANDIDATE_EXPERIENCE = 3;
    private static final int MAX_ACCEPTABLE_MIN = 4; // block 5+ years

    /**
     * Quick title-only check — no JD needed.
     * Returns false for obvious senior/management roles.
     */
    public boolean isTitleSuitable(String title) {
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
            log.info("Skipping senior/management role: {}", title);
            return false;
        }

        String text = (description + " " + title).toLowerCase();
        int minExp = extractMinExperience(text);

        if (minExp == -1) {
            log.info("No experience found in JD, allowing: {}", title);
            return true;
        }

        if (minExp > MAX_ACCEPTABLE_MIN) {
            log.info("Skipping job requiring {}+ years: {}", minExp, title);
            return false;
        }

        log.info("Experience filter passed: {}+ years required, candidate has {}: {}",
                minExp, CANDIDATE_EXPERIENCE, title);
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
}