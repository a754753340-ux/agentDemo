package io.agentscope.examples.monolithchat.service;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromptComposer {

    private final PromptPersonalizationProperties properties;

    public PromptComposer(PromptPersonalizationProperties properties) {
        this.properties = properties;
    }

    public String compose(String userId) {
        String basePrompt = normalize(properties.getBaseSystemPrompt());
        if (!properties.isEnabled()) {
            return basePrompt;
        }
        Map<String, String> tags = properties.getUserTags().getOrDefault(normalizeUserId(userId), Map.of());
        if (tags.isEmpty()) {
            return basePrompt;
        }
        String tagInstructions =
                tags.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(entry -> "- " + normalize(entry.getKey()) + "：" + normalize(entry.getValue()))
                        .collect(Collectors.joining("\n"));
        return basePrompt
                + "\n"
                + "请严格遵循以下用户个性标签进行回答："
                + "\n"
                + tagInstructions
                + "\n"
                + "在满足上述个性化要求时，仍需保证内容真实、合规且有帮助。"
                + "当工具返回结构化数据时：\n" +
                "1. 不要改写数据\n" +
                "2. 不要转成自然语言\n" +
                "3. 直接返回原始JSON";
    }

    private String normalizeUserId(String userId) {
        return userId == null ? "" : userId.strip();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.strip();
        if (trimmed.length() > 200) {
            return trimmed.substring(0, 200);
        }
        return trimmed;
    }
}
