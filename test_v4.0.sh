#!/bin/bash

# Test script for v4.0 Iterative ATS Implementation
# This runs Phase 1 testing with mock mode (FREE - no AWS costs)

echo "╔════════════════════════════════════════════════════════════════════════════╗"
echo "║                  v4.0 Iterative ATS - Test Script                          ║"
echo "║                     Phase 1: Mock Mode Testing                             ║"
echo "╚════════════════════════════════════════════════════════════════════════════╝"
echo ""

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "❌ Error: Must run from JobTracker root directory"
    exit 1
fi

# Check compilation
echo "📦 Step 1: Compiling project..."
./mvnw clean compile -q
if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi
echo ""

# Verify mock mode is enabled
echo "🎭 Step 2: Verifying mock mode is enabled..."
MOCK_ENABLED=$(grep "ai.dev.mock.enabled" src/main/resources/application-dev.properties | grep "true")
if [ -n "$MOCK_ENABLED" ]; then
    echo "✅ Mock mode is enabled (FREE - no AWS costs)"
else
    echo "⚠️  Mock mode not found or disabled"
    echo "   Please ensure: ai.dev.mock.enabled=true"
fi
echo ""

# Instructions for running
echo "╔════════════════════════════════════════════════════════════════════════════╗"
echo "║                       🚀 READY TO TEST                                     ║"
echo "╚════════════════════════════════════════════════════════════════════════════╝"
echo ""
echo "To run the application with mock mode:"
echo ""
echo "  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev"
echo ""
echo "What to look for in logs:"
echo ""
echo "✅ Expected (High Score - stops at iteration 1):"
echo "   🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)"
echo "   Iteration 1 - ATS Score: 87"
echo "   ✅ Target ATS score reached: 87 (iterations: 1)"
echo "   Tailored resume saved with ATS score: 87"
echo ""
echo "✅ Expected (Low Score - runs 4 iterations):"
echo "   Iteration 1 - ATS Score: 70"
echo "   ⚠️ ATS score 70 below target (85). Requesting improvement..."
echo "   Iteration 2 - ATS Score: 70"
echo "   ⚠️ ATS score 70 below target (85). Requesting improvement..."
echo "   Iteration 3 - ATS Score: 70"
echo "   ⚠️ ATS score 70 below target (85). Requesting improvement..."
echo "   Iteration 4 - ATS Score: 70"
echo "   ⚠️ Could not reach 85% ATS after 4 iterations. Final score: 70"
echo ""
echo "╔════════════════════════════════════════════════════════════════════════════╗"
echo "║                    📊 TESTING CHECKLIST                                    ║"
echo "╚════════════════════════════════════════════════════════════════════════════╝"
echo ""
echo "Phase 1 - Mock Mode (Current):"
echo "  [ ] Run application with mock mode"
echo "  [ ] Verify logs show iteration behavior"
echo "  [ ] Verify ATS scores are 80-94 range"
echo "  [ ] Verify prompt version is '4.0-iterative-85'"
echo "  [ ] No AWS costs (mock mode)"
echo ""
echo "Phase 2 - Real API Test (After Phase 1):"
echo "  [ ] Set ai.dev.mock.enabled=false"
echo "  [ ] Test with 1-2 real jobs"
echo "  [ ] Monitor AWS CloudWatch for Bedrock calls"
echo "  [ ] Verify iteration counts in logs"
echo "  [ ] Check final ATS scores >= 85"
echo ""
echo "Phase 3 - Data Verification:"
echo "  [ ] Check MongoDB: claudePromptVersion = '4.0-iterative-85'"
echo "  [ ] Verify ATS scores in database"
echo "  [ ] Check Telegram notifications have correct data"
echo "  [ ] Verify PDF attachments work"
echo ""
echo "╔════════════════════════════════════════════════════════════════════════════╗"
echo "║                   🎯 READY TO START?                                       ║"
echo "╚════════════════════════════════════════════════════════════════════════════╝"
echo ""
echo "Run: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev"
echo ""
