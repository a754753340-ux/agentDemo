package io.agentscope.examples.monolithchat.service;

import io.agentscope.examples.monolithchat.dto.RecommendTagItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RecommendTagTaskService {

    public static class TaskState {
        private final String taskId;
        private final String sessionId;
        private final String userId;
        private final String triggerMessageId;
        private final String title;
        private final List<RecommendTagItem> tags;
        private String selectedTagId;
        private boolean consumed;
        private final Instant createdAt;

        public TaskState(String taskId, String sessionId, String userId, String triggerMessageId, String title, List<RecommendTagItem> tags) {
            this.taskId = taskId;
            this.sessionId = sessionId;
            this.userId = userId;
            this.triggerMessageId = triggerMessageId;
            this.title = title;
            this.tags = tags;
            this.createdAt = Instant.now();
        }

        public String getTaskId() {
            return taskId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUserId() {
            return userId;
        }

        public String getTriggerMessageId() {
            return triggerMessageId;
        }

        public String getTitle() {
            return title;
        }

        public List<RecommendTagItem> getTags() {
            return tags;
        }

        public String getSelectedTagId() {
            return selectedTagId;
        }

        public void setSelectedTagId(String selectedTagId) {
            this.selectedTagId = selectedTagId;
        }

        public boolean isConsumed() {
            return consumed;
        }

        public void setConsumed(boolean consumed) {
            this.consumed = consumed;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    private final Map<String, TaskState> byTaskId = new ConcurrentHashMap<>();
    private final Map<String, String> latestByTrigger = new ConcurrentHashMap<>();

    public TaskState createTask(String sessionId, String userId, String triggerMessageId, String title, List<RecommendTagItem> tags) {
        String taskId = UUID.randomUUID().toString();
        List<RecommendTagItem> normalized = tags == null ? List.of() : tags.stream().filter(t -> t != null && t.getId() != null && !t.getId().isBlank()).collect(Collectors.toList());
        TaskState state = new TaskState(taskId, sessionId, userId, triggerMessageId, title, normalized);
        byTaskId.put(taskId, state);
        latestByTrigger.put(triggerKey(sessionId, userId, triggerMessageId), taskId);
        return state;
    }

    public TaskState getByTaskId(String taskId) {
        return byTaskId.get(taskId);
    }

    public TaskState getLatestByTrigger(String sessionId, String userId, String triggerMessageId) {
        String taskId = latestByTrigger.get(triggerKey(sessionId, userId, triggerMessageId));
        if (taskId == null) {
            return null;
        }
        return byTaskId.get(taskId);
    }

    public boolean selectTag(String taskId, String sessionId, String userId, String tagId) {
        TaskState state = byTaskId.get(taskId);
        if (state == null) {
            return false;
        }
        if (!state.getSessionId().equals(sessionId) || !state.getUserId().equals(userId)) {
            return false;
        }
        boolean exists = state.getTags().stream().anyMatch(t -> t.getId().equals(tagId));
        if (!exists) {
            return false;
        }
        state.setSelectedTagId(tagId);
        state.setConsumed(false);
        return true;
    }

    public RecommendTagItem consumeSelectedTag(String sessionId, String userId, String triggerMessageId) {
        TaskState state = getLatestByTrigger(sessionId, userId, triggerMessageId);
        if (state == null || state.getSelectedTagId() == null || state.getSelectedTagId().isBlank() || state.isConsumed()) {
            return null;
        }
        RecommendTagItem selected = state.getTags().stream()
                .filter(t -> state.getSelectedTagId().equals(t.getId()))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return null;
        }
        state.setConsumed(true);
        return selected;
    }

    private String triggerKey(String sessionId, String userId, String triggerMessageId) {
        return sessionId + ":" + userId + ":" + triggerMessageId;
    }
}
