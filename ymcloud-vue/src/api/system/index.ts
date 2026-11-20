import request from '@/api/request';
import type { ApiResult } from '@/types';
import type {
  SystemSettingVO,
  SystemSettingDTO,
  DashboardStatsVO,
  SubmissionChartVO,
  Server,
  UserOnlineVO,
} from '@/types/system';

/**
 * 获取公开的系统配置
 * @returns 包含公开的系统配置的完整响应
 */
export function getPublicSystemSetting() {
  return request<ApiResult<SystemSettingVO>>({
    url: '/public/system-setting',
    method: 'GET',
  });
}

/**
 * 获取后台系统配置
 * @returns 包含后台系统配置的完整响应
 */
export function getAdminSystemSetting() {
  return request<ApiResult<SystemSettingVO>>({
    url: '/admin/system-setting',
    method: 'GET',
  });
}

/**
 * 保存后台系统配置
 * @param data 系统配置
 * @returns 操作结果，不包含业务数据
 */
export function updateSystemSetting(data: SystemSettingDTO) {
  return request<ApiResult<void>>({
    url: '/admin/system-setting',
    method: 'PUT',
    data
  });
}

/**
 * 获取公开轮播配置
 * @returns 包含公开轮播配置的完整响应
 */
export function getPublicCarousel() {
  return request<ApiResult<string[]>>({ url: '/public/carousel' });
}

/**
 * 保存轮播图片顺序
 * @param imgApiUrl 有序的轮播图片访问地址
 * @returns 操作结果，不包含业务数据
 */
export function saveCarousel(imgApiUrl: string[]) {
  return request<ApiResult<void>>({
    url: '/admin/system-setting/carousel',
    method: 'PUT',
    data: { imgApiUrl },
  });
}

/**
 * 上传轮播图片
 * @param file 上传文件
 * @returns 包含已保存地址及本次新图片地址的列表，新地址位于末尾
 */
export function uploadCarousel(file: File) {
  const data = new FormData();
  data.append('file', file);
  return request<ApiResult<string[]>>({
    url: '/admin/system-setting/carousel/image',
    method: 'POST',
    data,
    timeout: 120000,
  });
}

/**
 * 获取管理员首页统计数据
 * @returns 包含管理员首页统计数据的完整响应
 */
export function getAdminDashboardStats() {
  return request<ApiResult<DashboardStatsVO>>({
    url: '/admin/dashboard/stats',
    method: 'GET',
  });
}

/**
 * 获取最近两周提交数据折线图
 * @returns 包含最近两周提交数据折线图的完整响应
 */
export function getAdminSubmissionChart() {
  return request<ApiResult<SubmissionChartVO>>({
    url: '/admin/dashboard/submission-chart',
    method: 'GET',
  });
}

/**
 * 获取服务器配置
 * @returns 包含服务器配置的完整响应
 */
export function getServerConfig() {
  return request<ApiResult<Server>>({
    url: '/monitor/server',
    method: 'GET',
  });
}

/**
 * 获取在线用户列表
 * @returns 包含在线用户列表的完整响应
 */
export function getUserOnlineList() {
  return request<ApiResult<UserOnlineVO[]>>({
    url: '/monitor/online/list',
    method: 'GET'
  });
}

/**
 * 强制下线
 * @param token 会话编号
 * @returns 操作结果，不包含业务数据
 */
export function forceOffline(token: string) {
  return request<ApiResult<void>>({
    url: '/monitor/online/' + token,
    method: 'DELETE',
  });
}
