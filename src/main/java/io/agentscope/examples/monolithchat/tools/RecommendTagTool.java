package io.agentscope.examples.monolithchat.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.examples.monolithchat.conf.RecommendTagProperties;
import io.agentscope.examples.monolithchat.dto.RecommendTagItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendTagTool {

    private final RecommendTagProperties recommendTagProperties;

    public RecommendTagTool(RecommendTagProperties recommendTagProperties) {
        this.recommendTagProperties = recommendTagProperties;
    }

    @Tool(description = "根据用户意图返回推荐标签列表供用户选择")
    public Object getRecommendTags() {
        List<RecommendTagItem> tags = new ArrayList<>();
        for (RecommendTagProperties.TagItem item : recommendTagProperties.getTags()) {
            if (item == null || !item.isEnabled() || item.getId() == null || item.getId().isBlank()) {
                continue;
            }
            RecommendTagItem tag = new RecommendTagItem();
            tag.setId(item.getId());
            tag.setLabel(item.getLabel());
            tag.setPromptHint(item.getPromptHint());
            tags.add(tag);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "请选择你偏好的推荐标签");
        data.put("tags", tags);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("render_type", "tag_list");
        payload.put("data", data);
        return payload;
    }
}
