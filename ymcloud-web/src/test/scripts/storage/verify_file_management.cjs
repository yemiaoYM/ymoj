/* Real browser checks against the local website; test identity is short-lived and supplied in memory. */
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');

function isStorageListResponse(response) {
  return new URL(response.url()).pathname === '/api/admin/storage/config'
    && response.request().method() === 'GET';
}

async function assertPendingConfiguration(row) {
  assert.equal(await row.count(), 1, 'Pending provider must match exactly one identifier cell.');
  assert.ok(await row.getByText('待配置', { exact: true }).isVisible());
  assert.equal((await row.locator('td').nth(3).innerText()).trim(), 'xxxx');
  const buckets = await row.locator('td').nth(4).innerText();
  assert.ok(buckets.includes('公开文件：xxxx') && buckets.includes('私有文件：xxxx'));
  assert.equal(await row.getByRole('button', { name: '配置', exact: true }).isEnabled(), true);
  for (const name of ['测试连接', '设为默认', '删除']) {
    assert.equal(await row.getByRole('button', { name, exact: true }).isDisabled(), true,
      'Examples must not permit storage operations before configuration.');
  }
}

async function main() {
  const token = process.env.YMCLOUD_BROWSER_TEST_TOKEN;
  const modulePath = process.env.YMCLOUD_PLAYWRIGHT_MODULE;
  if (!token || !modulePath) {
    throw new Error('The integration harness must supply the browser runtime and temporary identity.');
  }
  const { chromium } = require(modulePath);
  const output = path.resolve('target/test-output/file-management-browser');
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const checks = [];
  const errors = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1536, height: 1024 } });
    await context.addInitScript(value => {
      if (location.origin === 'http://127.0.0.1:5173') {
        localStorage.setItem('Authorization', JSON.stringify(value));
      }
    }, token);
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.name));
    await page.goto('http://127.0.0.1:5173/admin/common/file');
    await page.waitForLoadState('networkidle');
    await page.getByText('文件管理', { exact: true }).last().waitFor();
    for (const name of ['文件管理', '存储配置']) {
      const menu = page.locator('.el-menu-item').filter({ has: page.getByText(name, { exact: true }) });
      assert.equal(await menu.count(), 1, 'Each expected file/storage child menu must appear once.');
      assert.equal(await menu.locator('.el-icon').count(), 0, 'New child menus follow the existing icon-free style.');
    }
    checks.push('new-child-menus-without-icons');
    const filename = page.locator('.el-form-item').filter({ hasText: '文件名称' }).getByRole('textbox');
    await filename.fill('admin-http-');
    const filteredResponse = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/admin/file/list' && url.searchParams.get('fileName') === 'admin-http-';
    });
    await page.getByRole('button', { name: '搜索', exact: true }).click();
    const filtered = await (await filteredResponse).json();
    assert.equal(filtered.data.total, 2, 'The actual search endpoint must filter the two owned files.');
    await page.waitForFunction(() => document.querySelectorAll('.el-table__body tr').length === 2);
    await page.waitForFunction(() => !document.querySelector('.el-loading-mask'));
    const fileRows = page.locator('.el-table__body tr');
    assert.equal(await fileRows.count(), 2, 'The two controlled public/private uploads must be visible.');
    assert.equal(await page.getByRole('button', { name: '复制网址', exact: true }).count(), 1,
      'Only the controlled public file should expose the copy-URL action.');
    await page.screenshot({ path: path.join(output, 'files.png'), fullPage: true, animations: 'disabled' });
    checks.push('file-search-public-private-row-actions');
    await page.getByRole('button', { name: '上传文件', exact: true }).click();
    await page.getByRole('dialog').waitFor();
    assert.equal(await page.getByRole('radio', { name: '私有', exact: true }).isEnabled(), true);
    await page.getByRole('button', { name: '取消', exact: true }).click();
    checks.push('upload-dialog-public-private-choice');
    const listedResponse = page.waitForResponse(isStorageListResponse);
    await page.goto('http://127.0.0.1:5173/admin/common/storage-config');
    await page.waitForLoadState('networkidle');
    await page.getByText('存储配置', { exact: true }).last().waitFor();
    const listed = await (await listedResponse).json();
    assert.ok(listed.code === 200 && Array.isArray(listed.data), 'The real storage list must load successfully.');
    const localConfig = listed.data.find(config => config.key === 'local' && config.provider === 'local');
    assert.ok(localConfig, 'Fresh-install checks use the local row supplied by the real backend.');
    assert.ok(listed.data.some(config => config.key === 'seaweedfs' && config.active),
      'Existing SeaweedFS must remain the actual default.');
    assert.ok(await page.locator('.el-table__body tr').first().getByText('本地存储', { exact: true }).first().isVisible());
    assert.equal(await page.getByText('来源 / 引用', { exact: true }).count(), 0,
      'Storage list must not display the source/reference column.');
    const headers = await page.locator('.el-table__header th').allInnerTexts();
    assert.ok(headers.every(text => !text.includes('来源') && !text.includes('访问密钥')),
      'Storage list headers must not include source or access credentials.');
    await page.screenshot({ path: path.join(output, 'storage.png'), fullPage: true, animations: 'disabled' });
    const storageRows = page.locator('.el-table__body tr');
    // Match only the identifier cell; provider names can repeat across real configurations.
    const rowForKey = key => storageRows.filter({
      has: page.locator('td:nth-child(2)').filter({ hasText: new RegExp(`^\\s*${key}\\s*$`) }),
    });
    const seaweed = rowForKey('seaweedfs');
    assert.equal(await seaweed.count(), 1, 'Real SeaweedFS configuration must match exactly one identifier cell.');
    assert.ok(await seaweed.getByText('当前默认', { exact: true }).isVisible());
    for (const key of ['silo', 'aliyun', 's3']) {
      await assertPendingConfiguration(rowForKey(key));
    }
    checks.push('pending-provider-examples-and-disabled-actions');
    checks.push('no-source-or-access-key-columns-and-real-seaweed-default');

    // Simulate only the list response; never switch defaults or erase real connection records.
    const listPattern = '**/api/admin/storage/config';
    const freshList = { code: 200, msg: '操作成功', data: [{ ...localConfig, active: true }] };
    assert.equal(Object.hasOwn(freshList.data[0], 'accessKey'), false);
    assert.equal(Object.hasOwn(freshList.data[0], 'secretKey'), false);
    const freshHandler = async route => {
      if (route.request().method() !== 'GET') {
        await route.continue();
        return;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(freshList) });
    };
    await page.route(listPattern, freshHandler);
    try {
      const freshResponse = page.waitForResponse(isStorageListResponse);
      await page.getByRole('button', { name: '刷新', exact: true }).click();
      assert.equal((await (await freshResponse).json()).data.length, 1,
        'Fresh-install mock must return only the local backend configuration.');
      await seaweed.getByText('待配置', { exact: true }).waitFor();
      assert.equal(await storageRows.count(), 5, 'Local-only backend results must show four virtual examples.');
      assert.ok(await storageRows.first().getByText('本地存储', { exact: true }).first().isVisible());
      assert.ok(await storageRows.first().getByText('当前默认', { exact: true }).isVisible());
      // The replaced SeaweedFS tag remains briefly in the DOM during its leave transition.
      await page.waitForFunction(() => [...document.querySelectorAll('.el-table__body .el-tag')]
        .filter(tag => tag.textContent.trim() === '当前默认').length === 1);
      assert.equal(await page.getByText('当前默认', { exact: true }).count(), 1,
        'Fresh-install list must display exactly one current default.');
      for (const key of ['silo', 'seaweedfs', 'aliyun', 's3']) {
        await assertPendingConfiguration(rowForKey(key));
      }
      await page.screenshot({ path: path.join(output, 'storage-fresh-install.png'), fullPage: true, animations: 'disabled' });
      checks.push('fresh-install-local-only-default-four-pending-examples');
    } finally {
      await page.unroute(listPattern, freshHandler);
      const restoredResponse = page.waitForResponse(isStorageListResponse);
      await page.getByRole('button', { name: '刷新', exact: true }).click();
      const restored = await (await restoredResponse).json();
      assert.ok(restored.code === 200 && restored.data.some(config => config.key === 'seaweedfs' && config.active),
        'Removing the mock must restore the unchanged real SeaweedFS default.');
      await seaweed.getByText('当前默认', { exact: true }).waitFor();
    }
    checks.push('fresh-install-mock-removed-real-default-restored');

    await seaweed.getByRole('button', { name: '修改', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.waitFor();
    const endpoint = dialog.locator('.el-form-item').filter({ hasText: '连接地址' }).getByRole('textbox');
    assert.equal(await endpoint.isDisabled(), true, 'Referenced storage endpoint must be locked.');
    for (const label of ['Access Key', 'Secret Key']) {
      const field = dialog.locator('.el-form-item').filter({ hasText: label }).locator('input');
      assert.equal(await field.inputValue(), '', 'Saved credentials must never be populated in the form.');
    }
    await page.screenshot({ path: path.join(output, 'storage-edit.png'), fullPage: true, animations: 'disabled' });
    const basicBounds = await dialog.boundingBox();
    const basicFooter = await dialog.getByRole('button', { name: '保存', exact: true }).boundingBox();
    await dialog.getByRole('tab', { name: '更多设置', exact: true }).click();
    await page.screenshot({ path: path.join(output, 'storage-more.png'), fullPage: true, animations: 'disabled' });
    const moreBounds = await dialog.boundingBox();
    const moreFooter = await dialog.getByRole('button', { name: '保存', exact: true }).boundingBox();
    assert.ok(basicBounds && moreBounds && basicFooter && moreFooter);
    assert.ok(Math.abs(basicBounds.height - moreBounds.height) < 1 && Math.abs(basicBounds.y - moreBounds.y) < 1);
    assert.ok(Math.abs(basicFooter.y - moreFooter.y) < 1, 'Switching settings must keep footer buttons in place.');
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    checks.push('local-first-no-source-column-fixed-tab-layout');
    checks.push('referenced-locator-disabled-and-credentials-not-returned');

    await rowForKey('silo').getByRole('button', { name: '配置', exact: true }).click();
    await dialog.waitFor();
    await dialog.getByText('配置 Silo', { exact: true }).waitFor();
    const inputFor = label => dialog.locator('.el-form-item').filter({ hasText: label }).locator('input');
    assert.equal(await inputFor('配置名称').inputValue(), 'Silo');
    assert.equal(await inputFor('配置标识').inputValue(), 'silo');
    assert.ok(await dialog.locator('.el-form-item').filter({ hasText: '服务商' }).getByText('Silo', { exact: true }).isVisible());
    for (const [label, placeholder] of [
      ['连接地址', 'http://127.0.0.1:9000'],
      ['公开文件桶', '例如 ymcloud-public'],
      ['私有文件桶', '例如 ymcloud-private'],
      ['Access Key', '存储服务提供的 Access Key'],
      ['Secret Key', '存储服务提供的 Secret Key'],
    ]) {
      assert.equal(await inputFor(label).inputValue(), '', 'Examples supply placeholders, never real connection values.');
      assert.equal(await inputFor(label).getAttribute('placeholder'), placeholder);
    }
    let attemptedConfigWrites = 0;
    const recordConfigWrite = request => {
      if (new URL(request.url()).pathname.startsWith('/api/admin/storage/config')
          && ['POST', 'PUT', 'DELETE'].includes(request.method())) {
        attemptedConfigWrites++;
      }
    };
    page.on('request', recordConfigWrite);
    try {
      await dialog.getByRole('button', { name: '保存', exact: true }).click();
      for (const message of ['请输入连接地址', '请输入公开文件桶', '请输入私有文件桶']) {
        await dialog.getByText(message, { exact: true }).waitFor();
      }
      await page.waitForLoadState('networkidle');
      assert.equal(attemptedConfigWrites, 0, 'Saving an unconfigured example must fail before any storage write request.');
      assert.ok(await dialog.isVisible());
      await page.screenshot({ path: path.join(output, 'storage-example-configure.png'), fullPage: true, animations: 'disabled' });
    } finally {
      page.off('request', recordConfigWrite);
    }
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    checks.push('silo-example-new-mode-empty-fields-placeholder-and-no-post');

    await page.getByRole('button', { name: '新增配置', exact: true }).click();
    await dialog.waitFor();
    await dialog.getByRole('button', { name: '保存', exact: true }).click();
    await dialog.getByText('请输入配置名称', { exact: true }).waitFor();
    await dialog.locator('.el-form-item').filter({ hasText: '配置名称' }).getByRole('textbox').fill('校验测试');
    await dialog.locator('.el-form-item').filter({ hasText: '配置标识' }).getByRole('textbox').fill('validation-only');
    await inputFor('连接地址').fill('http://127.0.0.1:9000');
    await inputFor('公开文件桶').fill('validation-public');
    await inputFor('私有文件桶').fill('validation-private');
    await dialog.getByRole('tab', { name: '更多设置', exact: true }).click();
    await dialog.locator('.el-form-item').filter({ hasText: '地域' }).getByRole('textbox').fill('');
    await dialog.getByRole('tab', { name: '基本设置', exact: true }).click();
    await dialog.getByRole('button', { name: '保存', exact: true }).click();
    await dialog.getByText('请输入地域', { exact: true }).waitFor();
    assert.equal(await dialog.getByRole('tab', { name: '更多设置', exact: true }).getAttribute('aria-selected'), 'true');
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    checks.push('configuration-required-field-validation');
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: path.join(output, 'storage-mobile.png'), fullPage: true, animations: 'disabled' });
    const width = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, client: document.documentElement.clientWidth }));
    assert.ok(width.scroll <= width.client + 1, 'Small screens should scroll the table within its container.');
    const tableScroll = page.locator('.el-table__body-wrapper .el-scrollbar__wrap');
    const scroll = await tableScroll.evaluate(element => {
      element.scrollLeft = 120;
      return { left: element.scrollLeft, content: element.scrollWidth, width: element.clientWidth };
    });
    assert.ok(scroll.content > scroll.width && scroll.left > 0, 'Wide tables must remain scrollable on mobile.');
    await page.getByRole('button', { name: '新增配置', exact: true }).click();
    await dialog.waitFor();
    await page.screenshot({ path: path.join(output, 'storage-mobile-dialog-before-scroll.png'), fullPage: true, animations: 'disabled' });
    const bounds = await dialog.boundingBox();
    assert.ok(bounds && bounds.x >= 0 && bounds.x + bounds.width <= 391, 'Mobile dialogs must stay within the viewport.');
    const footerBeforeScroll = await dialog.getByRole('button', { name: '保存', exact: true }).boundingBox();
    const dialogScroll = dialog.locator('.el-scrollbar__wrap:visible');
    const internalScroll = await dialogScroll.evaluate(element => {
      element.scrollTop = 120;
      return { top: element.scrollTop, content: element.scrollHeight, height: element.clientHeight };
    });
    assert.ok(internalScroll.content > internalScroll.height && internalScroll.top > 0, 'Element Plus scrollbar must handle dialog scrolling.');
    const footerAfterScroll = await dialog.getByRole('button', { name: '保存', exact: true }).boundingBox();
    assert.ok(footerBeforeScroll && footerAfterScroll && Math.abs(footerBeforeScroll.y - footerAfterScroll.y) < 1);
    assert.equal(await dialog.locator('.el-tabs__content').evaluate(element => getComputedStyle(element).overflowY), 'hidden');
    await page.screenshot({ path: path.join(output, 'storage-mobile-dialog.png'), fullPage: true, animations: 'disabled' });
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    checks.push('mobile-layout-contained');
    checks.push('element-plus-dialog-scrollbar-and-fixed-footer');
    assert.deepEqual(errors, [], 'No unhandled browser errors.');
    await context.close();
    await fs.writeFile(path.join(output, 'report.json'), JSON.stringify({ checks, pageErrors: errors }, null, 2), 'utf8');
    console.log('Browser file-management checks passed.');
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  // Never serialize request objects, session tokens, or browser storage.
  console.error(error.message);
  process.exitCode = 1;
});
