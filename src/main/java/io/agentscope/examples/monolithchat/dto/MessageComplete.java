package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class MessageComplete {
    private String messageId;
    private String finishReason;
}
