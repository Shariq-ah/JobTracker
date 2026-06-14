package com.jobtracker.jobtracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Service for compiling LaTeX source code to PDF using local pdflatex installation.
 * Faster and more reliable than external API.
 * Requires texlive-latex-base and texlive-latex-extra packages installed on system.
 */
@Service
@Slf4j
public class LocalLaTeXCompilerService {

    @Value("${latex.local.enabled:true}")
    private boolean localEnabled;

    @Value("${latex.temp.dir:/tmp/jobtracker/latex}")
    private String tempDirPath;

    @Value("${latex.compilation.timeout.seconds:30}")
    private int timeoutSeconds;

    /**
     * Compiles LaTeX source code to PDF using local pdflatex command.
     * Falls back to API if local compilation not available.
     *
     * @param latexSource Complete LaTeX document source
     * @return PDF file as byte array
     * @throws RuntimeException if compilation fails
     */
    public byte[] compileToPDF(String latexSource) {
        if (!localEnabled || !isPdflatexAvailable()) {
            throw new RuntimeException("Local pdflatex not available - use API fallback");
        }

        Path workDir = null;
        try {
            // Create temporary working directory
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            workDir = Paths.get(tempDirPath, uniqueId);
            Files.createDirectories(workDir);

            // Write LaTeX source to file
            Path texFile = workDir.resolve("resume.tex");
            Files.writeString(texFile, latexSource, StandardCharsets.UTF_8);

            log.debug("Compiling LaTeX locally: {}", texFile);

            // Run pdflatex command
            ProcessBuilder pb = new ProcessBuilder(
                "pdflatex",
                "-interaction=nonstopmode",  // Don't stop on errors
                "-output-directory=" + workDir.toString(),
                texFile.toString()
            );
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Capture output for debugging
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for completion with timeout
            boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("pdflatex compilation timeout after " + timeoutSeconds + "s");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("pdflatex failed with exit code: {}", exitCode);
                log.debug("pdflatex output: {}", output.toString());
                throw new RuntimeException("pdflatex compilation failed (exit code: " + exitCode + ")");
            }

            // Read generated PDF
            Path pdfFile = workDir.resolve("resume.pdf");
            if (!Files.exists(pdfFile)) {
                throw new RuntimeException("PDF file not generated at: " + pdfFile);
            }

            byte[] pdfBytes = Files.readAllBytes(pdfFile);
            log.info("✅ LaTeX compiled locally ({} bytes)", pdfBytes.length);

            return pdfBytes;

        } catch (Exception e) {
            log.error("❌ Local LaTeX compilation error: {}", e.getMessage());
            throw new RuntimeException("Local LaTeX compilation failed: " + e.getMessage(), e);
        } finally {
            // Cleanup temporary directory
            if (workDir != null) {
                try {
                    deleteDirectory(workDir);
                } catch (Exception e) {
                    log.warn("Failed to cleanup temp directory: {}", workDir);
                }
            }
        }
    }

    /**
     * Checks if pdflatex is available on the system.
     */
    public boolean isPdflatexAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("pdflatex", "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectory(Path directory) throws Exception {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                });
        }
    }
}
