/**
 * 文件管理与存储配置接口类型。
 */
export type StorageResult<T> = {
  code: number;
  msg: string;
  data: T;
};

export type FilePageQuery = {
  pageNum: number;
  pageSize: number;
  fileName?: string;
  isPublic?: 0 | 1;
  bizType?: number;
  storageProfile?: string;
};

export type StorageFileVO = {
  id: number;
  fileName: string;
  fileType: string | null;
  fileSize: number | null;
  bizType: number | null;
  bizId: number | null;
  isPublic: 0 | 1;
  storageProfile: string | null;
  uploaderUsername: string | null;
  createdTime: string | null;
  url: string | null;
  sha256: string | null;
  canDownload: boolean;
  canDelete: boolean;
};

export type FilePageVO = {
  records: StorageFileVO[];
  total: number;
};

export type StorageProvider = 'seaweedfs' | 'silo' | 'minio' | 'aliyun' | 's3' | 'local';

export type StorageConfigDTO = {
  key: string;
  name: string;
  provider: StorageProvider;
  endpoint: string;
  region: string;
  publicBucket: string;
  privateBucket: string;
  pathStyle: boolean;
  accessKey?: string;
  secretKey?: string;
  remark?: string;
};

/**
 * 列表响应不包含凭据，编辑时不尝试还原密钥。
 */
export type StorageConfigVO = Omit<StorageConfigDTO, 'accessKey' | 'secretKey'> & {
  active: boolean;
  source: 'config' | 'database';
  referenced: boolean;
  credentialConfigured: boolean;
  canDelete: boolean;
};
