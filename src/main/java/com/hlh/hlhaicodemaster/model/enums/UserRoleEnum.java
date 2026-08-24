package com.hlh.hlhaicodemaster.model.enums;

import cn.hutool.core.util.ObjUtil;
import com.hlh.hlhaicodemaster.annotation.AuthCheck;
import lombok.Getter;

@Getter
public enum UserRoleEnum {
    // text 是中文描述/显示名；
    // 真正业务中用的经常是根据 value 来匹配和获取枚举对象，即USER、ADMIN。
    USER("用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;

    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum anEnum : UserRoleEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
