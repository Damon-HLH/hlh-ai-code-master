package com.hlh.hlhaicodemaster.ai;

import com.hlh.hlhaicodemaster.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流式对话并发测试：验证多个不同 appId 同时发起流式对话时，是否会互相阻塞。
 * <p>
 * 判定思路（区分"应用侧串行"与"外部延迟"）：
 * - 三路请求用同一个"发令枪"(CountDownLatch) 同时开始订阅，记录每路：
 * 【派发时刻 subscribe】【首个 token 到达 first】【流完成 complete】相对于统一起点 t0 的耗时。
 * - 派发时刻：若三路都接近 0（几乎同时把请求交出去），说明应用侧没有排队阻塞；
 * 若明显错开（后一路等到前一路完成才派发），说明应用侧串行。
 * - 流式窗口 [first, complete] 是否重叠：用较长输出的提示词，让每路流式持续数秒，
 * 并发时窗口应大量重叠，串行时则首尾相接。
 * <p>
 * 注意：本测试会真实调用大模型接口，需要本地 MySQL / Redis / 网络就绪，会产生一定 token 消耗。
 * 建议连续跑 2~3 次：若被延迟的总是同一路 => 更像应用侧问题；若随机某路延迟 => 更像外部(API限流/网络)抖动。
 */
@Slf4j
@SpringBootTest
public class AiStreamConcurrentTest {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    /**
     * 并发路数
     */
    private static final int CONCURRENCY = 3;

    /**
     * 单路流式超时时间
     */
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(180);

    /**
     * 使用明显的测试专用 appId，避免污染真实业务数据
     */
    private static final long[] TEST_APP_IDS = {999000001L, 999000002L, 999000003L};

    /**
     * 使用能产出较长输出的提示词，让每路流式持续数秒，便于观察窗口是否重叠
     */
    private static final String PROMPT =
            "生成一个完整的单页 HTML 个人简介网站，包含导航栏、英雄区、关于我、技能列表(至少8项)、"
                    + "项目展示(至少4个卡片)、时间线、联系方式和页脚，并配上完整的内联 CSS 样式，内容尽量丰富";

    @Test
    public void testConcurrentStreamingDoesNotBlock() throws InterruptedException {
        // 每一路的测量结果（用数组按索引写入，避免多线程操作 List 的并发问题）
        StreamMetrics[] metricsArray = new StreamMetrics[CONCURRENCY];
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(CONCURRENCY);

        // 统一起点 t0（发令枪打开的时刻）
        AtomicLong t0 = new AtomicLong();

        for (int i = 0; i < CONCURRENCY; i++) {
            final long appId = TEST_APP_IDS[i];
            final int index = i + 1;
            final int slot = i;
            Thread.ofVirtual().name("stream-test-" + index).start(() -> {
                StreamMetrics metrics = new StreamMetrics(index, appId);
                metricsArray[slot] = metrics;
                try {
                    // 所有线程在此等待，确保"同时"发起
                    startGate.await();
                    // 每个 appId 拿到各自缓存的 AI Service 实例（与真实业务路径一致）
                    AiCodeGeneratorService service =
                            aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);

                    Flux<String> stream = service.generateHtmlCodeStream(PROMPT);

                    // 派发时刻：订阅(触发底层发起 HTTP 请求)前的时间点
                    metrics.subscribeAt = System.nanoTime();
                    stream.doOnNext(token -> {
                                long now = System.nanoTime();
                                metrics.firstTokenAt.compareAndSet(0, now);
                                metrics.lastTokenAt.set(now);
                                metrics.tokenCount.incrementAndGet();
                            })
                            .doOnError(err -> {
                                metrics.error = err;
                                log.error("路 {} (appId={}) 流式出错: {}", index, appId, err.getMessage());
                            })
                            .blockLast(STREAM_TIMEOUT);
                } catch (Exception e) {
                    metrics.error = e;
                    log.error("路 {} (appId={}) 执行异常: {}", index, appId, e.getMessage(), e);
                } finally {
                    metrics.finishAt = System.nanoTime();
                    doneGate.countDown();
                }
            });
        }

        // 发令枪：三路同时开始
        t0.set(System.nanoTime());
        startGate.countDown();

        boolean allDone = doneGate.await(STREAM_TIMEOUT.toSeconds() * CONCURRENCY, TimeUnit.SECONDS);
        if (!allDone) {
            log.warn("部分流式请求在超时时间内未完成");
        }

