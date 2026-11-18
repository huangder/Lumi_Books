// Footnote bodies leave the reading flow; their empty publisher shells must too.
// Run with desktop Chromium, without installing an Android test APK.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const {pathToFileURL} = require('node:url');
const root = path.resolve(__dirname, '..');
const out = path.join(root, '.local/footnote-containers');
fs.mkdirSync(out, {recursive:true});
const source = fs.readFileSync(path.join(root,
  'app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const alignment = fs.readFileSync(path.join(root,
  'app/src/main/java/com/huangder/lumibooks/util/epub/EpubReaderAlignment.kt'), 'utf8');
const constant = name => {
  const match = (name === 'EPUB_READER_ALIGNMENT_SCRIPT' ? alignment : source)
    .match(new RegExp('(?:private )?const val ' + name + ' = """([\\s\\S]*?)"""'));
  assert.ok(match, name);
  return match[1];
};
const assembly = source.match(/private val READER_SCRIPT:[\s\S]*?private const val READER_SCRIPT_PART_/)[0];
const script = [...assembly.matchAll(/append\((\w+)\)/g)].map(m => constant(m[1])).join('');

async function verify(flow) {
  let checks = 0;
  const check = (ok, label) => { if (!ok) throw Error(label); checks++; };
  const wait = () => new Promise(r => setTimeout(r, 100));
  try {
    const config = {flow, insets:{top:24, bottom:24, left:24, right:24}};
    LumiReader.configure(config);
    await wait(); await wait();
    for (const id of ['note1', 'note2', 'notes', 'outer', 'nested']) {
      const node = document.getElementById(id);
      check(getComputedStyle(node).display === 'none', id + ' must leave reading flow');
      check(node.getClientRects().length === 0, id + ' must paint no box or glow');
    }
    for (const id of ['chapter', 'mixed', 'kept-text', 'kept-image', 'unpaired', 'heading']) {
      check(document.getElementById(id).getClientRects().length > 0, id + ' must remain visible');
    }
    const length = LumiReader.currentLocator().textLength;
    const reference = document.getElementById('ref1');
    const rect = reference.getBoundingClientRect();
    reference.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true,
      clientX:rect.left + 1, clientY:rect.top + 1}));
    await wait(); await wait();
    const popup = document.getElementById('lumi-footnote-popover');
    check(popup && popup.textContent.includes('First note content'), 'hidden note still opens in popup');
    check(popup.querySelector('#lumi-footnote-content').getClientRects().length > 0, 'popup content remains visible');
    check(LumiReader.currentLocator().textLength === length, 'popup keeps source locator offsets');
    LumiReader.configure({...config, insets:{top:32, bottom:32, left:32, right:32}});
    await wait();
    check(getComputedStyle(document.getElementById('notes')).display === 'none', 'repagination keeps shell hidden');
    return {checks, passed:true};
  } catch (error) { return {checks, passed:false, error:error.stack}; }
}

for (const flow of ['paginated', 'scrolled']) {
  const file = path.join(out, flow + '.html');
  fs.writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8">
    <style>${constant('READER_CSS')}</style><style>
    body{font:20px/1.5 serif} .duokan-footnote-content{
      border:0px dashed #C108C3;margin:1em 1em .5em 1em;padding:5px!important;
      box-shadow:0 0 1em #E659E7;clear:both}
    </style></head><body data-lumi-layout="reflowable">
    <section id="chapter"><p>Chapter text <a id="ref1" class="duokan-footnote" href="#note1">[1]</a>
    <a class="duokan-footnote" href="#note2">[2]</a></p>
    <div id="outer"><ol id="notes" class="duokan-footnote-content">
    <!-- Publisher footnote container from the reported book. -->
    <li id="note1"><a href="#ref1">First note content</a></li>
    <li id="nested"><div><aside id="note2" epub:type="footnote">Second note content</aside></div></li>
    </ol></div><div id="mixed"><aside epub:type="footnote" id="other">Other note</aside>
    <p id="kept-text">Ordinary prose beside hidden notes</p></div>
    <div id="kept-image"><aside epub:type="footnote" id="image-note">Image note</aside>
    <img alt="Publisher illustration" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24'/%3E"></div>
    <ol id="unpaired" class="duokan-footnote-content"><li>Unreferenced publisher content</li></ol>
    <div id="heading">Publisher heading <aside epub:type="footnote" id="heading-note">Heading note</aside></div>
    <p><a epub:type="noteref" href="#other">[3]</a><a epub:type="noteref" href="#image-note">[4]</a>
    <a epub:type="noteref" href="#heading-note">[5]</a></p></section>
    <pre id="result" style="display:none"></pre>
    <script>window.lumiNative={postMessage:()=>{}};</script><script>${script}</script>
    <script>(${verify.toString()})('${flow}').then(r=>document.getElementById('result').textContent=JSON.stringify(r));</script>
    </body></html>`);
  const run = spawnSync(process.env.EPUB_PROBE_BROWSER ||
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--user-data-dir=' + path.join(out, 'profile-' + flow), '--window-size=480,1000',
    '--virtual-time-budget=4000', '--dump-dom', pathToFileURL(file).href],
    {windowsHide:true, encoding:'utf8', timeout:30000, maxBuffer:4 * 1024 * 1024});
  assert.ifError(run.error);
  assert.equal(run.status, 0, run.stderr);
  const match = run.stdout.match(/<pre id="result"[^>]*>([^<]+)<\/pre>/);
  assert.ok(match, flow + ': missing browser result');
  const result = JSON.parse(match[1].replaceAll('&gt;', '>').replaceAll('&lt;', '<').replaceAll('&amp;', '&'));
  assert.ok(result.passed, flow + ': ' + result.error);
  console.log(flow + ': ' + result.checks + ' checks passed');
}
