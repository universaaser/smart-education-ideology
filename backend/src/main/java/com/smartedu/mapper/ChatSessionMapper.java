package com.smartedu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartedu.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper 接口
 * 
 * @author SmartEducation Team
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
