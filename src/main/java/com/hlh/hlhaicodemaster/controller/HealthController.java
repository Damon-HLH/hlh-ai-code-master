package com.hlh.hlhaicodemaster.controller;


import com.hlh.hlhaicodemaster.common.BaseResponse;
import com.hlh.hlhaicodemaster.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> checkHealth() {
        return ResultUtils.success("请求成功");
    }
}
