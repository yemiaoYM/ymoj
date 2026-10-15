import assert from 'node:assert/strict';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { createVersionPlugin, parseProjectVersion, readProjectVersion, syncProjectVersion } from '../../scripts/sync-version.mjs';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const verificationDirectory = join(root, 'target', 'phase3-verification', 'frontend-version');
const script = fileURLToPath(new URL('../../scripts/sync-version.mjs', import.meta.url));

function pom(version, extra = '') {
  return `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <version>\${revision}</version>
  <properties><revision>${version}</revision></properties>
  ${extra}
</project>`;
}

function fixture(context, version = '5.2.1-rc.2') {
  mkdirSync(verificationDirectory, { recursive: true });
  const directory = mkdtempSync(join(verificationDirectory, 'test-'));
  context.after(() => rmSync(directory, { recursive: true, force: true }));
  const frontend = join(directory, 'ymcloud-vue');
  mkdirSync(frontend);
  writeFileSync(join(directory, 'pom.xml'), pom(version), 'utf8');
  const packageJson = { name: 'version-fixture', version: '1.0.0', dependencies: { example: '^1.0.0' } };
  const lock = {
    name: packageJson.name,
    version: packageJson.version,
    lockfileVersion: 3,
    packages: {
      '': structuredClone(packageJson),
      'node_modules/example': {
        version: '1.0.0',
        resolved: 'https://example.invalid/package.tgz',
        integrity: 'sha512-unchanged',
        dependencies: { nested: '~1.0.0' },
      },
    },
  };
  for (const [name, data] of [['package.json', packageJson], ['package-lock.json', lock]]) {
    writeFileSync(join(frontend, name), JSON.stringify(data, null, 2) + '\n', 'utf8');
  }
  return { directory, frontend, packageJson, lock };
}

test('读取唯一根 revision，支持与发布工具一致的正式及预发布版本', () => {
  for (const version of ['0.0.0', '4.0.0', '15.20.300', '5.0.0-rc.1', '5.0.0-SNAPSHOT', '1.2.3-alpha-beta.2']) {
    const xml = pom(version, `
      <!-- <revision>0.0.1</revision><version>old</version> -->
      <dependencies><dependency><version>9.9.9</version></dependency></dependencies>
      <profiles><profile><properties><revision>9.0.0</revision></properties></profile></profiles>`);
    assert.equal(parseProjectVersion(xml), version);
  }
});

test('拒绝格式非法、前导零、插值、元数据及过长版本', () => {
  for (const version of ['', '4.0', 'v4.0.0', '04.0.0', '4.00.0', '4.0.01', '4.0.0+build', '${other}',
    '4.0.0-', '4.0.0-rc..1', '4.0.0-rc_1', '4.0.0-版本', `4.0.0-${'a'.repeat(95)}`]) {
    assert.throws(() => parseProjectVersion(pom(version)), /revision/);
  }
});

test('拒绝缺失、重复、硬编码或结构无效的父项目版本', () => {
  const valid = pom('4.0.0');
  for (const xml of [
    valid.replace('<revision>4.0.0</revision>', ''),
    valid.replace('</properties>', '<revision>5.0.0</revision></properties>'),
    valid.replace('${revision}', '4.0.0'),
    valid.replace('<version>${revision}</version>', ''),
    valid.replace('</project>', '<version>${revision}</version></project>'),
    valid.replace('</revision>', '</wrong>'),
    valid.replace('<revision>4.0.0</revision>', '<revision><nested/>4.0.0</revision>'),
    valid.replace('</project>', ''),
    valid + '<project/>',
  ]) {
    assert.throws(() => parseProjectVersion(xml));
  }
});

test('同步只修改三个项目版本，保留所有第三方依赖及 CRLF 格式', (context) => {
  const sample = fixture(context);
  for (const name of ['package.json', 'package-lock.json']) {
    const path = join(sample.frontend, name);
    writeFileSync(path, readFileSync(path, 'utf8').replace(/\n/g, '\r\n'), 'utf8');
  }
  const result = syncProjectVersion(sample.frontend);
  assert.deepEqual(result, { version: '5.2.1-rc.2', changed: ['package.json', 'package-lock.json'] });
  const expectedPackage = { ...sample.packageJson, version: result.version };
  const expectedLock = structuredClone(sample.lock);
  expectedLock.version = result.version;
  expectedLock.packages[''].version = result.version;
  assert.deepEqual(JSON.parse(readFileSync(join(sample.frontend, 'package.json'), 'utf8')), expectedPackage);
  const lockText = readFileSync(join(sample.frontend, 'package-lock.json'), 'utf8');
  assert.deepEqual(JSON.parse(lockText), expectedLock);
  assert.ok(lockText.includes('\r\n'));
  assert.ok(!/(?<!\r)\n/.test(lockText));
  assert.equal(readProjectVersion(join(sample.directory, 'pom.xml')), result.version);
});

