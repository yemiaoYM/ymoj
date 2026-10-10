/* Explicit admin setup against the local website; never save identities or access keys in artifacts. */
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');

async function main() {
  const required = ['YMCLOUD_BROWSER_TEST_TOKEN', 'YMCLOUD_PLAYWRIGHT_MODULE', 'YMCLOUD_S3_ENDPOINT',
    'YMCLOUD_S3_ACCESS_KEY', 'YMCLOUD_S3_SECRET_KEY', 'YMCLOUD_S3_PUBLIC_BUCKET', 'YMCLOUD_S3_PRIVATE_BUCKET'];
  assert.ok(required.every(name => process.env[name]), 'Admin setup environment is incomplete.');
  const { chromium } = require(process.env.YMCLOUD_PLAYWRIGHT_MODULE);
  const output = path.resolve('target/test-output/storage-admin-setup');
  await fs.mkdir(output, { recursive: true });
  const report = { checks: [], stage: 'launch', pageErrors: [] };
  const resume = process.env.YMCLOUD_STORAGE_ADMIN_SETUP_RESUME === 'true';
  if (resume) {
    await fs.copyFile(path.join(output, 'report.json'), path.join(output, 'initial-attempt.json'));
    report.resumed = true;
  }
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 1536, height: 1024 } });
    await context.addInitScript(token => {
      if (location.origin === 'http://127.0.0.1:5173') {
        localStorage.setItem('Authorization', JSON.stringify(token));
      }
    }, process.env.YMCLOUD_BROWSER_TEST_TOKEN);
    const page = await context.newPage();
    page.on('pageerror', error => report.pageErrors.push(error.name));
    const listedResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/admin/storage/config'
      && response.request().method() === 'GET');
    await page.goto('http://127.0.0.1:5173/admin/common/storage-config');
    await page.waitForLoadState('networkidle');
    const listed = await (await listedResponse).json();
    assert.ok(listed.code === 200 && Array.isArray(listed.data), 'Startup must load the real backend configuration list.');
    const configuredKeys = listed.data.map(config => config.key).sort();
    assert.deepEqual(configuredKeys, resume ? ['local', 'seaweedfs'] : ['local'],
      'Startup must contain only the expected real configurations; examples are browser-only.');
    const rows = page.locator('.el-table__body tr');
    const rowForKey = key => rows.filter({
      has: page.locator('td:nth-child(2)').filter({ hasText: new RegExp(`^\\s*${key}\\s*$`) }),
    });
    assert.equal(await rows.count(), 5, 'Missing providers appear as virtual examples alongside local storage.');
    assert.ok(await rows.first().getByText('本地存储', { exact: true }).first().isVisible());
    assert.ok(await rows.first().getByText('当前默认', { exact: true }).isVisible());
    assert.equal(await page.getByText('来源 / 引用', { exact: true }).count(), 0,
      'Admin setup list must not display the source/reference column.');
    const headers = await page.locator('.el-table__header th').allInnerTexts();
    assert.ok(headers.every(text => !text.includes('来源') && !text.includes('访问密钥')));
    for (const key of resume ? ['minio', 'aliyun', 's3'] : ['minio', 'seaweedfs', 'aliyun', 's3']) {
      const example = rowForKey(key);
      assert.equal(await example.count(), 1, 'Startup example must match exactly one identifier cell.');
      assert.ok(await example.getByText('待配置', { exact: true }).isVisible());
      for (const name of ['测试连接', '设为默认', '删除']) {
        assert.equal(await example.getByRole('button', { name, exact: true }).isDisabled(), true);
      }
    }
    report.checks.push('virtual-provider-examples-do-not-create-backend-connections');
    if (!resume) {
      report.checks.push('local-only-startup');
      await page.screenshot({ path: path.join(output, 'local-only.png'), fullPage: true, animations: 'disabled' });
    }

    if (!resume) {
      report.stage = 'add-through-form';
      await page.getByRole('button', { name: '新增配置', exact: true }).click();
      const dialog = page.getByRole('dialog');
      await dialog.waitFor();
      const input = label => dialog.locator('.el-form-item').filter({ hasText: label }).locator('input');
      await input('配置名称').fill('SeaweedFS');
      await input('配置标识').fill('seaweedfs');
      await input('连接地址').fill(process.env.YMCLOUD_S3_ENDPOINT);
      await input('公开文件桶').fill(process.env.YMCLOUD_S3_PUBLIC_BUCKET);
      await input('私有文件桶').fill(process.env.YMCLOUD_S3_PRIVATE_BUCKET);
      await input('Access Key').fill(process.env.YMCLOUD_S3_ACCESS_KEY);
      await input('Secret Key').fill(process.env.YMCLOUD_S3_SECRET_KEY);
      await dialog.getByRole('tab', { name: '更多设置', exact: true }).click();
      await input('地域').fill(process.env.YMCLOUD_S3_REGION || 'us-east-1');
      await dialog.getByRole('tab', { name: '基本设置', exact: true }).click();
      const savedResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/admin/storage/config'
        && response.request().method() === 'POST', { timeout: 180000 });
      await dialog.getByRole('button', { name: '保存', exact: true }).click();
      const saved = await (await savedResponse).json();
      assert.ok(saved.code === 200, 'Admin form save must succeed without exposing response contents.');
      await dialog.waitFor({ state: 'hidden' });
      const seaweed = rowForKey('seaweedfs');
      await seaweed.getByText('备用', { exact: true }).waitFor();
      assert.ok(await rows.first().getByText('当前默认', { exact: true }).isVisible(), 'Saving must keep local as default.');
      assert.ok(await seaweed.getByText('备用', { exact: true }).isVisible());
      report.checks.push('save-keeps-local-default');
    }
    const configuredRow = rowForKey('seaweedfs');
    assert.equal(await configuredRow.count(), 1, 'Saved SeaweedFS configuration must match exactly one identifier cell.');
    assert.ok(await rows.first().getByText('当前默认', { exact: true }).isVisible());
    assert.ok(await configuredRow.getByText('备用', { exact: true }).isVisible());

    report.stage = 'test-through-row-action';
    const testedResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/admin/storage/config/seaweedfs/test',
      { timeout: 120000 });
    await configuredRow.getByRole('button', { name: '测试连接', exact: true }).click();
    assert.ok((await (await testedResponse).json()).code === 200, 'SeaweedFS connection test must succeed.');
    report.stage = 'test-response-verified';
    report.checks.push('manual-connection-test');

    report.stage = 'activate-through-confirmation';
    await configuredRow.getByRole('button', { name: '设为默认', exact: true }).click();
    const confirm = page.getByRole('dialog');
    await confirm.waitFor();
    const activatedResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/admin/storage/config/seaweedfs/activate',
      { timeout: 120000 });
    await confirm.getByRole('button', { name: '确定', exact: true }).click();
    assert.ok((await (await activatedResponse).json()).code === 200, 'Explicit admin activation must succeed.');
    await configuredRow.getByText('当前默认', { exact: true }).waitFor();
    await page.waitForFunction(() => !document.querySelector('.el-loading-mask'));
    report.checks.push('explicit-default-selection');
    assert.deepEqual(report.pageErrors, []);
    await page.screenshot({ path: path.join(output, 'seaweed-configured.png'), fullPage: true, animations: 'disabled' });
    report.stage = 'complete';
    await context.close();
    console.log('Explicit SeaweedFS admin setup passed.');
  } catch (error) {
    report.errorType = error.name;
    throw error;
  } finally {
    await browser.close();
    await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2), 'utf8');
  }
}

main().catch(error => {
  console.error('Explicit SeaweedFS admin setup failed: ' + error.name);
  process.exitCode = 1;
});
