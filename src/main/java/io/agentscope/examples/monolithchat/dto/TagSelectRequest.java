package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class TagSelectRequest {
    private String sessionId;
    private String userId;
    private String taskId;
    private String tagId;
}
