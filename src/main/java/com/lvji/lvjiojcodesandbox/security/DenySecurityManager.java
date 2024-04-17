package com.lvji.lvjiojcodesandbox.security;

import java.security.Permission;

/**
 * 禁止所有权限的安全管理器
 */
public class DenySecurityManager extends SecurityManager{

    /**
     * 限制所有权限
     * @param perm
     */
    @Override
    public void checkPermission(Permission perm) {
        throw new SecurityException("权限异常:" + perm.toString());
    }
}
