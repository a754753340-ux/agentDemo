package io.agentscope.examples.monolithchat.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "chat.tool-policy.recommend")
public class RecommendToolPolicyProperties {
    private List<String> intentPatterns = new ArrayList<>(List.of("推荐", "找对象", "匹配", "来\\d+个", "给我推荐"));
    private Map<String, List<String>> preferencePatterns = new LinkedHashMap<>();
    private int minPreferenceSignals = 1;
    private int fallbackRecommendCount = 5;
    private String directRecommendText = "好的，我已根据你的偏好直接为你推荐：";
    private String rejectRecommendText = "如果你需要推荐，我可以根据你的偏好马上帮你筛选。";

    public RecommendToolPolicyProperties() {
        preferencePatterns.put("appearance", List.of("性感", "清纯", "甜美", "御姐", "可爱", "漂亮", "高颜值"));
        preferencePatterns.put("personality", List.of("温柔", "活泼", "开朗", "高冷", "幽默", "成熟"));
        preferencePatterns.put("interest", List.of("健身", "旅行", "二次元", "游戏", "读书", "音乐", "电影"));
        preferencePatterns.put("scene", List.of("同城", "附近", "白天", "夜晚"));
    }

    public List<String> getIntentPatterns() {
        return intentPatterns;
    }

    public void setIntentPatterns(List<String> intentPatterns) {
        this.intentPatterns = intentPatterns;
    }

    public Map<String, List<String>> getPreferencePatterns() {
        return preferencePatterns;
    }

    public void setPreferencePatterns(Map<String, List<String>> preferencePatterns) {
        this.preferencePatterns = preferencePatterns;
    }

    public int getMinPreferenceSignals() {
        return minPreferenceSignals;
    }

    public void setMinPreferenceSignals(int minPreferenceSignals) {
        this.minPreferenceSignals = minPreferenceSignals;
    }

    public int getFallbackRecommendCount() {
        return fallbackRecommendCount;
    }

    public void setFallbackRecommendCount(int fallbackRecommendCount) {
        this.fallbackRecommendCount = fallbackRecommendCount;
    }

    public String getDirectRecommendText() {
        return directRecommendText;
    }

    public void setDirectRecommendText(String directRecommendText) {
        this.directRecommendText = directRecommendText;
    }

    public String getRejectRecommendText() {
        return rejectRecommendText;
    }

    public void setRejectRecommendText(String rejectRecommendText) {
        this.rejectRecommendText = rejectRecommendText;
    }
}
