package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class ToolConfirmResponse {
    private String status;
    private String triggerMessageId;
    private String toolName;
}
