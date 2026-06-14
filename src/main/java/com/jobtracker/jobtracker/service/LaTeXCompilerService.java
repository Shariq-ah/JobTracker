package com.jobtracker.jobtracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for compiling LaTeX source code to PDF.
 * Priority: Local pdflatex (faster, more reliable) → YtoTech API (fallback)
 */
@Service
@Slf4j
public class LaTeXCompilerService {

    private final LocalLaTeXCompilerService localCompiler;

    @Value("${latex.online.api.url:https://ytotech.com}")
    private String latexApiUrl;

    @Value("${latex.compilation.timeout.seconds:30}")
    private int timeoutSeconds;

    @Value("${latex.local.enabled:true}")
    private boolean localEnabled;

    public LaTeXCompilerService(LocalLaTeXCompilerService localCompiler) {
        this.localCompiler = localCompiler;
    }

    /**
     * Compiles LaTeX source code to PDF.
     * Tries local pdflatex first (faster), falls back to YtoTech API if unavailable.
     *
     * @param latexSource Complete LaTeX document source
     * @return PDF file as byte array
     * @throws RuntimeException if compilation fails
     */
    public byte[] compileToPDF(String latexSource) {
        // Try local compilation first (faster and more reliable)
        if (localEnabled && localCompiler.isPdflatexAvailable()) {
            try {
                log.debug("Compiling LaTeX locally...");
                return localCompiler.compileToPDF(latexSource);
            } catch (Exception e) {
                log.warn("Local LaTeX compilation failed, falling back to API: {}", e.getMessage());
            }
        }

        // Fallback to YtoTech API
        return compileToPDFViaAPI(latexSource);
    }

    /**
     * Compiles LaTeX using YtoTech online API (fallback method).
     */
    private byte[] compileToPDFViaAPI(String latexSource) {
        try {
            log.debug("Compiling LaTeX via YtoTech API...");

            // Build YtoTech API payload (matches working example format)
            Map<String, Object> payload = new HashMap<>();
            payload.put("compiler", "pdflatex");

            Map<String, Object> resource = new HashMap<>();
            resource.put("name", "main.tex");
            resource.put("content", latexSource);
            // Note: Don't include "main": true - not needed

            payload.put("resources", List.of(resource));

            // Create HTTP client with redirect handling
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                .build();

            // Convert payload to JSON
            tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // Build HTTP request
            java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(latexApiUrl))
                .header("Content-Type", "application/json")
                .header("User-Agent", "JobTracker-Resume-Generator")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .build();

            // Send request and get PDF
            java.net.http.HttpResponse<byte[]> httpResponse = httpClient.send(
                httpRequest,
                java.net.http.HttpResponse.BodyHandlers.ofByteArray()
            );

            if (httpResponse.statusCode() == 200 && httpResponse.body() != null) {
                // CRITICAL: Check Content-Type to verify it's actually a PDF
                String contentType = httpResponse.headers().firstValue("Content-Type").orElse("");

                if (contentType.contains("application/pdf")) {
                    byte[] pdfBytes = httpResponse.body();
                    log.info("✅ LaTeX compiled via YtoTech API ({} bytes)", pdfBytes.length);
                    return pdfBytes;
                } else {
                    // API returned HTML error page instead of PDF
                    String errorBody = new String(httpResponse.body());
                    log.error("❌ API did not return PDF. Content-Type: {}", contentType);
                    log.error("Server Response: {}", errorBody.length() > 500 ? errorBody.substring(0, 500) : errorBody);
                    throw new RuntimeException("LaTeX API returned " + contentType + " instead of PDF");
                }
            } else {
                String errorBody = httpResponse.body() != null ? new String(httpResponse.body()) : "No body";
                log.error("Server Logs: {}", errorBody.length() > 500 ? errorBody.substring(0, 500) : errorBody);
                throw new RuntimeException("LaTeX API returned status: " + httpResponse.statusCode());
            }

        } catch (Exception e) {
            log.error("❌ LaTeX compilation error: {}", e.getMessage());
            throw new RuntimeException("LaTeX compilation failed: " + e.getMessage(), e);
        }
    }
}
