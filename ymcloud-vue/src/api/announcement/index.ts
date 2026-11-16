import request from '@/api/request';
import type {
  AnnouncementPageQuery,
  PublicAnnouncement,
  AdminAnnouncementPageQuery,
  Announcement,
  AnnouncementDTO,
} from '@/types/announcement';
import type { ApiResult, PageVO } from '@/types';

/**
 * 获取公开公告分页；错误由公告区域展示，避免重复弹窗
 * @param params 查询条件
 * @returns 包含公开公告分页的完整响应
 */
export function getAnnouncementTableList(params: AnnouncementPageQuery) {
  return request<ApiResult<PageVO<PublicAnnouncement>>>({
    url: '/public/announcements',
    method: 'GET',
    params,
    skipErrorMessage: true,
  });
}

/**
 * 获取公告列表（管理员）
 * @param params 查询参数
 * @returns 包含公告列表（管理员）的完整响应
 */
export function getAdminAnnouncementList(params: AdminAnnouncementPageQuery) {
  return request<ApiResult<PageVO<Announcement>>>({
    url: '/admin/announcement/list',
    method: 'GET',
    params
  });
}

/**
 * 根据ID获取公告详情（管理员）
 * @param id 公告ID
 * @returns 包含公告详情（管理员）的完整响应
 */
export function getAdminAnnouncementById(id: number) {
  return request<ApiResult<Announcement>>({
    url: `/admin/announcement/${id}`,
    method: 'GET'
  });
}

/**
 * 新增公告（管理员）
 * @param announcement 公告信息
 * @returns 操作结果，不包含业务数据
 */
export function addAnnouncement(announcement: AnnouncementDTO) {
  return request<ApiResult<void>>({
    url: '/admin/announcement',
    method: 'POST',
    data: announcement
  });
}

/**
 * 修改公告（管理员）
 * @param id 公告ID
 * @param announcement 公告信息
 * @returns 操作结果，不包含业务数据
 */
export function updateAnnouncement(id: number, announcement: AnnouncementDTO) {
  return request<ApiResult<void>>({
    url: `/admin/announcement/${id}`,
    method: 'PUT',
    data: announcement
  });
}

/**
 * 删除公告（管理员）
 * @param ids 公告ID列表
 * @returns 操作结果，不包含业务数据
 */
export function deleteAnnouncementByIds(ids: number | number[]) {
  return request<ApiResult<void>>({
    url: `/admin/announcement/${ids}`,
    method: 'DELETE'
  });
}

/**
 * 修改公告状态（管理员）
 * @param id 公告ID
 * @param status 状态
 * @returns 操作结果，不包含业务数据
 */
export function updateAnnouncementStatus(id: number, status: boolean) {
  return request<ApiResult<void>>({
    url: `/admin/announcement/${id}/status`,
    method: 'PUT',
    params: { status }
  });
}

/**
 * 修改公告置顶状态（管理员）
 * @param id 公告ID
 * @param pinned 置顶状态
 * @returns 操作结果，不包含业务数据
 */
export function updateAnnouncementPinned(id: number, pinned: boolean) {
  return request<ApiResult<void>>({
    url: `/admin/announcement/${id}/pinned`,
    method: 'PUT',
    params: { pinned }
  });
}
