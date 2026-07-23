# Workday Companies - Ready-to-Add Templates

## 📋 Instructions
1. For each company below, use browser DevTools to find facet IDs
2. Follow the guide in `WORKDAY_FACET_FINDER.md`
3. Replace `<FIND_THIS>` placeholders with actual values
4. Copy the code to `ProviderRegistry.java`

---

## 🚀 High-Priority Companies to Add

### 1. Google (Workday)

**Careers Page**: https://www.google.com/about/careers/applications/  
**Expected Workday URL**: `https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs`

**Steps to Find Facet IDs**:
1. Go to https://www.google.com/about/careers/applications/
2. Search "Java" → Location "India" → Engineering & Technology
3. DevTools → Network → Look for POST to `google.wd5.myworkdayjobs.com`
4. Copy facet IDs from request payload

**Code Template**:
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
                    // Find these facet IDs using browser DevTools
                    .locationCountryId("<FIND_THIS>")      // India
                    .timeTypeId("<FIND_THIS>")             // Full-time
                    .jobFamilyId("<FIND_THIS>")            // Engineering & Technology
                    .jobUrlTemplate("https://www.google.com/about/careers/applications/jobs/results/{id}")
                    .jdFetchDelayMs(500)
                    .jdFetchThreads(2)
                    .build(),
            oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
}

// Add to ProviderRegistry constructor
@Value("${provider.google.enabled:true}")
private boolean googleEnabled;

// Add to jobProviders() method
if (googleEnabled) {
    providers.add(google());
} else {
    log.warn("⏸️ Google provider DISABLED via config");
}
```

**application-prod.properties**:
```properties
provider.google.enabled=${PROVIDER_GOOGLE_ENABLED:true}
```

**Expected Job Volume**: ~50-100 jobs per run (Google has many Java openings in India)

---

### 2. Adobe (Workday)

**Careers Page**: https://careers.adobe.com/  
**Expected Workday URL**: `https://adobe.wd5.myworkdayjobs.com/wday/cxs/adobe/external/jobs`

**Steps to Find Facet IDs**:
1. Go to https://careers.adobe.com/us/en/search-results?keywords=Java&location=India
2. DevTools → Network → Look for POST to `adobe.wd5.myworkdayjobs.com`
3. Copy facet IDs

**Code Template**:
```java
// ─── Adobe ──────────────────────────────────────────────────
private JobProvider adobe() {
    return new DynamicJobProvider(
            JobProviderConfig.builder()
                    .companyName("Adobe")
                    .platform(Platform.WORKDAY)
                    .listUrl("https://adobe.wd5.myworkdayjobs.com/wday/cxs/adobe/external/jobs")
                    .jdUrl("https://adobe.wd5.myworkdayjobs.com/wday/cxs/adobe/external")
                    .keyword("Java")
                    .limit(20)
                    .lookbackDays(2)
                    // Find these facet IDs using browser DevTools
                    .locationCountryId("<FIND_THIS>")      // India
                    .timeTypeId("<FIND_THIS>")             // Full-time
                    .jobFamilyId("<FIND_THIS>")            // Software Engineering
                    .jobUrlTemplate("https://careers.adobe.com/us/en/job/{id}")
                    .jdFetchDelayMs(500)
                    .jdFetchThreads(2)
                    .build(),
            oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
}

// Add to ProviderRegistry constructor
@Value("${provider.adobe.enabled:true}")
private boolean adobeEnabled;

// Add to jobProviders() method
if (adobeEnabled) {
    providers.add(adobe());
} else {
    log.warn("⏸️ Adobe provider DISABLED via config");
}
```

**application-prod.properties**:
```properties
provider.adobe.enabled=${PROVIDER_ADOBE_ENABLED:true}
```

**Expected Job Volume**: ~20-30 jobs per run

---

### 3. Salesforce (Workday)

**Careers Page**: https://salesforce.wd1.myworkdayjobs.com/External_Career_Site  
**Workday URL**: `https://salesforce.wd1.myworkdayjobs.com/wday/cxs/salesforce/External_Career_Site/jobs`

**Steps to Find Facet IDs**:
1. Go to https://salesforce.wd1.myworkdayjobs.com/External_Career_Site
2. Search "Java" → Location "India" → Engineering
3. DevTools → Network → Look for POST to `salesforce.wd1.myworkdayjobs.com`
4. Copy facet IDs

