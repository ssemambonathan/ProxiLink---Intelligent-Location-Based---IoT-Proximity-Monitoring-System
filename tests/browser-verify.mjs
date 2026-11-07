// Independent browser verification for the ProxiLink dashboard.
// Drives headless Chrome over the DevTools Protocol, captures screenshots and
// reports console errors / failed requests. No external dependencies.

import { spawn } from 'node:child_process';
import { mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';

const CHROME = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const BASE = process.env.DASHBOARD_URL || 'http://localhost:5173';
const API = process.env.API_URL || 'http://localhost:8080';
const OUT = path.resolve('documentation/screenshots');
const PORT = 9333;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(email, password) {
  const res = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`login ${email} -> ${res.status}`);
  return res.json();
}

class Cdp {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    this.handlers = new Map();
    ws.addEventListener('message', (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
        return;
      }
      if (msg.method) {
        for (const fn of this.handlers.get(msg.method) ?? []) fn(msg.params);
      }
    });
  }
  on(method, fn) {
    const list = this.handlers.get(method) ?? [];
    list.push(fn);
    this.handlers.set(method, list);
  }
  send(method, params = {}) {
    const id = ++this.id;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }
}

async function openPage() {
  for (let i = 0; i < 60; i++) {
    try {
      const pages = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
      const page = pages.find((p) => p.type === 'page');
      if (page) return page;
    } catch {
      /* chrome still starting */
    }
    await sleep(250);
  }
  throw new Error('Chrome DevTools endpoint did not come up');
}

