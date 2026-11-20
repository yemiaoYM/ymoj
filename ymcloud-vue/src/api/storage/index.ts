import request from '@/api/request';
import type { ApiResult, PageVO } from '@/types';
import type { FilePageQuery, StorageFileVO, StorageConfigVO, StorageConfigDTO } from '@/types/storage';
import type { AxiosProgressEvent } from 'axios';

export type ImagePurpose = 'signature' | 'problem' | 'contest' | 'announcement';

/**
 * 使用当前会话下载站内图片
 */
export function downloadFileImage(url: string, contestId?: number, signal?: AbortSignal) {
  return request<Blob>({
    url, params: { contestId }, responseType: 'blob', signal, skipErrorMessage: true, timeout: 120000,
  });
}

/**
 * 上传 Markdown 插图，服务端按正文所属业务登记
 * @param file 用户选择的图片，不超过 3 MiB
 * @param purpose 正文所属业务
 * @param bizId 编辑已有业务时传其主键
 * @returns 图片文件 ID，雪花 ID 由后端序列化为字符串
 */
export function uploadMarkdownImage(file: Blob, purpose: ImagePurpose, bizId?: number) {
  const routes = {
    signature: '/user/signature/image',
    problem: '/admin/problem/image',
    contest: '/admin/contest/description/image',
    announcement: '/admin/announcement/image',
  };
  const data = new FormData();
  data.append('file', file, file instanceof File ? file.name : 'markdown.png');
  return request<ApiResult<string>>({
    url: routes[purpose],
    params: purpose === 'signature' ? undefined : { [purpose + 'Id']: bizId },
    method: 'POST',
    data,
    timeout: 120000,
  });
}

/**
 * 分页查询文件，访问能力由后端逐行返回
 * @param params 查询条件
 * @returns 包含文件分页及访问能力的完整响应
 */
export function getAdminFiles(params: FilePageQuery) {
  return request<ApiResult<PageVO<StorageFileVO>>>({
    url: '/admin/file/list',
    method: 'GET',
    params,
  });
}

/**
 * 下载授权文件，业务错误由请求层统一处理
 * @param id 对外 十进制文件标识
 * @returns 包含文件内容的完整响应
 */
export function downloadAdminFile(id: string | number) {
  return request<Blob>({
    url: `/admin/file/${id}/download`,
    method: 'GET',
    responseType: 'blob',
    timeout: 120000,
  });
}

/**
 * 上传通用文件，FormData 包含 file
 * @param data 请求业务数据
 * @param onUploadProgress 上传进度回调
 * @returns 包含已保存文件元数据的完整响应
 */
export function uploadAdminFile(
  data: FormData,
  onUploadProgress?: (event: AxiosProgressEvent) => void,
) {
  return request<ApiResult<StorageFileVO>>({
    url: '/admin/file/upload',
    method: 'POST',
    data,
    onUploadProgress,
    timeout: 120000,
  });
}

/**
 * 按选中的文件主键一次删除通用文件
 * @param ids 一个或多个文件主键，保留雪花 ID 的原始精度
 * @returns 操作结果，不包含业务数据
 */
export function deleteAdminFiles(ids: Array<string | number> | string | number) {
  return request<ApiResult<void>>({
    url: `/admin/file/${ids}`,
    method: 'DELETE',
  });
}

/**
 * 查询去除凭据的存储配置列表
 * @returns 包含去除凭据的存储配置列表的完整响应
 */
export function getStorageConfigs(params: { pageNo: number; pageSize: number; configKey?: string; bucketName?: string; status?: string }) {
  return request<ApiResult<PageVO<StorageConfigVO>>>({
    url: '/admin/storage/config/list',
    params,
    method: 'GET',
  });
}

/** 读取当前配置详情，凭据留在服务端 */
export function getStorageConfig(id: string) {
  return request<ApiResult<StorageConfigVO>>({ url: `/admin/storage/config/${id}`, method: 'GET' });
}

/**
 * 创建存储配置，不自动启用
 * @param data 请求业务数据
 * @returns 操作结果，不包含业务数据
 */
export function addStorageConfig(data: StorageConfigDTO) {
  return request<ApiResult<void>>({
    url: '/admin/storage/config',
    method: 'POST',
    data,
    timeout: 120000,
  });
}

/**
 * 修改存储配置；未填写的凭据沿用原值
 * @param data 请求业务数据
 * @returns 操作结果，不包含业务数据
 */
export function updateStorageConfig(data: StorageConfigDTO) {
  return request<ApiResult<void>>({
    url: '/admin/storage/config',
    method: 'PUT',
    data,
    timeout: 120000,
  });
}

/**
 * 删除未使用、未被引用的后台配置
 * @param id 配置主键
 * @returns 操作结果，不包含业务数据
 */
export function deleteStorageConfig(id: string) {
  return request<ApiResult<void>>({
    url: `/admin/storage/config/${id}`,
    method: 'DELETE',
  });
}

/** 切换默认配置，历史文件继续按其登记的服务读取 */
export function activateStorageConfig(id: string) {
  return request<ApiResult<void>>({
    url: '/admin/storage/config/changeStatus',
    method: 'PUT',
    data: { id },
  });
}
