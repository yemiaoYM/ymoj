import { JUDGE_STATUS } from '@/constants/judge';

const DECIMAL_UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
const BINARY_UNITS = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];

//定义类型
type AlterType = 'primary' | 'success' | 'warning' | 'info' | 'error';
type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger';
//状态类型
interface StatusType {
  alter: AlterType;
  tag: TagType;
}
//状态映射表
export const statusTypeMap: Record<string, StatusType> = {
  // success
  [JUDGE_STATUS.ACCEPTED.label]: { alter: 'success', tag: 'success' },
  [JUDGE_STATUS.PARTIAL_ACCEPTED.label]: { alter: 'success', tag: 'success' },

  // warning
  [JUDGE_STATUS.SUBMITTED_FAILED.label]: { alter: 'warning', tag: 'warning' },
  [JUDGE_STATUS.CANCELLED.label]: { alter: 'warning', tag: 'warning' },
  [JUDGE_STATUS.COMPILE_ERROR.label]: { alter: 'warning', tag: 'warning' },

  // info
  [JUDGE_STATUS.COMPILING.label]: { alter: 'info', tag: 'info' },
  [JUDGE_STATUS.JUDGING.label]: { alter: 'info', tag: 'info' },
  [JUDGE_STATUS.PENDING.label]: { alter: 'info', tag: 'info' },
  [JUDGE_STATUS.NOT_SUBMITTED.label]: { alter: 'info', tag: 'info' },
  [JUDGE_STATUS.SUBMITTING.label]: { alter: 'info', tag: 'info' },
  [JUDGE_STATUS.NULL.label]: { alter: 'info', tag: 'info' },

  // error
  [JUDGE_STATUS.PRESENTATION_ERROR.label]: { alter: 'error', tag: 'danger' },
  [JUDGE_STATUS.WRONG_ANSWER.label]: { alter: 'error', tag: 'danger' },
  [JUDGE_STATUS.TIME_LIMIT_EXCEEDED.label]: { alter: 'error', tag: 'danger' },
  [JUDGE_STATUS.MEMORY_LIMIT_EXCEEDED.label]: { alter: 'error', tag: 'danger' },
  [JUDGE_STATUS.RUNTIME_ERROR.label]: { alter: 'error', tag: 'danger' },
  [JUDGE_STATUS.SYSTEM_ERROR.label]: { alter: 'error', tag: 'danger' },
};

/**
 * 获取状态类型
 * @param status 状态
 * @returns 状态类型
 */
export function getStatusType(status: string): StatusType {
  //默认info
  return statusTypeMap[status] || { alter: 'info', tag: 'info' }; 
}

/**
 * 根据分数返回对应的评分类型
 * @param score 分数
 * @returns 评分类型
 */
export function getScoreType(score: number|null): string {
  if(score === null) return 'primary';
  return (
    ['danger', 'warning', 'info', 'primary', 'success']
      .find((_, i) => score >= [0, 40, 60, 80, 90][i] && score < [40, 60, 80, 90, Infinity][i])!
  );
}

/**
 * 赛制模式转换
 * @param mode 0=ACM，1=OI，2=IOI
 */
export function formatContestMode(mode: number): string {
  const modeMap: Record<number, string> = {
    0: "ACM",
    1: "OI",
    2: "IOI"
  };
  return modeMap[mode] ?? "未知模式";
}

/**
 * 比赛类型转换
 * @param style 0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛
 */
export function formatContestStyle(style: number): string {
  const styleMap: Record<number, string> = {
    0: "其他",
    1: "周赛",
    2: "月赛",
    3: "娱乐赛",
    4: "挑战赛"
  };
  return styleMap[style] ?? "未知类型";
}

/**
 * 格式化比赛时长（分钟 → 小时分钟）
 * @param minutes 比赛时长（单位：分钟）
 */
