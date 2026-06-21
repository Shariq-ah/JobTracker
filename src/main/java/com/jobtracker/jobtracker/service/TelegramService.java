package com.jobtracker.jobtracker.service;

import com.jobtracker.jobtracker.model.Job;
import com.jobtracker.jobtracker.model.TailoredResume;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending Telegram notifications (text messages and documents).
 */
@Service
@Slf4j
public class TelegramService {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.chat.id}")
    private String chatId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final GeneralResumeService generalResumeService;

    public TelegramService(GeneralResumeService generalResumeService) {
        this.generalResumeService = generalResumeService;
    }

    /**
     * Sends tailored resume PDF via Telegram with ATS score.
     *
     * @param job Job the resume was tailored for
     * @param resume Tailored resume with PDF and ATS score
     */
    public void sendTailoredResume(Job job, TailoredResume resume) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendDocument";

            // Create multipart request
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", chatId);
            body.add("caption", buildResumeCaption(job, resume));
            body.add("parse_mode", "HTML");

            // Attach PDF with proper filename
            ByteArrayResource pdfResource = new ByteArrayResource(resume.getPdfBytes()) {
                @Override
                public String getFilename() {
                    return sanitizeFilename(job.getCompany(), job.getTitle());
                }
            };
            body.add("document", pdfResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.info("Tailored resume sent via Telegram: {} (ATS: {})",
                response.getStatusCode(), resume.getAtsScore());

        } catch (Exception e) {
            log.error("Failed to send tailored resume via Telegram: {}", e.getMessage());
        }
    }

    /**
     * Builds caption for resume PDF including ATS score.
     */
    private String buildResumeCaption(Job job, TailoredResume resume) {
        String atsEmoji = getAtsEmoji(resume.getAtsScore());
        String recommendation = job.getApplyRecommendation() != null
            ? job.getApplyRecommendation()
            : "Apply";

        return String.format("""
            📄 <b>TAILORED RESUME FOR THIS JOB ⬆️</b>

            🎯 <b>Optimized for:</b> %s at %s
            ⭐ <b>Job Match:</b> %.0f%% (%s)

            %s <b>ATS Score:</b> %d/100
            💡 <b>ATS Analysis:</b> %s

            ⚠️ <b>Use THIS resume ONLY for the job above</b>
            ✨ <i>Keywords and experience tailored specifically</i>
            """,
            job.getTitle(),                                                           // %s - Job Title
            job.getCompany(),                                                         // %s - Company
            job.getAiMatchScore() != null ? job.getAiMatchScore() : job.getMatchScore(), // %.0f - Match Score
            recommendation,                                                           // %s - Recommendation
            atsEmoji,                                                                 // %s - ATS Emoji
            resume.getAtsScore(),
            resume.getAtsReasoning() != null && !resume.getAtsReasoning().isEmpty()
                ? truncate(resume.getAtsReasoning(), 150)
                : "Keywords and experience optimized for ATS"
        );
    }

    /**
     * Sanitizes filename for Telegram document.
     */
    private String sanitizeFilename(String company, String title) {
        String companyClean = company.replaceAll("[^a-zA-Z0-9]", "_");
        String titleClean = title.replaceAll("[^a-zA-Z0-9]", "_");
        String truncatedTitle = titleClean.length() > 30
            ? titleClean.substring(0, 30)
            : titleClean;
        return String.format("Resume_%s_%s.pdf", companyClean, truncatedTitle);
    }

    /**
     * Returns emoji based on ATS score.
     */
    private String getAtsEmoji(Integer atsScore) {
        if (atsScore == null) return "📊";
        if (atsScore >= 90) return "🔥";  // Excellent
        if (atsScore >= 80) return "✅";  // Great
        if (atsScore >= 70) return "👍";  // Good
        if (atsScore >= 60) return "⚠️";  // Fair
        return "❌";  // Poor
    }

    /**
     * Sends general (non-tailored) resume for jobs with score < 50% or when tailoring fails.
     * Cost: $0 (no AI, uses pre-compiled PDF)
     */
    public void sendGeneralResume(Job job, String reason) {
        try {
            byte[] generalPdf = generalResumeService.getGeneralResumePdf();

            if (generalPdf == null) {
                log.warn("General resume not available, sending text notification only");
                sendGeneralResumeTextOnly(job, reason);
                return;
            }

            String url = "https://api.telegram.org/bot" + botToken + "/sendDocument";

            // Create multipart request
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", chatId);
            body.add("caption", buildGeneralResumeCaption(job, reason));
            body.add("parse_mode", "HTML");

            // Attach general resume PDF
            ByteArrayResource pdfResource = new ByteArrayResource(generalPdf) {
                @Override
                public String getFilename() {
                    return "Resume_Sharique_Ahmad.pdf";
                }
            };
            body.add("document", pdfResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Connection", "close");  // Prevent connection pooling issues

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            // Retry logic for network failures
            int maxRetries = 2;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
                    log.info("General resume sent via Telegram: {}", response.getStatusCode());
                    return;  // Success
                } catch (Exception retryEx) {
                    if (attempt == maxRetries) {
                        throw retryEx;  // Final attempt failed
                    }
                    log.warn("Telegram send attempt {} failed, retrying...", attempt);
                    Thread.sleep(1000);  // Wait 1s before retry
                }
            }

        } catch (Exception e) {
            log.error("Failed to send general resume via Telegram: {}", e.getMessage());
            // Fallback: send text-only notification
            sendGeneralResumeTextOnly(job, reason);
        }
    }

    /**
     * Builds caption for general resume.
     */
    private String buildGeneralResumeCaption(Job job, String reason) {
        return String.format("""
            📄 <b>GENERAL RESUME FOR THIS JOB ⬆️</b>

            🏢 <b>Job:</b> %s at %s
            ⭐ <b>Match:</b> %.0f%%

            💡 <b>Reason:</b> %s

            📝 <b>This is your standard resume</b>
            (Not tailored - no AI cost incurred)

            🔗 <a href="%s">Click to Apply</a>
            """,
            job.getTitle(),
            job.getCompany(),
            job.getAiMatchScore() != null ? job.getAiMatchScore() : job.getMatchScore(),
            reason,
            job.getUrl()
        );
    }

    /**
     * Sends text-only notification when general resume PDF is not available.
     */
    private void sendGeneralResumeTextOnly(Job job, String reason) {
        try {
            String message = String.format("""
                📄 <b>Resume for This Job</b>

                🏢 <b>Job:</b> %s at %s
                ⭐ <b>Match:</b> %.0f%%

                💡 <b>Reason:</b> %s

                ⚠️ <b>Action Required:</b>
                Please attach your general resume manually

                🔗 <a href="%s">Click to Apply</a>
                """,
                job.getTitle(),
                job.getCompany(),
                job.getAiMatchScore() != null ? job.getAiMatchScore() : job.getMatchScore(),
                reason,
                job.getUrl()
            );

            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", message);
            payload.put("parse_mode", "HTML");
            payload.put("disable_web_page_preview", true);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            log.info("General resume text notification sent");

        } catch (Exception e) {
            log.error("Failed to send general resume text notification: {}", e.getMessage());
        }
    }

    /**
     * Sends notification when resume tailoring fails.
     * @deprecated Use sendGeneralResume instead
     */
    @Deprecated
    public void sendResumeTailoringFailure(Job job, String errorReason) {
        try {
            String message = String.format("""
                ⚠️ <b>Resume Tailoring Failed</b>

                🏢 <b>Job:</b> %s at %s
                ⭐ <b>Match:</b> %.0f%%

                ❌ <b>Reason:</b> %s

                💡 <b>You can still apply manually:</b>
                🔗 <a href="%s">Click to Apply</a>

                📝 <i>Use your general resume for this application</i>
                """,
                job.getTitle(),
                job.getCompany(),
                job.getAiMatchScore() != null ? job.getAiMatchScore() : job.getMatchScore(),
                errorReason,
                job.getUrl()
            );

            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", message);
            payload.put("parse_mode", "HTML");
            payload.put("disable_web_page_preview", true);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

            log.info("Resume failure notification sent: {}", response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to send resume failure notification: {}", e.getMessage());
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.isEmpty()) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
