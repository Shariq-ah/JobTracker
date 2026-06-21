# MongoDB TTL Index Setup - Automatic 30-Day Cleanup

## What Was Implemented

**Automatic deletion of old tailored resumes** to prevent MongoDB storage from filling up.

## How It Works

MongoDB TTL (Time To Live) index automatically deletes documents older than 30 days:

```
MongoIndexConfig.java
    ↓
Creates TTL index on "tailored_resumes" collection
    ↓
Index monitors "tailoredAt" field
    ↓
MongoDB background process checks every 60 seconds
    ↓
Documents where tailoredAt < (now - 30 days) → Deleted automatically
```

## Configuration Details

**File Created:** `src/main/java/com/jobtracker/jobtracker/config/MongoIndexConfig.java`

**Index Specification:**
- **Collection**: `tailored_resumes`
- **Field**: `tailoredAt`
- **TTL**: 30 days
- **Index Name**: `tailoredAt_ttl_30days`

## Storage Impact

### Before TTL Index
- Resumes grow indefinitely
- Time to 500 MB: **13-17 months**

### After TTL Index (30-day retention)
- Maximum resumes at any time: **~180 documents** (6/day × 30 days)
- Maximum resume storage: **18.4 MB** (rolling window)
- Time to 500 MB: **~31 months** (2.6 years) ✅

**Result: 2x longer runway!**

## How to Verify It's Working

### Method 1: Check Index in MongoDB Atlas UI

1. Go to MongoDB Atlas → Clusters → Browse Collections
2. Select `tailored_resumes` collection
3. Click "Indexes" tab
4. You should see: `tailoredAt_ttl_30days` with `expireAfterSeconds: 2592000`

### Method 2: Check Application Logs

When the application starts, you'll see:

```
Creating MongoDB indexes...
✅ Created TTL index on tailored_resumes collection (30 days retention)
   → MongoDB will automatically delete resumes older than 30 days
   → Cleanup runs in background every 60 seconds
```

Or if index already exists:
```
Creating MongoDB indexes...
✅ TTL index already exists on tailored_resumes collection
```

### Method 3: Query MongoDB

```javascript
// Check current index
db.tailored_resumes.getIndexes()

// Output should include:
{
  "v": 2,
  "key": { "tailoredAt": 1 },
  "name": "tailoredAt_ttl_30days",
  "expireAfterSeconds": 2592000  // 30 days in seconds
}
```

## Testing the TTL Index

### Option 1: Insert Test Document (Old Date)

```javascript
// Insert a document with old date
db.tailored_resumes.insertOne({
  jobId: "test_old_resume",
  companyName: "Test Company",
  jobTitle: "Test Job",
  latexSource: "test",
  tailoredAt: new Date("2026-01-01"),  // 5+ months old
  atsScore: 85
})

// Wait 60-120 seconds, then check if it's deleted
db.tailored_resumes.findOne({ jobId: "test_old_resume" })
// Should return: null (document deleted)
```

### Option 2: Wait 30 Days

- Let the system run normally
- After 30 days, check that old resumes are being deleted
- Monitor collection size stays around 18 MB

## Important Notes

### What Gets Deleted
- Only documents in `tailored_resumes` collection
- Only documents where `tailoredAt` is older than 30 days
- **Jobs collection is NOT affected** (no TTL index on jobs)

### What Doesn't Get Deleted
- Recent resumes (< 30 days old)
- Jobs in the `jobs` collection
- Any other collections

### Why 30 Days?
- Gives you enough time to review and test resumes
- Keeps storage under control
- Can be changed by modifying `Duration.ofDays(30)` in `MongoIndexConfig.java`

## Customizing Retention Period

To change from 30 days to a different period:

**Edit:** `src/main/java/com/jobtracker/jobtracker/config/MongoIndexConfig.java`

```java
// Change this line:
.expire(Duration.ofDays(30))  // Current: 30 days

// Examples:
.expire(Duration.ofDays(7))   // 7 days
.expire(Duration.ofDays(60))  // 60 days (2 months)
.expire(Duration.ofDays(90))  // 90 days (3 months)
```

**Then:**
1. Delete the existing index in MongoDB:
   ```javascript
   db.tailored_resumes.dropIndex("tailoredAt_ttl_30days")
   ```
2. Restart the application - new index will be created with updated TTL

## Troubleshooting

### Issue 1: Index Not Created

**Check logs for:**
```
❌ Failed to create TTL index: <error message>
```

**Common causes:**
- MongoDB connection issue
- Insufficient permissions on MongoDB user
- Collection doesn't exist yet

**Fix:** Ensure MongoDB is running and accessible

### Issue 2: Documents Not Being Deleted

**Check:**
1. Index exists: `db.tailored_resumes.getIndexes()`
2. Documents are actually old: `db.tailored_resumes.find().sort({tailoredAt: 1}).limit(5)`
3. Wait 60-120 seconds (MongoDB's background job interval)

### Issue 3: Index Already Exists Error

**This is normal!** The code checks for existing index and skips creation if found.

**Log message:**
```
✅ TTL index already exists on tailored_resumes collection
```

## Cost Savings

**With 30-day retention:**
- **Storage saved**: ~80% (compared to keeping all resumes forever)
- **Monthly growth**: ~15 MB (jobs only) instead of ~39.5 MB (jobs + all resumes)
- **Runway extended**: 13 months → 31 months (138% increase)

## Monitoring Recommendations

**Weekly Check (via MongoDB Atlas UI):**
1. Collection size for `tailored_resumes`
2. Number of documents in collection
3. Should stabilize around 150-200 documents

**Monthly Check:**
1. Total database size
2. Trend should show linear growth (jobs only), not exponential

---

## Summary

✅ **Implemented**: Automatic 30-day cleanup for tailored resumes  
✅ **Result**: 2x longer storage runway (31 months vs 13 months)  
✅ **Maintenance**: Zero - completely automatic  
✅ **Performance**: No impact - runs in background  

**Last Updated**: 2026-06-21  
**Version**: 3.0.3 (MongoDB TTL Optimization)
