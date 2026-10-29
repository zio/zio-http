import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import vm from 'node:vm';
import {
  FAVICON,
  LOGO,
  NAV_LINKS,
  STYLESHEET,
  TITLE,
  breadcrumbsHtml,
  checkSideMenu,
  extensionless,
  extensionlessLinks,
  extensionlessSearchData,
  extensionlessUxJs,
  folderIndexRedirect,
  navbarHtml,
  nestedTypePaths,
  relocatePage,
  relocateSideMenu,
  themeApiPage,
} from '../scripts/lib/api-theme.mjs';

const config = createRequire(import.meta.url)('../docusaurus.config.js');

// The page skeleton Scaladoc 3.3.7 generates (zio/http/codec/HttpCodec.html, trimmed to the parts the theme touches).
const PAGE =
  '<!DOCTYPE html><html data-pathToRoot="../../../" data-rawLocation="zio/http/codec/HttpCodec" data-dynamicSideMenu="true"><head><meta charset="utf-8"></meta><title>HttpCodec</title>' +
  '<link rel="shortcut icon" type="image/x-icon" href="../../../favicon.ico"></link><script type="text/javascript" src="../../../scripts/theme.js"></script>' +
  '<link rel="stylesheet" href="../../../styles/theme/bundle.css"></link><script>var pathToRoot = "../../../";</script></head>' +
  '<body><div id=""><div id="header" class="body-small"><div class="header-container-left"><a href="../../../" class="logo-container">' +
  '<span class="project-name h300">ZIO HTTP</span></a><span onclick="dropdownHandler(event)" class="text-button with-arrow" id="dropdown-trigger">' +
  '<a><div class="projectVersion">3.11.4-SNAPSHOT</div></a></span><div id="version-dropdown" class="dropdown-menu"></div></div>' +
  '<div class="header-container-right"><button id="search-toggle" class="icon-button"></button><span id="theme-toggle" class="icon-button"></span>' +
  '<span id="mobile-menu-toggle" class="icon-button hamburger"></span></div></div>' +
  '<div id="mobile-menu"><div class="mobile-menu-header body-small"><span class="mobile-menu-logo">' +
  '<span class="project-name h300">ZIO HTTP</span></span><button id="mobile-menu-close" class="icon-button close"></button></div>' +
  '<div class="mobile-menu-container body-medium"><input id="mobile-scaladoc-searchbar-input" class="scaladoc-searchbar-input" type="search" placeholder="Find anything">' +
  '</input><span id="mobile-theme-toggle" class="mobile-menu-item mode"></span></div></div>' +
  '<span id="mobile-sidebar-toggle" class="floating-button"></span><div id="leftColumn" class="body-small"></div>' +
  '<div id="footer" class="body-small"><div class="left-container">Generated with</div><div class="right-container">' +
  '<a href="https://github.com/zio/zio-http"><button class="icon-button gh"></button></a><div class="text"></div></div>' +
  '<div class="text-mobile"></div></div><div id="scaladoc-searchBar"></div>' +
  '<div id="main"><div class="breadcrumbs container"><a href="../../../index.html">ZIO HTTP</a>/<a href="../../http.html">zio.http</a>/' +
  '<a href="../codec.html">zio.http.codec</a>/<a href="HttpCodec.html">HttpCodec</a></div>' +
  '<div id="content" class="body-medium"><div><div class="cover-header"><h1 class="h600">HttpCodec</h1></div></div>' +
  '<div id="footer" class="body-small mobile-footer"><div class="left-container">Generated with</div>' +
  '<div class="right-container"><a href="https://github.com/zio/zio-http"><button class="icon-button gh"></button></a>' +
  '<div class="text"></div></div><div class="text-mobile"></div></div></div></div></div></body></html>';

const PROMPT = 'Fetch https://example.dev/start.md and "follow" <it>';
const count = (s, needle) => s.split(needle).length - 1;

