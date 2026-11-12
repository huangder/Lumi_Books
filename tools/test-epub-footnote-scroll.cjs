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
  const touch = (type, point) => send('Input.dispatchTouchEvent', { type, touchPoints: point ? [point] : [] });
  const swipe = async (from, to, checkFrame, release = 'touchEnd') => {
    await touch('touchStart', from);
    for (let step = 1; step <= 12; step++) {
      await touch('touchMove', {
        x: from.x + (to.x - from.x) * step / 12,
        y: from.y + (to.y - from.y) * step / 12
      });
      await wait(20); // Allow Chromium to present the move before measuring it.
      await checkFrame?.();
    }
    await touch(release);
    await wait(350);
  };
  const bounds = (id) => evaluate(`document.getElementById('${id}').getBoundingClientRect().toJSON()`);
  const point = (x, y) => ({ x, y });
  const center = (rect) => point(rect.x + rect.width / 2, rect.y + rect.height / 2);
  const tap = async (at) => {
    await touch('touchStart', at);
    await touch('touchEnd');
    await wait(350);
  };
  const readingPosition = () => evaluate(`(() => {
    const body = document.body.getBoundingClientRect(), line = document.getElementById('reading-line').getBoundingClientRect();
    return [window.scrollX, window.scrollY, visualViewport.pageLeft, visualViewport.pageTop,
      body.x, body.y, line.x, line.y, LumiReader.currentPosition().pageIndex];
  })()`);
  const scrollTop = () => evaluate('document.getElementById("lumi-footnote-content").scrollTop');
  const checkNoReadingActions = async () => {
    assert.deepEqual(await evaluate(`messages.filter(m =>
      ['page', 'chapterTurn', 'bookEnd', 'tap', 'image', 'selection'].includes(m.type)).map(m => m.type)`), []);
  };
  for (const layout of ['reflowable', 'pre_paginated']) for (const flow of ['paginated', 'scrolled']) {
    const file = path.join(directory, `${layout}-${flow}.html`);
    fs.writeFileSync(file, `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
      <style>${constant('READER_CSS')}</style></head><body data-lumi-layout="${layout}">
      <p id="reading-line"><a id="note" epub:type="noteref" href="#fn">[1]</a>
        <a id="short-note" epub:type="noteref" href="#fn2">[2]</a> Reading page</p>
      ${'<p style="padding-block: 16px">Reading surface with enough text to fill several pages.</p>'.repeat(60)}
      <aside id="fn" epub:type="footnote">${'<p>Long footnote content for scrolling.</p>'.repeat(80)}</aside>
      <aside id="fn2" epub:type="footnote"><p>Short footnote.</p></aside>
      <script>window.messages=[];window.lumiNative={postMessage:v=>messages.push(JSON.parse(v))};</script>
      <script>${script}</script></body></html>`);
    await send('Page.navigate', { url: pathToFileURL(file).href });
    for (let n = 0; n < 100; n++) {
      if (await evaluate('!!window.LumiReader')) break;
      await wait(50);
    }
    await evaluate(`window.LumiReader.configure({flow:'${flow}',nativePaging:false,transition:'slide',insets:{top:0,right:0,bottom:0,left:0}})`);
    await wait(400);
    if (layout === 'reflowable') {
      assert.ok(await evaluate('LumiReader.currentPosition().pageCount > 1'), 'Reading fixture must have multiple pages');
    }
    const rootAction = () => evaluate('getComputedStyle(document.documentElement).touchAction');
    const normalTouchAction = await rootAction();
    const reading = await readingPosition();
    const checkPage = async () => assert.deepEqual(await readingPosition(), reading, 'Reading page moved during the stroke');
    const checkClosed = async () => {
      await checkPage();
      assert.ok(await evaluate('!document.getElementById("lumi-footnote-popover") && messages.some(m=>m.type==="footnotePopover"&&!m.payload.visible)'));
      assert.equal(await rootAction(), normalTouchAction, 'Closing must restore the normal touch behavior');
      await checkNoReadingActions();
    };
    const open = async (anchor) => {
      await evaluate('messages.length=0');
      await tap(center(await bounds(anchor)));
      assert.ok(await evaluate('messages.some(m=>m.type==="footnotePopover"&&m.payload.visible)'));
      await evaluate('messages.length=0');
      assert.equal(await rootAction(), 'none');
      const initial = await bounds('lumi-footnote-popover');
      return async () => {
        await checkPage();
        assert.deepEqual(await bounds('lumi-footnote-popover'), initial, 'Footnote moved during the stroke');
      };
    };
    let checkFrame = await open('note');
    const content = await bounds('lumi-footnote-content');
    const bottom = point(content.x + content.width / 2, content.bottom - 25);
    const top = point(bottom.x + 22, content.top + 25);
    await swipe(bottom, top, checkFrame);
    assert.ok(await scrollTop() > 30, 'Upward diagonal swipe must scroll the note');
    const scrolled = await scrollTop();
    await swipe(center(content), point(content.right - 5, center(content).y + 6), checkFrame);
    assert.equal(await scrollTop(), scrolled, 'Horizontal swipe must not scroll the note');
    await swipe(top, bottom, checkFrame);
    assert.equal(await scrollTop(), 0, 'Reverse swipe must return to the top');
    await swipe(top, bottom, checkFrame);
    assert.equal(await scrollTop(), 0, 'Top boundary must not pan the page');
    const maximum = await evaluate(`(() => {
      const content = document.getElementById('lumi-footnote-content');
      content.scrollTop = content.scrollHeight;
      return content.scrollTop;
    })()`);
    await swipe(bottom, top, checkFrame);
    assert.equal(await scrollTop(), maximum, 'Bottom boundary must not pan the page');
    await checkNoReadingActions();
    await swipe(point(460, 740), point(390, 470), checkFrame, 'touchCancel');
    await checkFrame();
    await swipe(point(460, 740), point(390, 470), checkFrame);
    await checkClosed();

    checkFrame = await open('short-note');
    const shortContent = await bounds('lumi-footnote-content');
    const shortStart = center(shortContent);
    await swipe(shortStart, point(shortStart.x + 18, shortStart.y + 150), checkFrame);
    assert.equal(await scrollTop(), 0);
    await tap(point(460, 740));
    await checkClosed();

    if (layout === 'reflowable' && flow === 'scrolled') {
      await swipe(point(460, 740), point(460, 400));
      assert.ok(await evaluate('window.scrollY > 30'), 'A fresh swipe must scroll the reading page');
    }
    if (layout === 'reflowable' && flow === 'paginated') {
      await swipe(point(420, 650), point(80, 650));
      assert.equal(await evaluate('LumiReader.currentPosition().pageIndex'), 1, 'A fresh swipe must turn the reading page');
    }
    console.log(`PASS: ${layout}/${flow}: long/short notes, frame-by-frame page isolation, cancellation and dismissal`);
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
