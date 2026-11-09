// Research probe for the current EPUB WebView CSS/bridge. No Android device/APKs.
// node tools/probe-duokan-compat.cjs [output-directory]
// Fixtures are independently authored; this does not run Duokan or its engine.
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const output = path.resolve(process.argv[2] || path.join(root, '.local/duokan-research/probe'));
fs.mkdirSync(output, { recursive: true });
const source = fs.readFileSync(path.join(root,
  'app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const constant = name => {
  const match = source.match(new RegExp('private const val ' + name + ' = """([\\s\\S]*?)"""'));
  assert.ok(match, 'Missing source constant ' + name);
  return match[1];
};
const css = constant('READER_CSS');
const floatedCss = constant('FLOATED_MEDIA_CSS');
const assembly = source.match(/private val READER_SCRIPT:[\s\S]*?private const val READER_SCRIPT_PART_/)[0];
const partNames = [...assembly.matchAll(/append\((READER_SCRIPT_PART_\w+)\)/g)].map(match => match[1]);
assert.ok(partNames.length >= 4, 'Missing production bridge assembly');
const script = partNames.map(constant).join('');
new (require('node:vm').Script)(script); // Reject incomplete extraction before launching Chromium.
const browser = process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const fontUrl = pathToFileURL(path.join(root, 'app/src/main/res/font/lxgw_wenkai.ttf')).href;
const image = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="#527a91"/></svg>');
const scenarios = [
  { name: 'publisher-only', reader: false },
  { name: 'reader-scrolled', reader: true, flow: 'scrolled' },
  { name: 'reader-paginated', reader: true, flow: 'paginated' },
  { name: 'reader-explicit-font', reader: true, flow: 'scrolled', fontFamily: 'monospace' },
  { name: 'reader-font-alias-proof', reader: true, flow: 'scrolled', alias: true },
];
async function measure(scenario) {
  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
  if (scenario.reader) {
    window.LumiReader.configure({ flow: scenario.flow, nativePaging: true, transition: 'none',
      fontFamily: scenario.fontFamily, insets: { top: 0, right: 0, bottom: 0, left: 0 } });
  }
  await Promise.all([...document.images].map(image => image.decode().catch(() => {})));
  await document.fonts.load('24px ProbeBundledKai');
  if (scenario.alias) await document.fonts.load('24px DK-KAITI');
  await document.fonts.ready;
  await delay(650);
  const style = id => {
    const el = document.getElementById(id), s = getComputedStyle(el), r = el.getBoundingClientRect();
    return { display: s.display, float: s.cssFloat, width: r.width, height: r.height,
      textIndent: s.textIndent, fontFamily: s.fontFamily, verticalAlign: s.verticalAlign,
      fontFeatureSettings: s.fontFeatureSettings, color: s.color };
  };
  const metric = family => {
    const canvas = document.createElement('canvas'), ctx = canvas.getContext('2d');
    ctx.font = '24px ' + family;
    return ctx.measureText('多看阅读排版适配 ABC 0123').width;
  };
  const data = { name: scenario.name, userAgent: navigator.userAgent,
    viewport: { width: innerWidth, height: innerHeight },
    cssSupports: { duokanIndent: CSS.supports('duokan-text-indent', '2em'),
      duokanDropCaps: CSS.supports('duokan-drop-caps-style', 'duokan-two-line-caps'),
      duokanMiddle: CSS.supports('vertical-align', 'duokan-middle-line'),
      initialLetter: CSS.supports('initial-letter', '2'),
      textSpacingTrim: CSS.supports('text-spacing-trim', 'trim-both'),
      lineBreakStrict: CSS.supports('line-break', 'strict') },
    customIndent: style('custom-indent'), standardIndent: style('standard-indent'),
    formula: style('formula'), classFormula: style('class-formula'),
    inlineStyleFormula: style('inline-style-formula'), chatAvatar: style('chat-avatar'),
    floatWrapper: style('float-wrapper'),
    publisherFeatures: style('publisher-features'),
    bodyFont: style('custom-indent').fontFamily, kai: style('kai'), hei: style('hei'),
    fontWidths: { dkKai: metric('"DK-KAITI"'), missing: metric('"DefinitelyMissingLumiFont"'),
      bundledKai: metric('"ProbeBundledKai"') },
    notes: [], position: scenario.reader ? window.LumiReader.currentPosition() : null };
  if (scenario.reader) {
    for (const id of ['bare-note', 'linked-note', 'noteref_1']) {
      // Scroll/bring the actual marker into view, then dispatch its ordinary click.
      const el = document.getElementById(id);
      el.scrollIntoView({ block: 'center' });
      await delay(30);
      window.messages.length = 0;
      const rect = el.getBoundingClientRect();
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true,
        clientX: rect.left + rect.width / 2, clientY: rect.top + rect.height / 2 }));
      await delay(180);
      const popover = document.getElementById('lumi-footnote-popover');
      data.notes.push({ id, opened: !!popover, text: popover?.textContent || '',
        events: window.messages.map(message => message.type) });
      if (popover) { document.body.dispatchEvent(new MouseEvent('click', { bubbles: true })); await delay(350); }
    }
    // Prove why adding hidden footnote bodies is not a harmless DOM rewrite.
    // Keep probe-result empty during these measurements: it is a body text node too.
    // Vary only the public configuration identity to avoid the locator's frame cache.
    const freshTextLength = generation => {
      window.LumiReader.configure({ flow: scenario.flow, nativePaging: true, transition: 'none',
        fontFamily: scenario.fontFamily, configurationGeneration: generation,
        insets: { top: 0, right: 0, bottom: 0, left: 0 } });
      return window.LumiReader.currentLocator().textLength;
    };
    const before = freshTextLength(101);
    const hidden = document.createElement('aside');
    hidden.hidden = true;
    hidden.textContent = 'INJECTED_NOTE_123';
    document.body.appendChild(hidden);
    const insideBody = freshTextLength(102);
    hidden.remove();
    document.documentElement.appendChild(hidden);
    const outsideBody = freshTextLength(103);
    hidden.remove();
    data.hiddenTextIndexProof = { before, insideBody, outsideBody,
      injectedUtf16Length: 'INJECTED_NOTE_123'.length };
  }
  document.getElementById('probe-result').textContent = JSON.stringify(data);
}
const report = { generatedAt: new Date().toISOString(),
  scope: 'Desktop Chromium, independently authored fixtures, current Kotlin CSS/JS constants; wrapper marker modeled explicitly, not full JVM transform or Android WebView.',
  sourceSha256: require('node:crypto').createHash('sha256').update(source).digest('hex'), results: [] };
