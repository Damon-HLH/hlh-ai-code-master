package com.hlh.hlhaicodemaster.core.saver;

import cn.hutool.core.util.StrUtil;
import com.hlh.hlhaicodemaster.ai.model.HtmlCodeResult;
import com.hlh.hlhaicodemaster.exception.BusinessException;
import com.hlh.hlhaicodemaster.exception.ErrorCode;
import com.hlh.hlhaicodemaster.model.enums.CodeGenTypeEnum;

public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {

    /**
     * 获取代码生成类型
     *
     * @return 代码生成类型枚举
     */
    @Override
    public CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.HTML;
    }

    /**
     * 保存文件
     *
     * @param result
     * @param baseDirPath
     */
    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    /**
     * 验证输入参数，在父类基础上增加 HTML 代码的非空验证
     *
     * @param result
     */
    @Override
    protected void validateInput(HtmlCodeResult result) {
        super.validateInput(result);
        // HTML 代码不能为空
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML 代码不能为空");
        }
    }

}
