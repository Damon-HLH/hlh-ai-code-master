package com.hlh.hlhaicodemaster.core.handler;

import com.hlh.hlhaicodemaster.model.entity.User;
import com.hlh.hlhaicodemaster.model.enums.ChatHistoryMessageTypeEnum;
import com.hlh.hlhaicodemaster.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .map(chunk -> {
                    // 收集AI响应内容
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史（落库失败仅记日志，不能阻断流的正常结束）
                    String aiResponse = aiResponseBuilder.toString();
                    try {
                        chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    } catch (Exception e) {
                        log.error("保存 AI 回复到对话历史失败，appId: {}, 消息长度: {}", appId, aiResponse.length(), e);
                    }
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息（落库失败仅记日志，避免二次异常把错误信号冲掉）
                    try {
                        String errorMessage = "AI回复失败: " + error.getMessage();
                        chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    } catch (Exception e) {
                        log.error("记录 AI 错误消息到对话历史失败，appId: {}", appId, e);
                    }
                });
    }
}
