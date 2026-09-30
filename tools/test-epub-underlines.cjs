// Execute the production annotation overlay in desktop Chromium. No device or APK.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const source = readFileSync(join(__dirname, '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
const script = [...source.matchAll(/private const val READER_SCRIPT_PART_[123] = """([\s\S]*?)"""/g)].map(m => m[1]).join('');

async function verify(flow, vertical) {
  const check = (condition, message) => { if (!condition) throw Error(message); };
  try {
    window.LumiReader.configure({ flow, transition: 'none', insets: { top: 0, right: 0, bottom: 0, left: 0 } });
    await new Promise(resolve => setTimeout(resolve, 300));
    const target = document.getElementById('marked');
    const exact = target.textContent;
    let checks = 0;
    for (const mode of [3, 1, 3, 2, 4]) {
      for (const fallback of [false, true]) {
        const originalHighlight = window.Highlight;
        if (fallback) window.Highlight = undefined;
        const item = { type: 'underline', exact, start: { exact }, color: '#ff0000', ruleStyle: {
          underlineMode: mode, textColor: '#ff0000', fontWeight: 400, italic: false
        } };
        window.LumiReader.setHighlights([]);
        check(window.LumiReader.setHighlights([item]), 'Overlay call failed');
        window.Highlight = originalHighlight;
        const lines = [...document.querySelectorAll('#lumi-underline-layer svg')];
        check(lines.length > 1, `Expected multiple ${vertical ? 'columns' : 'lines'}, mode ${mode}`);
        const range = document.createRange(); range.selectNodeContents(target);
        const rects = [...range.getClientRects()].filter(r => r.width > 0 && r.height > 0);
        const paths = lines.map(svg => svg.querySelector('path').getBoundingClientRect());
        for (const rect of rects) {
          const matching = paths.some(path => vertical
            ? path.left >= rect.left - 1 && path.right <= rect.right + 1 && path.top <= rect.top + 1 && path.bottom >= rect.bottom - 1
            : path.top >= rect.top - 1 && path.bottom <= rect.bottom + 1 && path.left <= rect.left + 1 && path.right >= rect.right - 1);
          check(matching, `Uncovered rect mode ${mode} fallback ${fallback}: ${JSON.stringify(rect.toJSON())}; paths ${JSON.stringify(paths.map(p => p.toJSON()))}`);
        }
        lines.forEach(svg => {
          const path = svg.querySelector('path');
          if (mode === 2) check((path.getAttribute('d').match(/M/g) || []).length === 2, 'Double line missing stroke');
          if (mode === 4) check(path.getAttribute('stroke-dasharray') === '4 3', 'Dash pattern missing');
        });
        checks++;
      }
    }
    window.LumiReader.setHighlights([]);
    check(document.querySelectorAll('#lumi-underline-layer svg').length === 0, 'Old lines remained after clearing');
    document.getElementById('result').textContent = JSON.stringify({ passed: true, checks });
  } catch (error) {
    document.getElementById('result').textContent = JSON.stringify({ passed: false, error: error.message });
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-underlines-'));
try {
  let checks = 0;
  for (const vertical of [false, true]) for (const flow of ['paginated', 'scrolled']) for (const size of [18, 30]) {
    const file = join(directory, `lines-${vertical}-${flow}-${size}.html`);
    const text = '天地玄黄宇宙洪荒日月盈昃辰宿列张'.repeat(24);
    writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8"><style>${css}</style>
      <style>body{writing-mode:${vertical ? 'vertical-rl' : 'horizontal-tb'};font-size:${size}px!important;line-height:1.7!important;margin:0!important;padding:0!important}p{margin:0!important}</style></head>
      <body data-lumi-layout="reflowable"><p id="marked">${text}</p><pre id="result" style="display:none"></pre>
      <script>${script}</script><script>(${verify.toString()})(${JSON.stringify(flow)},${vertical})</script></body></html>`);
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
    assert.ok(report.passed, `${flow}, vertical ${vertical}, font ${size}: ${report.error}`);
    checks += report.checks;
  }
  console.log(`PASS: ${checks} EPUB underline checks across writing modes, flows, font sizes, CSS highlight support and style switching.`);
} finally {
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-underlines-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
