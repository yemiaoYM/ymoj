import type { PageQuery } from '@/types';

/**
 * 后台用户资料
 */
export type User = {
  /**
   * 用户ID
   */
  id: number;
  /**
   * 登录用户名（唯一）
   */
  username: string;
  /**
   * 响应中已清除密码，固定为 null
   */
  password: null;
  /**
   * 昵称
   */
  nickname: string | null;
  /**
   * 性别：保密、男、女（后端枚举序列化值）
   */
  sex: string;
  /**
   * 邮箱
   */
  email: string;
  /**
   * 头像 URL
   */
  avatar: string | null;
  /**
   * 学校
   */
  school: string | null;
  /**
   * 个性签名
   */
  signature: string | null;
  /**
   * 博客链接
   */
  blog: string | null;
  /**
   * 最后登录 IP
   */
  loginIp: string | null;
  /**
   * 最后登录时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  loginDate: string | null;
  /**
   * 账号状态：正常、停用（后端枚举序列化值）
   */
  status: string;
  /**
   * 创建时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  createdTime: string;
  /**
   * 更新时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  updatedTime: string;
};

/**
 * 保存后台用户参数
 */
export type UserDTO = {
  /**
   * 用户id
   */
  id: number | null;
  /**
   * 登录用户名（唯一）
   */
  username: string;
  /**
   * 密码
   */
  password: string | null;
  /**
   * 昵称
   */
  nickname: string | null;
  /**
   * 性别：保密、男、女（后端枚举序列化值）
   */
  sex: string;
  /**
   * 邮箱
   */
  email: string;
  /**
   * 头像 URL
   */
  avatar: string | null;
  /**
   * 学校
   */
  school: string | null;
  /**
   * 个性签名
   */
  signature: string | null;
  /**
   * 博客链接
   */
  blog: string | null;
  /**
   * 账号状态：正常、停用（后端枚举序列化值）
   */
  status: string;
};

/**
 * 当前登录用户资料
 */
export type UserProfile = {
  /**
   * 用户ID
   */
  id: number;
  /**
   * 用户名
   */
  username: string;
  /**
   * 昵称
   */
  nickname: string | null;
  /**
   * 性别
   */
  sex: string;
  /**
   * 电子邮箱
   */
  email: string;
  /**
   * 头像
   */
  avatar: string | null;
  /**
   * 学校
   */
  school: string | null;
  /**
   * 个性签名
   */
  signature: string | null;
  /**
   * 博客
   */
  blog: string | null;
  /**
   * 登录时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  loginDateTime: string | null;
  /**
   * IP地址
   */
  ipaddr: string | null;
  /**
   * 登录地点
   */
  loginLocation: string | null;
  /**
   * 浏览器类型
   */
  browser: string | null;
  /**
   * 操作系统
   */
  os: string | null;
  /**
   * 用户角色信息
   */
  roles: string[];
  /**
   * 用户权限信息
   */
  permissions: string[];
};

/**
 * 公开用户主页资料
 */
export type UserInfoVO = {
  /**
   * 用户名
   */
  username: string;
  /**
   * 昵称
   */
  nickname: string | null;
  /**
   * 性别
   */
  sex: string;
  /**
   * 电子邮箱
   */
  email: string;
  /**
   * 头像
   */
  avatar: string | null;
  /**
   * 学校
   */
  school: string | null;
  /**
   * 个性签名
   */
  signature: string | null;
  /**
   * 博客
   */
  blog: string | null;
  /**
   * 最后登录时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  loginDate: string | null;
  /**
   * 已解决题目的题号
   */
  problemId: string[];
  /**
   * 已解决题目个数
   */
  ac: number;
  /**
   * 总提交个数
   */
  total: number;
};

/**
 * 用户通过题数排名
 */
export type UserAcRankVO = {
  /**
   * 用户名
   */
  username: string;
  /**
   * 用户昵称
   */
  nickname: string | null;
  /**
   * 用户头像
   */
  avatar: string | null;
  /**
   * ac题目数量
   */
  count: number;
};

/**
 * 修改个人资料参数
 */
export type UpdateUserProfileDTO = {
  /**
   * 用户昵称
   */
  nickname?: string | null;
  /**
   * 个性签名
   */
  signature?: string | null;
  /**
   * 性别
   */
  sex?: string;
  /**
   * 学校
   */
  school?: string | null;
  /**
   * 个人博客
   */
  blog?: string | null;
};

/**
 * 修改密码参数
 */
export type UpdateUserPasswordDTO = {
  /**
   * 当前密码
   */
  password: string;
  /**
   * 新密码
   */
  newPassword: string;
  /**
   * 确认密码
   */
  rePassword: string;
};

/**
 * 修改邮箱参数
 */
export type UpdateUserEmailDTO = {
  /**
   * 当前密码
   */
  password: string;
  /**
   * 新邮箱
   */
  newEmail: string;
};

/**
 * 后台用户查询条件
 */
export type AdminUserPageQuery = PageQuery & {
  /**
   * 用户名
   */
  username?: string | null;
  /**
   * 邮箱
   */
  email?: string | null;
  /**
   * 状态
   */
  status?: number | null;
};

/**
 * 提交日历，日期为 yyyy-MM-dd
 */
export type UserCalendarHeatmapVO = {
  date: string;
  dataList: { date: string; count: number }[];
};
