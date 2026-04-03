package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class ChatSendResponse {
    private String messageId;
    private String sessionId;
    private String status;
}
