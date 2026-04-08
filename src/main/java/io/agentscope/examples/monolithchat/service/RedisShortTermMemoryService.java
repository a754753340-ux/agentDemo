package io.agentscope.examples.monolithchat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RedisShortTermMemoryService {

    private static final int MAX_CONTEXT_ITEMS = 40;
    private static final Duration CONTEXT_TTL = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisShortTermMemoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void appendUserMessage(String sessionId, String userId, String messageId, String content) {
        append(sessionId, userId, "user", messageId, content);
    }

    public void appendAssistantMessage(String sessionId, String userId, String messageId, String content) {
        append(sessionId, userId, "assistant", messageId, content);
    }

    public List<String> loadRecentTextContext(String sessionId, String userId, int limit) {
        String key = contextKey(sessionId, userId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size <= 0) {
            return List.of();
        }
        long end = size - 1;
        long start = Math.max(0, end - Math.max(1, limit) + 1);
        List<String> range = redisTemplate.opsForList().range(key, start, end);
        if (range == null || range.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String item : range) {
            if (item == null || item.isBlank()) {
                continue;
            }
            try {
                MemoryLine line = objectMapper.readValue(item, MemoryLine.class);
                if (line.getContent() == null || line.getContent().isBlank()) {
                    continue;
                }
                lines.add(line.getRole() + "：" + line.getContent());
            } catch (Exception ignored) {
            }
        }
        return lines;
    }

    private void append(String sessionId, String userId, String role, String messageId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        String key = contextKey(sessionId, userId);
        MemoryLine line = new MemoryLine();
        line.setRole(role);
        line.setMessageId(messageId);
        line.setContent(content.strip());
        line.setTs(LocalDateTime.now().toString());
        try {
            String json = objectMapper.writeValueAsString(line);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MAX_CONTEXT_ITEMS, -1);
            redisTemplate.expire(key, CONTEXT_TTL);
        } catch (Exception ignored) {
        }
    }

    private String contextKey(String sessionId, String userId) {
        return "stm:ctx:" + safe(sessionId) + ":" + safe(userId);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.strip();
    }

    public static class MemoryLine {
        private String role;
        private String messageId;
        private String content;
        private String ts;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getTs() {
            return ts;
        }

        public void setTs(String ts) {
            this.ts = ts;
        }
    }
}
