// Production EPUB CSS/JS in desktop Chromium. No Duokan code/assets or device APKs.
// JVM tests cover the structural markers modeled here; this checks browser behavior.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const output = path.join(root, '.local/duokan-implementation/browser');
fs.mkdirSync(output, { recursive: true });
const source = fs.readFileSync(path.join(root,
  'app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
function constant(name) {
  const match = source.match(new RegExp('private const val ' + name + ' = """([\\s\\S]*?)"""'));
  assert.ok(match, name);
  return match[1];
}
const assembly = source.match(/private val READER_SCRIPT:[\s\S]*?private const val READER_SCRIPT_PART_/)[0];
const script = [...assembly.matchAll(/append\((READER_SCRIPT_PART_\w+)\)/g)].map(m => constant(m[1])).join('');
new (require('node:vm').Script)(script);
const image = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="#527a91"/></svg>');
async function verify(options) {
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
  let checks = 0;
  const check = (ok, label) => { if (!ok) throw Error(label); checks++; };
  try {
    const configure = (generation = 1) => window.LumiReader.configure({ flow: options.flow,
      nativePaging: true, transition: 'none', configurationGeneration: generation,
      insets: { top: 24, right: 24, bottom: 24, left: 24 } });
    configure();
    await Promise.all([...document.images].map(i => i.decode().catch(() => {})));
    await wait(550);
    const avatar = document.getElementById('avatar');
    const avatarStyle = getComputedStyle(avatar);
    check(avatarStyle.display === 'inline', 'avatar must remain inline');
    check(Math.abs(avatar.getBoundingClientRect().height - parseFloat(avatarStyle.fontSize) * 3.5) < 2,
      'publisher avatar height must survive pagination');
    check(avatarStyle.verticalAlign === 'middle', 'avatar alignment');
    const small = document.getElementById('small-float');
    check(getComputedStyle(small).cssFloat === 'right', 'small illustration keeps text wrapping: ' + JSON.stringify({rect:small.getBoundingClientRect().toJSON(),style:getComputedStyle(small).cssText,attr:small.outerHTML,body:document.body.getBoundingClientRect().toJSON(),scroll:small.scrollHeight,viewport:[innerWidth,innerHeight]}));
    if (options.flow === 'paginated') {
      check(small.getAttribute('data-lumi-bounded-float') === 'true', 'small float accepted');
      check(getComputedStyle(document.getElementById('large-float')).cssFloat === 'none',
        'oversized illustration uses block fallback');
    }
    check(parseFloat(getComputedStyle(document.body).paddingLeft) === (options.fullWindow ? 0 : 24), 'full-window chapter insets');
    const artwork = document.getElementById('art-note');
    check(!artwork.hasAttribute('data-lumi-footnote-marker'), 'note artwork must not become a tiny marker');
    check(artwork.getBoundingClientRect().width >= innerWidth * .5, 'note artwork must remain readable at column width');
    check(getComputedStyle(artwork).display === 'block', 'note artwork keeps block layout');
    const originalLength = window.LumiReader.currentLocator().textLength;
    const click = el => {
      const r = el.getBoundingClientRect();
      el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true,
        clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 }));
    };
    for (const id of ['bare-note', 'linked-note', 'plain-type-note', 'sample-note', 'art-note']) {
      check(getComputedStyle(document.getElementById(id)).webkitTapHighlightColor === 'rgba(0, 0, 0, 0)',
        id + ' must not flash the WebView pressed-link overlay');
      const el = document.getElementById(id);
      el.scrollIntoView({ block: 'center' });
      await wait(60);
      window.messages.length = 0;
      // Exercise touchend and the following synthesized click as one user tap.
      const r = el.getBoundingClientRect();
      const touch = new Touch({ identifier: 1, target: el,
        clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 });
      el.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, cancelable: true,
        touches: [touch], targetTouches: [touch], changedTouches: [touch] }));
      el.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true,
        touches: [], targetTouches: [], changedTouches: [touch] }));
      click(el);
      await wait(120);
      const note = document.getElementById('lumi-footnote-popover');
      check(!!note, id + ' opens note');
      check(note.textContent.includes(id === 'bare-note' ? 'Literal <b>note</b>' : 'Linked note content'),
        id + ' displays correct body');
      check(!window.messages.some(m => ['tap', 'image', 'link', 'chapterTurn'].includes(m.type)),
        id + ' must not trigger image preview, page turn or navigation');
      check(!document.body.contains(note), 'popover must not enter body locator index');
      if (id === 'bare-note') check(!note.querySelector('b'), 'alt must be literal text');
      configure(10 + checks);
      check(window.LumiReader.currentLocator().textLength === originalLength, 'note must not change text index');
      document.body.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      await wait(250);
      check(!document.getElementById('lumi-footnote-popover'), 'outside tap closes note');
    }
    // A long press/context menu on an image note must not open the image viewer.
    const marker = document.getElementById('bare-note');
    window.messages.length = 0;
    const r = marker.getBoundingClientRect();
    const touch = new Touch({ identifier: 2, target: marker, clientX: r.left + 2, clientY: r.top + 2 });
    marker.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, touches: [touch], changedTouches: [touch] }));
    await wait(600);
    marker.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true,
      clientX: r.left + 2, clientY: r.top + 2 }));
    check(!window.messages.some(m => m.type === 'image'), 'image note long press must not preview');
    const ordinary = document.getElementById('ordinary');
    ordinary.scrollIntoView({ block: 'center' });
    await wait(60);
    const bounds = ordinary.getBoundingClientRect();
    ordinary.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true,
      clientX: bounds.left + 2, clientY: bounds.top + 2 }));
    check(window.messages.some(m => m.type === 'image'), 'ordinary image keeps preview');
    check(!document.getElementById('lumi-footnote-popover'), 'ordinary alt must not become a note');
    document.getElementById('result').textContent = JSON.stringify({ checks, options });
  } catch (e) { document.getElementById('result').textContent = JSON.stringify({ error: e.stack, checks, options }); }
}
const results = [];
for (const flow of ['scrolled', 'paginated']) for (const [size, fullWindow] of [[[480, 1000], false], [[1000, 760], false], [[480, 1000], true]]) {
  const options = { flow, size, fullWindow };
  const name = flow + '-' + size.join('x') + (fullWindow ? '-full-window' : '');
  const fixture = `<!doctype html><html><head><meta charset="utf-8"><style>
    body{font:20px/1.5 serif}p{margin:4px 0}.photo{height:3.5em;vertical-align:middle}
    .wrap{float:right;width:40%;margin-left:6px}.wrap img{width:100%}#large-float{width:80%}
    img.duokan-footnote{width:1em;height:1em}
    </style><style>${constant('READER_CSS')}\n${constant('FLOATED_MEDIA_CSS')}</style></head>
    <body data-lumi-layout="reflowable" ${fullWindow ? 'data-lumi-full-window="true"' : ''}><p><img id="avatar" class="photo" data-lumi-inline-media="true" src="${image}">Role</p>
    <p>Image note <img id="bare-note" class="duokan-footnote" src="${image}" alt="Literal &lt;b&gt;note&lt;/b&gt;"></p>
    <p><a id="linked-note" epub:type="noteref" href="#fn1">[1]</a></p>
    <p><a id="plain-type-note" type="noteref" href="#opaque">note</a></p>
    <p><a id="sample-note" class="duokan-footnote" type="noteref" href="#fn2"><img src="${image}"></a></p>
    <a class="duokan-footnote" type="noteref" href="#fn1" data-lumi-footnote-illustration-ref="true" data-lumi-media-wrapper="true">
      <img id="art-note" class="duokan-image-static" width="100%" data-lumi-footnote-illustration="true" src="${image}"></a>
    <aside id="fn1" epub:type="footnote">Linked note content</aside>
    <div id="opaque" type="footnote">Linked note content</div>
    <aside type="footnote"><ol class="duokan-footnote-content"><li id="fn2" class="duokan-footnote-item">
      <a href="#sample-note"><div><p>Linked note content</p></div></a></li></ol></aside>
    <div id="small-float" class="wrap" data-lumi-media-wrapper="true" data-lumi-floated-media="true"><img src="${image}"></div>
    <p>${'Text wraps around the illustration. '.repeat(8)}</p><div style="clear:both"></div>
    <div id="large-float" class="wrap" data-lumi-media-wrapper="true" data-lumi-floated-media="true"><img src="${image}"></div>
    <p>After illustration.</p><img id="ordinary" src="${image}" alt="Just a label">
    <pre id="result" style="display:none"></pre><script>window.messages=[];window.lumiNative={postMessage:v=>messages.push(JSON.parse(v))}</script>
    <script>${script}</script><script>(${verify.toString()})(${JSON.stringify(options)})</script></body></html>`;
  const file = path.join(output, name + '.html');
  fs.writeFileSync(file, fixture);
  const run = spawnSync(process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      '--user-data-dir=' + path.join(output, 'profile-' + name), '--window-size=' + size.join(','),
      '--virtual-time-budget=9000', '--dump-dom', pathToFileURL(file).href],
    { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
  assert.ifError(run.error);
  assert.equal(run.status, 0, run.stderr);
  const match = run.stdout.match(/<pre id="result"[^>]*>([^<]+)<\/pre>/);
  assert.ok(match, name + ': missing browser result');
  const result = JSON.parse(match[1].replaceAll('&quot;', '"').replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&'));
  assert.ok(!result.error, JSON.stringify(result));
  results.push(result);
  console.log(name + ': ' + result.checks + ' checks passed');
}
fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(results, null, 2) + '\n');
