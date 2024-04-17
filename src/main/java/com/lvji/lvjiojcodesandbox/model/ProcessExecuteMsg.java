package com.lvji.lvjiojcodesandbox.model;

import lombok.Data;

/**
 * 进程执行信息
 */
@Data
public class ProcessExecuteMsg {

    private Integer exitValue;

    /**
     * 用户代码正常执行信息
     */
    private String message;

    /**
     * 错误执行信息
     */
    private String errorMessage;

    /**
     * 代码执行时间
     */
    private Long time;

    /**
     * 代码执行占用内存
     */
    private Long memory;
}
