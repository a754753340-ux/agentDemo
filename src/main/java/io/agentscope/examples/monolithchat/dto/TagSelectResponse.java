package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class TagSelectResponse {
    private String status;
    private String taskId;
    private String tagId;
    private String triggerMessageId;
}
