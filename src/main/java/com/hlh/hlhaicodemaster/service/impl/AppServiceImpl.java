package com.hlh.hlhaicodemaster.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hlh.hlhaicodemaster.ai.AiCodeGenTypeRoutingService;
import com.hlh.hlhaicodemaster.ai.AiCodeGenTypeRoutingServiceFactory;
import com.hlh.hlhaicodemaster.constant.AppConstant;
import com.hlh.hlhaicodemaster.core.AiCodeGeneratorFacade;
import com.hlh.hlhaicodemaster.core.builder.VueProjectBuilder;
import com.hlh.hlhaicodemaster.core.handler.StreamHandlerExecutor;
import com.hlh.hlhaicodemaster.exception.BusinessException;
import com.hlh.hlhaicodemaster.exception.ErrorCode;
import com.hlh.hlhaicodemaster.exception.ThrowUtils;
import com.hlh.hlhaicodemaster.model.dto.app.AppAddRequest;
import com.hlh.hlhaicodemaster.model.dto.app.AppQueryRequest;
import com.hlh.hlhaicodemaster.model.entity.User;
import com.hlh.hlhaicodemaster.model.enums.ChatHistoryMessageTypeEnum;
import com.hlh.hlhaicodemaster.model.enums.CodeGenTypeEnum;
import com.hlh.hlhaicodemaster.model.vo.AppVO;
import com.hlh.hlhaicodemaster.model.vo.UserVO;
import com.hlh.hlhaicodemaster.service.ChatHistoryService;
import com.hlh.hlhaicodemaster.service.ScreenshotService;
import com.hlh.hlhaicodemaster.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.hlh.hlhaicodemaster.model.entity.App;
import com.hlh.hlhaicodemaster.mapper.AppMapper;
import com.hlh.hlhaicodemaster.service.AppService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/Damon-HLH">hlh</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;


    /**
     * 通过对话生成网页代码应用
     *
     * @param appId
     * @param message
     * @param loginUser
     * @return
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        //1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        //2. 查询应用信息
        App app = getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3. 权限校验，仅本人可以和自己的应用对话
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "没有权限访问该应用");
        }
        //4. 获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型错误");
        }
        //5. 在调用 AI 前，先保存用户消息到数据库中
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        //6. 调用 AI 生成代码（流式）
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        //7. 收集 AI 响应的内容，并且在完成后保存记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型 (多例模式)
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService().routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }


    /**
     * 应用部署，返回应用可访问的URL
     *
     * @param appId
     * @param loginUser
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有部署key deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建代码源目录路径（之前在生成代码时，已经保存了）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查保存代码文件的源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }

        // 7. Vue项目特殊处理，执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // 不用异步，用户点击构建按钮后的，需要立即返回结果给用户
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 构建项目失败，请重试");
            // 构建完成后，需要将构建的文件复制到目录
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }

        // 8. 复制代码文件从保存代码目录到部署目录中
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 9. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey); // 更新部署秘钥
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 返回可访问的 URL 地址，给用户
        // 10. 构建应用访问 URL  生产环境地址
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);
//        String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 11. 异步生成截图并且更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 删除应用：删除数据库记录，并清理生成的代码目录和部署目录（尽力删除，文件夹删除失败不影响删除结果）
     *
     * @param app 应用信息（需要包含 codeGenType 和 deployKey）
     * @return 数据库删除结果
     */
    @Override
    public boolean deleteApp(App app) {
        // 1. 先删除数据库记录，删除成功后再清理文件夹（文件夹删除失败不回滚，不阻塞删除结果）
        boolean result = this.removeById(app.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除应用失败");
        // 2. 删除生成的应用代码目录：tmp/code_output/{codeGenType}_{appId}
        deleteCodeGenDirQuietly(app);
        // 3. 删除部署的应用代码目录：tmp/code_deploy/{deployKey}（仅部署过的应用存在）
        deleteDeployDirQuietly(app);
        return result;
    }

    /**
     * 删除应用对应的生成代码目录：tmp/code_output/{codeGenType}_{appId}
     * 三种代码生成方案（HTML、MULTI_FILE、VUE_PROJECT）的目录命名规则统一为 {类型}_{应用ID}，
     * 分别为 html_{appId}、multi_file_{appId}、vue_project_{appId}
     */
    private void deleteCodeGenDirQuietly(App app) {
        String codeGenType = app.getCodeGenType();
        // codeGenType 为空说明应用还没有生成过代码，代码目录不存在，直接跳过
        if (StrUtil.isBlank(codeGenType)) {
            log.info("应用 codeGenType 为空，跳过生成代码目录删除，appId: {}", app.getId());
            return;
        }
        String sourceDirName = codeGenType + "_" + app.getId();
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        deleteAppDirQuietly(sourceDirPath, AppConstant.CODE_OUTPUT_ROOT_DIR, app.getId());
    }

    /**
     * 删除应用对应的部署目录：tmp/code_deploy/{deployKey}
     * 部署目录只有在应用部署后才会复制生成，未部署（deployKey 为空）则跳过
     */
    private void deleteDeployDirQuietly(App app) {
        String deployKey = app.getDeployKey();
        // deployKey 为空说明应用从未部署过，部署目录不存在，直接跳过
        if (StrUtil.isBlank(deployKey)) {
            log.info("应用 deployKey 为空，跳过部署目录删除，appId: {}", app.getId());
            return;
        }
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        deleteAppDirQuietly(deployDirPath, AppConstant.CODE_DEPLOY_ROOT_DIR, app.getId());
    }

    /**
     * 安全删除应用相关目录（尽力删除，不抛异常，不影响删除应用主流程）
     * 文件夹校验：目录必须位于受管理的根目录内（防止路径异常导致误删），且存在、是目录，才执行删除
     *
     * @param dirPath 待删除目录路径
     * @param rootDir 受管理的根目录（生成目录或部署目录）
     * @param appId   应用 ID（仅用于日志）
     */
    private void deleteAppDirQuietly(String dirPath, String rootDir, Long appId) {
        try {
            File dir = new File(dirPath);
            // 校验目录必须位于受管理的根目录内，防止路径异常导致误删其他目录
            Path normalizedDir = dir.toPath().normalize().toAbsolutePath();
            Path normalizedRoot = new File(rootDir).toPath().normalize().toAbsolutePath();
            if (!normalizedDir.startsWith(normalizedRoot)) {
                log.warn("目录不在受管理的根目录内，跳过删除，appId: {}, dir: {}", appId, dirPath);
                return;
            }
            // 校验目录存在且是目录，不存在则跳过（代码可能还没生成、或从未部署）
            if (!dir.exists() || !dir.isDirectory()) {
                log.info("应用相关目录不存在，跳过删除，appId: {}, dir: {}", appId, dirPath);
                return;
            }
            boolean deleted = FileUtil.del(dir);
            if (deleted) {
                log.info("应用相关目录删除成功，appId: {}, dir: {}", appId, dirPath);
            } else {
                log.warn("应用相关目录删除失败，可能存在文件占用，appId: {}, dir: {}", appId, dirPath);
            }
        } catch (Exception e) {
            log.error("删除应用相关目录发生异常，不影响应用删除结果，appId: {}, dir: {}, 错误: {}", appId, dirPath, e.getMessage(), e);
        }
    }

    /**
     * 异步生成应用截图并更新数据库封面
     *
     * @param appId
     * @param appDeployUrl
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appDeployUrl) {
        // 使用虚拟线程并执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成并上传截图
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appDeployUrl);
            // 更新数据库的封面
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面失败");
        });
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            // 脱敏方法
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)  // %xxx$
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }


    /**
     * 根据应用列表获取应用VO列表
     * 1. 先收集所有userId到集合中
     * 2. 根据userId集合批量查询所有用户信息
     * 3. 构建Map映射关系userId =>UserVO
     * 4. 一次性组装所有AppVO，根据userId从Map中取到需要的用户信息
     *
     * @param appList
     * @return
     */
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    /**
     * 删除应用时，关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }


}