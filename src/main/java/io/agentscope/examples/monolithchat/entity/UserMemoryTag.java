package io.agentscope.examples.monolithchat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_memory_tag")
public class UserMemoryTag {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    @TableField("tag_key")
    private String tagKey;

    @TableField("tag_value")
    private String tagValue;

    @TableField("weight")
    private Double weight;

    @TableField("source")
    private String source;

    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
