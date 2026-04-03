package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class ErrorEvent {
    private String messageId;
    private String code;
    private String message;
}
