package io.agentscope.examples.monolithchat.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "chat.personalization")
public class PromptPersonalizationProperties {

    private boolean enabled = true;
    private String baseSystemPrompt = "你是一个聊天机器人";
    private Map<String, Map<String, String>> userTags = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseSystemPrompt() {
        return baseSystemPrompt;
    }

    public void setBaseSystemPrompt(String baseSystemPrompt) {
        this.baseSystemPrompt = baseSystemPrompt;
    }

    public Map<String, Map<String, String>> getUserTags() {
        return userTags;
    }

    public void setUserTags(Map<String, Map<String, String>> userTags) {
        this.userTags = userTags;
    }
}
