import request from './request';
import type { AxiosProgressEvent, AxiosResponse } from 'axios';
import type {
  FilePageQuery,
  FilePageVO,
  StorageConfigDTO,
  StorageConfigVO,
  StorageFileVO,
  StorageResult,
} from '@/type/storage';

/**
 * 分页查询文件，访问能力由后端逐行返回。
 */
export function getAdminFiles(params: FilePageQuery): Promise<AxiosResponse<StorageResult<FilePageVO>>> {
  return request({
    url: '/admin/file/list',
    method: 'GET',
    params,
  });
}

/** 下载授权文件，业务错误仍需由调用方识别 Blob 中的 JSON。 */
export function downloadAdminFile(id: number): Promise<AxiosResponse<Blob>> {
  return request({
    url: `/admin/file/${id}/download`,
    method: 'GET',
    responseType: 'blob',
    timeout: 120000,
  });
}

/** 上传通用文件，FormData 包含 file 和 isPublic。 */
export function uploadAdminFile(
  data: FormData,
  onUploadProgress?: (event: AxiosProgressEvent) => void,
): Promise<AxiosResponse<StorageResult<StorageFileVO>>> {
  return request({
    url: '/admin/file/upload',
    method: 'POST',
    data,
    onUploadProgress,
    timeout: 120000,
  });
}

/** 删除可管理的通用文件，测试数据生命周期由题目版本接口维护。 */
export function deleteAdminFile(id: number): Promise<AxiosResponse<StorageResult<unknown>>> {
  return request({
    url: `/admin/file/${id}`,
    method: 'DELETE',
  });
}

/** 查询去除凭据的存储配置列表。 */
export function getStorageConfigs(): Promise<AxiosResponse<StorageResult<StorageConfigVO[]>>> {
  return request({
    url: '/admin/storage/config',
    method: 'GET',
  });
}

/** 创建存储配置，不自动启用。 */
export function addStorageConfig(data: StorageConfigDTO): Promise<AxiosResponse<StorageResult<unknown>>> {
  return request({
    url: '/admin/storage/config',
    method: 'POST',
    data,
    timeout: 120000,
  });
}

/** 修改存储配置；未填写的凭据沿用原值。 */
export function updateStorageConfig(
  key: string,
  data: StorageConfigDTO,
): Promise<AxiosResponse<StorageResult<unknown>>> {
  return request({
    url: `/admin/storage/config/${encodeURIComponent(key)}`,
    method: 'PUT',
    data,
    timeout: 120000,
  });
}

/** 删除未使用、未被引用的后台配置。 */
export function deleteStorageConfig(key: string): Promise<AxiosResponse<StorageResult<unknown>>> {
  return request({
    url: `/admin/storage/config/${encodeURIComponent(key)}`,
    method: 'DELETE',
  });
}

/** 使用服务端已保存的配置测试连接，前端不读取或传递原有凭据。 */
export function testStorageConfig(key: string): Promise<AxiosResponse<StorageResult<unknown>>> {
  return request({
    url: `/admin/storage/config/${encodeURIComponent(key)}/test`,
    method: 'POST',
    timeout: 120000,
  });
}

/** 启用新上传的默认配置，已有文件仍按记录中的配置读取。 */
export function activateStorageConfig(key: string): Promise<AxiosResponse<StorageResult<unknown>>> {
  return request({
    url: `/admin/storage/config/${encodeURIComponent(key)}/activate`,
    method: 'POST',
    timeout: 120000,
  });
}
