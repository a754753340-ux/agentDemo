package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

import java.util.List;

@Data
public class ImagesAdd {
    private String messageId;
    private List<ImageItem> images;
}
