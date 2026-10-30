#!/usr/bin/env node
// Derives the website's icon set from the logo artwork in assets/zio-http-logo.svg
// (the request/response arrows of the homepage design). Never edit the derived
// files by hand: change the constants here and re-run.
//
//   node website/scripts/generate-icons.mjs
//
// Needs a Chromium or Chrome binary (CHROMIUM=/path/to/chromium to override);
// it rasterises the SVG, so no image library is required.
import { spawn } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const website = join(dirname(fileURLToPath(import.meta.url)), '..');
const img = join(website, 'static', 'img');
const chromium = process.env.CHROMIUM || 'chromium';

const ARROWS = '#3DDC97';
const TILE = '#0B1020';
const paths =
  '<path d="M5 11.5H25"/><path d="M20.5 7L25 11.5L20.5 16"/><path d="M27 21.5H7"/><path d="M11.5 17L7 21.5L11.5 26"/>';

const svg = (background) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">${background}` +
  `<g fill="none" stroke="${ARROWS}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">${paths}</g></svg>`;

// The tile is the navbar logo in light mode and the favicon (the arrows alone
// would vanish on a light browser tab); the mark is the dark-mode navbar logo.
const tile = svg(`<rect width="32" height="32" rx="7" fill="${TILE}"/>`);
const mark = svg('');
// iOS rounds the corners itself, so the home-screen icon is a full-bleed square.
const square = svg(`<rect width="32" height="32" fill="${TILE}"/>`);

// Chromium is driven over the DevTools protocol: its --screenshot flag cannot
// make a transparent image of an exact size.
const PORT = 9341;
let socket;
let nextId = 0;
const pending = new Map();

async function connect() {
  const browser = spawn(chromium, ['--headless=new', '--no-sandbox', `--remote-debugging-port=${PORT}`, 'about:blank'], { stdio: 'ignore' });
  for (let i = 0; i < 50 && !socket; i++) {
    await new Promise((r) => setTimeout(r, 200));
    try {
      const tabs = await (await fetch(`http://localhost:${PORT}/json`)).json();
      const page = tabs.find((t) => t.type === 'page');
      if (page) {
        socket = new WebSocket(page.webSocketDebuggerUrl);
        await new Promise((r, e) => { socket.onopen = r; socket.onerror = e; });
      }
    } catch (_) {
      socket = undefined;
    }
  }
  if (!socket) throw new Error(`could not reach ${chromium}`);
  socket.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.id && pending.has(m.id)) pending.get(m.id)(m.result);
  };
  return browser;
}

const send = (method, params = {}) =>
  new Promise((resolve) => {
    const id = ++nextId;
    pending.set(id, resolve);
    socket.send(JSON.stringify({ id, method, params }));
  });

async function render(source, size) {
  await send('Emulation.setDeviceMetricsOverride', { width: size, height: size, deviceScaleFactor: 1, mobile: false });
  await send('Emulation.setDefaultBackgroundColorOverride', { color: { r: 0, g: 0, b: 0, a: 0 } });
  const html = `<!doctype html><style>html,body{margin:0;overflow:hidden}svg{display:block;width:${size}px;height:${size}px}</style>${source}`;
  await send('Page.navigate', { url: `data:text/html;base64,${Buffer.from(html).toString('base64')}` });
  await new Promise((r) => setTimeout(r, 300));
  const { data } = await send('Page.captureScreenshot', { format: 'png' });
  return Buffer.from(data, 'base64');
}

// A .ico is a small directory followed by the images; PNG-compressed entries are valid since Vista.
function ico(pngs) {
  const head = Buffer.alloc(6 + 16 * pngs.length);
  head.writeUInt16LE(1, 2);
  head.writeUInt16LE(pngs.length, 4);
  let offset = head.length;
  pngs.forEach(({ size, data }, i) => {
    const at = 6 + 16 * i;
    head.writeUInt8(size, at);
    head.writeUInt8(size, at + 1);
    head.writeUInt16LE(1, at + 4);
    head.writeUInt16LE(32, at + 6);
    head.writeUInt32LE(data.length, at + 8);
    head.writeUInt32LE(offset, at + 12);
    offset += data.length;
  });
  return Buffer.concat([head, ...pngs.map((p) => p.data)]);
}

const browser = await connect();
try {
  const write = (name, data) => writeFileSync(join(img, name), data);
  write('zio-http-logo-tile.png', await render(tile, 128));
  write('zio-http-logo-mark.png', await render(mark, 128));
  write('favicon.png', await render(tile, 192));
  write('apple-touch-icon.png', await render(square, 180));
  const sizes = [];
  for (const size of [16, 32, 48]) sizes.push({ size, data: await render(tile, size) });
  write('favicon.ico', ico(sizes));
} finally {
  browser.kill();
}
process.exit(0);