test('the API pages get the site navbar in place of the Scaladoc header', () => {
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.equal(count(html, '<div id="header"'), 1);
  assert.match(html, /<div id="header" class="zh-navbar">/);
  assert.match(html, /<a class="zh-navbar__brand" href="\/"><img class="zh-logo zh-logo--light" src="\/img\/zio-http-logo-tile\.png"/);
  assert.ok(!html.includes('projectVersion'), 'the version picker is gone');
  assert.ok(!html.includes('id="theme-toggle"'), "Scaladoc's toggle is replaced");
  // Scaladoc's scripts look these up by id at startup and throw without them.
  for (const id of ['search-toggle', 'mobile-menu-toggle', 'dropdown-trigger', 'version-dropdown', 'mobile-theme-toggle', 'mobile-menu-close'])
    assert.equal(count(html, `id="${id}"`), 1, id);
});

test('the navbar links match the Docusaurus navbar, with API as the current page', () => {
  // A `type: 'doc'` item links to its doc's URL: the docs are served at the site root, and index.md has the slug
  // /introduction.
  const docUrl = (docId) => (docId === 'index' ? '/introduction' : `/${docId}`);
  // The GitHub and Discord items are icon-only on the docs (no label), but the API pages keep their text (NAV_LINKS).
  const iconLabels = { 'header-github-link': 'GitHub', 'header-discord-link': 'Discord' };
  const site = config.themeConfig.navbar.items.map((i) => ({
    label: i.label ?? iconLabels[i.className],
    href: i.type === 'doc' ? docUrl(i.docId) : (i.to ?? i.href).replace('pathname://', ''),
    position: i.position,
  }));
  assert.deepEqual(NAV_LINKS.map(({ label, href, position }) => ({ label, href, position })), site);
  const nav = navbarHtml({ prompt: PROMPT });
  assert.match(nav, /<a class="zh-navbar__link zh-navbar__link--active" href="\/api\/" aria-current="page">API<\/a>/);
  assert.match(nav, /<a class="zh-navbar__link" href="https:\/\/github\.com\/zio\/zio-http" target="_blank" rel="noopener noreferrer">GitHub<svg/);
  assert.match(nav, /<a class="zh-navbar__link" href="https:\/\/discord\.gg\/2ccFBr4" target="_blank" rel="noopener noreferrer">Discord<svg/);
  // Left items follow the brand, right items come before the onboard button, the colour-mode toggle and search.
  const left = nav.slice(nav.indexOf('zh-navbar__left'), nav.indexOf('zh-navbar__right'));
  assert.ok(left.includes('>Examples</a>') && !left.includes('>FAQ</a>'));
  const right = nav.slice(nav.indexOf('zh-navbar__right'));
  assert.ok(right.indexOf('>GitHub') < right.indexOf('zh-onboard') && right.indexOf('zh-mode') < right.indexOf('search-toggle'));
});

test('the navbar logo and title are the ones docusaurus.config.js names', () => {
  const { navbar } = config.themeConfig;
  assert.equal(TITLE, navbar.title);
  assert.deepEqual(LOGO, { alt: navbar.logo.alt, light: `/${navbar.logo.src}`, dark: `/${navbar.logo.srcDark}` });
});

test('the onboard button carries the site prompt, escaped for the attribute', async () => {
  assert.ok(
    navbarHtml({ prompt: PROMPT }).includes('data-onboard-prompt="Fetch https://example.dev/start.md and &quot;follow&quot; &lt;it>"'),
  );
  const { PROMPT: site } = await import('../src/components/OnboardAgentButton/prompt.mjs');
  assert.match(site, /^Fetch https:\/\/ziohttp\.com\/start\.md /);
});

test('the mobile menu gets the site logo and links, and the stylesheet comes last in <head>', () => {
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.match(html, /<a class="mobile-menu-logo" href="\/"><img [^>]*>.*?<b class="zh-navbar__title">ZIO HTTP<\/b><\/a><button id="mobile-menu-close"/);
  assert.match(
    html,
    /<nav class="zh-mobile-links"><a class="zh-mobile-link" href="\/introduction">Getting Started<\/a>.*<a class="zh-mobile-link zh-mobile-link--active" href="\/api\/" aria-current="page">API<\/a>.*<\/nav><span id="mobile-theme-toggle"/,
  );
  assert.ok(html.includes(`<link rel="stylesheet" href="${STYLESHEET}"></head>`));
  assert.equal(count(html, 'project-name'), 0);
});

