package io.agentscope.examples.monolithchat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(value = "message_id", type = IdType.INPUT)
    private String messageId;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private String userId;

    @TableField("role")
    private String role;

    @TableField("status")
    private String status;

    @TableField("seq")
    private Integer seq;

    @TableField("create_time")
    private LocalDateTime createTime;
}
