package io.agentscope.examples.monolithchat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_behavior_memory")
public class UserBehaviorMemory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    @TableField("session_id")
    private String sessionId;

    @TableField("behavior_type")
    private String behaviorType;

    @TableField("behavior_value")
    private String behaviorValue;

    @TableField("trigger_message_id")
    private String triggerMessageId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
