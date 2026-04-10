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

        return appendToolRules(basePrompt
                + "\n"
                + "请严格遵循以下用户个性标签进行回答："
                + "\n"
                + "在满足上述个性化要求时，仍需保证内容真实、合规且有帮助。");
    }

    private String appendToolRules(String prompt) {
        return prompt
                + "当工具返回结构化数据时：\n"
                + "1. 不要改写数据\n"
                + "2. 不要转成自然语言\n"
                + "3. 直接返回原始JSON\n"
                + "当你准备调用任何工具并返回结构化结果前，先输出1句简短自然语言过渡文案（20~40字，口语化，不含JSON），再输出工具结果。\n"
                + "当用户要求推荐人数时（如“推荐3个用户/来10个”），调用 getRecommend 工具时必须携带 num=N，范围限制为 1~20，超出则裁剪。\n"
                + "当用户已明确表达偏好（如“我喜欢性感/温柔/同城”），禁止调用 getRecommendTags，必须直接调用 getRecommend。\n"
                + "当用户要求打电话给某人时，调用 call 工具时必须携带 targetUser=想通话的人信息\n"
                + "当用户有找对象或推荐诉求但偏好不完整时，先调用 getRecommendTags 工具让用户选择标签。\n"
                + "【重要】当你调用了 getRecommendTags 工具并获得返回结果后，绝对不要再生成任何自然语言或文本分析，必须立刻结束本回合的回答！";
//                +"【记忆使用规则】\n" +
//                " 1. 只使用与当前问题直接相关的记忆\n" +
//                " 2. 不要主动提及用户未提及的敏感信息\n" +
//                " 3. 如果不确定是否相关，请不要使用" +
//                " 4. 不要用历史记忆来调用任何工具";

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
