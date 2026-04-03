package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class ProductRes {
    private String prodId;
    private String packageId;
    private String currencySymbol;
    private Integer price;
    private Integer presentedAmount;
    private Integer amount;
    private String iosItemId;
    private Integer presentType;
    private String labelText;
    private String usdPrice;
}
