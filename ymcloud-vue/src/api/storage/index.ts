import request from '@/api/request';
import type { ApiResult, PageVO } from '@/types';
import type { FilePageQuery, StorageFileVO, StorageConfigVO, StorageConfigDTO } from '@/types/storage';
import type { AxiosProgressEvent } from 'axios';

/**
 * 上传 Markdown 插图，服务端校验并压缩后存入公开文件区
 * @param file 用户确认裁剪后的 PNG 图片，不超过 5 MiB
 * @returns 包含图片公开地址的完整响应
 */
export function uploadMarkdownImage(file: Blob) {
  const data = new FormData();
  data.append('file', file, 'markdown.png');
  return request<ApiResult<string>>({
    url: '/markdown/image',
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
 * @param id 对外 UUIDv4 文件标识
 * @returns 包含文件内容的完整响应
 */
export function downloadAdminFile(id: string) {
  return request<Blob>({
    url: `/admin/file/${id}/download`,
    method: 'GET',
    responseType: 'blob',
    timeout: 120000,
  });
}

/**
 * 上传通用文件，FormData 包含 file 和 isPublic
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
 * 删除可管理的通用文件，测试数据生命周期由题目版本接口维护
 * @param id 对外 UUIDv4 文件标识
 * @returns 操作结果，不包含业务数据
 */
export function deleteAdminFile(id: string) {
  return request<ApiResult<void>>({
    url: `/admin/file/${id}`,
    method: 'DELETE',
  });
}

/**
 * 查询去除凭据的存储配置列表
 * @returns 包含去除凭据的存储配置列表的完整响应
 */
export function getStorageConfigs() {
  return request<ApiResult<StorageConfigVO[]>>({
    url: '/admin/storage/config',
    method: 'GET',
  });
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
 * @param key 存储配置标识
 * @param data 请求业务数据
 * @returns 操作结果，不包含业务数据
 */
export function updateStorageConfig(key: string, data: StorageConfigDTO) {
  return request<ApiResult<void>>({
    url: `/admin/storage/config/${encodeURIComponent(key)}`,
    method: 'PUT',
    data,
    timeout: 120000,
  });
}

/**
 * 删除未使用、未被引用的后台配置
 * @param key 存储配置标识
 * @returns 操作结果，不包含业务数据
 */
export function deleteStorageConfig(key: string) {
  return request<ApiResult<void>>({
    url: `/admin/storage/config/${encodeURIComponent(key)}`,
    method: 'DELETE',
  });
}

/**
 * 使用服务端已保存的配置测试连接，前端不读取或传递原有凭据
 * @param key 存储配置标识
 * @returns 操作结果，不包含业务数据
 */
export function testStorageConfig(key: string) {
  return request<ApiResult<void>>({
    url: `/admin/storage/config/${encodeURIComponent(key)}/test`,
    method: 'POST',
    timeout: 120000,
  });
}

/**
 * 启用新上传的默认配置，已有文件仍按记录中的配置读取
 * @param key 存储配置标识
 * @returns 操作结果，不包含业务数据
 */
export function activateStorageConfig(key: string) {
  return request<ApiResult<void>>({
    url: `/admin/storage/config/${encodeURIComponent(key)}/activate`,
    method: 'POST',
    timeout: 120000,
  });
}
