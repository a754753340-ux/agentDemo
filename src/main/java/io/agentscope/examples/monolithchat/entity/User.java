package io.agentscope.examples.monolithchat.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer gender; // 0-未知 1-男 2-女
    private Integer diamond;
    private String introduction;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}