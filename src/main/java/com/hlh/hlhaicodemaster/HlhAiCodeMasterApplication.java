package com.hlh.hlhaicodemaster;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hlh.hlhaicodemaster.mapper")
public class HlhAiCodeMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(HlhAiCodeMasterApplication.class, args);
    }

}
