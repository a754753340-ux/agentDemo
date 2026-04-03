package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

import java.util.List;

@Data
public class StreamTagListData {
    private String type;
    private String taskId;
    private String title;
    private String message;
    private List<RecommendTagItem> tags;
    private String triggerMessageId;
}
