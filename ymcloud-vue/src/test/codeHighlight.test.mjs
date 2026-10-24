import assert from 'node:assert/strict';
import test from 'node:test';
import hljs from 'highlight.js/lib/core';
import { highlightCode } from '../utils/codeHighlight.ts';

for (const [language, code] of [
  ['C', 'int main(void) { return 0; }'],
  [' C++ ', 'int main() { return 0; }'],
  ['Java', 'public class Main {}'],
  ['Python3', 'def main():\n    return 0']
]) {
  test(`${language.trim()} 使用明确语言高亮`, () => {
    assert.match(highlightCode(code, language), /class="hljs-/);
  });
}

test('未知语言保持纯文本，不自动探测', () => {
  for (const language of ['', 'unknown', 'javascript', '__proto__']) {
    assert.equal(highlightCode('int main() {}', language), null);
  }
});

test('源码中的 HTML 标签和事件不能成为真实节点', () => {
  const html = highlightCode('// <img src=x onerror=alert(1)> & <script>alert(1)</script>', 'cpp');
  assert.ok(!html.includes('<img'));
  assert.ok(!html.includes('<script'));
  assert.match(html, /&lt;img/);
  assert.match(html, /&amp;/);
});

test('保留首尾空行和缩进，支持空源码', () => {
  const code = '\n  // comment\n\n';
  const html = highlightCode(code, 'cpp');
  assert.equal(html.replace(/<\/?span[^>]*>/g, ''), code);
  assert.equal(highlightCode('', 'C'), '');
});

test('独立实例不污染其他高亮器的语言注册', () => {
  assert.deepEqual(hljs.listLanguages(), []);
});
