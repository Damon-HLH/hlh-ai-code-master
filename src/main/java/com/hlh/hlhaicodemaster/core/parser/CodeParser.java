package com.hlh.hlhaicodemaster.core.parser;

/**
 * 代码解析器策略接口（策略模式）
 * 对应的子类，实现该接口，用于实现不同的代码解析方法（比如解析为HTML格式代码或者多文件格式的代码）
 * @param <T>
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容
     * @param codeContent 原始代码内容
     * @return 解析后的代码内容
     */
    T parseCode(String codeContent);
}
