// Run the production reader script in Chromium; no device or test APK.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const source = readFileSync(join(__dirname, '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
const script = [...source.matchAll(/private const val READER_SCRIPT_PART_\w+ = """([\s\S]*?)"""/g)].map(m => m[1]).join('');

async function verify(flow, fallback) {
  const check = (ok, message) => { if (!ok) throw Error(message); };
  const settle = () => new Promise(resolve => setTimeout(resolve, 250));
  try {
    if (fallback) window.Highlight = undefined;
    window.LumiReader.configure({ flow, transition: 'none', fontFamily: 'serif', bodyFontWeight: 500,
      insets: { top: 0, right: 0, bottom: 0, left: 0 } });
    await settle();
    const target = document.getElementById('marked');
    const original = getComputedStyle(target);
    const originalFamily = original.fontFamily;
    const originalWeight = original.fontWeight;
    const originalColor = original.color;
    const manual = { exact: 'manual annotation', type: 'highlight', color: '#ffff00' };
    let checks = 0;
    for (const fontFamily of ['monospace', 'sans-serif', 'serif']) {
      for (const underlineMode of [1, 2, 3, 4]) {
        const rule = { exact: 'rule text', type: 'underline', color: '#ff0000', generatedByRule: true,
          ruleStyle: { fontFamily, fontWeight: 700, italic: true, textColor: '#ff0000',
            highlightColor: '#00ff00', highlightMode: 0, underlineMode } };
        window.LumiReader.setHighlights([manual, rule]);
        await settle();
        const span = target.querySelector('[data-lumi-rule-style]');
        check(span, 'Rule did not wrap matching text');
        const style = getComputedStyle(span);
        check(style.fontFamily === fontFamily, `Rule font overridden: ${style.fontFamily} != ${fontFamily}`);
        check(style.fontWeight === '700' && style.fontStyle === 'italic', 'Rule weight/italic overridden');
        if (fallback) check(style.color === 'rgb(255, 0, 0)', 'Fallback rule color overridden');
        check(document.querySelectorAll('#lumi-underline-layer svg').length > 0, 'Rule underline missing');
        // Disable only this rule while retaining a manual annotation.
        window.LumiReader.setHighlights([manual]);
        await settle();
        check(!target.querySelector('[data-lumi-rule-style]'), 'Font span remained after rule disabled');
        check(getComputedStyle(target).fontFamily === originalFamily, 'Body font not restored');
        check(getComputedStyle(target).fontWeight === originalWeight, 'Body weight not restored');
        check(getComputedStyle(target).color === originalColor, 'Body color not restored');
        check(!document.querySelector('#lumi-underline-layer svg'), 'Underline remained after rule disabled');
        check(!document.getElementById('lumi-rule-highlight-styles'), 'Rule color stylesheet remained');
        check(!window.CSS?.highlights || ![...CSS.highlights.keys()].some(k => String(k).startsWith('lumi-rule-')),
          'CSS highlight remained after rule disabled');
        check(document.querySelector('#lumi-highlight-layer').children.length === 1,
          'Rule background remained or manual annotation disappeared');
        checks++;
      }
    }
    window.LumiReader.setHighlights([]);
    await settle();
    check(!document.querySelector('#lumi-highlight-layer').children.length, 'Marks remained after clearing all');
    document.getElementById('result').textContent = JSON.stringify({ passed: true, checks });
  } catch (error) {
    document.getElementById('result').textContent = JSON.stringify({ passed: false, error: error.message });
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-rule-styles-'));
try {
  let checks = 0;
  for (const flow of ['paginated', 'scrolled']) for (const fallback of [false, true]) {
    const file = join(directory, `${flow}-${fallback}.html`);
    writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8"><style>${css}</style>
      <style>body,p,span{font-family:serif!important;font-weight:500!important;font-style:normal!important;color:#123456!important}
      p{font-size:24px;line-height:1.7;margin:0}</style></head><body data-lumi-layout="reflowable">
      <p id="marked">before rule text after</p><p>manual annotation</p><pre id="result" style="display:none"></pre>
      <script>${script}</script><script>(${verify.toString()})(${JSON.stringify(flow)},${fallback})</script></body></html>`);
    const result = spawnSync(process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
      '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      '--user-data-dir=' + join(directory, 'profile'), '--window-size=480,700', '--virtual-time-budget=12000',
      '--dump-dom', pathToFileURL(file).href
    ], { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
    if (result.error) throw result.error;
    assert.equal(result.status, 0, result.stderr);
    const match = result.stdout.match(/<pre id="result"[^>]*>([^<]+)<\/pre>/);
    assert.ok(match, 'Missing browser report');
    const report = JSON.parse(match[1].replaceAll('&quot;', '"').replaceAll('&amp;', '&'));
    assert.ok(report.passed, `${flow}, fallback ${fallback}: ${report.error}`);
    checks += report.checks;
  }
  console.log(`PASS: ${checks} EPUB rule font/disable checks across flows and CSS highlight support.`);
} finally {
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-rule-styles-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
