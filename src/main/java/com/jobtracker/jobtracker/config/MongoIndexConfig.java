package com.jobtracker.jobtracker.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.time.Duration;

/**
 * MongoDB index configuration.
 * Creates TTL (Time To Live) index on tailored_resumes collection
 * to automatically delete documents older than 30 days.
 */
@Slf4j
@Configuration
public class MongoIndexConfig {

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void createIndexes() {
        log.info("Creating MongoDB indexes...");

        // Create TTL index on tailored_resumes collection
        // Documents older than 30 days will be automatically deleted
        try {
            IndexOperations indexOps = mongoTemplate.indexOps("tailored_resumes");

            Index ttlIndex = new Index()
                .on("tailoredAt", Sort.Direction.ASC)
                .expire(Duration.ofDays(30))  // Auto-delete after 30 days
                .named("tailoredAt_ttl_30days");

            // Check if index already exists to avoid recreation
            if (!indexOps.getIndexInfo().stream()
                    .anyMatch(info -> "tailoredAt_ttl_30days".equals(info.getName()))) {
                indexOps.ensureIndex(ttlIndex);
                log.info("✅ Created TTL index on tailored_resumes collection (30 days retention)");
                log.info("   → MongoDB will automatically delete resumes older than 30 days");
                log.info("   → Cleanup runs in background every 60 seconds");
            } else {
                log.info("✅ TTL index already exists on tailored_resumes collection");
            }
        } catch (Exception e) {
            log.error("❌ Failed to create TTL index: {}", e.getMessage());
        }
    }
}
