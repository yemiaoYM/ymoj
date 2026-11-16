import request from '@/api/request';
import type { ApiResult } from '@/types';
import type {
  CaptchaVO,
  LoginDTO,
  RegisterDTO,
  ForgetPasswordDTO,
  ResetPasswordDTO,
} from '@/types/user/auth';

/**
 * 获取验证码
 * @returns 包含验证码的完整响应
 */
export function getCaptcha() {
  return request<ApiResult<CaptchaVO>>({
    url: '/captchaImage',
    method: 'GET',
  });
}

/**
 * 登录
 * @param data 登录信息
 * @returns 包含登录令牌的完整响应
 */
export function login(data: LoginDTO) {
  return request<ApiResult<string>>({
    url: '/login',
    method: 'POST',
    data
  });
}

/**
 * 注册
 * @param data 注册信息
 * @returns 操作结果，不包含业务数据
 */
export function register(data: RegisterDTO) {
  return request<ApiResult<void>>({
    url: '/register',
    method: 'POST',
    data
  });
}

/**
 * 忘记密码-发送验证码
 * @param data 忘记密码信息
 * @returns 包含验证码会话标识 uuid 的完整响应
 */
export function forgetPasswordSendCode(data: ForgetPasswordDTO) {
  return request<ApiResult<string>>({
    url: '/forget-code',
    method: 'POST',
    data
  });
}

/**
 * 忘记密码-重置密码
 * @param data 忘记密码信息
 * @returns 操作结果，不包含业务数据
 */
export function forgetPasswordResetPassword(data: ResetPasswordDTO) {
  return request<ApiResult<void>>({
    url: '/forget-password',
    method: 'PUT',
    data
  });
}

/**
 * 登出
 * @returns 操作结果，不包含业务数据
 */
export function logout() {
  return request<ApiResult<void>>({
    url: '/logout',
    method: 'GET',
  });
}
