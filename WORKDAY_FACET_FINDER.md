# Workday Facet ID Finder Guide

## Quick Method to Find Facet IDs

### Step 1: Open Company Careers Page
Go to the company's careers page and search for "Java" jobs in "India".

### Step 2: Open Browser DevTools
1. Press `F12` or `Right-click → Inspect`
2. Go to **Network** tab
3. Filter by **XHR** or **Fetch**
4. Clear the network log

### Step 3: Trigger Search
1. Enter search term: "Java"
2. Select location: "India"
3. Apply filters: "Full-time", "Software Engineering"

### Step 4: Find the POST Request
Look for POST request to URL pattern:
```
https://{company}.wd{1-5}.myworkdayjobs.com/wday/cxs/{company}/{SiteName}/jobs
```

### Step 5: Copy Facet IDs from Request Payload
Click on the request → **Payload** tab → Copy JSON

Example payload:
```json
{
  "appliedFacets": {
    "locationCountry": ["c4f78be1a8f14da0ab49ce1162348a5e"],
    "timeType": ["3d32d47be90110109faf15aa8b2200bf"],
    "jobFamily": ["2745bc1368021016a991f65e710a3b1e"]
  },
  "limit": 20,
  "offset": 0,
  "searchText": "Java"
}
```

**Facet IDs to extract**:
- `locationCountry` → India facet ID
- `timeType` → Full-time facet ID
- `jobFamily` → Software Engineering facet ID

---

## Common Company Workday URLs

| Company | Careers URL | Workday URL Pattern |
|---------|-------------|---------------------|
| **Google** | https://www.google.com/about/careers/applications/ | `google.wd5.myworkdayjobs.com` |
| **Adobe** | https://careers.adobe.com | `adobe.wd5.myworkdayjobs.com` |
| **Salesforce** | https://salesforce.wd1.myworkdayjobs.com | `salesforce.wd1.myworkdayjobs.com` |
| **Netflix** | https://jobs.netflix.com | `netflix.wd1.myworkdayjobs.com` |
| **Walmart** | https://careers.walmart.com | `walmart.wd5.myworkdayjobs.com` |
| **Target** | https://jobs.target.com | `target.wd5.myworkdayjobs.com` |
| **Oracle** | https://oracle.taleo.net | ❌ Uses Taleo, not Workday |
| **Meta** | https://www.metacareers.com | ❌ Uses custom API, not Workday |

---

## Example: Finding Google Facet IDs

### 1. Go to Google Careers
https://www.google.com/about/careers/applications/

### 2. Search for Java in India
- Search: "Java"
- Location: "India"
- Filters: "Software Engineering"

### 3. Network Request (Example)
```http
POST https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs
```

**Request Payload**:
```json
{
  "appliedFacets": {
    "locationCountry": ["bc33aa3152ec42d4995f4791a106ed09"],  // India
    "timeType": ["5e7e18fb01f5449bb3fdb291f5a34798"],        // Full-time
    "jobFamilyGroup": ["0c40f6bd1d8f10ae43ffaefd46dc7e78"]  // Engineering & Technology
  },
  "limit": 20,
  "offset": 0,
  "searchText": "Java"
}
```

### 4. Job Description URL Pattern
Example job URL:
```
https://www.google.com/about/careers/applications/jobs/results/123456789
```

API returns `externalPath`:
```
/job/Bengaluru-India/Software-Engineer-III_123456789
```

Full JD API URL:
```
https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/job/Bengaluru-India/Software-Engineer-III_123456789
```

**Job Application URL Template**:
```
https://www.google.com/about/careers/applications/jobs/results/{externalId}
```
(Extract numeric ID from externalPath using regex: `_(\d+)$`)

---

## Testing Your Configuration

Once you have facet IDs, test with curl:

```bash
curl -X POST 'https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'User-Agent: Mozilla/5.0' \
  -d '{
    "appliedFacets": {
      "locationCountry": ["bc33aa3152ec42d4995f4791a106ed09"],
      "timeType": ["5e7e18fb01f5449bb3fdb291f5a34798"],
      "jobFamilyGroup": ["0c40f6bd1d8f10ae43ffaefd46dc7e78"]
    },
    "limit": 5,
    "offset": 0,
    "searchText": "Java"
  }'
```

**Expected Response**:
```json
{
  "total": 25,
  "jobPostings": [
    {
      "title": "Software Engineer III",
      "externalPath": "/job/Bengaluru-India/Software-Engineer-III_123456",
      "postedOn": "Posted Today",
      "locationsText": "Bengaluru, Karnataka, India"
    }
  ]
}
```

---

## Common Facet ID Patterns

**Note**: Facet IDs are **company-specific**. You MUST find them for each company.

However, some patterns exist:

### Location Country (India)
- Usually a long UUID-like string: `c4f78be1a8f14da0ab49ce1162348a5e`
- Sometimes shorter: `bc33aa3152ec42d4995f4791a106ed09`

### Time Type (Full-time)
- Pattern: `[0-9a-f]{32}` (32-char hex)
- Example: `3d32d47be90110109faf15aa8b2200bf`

### Job Family (Software Engineering)
- Pattern: `[0-9a-f]{32}`
- May be called:
  - `jobFamily` (Visa uses this)
  - `jobFamilyGroup` (Google uses this)
  - `category` (Some companies)

---

## Troubleshooting

### Issue: Empty Results
**Cause**: Wrong facet IDs or site name
**Solution**: 
- Verify site name in URL (e.g., `/Careers` vs `/careers`)
- Try without filters first (only searchText)
- Check if country code is correct

### Issue: 404 Not Found
**Cause**: Wrong Workday subdomain (wd1, wd5, etc.)
**Solution**:
- Check browser network tab for exact domain
- Try different wd numbers: wd1, wd5, wd12

### Issue: 403 Forbidden
**Cause**: Missing User-Agent or headers
**Solution**: Add these headers:
```
User-Agent: Mozilla/5.0
Accept: application/json
Content-Type: application/json
```

---

## Template for Adding New Company

Once you have facet IDs, add to `ProviderRegistry.java`:

```java
// ─── [Company Name] ─────────────────────────────────────────
private JobProvider companyName() {
    return new DynamicJobProvider(
            JobProviderConfig.builder()
                    .companyName("Company Name")
                    .platform(Platform.WORKDAY)
                    .listUrl("https://company.wd5.myworkdayjobs.com/wday/cxs/company/SiteName/jobs")
                    .jdUrl("https://company.wd5.myworkdayjobs.com/wday/cxs/company/SiteName")
                    .keyword("Java")
                    .limit(20)
                    .lookbackDays(2)
                    // Workday facet IDs (find using browser DevTools)
                    .locationCountryId("<facet_id_for_india>")      // India
                    .timeTypeId("<facet_id_for_fulltime>")          // Full-time
                    .jobFamilyId("<facet_id_for_engineering>")      // Software Engineering
                    .jdFetchDelayMs(500)
                    .jdFetchThreads(2)
                    .build(),
            oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
}
```

Then add to `jobProviders()` list:
```java
if (companyNameEnabled) {
    providers.add(companyName());
} else {
    log.warn("⏸️ Company Name provider DISABLED via config");
}
```

And add toggle to `application-prod.properties`:
```properties
provider.companyname.enabled=${PROVIDER_COMPANYNAME_ENABLED:true}
```

---

**Last Updated**: 2026-07-24  
**Maintainer**: Sharique
