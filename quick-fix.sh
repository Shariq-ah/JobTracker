#!/bin/bash

echo "================================================"
echo "JobTracker Security Cleanup - Quick Method"
echo "================================================"
echo ""
echo "⚠️  WARNING: This will rewrite Git history!"
echo "⚠️  Make sure to rotate your AWS credentials first!"
echo ""
read -p "Have you rotated your AWS credentials? (yes/no): " rotated

if [ "$rotated" != "yes" ]; then
    echo "❌ Please rotate AWS credentials first:"
    echo "   1. Go to: https://console.aws.amazon.com/iam/"
    echo "   2. Delete old key: AKIATEVS5PLOXASI5NON"
    echo "   3. Create new key and update .env.local"
    exit 1
fi

echo ""
echo "Starting cleanup..."
echo ""

# Stage the secure files
echo "📝 Staging secure configuration files..."
git add .gitignore
git add src/main/resources/application-dev.properties
git add .env.example
git add README.md
git add SECURITY_CLEANUP.md

# Check if .env.local exists and is not staged
if [ -f .env.local ]; then
    echo "✅ .env.local exists (will not be committed)"
else
    echo "⚠️  Warning: .env.local not found. Run: cp .env.example .env.local"
fi

# Commit the secure version
echo "💾 Committing secure configuration..."
git commit -m "Security: Remove hardcoded credentials, use environment variables

- Replaced hardcoded AWS credentials with environment variables
- Replaced hardcoded Telegram credentials with environment variables  
- Added .env.local to .gitignore for local development
- Added .env.example as template
- Updated README.md with deployment instructions
- Added SECURITY_CLEANUP.md guide

BREAKING CHANGE: Requires environment variables to be set
See .env.example for required variables
See SECURITY_CLEANUP.md for migration steps"

echo ""
echo "✅ Secure configuration committed!"
echo ""
echo "Next steps:"
echo "1. Review changes: git log -1 -p"
echo "2. Push to remote: git push origin main"
echo ""
echo "For Render.com:"
echo "1. Add environment variables in Render dashboard"
echo "2. Redeploy the service"
echo ""
echo "⚠️  Note: Old commits still contain secrets in Git history"
echo "   See SECURITY_CLEANUP.md for instructions to clean history"
echo ""
