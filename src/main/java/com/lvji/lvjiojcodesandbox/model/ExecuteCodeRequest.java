package com.lvji.lvjiojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 代码沙箱请求包装类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeRequest {

    /**
     * 用户代码
     */
    private String code;

    /**
     * 用户使用的编程语言
     */
    private String language;

    /**
     * 判题输入用例
     */
    private List<String> inputList;

}
