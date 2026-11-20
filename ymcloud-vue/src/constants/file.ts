/**
 * 文件管理筛选与标签共用的业务类型
 */
export const FILE_BIZ_TYPES = {
  GENERAL: { value: 0, label: '通用', type: 'info' },
  PROBLEM: { value: 1, label: '题目业务', type: 'primary' },
  CONTEST: { value: 2, label: '比赛业务', type: 'success' },
  ANNOUNCEMENT: { value: 3, label: '公告业务', type: 'warning' },
  TEST_DATA: { value: 5, label: '测试数据', type: 'danger' },
} as const;

export type FileBizTypeCode = (typeof FILE_BIZ_TYPES)[keyof typeof FILE_BIZ_TYPES]['value'];
