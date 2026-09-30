// Production WebView bridge regression in isolated Chromium; no device/APKs.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
const script = [...source.matchAll(/private const val READER_SCRIPT_PART_[123] = """([\s\S]*?)"""/g)].map(m => m[1]).join('');

async function verify() {
  const near = (a, b) => { if (Math.abs(a - b) > 1) throw Error(`Expected ${b}, got ${a}`); };
  try {
    await Promise.all([...document.images].map(image => image.decode()));
    window.LumiReader.configure({ flow: 'scrolled' });
    for (let i = 0; i < 100 && !document.documentElement.classList.contains('lumi-scrolled'); i++) {
      await new Promise(resolve => setTimeout(resolve, 20));
    }
    const second = document.getElementById('second');
    const position = JSON.parse(second.getAttribute('data-lumi-comic-image'));
    position.scroll = 0.35;
    const expected = window.scrollY + second.getBoundingClientRect().top + second.getBoundingClientRect().height * position.scroll;
    window.LumiReader.restore({ version: 1, comicImage: position });
    near(window.scrollY, expected);
    const current = window.LumiReader.currentPosition().locator;
    if (current.comicImage.occurrence !== 1) throw Error('Repeated image mapped to first occurrence');
    near(current.comicImage.scroll * 1000, 350);
    window.LumiReader.restore({ version: 1, comicImage: { ...position, occurrence: 0, scroll: 0 } });
    near(document.getElementById('first').getBoundingClientRect().top, 0);
    window.scrollTo(0, 0);
    const text = window.LumiReader.currentPosition().locator;
    if (!text.exact || !text.exact.includes('Preserved text')) throw Error('Image locator replaced the text anchor');
    document.getElementById('result').textContent = JSON.stringify({ passed: true });
  } catch (error) { document.getElementById('result').textContent = JSON.stringify({ passed: false, error: error.message }); }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-comic-navigation-'));
try {
  const svg = encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="480" height="1500"><rect width="480" height="1500" fill="red"/></svg>');
  const markup = ['first', 'second', 'third'].map((id, occurrence) => {
    const position = JSON.stringify({ type: 'lumi_epub_comic_position', version: 1,
      document: 'page.xhtml', image: 'repeat.jpg', occurrence, chapter: 0, scroll: 0 });
    return `<img id="${id}" src="data:image/svg+xml,${svg}" data-lumi-comic-image='${position}'>`;
  }).join('');
  const file = join(directory, 'navigation.html');
  writeFileSync(file, `<!doctype html><html><head><style>${css}</style>
    <style>img{width:100%;height:auto;max-height:none!important}</style></head>
    <body data-lumi-layout="reflowable"><p>Preserved text before the images.</p>${markup}
    <pre id="result" style="display:none"></pre><script>${script}</script><script>(${verify.toString()})()</script></body></html>`);
  const result = spawnSync(process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--user-data-dir=' + join(directory, 'profile'), '--window-size=480,900', '--virtual-time-budget=5000',
    '--dump-dom', pathToFileURL(file).href,
  ], { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, result.stderr);
  const match = result.stdout.match(/<pre id="result"[^>]*>([^<]+)<\/pre>/);
  assert.ok(match, 'Browser did not produce a navigation result');
  const report = JSON.parse(match[1]);
  assert.ok(report.passed, report.error);
  console.log('PASS: repeated-image restore, image scroll fraction and preserved text anchors.');
} finally {
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-comic-navigation-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
