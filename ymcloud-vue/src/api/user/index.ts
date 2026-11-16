import request from '@/api/request';
import type { ApiResult, PageVO } from '@/types';
import type {
  UserProfile,
  UserInfoVO,
  UserCalendarHeatmapVO,
  UpdateUserProfileDTO,
  UpdateUserPasswordDTO,
  UpdateUserEmailDTO,
  UserAcRankVO,
  AdminUserPageQuery,
  User,
  UserDTO,
} from '@/types/user';

/**
 * 获取当前用户基本信息
 * @returns 包含当前用户基本信息的完整响应
 */
export function getUserProfile() {
  return request<ApiResult<UserProfile>>({
    url: '/user/profile',
    method: 'GET',
  });
}

/**
 * 获取用户信息
 * @param username 用户名
 * @returns 包含用户信息的完整响应
 */
export function getUserInfoByUsername(username: string) {
  return request<ApiResult<UserInfoVO>>({
    url: '/public/user-info',
    method: 'GET',
    params: { username }
  });
}

/**
 * 获取用户日历热力图
 * @param username 用户名
 * @returns 包含用户日历热力图的完整响应
 */
export function getUserCalendarHeatmap(username: string) {
  return request<ApiResult<UserCalendarHeatmapVO>>({
    url: '/public/user-calendar-heatmap',
    method: 'GET',
    params: {
      username
    }
  });
}

/**
 * 更新用户资料
 * @param data 用户资料
 * @returns 操作结果，不包含业务数据
 */
export function updateUserProfile(data: UpdateUserProfileDTO) {
  return request<ApiResult<void>>({
    url: '/user/update-profile',
    method: 'PUT',
    data
  });
}

/**
 * 更新用户密码
 * @param data 用户密码
 * @returns 操作结果，不包含业务数据
 */
export function updateUserPassword(data: UpdateUserPasswordDTO) {
  return request<ApiResult<void>>({
    url: '/user/update-password',
    method: 'PUT',
    data
  });
}

/**
 * 更新用户邮箱
 * @param data 用户邮箱
 * @returns 操作结果，不包含业务数据
 */
export function updateUserEmail(data: UpdateUserEmailDTO) {
  return request<ApiResult<void>>({
    url: '/user/update-email',
    method: 'PUT',
    data
  });
}

/**
 * 获取用户AC排名
 * @returns 包含用户AC排名的完整响应
 */
export function getUserAcRank() {
  return request<ApiResult<UserAcRankVO[]>>({
    url: '/public/user-ac-rank',
    method: 'GET'
  });
}

/**
 * 获取用户列表
 * @param params 请求参数
 * @returns 包含用户列表的完整响应
 */
export function getUserTableList(params: AdminUserPageQuery) {
  return request<ApiResult<PageVO<User>>>({
    url: '/admin/user/list',
    method: 'GET',
    params
  });
}

/**
 * 获取用户信息
 * @param id 用户ID
 * @returns 包含用户信息的完整响应
 */
export function getUserById(id: number) {
  return request<ApiResult<User>>({
    url: `/admin/user/${id}`,
    method: 'GET',
  });
}

/**
 * 添加用户
 * @param data 用户信息
 * @returns 操作结果，不包含业务数据
 */
export function addUser(data: UserDTO) {
  return request<ApiResult<void>>({
    url: '/admin/user',
    method: 'POST',
    data
  });
}

/**
 * 更新用户
 * @param data 用户信息
 * @returns 操作结果，不包含业务数据
 */
export function updateUser(data: UserDTO) {
  return request<ApiResult<void>>({
    url: '/admin/user',
    method: 'PUT',
    data
  });
}

/**
 * 更新用户状态
 * @param id 用户ID
 * @param status 状态
 * @returns 操作结果，不包含业务数据
 */
export function changeUserStatus(id: number, status: string) {
  const data = {
    id,
    status
  };
  return request<ApiResult<void>>({
    url: '/admin/user/status',
    method: 'PUT',
    data
  });
}

/**
 * 删除用户
 * @param id 用户ID
 * @returns 操作结果，不包含业务数据
 */
export function deleteUserByIds(id: number | number[]) {
  return request<ApiResult<void>>({
    url: `/admin/user/${id}`,
    method: 'DELETE',
  });
}

/**
 * 重置用户密码
 * @param id 用户ID
 * @param password 新密码
 * @returns 操作结果，不包含业务数据
 */
export function resetUserPassword(id: number, password: string) {
  const data = {
    id,
    password
  };
  return request<ApiResult<void>>({
    url: '/admin/user/reset-password',
    method: 'PUT',
    data
  });
}

/**
 * 上传当前用户确认裁剪后的头像
 * @param file 裁剪后的 PNG 图片
 * @returns 操作结果，不包含业务数据
 */
export function uploadAvatar(file: Blob) {
  const data = new FormData();
  data.append('file', file, 'avatar.png');
  return request<ApiResult<void>>({
    url: '/user/upload-avatar',
    method: 'POST',
    data,
    timeout: 120000,
  });
}
