package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

import java.util.List;

@Data
public class StreamBlockData {
    private String type;
    private Object data;
    private String message;
    private List<ImageUrlItem> images;
}
