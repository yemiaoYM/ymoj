import { readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const frontendDirectory = fileURLToPath(new URL('../', import.meta.url));
const versionPattern = /^(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?$/;

/** 只读取根项目的直接子元素，避免依赖、profile 或注释中的版本干扰。 */
export function parseProjectVersion(xml) {
  const stack = [];
  const projectVersions = [];
  const revisions = [];
  const tokens = /<!--[\s\S]*?-->|<\?[\s\S]*?\?>|<\/?[^>]+>|[^<]+/g;
  let offset = 0;
  let roots = 0;

  for (const match of xml.matchAll(tokens)) {
    if (match.index !== offset) {
      throw new Error('父 POM 的 XML 结构无效');
    }
    const token = match[0];
    offset += token.length;
    if (token.startsWith('<!--') || token.startsWith('<?')) {
      continue;
    }
    if (!token.startsWith('<')) {
      if (stack.length > 0) {
        stack.at(-1).text += token;
      } else if (token.trim()) {
        throw new Error('父 POM 的 XML 结构无效');
      }
      continue;
    }

    const closing = token.match(/^<\/([\w:.-]+)\s*>$/);
    if (!closing) {
      const opening = token.match(/^<([\w:.-]+)(?:\s[^<>]*?)?\s*(\/?)>$/);
      if (!opening) {
        throw new Error('父 POM 包含不支持的 XML 结构');
      }
      if (stack.length === 0) {
        roots += 1;
        if (roots !== 1 || opening[1] !== 'project') {
          throw new Error('父 POM 必须包含唯一的 project 根元素');
        }
      } else {
        stack.at(-1).hasChildren = true;
      }
      stack.push({ name: opening[1], text: '', hasChildren: false });
      if (!opening[2]) {
        continue;
      }
    } else if (stack.at(-1)?.name !== closing[1]) {
      throw new Error('父 POM 的 XML 标签不匹配');
    }

    const path = stack.map((element) => element.name).join('/');
    const element = stack.pop();
    if (path === 'project/version' || path === 'project/properties/revision') {
      if (element.hasChildren) {
        throw new Error('父 POM 的版本必须是文本值');
      }
      (path === 'project/version' ? projectVersions : revisions).push(element.text.trim());
    }
  }

  if (offset !== xml.length || stack.length !== 0 || roots !== 1) {
    throw new Error('父 POM 的 XML 结构无效');
  }
  if (projectVersions.length !== 1 || projectVersions[0] !== '${revision}') {
    throw new Error('父 POM 的项目版本必须引用 ${revision}');
  }
  if (revisions.length !== 1) {
    throw new Error('父 POM 必须且只能定义一个 revision');
  }
  const version = revisions[0];
  if (version.length > 100 || !versionPattern.test(version)) {
    throw new Error('revision 必须为可用于镜像标签的三段版本号，可附加连字符预发布标识');
  }
  return version;
}

export function readProjectVersion(pomPath = join(frontendDirectory, '..', 'pom.xml')) {
  return parseProjectVersion(readFileSync(pomPath, 'utf8'));
}

/** 先完成全部校验，再同步三个项目版本字段，不改动第三方依赖。 */
export function syncProjectVersion(directory = frontendDirectory) {
  const version = readProjectVersion(join(directory, '..', 'pom.xml'));
  const files = ['package.json', 'package-lock.json'].map((name) => {
    const path = join(directory, name);
    const original = readFileSync(path, 'utf8');
    return { name, path, original, data: JSON.parse(original) };
  });
  const packageJson = files[0].data;
  const lock = files[1].data;
  const lockRoot = lock?.packages?.[''];
  if (!packageJson?.name || packageJson.name !== lock?.name || packageJson.name !== lockRoot?.name
      || ![2, 3].includes(lock.lockfileVersion)) {
    throw new Error('package-lock.json 必须包含与 package.json 一致的根项目');
  }

  const changed = [];
  for (const file of files) {
    if (file.data.version === version && (file.name !== 'package-lock.json' || lockRoot.version === version)) {
      continue;
    }
    file.data.version = version;
    if (file.name === 'package-lock.json') {
      lockRoot.version = version;
    }
    const newline = file.original.includes('\r\n') ? '\r\n' : '\n';
    const content = JSON.stringify(file.data, null, 2).replace(/\n/g, newline) + newline;
    writeFileSync(file.path, content, 'utf8');
    changed.push(file.name);
  }
  return { version, changed };
}

/** Vite 直接从父 POM 生成产物标识，发布校验不依赖 package.json 的缓存值。 */
export function createVersionPlugin(pomPath) {
  const version = readProjectVersion(pomPath);
  return {
    name: 'ymcloud-project-version',
    apply: 'build',
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'version.json',
        source: `${JSON.stringify({ version })}\n`,
      });
    },
  };
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  try {
    const result = syncProjectVersion();
    console.log(`前端项目版本已同步：${result.version}`);
  } catch (error) {
    console.error(`无法同步前端项目版本：${error.message}`);
    process.exitCode = 1;
  }
}
