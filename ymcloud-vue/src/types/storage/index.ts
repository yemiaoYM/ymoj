/**
 * 后台文件信息
 */
export type StorageFileVO = {
  /**
   * 文件 ID
   */
  id: number;
  fileName: string;
  fileType: string | null;
  fileSize: number | null;
  bizType: number | null;
  bizId: number | null;
  isPublic: number | null;
  storageProfile: string | null;
  uploaderUsername: string | null;
  /**
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  createdTime: string | null;
  url: string | null;
  sha256: string | null;
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
  isPublic?: number | null;
  bizType?: number | null;
  storageProfile?: string | null;
};

/**
 * 保存存储配置参数
 */
export type StorageConfigDTO = {
  /**
   * 存储配置唯一标识
   */
  key: string;
  name: string;
  provider: string | null;
  /**
   * 显式存储访问协议，不由服务商推导
   */
  driverType: StorageDriverType;
  endpoint: string;
  region: string;
  publicBucket: string;
  privateBucket: string;
  pathStyle: boolean;
  accessKey?: string | null;
  secretKey?: string | null;
  remark?: string | null;
};

/**
 * 存储配置，不包含访问凭据
 */
export type StorageConfigVO = {
  /**
   * 存储配置唯一标识
   */
  key: string;
  name: string;
  provider: string | null;
  /**
   * 显式存储访问协议，不由服务商推导
   */
  driverType: StorageDriverType;
  endpoint: string;
  region: string;
  publicBucket: string;
  privateBucket: string;
  pathStyle: boolean;
  remark: string | null;
  active: boolean;
  source: 'config' | 'database';
  referenced: boolean;
  credentialConfigured: boolean;
  canDelete: boolean;
};

/**
 * 已实现的存储访问协议
 */
export type StorageDriverType = 'LOCAL' | 'S3' | 'ALIYUN_OSS';
