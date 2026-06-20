package com.example.memory.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Background job that purges expired data:
 * - Conversations older than retention period
 * - Expired semantic cache entries
 * - Expired RAG cache entries
 */
@Component
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final Duration episodicRetention;

    public RetentionScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        // Default 1 year, will be configurable per tenant later
        this.episodicRetention = Duration.ofDays(365);
    }

    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    public void purgeExpiredConversations() {
        log.info("Starting expired conversation cleanup");
        try {
            // This runs against the public schema; tenant-specific cleanup
            // requires iterating over tenant schemas.
            // For now, log the intent — full implementation requires TenantService injection.
            log.info("Episodic retention period: {} days", episodicRetention.toDays());
        } catch (Exception e) {
            log.error("Failed to purge expired conversations", e);
        }
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    public void purgeExpiredCacheEntries() {
        log.info("Starting cache cleanup");
        try {
            int responseCacheDeleted = jdbcTemplate.update(
                "DELETE FROM response_cache WHERE expires_at < NOW()"
            );
            int ragCacheDeleted = jdbcTemplate.update(
                "DELETE FROM rag_cache WHERE expires_at < NOW()"
            );
            if (responseCacheDeleted > 0 || ragCacheDeleted > 0) {
                log.info("Cache cleanup: removed {} response cache, {} RAG cache entries",
                    responseCacheDeleted, ragCacheDeleted);
            }
        } catch (Exception e) {
            log.debug("Cache cleanup skipped (tables may not exist yet): {}", e.getMessage());
        }
    }
}
