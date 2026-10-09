import request from './request';
import type { RoleDTO, AuthDTO, RolePageQuery } from '@/type/rbac';

/**
 * 分页查询角色
 */
export function getRoleList(params: RolePageQuery) {
  return request({
    url: '/admin/role/list',
    method: 'GET',
    params,
  });
}

/**
 * 查询启用的角色选项
 */
export function getRoleOptions() {
  return request({
    url: '/admin/role/options',
    method: 'GET',
  });
}

/**
 * 根据ID查询角色
 */
export function getRoleById(id: number) {
  return request({
    url: `/admin/role/${id}`,
    method: 'GET',
  });
}

/**
 * 新增角色
 */
export function addRole(data: RoleDTO) {
  return request({
    url: '/admin/role',
    method: 'POST',
    data,
  });
}

/**
 * 修改角色
 */
export function updateRole(id: number, data: RoleDTO) {
  return request({
    url: `/admin/role/${id}`,
    method: 'PUT',
    data,
  });
}

/**
 * 删除角色
 */
export function deleteRole(id: number) {
  return request({
    url: `/admin/role/${id}`,
    method: 'DELETE',
  });
}

/**
 * 查询权限选项
 */
export function getRoleAuthOptions() {
  return request({
    url: '/admin/role/auth-options',
    method: 'GET',
  });
}

/**
 * 查询角色权限ID
 */
export function getRoleAuthIds(id: number) {
  return request({
    url: `/admin/role/${id}/auth`,
    method: 'GET',
  });
}

/**
 * 分配角色权限
 */
export function updateRoleAuths(id: number, authIds: number[]) {
  return request({
    url: `/admin/role/${id}/auth`,
    method: 'PUT',
    data: { authIds },
  });
}

/**
 * 查询用户角色ID
 */
export function getUserRoleIds(id: number) {
  return request({
    url: `/admin/user/${id}/roles`,
    method: 'GET',
  });
}

/**
 * 查询用户角色选项，包含已分配的停用角色
 */
export function getUserRoleOptions(id: number) {
  return request({
    url: `/admin/user/${id}/roles/options`,
    method: 'GET',
  });
}

/**
 * 分配用户角色
 */
export function updateUserRoles(id: number, roleIds: number[]) {
  return request({
    url: `/admin/user/${id}/roles`,
    method: 'PUT',
    data: { roleIds },
  });
}

/**
 * 查询菜单权限
 */
export function getAuthList() {
  return request({
    url: '/admin/auth/list',
    method: 'GET',
  });
}

/**
 * 新增菜单权限
 */
export function addAuth(data: AuthDTO) {
  return request({
    url: '/admin/auth',
    method: 'POST',
    data,
  });
}

/**
 * 修改菜单权限
 */
export function updateAuth(id: number, data: AuthDTO) {
  return request({
    url: `/admin/auth/${id}`,
    method: 'PUT',
    data,
  });
}

/**
 * 删除菜单权限
 */
export function deleteAuth(id: number) {
  return request({
    url: `/admin/auth/${id}`,
    method: 'DELETE',
  });
}
