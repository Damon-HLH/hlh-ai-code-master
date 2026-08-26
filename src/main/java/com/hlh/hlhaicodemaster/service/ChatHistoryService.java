package com.hlh.hlhaicodemaster.service;

import com.hlh.hlhaicodemaster.model.dto.chathistory.ChatHistoryQueryRequest;
import com.hlh.hlhaicodemaster.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.hlh.hlhaicodemaster.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/Damon-HLH">hlh</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话记录
     * @param appId
     * @param message
     * @param messageType
     * @param userId
     * @return
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据appId删除对话记录
     * @param appId
     * @return
     */
    boolean deleteByAppId(Long appId);

    /**
     * 根据appId分页查询对话记录-游标查询
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 加载对话历史到内存中
     * @param appId
     * @param chatMemory
     * @param maxCount 最多加载多少条
     * @return 加载成功的对话历史数量
     */
    int loadChatHistoryToMemory(long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 构造查询条件
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
