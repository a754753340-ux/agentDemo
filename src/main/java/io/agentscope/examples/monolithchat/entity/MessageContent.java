package io.agentscope.examples.monolithchat.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("message_content")
public class MessageContent {

    @TableField("message_id")
    private String messageId;

    @TableField("idx")
    private Integer idx;

    @TableField("type")
    private String type;

    @TableField("data")
    private String data;
}