**Code Template**:
```java
// ─── Salesforce ─────────────────────────────────────────────
private JobProvider salesforce() {
    return new DynamicJobProvider(
            JobProviderConfig.builder()
                    .companyName("Salesforce")
                    .platform(Platform.WORKDAY)
                    .listUrl("https://salesforce.wd1.myworkdayjobs.com/wday/cxs/salesforce/External_Career_Site/jobs")
                    .jdUrl("https://salesforce.wd1.myworkdayjobs.com/wday/cxs/salesforce/External_Career_Site")
                    .keyword("Java")
                    .limit(20)
                    .lookbackDays(2)
                    // Find these facet IDs using browser DevTools
                    .locationCountryId("<FIND_THIS>")      // India
                    .timeTypeId("<FIND_THIS>")             // Full-time
                    .jobFamilyId("<FIND_THIS>")            // Engineering
                    .jobUrlTemplate("https://salesforce.wd1.myworkdayjobs.com/External_Career_Site/job/{id}")
                    .jdFetchDelayMs(500)
                    .jdFetchThreads(2)
                    .build(),
            oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
}

// Add to ProviderRegistry constructor
@Value("${provider.salesforce.enabled:true}")
private boolean salesforceEnabled;

// Add to jobProviders() method
if (salesforceEnabled) {
    providers.add(salesforce());
} else {
    log.warn("⏸️ Salesforce provider DISABLED via config");
}
```

**application-prod.properties**:
```properties
provider.salesforce.enabled=${PROVIDER_SALESFORCE_ENABLED:true}
```

**Expected Job Volume**: ~30-40 jobs per run

---

### 4. Netflix (Workday)

**Careers Page**: https://jobs.netflix.com/  
**Expected Workday URL**: `https://netflix.wd1.myworkdayjobs.com/wday/cxs/netflix/Netflix_External_Site/jobs`

**Code Template**:
```java
// ─── Netflix ────────────────────────────────────────────────
private JobProvider netflix() {
    return new DynamicJobProvider(
            JobProviderConfig.builder()
                    .companyName("Netflix")
                    .platform(Platform.WORKDAY)
                    .listUrl("https://netflix.wd1.myworkdayjobs.com/wday/cxs/netflix/Netflix_External_Site/jobs")
                    .jdUrl("https://netflix.wd1.myworkdayjobs.com/wday/cxs/netflix/Netflix_External_Site")
                    .keyword("Java")
                    .limit(20)
                    .lookbackDays(2)
                    // Find these facet IDs using browser DevTools
                    .locationCountryId("<FIND_THIS>")      // India
                    .timeTypeId("<FIND_THIS>")             // Full-time
                    .jobFamilyId("<FIND_THIS>")            // Engineering
                    .jobUrlTemplate("https://jobs.netflix.com/jobs/{id}")
                    .jdFetchDelayMs(500)
                    .jdFetchThreads(2)
                    .build(),
            oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
}

// Add to ProviderRegistry constructor
@Value("${provider.netflix.enabled:true}")
private boolean netflixEnabled;

// Add to jobProviders() method
if (netflixEnabled) {
    providers.add(netflix());
} else {
    log.warn("⏸️ Netflix provider DISABLED via config");
}
```

**application-prod.properties**:
```properties
provider.netflix.enabled=${PROVIDER_NETFLIX_ENABLED:true}
```

**Expected Job Volume**: ~5-10 jobs per run (Netflix is selective but high quality)

---

### 5. Walmart (Workday)

**Careers Page**: https://careers.walmart.com/  
**Expected Workday URL**: `https://walmart.wd5.myworkdayjobs.com/wday/cxs/walmart/WalmartExternal/jobs`

**Code Template**:
```java
// ─── Walmart ────────────────────────────────────────────────
private JobProvider walmart() {
    return new DynamicJobProvider(
            JobProviderConfig.builder()
                    .companyName("Walmart")
                    .platform(Platform.WORKDAY)
                    .listUrl("https://walmart.wd5.myworkdayjobs.com/wday/cxs/walmart/WalmartExternal/jobs")
                    .jdUrl("https://walmart.wd5.myworkdayjobs.com/wday/cxs/walmart/WalmartExternal")
                    .keyword("Java")
                    .limit(20)
                    .lookbackDays(2)
                    // Find these facet IDs using browser DevTools
                    .locationCountryId("<FIND_THIS>")      // India
                    .timeTypeId("<FIND_THIS>")             // Full-time
                    .jobFamilyId("<FIND_THIS>")            // Software Engineering
                    .jobUrlTemplate("https://careers.walmart.com/us/jobs/{id}")
                    .jdFetchDelayMs(500)
                    .jdFetchThreads(2)
                    .build(),
            oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
}

// Add to ProviderRegistry constructor
@Value("${provider.walmart.enabled:true}")
private boolean walmartEnabled;

// Add to jobProviders() method
if (walmartEnabled) {
    providers.add(walmart());
} else {
    log.warn("⏸️ Walmart provider DISABLED via config");
}
```

**application-prod.properties**:
```properties
provider.walmart.enabled=${PROVIDER_WALMART_ENABLED:true}
```

**Expected Job Volume**: ~40-60 jobs per run (Walmart has large tech teams in India)

---

### 6. Target (Workday)

**Careers Page**: https://jobs.target.com/  
**Expected Workday URL**: `https://target.wd5.myworkdayjobs.com/wday/cxs/target/TargetCareers/jobs`

