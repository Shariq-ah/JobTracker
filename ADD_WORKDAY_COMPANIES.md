# Adding Workday Companies - Complete Guide

## 📚 Table of Contents
1. [Quick Start](#quick-start)
2. [Available Resources](#available-resources)
3. [Step-by-Step Process](#step-by-step-process)
4. [Testing](#testing)
5. [Troubleshooting](#troubleshooting)

---

## 🚀 Quick Start

You have everything you need to add Workday companies! Here's the workflow:

```bash
# 1. Find facet IDs for a company (e.g., Google)
#    - Open browser DevTools
#    - Go to Google Careers
#    - Search "Java" in "India"
#    - Network tab → Copy facet IDs from POST request

# 2. Test the API with your script
./test-workday-api.sh google \
  "https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs" \
  "bc33aa3152ec42d4995f4791a106ed09" \
  "5e7e18fb01f5449bb3fdb291f5a34798" \
  "0c40f6bd1d8f10ae43ffaefd46dc7e78"

# 3. Copy the generated Java code to ProviderRegistry.java

# 4. Add property to application-prod.properties

# 5. Test locally
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 6. Monitor logs
# Look for: "Fetching from: Google"
# Look for: "X jobs fetched from Google"
```

---

## 📂 Available Resources

I've created 3 files to help you:

### 1. `WORKDAY_FACET_FINDER.md`
**What it contains**:
- Detailed guide on finding facet IDs using browser DevTools
- Step-by-step screenshots guidance
- Common facet ID patterns
- Troubleshooting tips
- Testing with curl commands

**When to use**: When you need to find facet IDs for a new company

---

### 2. `WORKDAY_COMPANIES_TEMPLATE.md`
**What it contains**:
- Ready-to-use code templates for 6 high-value companies:
  - Google (expected ~80 jobs/run)
  - Adobe (expected ~25 jobs/run)
  - Salesforce (expected ~35 jobs/run)
  - Netflix (expected ~8 jobs/run)
  - Walmart (expected ~50 jobs/run)
  - Target (expected ~20 jobs/run)
- Complete integration checklist
- Expected job volumes
- Common issues and solutions

**When to use**: When you want to add one of these specific companies

---

### 3. `test-workday-api.sh` (Executable Script)
**What it does**:
- Tests Workday API with your facet IDs
- Shows sample jobs from the API
- **Automatically generates Java code** for ProviderRegistry.java
- **Automatically generates property** for application-prod.properties
- Validates your configuration before coding

**When to use**: Before adding a company to code - verify API works first!

---

## 🔄 Step-by-Step Process

### Step 1: Choose a Company

Pick from the recommended list:
- **Google** (highest volume, great roles)
- **Adobe** (strong Java focus)
- **Salesforce** (enterprise Java)
- **Netflix** (high quality, selective)
- **Walmart** (large teams in India)
- **Target** (growing tech presence)

Or find your own Workday company!

---

### Step 2: Find Facet IDs

#### Option A: Use Browser DevTools (Recommended)

1. Go to company careers page
2. Open DevTools (F12)
3. Go to **Network** tab
4. Filter by **XHR** or **Fetch**
5. Search "Java" in "India"
6. Find POST request to `*.myworkdayjobs.com`
7. Click request → **Payload** tab
8. Copy facet IDs from JSON:
   ```json
   {
     "appliedFacets": {
       "locationCountry": ["<COPY_THIS>"],
       "timeType": ["<COPY_THIS>"],
       "jobFamily": ["<COPY_THIS>"]
     }
   }
   ```

#### Option B: Test Without Facets First

Run script without facet IDs to see all jobs:
```bash
./test-workday-api.sh google \
  "https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs"
```

Then filter manually to find India/Java jobs.

---

### Step 3: Test API with Script

```bash
./test-workday-api.sh <company> <url> <location_facet> <time_facet> <job_family_facet>
```

**Example for Google**:
```bash
./test-workday-api.sh google \
  "https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs" \
  "bc33aa3152ec42d4995f4791a106ed09" \
  "5e7e18fb01f5449bb3fdb291f5a34798" \
  "0c40f6bd1d8f10ae43ffaefd46dc7e78"
```

**Script Output**:
- ✅ Shows if API call succeeded
- 📊 Shows total jobs available
- 📋 Shows sample job listings
- 💻 **Generates Java code** for you to copy
- ⚙️ **Generates property** for you to copy

---

### Step 4: Add to ProviderRegistry.java

Copy the generated Java code from script output:

```java
// ─── Google ─────────────────────────────────────────────────
private JobProvider google() {
    return new DynamicJobProvider(
            JobProviderConfig.builder()
                    .companyName("Google")
                    .platform(Platform.WORKDAY)
                    .listUrl("https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs")
                    .jdUrl("https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers")
                    .keyword("Java")
                    .limit(20)
                    .lookbackDays(2)
                    .locationCountryId("bc33aa3152ec42d4995f4791a106ed09")  // India
                    .timeTypeId("5e7e18fb01f5449bb3fdb291f5a34798")        // Full-time
                    .jobFamilyId("0c40f6bd1d8f10ae43ffaefd46dc7e78")       // Engineering
                    .jdFetchDelayMs(500)
                    .jdFetchThreads(2)
                    .build(),
            oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
}
```

Add toggle field to constructor area:
```java
@Value("${provider.google.enabled:true}")
private boolean googleEnabled;
```

Add to `jobProviders()` method:
```java
if (googleEnabled) {
    providers.add(google());
} else {
    log.warn("⏸️ Google provider DISABLED via config");
}
```

---

### Step 5: Add Toggle to application-prod.properties

```properties
# Google provider toggle
provider.google.enabled=${PROVIDER_GOOGLE_ENABLED:true}
```

---

### Step 6: Test Locally

```bash
# Build
./mvnw clean compile

# Run with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Watch logs for:
# "Fetching from: Google"
# "X jobs fetched from Google"
# "NEW JOB: [Job Title] | Score: XX%"
```

---

### Step 7: Verify in Telegram

Check your Telegram for notifications from the new provider.

---

### Step 8: Update Documentation

Add to `PROVIDERS.md`:
```markdown
## Google

**Platform:** Workday  
**Company Name:** Google

### API Endpoints

#### Job List API
https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs

[... add details like Visa example ...]
```

---

## 🧪 Testing

### Test API Before Coding

```bash
# Test without filters (see all jobs)
./test-workday-api.sh google \
  "https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs"

# Test with filters (see filtered jobs)
./test-workday-api.sh google \
  "https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs" \
  "bc33aa3152ec42d4995f4791a106ed09" \
  "5e7e18fb01f5449bb3fdb291f5a34798" \
  "0c40f6bd1d8f10ae43ffaefd46dc7e78"
```

### Test in Application

```bash
# Run locally
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Monitor logs
tail -f logs/jobtracker.log | grep -E "(Fetching from|jobs fetched|NEW JOB)"

# Check MongoDB
mongosh
use jobtracker
db.jobs.find({company: "Google"}).count()
db.jobs.find({company: "Google"}).limit(3).pretty()
```

### Test with Toggle

```bash
# Disable provider via environment variable
export PROVIDER_GOOGLE_ENABLED=false

# Run
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Should see: "⏸️ Google provider DISABLED via config"
```

---

## 🔧 Troubleshooting

### Issue: "No jobs found"

**Possible Causes**:
1. Wrong facet IDs
2. Wrong site name in URL
3. No Java jobs in India right now

**Solutions**:
```bash
# 1. Test without filters first
./test-workday-api.sh google \
  "https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs"

# 2. Verify facet IDs in browser DevTools
# 3. Check careers page manually
```

---

### Issue: "403 Forbidden"

**Cause**: Missing or incorrect headers

**Solution**: WorkdayPlatformHandler already sets correct headers. If still failing:
- Verify URL is correct
- Check if company blocks automated requests
- Try different User-Agent string

---

### Issue: "Invalid JSON response"

**Cause**: Wrong URL or API endpoint changed

**Solutions**:
```bash
# Test with curl directly
curl -X POST "https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "User-Agent: Mozilla/5.0" \
  -d '{"limit": 5, "offset": 0, "searchText": "Java"}' | jq .

# Check response structure
```

---

### Issue: "Duplicate jobs with Visa"

**Cause**: Same job appears in both providers

**Solution**: This is expected! MongoDB deduplication prevents duplicates. Both companies may list same job.

---

### Issue: "Rate limited (429 error)"

**Cause**: Too many requests too quickly

**Solutions**:
1. Increase `jdFetchDelayMs` from 500 to 1000
2. Reduce `jdFetchThreads` from 2 to 1
3. Add retry logic if needed (see MicrosoftPlatformHandler for example)

---

## 📊 Expected Results

After adding **Google + Adobe + Salesforce**:

| Metric | Before | After |
|--------|--------|-------|
| **Total Providers** | 7 | 10 |
| **Expected Jobs/Run** | ~250 | ~390 |
| **New Jobs/Day** | ~40 | ~65 |
| **AI Cost/Month** | $1.60 | $2.60 |
| **Processing Time/Run** | ~90s | ~120s |

**Cost is still very reasonable!** Only ~$2.60/month for 10 providers.

---

## ✅ Checklist

Use this checklist for each company you add:

- [ ] Find company's Workday URL
- [ ] Find facet IDs using browser DevTools
- [ ] Test API with `test-workday-api.sh` script
- [ ] Verify API returns India Java jobs
- [ ] Copy generated Java code to `ProviderRegistry.java`
- [ ] Add `@Value` annotation for toggle
- [ ] Add to `jobProviders()` list with toggle check
- [ ] Add toggle to `application-prod.properties`
- [ ] Build project: `./mvnw clean compile`
- [ ] Test locally: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- [ ] Monitor logs for 2-3 scheduler runs
- [ ] Verify Telegram notifications
- [ ] Check MongoDB for new jobs
- [ ] Test toggle (disable via env var)
- [ ] Update `PROVIDERS.md` documentation
- [ ] Update `CLAUDE.md` supported companies list
- [ ] Commit: `git commit -m "feat: Add [Company] Workday provider"`
- [ ] Push to production

---

## 🎯 Recommended Order

Add companies in this order (easy → hard):

1. **Salesforce** - Well-documented Workday site, easy to find facets
2. **Adobe** - Similar structure to Salesforce
3. **Walmart** - Large volume, straightforward setup
4. **Target** - Similar to Walmart
5. **Google** - May have more complex filtering
6. **Netflix** - Selective, may have fewer jobs

---

## 💡 Pro Tips

1. **Test one at a time**: Don't add all 6 companies at once. Add → Test → Commit → Repeat.

2. **Monitor first run carefully**: Watch logs for any errors or unexpected behavior.

3. **Check job quality**: First few runs, manually check if jobs are relevant (India, Java, correct experience level).

4. **Adjust rate limits if needed**: Start conservative (500ms, 2 threads). Increase if needed.

5. **Document facet IDs**: Add comment with how you found them for future reference.

6. **Keep PROVIDERS.md updated**: Future you will thank you!

---

## 📝 Example: Adding Google (Full Walkthrough)

```bash
# 1. Find facet IDs in browser
# Open https://www.google.com/about/careers/applications/
# Search "Java" → Location "India"
# DevTools → Network → POST to google.wd5.myworkdayjobs.com
# Copy facet IDs from request payload

# 2. Test API
./test-workday-api.sh google \
  "https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs" \
  "bc33aa3152ec42d4995f4791a106ed09" \
  "5e7e18fb01f5449bb3fdb291f5a34798" \
  "0c40f6bd1d8f10ae43ffaefd46dc7e78"

# Output shows:
# ✅ Success!
# Total jobs available: 87
# Jobs returned: 5
# [... sample jobs ...]
# [... generated Java code ...]

# 3. Copy generated code to ProviderRegistry.java
# (Script output provides ready-to-use code)

# 4. Add to application-prod.properties
echo "provider.google.enabled=\${PROVIDER_GOOGLE_ENABLED:true}" >> \
  src/main/resources/application-prod.properties

# 5. Build and test
./mvnw clean compile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 6. Watch logs
# Look for:
# "Fetching from: Google"
# "87 jobs fetched from Google"
# "NEW JOB: Software Engineer III | Score: 85%"

# 7. Check Telegram
# Should receive notifications for new Google jobs

# 8. Commit
git add .
git commit -m "feat: Add Google Workday provider with 87 jobs"
git push origin main
```

Done! 🎉

---

## 📞 Need Help?

If you run into issues:

1. **Check logs**: Look for error messages in console
2. **Test with curl**: Verify API is accessible
3. **Compare with Visa**: Visa is working example, compare your config
4. **Check browser DevTools**: Verify facet IDs are correct
5. **Disable temporarily**: Use toggle if provider is causing issues

---

**Last Updated**: 2026-07-24  
**Version**: 1.0  
**Maintainer**: Sharique

---

Happy job hunting! 🚀
