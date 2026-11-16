import request from '@/api/request';
import type { ApiResult } from '@/types';
import type {
  Tag,
  TagsWithClassification,
  TagDTO,
  TagClassificationDTO,
} from '@/types/problem/tag';

/**
 * 获取标签列表
 * @returns 包含标签列表的完整响应
 */
export function getTagsList() {
  return request<ApiResult<Tag[]>>({
    url: '/public/tags',
    method: 'GET',
  });
}

/**
 * 获取标签和分类列表（管理员）
 * @returns 包含标签和分类列表（管理员）的完整响应
 */
export function getAdminTagsAndClassifications() {
  return request<ApiResult<TagsWithClassification[]>>({
    url: '/admin/tag/list',
    method: 'GET'
  });
}

/**
 * 添加标签（管理员）
 * @param tag 标签信息
 * @returns 操作结果，不包含业务数据
 */
export function addAdminTag(tag: TagDTO) {
  return request<ApiResult<void>>({
    url: '/admin/tag',
    method: 'POST',
    data: tag
  });
}

/**
 * 更新标签（管理员）
 * @param id 标签ID
 * @param tag 标签信息
 * @returns 操作结果，不包含业务数据
 */
export function updateAdminTag(id: number, tag: TagDTO) {
  return request<ApiResult<void>>({
    url: `/admin/tag/${id}`,
    method: 'PUT',
    data: tag
  });
}

/**
 * 删除标签（管理员）
 * @param id 标签ID
 * @returns 操作结果，不包含业务数据
 */
export function deleteAdminTag(id: number) {
  return request<ApiResult<void>>({
    url: `/admin/tag/${id}`,
    method: 'DELETE'
  });
}

/**
 * 添加标签分类（管理员）
 * @param classification 标签分类信息
 * @returns 操作结果，不包含业务数据
 */
export function addAdminTagClassification(classification: TagClassificationDTO) {
  return request<ApiResult<void>>({
    url: '/admin/tag/classification',
    method: 'POST',
    data: classification
  });
}

/**
 * 更新标签分类（管理员）
 * @param id 标签分类ID
 * @param classification 标签分类信息
 * @returns 操作结果，不包含业务数据
 */
export function updateAdminTagClassification(id: number, classification: TagClassificationDTO) {
  return request<ApiResult<void>>({
    url: `/admin/tag/classification/${id}`,
    method: 'PUT',
    data: classification
  });
}

/**
 * 删除标签分类（管理员）
 * @param id 标签分类ID
 * @returns 操作结果，不包含业务数据
 */
export function deleteAdminTagClassification(id: number) {
  return request<ApiResult<void>>({
    url: `/admin/tag/classification/${id}`,
    method: 'DELETE'
  });
}
