package com.hlh.hlhaicodemaster.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


/**
 * 构建Vue Project 项目 (注意环境问题！！！已更改命令 npm -> npm.cmd（如果是windows）)
 */
@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 异步构建 Vue 项目（不阻塞主流程）
     *
     * @param projectPath 项目路径
     */
    public void buildProjectAsync(String projectPath) {
        // 在单独的线程中执行构建，避免阻塞主流程
        // 使用虚拟线程
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        buildProject(projectPath);
                    } catch (Exception e) {
                        log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
                    }
                });
    }


    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在，{}", projectPath);
            return false;
        }
        //检查是否有 package.json 文件
        File packageJsonFile = new File(projectDir, "package.json");
        if (!packageJsonFile.exists()) {
            log.error("项目目录下不存在 package.json 文件，{}", projectPath);
            return false;
        }
        log.info("开始构建 Vue 项目: {}", projectPath);
        //执行 npm install
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败，{}", projectPath);
            return false;
        }
        //执行 npm run build
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败，{}", projectPath);
            return false;
        }
        //验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists() || !distDir.isDirectory()) {
            log.error("构建成功，但 dist 目录未生成，{}", projectPath);
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录：{}", projectPath);
        return true;
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180); // 3分钟超时
    }

    // 根据操作系统更改命令
    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    /**
     * 操作系统检测
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            ProcessBuilder processBuilder = new ProcessBuilder(command.split("\\s+"));
            processBuilder.directory(workingDir);
            Process process = processBuilder.start();
            // 并发消费 stdout 和 stderr，避免输出缓冲区满导致子进程阻塞，同时保留完整输出用于失败排查
            CompletableFuture<String> stdoutFuture = readStreamAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = readStreamAsync(process.getErrorStream());
            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            } else {
                log.error("命令执行失败，退出码: {}，命令: {}\n标准输出:\n{}\n错误输出:\n{}", exitCode, command, stdout, stderr);
                return false;
            }
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return false;
        }
    }

    /**
     * 异步读取进程输出流（按平台默认控制台编码读取，Windows 中文环境通常为 GBK）
     */
    private CompletableFuture<String> readStreamAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sb = new StringBuilder();
            // Windows 控制台默认 GBK，Linux/Mac 默认 UTF-8
            String charsetName = isWindows() ? "GBK" : StandardCharsets.UTF_8.name();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charsetName))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (Exception e) {
                log.warn("读取命令输出失败: {}", e.getMessage());
            }
            return sb.toString();
        });
    }
}