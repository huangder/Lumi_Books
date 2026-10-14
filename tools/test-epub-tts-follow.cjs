// Run the production EPUB scroll/highlight code in Chromium, without an Android device.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const source = readFileSync(join(__dirname, '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
const script = [...source.matchAll(/private const val READER_SCRIPT_PART_(?:1B|[123]) = """([\s\S]*?)"""/g)].map(m => m[1]).join('');

async function verify() {
  const check = (condition, message) => { if (!condition) throw Error(message); };
  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
  try {
    const reader = window.LumiReader;
    reader.configure({ flow: 'scrolled', transition: 'none', canTurnPrevious: false, canTurnNext: false });
    await delay(300);
    const text = document.getElementById('reading').textContent;
    const highlight = paragraph => {
      const start = text.indexOf('Paragraph ' + paragraph + ':');
      reader.setTtsHighlight(start, start + 40, '#aaccff', true);
    };
    const touch = (type, y) => {
      const touches = type === 'touchend' ? [] : [new Touch({ identifier: 1, target: document.body,
        clientX: 200, clientY: y, pageX: 200, pageY: y + window.scrollY })];
      document.body.dispatchEvent(new TouchEvent(type, { touches, changedTouches: touches,
        bubbles: true, cancelable: true }));
    };
    reader.setTtsFollowEnabled(true);
    highlight(70);
    await delay(50);
    check(window.scrollY > 1000, 'The initial spoken sentence should be brought into view');
    touch('touchstart', 300);
    touch('touchmove', 180);
    // Synthetic touch events do not perform a browser drag. Move the viewport as a drag would.
    window.scrollBy(0, -200);
    const browsingY = window.scrollY;
    check(__lumiMessages.filter(m => m.type === 'manualScroll').length === 1,
      'Manual scroll must be reported before finger-up');
    highlight(90);
    await delay(50);
    check(window.scrollY === browsingY, 'A queued highlight must not move the viewport during a drag');
    touch('touchend', 180);
    highlight(100);
    await delay(100);
    check(window.scrollY === browsingY, 'Finger-up must not resume TTS following');
    reader.setTtsFollowEnabled(false);
    highlight(110);
    await delay(50);
    check(window.scrollY === browsingY, 'Native follow suspension must keep the browsing position');
    reader.setTtsFollowEnabled(true);
    highlight(110);
    await delay(50);
    check(window.scrollY > browsingY + 500, 'Explicit return must resume following even for an unchanged highlight');
    const returnedY = window.scrollY;
    touch('touchstart', 300);
    touch('touchmove', 299);
    touch('touchend', 299);
    window.scrollBy(0, -200);
    highlight(110);
    await delay(50);
    check(window.scrollY === returnedY, 'A tap without dragging must leave automatic following enabled');
    document.getElementById('result').textContent = JSON.stringify({ passed: true, checks: 7 });
  } catch (error) {
    document.getElementById('result').textContent = JSON.stringify({ passed: false, error: error.message });
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-tts-follow-'));
try {
  let checks = 0;
  for (const size of [18, 30]) {
    const file = join(directory, `follow-${size}.html`);
    const text = Array.from({ length: 120 }, (_, i) => `Paragraph ${i + 1}: reading text with a stable listening position.`).join('\n');
    writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8"><style>${css}</style>
      <style>body{font-size:${size}px!important;line-height:1.7!important}p{white-space:pre-wrap!important}</style></head>
      <body data-lumi-layout="reflowable"><p id="reading">${text}</p><pre id="result" style="display:none"></pre>
      <script>window.__lumiMessages=[];window.lumiNative={postMessage:m=>__lumiMessages.push(JSON.parse(m))};</script>
      <script>${script}</script><script>(${verify.toString()})()</script></body></html>`);
    const result = spawnSync(process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
      '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      '--user-data-dir=' + join(directory, 'profile'), '--window-size=480,700', '--virtual-time-budget=4000',
      '--dump-dom', pathToFileURL(file).href
    ], { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
    if (result.error) throw result.error;
    assert.equal(result.status, 0, result.stderr);
    const match = result.stdout.match(/<pre id="result"[^>]*>([^<]+)<\/pre>/);
    assert.ok(match, 'Missing browser report');
    const report = JSON.parse(match[1].replaceAll('&quot;', '"').replaceAll('&amp;', '&'));
    assert.ok(report.passed, `Font ${size}: ${report.error}`);
    checks += report.checks;
  }
  console.log(`PASS: ${checks} EPUB TTS checks for drag interruption, late highlights, finger-up and explicit return.`);
} finally {
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-tts-follow-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
