import request from '@/api/request';
import type { ApiResult, PageVO } from '@/types';
import type { MenuRoute, RolePageQuery, RoleVO, RoleDTO, AuthVO, AuthDTO } from '@/types/system/rbac';

/**
 * 获取菜单列表
 * @returns 包含菜单列表的完整响应
 */
export function getMenus() {
  return request<ApiResult<MenuRoute[]>>({
    url: '/admin/menu',
    method: 'GET',
  });
}

/**
 * 分页查询角色
 * @param params 查询条件
 * @returns 包含角色的完整响应
 */
export function getRoleList(params: RolePageQuery) {
  return request<ApiResult<PageVO<RoleVO>>>({
    url: '/admin/role/list',
    method: 'GET',
    params,
  });
}

/**
 * 根据ID查询角色
 * @param id 角色 ID
 * @returns 包含角色详情的完整响应
 */
export function getRoleById(id: number) {
  return request<ApiResult<RoleVO>>({
    url: `/admin/role/${id}`,
    method: 'GET',
  });
}

/**
 * 新增角色
 * @param data 请求业务数据
 * @returns 操作结果，不包含业务数据
 */
export function addRole(data: RoleDTO) {
  return request<ApiResult<void>>({
    url: '/admin/role',
    method: 'POST',
    data,
  });
}

/**
 * 修改角色
 * @param id 角色 ID
 * @param data 请求业务数据
 * @returns 操作结果，不包含业务数据
 */
export function updateRole(id: number, data: RoleDTO) {
  return request<ApiResult<void>>({
    url: `/admin/role/${id}`,
    method: 'PUT',
    data,
  });
}

/**
 * 删除角色
 * @param id 角色 ID
 * @returns 操作结果，不包含业务数据
 */
export function deleteRole(id: number) {
  return request<ApiResult<void>>({
    url: `/admin/role/${id}`,
    method: 'DELETE',
  });
}

/**
 * 查询权限选项
 * @returns 包含权限选项的完整响应
 */
export function getRoleAuthOptions() {
  return request<ApiResult<AuthVO[]>>({
    url: '/admin/role/auth-options',
    method: 'GET',
  });
}

/**
 * 查询角色权限ID
 * @param id 角色 ID
 * @returns 包含角色权限ID的完整响应
 */
export function getRoleAuthIds(id: number) {
  return request<ApiResult<number[]>>({
    url: `/admin/role/${id}/auth`,
    method: 'GET',
  });
}

/**
 * 分配角色权限
 * @param id 角色 ID
 * @param authIds 授权权限 ID 列表
 * @returns 操作结果，不包含业务数据
 */
export function updateRoleAuths(id: number, authIds: number[]) {
  return request<ApiResult<void>>({
    url: `/admin/role/${id}/auth`,
    method: 'PUT',
    data: { authIds },
  });
}

/**
 * 查询用户角色ID
 * @param id 用户 ID
 * @returns 包含用户角色ID的完整响应
 */
export function getUserRoleIds(id: number) {
  return request<ApiResult<number[]>>({
    url: `/admin/user/${id}/roles`,
    method: 'GET',
  });
}

/**
 * 查询用户角色选项，包含已分配的停用角色
 * @param id 用户 ID
 * @returns 包含用户角色选项，包含已分配的停用角色的完整响应
 */
export function getUserRoleOptions(id: number) {
  return request<ApiResult<RoleVO[]>>({
    url: `/admin/user/${id}/roles/options`,
    method: 'GET',
  });
}

/**
 * 分配用户角色
 * @param id 用户 ID
 * @param roleIds 角色 ID 列表
 * @returns 操作结果，不包含业务数据
 */
export function updateUserRoles(id: number, roleIds: number[]) {
  return request<ApiResult<void>>({
    url: `/admin/user/${id}/roles`,
    method: 'PUT',
    data: { roleIds },
  });
}

/**
 * 查询菜单权限
 * @returns 包含菜单权限的完整响应
 */
export function getAuthList() {
  return request<ApiResult<AuthVO[]>>({
    url: '/admin/auth/list',
    method: 'GET',
  });
}

/**
 * 新增菜单权限
 * @param data 请求业务数据
 * @returns 操作结果，不包含业务数据
 */
export function addAuth(data: AuthDTO) {
  return request<ApiResult<void>>({
    url: '/admin/auth',
    method: 'POST',
    data,
  });
}

/**
 * 修改菜单权限
 * @param id 权限 ID
 * @param data 请求业务数据
 * @returns 操作结果，不包含业务数据
 */
export function updateAuth(id: number, data: AuthDTO) {
  return request<ApiResult<void>>({
    url: `/admin/auth/${id}`,
    method: 'PUT',
    data,
  });
}

/**
 * 删除菜单权限
 * @param id 权限 ID
 * @returns 操作结果，不包含业务数据
 */
export function deleteAuth(id: number) {
  return request<ApiResult<void>>({
    url: `/admin/auth/${id}`,
    method: 'DELETE',
  });
}
