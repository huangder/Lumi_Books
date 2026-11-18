// Check static outlines and real variable glyphs in both original-layout flows.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const root = join(__dirname, '..');
const source = readFileSync(join(root, 'app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
const alignmentSource = readFileSync(join(root,
  'app/src/main/java/com/huangder/lumibooks/util/epub/EpubReaderAlignment.kt'), 'utf8');
const alignment = alignmentSource.match(/internal const val EPUB_READER_ALIGNMENT_SCRIPT = """([\s\S]*?)"""/)[1];
const script = [...source.matchAll(/private const val (READER_SCRIPT_PART_\w+) = """([\s\S]*?)"""/g)]
  .map(m => m[2] + (m[1] === 'READER_SCRIPT_PART_1' ? alignment : '')).join('');

async function verify(fonts) {
  let checks = 0;
  const check = (ok, message) => { if (!ok) throw Error(message); checks++; };
  try {
    const target = document.getElementById('marked');
    const configure = (flow, weight, font) => window.LumiReader.configure({
      flow, transition: 'none', bodyFontWeight: weight,
      ...(font && { fontFamily: 'Lumi Reader Override', fontUrl: font.url, fontWeightDescriptor: font.descriptor }),
    });
    const ink = async (flow, font, weight) => {
      configure(flow, weight, font);
      await document.fonts.ready;
      await document.fonts.load(`${weight} 48px "Lumi Reader Override"`, font.text);
      const style = getComputedStyle(target);
      check(style.fontWeight === String(weight), `${font.name}: requested weight not applied`);
      check(parseFloat(style.webkitTextStrokeWidth) === 0, `${font.name}: variable text got an outline`);
      const canvas = document.createElement('canvas'); canvas.width = 900; canvas.height = 100;
      const ctx = canvas.getContext('2d');
      ctx.font = `${style.fontWeight} 48px ${style.fontFamily}`;
      ctx.fillText(font.text, 10, 70);
      const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
      let sum = 0;
      for (let i = 3; i < pixels.length; i += 4) sum += pixels[i];
      return sum;
    };
    const ruleInk = async (font, weight) => {
      const family = 'LumiRule_test font';
      window.LumiReader.setHighlights([{ exact: font.text, ruleStyle: {
        fontFamily: family, fontUrl: font.url, fontWeight: weight, fontWeightDescriptor: font.descriptor
      } }]);
      const faces = await document.fonts.load(`${weight} 48px "${family}"`, font.text);
      check(faces.length === 1, `${font.name}: the rule font was not loaded`);
      const mark = target.querySelector('[data-lumi-rule-style]');
      check(mark && getComputedStyle(mark).fontWeight === String(weight), `${font.name}: rule weight not applied`);
      check(parseFloat(getComputedStyle(mark).webkitTextStrokeWidth) === 0, `${font.name}: rule font inherited the body outline`);
      const descriptor = document.getElementById('lumi-rule-fonts').sheet.cssRules[0].style.fontWeight;
      check(descriptor === font.descriptor, `${font.name}: incorrect rule weight descriptor ${descriptor}`);
      const ctx = document.createElement('canvas').getContext('2d');
      ctx.canvas.width = 900;
      ctx.font = `${weight} 48px "${family}"`;
      return ctx.measureText(font.text).width;
    };
    const [staticFont, variableFont] = fonts;
    const staticOutline = async (flow, weight, override) => {
      configure(flow, weight, override);
      await document.fonts.ready;
      const style = getComputedStyle(target);
      check(style.fontWeight === '400', `${flow}: static face changed CSS weight at ${weight}`);
      const range = document.createRange();
      range.selectNodeContents(target);
      return { stroke: parseFloat(style.webkitTextStrokeWidth), width: range.getBoundingClientRect().width };
    };
    for (const flow of ['paginated', 'scrolled']) {
      target.textContent = staticFont.text;
      for (const font of [undefined, staticFont]) {
        const regular = await staticOutline(flow, 400, font);
        let previous = regular.stroke;
        check(previous === 0, `${flow}: default weight got an outline`);
        for (const weight of [500, 600, 700, 800, 900]) {
          const current = await staticOutline(flow, weight, font);
          check(current.stroke > previous, `${flow}: static outline did not grow at ${weight} (${current.stroke})`);
          check(current.width === regular.width, `${flow}: static outline changed glyph advances at ${weight}`);
          previous = current.stroke;
        }
        for (const weight of [250, 400]) {
          const restored = await staticOutline(flow, weight, font);
          check(restored.stroke === 0 && restored.width === regular.width, `${flow}: static face changed at ${weight}`);
        }
      }
      configure(flow, 700, staticFont);
      await ruleInk(staticFont, 700);
      window.LumiReader.setHighlights([]);
      target.textContent = variableFont.text;
      const light = await ink(flow, variableFont, 250);
      const regular = await ink(flow, variableFont, 400);
      const medium = await ink(flow, variableFont, 550);
      const bold = await ink(flow, variableFont, 700);
      check(light < regular && regular < medium && medium < bold, `${flow}: variable ink did not increase with weight`);
      check(await ink(flow, variableFont, 400) === regular, `${flow}: resetting weight left stale bold`);
      const lightRule = await ruleInk(variableFont, 250);
      const heavyRule = await ruleInk(variableFont, 850);
      check(heavyRule !== lightRule, `${flow}: rule lost the variable weight axis`);
      window.LumiReader.setHighlights([]);

      const publisher = document.getElementById('publisher');
      const staticChild = document.getElementById('publisher-static');
      for (const weight of [250, 700, 400]) {
        configure(flow, weight);
        const style = getComputedStyle(publisher);
        check(style.fontVariationSettings === (weight === 400 ? 'normal' : `"wght" ${weight}`),
          `${flow}: publisher variable face did not use its weight axis at ${weight}`);
        check(parseFloat(style.webkitTextStrokeWidth) === 0, `${flow}: publisher variable face got an outline`);
        const nested = getComputedStyle(staticChild);
        check(nested.fontWeight === '700', `${flow}: publisher bold text changed at ${weight}`);
        check((parseFloat(nested.webkitTextStrokeWidth) > 0) === (weight > 400),
          `${flow}: publisher static descendant got the wrong outline at ${weight}`);
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
  writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8"><style>${css}</style>
    <style>
      @font-face{font-family:"Publisher VF";src:url("${fonts[1].url}");font-weight:200 900}
      #publisher{font-family:"Publisher VF", serif}
      #publisher-static{font-family:sans-serif;font-weight:700}
    </style></head>
    <body data-lumi-layout="reflowable"><p id="marked" style="font-size:20px">天地玄黄 Reading</p>
    <p id="publisher">Original <span id="publisher-static">bold</span></p><pre id="result" style="display:none"></pre>
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