test('重复同步不重写文件，父 POM 更新后两个清单同步跟进', (context) => {
  const sample = fixture(context);
  syncProjectVersion(sample.frontend);
  const paths = ['package.json', 'package-lock.json'].map((name) => join(sample.frontend, name));
  const before = paths.map((path) => readFileSync(path, 'utf8'));
  assert.deepEqual(syncProjectVersion(sample.frontend).changed, []);
  assert.deepEqual(paths.map((path) => readFileSync(path, 'utf8')), before);
  writeFileSync(join(sample.directory, 'pom.xml'), pom('6.0.0'), 'utf8');
  assert.equal(syncProjectVersion(sample.frontend).version, '6.0.0');
  assert.equal(JSON.parse(readFileSync(paths[0], 'utf8')).version, '6.0.0');
  assert.equal(JSON.parse(readFileSync(paths[1], 'utf8')).packages[''].version, '6.0.0');
});

test('父 POM 或锁文件无效时，不提前修改任何清单', (context) => {
  const sample = fixture(context);
  const packagePath = join(sample.frontend, 'package.json');
  const lockPath = join(sample.frontend, 'package-lock.json');
  const original = readFileSync(packagePath, 'utf8');
  for (const badLock of ['{broken', JSON.stringify({ ...sample.lock, packages: {} }),
    JSON.stringify({ ...sample.lock, name: 'different-project' })]) {
    writeFileSync(lockPath, badLock, 'utf8');
    assert.throws(() => syncProjectVersion(sample.frontend));
    assert.equal(readFileSync(packagePath, 'utf8'), original);
    assert.equal(readFileSync(lockPath, 'utf8'), badLock);
  }
  const lockText = JSON.stringify(sample.lock);
  writeFileSync(lockPath, lockText, 'utf8');
  writeFileSync(join(sample.directory, 'pom.xml'), pom('invalid'), 'utf8');
  assert.throws(() => syncProjectVersion(sample.frontend));
  assert.equal(readFileSync(packagePath, 'utf8'), original);
  assert.equal(readFileSync(lockPath, 'utf8'), lockText);
});

test('命令行不依赖当前目录，失败返回非零状态', (context) => {
  const sample = fixture(context);
  mkdirSync(join(sample.frontend, 'scripts'));
  const copiedScript = join(sample.frontend, 'scripts', 'sync-version.mjs');
  copyFileSync(script, copiedScript);
  const command = () => spawnSync(process.execPath, [copiedScript], { cwd: verificationDirectory, encoding: 'utf8' });
  const success = command();
  assert.equal(success.status, 0, success.stderr);
  assert.match(success.stdout, /5\.2\.1-rc\.2/);
  assert.equal(JSON.parse(readFileSync(join(sample.frontend, 'package.json'), 'utf8')).version, '5.2.1-rc.2');
  writeFileSync(join(sample.directory, 'pom.xml'), pom('invalid'), 'utf8');
  const failure = command();
  assert.equal(failure.status, 1);
  assert.match(failure.stderr, /无法同步前端项目版本/);
});

test('构建标识读取父 POM，忽略旧 package 版本并输出固定 version.json', (context) => {
  const sample = fixture(context, '7.1.0');
  const plugin = createVersionPlugin(join(sample.directory, 'pom.xml'));
  assert.equal(plugin.apply, 'build');
  const assets = [];
  plugin.generateBundle.call({ emitFile: (asset) => assets.push(asset) });
  assert.deepEqual(assets, [{ type: 'asset', fileName: 'version.json', source: '{"version":"7.1.0"}\n' }]);
  assert.equal(JSON.parse(readFileSync(join(sample.frontend, 'package.json'), 'utf8')).version, '1.0.0');
});
