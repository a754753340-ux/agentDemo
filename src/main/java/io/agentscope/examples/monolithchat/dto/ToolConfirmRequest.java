package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class ToolConfirmRequest {
    private String sessionId;
    private String userId;
    private String confirmationId;
    private Boolean approved;
}
