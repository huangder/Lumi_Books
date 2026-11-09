// Check rendered ink, not just computed CSS: a static face can report 700 without getting bolder.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const root = join(__dirname, '..');
const source = readFileSync(join(root, 'app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
const script = [...source.matchAll(/private const val READER_SCRIPT_PART_\w+ = """([\s\S]*?)"""/g)].map(m => m[1]).join('');

async function verify(fonts) {
  let checks = 0;
  const check = (ok, message) => { if (!ok) throw Error(message); checks++; };
  try {
    const target = document.getElementById('marked');
    const ink = async (flow, font, weight, descriptor = font.descriptor) => {
      window.LumiReader.configure({ flow, transition: 'none', fontFamily: 'Lumi Reader Override',
        fontUrl: font.url, fontWeightDescriptor: descriptor, bodyFontWeight: weight });
      await document.fonts.ready;
      await document.fonts.load(`${weight} 48px "Lumi Reader Override"`, font.text);
      const style = getComputedStyle(target);
      check(style.fontWeight === String(weight), `${font.name}: requested weight not applied`);
      const canvas = document.createElement('canvas'); canvas.width = 900; canvas.height = 100;
      const ctx = canvas.getContext('2d');
      ctx.font = `${style.fontWeight} 48px ${style.fontFamily}`;
      ctx.fillText(font.text, 10, 70);
      const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
      let sum = 0;
      for (let i = 3; i < pixels.length; i += 4) sum += pixels[i];
      return sum;
    };
    for (const flow of ['paginated', 'scrolled']) for (const font of fonts) {
      const regular = await ink(flow, font, 400);
      const bold = await ink(flow, font, 700);
      check(bold > regular * 1.08, `${flow} ${font.name}: bold ink ${bold} did not exceed regular ${regular}`);
      const restored = await ink(flow, font, 400);
      check(restored === regular, `${flow} ${font.name}: resetting weight left stale bold`);
      if (font.descriptor === '400') {
        const broken = await ink(flow, font, 700, '100 900');
        check(broken < bold * 0.95, 'Fixture must detect the old static-font range bug');
      }
    }
    document.getElementById('result').textContent = JSON.stringify({ passed: true, checks });
  } catch (error) {
    document.getElementById('result').textContent = JSON.stringify({ passed: false, checks, error: error.message });
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-font-weight-'));
try {
  const fonts = [
    { name: 'static Kaiti', file: 'lxgw_wenkai.ttf', descriptor: '400', text: '天地玄黄字重测试' },
    { name: 'variable Serif', file: 'source_serif4_regular.ttf', descriptor: '200 900', text: 'Reading font weight' },
  ].map(font => ({ ...font, url: 'data:font/ttf;base64,' + readFileSync(join(root, 'app/src/main/res/font', font.file)).toString('base64') }));
  const file = join(directory, 'weight.html');
  writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8"><style>${css}</style></head>
    <body data-lumi-layout="reflowable"><p id="marked">天地玄黄 Reading</p><pre id="result" style="display:none"></pre>
    <script>${script}</script><script>(${verify.toString()})(${JSON.stringify(fonts)})</script></body></html>`);
  const result = spawnSync(process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--user-data-dir=' + join(directory, 'profile'), '--window-size=480,700', '--virtual-time-budget=15000',
    '--dump-dom', pathToFileURL(file).href,
  ], { windowsHide: true, encoding: 'utf8', timeout: 60000, maxBuffer: 64 * 1024 * 1024 });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, result.stderr);
  const match = result.stdout.match(/<pre id="result"[^>]*>([^<]+)<\/pre>/);
  assert.ok(match, 'Missing browser report');
  const report = JSON.parse(match[1].replaceAll('&quot;', '"').replaceAll('&amp;', '&'));
  assert.ok(report.passed, report.error);
  console.log(`PASS: ${report.checks} EPUB font weight checks with rendered static/variable fonts, both flows and reset.`);
} finally {
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-font-weight-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
