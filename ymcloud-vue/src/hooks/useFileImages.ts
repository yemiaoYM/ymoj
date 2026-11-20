import { computed, onBeforeUnmount, shallowReactive, watch } from 'vue';
import { downloadFileImage } from '@/api/storage';
import { useUserStore } from '@/stores/user';
import { createMarkdownSanitizer } from '@/utils/markdown';

/**
 * 新增业务时只提交当前正文仍在引用的预上传文件 ID，文件主键始终按字符串处理
 */
export function referencedImageIds(uploaded: string[] | undefined, ...sections: string[]): string[] {
  const referenced = new Set(sections.flatMap(section =>
    Array.from(section.matchAll(/\/api\/files\/([1-9]\d*)/g), match => match[1]),
  ));
  return [...new Set(uploaded)].filter(id => referenced.has(id));
}

/** 授权图片随正文、比赛和登录会话释放，外部图片由浏览器正常加载 */
export function useFileImages(options: {
  sources: () => (string | null | undefined)[];
  contestId?: () => number | undefined;
  publicImages?: () => boolean;
  localImage?: (url: string) => boolean;
}) {
  const user = useUserStore();
  const images = shallowReactive(new Map<string, string>());
  const pending = new Map<string, AbortController>();
  const controlled = /^\/(?:api\/)?files\/[1-9]\d*$/;

  function remove(source: string) {
    pending.get(source)?.abort();
    pending.delete(source);
    const url = images.get(source);
    if (url) URL.revokeObjectURL(url);
    images.delete(source);
  }

  function clear() {
    for (const source of pending.keys()) remove(source);
  }

  function refresh() {
    const sources = new Set((options.publicImages?.() ? [] : options.sources()).filter((s): s is string => !!s && controlled.test(s)));
    for (const source of pending.keys()) {
      if (!sources.has(source)) remove(source);
    }
    for (const source of sources) {
      if (pending.has(source)) continue;
      const request = new AbortController();
      pending.set(source, request);
      downloadFileImage(source.replace(/^\/api\//, '/'), options.contestId?.(), request.signal)
        .then(({ data }) => {
          if (!request.signal.aborted && ['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(data.type)) {
            images.set(source, URL.createObjectURL(data));
          }
        })
        .catch(() => { /* 拒绝、取消或不可用的图片保留空占位 */ });
    }
  }

  watch([() => user.token, () => options.contestId?.()], () => { clear(); refresh(); }, { flush: 'sync' });
  watch([options.sources, () => options.publicImages?.()], refresh, { immediate: true });
  onBeforeUnmount(clear);

  function resolveUrl(source: string) {
    return controlled.test(source) ? images.get(source) || '' : source;
  }

  const sanitize = createMarkdownSanitizer(url => !!options.localImage?.(url) || [...images.values()].includes(url));
  const sanitizeMarkdown = computed(() => {
    if (options.publicImages?.()) return sanitize;
    const snapshot = new Map(images);
    return (html: string) => sanitize(html.replace(/(\bsrc=")(\/api\/files\/[1-9]\d*)"/g,
      (_match, prefix, source) => prefix + (snapshot.get(source) || '') + '"'));
  });
  return { resolveUrl, sanitizeMarkdown };
}
