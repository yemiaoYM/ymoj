import request from './request';

/**
 * 上传 Markdown 插图，服务端校验并压缩后存入公开文件区
 * @param file 用户确认裁剪后的 PNG 图片，不超过 5 MiB
 * @returns 包含图片公开地址的完整响应
 */
export function uploadMarkdownImage(file: Blob) {
  const data = new FormData();
  data.append('file', file, 'markdown.png');
  return request({ url: '/markdown/image', method: 'POST', data, timeout: 120000 });
}
