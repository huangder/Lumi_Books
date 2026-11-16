// Exercise live publisher-layout margins in Chromium without device test APKs.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const parts = [...source.matchAll(/private const val READER_SCRIPT_PART_\w+ = """([\s\S]*?)"""/g)];
assert.equal(parts.length, 5);
const script = parts.map(match => match[1]).join('');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];

async function verify(flow, fixed, night) {
  let checks = 0;
  const check = (condition, message) => { if (!condition) throw Error(message); checks++; };
  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
  const reader = window.LumiReader;
  const config = { flow, transition: 'none', theme: night ? 'night' : 'day',
    publisherPaintOnly: !night, fontFamily: 'serif', lineHeight: 1.8,
    insets: { top: 20, right: 20, bottom: 20, left: 20 }, configurationGeneration: 1 };
  try {
    reader.configure(config);
    await delay(250);
    if (!fixed) reader.goToPage(Math.floor(reader.currentPosition().pageCount / 2));
    const originalText = document.getElementById('reading').textContent;
    const override = document.getElementById('lumi-reader-overrides');
    const nightColor = getComputedStyle(document.getElementById('copy-0')).color;
    const initialInsets = ['top', 'right', 'bottom', 'left'].map(side =>
      fixed ? 0 : parseFloat(document.body.style.getPropertyValue('--lumi-inset-' + side)) - config.insets[side]);
    // Count operations rather than assert flaky timing thresholds. A live inset
    // change must not rebuild typography or scan footnote reference structure.
    const mutations = new MutationObserver(() => {});
    mutations.observe(override, { childList: true, subtree: true });
    const query = document.body.querySelectorAll;
    let footnoteScans = 0;
    document.body.querySelectorAll = function(selector) {
      if (selector === 'a[href]') footnoteScans++;
      return query.call(this, selector);
    };
    for (let i = 1; i <= 12; i++) {
      const anchor = reader.currentLocator();
      config.configurationGeneration++;
      config.insets = { top: 20 + i * 2, right: 20 + i * 3, bottom: 20 + i, left: 20 + i * 4 };
      const beforeMessages = __lumiMessages.length;
      reader.configure(config);
      const current = reader.currentPosition();
      check(current.configurationGeneration === config.configurationGeneration, 'Newest settings applied synchronously');
      check(__lumiMessages.slice(beforeMessages).filter(m => m.type === 'ready').length === 1,
        'Only one completed layout notification per preview');
      if (!fixed) check(reader.pageText(current.pageIndex).text.includes(anchor.exact), 'Reading anchor remains on screen');
      check(document.body.style.visibility === 'visible', 'Page stays visible during the drag');
      if (!fixed) ['top', 'right', 'bottom', 'left'].forEach((side, index) => {
        check(parseFloat(document.body.style.getPropertyValue('--lumi-inset-' + side)) ===
          initialInsets[index] + config.insets[side], side + ' margin follows every preview');
      });
    }
    check(footnoteScans === 0, 'Margins must not repeat footnote setup');
    check(mutations.takeRecords().length === 0, 'Margins must not rewrite typography styles');
    check(document.getElementById('reading').textContent === originalText, 'Publisher DOM text retained');
    check(getComputedStyle(document.getElementById('copy-0')).color === nightColor, 'Publisher/night colors retained');
    // Changing typography after previews must still take the full configuration path.
    reader.configure({ ...config, configurationGeneration: 20, lineHeight: 2 });
    check(getComputedStyle(document.getElementById('copy-0')).lineHeight === '36px', 'Typography changes still apply');
    check(__lumiMessages.some(m => m.type === 'ready' && m.payload.configurationGeneration === 20),
      'Final configuration reports completed geometry');
    if (flow === 'paginated' && !fixed) {
      config.transition = 'slide';
      config.configurationGeneration = 21;
      reader.configure(config);
      reader.next();
      check(document.getElementById('lumi-page-stage')?.dataset.active === 'true', 'Old page animation started');
      document.getElementById('reading').style.fontSize = '24px';
      const beforeReflow = __lumiMessages.length;
      reader.configure({ ...config, textZoom: 133, configurationGeneration: 22 });
      check(document.getElementById('lumi-page-stage').dataset.active !== 'true',
        'Font resize releases the previous page animation');
      await delay(450);
      const resized = reader.currentPosition();
      const stable = __lumiMessages.slice(beforeReflow).find(m => m.type === 'layoutStable' &&
        m.payload.configurationGeneration === 22);
      check(stable?.payload.pageIndex === resized.pageIndex, 'Stable layout reports the restored page');
      reader.next();
      await delay(400);
      check(reader.currentPosition().pageIndex === resized.pageIndex + 1, 'Next page uses the resized geometry');
      reader.previous();
      await delay(400);
      check(reader.currentPosition().pageIndex === resized.pageIndex, 'Previous page uses the resized geometry');

      // A publisher can animate a graphic without changing its place in the
      // layout. Alternate its transform just before each readiness frame.
      const graphic = document.createElement('img');
      graphic.alt = '';
      graphic.width = 24;
      graphic.height = 24;
      document.getElementById('copy-0').append(graphic);
      let offset = 0;
      function moveGraphic() {
        offset = offset === 0 ? 24 : 0;
        graphic.style.transform = 'translateY(' + offset + 'px)';
        requestAnimationFrame(moveGraphic);
      }
      // Headless virtual time may jump across timers without delivering frames.
      // Drive the frame callbacks while Chromium still computes the actual layout.
      const browserFrame = window.requestAnimationFrame;
      const frameCallbacks = [];
      window.requestAnimationFrame = callback => frameCallbacks.push(callback);
      let target;
      try {
        moveGraphic();
        reader.configure({ ...config, textZoom: 140, configurationGeneration: 23 });
        target = reader.currentPosition().pageIndex + 1;
        reader.preparePage(target, 23);
        for (let i = 0; i < 8; i++) {
          for (const callback of frameCallbacks.splice(0)) callback(performance.now());
        }
      } finally {
        window.requestAnimationFrame = browserFrame;
      }
      check(__lumiMessages.some(m => m.type === 'layoutStable' && m.payload.configurationGeneration === 23),
        'Publisher graphic motion cannot block layout readiness');
      check(__lumiMessages.some(m => m.type === 'pagePrepared' && m.payload.requestToken === 23 &&
        m.payload.pageIndex === target), 'Neighbour preparation completes during publisher graphic motion');
    }
    document.getElementById('result').textContent = JSON.stringify({ passed: true, checks });
  } catch (error) {
    document.getElementById('result').textContent = JSON.stringify({ passed: false, checks, error: error.message });
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-live-margins-'));
try {
  for (const [flow, fixed, night] of [['paginated', false, false], ['scrolled', false, false],
    ['paginated', false, true], ['paginated', true, false]]) {
    const fixture = join(directory, `margins-${flow}-${fixed}-${night}.html`);
    const paragraphs = Array.from({ length: fixed ? 8 : 160 }, (_, i) =>
      `<p id="copy-${i}">Paragraph ${i}: ${'Publisher text for testing live margin changes and stable reading position. '.repeat(6)}</p>`).join('');
    writeFileSync(fixture, `<!doctype html><html><head><meta charset="utf-8">
      ${fixed ? '<meta name="viewport" content="width=480,height=700">' : ''}
      <style>${css}</style><style>body{font-size:18px}p{margin:8px 0}</style></head>
      <body data-lumi-layout="${fixed ? 'pre_paginated' : 'reflowable'}"><div id="reading">${paragraphs}</div>
      <pre id="result" style="display:none"></pre><script>
      window.__lumiMessages=[];window.lumiNative={postMessage:m=>__lumiMessages.push(JSON.parse(m))};</script>
      <script>${script}</script><script>(${verify.toString()})(${JSON.stringify(flow)},${fixed},${night})</script></body></html>`);
    const result = spawnSync(process.env.EPUB_PROBE_BROWSER ||
      'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
        '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--user-data-dir=' + join(directory, 'profile'), '--window-size=480,700', '--virtual-time-budget=4000',
        '--dump-dom', pathToFileURL(fixture).href
      ], { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
    if (result.error) throw result.error;
    assert.equal(result.status, 0, result.stderr);
    const match = result.stdout.match(/<pre id="result"[^>]*>([^<]+)<\/pre>/);
    assert.ok(match, 'Missing browser report');
    const report = JSON.parse(match[1].replaceAll('&quot;', '"').replaceAll('&amp;', '&'));
    assert.ok(report.passed, `${flow}, fixed=${fixed}, night=${night}: ${report.error} (${report.checks} checks)`);
    console.log('PASS:', flow, { fixed, night, ...report });
  }
} finally {
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-live-margins-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
