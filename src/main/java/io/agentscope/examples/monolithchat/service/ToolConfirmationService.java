package io.agentscope.examples.monolithchat.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ToolConfirmationService {

    public static class PendingTool {
        private final String confirmationId;
        private final String sessionId;
        private final String userId;
        private final String toolName;
        private final String triggerMessageId;
        private final Instant createdAt;

        public PendingTool(String confirmationId, String sessionId, String userId, String toolName, String triggerMessageId) {
            this.confirmationId = confirmationId;
            this.sessionId = sessionId;
            this.userId = userId;
            this.toolName = toolName;
            this.triggerMessageId = triggerMessageId;
            this.createdAt = Instant.now();
        }

        public String getConfirmationId() {
            return confirmationId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUserId() {
            return userId;
        }

        public String getToolName() {
            return toolName;
        }

        public String getTriggerMessageId() {
            return triggerMessageId;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    private final Map<String, PendingTool> pendingById = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> approvedToolNamesBySession = new ConcurrentHashMap<>();

    public String createPending(String sessionId, String userId, String toolName, String triggerMessageId) {
        String id = UUID.randomUUID().toString();
        pendingById.put(id, new PendingTool(id, sessionId, userId, toolName, triggerMessageId));
        return id;
    }

    public PendingTool getPending(String confirmationId) {
        return pendingById.get(confirmationId);
    }

    public void approve(String confirmationId) {
        PendingTool pending = pendingById.get(confirmationId);
        if (pending == null) {
            return;
        }
        approvedToolNamesBySession
                .computeIfAbsent(sessionKey(pending.sessionId, pending.userId), k -> ConcurrentHashMap.newKeySet())
                .add(pending.toolName);
    }

    public void deny(String confirmationId) {
        pendingById.remove(confirmationId);
    }

    public boolean consumeApproval(String sessionId, String userId, String toolName) {
        Set<String> set = approvedToolNamesBySession.get(sessionKey(sessionId, userId));
        if (set == null) {
            return false;
        }
        return set.remove(toolName);
    }

    private String sessionKey(String sessionId, String userId) {
        return sessionId + ":" + userId;
    }
}
