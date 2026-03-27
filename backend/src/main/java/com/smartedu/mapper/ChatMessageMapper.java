package com.smartedu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartedu.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息 Mapper 接口
 * 
 * @author SmartEducation Team
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
