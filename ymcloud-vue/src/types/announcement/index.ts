import type { PageQuery } from '@/types';

/**
 * 公开公告
 */
export type PublicAnnouncement = {
  /**
   * 公告ID，用于列表身份和展开状态
   */
  id: number;
  /**
   * 公告标题
   */
  title: string;
  /**
   * 公告内容（支持HTML/Markdown）
   */
  content: string;
  /**
   * 是否置顶：true=置顶，false=不置顶
   */
  pinned: boolean;
  /**
   * 发布人用户名
   */
  authorUsername: string;
  /**
   * 更新时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  updatedTime: string;
};

/**
 * 后台公告
 */
export type Announcement = {
  /**
   * 公告ID
   */
  id: number;
  /**
   * 公告标题
   */
  title: string;
  /**
   * 公告内容（支持HTML/Markdown）
   */
  content: string;
  /**
   * 公告权重（值越大越靠前）
   */
  priority: number;
  /**
   * 是否置顶：true=置顶，false=不置顶
   */
  pinned: boolean;
  /**
   * 是否可见：true=可见，false=不可见
   */
  status: boolean;
  /**
   * 发布人ID
   */
  authorId: number;
  /**
   * 发布人用户名
   */
  authorUsername: string;
  /**
   * 创建时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  createdTime: string;
  /**
   * 更新时间
   * 格式：yyyy-MM-dd HH:mm:ss
   */
  updatedTime: string;
};

/**
 * 保存公告参数
 */
export type AnnouncementDTO = {
  /**
   * 新增时绑定的已上传正文图片 ID
   */
  fileIds?: string[];
  /**
   * 公告标题
   */
  title: string;
  /**
   * 公告内容（支持HTML/Markdown）
   */
  content: string;
  /**
   * 公告权重（值越大越靠前）
   */
  priority: number;
  /**
   * 是否置顶：true=置顶，false=不置顶
   */
  pinned: boolean;
  /**
   * 是否可见：true=可见，false=不可见
   */
  status: boolean;
};

/**
 * 后台公告查询条件
 */
export type AdminAnnouncementPageQuery = PageQuery & {
  /**
   * 公告标题（模糊搜索）
   */
  title?: string | null;
  /**
   * 是否置顶：true=置顶，false=不置顶，null=全部
   */
  pinned?: boolean | null;
  /**
   * 是否可见：true=可见，false=不可见，null=全部
   */
  status?: boolean | null;
};

/**
 * 公开公告分页条件
 */
export type AnnouncementPageQuery = {
  pageNo: number;
};
