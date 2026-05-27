# Security Cleanup Guide

## ⚠️ IMPORTANT: Your AWS credentials were exposed in Git history

Your `application-dev.properties` file with AWS credentials has been committed to Git. We need to:
1. Remove secrets from Git history
2. Rotate your AWS credentials
3. Push the clean version

## Step 1: Rotate Your AWS Credentials (CRITICAL)

**Since your AWS credentials are already in Git history, they are compromised. You MUST rotate them immediately:**

1. Go to AWS Console: https://console.aws.amazon.com/iam/
2. Navigate to: IAM → Users → Your User → Security Credentials
3. Click "Create access key" for a new key
4. **Delete the old access key**: `AKIATEVS5PLOXASI5NON`
5. Update `.env.local` with the new credentials

## Step 2: Clean Git History (DESTRUCTIVE - Will rewrite history)

⚠️ **WARNING**: This will rewrite Git history. If others are working on this repo, coordinate with them first!

```bash
# Option A: Remove file from all commits (safest for shared repos)
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch src/main/resources/application-dev.properties" \
  --prune-empty --tag-name-filter cat -- --all

# Option B: Use BFG Repo-Cleaner (faster, recommended)
# Download BFG: https://rtyley.github.io/bfg-repo-cleaner/
java -jar bfg.jar --delete-files application-dev.properties

# After cleaning, force push
git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

## Step 3: Commit Secure Version

```bash
# Stage the secure changes
git add .gitignore
git add src/main/resources/application-dev.properties
git add .env.example
git add README.md

# Commit with clear message
git commit -m "Security: Remove hardcoded credentials, use environment variables

- Replaced hardcoded AWS credentials with environment variables
- Replaced hardcoded Telegram credentials with environment variables
- Added .env.local to .gitignore for local development
- Added .env.example as template
- Updated README.md with deployment instructions

BREAKING CHANGE: Requires environment variables to be set for local development
See .env.example for required variables"
```

## Step 4: Force Push (if you cleaned history)

```bash
# Force push to overwrite remote history
git push origin main --force

# If you have other branches, clean them too
git push origin --force --all
```

## Step 5: Update .env.local with New AWS Credentials

```bash
# Edit .env.local with new credentials
nano .env.local

# Load them for local development
export $(cat .env.local | xargs)

# Test the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Alternative: Start Fresh (Easier but loses history)

If this is a personal project and you don't care about Git history:

```bash
# 1. Remove .git directory
rm -rf .git

# 2. Initialize new repo
git init
git add .
git commit -m "Initial commit with secure configuration"

# 3. Force push to remote
git remote add origin <your-remote-url>
git push -u origin main --force
```

## For Render.com Deployment

After pushing, configure environment variables in Render.com dashboard:

1. Go to your service → Environment
2. Add the following environment variables:
   ```
   MONGO_URI=<your_mongodb_atlas_uri>
   TELEGRAM_BOT_TOKEN=<your_bot_token>
   TELEGRAM_CHAT_ID=<your_chat_id>
   AWS_BEDROCK_REGION=us-east-1
   AWS_ACCESS_KEY_ID=<NEW_AWS_ACCESS_KEY>
   AWS_SECRET_ACCESS_KEY=<NEW_AWS_SECRET_KEY>
   AWS_BEDROCK_MODEL_ID=us.anthropic.claude-haiku-4-5-20251001-v1:0
   ```

## Verification Checklist

- [ ] AWS credentials rotated (old key deleted)
- [ ] Git history cleaned (no secrets in `git log -p`)
- [ ] .env.local added to .gitignore
- [ ] .env.local contains actual credentials (gitignored)
- [ ] .env.example contains no secrets (safe to commit)
- [ ] application-dev.properties uses environment variables
- [ ] Changes committed and pushed
- [ ] Render.com environment variables configured
- [ ] Application tested locally with new credentials
- [ ] Application tested on Render.com

## Security Best Practices Going Forward

1. **Never commit credentials** to Git
2. **Use environment variables** for all secrets
3. **Use .env.local** for local development (gitignored)
4. **Use .env.example** as template (safe to commit)
5. **Rotate credentials** immediately if exposed
6. **Use AWS Secrets Manager** for production (optional)
7. **Enable AWS CloudTrail** to monitor API usage
8. **Set up AWS billing alerts** to detect unauthorized usage

## GitHub Security Features

If pushing to GitHub, enable:
1. **Secret scanning**: Settings → Security → Secret scanning
2. **Dependabot alerts**: Settings → Security → Dependabot
3. **Branch protection**: Settings → Branches → Add rule

---

**Status**: ⚠️ URGENT - Complete steps 1-5 before pushing to remote