package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class RecommendTagItem {
    private String id;
    private String label;
    private String promptHint;
}