test('the API pages use the site favicon, the one docusaurus.config.js names', () => {
  assert.equal(FAVICON, `/${config.favicon}`);
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.equal(count(html, 'rel="icon"'), 1);
  assert.equal(count(html, 'rel="shortcut icon"'), 0);
  assert.ok(html.includes(`<link rel="icon" href="${FAVICON}"><script`));
  assert.ok(!html.includes('favicon.ico'));
});

test('the breadcrumbs read Home > API > packages > page, at the top of the text column', () => {
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.ok(!html.includes('class="breadcrumbs container"'), "Scaladoc's trail is gone");
  const nav = /<div id="content" class="body-medium"><div>(<nav class="zh-breadcrumbs" aria-label="Breadcrumbs">.*?<\/nav>)<div class="cover-header">/.exec(html);
  assert.ok(nav, 'the breadcrumbs open the text column');
  const crumbs = [...nav[1].matchAll(/<li class="([^"]*)">(?:<a (?:aria-label="Home page" )?class="zh-breadcrumbs__link" href="([^"]*)">(?:<svg[^]*?<\/svg>|<span>([^<]*)<\/span>)<\/a>|<span class="zh-breadcrumbs__link">([^<]*)<\/span>)<\/li>/g)]
    .map(([, cls, href, label, current]) => (current ? `[${current}]` : `${label ?? 'home'} ${href}`) + (cls.includes('--active') ? ' (active)' : ''));
  assert.deepEqual(crumbs, ['home /', 'API /api/', 'zio.http ../../http', 'codec ../codec', '[HttpCodec] (active)']);
});

test('on the API index the trail ends at API', () => {
  assert.ok(
    breadcrumbsHtml([{ href: 'index.html', label: 'ZIO HTTP' }]).endsWith(
      '<li class="zh-breadcrumbs__item zh-breadcrumbs__item--active"><span class="zh-breadcrumbs__link">API</span></li></ul></nav>',
    ),
  );
});

test('the side menu must have zio.http as its only root', () => {
  const pkg = (name, children = []) => ({ name, location: name.split('.'), kind: 'package', children });
  const menu = { docs: null, api: { name: 'ZIO HTTP', location: ['index'], kind: null, children: [pkg('zio.http')] } };
  assert.equal(checkSideMenu(menu), menu);
  assert.throws(() => checkSideMenu({ api: { children: [pkg('zio.http'), pkg('zio.blocks')] } }), /only API root/);
});

test('API URLs have no .html, and an index page is its folder', () => {
  const cases = {
    'Body.html': 'Body',
    '../codec/HttpCodec.html#map-1': '../codec/HttpCodec#map-1',
    'zio/http/template.html': 'zio/http/template',
    'MediaType$.html#text.html-0': 'MediaType$#text.html-0',
    'Other#anchor.html': 'Other#anchor.html',
    '../../../index.html': '../../../',
    'index.html#': './#',
    'Body.scala': 'Body.scala',
  };
  for (const [from, to] of Object.entries(cases)) assert.equal(extensionless(from), to, from);
});

test('only relative links lose .html; the site and other sites keep theirs', () => {
  assert.equal(
    extensionlessLinks(
      '<a href="Body.html">c</a><a href="#m">m</a><a href="/api/">api</a>' +
        '<a href="https://docs.oracle.com/javase/21/docs/api/java.base/java/lang/Object.html">o</a><a href="//cdn.example/x.html">x</a>',
    ),
    '<a href="Body">c</a><a href="#m">m</a><a href="/api/">api</a>' +
      '<a href="https://docs.oracle.com/javase/21/docs/api/java.base/java/lang/Object.html">o</a><a href="//cdn.example/x.html">x</a>',
  );
  assert.match(themeApiPage(PAGE, { prompt: PROMPT }), /href="\.\.\/\.\.\/http"><span>zio\.http<\/span>/);
});

