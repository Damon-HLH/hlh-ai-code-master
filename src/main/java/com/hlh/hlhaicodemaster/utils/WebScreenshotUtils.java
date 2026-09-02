package com.hlh.hlhaicodemaster.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hlh.hlhaicodemaster.exception.BusinessException;
import com.hlh.hlhaicodemaster.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

/**
 * 截图工具类
 */
@Slf4j
public class WebScreenshotUtils {

    // 1）第一步是初始化驱动。需要注意避免重复初始化驱动程序：
    //1.在静态代码块里初始化驱动，确保整个应用生命周期内只初始化一次
    //2.默认使用已经初始化好的驱动实例
    //3.在项目停止前正确销毁驱动，释放资源

    private static final WebDriver webDriver;

    //全局静态初始化，避免重复初始化驱动程序。
    static {
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HEIGHT = 900;
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);

//        // 注册 JVM 关闭钩子
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            log.info("JVM 正在关闭，准备清理 Chrome 进程...");
//            if (webDriver != null) {
//                try {
//                    webDriver.quit(); // 这样才能真正在应用停止时杀死 Chrome 进程
//                    log.info("Chrome 进程清理完毕");
//                } catch (Exception e) {
//                    log.error("清理 Chrome 进程失败", e);
//                }
//            }
//        }));
    }

    /**
     * 退出时销毁
     */
    @PreDestroy
    public void destroy() {
        webDriver.quit();
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        try {
            // 创建临时目录
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            // 图片后缀
            final String IMAGE_SUFFIX = ".png";
            // 原始截图文件路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            // 访问网页
            webDriver.get(webUrl);
            // 等待页面加载完成
            waitForPageLoad(webDriver);
            // 截图
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            // 保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功: {}", imageSavePath);
            // 压缩图片
            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功: {}", compressedImagePath);
            // 删除原始图片，只保留压缩图片
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        }
    }


    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 配置 Chrome 选项
            ChromeOptions options = buildChromeOptions(width, height);
            // 创建驱动：优先使用 Selenium 4 内置的 Selenium Manager 自动管理驱动，失败时回退到 WebDriverManager
            WebDriver driver = createChromeDriverWithFallback(options);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 创建 ChromeDriver，按优先级尝试多种驱动管理方式：
     * <ol>
     *   <li>本地已安装的 chromedriver（通过系统属性或 PATH 查找，无网络请求，最快）</li>
     *   <li>Selenium 4 内置的 Selenium Manager（自动管理，但需访问 Google 域名，国内可能超时）</li>
     *   <li>WebDriverManager（第三方库，支持国内镜像）</li>
     * </ol>
     */
    private static WebDriver createChromeDriverWithFallback(ChromeOptions options) {
        // 方案一：优先使用本地已安装的 chromedriver（零网络请求，生产环境推荐）
        // 可通过 -Dwebdriver.chrome.driver=/path/to/chromedriver 指定路径
        // 或将 chromedriver 加入系统 PATH
        String localDriverPath = resolveLocalChromeDriverPath();
        if (localDriverPath != null) {
            try {
                log.info("使用本地已安装的 chromedriver: {}", localDriverPath);
                System.setProperty("webdriver.chrome.driver", localDriverPath);
                WebDriver driver = new ChromeDriver(options);
                log.info("本地 chromedriver 启动成功");
                return driver;
            } catch (Exception e) {
                log.warn("本地 chromedriver 启动失败（{}），尝试下一种方式", e.getMessage());
                System.clearProperty("webdriver.chrome.driver");
            }
        }
        // 方案二：Selenium 4 内置的 Selenium Manager（自动检测浏览器并下载匹配驱动）
        // 注意：需要访问 googlechromelabs.github.io，国内网络可能超时
        try {
            log.info("尝试使用 Selenium Manager 自动管理 ChromeDriver...");
            WebDriver driver = new ChromeDriver(options);
            log.info("Selenium Manager 自动管理 ChromeDriver 成功");
            return driver;
        } catch (Exception e) {
            log.warn("Selenium Manager 创建 ChromeDriver 失败（{}），尝试使用 WebDriverManager 回退", e.getMessage());
        }
        // 方案三：回退到 WebDriverManager
        try {
            WebDriverManager.chromedriver().setup();
            log.info("WebDriverManager 成功设置 ChromeDriver");
            return new ChromeDriver(options);
        } catch (Exception e) {
            log.error("WebDriverManager 设置 ChromeDriver 也失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "ChromeDriver 初始化失败: " + e.getMessage());
        }
    }

    /**
     * 查找本地已安装的 chromedriver 路径
     * 查找顺序：系统属性 webdriver.chrome.driver → PATH 环境变量中的 chromedriver
     */
    private static String resolveLocalChromeDriverPath() {
        // 1. 检查系统属性是否已指定路径
        String driverPath = System.getProperty("webdriver.chrome.driver");
        if (StrUtil.isNotBlank(driverPath) && new File(driverPath).exists()) {
            return driverPath;
        }
        // 2. 尝试从 PATH 中查找 chromedriver
        String pathEnv = System.getenv("PATH");
        if (StrUtil.isNotBlank(pathEnv)) {
            // Windows 查找 chromedriver.exe，Linux 查找 chromedriver
            String driverName = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "chromedriver.exe" : "chromedriver";
            for (String dir : pathEnv.split(File.pathSeparator)) {
                File candidate = new File(dir, driverName);
                if (candidate.exists() && candidate.canExecute()) {
                    return candidate.getAbsolutePath();
                }
            }
        }
        return null;
    }

    /**
     * 构建 Chrome 选项
     */
    private static ChromeOptions buildChromeOptions(int width, int height) {
        ChromeOptions options = new ChromeOptions();
        // 无头模式
        options.addArguments("--headless");
        // 禁用GPU（在某些环境下避免问题）
        options.addArguments("--disable-gpu");
        // 禁用沙盒模式（Docker环境需要）
        options.addArguments("--no-sandbox");
        // 禁用开发者shm使用
        options.addArguments("--disable-dev-shm-usage");
        // 设置窗口大小
        options.addArguments(String.format("--window-size=%d,%d", width, height));
        // 禁用扩展
        options.addArguments("--disable-extensions");
        // 设置用户代理
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        return options;
    }


    /**
     * 保存图片到文件
     *
     * @param imageBytes
     * @param imagePath
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败,{}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // 等待 document.readyState 为complete
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                            .equals("complete")
            );
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }


}

