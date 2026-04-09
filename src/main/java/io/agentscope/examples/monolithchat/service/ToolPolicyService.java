package io.agentscope.examples.monolithchat.service;

import io.agentscope.examples.monolithchat.conf.RecommendToolPolicyProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ToolPolicyService {
    private final RecommendToolPolicyProperties recommendProperties;

    public ToolPolicyService(RecommendToolPolicyProperties recommendProperties) {
        this.recommendProperties = recommendProperties;
    }

    public RecommendDecision analyzeRecommendIntent(String userText) {
        String text = userText == null ? "" : userText;
        boolean hasRecommendIntent = matchesAny(text, recommendProperties.getIntentPatterns());
        Set<String> signals = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : recommendProperties.getPreferencePatterns().entrySet()) {
            for (String pattern : entry.getValue()) {
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                    signals.add(entry.getKey());
                    break;
                }
            }
        }
        boolean sufficient = signals.size() >= Math.max(1, recommendProperties.getMinPreferenceSignals());
        return new RecommendDecision(hasRecommendIntent, sufficient, new ArrayList<>(signals));
    }

    public int fallbackRecommendCount() {
        int count = recommendProperties.getFallbackRecommendCount();
        if (count < 1) {
            return 1;
        }
        if (count > 20) {
            return 20;
        }
        return count;
    }

    public String directRecommendText() {
        return recommendProperties.getDirectRecommendText();
    }

    public String rejectRecommendText() {
        return recommendProperties.getRejectRecommendText();
    }

    private boolean matchesAny(String text, List<String> patterns) {
        if (text == null || text.isBlank() || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    public static class RecommendDecision {
        private final boolean hasRecommendIntent;
        private final boolean sufficientPreference;
        private final List<String> preferenceSignals;

        public RecommendDecision(boolean hasRecommendIntent, boolean sufficientPreference, List<String> preferenceSignals) {
            this.hasRecommendIntent = hasRecommendIntent;
            this.sufficientPreference = sufficientPreference;
            this.preferenceSignals = preferenceSignals;
        }

        public boolean isHasRecommendIntent() {
            return hasRecommendIntent;
        }

        public boolean isSufficientPreference() {
            return sufficientPreference;
        }

        public List<String> getPreferenceSignals() {
            return preferenceSignals;
        }
    }
}
