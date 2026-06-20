package com.example.memory.episodic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed session cache for active conversation context.
 * Stores the last N messages for quick retrieval without DB round-trips.
 */
@Component
public class ConversationSessionCache {

    private static final Logger log = LoggerFactory.getLogger(ConversationSessionCache.class);
    private static final String KEY_PREFIX = "conv:session:";
    private static final int MAX_CACHED_MESSAGES = 20;
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ConversationSessionCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void cacheMessage(UUID conversationId, CachedMessage message) {
        String key = KEY_PREFIX + conversationId;
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MAX_CACHED_MESSAGES, -1);
            redisTemplate.expire(key, SESSION_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache message for conversation {}", conversationId, e);
        }
    }

    public List<CachedMessage> getSessionMessages(UUID conversationId) {
        String key = KEY_PREFIX + conversationId;
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            return List.of();
        }

        List<CachedMessage> messages = new ArrayList<>(jsonList.size());
        for (String json : jsonList) {
            try {
                messages.add(objectMapper.readValue(json, CachedMessage.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached message", e);
            }
        }
        return messages;
    }

    public void evict(UUID conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    public record CachedMessage(String role, String content, long timestamp) {}
}
