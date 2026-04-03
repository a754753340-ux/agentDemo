package io.agentscope.examples.monolithchat.rsp;

import lombok.Data;

@Data
public class UserRecommendRsp<T> {

    private String type;

    private T data;
}