async function main() {
  await mkdir(OUT, { recursive: true });
  const profile = path.join(tmpdir(), `proxilink-chrome-${Date.now()}`);
  const chrome = spawn(CHROME, [
    '--headless=new',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${profile}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-gpu',
    '--hide-scrollbars',
    '--window-size=1440,1000',
    'about:blank',
  ], { stdio: 'ignore' });

  const results = [];
  let current = { route: 'startup', consoleErrors: [], failedRequests: [] };

  try {
    const page = await openPage();
    const ws = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      ws.addEventListener('open', resolve, { once: true });
      ws.addEventListener('error', reject, { once: true });
    });
    const cdp = new Cdp(ws);
    await cdp.send('Page.enable');
    await cdp.send('Runtime.enable');
    await cdp.send('Log.enable');
    await cdp.send('Network.enable');

    cdp.on('Runtime.consoleAPICalled', (p) => {
      if (p.type === 'error') current.consoleErrors.push(p.args.map((a) => a.value ?? a.description ?? '').join(' ').slice(0, 300));
    });
    cdp.on('Log.entryAdded', (p) => {
      if (p.entry.level === 'error') current.consoleErrors.push(`${p.entry.source}: ${p.entry.text}`.slice(0, 300));
    });
    cdp.on('Network.loadingFailed', (p) => {
      if (p.canceled) return;
      current.failedRequests.push(`${p.type} ${p.errorText}`.slice(0, 200));
    });

    const evaluate = async (expression) => {
      const r = await cdp.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture: true });
      if (r.exceptionDetails) throw new Error(r.exceptionDetails.text);
      return r.result.value;
    };

    const setSession = async (auth) => {
      const session = {
        accessToken: auth.accessToken,
        refreshToken: auth.refreshToken,
        expiresAt: Date.now() + auth.expiresInSeconds * 1000,
        user: auth.user,
      };
      await evaluate(`localStorage.setItem('proxilink.session.v1', ${JSON.stringify(JSON.stringify(session))}); true`);
    };

    const visit = async (name, route, opts = {}) => {
      current = { route, consoleErrors: [], failedRequests: [] };
      await cdp.send('Emulation.setDeviceMetricsOverride', {
        width: opts.width ?? 1440,
        height: opts.height ?? 1000,
        deviceScaleFactor: 1,
        mobile: Boolean(opts.mobile),
      });
      if (opts.blockApi) await cdp.send('Network.setBlockedURLs', { urls: [`${BASE}/api/v1/*`] });
      else await cdp.send('Network.setBlockedURLs', { urls: [] });
      await cdp.send('Page.navigate', { url: `${BASE}${route}` });
      await sleep(opts.wait ?? 3000);
      const state = await evaluate(`(() => ({
        url: location.pathname,
        title: document.title,
        text: document.body.innerText.slice(0, 6000),
        headings: Array.from(document.querySelectorAll('h1,h2')).map((e) => e.innerText).filter(Boolean).slice(0, 12),
        buttons: Array.from(document.querySelectorAll('button')).map((e) => e.innerText || e.getAttribute('aria-label')).filter(Boolean).slice(0, 30),
      }))()`);
      const shot = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
      const file = path.join(OUT, `${name}.png`);
      await writeFile(file, Buffer.from(shot.data, 'base64'));
      results.push({ name, route, url: state.url, title: state.title, headings: state.headings, text: state.text, buttons: state.buttons, screenshot: path.relative(process.cwd(), file), consoleErrors: current.consoleErrors, failedRequests: current.failedRequests });
      console.log(`captured ${name} -> ${state.url} (${current.consoleErrors.length} console errors)`);
    };

    // --- Administrator session ---
    const admin = await login('admin@proxilink.dev', 'ProxiLink!Demo2026');
    await cdp.send('Page.navigate', { url: `${BASE}/login` });
    await sleep(1500);
    await setSession(admin);
    await visit('01-overview', '/');
    await visit('02-live', '/live');
    await visit('03-map', '/map', { wait: 5000 });
    await visit('04-devices', '/devices');
    await visit('05-analytics', '/analytics', { wait: 4000 });
    await visit('06-profile', '/profile');
    await visit('07-admin-users', '/admin/users');
    await visit('08-admin-audit', '/admin/audit');
    await visit('09-admin-settings', '/admin/settings');
    await visit('10-overview-mobile', '/', { width: 390, height: 844, mobile: true });
    await visit('11-live-mobile', '/live', { width: 390, height: 844, mobile: true });
    await visit('12-api-unreachable', '/', { blockApi: true, wait: 3500 });
    await cdp.send('Network.setBlockedURLs', { urls: [] });

    // --- Standard user must be denied admin routes ---
    const user = await login('demo@proxilink.dev', 'ProxiLink!Demo2026');
    await cdp.send('Page.navigate', { url: `${BASE}/login` });
    await sleep(1200);
    await setSession(user);
    await visit('13-user-forbidden', '/admin/users');

    // --- Session expiry: bogus token must bounce to login ---
    current = { route: '/live (expired token)', consoleErrors: [], failedRequests: [] };
    await evaluate(`(() => { const s = JSON.parse(localStorage.getItem('proxilink.session.v1')); s.accessToken = 'expired.invalid.token'; s.refreshToken = 'invalid'; localStorage.setItem('proxilink.session.v1', JSON.stringify(s)); return true; })()`);
    await cdp.send('Page.navigate', { url: `${BASE}/live` });
    await sleep(8000);
    const expiredPath = await evaluate('location.pathname');
    const expiredText = await evaluate('document.body.innerText.slice(0, 800)');
    const expiredShot = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    await writeFile(path.join(OUT, '14-expired-session.png'), Buffer.from(expiredShot.data, 'base64'));
    results.push({ name: '14-expired-session', url: expiredPath, text: expiredText, consoleErrors: current.consoleErrors, failedRequests: current.failedRequests });
    console.log('expired session ->', expiredPath);

    ws.close();
  } finally {
    chrome.kill();
    await sleep(500);
    await rm(profile, { recursive: true, force: true }).catch(() => {});
  }

  await writeFile(path.join(OUT, 'browser-verification.json'), JSON.stringify(results, null, 2));
  console.log(`\nwrote ${results.length} results to ${path.join(OUT, 'browser-verification.json')}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
