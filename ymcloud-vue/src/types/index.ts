/**
 * 统一 JSON 响应，对应后端 Result<T>
 * void 表示无业务数据，后端实际序列化为 null
 */
export type ApiResult<T = void> = {
  code: number;
  msg: string;
  data: [T] extends [void] ? null : T;
};

/**
 * 分页数据，对应后端 PageVO<T>
 */
export type PageVO<T> = {
  total: number;
  pages: number;
  records: T[];
};

/**
 * 通用分页查询，对应后端 PageQuery
 */
export type PageQuery = {
  pageNo?: number;
  pageSize?: number;
  sortBy?: string;
  isAsc?: boolean;
};
