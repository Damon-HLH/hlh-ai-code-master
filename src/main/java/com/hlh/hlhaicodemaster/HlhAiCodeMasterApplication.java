package com.hlh.hlhaicodemaster;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@MapperScan("com.hlh.hlhaicodemaster.mapper")
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
public class HlhAiCodeMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(HlhAiCodeMasterApplication.class, args);
    }

}
