# Quick Start - Zero Cost Testing

## 🚀 Start Testing (FREE)

```bash
# Enable dev mode (ZERO cost)
export AI_DEV_MOCK_ENABLED=true

# Run application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## ✅ Verify Dev Mode Active

Look for these in logs:
```
🎭 DEV MODE: Using mock response (FREE - no API call)
🎭 DEV MODE: Using mock resume tailoring (FREE - no API call)
```

## 💰 Cost Comparison

| Mode | Cost per Run | 10 Runs/Day | Monthly |
|------|--------------|-------------|---------|
| **Production** | $0.04 | $0.40 | **$12** |
| **Dev Mock** | $0.00 | $0.00 | **$0** ✅ |

## 🔄 Switch to Production Mode

```bash
# Disable dev mode (use real API)
export AI_DEV_MOCK_ENABLED=false

# Or unset the variable
unset AI_DEV_MOCK_ENABLED
```

## 📖 Full Documentation

See [DEV_MODE_GUIDE.md](DEV_MODE_GUIDE.md) for complete details.

---

**Remember**: Always use dev mode for local testing to save money! 💸
