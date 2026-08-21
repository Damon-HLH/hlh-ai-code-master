package com.hlh.hlhaicodemaster.exception;

import com.hlh.hlhaicodemaster.common.BaseResponse;
import com.hlh.hlhaicodemaster.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    //业务异常
    //假如在service层抛出BusinessException异常，则执行此方法
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    //运行时异常
    //假如在service层抛出RuntimeException异常，则执行此方法
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
