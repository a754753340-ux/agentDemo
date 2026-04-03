package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class StreamDeltaData {
    private String type;
    private String delta;
}
