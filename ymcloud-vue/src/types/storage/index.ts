import type { FileBizTypeCode } from '@/constants/file';

/**
 * 后台文件信息
 */
export type StorageFileVO = {
  /**
   * 文件主键，超出安全整数范围时由后端序列化为字符串
   */
  id: string | number;
  fileName: string;
  originalName: string;
  fileSuffix: string;
  fileType: string | null;
  fileSize: number | null;
  bizType: FileBizTypeCode;
  bizId: string | number | null;
  service: string;
  createdBy: string | number | null;
  /**
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  createdTime: string | null;
  url: string | null;
  canDownload: boolean;
  canDelete: boolean;
};

/**
 * 文件查询条件
 */
export type FilePageQuery = {
  /**
   * 页码，从 1 开始
   */
  pageNum: number;
  pageSize: number;
  fileName?: string | null;
  bizType?: FileBizTypeCode | null;
  storageKey?: string | null;
};

/**
 * 保存存储配置参数
 */
export type StorageConfigDTO = {
  id?: string;
  /**
   * 存储配置唯一标识
   */
  configKey: string;
  endpoint: string;
  isHttps: 'Y' | 'N';
  region: string;
  bucketName: string;
  prefix: string | null;
  domain: string | null;
  accessPolicy: '0' | '1' | '2';
  accessKey?: string | null;
  secretKey?: string | null;
  remark?: string | null;
};

/**
 * 存储配置，不包含访问凭据
 */
export type StorageConfigVO = {
  id: string;
  /**
   * 存储配置唯一标识
   */
  configKey: string;
  endpoint: string;
  isHttps: 'Y' | 'N';
  region: string;
  bucketName: string;
  prefix: string | null;
  domain: string | null;
  accessPolicy: '0' | '1' | '2';
  remark: string | null;
  status: 'Y' | 'N';
  referenced: boolean;
  credentialConfigured: boolean;
  canDelete: boolean;
};
