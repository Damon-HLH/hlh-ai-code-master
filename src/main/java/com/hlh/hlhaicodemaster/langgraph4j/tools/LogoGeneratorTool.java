package com.hlh.hlhaicodemaster.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisOutput;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.hlh.hlhaicodemaster.langgraph4j.model.ImageResource;
import com.hlh.hlhaicodemaster.langgraph4j.model.enums.ImageCategoryEnum;
import com.hlh.hlhaicodemaster.manager.CosManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Logo 图片生成工具
 */
@Slf4j
@Component
public class LogoGeneratorTool {

    @Resource
    private CosManager cosManager;

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    // wanx2.0-t2i-turbo 仅支持固定分辨率集合，最小为 768*768，不支持 512*512
                    .size("768*768")
                    .n(1) // 生成 1 张足够，因为 AI 不知道哪张最好
                    .build();
            ImageSynthesis imageSynthesis = new ImageSynthesis();
            ImageSynthesisResult result = imageSynthesis.call(param);
            // 任务失败时 SDK 不抛异常（HTTP 仍为 200），需主动检查任务状态，避免静默返回空列表
            if (result == null || result.getOutput() == null) {
                log.error("生成 Logo 失败：DashScope 返回为空，requestId={}",
                        result != null ? result.getRequestId() : "null");
                return logoList;
            }
            ImageSynthesisOutput output = result.getOutput();
            if (!"SUCCEEDED".equals(output.getTaskStatus())) {
                log.error("生成 Logo 失败：任务状态={}，错误码={}，错误信息={}，requestId={}",
                        output.getTaskStatus(), output.getCode(), output.getMessage(), result.getRequestId());
                return logoList;
            }
            if (output.getResults() != null) {
                List<Map<String, String>> results = output.getResults();
                for (Map<String, String> imageResult : results) {
                    String imageUrl = imageResult.get("url");
                    if (StrUtil.isNotBlank(imageUrl)) {
                        // DashScope 临时链接仅 24 小时有效，转存到腾讯云 COS 获得持久化 URL；转存失败则降级使用临时链接
                        String finalUrl = transferToCos(imageUrl);
                        logoList.add(ImageResource.builder()
                                .category(ImageCategoryEnum.LOGO)
                                .description(description)
                                .url(finalUrl)
                                .build());
                    } else {
                        log.warn("生成 Logo 的单张图片失败：错误码={}，错误信息={}",
                                imageResult.get("code"), imageResult.get("message"));
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }

    /**
     * 将 DashScope 生成的临时图片下载并转存到腾讯云 COS，返回持久化访问 URL，失败时返回原临时链接
     */
    private String transferToCos(String imageUrl) {
        File tempFile = null;
        try {
            // 下载阿里云 OSS 的临时图片到本地临时文件（DashScope 输出为 png 格式）
            tempFile = FileUtil.createTempFile("logo_", ".png", true);
            HttpUtil.downloadFile(imageUrl, tempFile);
            // 上传到 COS
            String keyName = String.format("/logo/%s/%s", RandomUtil.randomString(5), tempFile.getName());
            String cosUrl = cosManager.uploadFile(keyName, tempFile);
            if (StrUtil.isNotBlank(cosUrl)) {
                return cosUrl;
            }
        } catch (Exception e) {
            log.error("Logo 图片转存 COS 失败，降级使用临时链接: {}", e.getMessage(), e);
        } finally {
            // 清理临时文件，避免磁盘堆积；即使删除失败也不影响主流程，由 JVM 退出时兜底删除
            if (tempFile != null) {
                tempFile.deleteOnExit();
                FileUtil.del(tempFile);
            }
        }
        return imageUrl;
    }
}
