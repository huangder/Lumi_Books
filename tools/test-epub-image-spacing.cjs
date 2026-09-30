// Real Chromium geometry checks; no device or test APK required.
// Run: node tools/test-epub-image-spacing.cjs
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');

const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const readerCss = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
const parts = [...source.matchAll(/private const val READER_SCRIPT_PART_[123] = """([\s\S]*?)"""/g)];
assert.equal(parts.length, 3);
const readerScript = parts.map(match => match[1]).join('');

async function verifySpacing() {
  let checks = 0;
  const near = (actual, expected, label) => {
    if (Math.abs(actual - expected) > 1) throw Error(`${label}: expected ${expected}, got ${actual}`);
    checks++;
  };
  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
  try {
    await Promise.all([...document.images].map(image => image.decode()));
    const media = document.getElementById('page-image');
    const candidate = document.body.hasAttribute('data-lumi-cover-candidate');
    const configure = async flow => {
      window.LumiReader.configure({ flow, insets: { top: 24, bottom: 24, left: 16, right: 16 } });
      for (let i = 0; i < 100; i++) {
        if (document.documentElement.classList.contains('lumi-' + flow)) return;
        await delay(20);
      }
      throw Error('Reader did not configure ' + flow);
    };
    // Repeat the switch to catch leftover page-sized boxes on reused documents.
    for (const flow of ['scrolled', 'paginated', 'scrolled']) {
      await configure(flow);
      window.scrollTo(0, 0);
      const image = media.getBoundingClientRect();
      const naturalHeight = image.width * media.naturalHeight / media.naturalWidth;
      const expectedHeight = flow === 'paginated' ? window.innerHeight :
        candidate ? Math.max(window.innerHeight, naturalHeight) : naturalHeight;
      near(document.body.getBoundingClientRect().height, expectedHeight, flow + ' body height');
      for (const container of document.querySelectorAll('[data-lumi-cover-container]')) {
        near(container.getBoundingClientRect().height, expectedHeight, flow + ' wrapper height');
      }
      if (flow === 'scrolled') {
        near(image.height, naturalHeight, 'Image keeps its aspect ratio');
        near(image.top, candidate ? Math.max(0, (window.innerHeight - naturalHeight) / 2) : 0,
          'Only the cover receives vertical centering');
      }
    }
    document.getElementById('spacing-result').textContent = JSON.stringify({ passed: true, checks });
  } catch (error) {
    document.getElementById('spacing-result').textContent = JSON.stringify({ passed: false, checks, error: error.message });
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-image-spacing-'));
let total = 0;
try {
  for (const candidate of [false, true]) for (const tall of [false, true]) for (const nested of [false, true]) {
    const height = tall ? 2400 : 200;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="${height}"><rect width="100%" height="100%" fill="red"/></svg>`;
    const image = `<img id="page-image" data-lumi-cover-media="true" src="data:image/svg+xml,${encodeURIComponent(svg)}">`;
    const content = nested ? `<section data-lumi-cover-container="true"><div data-lumi-cover-container="true">${image}</div></section>` : image;
    const fixture = join(directory, 'spacing.html');
    writeFileSync(fixture, `<!doctype html><html><head><meta charset="utf-8"><style>${readerCss}</style></head>
      <body data-lumi-layout="reflowable" data-lumi-media-only="true" data-lumi-cover="true"
        ${candidate ? 'data-lumi-cover-candidate="true"' : ''}>
      ${content}<pre id="spacing-result" style="display:none"></pre>
      <script>${readerScript}</script><script>(${verifySpacing.toString()})()</script></body></html>`);
    const result = spawnSync(process.env.EPUB_PROBE_BROWSER ||
      'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
        '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--user-data-dir=' + join(directory, 'profile'), '--window-size=480,900',
        '--virtual-time-budget=5000', '--dump-dom', pathToFileURL(fixture).href,
      ], { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
    if (result.error) throw result.error;
    assert.equal(result.status, 0, result.stderr);
    const match = result.stdout.match(/<pre id="spacing-result"[^>]*>([^<]+)<\/pre>/);
    assert.ok(match, 'Browser did not produce a spacing result');
    const report = JSON.parse(match[1]);
    assert.ok(report.passed, `cover=${candidate}, tall=${tall}, nested=${nested}: ${report.error}`);
    total += report.checks;
  }
  console.log('PASS:', total, 'real-browser image spacing checks (cover/interior, short/tall, direct/nested, mode switching).');
} finally {
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-image-spacing-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
