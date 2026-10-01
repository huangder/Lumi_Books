// Run the production EPUB bridge in desktop Chromium; no device or test APK.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const source = readFileSync(join(__dirname, '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
const script = [...source.matchAll(/private const val READER_SCRIPT_PART_[123] = """([\s\S]*?)"""/g)].map(m => m[1]).join('');

async function verify(flow) {
  const check = (condition, message) => { if (!condition) throw Error(message); };
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
  try {
    window.LumiReader.configure({ flow, nativePaging: true, transition: 'none',
      insets: { top: 0, right: 0, bottom: 0, left: 0 } });
    await wait(350);
    const target = document.getElementById('marked');
    const exact = target.textContent;
    const style = { textColor: '#ff0000', underlineMode: 1, fontWeight: 400, italic: false };
    const base = { exact, start: { exact }, color: '#ff0000', type: 'highlight' };
    const generated = { ...base, generatedByRule: true, ruleStyle: style };
    const manual = { ...base, generatedByRule: false };
    let checks = 0;
    for (const [label, items, selectable] of [
      ['rule color', [generated], false],
      ['rule underline', [{ ...generated, type: 'underline' }], false],
      ['rule without snapshot', [{ ...generated, ruleStyle: undefined }], false],
      ['manual highlight', [manual], true],
      ['manual underline snapshot', [{ ...manual, type: 'underline', ruleStyle: style }], true],
      ['legacy manual highlight', [base], true],
      ['overlapping rule then manual', [generated, manual], true],
      ['overlapping manual then rule', [manual, generated], true],
    ]) {
      window.LumiReader.setHighlights(items);
      for (const input of ['click', 'touch']) {
        await wait(470); // Let the previous touch/click suppression expire.
        const range = document.createRange();
        range.selectNodeContents(target);
        const rect = [...range.getClientRects()].find(r => r.width > 0 && r.height > 0);
        check(!!rect, 'Missing text geometry');
        const x = (rect.left + rect.right) / 2, y = (rect.top + rect.bottom) / 2;
        const element = document.elementFromPoint(x, y);
        window.messages.length = 0;
        const click = () => element.dispatchEvent(new MouseEvent('click', {
          bubbles: true, cancelable: true, clientX: x, clientY: y
        }));
        if (input === 'touch') {
          const touch = new Touch({ identifier: 1, target: element, clientX: x, clientY: y });
          element.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, cancelable: true,
            touches: [touch], targetTouches: [touch], changedTouches: [touch] }));
          element.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true,
            touches: [], targetTouches: [], changedTouches: [touch] }));
        }
        click(); // Also exercise the synthetic click following touchend.
        const selections = window.messages.filter(m => m.type === 'selection');
        check(selections.length === (selectable ? 1 : 0),
          `${label} ${input}: expected ${selectable ? 1 : 0} selection events, got ${selections.length}`);
        if (selectable) {
          check(selections[0].payload.annotationOnly === true, 'Manual tap must open annotation menu');
          check(selections[0].payload.text === exact, 'Manual tap selected the wrong text');
        } else {
          check(window.messages.some(m => m.type === 'tap'), 'Rule text must keep normal reader tap actions');
        }
        checks++;
      }
    }
    // A real text selection (as produced by long press/drag) still reaches the menu.
    window.LumiReader.setHighlights([generated]);
    window.messages.length = 0;
    const range = document.createRange();
    range.selectNodeContents(target);
    window.getSelection().removeAllRanges();
    window.getSelection().addRange(range);
    await wait(250);
    const selection = window.messages.find(m => m.type === 'selection');
    check(selection && selection.payload.text === exact && !selection.payload.annotationOnly,
      'Text selection over a rule highlight must still open the selection menu');
    document.getElementById('result').textContent = JSON.stringify({ passed: true, checks: checks + 1 });
  } catch (error) {
    document.getElementById('result').textContent = JSON.stringify({ passed: false, error: error.message });
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-annotation-taps-'));
try {
  let checks = 0;
  for (const layout of ['reflowable', 'pre_paginated'])
  for (const vertical of [false, true]) for (const flow of ['paginated', 'scrolled']) {
    const file = join(directory, `taps-${layout}-${vertical}-${flow}.html`);
    writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=480,height=700"><style>${css}</style>
      <style>body{writing-mode:${vertical ? 'vertical-rl' : 'horizontal-tb'};font-size:22px!important;line-height:1.7!important;margin:0!important;padding:0!important}p{margin:0!important}</style></head>
      <body data-lumi-layout="${layout}"><p><span id="marked">规则文字测试</span></p><pre id="result" style="display:none"></pre>
      <script>window.messages=[];window.lumiNative={postMessage:value=>window.messages.push(JSON.parse(value))};</script>
      <script>${script}</script><script>(${verify.toString()})(${JSON.stringify(flow)})</script></body></html>`);
    const result = spawnSync(process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
      '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      '--user-data-dir=' + join(directory, 'profile'), '--window-size=480,700', '--virtual-time-budget=14000',
      '--dump-dom', pathToFileURL(file).href
    ], { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
    if (result.error) throw result.error;
    assert.equal(result.status, 0, result.stderr);
    const match = result.stdout.match(/<pre id="result"[^>]*>([^<]+)<\/pre>/);
    assert.ok(match, 'Missing browser report');
    const report = JSON.parse(match[1].replaceAll('&quot;', '"').replaceAll('&amp;', '&'));
    assert.ok(report.passed, `${layout}, ${flow}, vertical ${vertical}: ${report.error}`);
    checks += report.checks;
  }
  console.log(`PASS: ${checks} EPUB annotation tap checks across fixed/reflowable layouts, horizontal/vertical text and paginated/scrolled flows.`);
} finally {
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-annotation-taps-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
