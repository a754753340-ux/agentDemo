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
            return appendToolRules(basePrompt);
        }
        Map<String, String> tags = properties.getUserTags().getOrDefault(normalizeUserId(userId), Map.of());
        if (tags.isEmpty()) {
            return appendToolRules(basePrompt);
        }
        String tagInstructions =
                tags.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(entry -> "- " + normalize(entry.getKey()) + "：" + normalize(entry.getValue()))
                        .collect(Collectors.joining("\n"));
        return appendToolRules(basePrompt
                + "\n"
                + "请严格遵循以下用户个性标签进行回答："
                + "\n"
                + tagInstructions
                + "\n"
                + "在满足上述个性化要求时，仍需保证内容真实、合规且有帮助。");
    }

    private String appendToolRules(String prompt) {
        return prompt
                + "当工具返回结构化数据时：\n"
                + "1. 不要改写数据\n"
                + "2. 不要转成自然语言\n"
                + "3. 直接返回原始JSON\n"
                + "当用户要求推荐人数时（如“推荐3个用户/来10个”），调用 getRecommend 工具时必须携带 num=N，范围限制为 1~20，超出则裁剪。\n"
                + "当用户要求打电话给某人时，调用 call 工具时必须携带 targetUserId=想通话的人信息\n"
                + "当用户有找对象或推荐诉求但偏好不完整时，先调用 getRecommendTags 工具让用户选择标签。\n"
                + "【重要】当你调用了 getRecommendTags 工具并获得返回结果后，绝对不要再生成任何自然语言或文本分析，必须立刻结束本回合的回答！";
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
