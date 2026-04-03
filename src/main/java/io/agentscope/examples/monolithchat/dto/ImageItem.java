package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class ImageItem {
    private String id;
    private String url;
    private String thumbUrl;
    private String alt;
    private Integer width;
    private Integer height;
}
