import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const config = createRequire(import.meta.url)('../docusaurus.config.js');

test('the footer carries the copyright, the Apache 2.0 licence link and the build year', () => {
  const copyright = config.themeConfig.footer.copyright;
  assert.equal(
    copyright.replace(/<[^>]+>/g, ''),
    `Copyright © ${new Date().getFullYear()} ZIO Maintainers · Licensed under the Apache License 2.0 · Built with Docusaurus`,
  );
  assert.ok(copyright.includes('<a href="https://github.com/zio/zio-http/blob/main/LICENSE"'));
  assert.ok(copyright.includes('<a href="https://docusaurus.io/"'));
});
