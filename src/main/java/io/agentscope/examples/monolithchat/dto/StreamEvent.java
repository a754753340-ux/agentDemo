package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class StreamEvent<T> {
    private String id;
    private String type;
    private boolean delta;
    private String sessionId;
    private String userId;
    private Long ts;
    private T data;
}
