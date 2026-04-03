package io.agentscope.examples.monolithchat.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserCardsAdd {
    private String messageId;
    private List<UserCard> users;
}
