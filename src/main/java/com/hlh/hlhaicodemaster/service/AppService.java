package com.hlh.hlhaicodemaster.service;

import com.hlh.hlhaicodemaster.model.dto.app.AppAddRequest;
import com.hlh.hlhaicodemaster.model.dto.app.AppQueryRequest;
import com.hlh.hlhaicodemaster.model.entity.User;
import com.hlh.hlhaicodemaster.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.hlh.hlhaicodemaster.model.entity.App;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/Damon-HLH">hlh</a>
 */
public interface AppService extends IService<App> {

    /**
     * 通过对话生成网页代码应用
     * @param appId
     * @param message
     * @param loginUser
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 创建应用
     * @param appAddRequest
     * @param loginUser
     * @return
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 应用部署，返回应用可访问的URL
     *
     * @param appId
     * @param loginUser
     */
    String deployApp(Long appId,User loginUser);

    /**
     * 删除应用：删除数据库记录，并清理生成的代码目录和部署目录（尽力删除，文件夹删除失败不影响删除结果）
     *
     * @param app 应用信息（需要包含 codeGenType 和 deployKey）
     * @return 数据库删除结果
     */
    boolean deleteApp(App app);

    /**
     * 异步生成应用截图并更新封面
     * @param appId
     * @param appDeployUrl
     */
    void generateAppScreenshotAsync(Long appId, String appDeployUrl);

    /**
     * 获取应用封装类
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * 构造应用查询条件
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 分页查询应用时，需要额外封装获取应用的创建用户信息
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);

}
