package com.jobtracker.jobtracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;

/**
 * Service to manage the general (non-tailored) resume PDF.
 * Loads user's pre-compiled PDF once at startup and caches it.
 * Used for jobs with score < 50% or when tailoring fails.
 * Cost: $0 (no AI calls, no compilation)
 */
@Service
@Slf4j
public class GeneralResumeService {

    private final ResourceLoader resourceLoader;

    @Value("${resume.general.pdf.path:}")
    private String externalPdfPath;

    private byte[] cachedGeneralResumePdf;
    private boolean initialized = false;

    public GeneralResumeService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Loads general resume PDF once at startup and caches it in memory.
     * Tries two locations:
     * 1. External path (if configured in properties)
     * 2. Classpath: /resume/general_resume.pdf
     */
    @PostConstruct
    public void loadGeneralResume() {
        try {
            // Try external path first (if configured)
            if (externalPdfPath != null && !externalPdfPath.isEmpty()) {
                File pdfFile = new File(externalPdfPath);
                if (pdfFile.exists()) {
                    cachedGeneralResumePdf = Files.readAllBytes(pdfFile.toPath());
                    log.info("✅ General resume loaded from: {} ({} bytes)",
                        externalPdfPath, cachedGeneralResumePdf.length);
                    initialized = true;
                    return;
                } else {
                    log.warn("❌ External PDF path configured but file not found: {}", externalPdfPath);
                }
            }

            // Try classpath resources
            try {
                Resource resource = resourceLoader.getResource("classpath:resume/general_resume.pdf");
                if (resource.exists()) {
                    cachedGeneralResumePdf = resource.getInputStream().readAllBytes();
                    log.info("✅ General resume loaded from classpath ({} bytes)", cachedGeneralResumePdf.length);
                    initialized = true;
                    return;
                }
            } catch (Exception e) {
                log.debug("General resume not found in classpath: {}", e.getMessage());
            }

            // No PDF found
            log.warn("⚠️ General resume PDF not found. Please place it at:");
            log.warn("   Option 1: src/main/resources/resume/general_resume.pdf");
            log.warn("   Option 2: Configure 'resume.general.pdf.path' in application.properties");
            log.warn("   → Jobs with score < 50% or tailoring failures will not receive resume PDFs");
            initialized = false;

        } catch (Exception e) {
            log.error("❌ Failed to load general resume: {}", e.getMessage());
            initialized = false;
        }
    }

    /**
     * Gets the cached general resume PDF.
     *
     * @return PDF bytes, or null if not available
     */
    public byte[] getGeneralResumePdf() {
        if (!initialized || cachedGeneralResumePdf == null) {
            log.warn("General resume not available");
            return null;
        }
        return cachedGeneralResumePdf;
    }

    /**
     * Checks if general resume is available.
     */
    public boolean isAvailable() {
        return initialized && cachedGeneralResumePdf != null;
    }
}
