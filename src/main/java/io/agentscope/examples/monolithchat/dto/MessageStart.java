package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class MessageStart {
    private String messageId;
    private String role;
}
