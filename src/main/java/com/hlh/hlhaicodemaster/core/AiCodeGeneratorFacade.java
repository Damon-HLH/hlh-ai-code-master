package com.hlh.hlhaicodemaster.core;

import com.hlh.hlhaicodemaster.ai.AiCodeGeneratorService;
import com.hlh.hlhaicodemaster.ai.model.HtmlCodeResult;
import com.hlh.hlhaicodemaster.ai.model.MultiFileCodeResult;
import com.hlh.hlhaicodemaster.core.parser.CodeParserExecutor;
import com.hlh.hlhaicodemaster.core.saver.CodeFileSaverExecutor;
import com.hlh.hlhaicodemaster.exception.BusinessException;
import com.hlh.hlhaicodemaster.exception.ErrorCode;
import com.hlh.hlhaicodemaster.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.io.File;

/**
 * AI 代码生成门面类，组合代码生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 一、普通统一入口（同步，直接返回代码文件）：
     * 根据用户提示词和代码生成类型，
     * 调用 AI 生成相应代码和保存代码到文件中功能
     *
     * @param userMessage
     * @param codeGenTypeEnum
     * @return
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(htmlCodeResult, CodeGenTypeEnum.HTML);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(multiFileCodeResult, CodeGenTypeEnum.MULTI_FILE);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 二、统一入口（流式，返回代码片段）：
     * 根据用户提示词和代码生成类型，
     * 调用 AI 生成相应代码和保存代码到文件中(流式)
     *
     * @param userMessage
     * @param codeGenTypeEnum
     * @return
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /*
     * 通用的处理代码流并保存代码
     * 根据不同的生成代码模式，对应执行不同的 解析代码、保存代码文件 逻辑
     *
     * @param codeStream      代码流
     * @param codeGenTypeEnum 代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenTypeEnum) {
        //定义一个字符串拼接器，用于当流式返回所有的代码之后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                .doOnNext(chunk -> {
                    // 实时收集代码片段
                    codeBuilder.append(chunk);
                })
                .doOnComplete(() -> {
                    // 流式返回完成后，保存代码
                    try {
                        String completeCode = codeBuilder.toString();
                        // 1.使用执行器解析代码为对象
                        Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenTypeEnum);
                        // 2.使用执行器保存代码到文件
                        File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenTypeEnum);
                        log.info("保存代码完成，保存目录：{}", saveDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存代码出错：{}", e.getMessage());
                    }
                });
    }

}
