# Development Mode - Zero Cost Testing

## Problem
Every time you test JobTracker locally, it calls AWS Bedrock (Claude Haiku), costing money:
- **JD Extraction**: ~$0.001 per job × 20 jobs = $0.02 per test run
- **Resume Tailoring**: ~$0.004 per resume × 5 resumes = $0.02 per test run
- **Total**: ~$0.04 per test run
- **10 test runs per day**: ~$0.40/day or **$12/month just for testing!**

## Solution: Dev Mock Mode ✅

Development mode uses **mock responses** instead of calling Claude API.

### How to Enable

**Option 1: Environment Variable (Recommended)**
```bash
export AI_DEV_MOCK_ENABLED=true
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Option 2: Update application-dev.properties**
```properties
ai.dev.mock.enabled=true
```

**Option 3: Command Line**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dai.dev.mock.enabled=true
```

---

## How It Works

### JD Extraction (Dev Mode)
Instead of calling Bedrock:
```
✅ Real API: ~$0.001 per job
🎭 Dev Mock: $0.00 (FREE)
```

Mock response includes:
- Match score (60-95%, varies by job)
- Recommendation (Apply/Strong Apply/etc)
- Matched/missing skills
- Experience requirements
- Work mode, salary, etc.

**Log output:**
```
🎭 DEV MODE: Using mock response (FREE - no API call)
Successfully extracted structured data for: Software Engineer - Java
```

### Resume Tailoring (Dev Mode)
Instead of calling Claude API:
```
✅ Real API: ~$0.004 per resume
🎭 Dev Mock: $0.00 (FREE)
```

Mock response includes:
- Tailored experience bullets
- ATS score (75-94%, varies by company)
- ATS reasoning

**Log output:**
```
🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)
✅ Tailored resume sent | ATS Score: 88 | Cost: $0.004
```

---

## Mock Response Behavior

### Realistic Variation
Scores vary deterministically based on job title/company hash:
- **JD Match Score**: 60-95%
- **ATS Score**: 75-94%
- **Adjusts for keywords**: Spring Boot +5%, Kafka +3%, Microservices +2%

This simulates real-world variability for testing.

### What Gets Mocked
✅ JD extraction (Bedrock API call)  
✅ Resume tailoring (Bedrock API call)  
❌ LaTeX compilation (still uses real YtoTech API - minimal cost)  
❌ Telegram notifications (still sends real messages)  
❌ MongoDB saves (still saves to DB)  

---

## When to Use Each Mode

| Scenario | Mode | Setting |
|----------|------|---------|
| **Local development** | Dev Mock | `ai.dev.mock.enabled=true` |
| **Testing filters** | Dev Mock | `ai.dev.mock.enabled=true` |
| **Testing resume layout** | Dev Mock | `ai.dev.mock.enabled=true` |
| **Testing AI accuracy** | Production | `ai.dev.mock.enabled=false` |
| **Production deployment** | Production | `ai.dev.mock.enabled=false` |

---

## Cost Comparison

### 10 Test Runs (Typical Daily Development)

**Without Dev Mode:**
```
JD Extraction: 10 runs × 20 jobs × $0.001 = $0.20
Resume Tailoring: 10 runs × 5 resumes × $0.004 = $0.20
Total: $0.40/day or $12/month
```

**With Dev Mode:**
```
JD Extraction: $0.00
Resume Tailoring: $0.00
Total: $0.00/day or $0/month ✅
```

**Savings: 100% of development costs**

---

## Verification

### Check if Dev Mode is Active

**Look for these log messages:**
```
🎭 DEV MODE: Using mock response (FREE - no API call)
🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)
```

If you see these, dev mode is working!

### Check Current Setting

```bash
grep "ai.dev.mock.enabled" src/main/resources/application-dev.properties
```

Expected output:
```
ai.dev.mock.enabled=${AI_DEV_MOCK_ENABLED:false}
```

---

## Troubleshooting

### Dev Mode Not Working
1. Verify environment variable is set:
   ```bash
   echo $AI_DEV_MOCK_ENABLED
   ```
   Should output: `true`

2. Check profile is correct:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. Restart application after changing settings

### Still Seeing API Costs
- Check AWS CloudWatch logs - should show zero Bedrock invocations
- Verify logs show "🎭 DEV MODE" messages
- Ensure `application-dev.properties` is being loaded (not prod)

---

## Best Practices

1. **Always use dev mode for local testing** - save money!
2. **Disable before production deployment** - ensure setting is `false` in prod
3. **Test AI accuracy occasionally** - disable dev mode periodically to verify real API responses
4. **Use environment variables** - easier to toggle without code changes

---

## Production Checklist

Before deploying to production:

- [ ] `ai.dev.mock.enabled=false` in application-prod.properties
- [ ] Environment variable `AI_DEV_MOCK_ENABLED` not set (or set to `false`)
- [ ] No "🎭 DEV MODE" messages in production logs
- [ ] Real API calls working correctly

---

**Last Updated**: 2026-06-14  
**Version**: 3.0.1 (Dev Mode)
