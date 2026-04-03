package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class StreamToolConfirmData {
    private String type;
    private String confirmationId;
    private String toolId;
    private String toolName;
    private Object toolInput;
    private String triggerMessageId;
    private String message;
}
