// Run after the opt-in EpubDuokanLocalCorpusTest export. Outputs only aggregates.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const root = path.resolve(process.argv[2] || path.join(__dirname, '../.local/duokan-implementation/corpus'));
const productionSource = fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const currentScript = [...productionSource.matchAll(/private const val READER_SCRIPT_PART_\w+ = """([\s\S]*?)"""/g)].map(m=>m[1]).join('');
async function verify(flow) {
  const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
  const check = (condition, message) => { if (!condition) throw Error(message); };
  try {
    window.messages = [];
    window.lumiNative = { postMessage: v => window.messages.push(JSON.parse(v)) };
    window.LumiReader.configure({ flow, nativePaging: true, transition: 'none',
      insets: { top: flow === 'scrolled' ? 0 : 24, right: 24, bottom: flow === 'scrolled' ? 0 : 24, left: 24 },
      scrollViewportInsets: {top: flow === 'scrolled' ? 24 : 0, bottom: flow === 'scrolled' ? 36 : 0} });
    await Promise.race([Promise.all([...document.images].map(i => i.decode().catch(() => {}))), wait(2500)]);
    await Promise.race([document.fonts.ready, wait(2500)]);
    await wait(500);
    const avatars = [...document.querySelectorAll('img.photo')];
    for (const avatar of avatars) {
      const style = getComputedStyle(avatar);
      check(['inline', 'inline-block'].includes(style.display), 'avatar unexpectedly block');
      check(Math.abs(avatar.getBoundingClientRect().height - parseFloat(style.fontSize) * 3.5) < 2,
        'avatar publisher height lost');
    }
    const floats = [...document.querySelectorAll('[data-lumi-floated-media="true"]')];
    const notes = [...document.querySelectorAll('a.duokan-footnote[href]')];
    let openedNotes = 0;
    for (let index = 0; index < notes.length; index++) {
      const anchor = notes[index];
      if (!anchor.id) anchor.id = 'corpus-note-' + index;
      window.LumiReader.goToFragment('#' + anchor.id);
      await wait(80);
      window.messages.length = 0;
      const artwork = anchor.querySelector('img.duokan-image-static[width="100%"]');
      if (artwork) {
        check(artwork.getAttribute('data-lumi-footnote-illustration') === 'true', 'note artwork not classified');
        check(!artwork.hasAttribute('data-lumi-footnote-marker'), 'note artwork misclassified as tiny marker');
        const imageRect = artwork.getBoundingClientRect();
        check(imageRect.width >= innerWidth * .5, 'full-width note artwork too small');
        check(imageRect.height > 60, 'note artwork has icon height');
      }
      const rect = anchor.getBoundingClientRect();
      // A block note can be taller than the visible remainder of a scroll page.
      // Hit-test the visible part, rather than an off-screen center of its full box.
      const left = Math.max(0, rect.left), right = Math.min(innerWidth, rect.right);
      const top = Math.max(flow === 'scrolled' ? 24 : 0, rect.top),
        bottom = Math.min(innerHeight - (flow === 'scrolled' ? 36 : 0), rect.bottom);
      const x = (left + right) / 2, y = (top + bottom) / 2;
      const point = right > left && bottom > top ? document.elementFromPoint(x, y) : null;
      check(point && (point === anchor || anchor.contains(point)), 'footnote not hit-testable at restored position ' +
        JSON.stringify({rect:rect.toJSON(),x,y,scrollY,clip:getComputedStyle(document.body).clipPath,hit:point&&point.tagName}));
      point.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true,
        clientX: x, clientY: y }));
      await wait(100);
      const popover = document.getElementById('lumi-footnote-popover');
      check(popover && popover.textContent.trim().length > 0, 'missing rich footnote content');
      check(!window.messages.some(m => ['image', 'link', 'tap', 'chapterTurn'].includes(m.type)), 'footnote interaction escaped');
      openedNotes++;
      document.body.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      await wait(250);
    }
    const result = { flow, avatars: avatars.length, floats: floats.length,
      boundedFloats: floats.filter(f => f.getAttribute('data-lumi-bounded-float') === 'true').length,
      notes: openedNotes, fullWindow: document.body.getAttribute('data-lumi-full-window') === 'true',
      pageCount: window.LumiReader.currentPosition().pageCount,
      missingImages: [...document.images].filter(i => !i.naturalWidth).length };
    const output = document.createElement('pre'); output.id = 'corpus-result';
    output.textContent = JSON.stringify(result); document.documentElement.appendChild(output);
  } catch (e) {
    const output = document.createElement('pre'); output.id = 'corpus-result';
    output.textContent = JSON.stringify({ flow, error: e.stack }); document.documentElement.appendChild(output);
  }
}
const results = [];
const books = fs.readdirSync(root).filter(n => /^book-\d+$/.test(n));
assert.ok(books.length, 'No exported books');
for (const book of books) {
  const chapters = fs.readFileSync(path.join(root, book, 'chapters.txt'), 'utf8').trim().split(/\r?\n/);
  for (const chapter of chapters) for (const flow of ['paginated', 'scrolled']) {
    const name = path.basename(chapter, '.html') + '-' + flow;
    const file = path.join(root, book, name + '.html');
    const source = fs.readFileSync(chapter, 'utf8')
      .replace(/(<script\b[^>]*id="lumi-reader-script"[^>]*>)[\s\S]*?(<\/script>)/,
        (_,open,close)=>open+'//<![CDATA[\n'+currentScript+'\n//]]>'+close)
      .replace(/<\/body>/i,
      `<script>(${verify.toString()})(${JSON.stringify(flow)})</script></body>`);
    fs.writeFileSync(file, source);
    const run = spawnSync(process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
      ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--allow-file-access-from-files',
        '--user-data-dir=' + path.join(root, 'browser-profile'), '--window-size=480,1000',
        '--virtual-time-budget=18000', '--dump-dom', pathToFileURL(file).href],
      { windowsHide: true, encoding: 'utf8', timeout: 45000, maxBuffer: 12 * 1024 * 1024 });
    assert.ifError(run.error); assert.equal(run.status, 0, run.stderr);
    const match = run.stdout.match(/<pre id="corpus-result"[^>]*>([^<]+)<\/pre>/);
    if (!match) fs.writeFileSync(path.join(root, book, name + '-diagnostic.html'), run.stdout);
    assert.ok(match, book + '/' + name + ' produced no result');
    const data = JSON.parse(match[1].replaceAll('&quot;', '"').replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&'));
    assert.ok(!data.error, book + '/' + name + ': ' + data.error);
    results.push({ book, chapter: path.basename(chapter), ...data });
    console.log(JSON.stringify(results.at(-1)));
  }
}
fs.writeFileSync(path.join(root, 'browser-results.json'), JSON.stringify(results, null, 2) + '\n');