        printReport(t0.get(), metricsArray);
    }

    /**
     * 打印时间线报告并给出是否阻塞的判定
     */
    private void printReport(long t0, StreamMetrics[] metricsArray) {
        log.info("==================== 流式并发测试报告 ====================");
        List<StreamMetrics> metricsList = new ArrayList<>(List.of(metricsArray));
        metricsList.sort(Comparator.comparingInt(m -> m.index));

        boolean anyError = false;
        long maxSubscribe = 0;
        for (StreamMetrics m : metricsList) {
            if (m.error != null) {
                anyError = true;
            }
            long subscribeMs = toMs(m.subscribeAt, t0);
            long firstMs = m.firstTokenAt.get() == 0 ? -1 : toMs(m.firstTokenAt.get(), t0);
            long completeMs = toMs(m.finishAt, t0);
            long streamMs = m.firstTokenAt.get() == 0 ? 0 : toMs(m.finishAt, m.firstTokenAt.get());
            maxSubscribe = Math.max(maxSubscribe, subscribeMs);

            log.info("路 {} | appId={} | 派发={}ms | 首token={}ms | 完成={}ms | 流式时长={}ms | token数={} | 错误={}",
                    m.index, m.appId, subscribeMs, firstMs, completeMs, streamMs, m.tokenCount.get(),
                    m.error == null ? "无" : m.error.getClass().getSimpleName());
        }

        // 统计流式窗口 [first, complete] 的重叠对数
        int overlapPairs = 0;
        for (int a = 0; a < metricsList.size(); a++) {
            for (int b = a + 1; b < metricsList.size(); b++) {
                if (windowsOverlap(metricsList.get(a), metricsList.get(b))) {
                    overlapPairs++;
                }
            }
        }
        int totalPairs = CONCURRENCY * (CONCURRENCY - 1) / 2;

        log.info("----------------------------------------------------------");
        log.info("最大派发时刻={}ms（三路是否几乎同时把请求交出去）", maxSubscribe);
        log.info("流式窗口重叠对数={}/{}", overlapPairs, totalPairs);

        if (anyError) {
            log.info("判定：⚠️ 有请求出错，结果不可靠，请检查网络/配置后重试");
        } else if (maxSubscribe > 1000) {
            // 派发本身就被拉开 => 应用侧在订阅/发起阶段就串行了
            log.info("判定：❌ 应用侧疑似串行——三路派发时刻被明显拉开(最大 {}ms)，请求不是同时发出的", maxSubscribe);
        } else if (overlapPairs == totalPairs) {
            log.info("判定：✅ 并发良好——三路几乎同时派发，且流式窗口两两重叠，未互相阻塞");
        } else if (overlapPairs == 0) {
            log.info("判定：🟡 三路派发几乎同时(应用侧未阻塞)，但流式窗口互不重叠——"
                    + "首token到达被拉开，更可能是外部(API限流/网络/模型首token延迟)。请结合 langchain4j 请求日志的请求发出时间戳确认");
        } else {
            log.info("判定：🟡 部分重叠({}/{} 对)，应用侧基本并发，个别路首token偏慢，建议多跑几次看是否随机", overlapPairs, totalPairs);
        }
        log.info("==========================================================");
    }

    /**
     * 判断两路的流式窗口 [firstToken, finish] 是否重叠
     */
    private boolean windowsOverlap(StreamMetrics a, StreamMetrics b) {
        if (a.firstTokenAt.get() == 0 || b.firstTokenAt.get() == 0) {
            return false;
        }
        // a 开始在 b 结束之前，且 b 开始在 a 结束之前 => 重叠
        return a.firstTokenAt.get() < b.finishAt && b.firstTokenAt.get() < a.finishAt;
    }

    private static long toMs(long nanoTime, long baseNano) {
        return TimeUnit.NANOSECONDS.toMillis(nanoTime - baseNano);
    }

    /**
     * 单路流式请求的测量数据
     */
    private static class StreamMetrics {
        final int index;
        final long appId;
        volatile long subscribeAt;
        final AtomicLong firstTokenAt = new AtomicLong(0);
        final AtomicLong lastTokenAt = new AtomicLong(0);
        final AtomicLong tokenCount = new AtomicLong(0);
        volatile long finishAt;
        volatile Throwable error;

        StreamMetrics(int index, long appId) {
            this.index = index;
            this.appId = appId;
        }
    }
}
