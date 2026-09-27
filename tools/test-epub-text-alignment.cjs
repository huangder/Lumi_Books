// Real Chromium CSS-cascade regression for publisher layout; no Android device/APKs.
// Run: node tools/test-epub-text-alignment.cjs
// Set EPUB_PROBE_BROWSER to use a different Chromium executable.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');

const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const scriptParts = [...source.matchAll(/private const val READER_SCRIPT_PART_[123] = """([\s\S]*?)"""/g)];
assert.equal(scriptParts.length, 3, 'All production reader script parts must be exercised');
const readerScript = scriptParts.map(match => match[1]).join('');
const readerCss = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];

function verifyAlignment() {
  let checks = 0;
  const equal = (actual, expected, label) => {
    if (actual !== expected) throw Error(label + ': expected ' + expected + ', got ' + actual);
    checks++;
  };
  const fixtures = [...document.querySelectorAll('[data-alignment-fixture]')];
  const baseline = fixtures.map(element => getComputedStyle(element).textAlign);
  const configure = (flow, options) => window.LumiReader.configure({ flow, ...options });
  const original = label => fixtures.forEach((element, index) => {
    equal(getComputedStyle(element).textAlign, baseline[index], label + ' / ' + element.id);
  });
  const typography = {
    fontFamily: 'monospace', textColor: '#123456', bodyFontWeight: 600, letterSpacingDp: 1.25,
  };
  const typographyIntact = label => {
    const style = getComputedStyle(document.getElementById('class-heading'));
    equal(style.fontFamily.replace(/["']/g, ''), 'monospace', label + ' font');
    equal(style.color, 'rgb(18, 52, 86)', label + ' color');
    equal(style.fontWeight, '600', label + ' weight');
    equal(style.letterSpacing, '1.25px', label + ' spacing');
  };
  try {
    equal(getComputedStyle(document.getElementById('class-heading')).textAlign, 'center',
      'Publisher stylesheet fixture starts centered');
    for (const flow of ['paginated', 'scrolled']) {
      configure(flow, { textAlignment: 'natural' });
      original(flow + ' default');
      equal(document.getElementById('lumi-reader-overrides'), null, flow + ' no default override');
      configure(flow, { textAlignment: 'natural', ...typography });
      original(flow + ' custom typography');
      typographyIntact(flow);
      // Explicit user choices still work; returning to natural restores every
      // publisher source, including inherited alignment and styled p/div titles.
      for (const textAlignment of ['left', 'center', 'right', 'justify']) {
        configure(flow, { textAlignment, ...typography });
        equal(getComputedStyle(document.getElementById('body-paragraph')).textAlign,
          textAlignment, flow + ' explicit ' + textAlignment);
        configure(flow, { textAlignment: 'natural', ...typography });
        original(flow + ' restore from ' + textAlignment);
        typographyIntact(flow + ' restore');
        configure(flow, { textAlignment });
        configure(flow, { textAlignment: 'natural' });
        original(flow + ' remove override from ' + textAlignment);
        equal(document.getElementById('lumi-reader-overrides'), null, flow + ' override removed');
      }
      for (const textAlignment of [undefined, 'invalid']) {
        configure(flow, { textAlignment: 'left' });
        configure(flow, { textAlignment });
        original(flow + ' unspecified or invalid alignment');
      }
    }
    document.getElementById('alignment-result').textContent = JSON.stringify({ passed: true, checks });
  } catch (error) {
    document.getElementById('alignment-result').textContent = JSON.stringify({ passed: false, checks, error: error.message });
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-alignment-'));
try {
  writeFileSync(join(directory, 'publisher.css'),
    '.chapter-title, .centered { text-align: center; } .signature { text-align: right; }');
  const fixture = join(directory, 'alignment.html');
  writeFileSync(fixture, `<!doctype html><html><head><meta charset="utf-8">
    <link rel="stylesheet" href="publisher.css">
    <style>body { text-align: justify; } .left-copy { text-align: left; }</style>
    <style>${readerCss}</style></head><body data-lumi-layout="reflowable">
    <h1 id="class-heading" class="chapter-title" data-alignment-fixture>居中的章标题</h1>
    <h2 id="inline-heading" style="text-align:center" data-alignment-fixture>内联居中标题</h2>
    <p id="paragraph-heading" class="chapter-title" data-alignment-fixture>段落形式的标题</p>
    <div id="division-heading" class="chapter-title" data-alignment-fixture>容器形式的标题</div>
    <section class="centered"><h3 id="inherited-heading" data-alignment-fixture>继承居中的标题</h3>
      <p id="inherited-paragraph" data-alignment-fixture><span>继承居中的副标题</span></p></section>
    <h4 id="legacy-heading" align="center" data-alignment-fixture>旧式居中标题</h4>
    <h5 id="important-heading" style="text-align:center!important" data-alignment-fixture>重要样式标题</h5>
    <p id="signature" class="signature" data-alignment-fixture>右对齐署名</p>
    <p id="left-paragraph" class="left-copy" data-alignment-fixture>左对齐正文</p>
    <p id="body-paragraph" data-alignment-fixture>继承原书两端对齐的正文。</p>
    <pre id="alignment-result"></pre>
    <script>${readerScript}</script><script>(${verifyAlignment.toString()})()</script>
    </body></html>`);
  const result = spawnSync(process.env.EPUB_PROBE_BROWSER ||
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
      '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      '--user-data-dir=' + join(directory, 'profile'), '--dump-dom', pathToFileURL(fixture).href,
    ], { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, result.stderr);
  const match = result.stdout.match(/<pre id="alignment-result">([^<]+)<\/pre>/);
  assert.ok(match, 'Browser did not produce an alignment result');
  const report = JSON.parse(match[1]);
  assert.ok(report.passed, report.error);
  console.log('PASS:', report.checks, 'real-browser publisher alignment checks (paginated and scrolled).');
} finally {
  // Only remove the uniquely created test directory directly inside temp.
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-alignment-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
