/**
 * 题目标签
 */
export type Tag = {
  /**
   * 标签ID
   */
  id: number;
  /**
   * 标签名称
   */
  name: string;
  /**
   * 父标签ID
   */
  parentId: number | null;
  /**
   * 标签颜色（如 #ff6600）
   */
  color: string;
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
 * 保存标签参数
 */
export type TagDTO = {
  /**
   * 标签名称
   */
  name: string;
  /**
   * 标签分类ID
   */
  parentId: number | null;
  color: string;
};

/**
 * 标签分类
 */
export type TagClassification = {
  /**
   * 主键
   */
  id: number;
  /**
   * 标签分类名称
   */
  name: string;
  /**
   * 分类优先级（数值越小优先级越高）
   */
  priority: number;
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
 * 保存标签分类参数
 */
export type TagClassificationDTO = {
  /**
   * 标签分类名称
   */
  name: string;
  /**
   * 分类排序权重（数值越小排序越靠前）
   */
  priority: number;
};

/**
 * 按分类分组的题目标签
 */
export type TagsWithClassification = {
  /**
   * 标签分类
   */
  classification: TagClassification | null;
  /**
   * 标签列表
   */
  tagList: Tag[];
};
