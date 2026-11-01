import createDOMPurify from 'dompurify';
import { XSSPlugin, type GlobalConfig } from 'md-editor-v3';

const color = /^(?:#[\da-f]{3,8}|[a-z]+|rgba?\([\d\s.,%]+\))$/i;

/** 原文 HTML 只开放排版属性，不能借 class、定位样式或嵌入内容影响页面 */
export const configureMarkdown: GlobalConfig['markdownItConfig'] = (md) => {
  md.set({ html: true });
  md.use(XSSPlugin, {
    xss: () => ({
      whiteList: {
        p: ['style', 'align'], div: ['style', 'align'], span: ['style'],
        h1: ['style'], h2: ['style'], h3: ['style'], h4: ['style'], h5: ['style'], h6: ['style'],
        blockquote: ['style'], br: [], hr: [], strong: [], b: [], em: [], i: [],
        u: [], s: [], del: [], sub: [], sup: [], mark: ['style'],
        ul: [], ol: ['start'], li: [], pre: [], code: [],
        table: [], thead: [], tbody: [], tr: [], th: ['align', 'colspan', 'rowspan'],
        td: ['align', 'colspan', 'rowspan'],
        a: ['href', 'title', 'target'], img: ['src', 'alt', 'title', 'width', 'height'],
      },
      stripIgnoreTag: true,
      stripIgnoreTagBody: ['script', 'style', 'iframe', 'object', 'embed', 'form', 'svg', 'math'],
      css: {
        whiteList: {
          color,
          'background-color': color,
          'text-align': /^(left|center|right|justify)$/,
          'font-size': /^(?:[1-4]?\dpx|[0-3](?:\.\d+)?(?:em|rem))$/,
          'font-family': /^[\p{L}\d\s,'"-]+$/u,
          'font-weight': /^(normal|bold|[1-9]00)$/,
          'font-style': /^(normal|italic)$/,
          'text-decoration': /^(none|underline|line-through)$/,
        },
      },
    }),
  });
};

/** 每个编辑器仅允许自身创建的本地图片，普通预览不开放 Blob URL */
export function createMarkdownSanitizer(isLocalImage: (url: string) => boolean = () => false) {
  const purifier = createDOMPurify(window);
  purifier.addHook('uponSanitizeAttribute', (node, attribute) => {
    if (node.tagName === 'IMG' && attribute.attrName === 'src' && isLocalImage(attribute.attrValue)) {
      attribute.forceKeepAttr = true;
    }
  });
  purifier.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'IMG') {
      const src = node.getAttribute('src');
      if (src && !isLocalImage(src)) {
        const link = document.createElement('a');
        link.href = src;
        if (!['http:', 'https:'].includes(link.protocol)) {
          node.removeAttribute('src');
        }
      }
      node.setAttribute('referrerpolicy', 'no-referrer');
    }
    if (node.tagName === 'A' && node.getAttribute('target') === '_blank') {
      node.setAttribute('rel', 'noopener noreferrer');
    }
  });
  return (html: string): string => purifier.sanitize(html, {
    ADD_ATTR: ['target'],
    FORBID_TAGS: ['style', 'iframe', 'object', 'embed', 'form'],
  });
}

/** 所有预览入口净化最终 HTML，同时保留渲染器生成的公式与代码样式 */
export const sanitizeMarkdown = createMarkdownSanitizer();
