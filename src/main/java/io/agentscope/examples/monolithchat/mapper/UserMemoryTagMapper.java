package io.agentscope.examples.monolithchat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.agentscope.examples.monolithchat.entity.UserMemoryTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMemoryTagMapper extends BaseMapper<UserMemoryTag> {
}
