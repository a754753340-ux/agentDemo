package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

@Data
public class UserCard {
    private String userId;
    private String nickname;
    private String avatar;
    private Integer gender;
    private Integer diamond;
}
