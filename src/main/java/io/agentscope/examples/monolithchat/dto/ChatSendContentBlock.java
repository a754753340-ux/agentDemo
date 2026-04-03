package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatSendContentBlock {
    private String type;
    private String text;
    private List<ImageUrlItem> images;
}
