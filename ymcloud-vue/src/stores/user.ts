import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { getUserProfile, login, logout, register } from '@/api/api';
import type { UserProfile } from '@/type/types';
import Storage from '@/utils/storage';

/**
 * 用户身份、会话及授权状态
 */
export const useUserStore = defineStore('userInfo', () => {
  // Token 存在而资料为空时尚未完成身份加载，不能据此授予权限
  const token = ref(Storage.get<string>('Authorization') || '');
  const user = ref<UserProfile | null>(null);
  const sessionExpired = ref(false);
  const isLogin = computed(() => !!token.value && user.value !== null);
  const isSuperAdmin = computed(() => isLogin.value && user.value?.id === 1);
  let profileRequest: Promise<void> | undefined;

  /**
   * 判断单项权限，只有ID为1的超级管理员拥有全部权限
   */
  const hasPermission = (permission: string): boolean => {
    if (!permission.trim() || !token.value || !user.value) {
      return false;
    }
    return isSuperAdmin.value || (permission !== '*:*:*' && user.value.permissions.includes(permission));
  };

  /**
   * 判断是否拥有任意一项权限
   */
  const hasAnyPermission = (permissions: string[]): boolean => {
    return permissions.some(hasPermission);
  };

  /**
   * 判断是否拥有全部指定权限，空要求不授予访问
   */
  const hasEveryPermission = (permissions: string[]): boolean => {
    return permissions.length > 0 && permissions.every(hasPermission);
  };

  /**
   * 判断实际角色成员关系，角色名称不代表超级管理员身份
   */
  const hasRole = (role: string): boolean => {
    return !!token.value && !!role && !!user.value && user.value.roles.includes(role);
  };

  /**
   * 判断是否拥有任意一个角色
   */
  const hasAnyRole = (roles: string[]): boolean => {
    return roles.some(hasRole);
  };

  /**
   * 判断是否拥有全部指定角色，空要求不授予访问
   */
  const hasEveryRole = (roles: string[]): boolean => {
    return roles.length > 0 && roles.every(hasRole);
  };

  /**
   * 清除身份及令牌，使尚未完成的资料请求失效
   * @param expired 是否由认证失效触发，供页面决定返回登录页
   */
  const clearSession = (expired = false) => {
    profileRequest = undefined;
    user.value = null;
    token.value = '';
    sessionExpired.value = expired;
    Storage.remove('Authorization');
  };

  /**
   * 获取当前用户资料，同一会话中的并发调用共享请求
   * @param force 是否重新获取已加载的资料
   * @returns 资料请求完成通知
   */
  const getUserInfo = (force = false): Promise<void> => {
    if (profileRequest) {
      return profileRequest;
    }
    if (!token.value || (user.value && !force)) {
      return Promise.resolve();
    }
    const request = getUserProfile().then(response => {
      if (profileRequest === request) {
        user.value = response.data.data;
      }
    }).finally(() => {
      if (profileRequest === request) {
        profileRequest = undefined;
      }
    });
    profileRequest = request;
    return request;
  };

  /**
   * 登录并加载新会话的身份资料
   */
  const doLogin = async (username: string, password: string, code: string, uuid: string) => {
    const response = await login({ username, password, code, uuid });
    clearSession();
    token.value = response.data.data;
    Storage.set('Authorization', token.value);
    await getUserInfo();
  };

  /**
   * 注册用户
   */
  const doRegister = (username: string, password: string, rePassword: string, email: string, code: string, uuid: string) => {
    return register({ username, password, rePassword, email, code, uuid });
  };

  /**
   * 退出当前会话，请求失败时仍清除本地身份
   */
  const doLogout = async () => {
    const currentToken = token.value;
    try {
      await logout();
    } finally {
      if (token.value === currentToken) {
        clearSession();
      }
    }
  };

  return {
    user,
    token,
    isLogin,
    isSuperAdmin,
    sessionExpired,
    clearSession,
    doLogin,
    getUserInfo,
    doLogout,
    doRegister,
    hasPermission,
    hasAnyPermission,
    hasEveryPermission,
    hasRole,
    hasAnyRole,
    hasEveryRole,
  };
});
