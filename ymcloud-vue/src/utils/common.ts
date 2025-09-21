//工具函数

/**
 * 复制对象属性
 * @param target 目标对象
 * @param source 源对象
 * 把source字段复制到target原有的字段中，不会新增字段
 */
export function copyProperties(target: any, source: any) {
  for (const key in target) {
    if(Object.prototype.hasOwnProperty.call(source, key)) {
      target[key] = source[key];
    }
  }
}