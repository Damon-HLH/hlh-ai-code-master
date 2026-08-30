package com.hlh.hlhaicodemaster.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hlh.hlhaicodemaster.ai.model.message.*;
import com.hlh.hlhaicodemaster.ai.tools.BaseTool;
import com.hlh.hlhaicodemaster.ai.tools.ToolManager;
import com.hlh.hlhaicodemaster.constant.AppConstant;
import com.hlh.hlhaicodemaster.core.builder.VueProjectBuilder;
import com.hlh.hlhaicodemaster.model.entity.User;
import com.hlh.hlhaicodemaster.model.enums.ChatHistoryMessageTypeEnum;
import com.hlh.hlhaicodemaster.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * 规范化了响应格式（包括AI回复、工具调用、工具调用结果），最终传给前端和保存数据化库的 输出 是一致的!
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史(这里已经得到经过json处理的响应结果，规范化了响应格式，最终传给前端和保存数据化库的AI输出是一致的)
                    String aiResponse = chatHistoryStringBuilder.toString();
                    try {
                        chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    } catch (Exception e) {
                        // 历史记录落库失败不能阻断流程，否则后续的 Vue 项目构建也不会被触发，仅记录日志，流仍正常结束给前端发 done 事件。
                        log.error("保存 AI 回复到对话历史失败，appId: {}, 消息长度: {}", appId, aiResponse.length(), e);
                    }
                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR+"/vue_project_"+appId;
                    // 在所有代码生成并写入文件完成后（AI用写入文件工具），就可以去构建Vue项目（npm install && npm run build）
                    vueProjectBuilder.buildProjectAsync(projectPath);
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

    /**
     * 解析并收集 TokenStream 数据
     * return 的数据是给前端的
     * chatHistoryStringBuilder.append 的数据是给后端的 保存到数据库的
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    BaseTool tool = toolManager.getTool(toolName);
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                String toolName = toolExecutedMessage.getName();
                BaseTool tool = toolManager.getTool(toolName);
                // 输出前端和要持久化的内容
                String result = tool.generateToolExecutedResult(jsonObject);
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }
}
