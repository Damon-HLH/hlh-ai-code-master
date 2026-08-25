package com.hlh.hlhaicodemaster.service;

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
     * 应用部署，返回应用可访问的URL
     *
     * @param appId
     * @param loginUser
     */
    String deployApp(Long appId,User loginUser);

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