export function formatContestDuration(minutes: number): string {
  if (!minutes) return '0分钟';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h ? `${h}小时` : ''}${m ? `${m}分钟` : ''}`.trim();
}

/**
 * 按十进制格式化字节数，例如 1500 B 显示为 1.5 KB
 * @param bytes 非负字节数
 * @returns 最多保留一位小数的容量文本，缺失或无效的数值显示为 - -
 */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || !Number.isFinite(bytes) || bytes < 0) {
    return '- -';
  }
  let value = bytes;
  let index = 0;
  while (value >= 1000 && index < DECIMAL_UNITS.length - 1) {
    value /= 1000;
    index++;
  }
  return `${parseFloat(value.toFixed(1))} ${DECIMAL_UNITS[index]}`;
}

/**
 * 按二进制格式化容量，例如 1536 B 显示为 1.5 KiB
 * @param size 非负容量
 * @param unit 输入单位，默认为 B
 * @returns 最多保留一位小数的容量文本，缺失或无效的数值显示为 - -
 */
export function formatBinaryBytes(size: number | null | undefined, unit: 'B' | 'KiB' = 'B'): string {
  if (size == null || !Number.isFinite(size) || size < 0) {
    return '- -';
  }
  let value = size;
  let index = unit === 'KiB' ? 1 : 0;
  if (unit === 'KiB' && size > 0 && size < 1) {
    value *= 1024;
    index = 0;
  }
  while (value >= 1024 && index < BINARY_UNITS.length - 1) {
    value /= 1024;
    index++;
  }
  return `${parseFloat(value.toFixed(1))} ${BINARY_UNITS[index]}`;
}

/**
 * 格式化耗时
 * @param timeMs - 毫秒
 * @returns 格式化后的时间，例如 "123 ms"
 */
export function formatSubmissionTime(timeMs: number | null): string {
  if (timeMs === null || timeMs === undefined) return '- -';
  return `${timeMs} ms`;
}

/**
 * 格式化代码长度
 * @param length - 代码长度（字符数）
 * @returns 格式化后的长度，例如 "123 B"
 */
export function formatSubmissionLength(length: number | null): string {
  if (length === null || length === undefined) return '- -';
  return `${length} B`;
}

/**
 * 格式化相对时间
 * @param date 当前时间
 * @returns 格式化后的相对时间
 */
export function formatRelativeTime(date: string): string {
  const now = Date.now();
  const past = new Date(date).getTime();
  if (isNaN(past)) return '';
  const diff = Math.floor((now - past) / 1000);
  const units = [
    { label: '年', value: 60 * 60 * 24 * 365 },
    { label: '个月', value: 60 * 60 * 24 * 30 },
    { label: '天', value: 60 * 60 * 24 },
    { label: '小时', value: 60 * 60 },
    { label: '分钟', value: 60 },
    { label: '秒', value: 1 }
  ];
  for (const unit of units) {
    const count = Math.floor(diff / unit.value);
    if (count > 0) {
      return `${count}${unit.label}前`;
    }
  }
  return '刚刚';
}

/**
 * 获取比赛状态类型
 * @param status 比赛状态
 * @returns 比赛状态类型
 */
export function getContestStatusType(status: string): string {
    switch (status) {
      case "已结束": return 'info';
      case "进行中": return 'primary';
      case "未开始": return 'warning';
      case "已报名": return 'success';
      default: return 'info';
  }
}

/**
 * 将值转换为数组
 * @param v 值
 * @returns 数组
 */
export function normalizeToArray<T>(v: any): T[] {
  if (!v) return [];
  if (Array.isArray(v)) return v;
  return [v as T];
}

/**
 * 判断数组中是否包含任意一个值
 * @param array 数组
 * @param values 值
 * @returns 是否包含任意一个值
 */
export function hasAnyValue<T>(array:any,values:any):boolean{
  array=normalizeToArray<T>(array);
  values=normalizeToArray<T>(values);
  return values.some((item:T)=>array.includes(item));
}

/**
 * 判断数组中是否包含所有值
 * @param array 数组
 * @param values 值
 * @returns 是否包含所有值
 */
export function hasEveryValue<T>(array:any,values:any):boolean{
  array=normalizeToArray<T>(array);
  values=normalizeToArray<T>(values);
  if(values.length>array.length)return false;
  return values.every((item:T)=>array.includes(item));
}

/**
 * 判断值是否为必填值
 * @param value 任意类型的值
 * @returns 是否为必填值
 */
export function isRequiredValue(value: any): boolean {
  if (value === null || value === undefined) return false;
  if (typeof value === 'string') return value.trim().length > 0;
  if (typeof value === 'number') return value !== 0;
  if (typeof value === 'boolean') return value === true;
  if (Array.isArray(value)) return value.length > 0;
  if (value instanceof Set || value instanceof Map) return value.size > 0;
  if (typeof value === 'object') return Object.keys(value).length > 0;
  return true;
}

/**
 * 判断值是否为空
 * @param value 任意类型的值
 * @returns 是否为空
 */
export function isNull(value: any): boolean {
  if (value === null || value === undefined) return true;
  if (typeof value === 'string') return value.trim().length === 0;
  if (typeof value === 'number') return value === 0;
  if (typeof value === 'boolean') return !value;
  if (Array.isArray(value)) return value.length === 0;
  if (value instanceof Set || value instanceof Map) return value.size === 0;
  if (typeof value === 'object') return Object.keys(value).length === 0;
  return false;
}

/**
 * 路径匹配器
 * @param {string} pattern
 * @param {string} path
 * @returns {Boolean}
 */
export function isPathMatch(pattern: string, path: string): boolean {
  const regexPattern = pattern.replace(/\//g, '\\/').replace(/\*\*/g, '.*').replace(/\*/g, '[^\\/]*')
  const regex = new RegExp(`^${regexPattern}$`)
  return regex.test(path)
}
