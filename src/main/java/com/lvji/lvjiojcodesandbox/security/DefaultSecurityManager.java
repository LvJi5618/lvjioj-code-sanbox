package com.lvji.lvjiojcodesandbox.security;

import java.security.Permission;

/**
 * 默认安全管理器
 */
public class DefaultSecurityManager extends SecurityManager{

    /**
     * 检查所有权限
     * @param perm
     */
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认不做任何权限限制");
        System.out.println(perm);
        // 继承父类的 checkPermission 方法会默认禁止所有权限
        //super.checkPermission(perm);
    }
}
