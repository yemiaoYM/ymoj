/**
 * 登录参数
 */
export type LoginDTO = {
  /**
   * 用户名
   */
  username: string;
  /**
   * 密码
   */
  password: string;
  /**
   * 验证码
   */
  code: string;
  /**
   * 验证码唯一标识
   */
  uuid: string;
};

/**
 * 注册参数
 */
export type RegisterDTO = {
  /**
   * 用户名
   */
  username: string;
  /**
   * 密码
   */
  password: string;
  /**
   * 确认密码
   */
  rePassword: string;
  /**
   * 电子邮箱
   */
  email: string;
  /**
   * 验证码
   */
  code: string;
  /**
   * 验证码唯一标识
   */
  uuid: string;
};

/**
 * 找回密码验证码参数
 */
export type ForgetPasswordDTO = {
  /**
   * 用户名
   */
  username: string;
  /**
   * 电子邮箱
   */
  email: string;
};

/**
 * 重置密码参数
 */
export type ResetPasswordDTO = {
  /**
   * 用户名
   */
  username: string;
  /**
   * 新密码
   */
  password: string;
  /**
   * 确认密码
   */
  rePassword: string;
  /**
   * 验证码
   */
  code: string;
  /**
   * 验证码唯一标识
   */
  uuid: string;
};

/**
 * 图片验证码
 */
export type CaptchaVO = {
  /**
   * 唯一标识
   */
  uuid: string;
  /**
   * 验证码图片（Base64）
   */
  image: string;
};
