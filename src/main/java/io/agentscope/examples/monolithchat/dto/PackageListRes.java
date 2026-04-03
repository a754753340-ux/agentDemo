package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

import java.util.List;

@Data
public class PackageListRes {
    private List<ProductRes> productsList;
    private Integer myDiamonds;
    private String title;
}