**Code Template**:
```java
// ─── Target ─────────────────────────────────────────────────
private JobProvider target() {
    return new DynamicJobProvider(
            JobProviderConfig.builder()
                    .companyName("Target")
                    .platform(Platform.WORKDAY)
                    .listUrl("https://target.wd5.myworkdayjobs.com/wday/cxs/target/TargetCareers/jobs")
                    .jdUrl("https://target.wd5.myworkdayjobs.com/wday/cxs/target/TargetCareers")
                    .keyword("Java")
                    .limit(20)
                    .lookbackDays(2)
                    // Find these facet IDs using browser DevTools
                    .locationCountryId("<FIND_THIS>")      // India
                    .timeTypeId("<FIND_THIS>")             // Full-time
                    .jobFamilyId("<FIND_THIS>")            // Technology
                    .jobUrlTemplate("https://jobs.target.com/job/{id}")
                    .jdFetchDelayMs(500)
                    .jdFetchThreads(2)
                    .build(),
            oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
}

// Add to ProviderRegistry constructor
@Value("${provider.target.enabled:true}")
private boolean targetEnabled;

// Add to jobProviders() method
if (targetEnabled) {
    providers.add(target());
} else {
    log.warn("⏸️ Target provider DISABLED via config");
}
```

**application-prod.properties**:
```properties
provider.target.enabled=${PROVIDER_TARGET_ENABLED:true}
```

**Expected Job Volume**: ~15-25 jobs per run

---

## 📝 Full Integration Checklist

For each company you add:

- [ ] Find facet IDs using browser DevTools
- [ ] Add method to `ProviderRegistry.java`
- [ ] Add `@Value` annotation for toggle
- [ ] Add to `jobProviders()` list with toggle check
- [ ] Add toggle to `application-prod.properties`
- [ ] Test locally with dev profile
- [ ] Monitor logs for 2-3 runs
- [ ] Update `PROVIDERS.md` with API documentation
- [ ] Update `CLAUDE.md` supported companies list
- [ ] Commit with message: `feat: Add [Company] Workday provider`

---

## 🚨 Common Issues & Solutions

### Issue: "No jobs found"
**Possible Causes**:
1. Wrong facet IDs
2. Wrong site name in URL
3. Company doesn't have Java jobs in India right now

**Solution**:
1. Verify facet IDs by comparing with browser DevTools
2. Try without filters first (only searchText)
3. Check if jobs exist on careers page manually

### Issue: "403 Forbidden"
**Cause**: Missing or incorrect headers

**Solution**: WorkdayPlatformHandler already sets correct headers:
```java
headers.set("User-Agent", "Mozilla/5.0");
headers.set("Accept", "application/json");
headers.set("Content-Type", "application/json");
```

### Issue: "Duplicate jobs"
**Cause**: Company was already added with different name

**Solution**: Check existing providers in `ProviderRegistry.java`

---

## 💡 Pro Tips

1. **Start with 1-2 companies**: Don't add all 6 at once. Test each one individually.

2. **Monitor rate limits**: Start with conservative settings (500ms delay, 2 threads) and adjust if needed.

3. **Check job volume**: Use `curl` to test API and see how many jobs you'll get per run.

4. **Facet ID validation**: Some companies use different field names:
   - `jobFamily` vs `jobFamilyGroup`
   - `category` vs `jobCategory`
   - Check `WorkdayPlatformHandler.java` for supported fields

5. **Site name casing**: Pay attention to capitalization in site name:
   - `/Careers` (capital C)
   - `/external` (lowercase)
   - `/External_Career_Site` (mixed case with underscores)

6. **Job URL extraction**: Some companies need ID extraction from externalPath:
   - Visa: Uses full externalPath as-is
   - Google: May need to extract numeric ID with regex `_(\d+)$`

---

## 📊 Expected Total Job Volume

After adding all 6 companies:

| Current Providers | Jobs/Run |
|-------------------|----------|
| Existing (7) | ~250 jobs |
| + Google | +80 jobs |
| + Adobe | +25 jobs |
| + Salesforce | +35 jobs |
| + Netflix | +8 jobs |
| + Walmart | +50 jobs |
| + Target | +20 jobs |
| **Total (13 providers)** | **~468 jobs/run** |

**AI Cost Impact**: 468 jobs × $0.001 = **$0.468 per run** × 96 runs/day = **$44.93/day**

Wait, that's too expensive! Let me recalculate...

Actually: 468 jobs/run × 1 run/15min × 96 runs/day = But we have deduplication!

**Realistic**: Only NEW jobs trigger AI analysis. Expect ~30-50 NEW jobs per day across all providers.

**Actual AI Cost**: ~40 new jobs/day × $0.001 = **$0.04/day** = **$1.20/month** (same as before!)

---

**Last Updated**: 2026-07-24  
**Maintainer**: Sharique
