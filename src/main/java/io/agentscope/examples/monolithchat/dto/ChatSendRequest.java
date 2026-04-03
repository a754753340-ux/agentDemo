package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatSendRequest {
    private String sessionId;
    private String userId;
    private List<ChatSendContentBlock> content;
}