test('search results and side-menu links are extensionless too', () => {
  assert.equal(
    extensionlessSearchData(
      'pages = [{"l":"index.html#","n":"ZIO HTTP"},{"l":"zio/http/Body.html#","n":"Body"},' +
        '{"l":"zio/http/Body.html#map-1","d":"zio.http.html"}]',
    ),
    'pages = [{"l":"./","n":"ZIO HTTP"},{"l":"zio/http/Body","n":"Body"},' + '{"l":"zio/http/Body#map-1","d":"zio.http.html"}]',
  );
  const ux = extensionlessUxJs('function linkTo(loc) {\n    return `${baseUrl.join("/")}/${loc.join("/")}.html`;\n  }');
  const linkTo = new Function('baseUrl', ux.replace(/^function linkTo\(loc\) \{|\}$/g, '').replace('return', 'return (loc) =>'));
  assert.equal(linkTo(['', 'api'])(['zio', 'http', 'Body']), '/api/zio/http/Body');
  assert.equal(linkTo(['', 'api'])(['index']), '/api/');
  assert.throws(() => extensionlessUxJs('return `${baseUrl}/${loc.join("/")}.html`;'), /side-menu links/);
});

// A side menu shaped like Scaladoc's: nested types are children of the type they are declared in.
const node = (location, kind, children = []) => ({ name: location.split('/').pop(), location: location.split('/'), kind, children });
const NESTED = { docs: null, api: { name: 'ZIO HTTP', location: ['index'], kind: null, children: [
  node('zio/http', 'package', [
    node('zio/http/Body', 'trait'),
    node('zio/http/Body$', 'object', [node('zio/http/Body$$ContentType', 'class')]),
    node('zio/http/Header$', 'object', [node('zio/http/Header$$ContentDisposition$', 'object', [
      node('zio/http/Header$$ContentDisposition$$Attachment', 'class'),
    ])]),
    node('zio/http/Version$', 'object', [node('zio/http/Version$$HTTP$div1/0$$', 'object')]),
    node('zio/http/codec', 'package', [
      node('zio/http/codec/Doc$', 'object', [node('zio/http/codec/Doc$$$bar$', 'object')]),
    ]),
  ]),
] } };

test('nested types move into folders named after the type they are declared in', () => {
  assert.deepEqual(Object.fromEntries(nestedTypePaths(NESTED)), {
    'zio/http/Body$$ContentType': 'zio/http/Body/ContentType',
    'zio/http/Header$$ContentDisposition$': 'zio/http/Header/ContentDisposition$',
    'zio/http/Header$$ContentDisposition$$Attachment': 'zio/http/Header/ContentDisposition/Attachment',
    'zio/http/Version$$HTTP$div1/0$$': 'zio/http/Version/HTTP$div1/0$$',
    'zio/http/codec/Doc$$$bar$': 'zio/http/codec/Doc/$bar$',
  });
});

test('a moved page is rewritten for its new folder, and links to moved pages follow them', () => {
  const moves = nestedTypePaths(NESTED);
  const page = (rawLocation, root, body) =>
    `<html data-pathToRoot="${root}" data-rawLocation="${rawLocation}"><head><script>var pathToRoot = "${root}";</script>` +
    `<link rel="stylesheet" href="${root}styles/a.css"></head><body>${body}</body></html>`;
  const moved = relocatePage(
    page('zio/http/Body$$ContentType', '../../', '<a href="Body.html">up</a><a href="Header$.html#m">h</a><a href="#m">m</a><a href="/api/">api</a>'),
    { from: 'zio/http/Body$$ContentType', to: 'zio/http/Body/ContentType', moves },
  );
  assert.equal(
    moved,
    page('zio/http/Body/ContentType', '../../../', '<a href="../Body.html">up</a><a href="../Header$.html#m">h</a><a href="#m">m</a><a href="/api/">api</a>'),
  );
  const staying = '<a href="Body$$ContentType.html#x">CT</a><a href="Body.html">b</a><a href="../../index.html">i</a>';
  assert.equal(
    relocatePage(staying, { from: 'zio/http/Body$', to: 'zio/http/Body$', moves }),
    '<a href="Body/ContentType.html#x">CT</a><a href="Body.html">b</a><a href="../../index.html">i</a>',
  );
});

