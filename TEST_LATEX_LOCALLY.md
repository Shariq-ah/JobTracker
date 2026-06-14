# Testing LaTeX Compilation Locally

## Option 1: Full App Test (Recommended)

This tests the entire flow: AI extraction → Resume tailoring → LaTeX compilation → Telegram send

### Steps:

1. **Enable Dev Mode** (to avoid AI costs):
```bash
export AI_DEV_MOCK_ENABLED=true
```

2. **Lower Resume Threshold** (temporarily, to force resume generation):
```bash
# Edit application-dev.properties
resume.tailoring.min-score=30  # Lower from 70 to force generation
```

3. **Run Application**:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

4. **Watch Logs** for:
```
🎭 DEV MODE: Using mock response (FREE - no API call)
🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)
Compiling LaTeX via YtoTech API...
✅ LaTeX compiled via YtoTech API (5243 bytes)  ← SUCCESS!
```

5. **Check Telegram** - You should receive a PDF

6. **Restore Threshold** after testing:
```bash
resume.tailoring.min-score=70
```

---

## Option 2: Quick Standalone Test

Test LaTeX compilation directly without running full app.

### Create Test File:

Save as `TestLatexCompiler.java` in project root:

```java
import com.jobtracker.jobtracker.service.LaTeXCompilerService;

public class TestLatexCompiler {
    public static void main(String[] args) {
        // Simple LaTeX document
        String testLatex = """
            \\documentclass{article}
            \\begin{document}
            \\title{Test Resume}
            \\author{Sharique Ahmad}
            \\date{\\today}
            \\maketitle
            
            \\section{Experience}
            Software Engineer with 3.6 years experience in Java and Spring Boot.
            
            \\section{Skills}
            Java, Spring Boot, Microservices, Kafka, Docker
            
            \\end{document}
            """;

        LaTeXCompilerService compiler = new LaTeXCompilerService();
        
        try {
            System.out.println("Testing LaTeX compilation...");
            byte[] pdf = compiler.compileToPDF(testLatex);
            
            // Save to file
            java.nio.file.Files.write(
                java.nio.file.Paths.get("test_resume.pdf"),
                pdf
            );
            
            System.out.println("✅ SUCCESS! PDF saved to: test_resume.pdf");
            System.out.println("Size: " + pdf.length + " bytes");
            
        } catch (Exception e) {
            System.err.println("❌ FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### Run Test:
```bash
# Compile
javac -cp "target/classes:$(./mvnw dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" TestLatexCompiler.java

# Run
java -cp ".:target/classes:$(./mvnw dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" TestLatexCompiler

# Check output
ls -lh test_resume.pdf
open test_resume.pdf  # Mac
# or xdg-open test_resume.pdf  # Linux
```

---

## Option 3: API Test (Fastest)

Test YtoTech API directly without Java code.

### Using curl:

```bash
curl -X POST https://ytotech.com \
  -H "Content-Type: application/json" \
  -d '{
    "compiler": "pdflatex",
    "resources": [{
      "name": "main.tex",
      "content": "\\documentclass{article}\\begin{document}Hello World!\\end{document}"
    }]
  }' \
  --output test.pdf

# Check result
file test.pdf
# Should show: "test.pdf: PDF document, version 1.X"

# If it shows HTML:
cat test.pdf  # Will show HTML error page
```

---

## What to Look For

### ✅ Success Indicators:
- Log shows: `✅ LaTeX compiled via YtoTech API (XXXX bytes)`
- PDF file size: ~5-10 KB (not 3 KB which would be HTML)
- `file test.pdf` shows: `PDF document`
- PDF opens correctly in viewer

### ❌ Failure Indicators:
- Log shows: `❌ API did not return PDF. Content-Type: text/html`
- File contains `<!DOCTYPE html>` when opened in text editor
- PDF viewer can't open it
- Error in logs about LaTeX compilation

---

## Common Issues & Fixes

### Issue 1: "Package X not found"
**Cause**: YtoTech doesn't have that LaTeX package  
**Fix**: Remove the package from `base_resume.tex` or use simpler template

### Issue 2: "Compilation timeout"
**Cause**: Template too complex  
**Fix**: Increase timeout in properties:
```properties
latex.compilation.timeout.seconds=60
```

### Issue 3: HTML returned instead of PDF
**Cause**: LaTeX syntax error  
**Fix**: Check logs for specific error, fix template syntax

---

## Quick Troubleshooting

```bash
# Check if YtoTech API is accessible
curl -I https://ytotech.com
# Should return: HTTP/1.1 200 OK

# Test with minimal LaTeX
echo '{"compiler":"pdflatex","resources":[{"name":"main.tex","content":"\\documentclass{article}\\begin{document}Test\\end{document}"}]}' | \
  curl -X POST https://ytotech.com -H "Content-Type: application/json" -d @- --output minimal.pdf

# Check if it's actually PDF
file minimal.pdf
```

---

## After Testing

1. **If successful**: Deploy to production with confidence! ✅
2. **If fails**: Check error logs, simplify template, or consider alternative LaTeX API
3. **Don't forget**: Restore `resume.tailoring.min-score=70` after testing

---

**Tip**: Option 1 (Full App Test) is recommended as it tests the entire pipeline including AI mocking, which saves you money during development.
