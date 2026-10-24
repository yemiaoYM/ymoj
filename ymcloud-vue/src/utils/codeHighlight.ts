import hljs from 'highlight.js/lib/core';
import c from 'highlight.js/lib/languages/c';
import cpp from 'highlight.js/lib/languages/cpp';
import java from 'highlight.js/lib/languages/java';
import python from 'highlight.js/lib/languages/python';

// 注册一次，实例之间共享；独立于富文本编辑器的高亮配置。
const highlighter = hljs.newInstance();
highlighter.registerLanguage('c', c);
highlighter.registerLanguage('cpp', cpp);
highlighter.registerLanguage('java', java);
highlighter.registerLanguage('python', python);

/** 仅解析已注册语言；返回值中的源码已转义，未知语言交组件按纯文本展示。 */
export function highlightCode(code: string, language: string): string | null {
  const name = language.trim().toLowerCase();
  const alias = name === 'python3' ? 'python' : name;
  if (!highlighter.getLanguage(alias)) {
    return null;
  }
  return highlighter.highlight(code, { language: alias, ignoreIllegals: true }).value;
}
