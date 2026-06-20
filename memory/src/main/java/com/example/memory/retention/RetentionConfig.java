package com.example.memory.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.memory.retention")
public record RetentionConfig(
    Duration episodicDefault,
    Duration cacheCleanupInterval
) {
    public RetentionConfig {
        if (episodicDefault == null) episodicDefault = Duration.ofDays(365);
        if (cacheCleanupInterval == null) cacheCleanupInterval = Duration.ofHours(1);
    }
}
