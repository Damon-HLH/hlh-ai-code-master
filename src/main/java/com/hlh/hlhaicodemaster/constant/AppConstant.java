package com.hlh.hlhaicodemaster.constant;

/**
 * 应用常量
 */
public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";

    /**
     * 构建状态消息在 SSE 内容流中的标识前缀。
     * 流处理器给构建状态消息加上该前缀，控制器据此将其从正文流中剥离，
     * 路由为独立的 build-status SSE 事件，供前端渲染构建进度条/状态气泡。
     */
    String BUILD_STATUS_STREAM_PREFIX = "@@build-status@@";
}
