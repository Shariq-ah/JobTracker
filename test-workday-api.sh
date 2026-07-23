#!/bin/bash

# Workday API Tester Script
# Usage: ./test-workday-api.sh <company> <url> <location_facet> <time_facet> <job_family_facet>
#
# Example:
# ./test-workday-api.sh google \
#   "https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs" \
#   "bc33aa3152ec42d4995f4791a106ed09" \
#   "5e7e18fb01f5449bb3fdb291f5a34798" \
#   "0c40f6bd1d8f10ae43ffaefd46dc7e78"

set -e

COMPANY=$1
URL=$2
LOCATION_FACET=$3
TIME_FACET=$4
JOB_FAMILY_FACET=$5

if [ -z "$COMPANY" ] || [ -z "$URL" ]; then
    echo "Usage: $0 <company> <url> [location_facet] [time_facet] [job_family_facet]"
    echo ""
    echo "Example:"
    echo "  $0 google https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs"
    echo ""
    echo "Or with facet IDs:"
    echo "  $0 google https://google.wd5.myworkdayjobs.com/wday/cxs/google/Careers/jobs \\"
    echo "    bc33aa3152ec42d4995f4791a106ed09 \\"
    echo "    5e7e18fb01f5449bb3fdb291f5a34798 \\"
    echo "    0c40f6bd1d8f10ae43ffaefd46dc7e78"
    exit 1
fi

echo "============================================"
echo "Testing Workday API for: $COMPANY"
echo "URL: $URL"
echo "============================================"
echo ""

# Build request payload
if [ -z "$LOCATION_FACET" ]; then
    # Test without facets (search only)
    echo "Testing with search term only (no filters)..."
    PAYLOAD='{
  "limit": 5,
  "offset": 0,
  "searchText": "Java"
}'
else
    # Test with facets
    echo "Testing with facets..."
    echo "  Location (India): $LOCATION_FACET"
    echo "  Time Type (Full-time): $TIME_FACET"
    echo "  Job Family: $JOB_FAMILY_FACET"
    PAYLOAD="{
  \"appliedFacets\": {
    \"locationCountry\": [\"$LOCATION_FACET\"],
    \"timeType\": [\"$TIME_FACET\"],
    \"jobFamily\": [\"$JOB_FAMILY_FACET\"]
  },
  \"limit\": 5,
  \"offset\": 0,
  \"searchText\": \"Java\"
}"
fi

echo ""
echo "Request Payload:"
echo "$PAYLOAD" | jq . 2>/dev/null || echo "$PAYLOAD"
echo ""
echo "============================================"
echo "Sending request..."
echo "============================================"
echo ""

# Make API call
RESPONSE=$(curl -s -X POST "$URL" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" \
  -d "$PAYLOAD")

# Check if response is valid JSON
if ! echo "$RESPONSE" | jq . > /dev/null 2>&1; then
    echo "❌ ERROR: Invalid JSON response or API call failed"
    echo ""
    echo "Raw response:"
    echo "$RESPONSE"
    exit 1
fi

# Parse response
TOTAL=$(echo "$RESPONSE" | jq -r '.total // 0')
JOB_COUNT=$(echo "$RESPONSE" | jq -r '.jobPostings | length')

echo "✅ Success!"
echo ""
echo "============================================"
echo "Results Summary"
echo "============================================"
echo "Total jobs available: $TOTAL"
echo "Jobs returned: $JOB_COUNT"
echo ""

if [ "$JOB_COUNT" -gt 0 ]; then
    echo "============================================"
    echo "Sample Jobs"
    echo "============================================"
    echo ""

    echo "$RESPONSE" | jq -r '.jobPostings[] |
        "📍 Title: \(.title)\n" +
        "   Location: \(.locationsText)\n" +
        "   Posted: \(.postedOn)\n" +
        "   External Path: \(.externalPath)\n" +
        "   Reference ID: \(.bulletFields[0] // \"N/A\")\n"'

    echo ""
    echo "============================================"
    echo "Configuration for ProviderRegistry.java"
    echo "============================================"
    echo ""

    # Extract base JD URL (remove /jobs from end)
    JD_URL="${URL%/jobs}"

    # Generate Java code
    cat <<EOF
// ─── ${COMPANY^} ─────────────────────────────────────────────────
private JobProvider ${COMPANY}() {
    return new DynamicJobProvider(
            JobProviderConfig.builder()
                    .companyName("${COMPANY^}")
                    .platform(Platform.WORKDAY)
                    .listUrl("$URL")
                    .jdUrl("$JD_URL")
                    .keyword("Java")
                    .limit(20)
                    .lookbackDays(2)
EOF

    if [ ! -z "$LOCATION_FACET" ]; then
        cat <<EOF
                    // Workday facet IDs
                    .locationCountryId("$LOCATION_FACET")      // India
                    .timeTypeId("$TIME_FACET")                 // Full-time
                    .jobFamilyId("$JOB_FAMILY_FACET")          // Software Engineering
EOF
    fi

    cat <<EOF
                    .jdFetchDelayMs(500)
                    .jdFetchThreads(2)
                    .build(),
            oracleHcmHandler, microsoftHandler, barclaysHandler, goldmanHandler, workdayHandler, amazonHandler);
}

// Add to constructor:
@Value("\${provider.${COMPANY}.enabled:true}")
private boolean ${COMPANY}Enabled;

// Add to jobProviders() method:
if (${COMPANY}Enabled) {
    providers.add(${COMPANY}());
} else {
    log.warn("⏸️ ${COMPANY^} provider DISABLED via config");
}
EOF

    echo ""
    echo "============================================"
    echo "application-prod.properties"
    echo "============================================"
    echo ""
    echo "provider.${COMPANY}.enabled=\${PROVIDER_${COMPANY^^}_ENABLED:true}"

else
    echo "⚠️  No jobs found. This could mean:"
    echo "  1. No Java jobs in India for this company right now"
    echo "  2. Wrong facet IDs (if you provided them)"
    echo "  3. Company doesn't use this Workday site for India jobs"
    echo ""
    echo "Try:"
    echo "  • Run without facet IDs to see all jobs"
    echo "  • Check the company careers page manually"
    echo "  • Verify facet IDs in browser DevTools"
fi

echo ""
echo "============================================"
echo "Full API Response (for debugging)"
echo "============================================"
echo ""
echo "$RESPONSE" | jq .

echo ""
echo "============================================"
echo "Next Steps"
echo "============================================"
echo "1. If facet IDs are missing, find them using browser DevTools"
echo "2. Copy the Java code above to ProviderRegistry.java"
echo "3. Add the property to application-prod.properties"
echo "4. Test locally with: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev"
echo "5. Monitor logs for: 'Fetching from: ${COMPANY^}'"
echo ""
