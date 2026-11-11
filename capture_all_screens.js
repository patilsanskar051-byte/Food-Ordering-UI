const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function getJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, res => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          reject(e);
        }
      });
    }).on('error', reject);
  });
}

class CDPClient {
  constructor(wsUrl) {
    this.ws = new WebSocket(wsUrl);
    this.id = 1;
    this.callbacks = new Map();
    this.ws.onmessage = msg => {
      const parsed = JSON.parse(msg.data);
      if (parsed.id && this.callbacks.has(parsed.id)) {
        const { resolve, reject } = this.callbacks.get(parsed.id);
        this.callbacks.delete(parsed.id);
        if (parsed.error) reject(parsed.error);
        else resolve(parsed.result);
      }
    };
  }

  ready() {
    return new Promise((resolve, reject) => {
      if (this.ws.readyState === WebSocket.OPEN) return resolve();
      this.ws.onopen = () => resolve();
      this.ws.onerror = err => reject(err);
    });
  }

  send(method, params = {}) {
    return new Promise((resolve, reject) => {
      const msgId = this.id++;
      this.callbacks.set(msgId, { resolve, reject });
      this.ws.send(JSON.stringify({ id: msgId, method, params }));
    });
  }

  async eval(expression) {
    const res = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true
    });
    return res.result ? res.result.value : null;
  }

  async screenshot(outPath, fullPage = false) {
    const res = await this.send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: fullPage
    });
    const buffer = Buffer.from(res.data, 'base64');
    fs.mkdirSync(path.dirname(outPath), { recursive: true });
    fs.writeFileSync(outPath, buffer);
    console.log(`Saved screenshot: ${outPath} (${buffer.length} bytes)`);
  }

  async setViewport(width, height, isMobile = false) {
    await this.send('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: isMobile
    });
    await this.send('Emulation.setVisibleSize', { width, height }).catch(() => {});
  }
}

async function run() {
  const mode = process.argv[2] || 'before';
  const outDir = path.resolve(__dirname, 'screenshots', mode);
  fs.mkdirSync(outDir, { recursive: true });

  const tempUserData = path.join(process.env.TEMP || 'C:\\Temp', 'chrome_cdp_' + Date.now());
  const chrome = spawn(CHROME_PATH, [
    '--headless=new',
    '--remote-debugging-port=9222',
    '--disable-gpu',
    '--no-sandbox',
    `--user-data-dir=${tempUserData}`,
    'http://localhost:8080/'
  ]);

  await sleep(2500);

  try {
    const targets = await getJson('http://127.0.0.1:9222/json');
    const pageTarget = targets.find(t => t.type === 'page');
    if (!pageTarget) throw new Error('No page target found');

    const client = new CDPClient(pageTarget.webSocketDebuggerUrl);
    await client.ready();
    await client.send('Page.enable');
    await client.send('DOM.enable');

    console.log(`Connected to page. Capturing [${mode}] screenshots...`);
    await sleep(1500);

    // 1. Auth Desktop
    await client.setViewport(1440, 900, false);
    await sleep(500);
    await client.screenshot(path.join(outDir, '01_auth_desktop.png'));

    // 2. Auth Mobile
    await client.setViewport(390, 844, true);
    await sleep(500);
    await client.screenshot(path.join(outDir, '02_auth_mobile.png'));

    // Reveal Homepage by bypassing Auth screen (for inspecting the app-shell layout)
    await client.setViewport(1440, 900, false);
    await client.eval(`
      const auth = document.getElementById("crave-auth-screen");
      const shell = document.getElementById("app-shell");
      if (auth) auth.classList.add("hidden");
      if (shell) shell.classList.remove("hidden");
    `);
    await sleep(800);

    // 3. Homepage Desktop
    await client.screenshot(path.join(outDir, '03_homepage_desktop.png'));

    // 4. Homepage Mobile
    await client.setViewport(390, 844, true);
    await sleep(600);
    await client.screenshot(path.join(outDir, '04_homepage_mobile.png'));

    // 5. Menu Desktop (scroll to menu section)
    await client.setViewport(1440, 900, false);
    await client.eval(`
      const menuSection = document.getElementById("food-section");
      if (menuSection) menuSection.scrollIntoView();
    `);
    await sleep(800);
    await client.screenshot(path.join(outDir, '05_menu_desktop.png'));

    // 6. Menu Mobile
    await client.setViewport(390, 844, true);
    await client.eval(`
      const menuSection = document.getElementById("food-section");
      if (menuSection) menuSection.scrollIntoView();
    `);
    await sleep(800);
    await client.screenshot(path.join(outDir, '06_menu_mobile.png'));

    // 7. Cart & Checkout Drawer Desktop
    await client.setViewport(1440, 900, false);
    await client.eval(`
      if (typeof addToCart === 'function') {
        addToCart(2);
        addToCart(3);
      }
      if (typeof showCart === 'function') {
        showCart();
      }
    `);
    await sleep(800);
    await client.screenshot(path.join(outDir, '07_cart_checkout_desktop.png'));

    // 8. Order History Desktop
    await client.eval(`
      const cartOverlay = document.querySelector(".cart-overlay");
      if (cartOverlay) cartOverlay.remove();
      if (typeof showOrders === 'function') {
        showOrders('db');
      }
    `);
    await sleep(800);
    await client.screenshot(path.join(outDir, '08_order_history_desktop.png'));

    console.log(`Successfully completed all screenshots for [${mode}]!`);
  } catch (err) {
    console.error('Error during screenshot capture:', err);
  } finally {
    chrome.kill();
    try {
      fs.rmSync(tempUserData, { recursive: true, force: true });
    } catch (e) {}
  }
}

run();