for (const scenario of scenarios) {
  const file = path.join(output, scenario.name + '.html');
  const fixture = `<!doctype html><html><head><meta charset="utf-8">
    <style>
    @font-face{font-family:ProbeBundledKai;src:url(${JSON.stringify(fontUrl)})}
    ${scenario.alias ? `@font-face{font-family:DK-KAITI;src:url(${JSON.stringify(fontUrl)})}` : ''}
    body{margin:0;font:20px/1.6 serif;color:#242424}p{margin:8px 0}
    #custom-indent{duokan-text-indent:2em}#standard-indent{text-indent:2em}
    #formula{height:1em;width:1em;display:inline;vertical-align:duokan-middle-line}
    .formula{height:1em;width:1em;display:inline;vertical-align:middle}
    .photo{height:3.5em;vertical-align:middle}
    #float-wrapper{float:right;width:35%;margin-left:8px}#float-wrapper img{width:100%}
    #publisher-features{font-feature-settings:"liga" 0}
    #kai{font-family:DK-KAITI}#hei{font-family:DK-HEITI}
    img.duokan-footnote{width:.8em;height:.8em}
    </style>${scenario.reader ? `<style>${css}\n${floatedCss}</style>` : ''}</head>
    <body data-lumi-layout="reflowable">
    <p id="custom-indent">专有缩进测试</p><p id="standard-indent">标准缩进测试</p>
    <p>行内公式之前<img id="formula" src="${image}">行内公式之后</p>
    <p>类选择器公式之前<img id="class-formula" class="formula" src="${image}">公式之后</p>
    <p>内联样式公式之前<img id="inline-style-formula" style="height:1em;width:1em;display:inline;vertical-align:middle" src="${image}">公式之后</p>
    <p class="name"><img id="chat-avatar" class="photo" src="${image}">角色名</p>
    <div id="float-wrapper" data-lumi-media-wrapper="true" data-lumi-floated-media="true"><img src="${image}"></div>
    <p>环绕文字。${'这是独立编写的图文混排样本。'.repeat(14)}</p><div style="clear:both"></div>
    <p id="publisher-features">出版社禁用连字测试</p>
    <p id="kai">楷体角色</p><p id="hei">黑体角色</p>
    <p>图片注释<img id="bare-note" class="duokan-footnote" src="${image}" alt="图片自身携带的注释正文"></p>
    <p>链接注释<a id="linked-note" epub:type="noteref" href="#fn1">[1]</a></p>
    <aside id="fn1" epub:type="footnote">标准链接的注释正文</aside>
    <p>多看注释<a id="noteref_1" class="duokan-footnote" href="#footnote_1"><img src="${image}" alt=""/></a></p>
    <aside><ol class="duokan-footnote-content"><li id="footnote_1" class="duokan-footnote-item">
      <a href="#noteref_1"><div><p>与样书同形的多看注释正文</p></div></a>
    </li></ol></aside>
    <pre id="probe-result" style="display:none"></pre>
    <script>window.messages=[];window.lumiNative={postMessage:value=>window.messages.push(JSON.parse(value))};</script>
    ${scenario.reader ? `<script>${script}</script>` : ''}
    <script>(${measure.toString()})(${JSON.stringify(scenario)}).catch(error=>document.getElementById('probe-result').textContent=JSON.stringify({error:error.stack}))</script>
    </body></html>`;
  fs.writeFileSync(file, fixture);
  const result = spawnSync(browser, ['--headless=new', '--disable-gpu', '--no-first-run',
    '--no-default-browser-check', '--allow-file-access-from-files',
    '--user-data-dir=' + path.join(output, 'profile-' + scenario.name), '--window-size=480,1100',
    '--virtual-time-budget=6500', '--dump-dom', pathToFileURL(file).href],
    { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, result.stderr);
  const match = result.stdout.match(/<pre id="probe-result"[^>]*>([^<]+)<\/pre>/);
  assert.ok(match, 'No report: ' + scenario.name);
  const data = JSON.parse(match[1].replaceAll('&quot;', '"').replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&'));
  assert.ok(!data.error, data.error);
  report.results.push(data);
  console.log(JSON.stringify(data));
}
fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(report, null, 2) + '\n');
console.log('Saved research observations:', path.join(output, 'results.json'));
