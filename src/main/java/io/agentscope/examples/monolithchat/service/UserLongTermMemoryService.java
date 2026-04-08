package io.agentscope.examples.monolithchat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.examples.monolithchat.dto.RecommendTagItem;
import io.agentscope.examples.monolithchat.entity.UserBehaviorMemory;
import io.agentscope.examples.monolithchat.entity.UserMemoryTag;
import io.agentscope.examples.monolithchat.mapper.UserBehaviorMemoryMapper;
import io.agentscope.examples.monolithchat.mapper.UserMemoryTagMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserLongTermMemoryService {

    private final UserMemoryTagMapper userMemoryTagMapper;
    private final UserBehaviorMemoryMapper userBehaviorMemoryMapper;

    public UserLongTermMemoryService(
            UserMemoryTagMapper userMemoryTagMapper,
            UserBehaviorMemoryMapper userBehaviorMemoryMapper) {
        this.userMemoryTagMapper = userMemoryTagMapper;
        this.userBehaviorMemoryMapper = userBehaviorMemoryMapper;
    }

    public void recordTagSelection(String userId, String source, RecommendTagItem tag) {
        if (userId == null || userId.isBlank() || tag == null || tag.getId() == null || tag.getId().isBlank()) {
            return;
        }
        String tagKey = "recommend_tag";
        String tagValue = tag.getLabel() == null || tag.getLabel().isBlank() ? tag.getId().strip() : tag.getLabel().strip();
        LambdaQueryWrapper<UserMemoryTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMemoryTag::getUserId, userId.strip())
                .eq(UserMemoryTag::getTagKey, tagKey)
                .eq(UserMemoryTag::getTagValue, tagValue)
                .last("limit 1");
        UserMemoryTag existed = userMemoryTagMapper.selectOne(wrapper);
        if (existed == null) {
            UserMemoryTag memoryTag = new UserMemoryTag();
            memoryTag.setUserId(userId.strip());
            memoryTag.setTagKey(tagKey);
            memoryTag.setTagValue(tagValue);
            memoryTag.setWeight(1.0);
            memoryTag.setSource(source == null ? "tag_select" : source);
            memoryTag.setUpdatedTime(LocalDateTime.now());
            userMemoryTagMapper.insert(memoryTag);
            return;
        }
        Double weight = existed.getWeight() == null ? 0.0 : existed.getWeight();
        existed.setWeight(weight + 1.0);
        existed.setUpdatedTime(LocalDateTime.now());
        existed.setSource(source == null ? existed.getSource() : source);
        userMemoryTagMapper.updateById(existed);
    }

    public void recordBehavior(String userId, String sessionId, String behaviorType, String behaviorValue, String triggerMessageId) {
        if (userId == null || userId.isBlank() || behaviorType == null || behaviorType.isBlank()) {
            return;
        }
        UserBehaviorMemory memory = new UserBehaviorMemory();
        memory.setUserId(userId.strip());
        memory.setSessionId(sessionId == null ? "" : sessionId.strip());
        memory.setBehaviorType(behaviorType.strip());
        memory.setBehaviorValue(behaviorValue == null ? "" : behaviorValue.strip());
        memory.setTriggerMessageId(triggerMessageId == null ? "" : triggerMessageId.strip());
        memory.setCreateTime(LocalDateTime.now());
        userBehaviorMemoryMapper.insert(memory);
    }

    public String loadTagSummary(String userId, int limit) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        LambdaQueryWrapper<UserMemoryTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMemoryTag::getUserId, userId.strip())
                .orderByDesc(UserMemoryTag::getWeight)
                .orderByDesc(UserMemoryTag::getUpdatedTime)
                .last("limit " + Math.max(1, limit));
        List<UserMemoryTag> tags = userMemoryTagMapper.selectList(wrapper);
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (UserMemoryTag tag : tags) {
            if (tag == null || tag.getTagValue() == null || tag.getTagValue().isBlank()) {
                continue;
            }
            double weight = tag.getWeight() == null ? 0.0 : tag.getWeight();
            lines.add(tag.getTagKey() + "=" + tag.getTagValue() + "(weight=" + String.format("%.1f", weight) + ")");
        }
        return String.join("；", lines);
    }

    public String loadBehaviorSummary(String userId, int limit) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        LambdaQueryWrapper<UserBehaviorMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehaviorMemory::getUserId, userId.strip())
                .orderByDesc(UserBehaviorMemory::getCreateTime)
                .last("limit " + Math.max(1, limit));
        List<UserBehaviorMemory> list = userBehaviorMemoryMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (UserBehaviorMemory item : list) {
            if (item == null || item.getBehaviorType() == null || item.getBehaviorType().isBlank()) {
                continue;
            }
            String value = item.getBehaviorValue() == null ? "" : item.getBehaviorValue();
            lines.add(item.getBehaviorType() + ":" + value);
        }
        return String.join("；", lines);
    }
}
