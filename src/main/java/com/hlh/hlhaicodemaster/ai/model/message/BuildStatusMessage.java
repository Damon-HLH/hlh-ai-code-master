package com.hlh.hlhaicodemaster.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 构建状态消息
 * <p>
 * 用于 Vue 工程模式在代码生成完成后，将项目打包构建（npm install / npm run build）的
 * 实时进度通过已有的 SSE 通道推送给前端，避免构建期间前端静默等待、无进度反馈。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class BuildStatusMessage extends StreamMessage {

    /**
     * 构建阶段标识：BUILDING（构建中）、SUCCESS（构建成功）、FAILED（构建失败）
     */
    private String stage;

    /**
     * 展示给前端的文本内容
     */
    private String data;

    public BuildStatusMessage(String stage, String data) {
        super(StreamMessageTypeEnum.BUILD_STATUS.getValue());
        this.stage = stage;
        this.data = data;
    }
}
