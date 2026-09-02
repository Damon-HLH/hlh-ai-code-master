package com.hlh.hlhaicodemaster.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.hlh.hlhaicodemaster.constant.UserConstant;
import com.hlh.hlhaicodemaster.exception.ErrorCode;
import com.hlh.hlhaicodemaster.exception.ThrowUtils;
import com.hlh.hlhaicodemaster.model.dto.chathistory.ChatHistoryQueryRequest;
import com.hlh.hlhaicodemaster.model.entity.App;
import com.hlh.hlhaicodemaster.model.entity.User;
import com.hlh.hlhaicodemaster.model.enums.ChatHistoryMessageTypeEnum;
import com.hlh.hlhaicodemaster.service.AppService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.hlh.hlhaicodemaster.model.entity.ChatHistory;
import com.hlh.hlhaicodemaster.mapper.ChatHistoryMapper;
import com.hlh.hlhaicodemaster.service.ChatHistoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 服务层实现。
 *
 * @author <a href="https://github.com/Damon-HLH">hlh</a>
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    @Lazy
    private AppService appService;

    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        // 验证参数
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);
        // 插入数据库
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .message(message)
                .messageType(messageType)
                .userId(userId)
                .build();
        return this.save(chatHistory);
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }

    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：应用创建者 或者 管理员 可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }

    /**
     * 对话记忆初始化时，需要从数据库中加载历史对话到记忆中
     *
     * 几个重要细节：
     * 1. 查询起始点设置为1而不是0，这是为了排除最新的用户消息。因为在对话流程中，用
     * 户消息被添加到数据库后，AI服务也会自动将用户消息添加到记忆中，如果不排除会导致消息重复。
     * 2. 注意反转从数据库中查到的消息列表，确保加载到记忆中的消息是按时间正序的。
     * 3. 加载前先清理Redis中的历史对话记忆，防止重复加载。
     * 然后就可以在初始化AlService的对话记忆时调用了，这相当于是懒加载，对话时才会加载记忆，节约内存。
     *
     * 关于 maxCount 与记忆窗口容量 maxMessages（见 AiCodeGeneratorServiceFactory 中 MessageWindowChatMemory.maxMessages）的关系：
     * 4. 二者职责不同，无需相等：maxMessages 是窗口容量上限（一次活跃会话内可堆积的消息数，超限后从最老淘汰）；
     *    maxCount 只是冷重载时从数据库捞回的“跨轮历史”条数。
     * 5. 本方法开头会执行 chatMemory.clear()，因此冷重载后记忆中最多只有 maxCount 条历史，即“跨轮记忆上限 = maxCount”，而非 maxMessages。
     * 6. 必须保证 maxCount < maxMessages，并给“当前这一轮”预留空间：本轮会加入 SystemMessage + 当前 UserMessage，
     *    Vue 项目场景一轮最多约 25 次工具调用（约 50 条工具请求/工具结果消息）。若 maxCount = maxMessages，
     *    刚重载的历史会被本轮消息立即挤出窗口，白白多查数据库且无收益。
     * 7. chat_history 表的 messageType 只有 user/ai，但 ai 消息是“合成文本”：流式响应完成时，
     *    JsonMessageStreamHandler 把 AI 文本片段 + 各工具的 TOOL_EXECUTED 结果拼接入库
     *    （TOOL_REQUEST 标记只给前端，不入库）。其中 FileWriteTool / FileModifyTool 会把完整
     *    文件内容嵌入。因此重载后 AI 能“读到”历史轮次的工具动作与生成代码 —— 跨轮上下文并非不可恢复。
     * 8. 但要区分两种形态：(a) 结构化协议消息（AiMessage.toolExecutionRequests + 配对 ToolExecutionResultMessage），
     *    只在当前活跃轮次窗口内，由 maxMessages 管，是工具循环能跑起来的真正协议，不入库、无法重载还原；
     *    (b) 扁平化文本（DB 那条合成 AI 消息），跨轮次可重载，由 maxCount 管能重载几轮。
     *    防死循环：当前轮次靠 maxMessages 窗口里的结构化消息；跨轮次靠 maxCount 控制的扁平化文本让 AI 以读文本方式了解历史。
     * 9. 由于每条 Vue AI 消息内嵌完整生成代码，maxMessages/maxCount 数的是条数而非 token，
     *    20 条重载可能已是很大的 token 量。若 maxCount 提到 50，冷重载会一次性把几十条含完整代码的消息
     *    塞进上下文，极易撑爆模型上下文窗口、token 成本剧增。故 maxCount 应保持小（20 甚至更小），切勿等于 maxMessages。
     * 10. 结论：maxCount 应明显小于 maxMessages（当前 20 / 50 合理）；仅当“跨很多轮后 AI 记不住早期需求”时才适当调大
     *     maxCount（如 30），必要时同步抬高 maxMessages（如 60~80），切勿设为相等。
     *
     * @param appId
     * @param chatMemory
     * @param maxCount   最多加载多少条历史对话；必须小于窗口容量 maxMessages 并预留本轮空间，切勿与 maxMessages 相等
     * @return
     */
    @Override
    public int loadChatHistoryToMemory(long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxCount); //!!! 从1开始加载，因为之前将第一条用户消息加入到数据库后，就创建了AI Service实例，载入了记忆
            List<ChatHistory> chatHistoryList = this.list(queryWrapper);
            if (CollUtil.isEmpty(chatHistoryList)) {
                return 0;
            }
            // 翻转列表，确保按照时间正序（老的在前，新的再后(下)）
            chatHistoryList = chatHistoryList.reversed();
            // 按照时间顺序将消息添加到记忆中
            int loadedCount = 0;
            // 先清理Redis历史缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory history : chatHistoryList) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime); // less than
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列!!!
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }


}
