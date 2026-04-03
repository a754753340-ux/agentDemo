package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class TextDelta {
    private String messageId;
    private String text;
    private String format;
}