test('the side menu and the search index point at the moved pages', () => {
  const moves = nestedTypePaths(NESTED);
  const locations = [];
  const walk = (i) => (locations.push(i.location.join('/')), i.children.forEach(walk));
  relocateSideMenu(NESTED, moves).api.children.forEach(walk);
  assert.ok(locations.includes('zio/http/Body/ContentType'));
  assert.ok(!locations.some((l) => l.includes('Body$$')));
  assert.equal(
    extensionlessSearchData('{"l":"zio/http/Body$$ContentType.html#"},{"l":"zio/http/Body$$ContentType.html#render-1"}', moves),
    '{"l":"zio/http/Body/ContentType"},{"l":"zio/http/Body/ContentType#render-1"}',
  );
});

test('both "Generated with Scaladoc" footers are gone, and nothing after them', () => {
  const html = themeApiPage(PAGE, { prompt: PROMPT });
  assert.ok(!html.includes('id="footer"'));
  assert.ok(!html.includes('Generated with'));
  assert.ok(html.endsWith('<h1 class="h600">HttpCodec</h1></div></div></div></div></div></body></html>'));
});

test('the API index sends /api to /api/ before anything loads', () => {
  const html = folderIndexRedirect(PAGE.replace(/^<!DOCTYPE html><html[^>]*>/, '<html>'));
  const script = /^<html><head><script>(.*?)<\/script><base href="\/api\/"><meta charset/.exec(html);
  assert.ok(script, 'the redirect is the first thing in <head>');
  const run = (pathname) => {
    let target = null;
    new Function('location', script[1])({ pathname, search: '?q=1', hash: '#x', replace: (u) => (target = u) });
    return target;
  };
  assert.equal(run('/api'), '/api/?q=1#x');
  assert.equal(run('/api/'), null);
});

test('a page whose markup no longer matches fails loudly', () => {
  assert.throws(() => themeApiPage(PAGE.replace('<div id="mobile-menu">', '<div id="menu">'), { prompt: PROMPT }), /header/);
  assert.throws(() => themeApiPage(PAGE.replace('<span id="mobile-theme-toggle"', '<span'), { prompt: PROMPT }), /mobile theme toggle/);
});

// Runs src/api-theme/theme.js against a minimal browser stand-in.
function loadThemeScript({ stored = null } = {}) {
  const store = new Map(stored ? [['theme', stored]] : []);
  const classes = new Set();
  const attrs = {};
  const listeners = {};
  const toggle = { title: '', attrs: {}, setAttribute(k, v) { this.attrs[k] = v; }, addEventListener(_, f) { this.click = f; } };
  const document = {
    documentElement: {
      classList: { toggle: (c, on) => (on ? classes.add(c) : classes.delete(c)) },
      setAttribute: (k, v) => (attrs[k] = v),
    },
    querySelectorAll: () => [toggle],
    getElementById: () => null,
    addEventListener() {},
  };
  const window = { addEventListener: (type, f) => (listeners[type] = f) };
  const localStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, v),
    removeItem: (k) => store.delete(k),
  };
  vm.runInNewContext(readFileSync(new URL('../src/api-theme/theme.js', import.meta.url), 'utf8'), {
    window, document, localStorage, Element: class {},
  });
  listeners.DOMContentLoaded();
  return { store, classes, attrs, toggle };
}

test('the colour mode is the one Docusaurus stores, light when nothing is stored, as docusaurus.config.js sets it', () => {
  // colorMode left at Docusaurus' defaults: light, not following the OS.
  assert.ok(!config.themeConfig.colorMode?.respectPrefersColorScheme);
  assert.ok([undefined, 'light'].includes(config.themeConfig.colorMode?.defaultMode));
  const dark = loadThemeScript({ stored: 'dark' });
  assert.ok(dark.classes.has('theme-dark'));
  assert.equal(dark.attrs['data-theme'], 'dark');
  const fresh = loadThemeScript();
  assert.ok(!fresh.classes.has('theme-dark'));
  assert.equal(fresh.attrs['data-theme-choice'], 'light');
  assert.equal(fresh.toggle.title, 'light mode');
});

test('every click flips the mode, and is stored in localStorage "theme" like Docusaurus', () => {
  const t = loadThemeScript();
  const seen = [];
  for (let i = 0; i < 3; i++) {
    t.toggle.click();
    seen.push([t.store.get('theme'), t.classes.has('theme-dark')]);
  }
  assert.deepEqual(seen, [['dark', true], ['light', false], ['dark', true]]);
});
