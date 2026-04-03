package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

import java.util.List;

@Data
public class StreamBlockData {
    private String type;
    private List<ImageUrlItem> images;
}
