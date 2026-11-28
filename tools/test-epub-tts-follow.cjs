// Run the production EPUB scroll/highlight code in Chromium, without an Android device.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const source = readFileSync(join(__dirname, '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
const alignment = readFileSync(join(__dirname, '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubReaderAlignment.kt'), 'utf8')
  .match(/EPUB_READER_ALIGNMENT_SCRIPT = """([\s\S]*?)"""/)[1];
const script = [...source.matchAll(/private const val (READER_SCRIPT_PART_\w+) = """([\s\S]*?)"""/g)]
  .map(m => m[2] + (m[1] === 'READER_SCRIPT_PART_1' ? alignment : '')).join('');

async function verify() {
  const check = (condition, message) => { if (!condition) throw Error(message); };
  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
  try {
    const reader = window.LumiReader;
    reader.configure({ flow: 'scrolled', transition: 'none', canTurnPrevious: false, canTurnNext: false });
    await delay(300);
    const text = document.getElementById('reading').textContent;
    const offset = paragraph => text.indexOf('Paragraph ' + paragraph + ':');
    const blocks = () => [...document.querySelectorAll('.lumi-tts-highlight-block')];
    const wrappedStart = offset(119) + 'Paragraph 119: '.length;
    const wrappedEnd = text.indexOf('.', wrappedStart) + 1;
    const wrappedRange = document.createRange();
    wrappedRange.setStart(document.getElementById('reading').firstChild, wrappedStart);
    wrappedRange.setEnd(document.getElementById('reading').firstChild, wrappedEnd);
    check(wrappedRange.getClientRects().length > 1, 'The sentence fixture must wrap');
    reader.setTtsHighlight(wrappedStart, wrappedEnd, '#aaccff');
    check(blocks().length === 1 && parseFloat(getComputedStyle(blocks()[0]).borderRadius) > 0,
      'A wrapped sentence must have a single rounded background');
    const wrappedHeight = blocks()[0].getBoundingClientRect().height;
    reader.setTtsHighlight(wrappedStart, wrappedStart + 10, '#aaccff');
    check(blocks().length === 1 && blocks()[0].getBoundingClientRect().height < wrappedHeight,
      'Moving to a short sentence must replace the old wrapped background');
    const highlight = paragraph => {
      const start = offset(paragraph);
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
    check(reader.isTtsRangeVisible(offset(70), offset(70) + 40) === true, 'A spoken range on screen must be visible');
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
    check(reader.isTtsRangeVisible(offset(90), offset(90) + 40) === false,
      'Visibility must be available while the finger is still down');
    window.scrollBy(0, -30);
    // Synthetic scrolling has no compositor drag in headless Chromium. Deliver its scroll event.
    window.dispatchEvent(new Event('scroll'));
    await delay(25);
    check(__lumiMessages.some(m => m.type === 'ttsVisibility' && m.payload.start === offset(90) && m.payload.visible === false),
      'Scrolling must report offscreen speech before finger-up');
    check(!__lumiMessages.some(m => m.type === 'manualScrollSettled'), 'The offscreen event must precede settling');
    window.scrollTo(0, browsingY);
    touch('touchend', 180);
    highlight(100);
    await delay(60);
    check(window.scrollY === browsingY, 'Finger-up must not resume TTS following');
    reader.setTtsFollowEnabled(false);
    window.scrollBy(0, -30);
    const flungY = window.scrollY;
    await delay(65);
    check(!__lumiMessages.some(m => m.type === 'manualScrollSettled'), 'Inertial movement must postpone the settled event');
    await delay(100);
    check(__lumiMessages.filter(m => m.type === 'manualScrollSettled').length === 1, 'A manual drag must report its settled viewport once');
    check(reader.isTtsRangeVisible(offset(70), offset(70) + 40) === false, 'A spoken range outside the screen must be hidden');
    const spokenRange = document.createRange();
    spokenRange.setStart(document.getElementById('reading').firstChild, offset(70));
    spokenRange.setEnd(document.getElementById('reading').firstChild, offset(70) + 40);
    window.scrollBy(0, Math.ceil(spokenRange.getBoundingClientRect().bottom) + 1);
    check(reader.isTtsRangeVisible(offset(70), offset(70) + 40) === false, 'A range just above the first visible line is hidden');
    window.scrollBy(0, -Math.ceil(spokenRange.getBoundingClientRect().height) - 4);
    check(reader.isTtsRangeVisible(offset(70), offset(70) + 40) === true, 'Returning the range into the viewport makes it visible');
    window.scrollTo(0, flungY);
    touch('touchstart', 300);
    touch('touchmove', 180);
    touch('touchend', 180);
    await delay(150);
    check(__lumiMessages.filter(m => m.type === 'manualScrollSettled').length === 2,
      'Dragging again while detached must report a new settled viewport');
    highlight(110);
    await delay(50);
    check(window.scrollY === flungY, 'Native follow suspension must keep the browsing position');
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
    reader.configure({ flow: 'paginated', transition: 'none', canTurnPrevious: false, canTurnNext: false });
    await delay(300);
    const start = offset(90);
    const anchor = { version: 2, textPosition: start, textLength: text.length, progression: 0 };
    reader.goToPage(0);
    check(reader.restore(anchor) && reader.currentPosition().pageIndex === 0,
      'An ordinary locator with no quote still uses its stored progression');
    check(reader.restore({ ...anchor, listeningAnchor: true }), 'The listening character anchor must resolve');
    const page = reader.currentPosition().pageIndex;
    check(page > 0, 'A background audio chunk must not strand the reader on its old page');
    const visible = reader.pageText(page);
    check(visible.startCharacterOffset <= start && start < visible.endCharacterOffset,
      'The restored page must contain the spoken offset');
    const pageBoundary = visible.endCharacterOffset;
    reader.setTtsHighlight(pageBoundary - 20, pageBoundary + 20, '#aaccff');
    check(blocks().length === 2, 'A sentence crossing two pages must have one background per page');
    document.getElementById('result').textContent = JSON.stringify({ passed: true, checks: 25 });
  } catch (error) {
    document.getElementById('result').textContent = JSON.stringify({ passed: false, error: error.message });
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-tts-follow-'));
try {
  let checks = 0;
  for (const size of [18, 30]) {
    const file = join(directory, `follow-${size}.html`);
    const text = Array.from({ length: 120 }, (_, i) => `Paragraph ${i + 1}: ${i === 118
      ? 'a single long sentence that naturally wraps onto several lines as the reader increases the font size and continues to listen'
      : 'reading text with a stable listening position'}.`).join('\n');
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
  console.log(`PASS: ${checks} EPUB TTS checks for scroll interruption, explicit return and paginated listening restoration.`);
} finally {
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-tts-follow-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
