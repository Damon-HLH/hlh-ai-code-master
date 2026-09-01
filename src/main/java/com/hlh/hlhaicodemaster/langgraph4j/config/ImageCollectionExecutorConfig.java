package com.hlh.hlhaicodemaster.langgraph4j.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图片收集专用线程池配置
 * 图片搜集任务均为长耗时 IO 型操作（HTTP 搜图、文生图、Mermaid CLI 渲染），
 * 使用独立线程池与默认公共线程池隔离，避免相互阻塞，并便于监控与调优
 */
@Configuration
public class ImageCollectionExecutorConfig {

    /**
     * IO 密集型任务线程池：核心线程数取较大值；有界队列防止任务堆积；
     * 拒绝策略采用 CallerRuns，降级由调用线程执行，保证任务不丢失
     */
    @Bean("imageCollectionExecutor")
    public ExecutorService imageCollectionExecutor() {
        return new ThreadPoolExecutor(
                8,
                16,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(32),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "image-collect-" + threadNumber.getAndIncrement());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
