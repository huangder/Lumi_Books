// Trusted Chromium touch input against production EPUB CSS/JS; no device APKs.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawn } = require('node:child_process');
const source = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const constant = name => source.match(new RegExp('private const val ' + name + ' = """([\\s\\S]*?)"""'))[1];
const assembly = source.match(/private val READER_SCRIPT:[\s\S]*?private const val READER_SCRIPT_PART_/)[0];
const script = [...assembly.matchAll(/append\((READER_SCRIPT_PART_\w+)\)/g)].map(m => constant(m[1])).join('');
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const directory = fs.mkdtempSync(path.join(tmpdir(), 'lumi-footnote-scroll-'));
let browser, socket;

async function main() {
  browser = spawn(process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--remote-debugging-port=0', '--user-data-dir=' + directory, 'about:blank'
  ], { windowsHide: true, stdio: 'ignore' });
  const portFile = path.join(directory, 'DevToolsActivePort');
  for (let n = 0; !fs.existsSync(portFile) && n < 100; n++) await wait(100);
  assert.ok(fs.existsSync(portFile), 'Browser debugging port did not become available');
  const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
  const pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  socket = new WebSocket(pages.find(p => p.type === 'page').webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  let serial = 0;
  const pending = new Map();
  socket.onmessage = event => {
    const data = JSON.parse(event.data);
    const request = pending.get(data.id);
    if (!request) return;
    pending.delete(data.id);
    clearTimeout(request.timer);
    if (data.error) request.reject(Error(JSON.stringify(data.error)));
    else request.resolve(data.result);
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++serial;
    const timer = setTimeout(() => { pending.delete(id); reject(Error(method + ' timed out')); }, 10000);
    pending.set(id, { resolve, reject, timer });
    socket.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const response = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    assert.ok(!response.exceptionDetails, JSON.stringify(response.exceptionDetails));
    return response.result.value;
  };
  await send('Emulation.setDeviceMetricsOverride', { width: 480, height: 800, deviceScaleFactor: 1, mobile: true });
  await send('Emulation.setTouchEmulationEnabled', { enabled: true });
  await send('Page.enable');
  const swipe = async (x, from, to) => {
    await send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y: from }] });
    for (let step = 1; step <= 12; step++) {
      await send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y: from + (to - from) * step / 12 }] });
      await wait(20);
    }
    await send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await wait(350);
  };
  for (const layout of ['reflowable', 'pre_paginated']) for (const flow of ['paginated', 'scrolled']) {
    const file = path.join(directory, `${layout}-${flow}.html`);
    fs.writeFileSync(file, `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
      <style>${constant('READER_CSS')}</style></head><body data-lumi-layout="${layout}">
      <p><a id="note" epub:type="noteref" href="#fn">[1]</a> Reading page</p>
      <aside id="fn" epub:type="footnote">${'<p>Long footnote content for scrolling.</p>'.repeat(80)}</aside>
      <script>window.messages=[];window.lumiNative={postMessage:v=>messages.push(JSON.parse(v))};</script>
      <script>${script}</script></body></html>`);
    await send('Page.navigate', { url: pathToFileURL(file).href });
    for (let n = 0; n < 100; n++) {
      if (await evaluate('!!window.LumiReader')) break;
      await wait(50);
    }
    await evaluate(`window.LumiReader.configure({flow:'${flow}',nativePaging:true,transition:'none',insets:{top:0,right:0,bottom:0,left:0}})`);
    await wait(400);
    await evaluate('document.getElementById("note").click()');
    await wait(350);
    const geometry = await evaluate(`(() => {
      const content=document.getElementById('lumi-footnote-content'), r=content.getBoundingClientRect();
      return {x:(r.left+r.right)/2,from:r.bottom-25,to:r.top+25,
        rootAction:getComputedStyle(document.documentElement).touchAction,
        scrollable:content.scrollHeight>content.clientHeight,
        open:messages.some(m=>m.type==='footnotePopover'&&m.payload.visible)};
    })()`);
    assert.ok(geometry.open, 'Opening must notify the native gesture host');
    assert.equal(geometry.rootAction, 'pan-y', 'Root must allow the nested note to scroll');
    assert.ok(geometry.scrollable);
    await swipe(geometry.x, geometry.from, geometry.to);
    assert.ok(await evaluate('document.getElementById("lumi-footnote-content").scrollTop > 30'), `${layout}/${flow}: upward swipe must scroll note`);
    await evaluate('messages.length=0');
    await swipe(geometry.x, geometry.to, geometry.from);
    assert.ok(await evaluate('!messages.some(m=>["tap","chapterTurn","image","selection"].includes(m.type))'), 'Note swipe must not trigger reading actions');
    // Outside input dismisses the note without turning the reading page.
    await evaluate('document.body.dispatchEvent(new MouseEvent("click",{bubbles:true,cancelable:true,clientX:450,clientY:750}))');
    await wait(350);
    assert.ok(await evaluate('!document.getElementById("lumi-footnote-popover") && messages.some(m=>m.type==="footnotePopover"&&!m.payload.visible)'));
    assert.ok(await evaluate('!document.documentElement.classList.contains("lumi-footnote-open")'));
    if (flow === 'paginated' || layout === 'pre_paginated') {
      assert.equal(await evaluate('getComputedStyle(document.documentElement).touchAction'), 'none');
    }
    console.log(`PASS: ${layout}/${flow}: trusted note scroll, gesture isolation, dismissal and paging restoration`);
  }
  await send('Browser.close');
}

main().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
  if (socket) socket.close();
  if (browser && browser.exitCode === null) {
    browser.kill();
    await wait(500);
  }
  assert.equal(path.dirname(path.resolve(directory)), path.resolve(tmpdir()));
  fs.rmSync(directory, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
});
