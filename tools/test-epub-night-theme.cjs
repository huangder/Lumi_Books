// Real Chromium CSS-cascade regression for publisher layout; no Android device/APKs.
// Run: node tools/test-epub-night-theme.cjs
// Set EPUB_PROBE_BROWSER to use a different Chromium executable.
const assert = require('node:assert/strict');
const { readFileSync, writeFileSync, mkdtempSync, rmSync } = require('node:fs');
const { join, resolve, dirname, basename } = require('node:path');
const { tmpdir } = require('node:os');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');

const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const scriptParts = [...source.matchAll(/private const val READER_SCRIPT_PART_(?:1B|[123]) = """([\s\S]*?)"""/g)];
assert.equal(scriptParts.length, 4, 'All production reader script parts must be exercised');
const readerScript = scriptParts.map(match => match[1]).join('');
const readerCss = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];

async function verifyThemes() {
  let checks = 0;
  const assert = (condition, label) => { if (!condition) throw Error(label); checks++; };
  const wait = () => new Promise(resolve => setTimeout(resolve, 40));
  const reader = window.LumiReader;
  const cfg = { flow: 'paginated', theme: 'day', preservePublisherBackground: false };
  const publisherColors = {
    colored: 'rgb(204, 34, 34)', blue: 'rgb(18, 52, 86)', green: 'rgb(0, 128, 0)',
    inherited: 'rgb(0, 128, 0)', light: 'rgb(245, 245, 245)', transparent: 'rgba(0, 0, 0, 0)',
    'dark-blue': 'rgb(0, 0, 15)',
    'svg-text': 'rgb(0, 0, 255)',
    'colored-link': 'rgb(102, 51, 153)', 'link-emphasis': 'rgb(102, 51, 153)',
    'important-red': 'rgb(204, 34, 34)', 'important-inherited': 'rgb(204, 34, 34)',
    'colored-heading': 'rgb(18, 52, 86)',
  };
  const checkTextColors = (bodyColor, label) => {
    for (const id of ['copy', 'gray', 'near-gray', 'important-black', 'nested-black', 'code-black']) {
      assert(getComputedStyle(document.getElementById(id)).color === bodyColor, label + ' neutral text / ' + id);
    }
    for (const [id, color] of Object.entries(publisherColors)) {
      assert(getComputedStyle(document.getElementById(id)).color === color, label + ' original color / ' + id);
    }
  };
  try {
    reader.configure(cfg);
    for(let i=0;i<120 && !window.__messages.some(m=>m.type==='layoutStable');i++) await wait();
    assert(window.__messages.some(m=>m.type==='layoutStable'), 'initial layout stable');
    assert(reader.currentPosition().pageCount > 1, 'fixture spans multiple pages');
    reader.goToPage(1);
    await wait(); await wait();
    const before = reader.currentPosition();
    const rectBefore = document.getElementById('copy').getBoundingClientRect();
    reader.configure({...cfg, theme:'night', backgroundColor:'#111111', autoTextColor:'#e8e8ea'});
    await wait(); await wait();
    assert(getComputedStyle(document.documentElement).filter === 'none', 'night must not invert the page');
    assert(getComputedStyle(document.documentElement).backgroundColor === 'rgb(17, 17, 17)', 'night paper is dark');
    assert(getComputedStyle(document.getElementById('copy')).color === 'rgb(232, 232, 234)', 'publisher text becomes readable');
    assert(getComputedStyle(document.getElementById('colored')).color === 'rgb(204, 34, 34)', 'publisher colored text is preserved');
    assert(getComputedStyle(document.getElementById('paper')).backgroundImage === 'none', 'large publisher gradient yields to night');
    assert(getComputedStyle(document.getElementById('picture')).filter === 'none', 'illustration is not inverted');
    assert(reader.currentPosition().layoutRevision === before.layoutRevision, 'paint update must not repaginate');
    assert(reader.currentPosition().pageIndex === before.pageIndex, 'paint update keeps position');
    const rectAfter = document.getElementById('copy').getBoundingClientRect();
    assert(Math.abs(rectBefore.x - rectAfter.x) < 1 && Math.abs(rectBefore.width - rectAfter.width) < 1,
      'paint update preserves actual column geometry');
    checkTextColors('rgb(232, 232, 234)', 'night');
    for (const [theme, color] of [
      ['sepia_dark', 'rgb(232, 213, 188)'], ['green_dark', 'rgb(200, 230, 201)'],
      ['night', 'rgb(232, 232, 234)'],
    ]) {
      reader.configure({...cfg, theme});
      await wait(); await wait();
      checkTextColors(color, theme);
      assert(reader.currentPosition().layoutRevision === before.layoutRevision, theme + ' keeps pagination');
      assert(reader.currentPosition().pageIndex === before.pageIndex, theme + ' keeps page');
    }
    reader.configure({...cfg, theme:'night', textColor:'#fafafa'});
    await wait(); await wait();
    checkTextColors('rgb(250, 250, 250)', 'custom night body color');
    reader.configure({...cfg, theme:'night'});
    await wait(); await wait();
    checkTextColors('rgb(232, 232, 234)', 'remove custom night body color');
    // A suite may use any theme identity with a custom dark paper/color.
    // Explicit text colors must preserve publisher accents as well.
    for (const [themeConfig, color, label] of [
      [{theme:'day', backgroundColor:'#101820', autoTextColor:'#e0e8f0'},
        'rgb(224, 232, 240)', 'default suite custom dark paper'],
      [{theme:'sepia', backgroundColor:'#302010', autoTextColor:'#eddcba'},
        'rgb(237, 220, 186)', 'sepia suite custom dark paper'],
      [{theme:'green', backgroundColor:'#102818', autoTextColor:'#d0ead8'},
        'rgb(208, 234, 216)', 'green suite custom dark paper'],
      [{theme:'day', backgroundColor:'#191919', autoTextColor:'#dddddd', textColor:'#ffffff'},
        'rgb(255, 255, 255)', 'explicit default suite color'],
      [{theme:'sepia_dark', textColor:'#fafafa'}, 'rgb(250, 250, 250)', 'explicit dark sepia'],
      [{theme:'green_dark', textColor:'#f0fff0'}, 'rgb(240, 255, 240)', 'explicit dark green'],
      [{theme:'sepia'}, 'rgb(62, 39, 35)', 'light sepia'],
      [{theme:'green'}, 'rgb(27, 94, 32)', 'light green'],
      [{theme:'day'}, 'rgb(51, 51, 51)', 'default'],
    ]) {
      reader.configure({...cfg, preservePublisherBackground:false, ...themeConfig});
      await wait(); await wait();
      checkTextColors(color, label);
      assert(reader.currentPosition().layoutRevision === before.layoutRevision, label + ' keeps pagination');
      assert(reader.currentPosition().pageIndex === before.pageIndex, label + ' keeps page');
    }
    reader.configure({...cfg, theme:'night', publisherPaintOnly:true, backgroundColor:'#111111',
      autoTextColor:'#e8e8ea', textColor:'#ffffff', coverBackgroundColor:'#000000',
      backgroundImage:true, backgroundUrl:'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg"/>'});
    await wait(); await wait();
    assert(!document.documentElement.classList.contains('lumi-night'), 'publisher suite ignores night tint');
    assert(getComputedStyle(document.getElementById('copy')).color === 'rgb(0, 0, 0)', 'publisher suite restores text');
    assert(getComputedStyle(document.getElementById('colored')).color === 'rgb(204, 34, 34)', 'publisher suite restores colored text');
    assert(document.getElementById('important-black').style.getPropertyValue('color') === 'rgb(34, 34, 34)', 'restore inline black color');
    assert(document.getElementById('important-black').style.getPropertyPriority('color') === 'important', 'restore inline black priority');
    assert(document.getElementById('blue').style.getPropertyValue('color') === '', 'stylesheet color restores without inline residue');
    assert(document.getElementById('inherited').style.getPropertyValue('color') === '', 'inherited color restores without inline residue');
    assert(document.getElementById('important-red').style.getPropertyValue('color') === 'rgb(204, 34, 34)', 'restore inline accent color');
    assert(document.getElementById('important-red').style.getPropertyPriority('color') === 'important', 'restore inline accent priority');
    assert(document.getElementById('important-inherited').style.getPropertyValue('color') === 'inherit', 'restore inherited accent declaration');
    assert(document.getElementById('important-inherited').style.getPropertyPriority('color') === 'important', 'restore inherited accent priority');
    assert(document.getElementById('paper').style.getPropertyValue('background-color') === 'rgb(250, 240, 220)', 'restore inline paper color');
    assert(document.getElementById('paper').style.getPropertyPriority('background-color') === 'important', 'restore inline priority');
    assert(getComputedStyle(document.getElementById('paper')).backgroundImage.includes('gradient'), 'restore publisher gradient');
    assert(document.documentElement.style.getPropertyValue('background-color') ===
      (window.__rootPaper ? 'rgb(253, 252, 251)' : ''), 'restore root inline color or absence');
    assert(document.documentElement.style.getPropertyPriority('background-color') ===
      (window.__rootPaper ? 'important' : ''), 'restore root priority or absence');
    assert(!document.getElementById('lumi-cover-background'), 'publisher suite ignores cover override');
    assert(!document.getElementById('lumi-reader-background') ||
      getComputedStyle(document.getElementById('lumi-reader-background')).display === 'none', 'publisher suite ignores custom image');
    // Match the native configuration identity used when a suite is switched.
    // The old stable geometry must not announce the new paint as already drawn.
    for (let cycle = 0; cycle < 6; cycle++) {
      const generation = 100 + cycle * 3;
      const start = window.__messages.length;
      for (let step = 0; step < 3; step++) {
        reader.configure({...cfg, configurationGeneration:generation + step,
          theme: ['night', 'sepia_dark', 'day'][step], publisherPaintOnly:step === 2});
        reader.reportLayoutStatus();
      }
      assert(!window.__messages.slice(start).some(m => m.type === 'layoutStable'),
        'rapid suite changes wait for current configuration frames');
      const position = reader.currentPosition();
      const targetPage = Math.min(position.pageCount - 1, position.pageIndex + 1);
      reader.preparePage(targetPage, generation);
      await wait(); await wait(); await wait();
      assert(window.__messages.slice(start).some(m => m.type === 'layoutStable' &&
        m.payload.configurationGeneration === generation + 2), 'latest suite becomes stable');
      assert(window.__messages.slice(start).some(m => m.type === 'pagePrepared' &&
        m.payload.configurationGeneration === generation + 2 && m.payload.requestToken === generation &&
        m.payload.pageIndex === targetPage), 'latest suite can prepare the next page');
      reader.previous();
      assert(reader.currentPosition().pageIndex === Math.max(0, targetPage - 1), 'turn after suite changes');
    }
    reader.configure({...cfg, flow:'scrolled', theme:'night', backgroundColor:'#171717'});
    await wait(); await wait();
    assert(getComputedStyle(document.documentElement).backgroundColor === 'rgb(23, 23, 23)', 'scrolled night paper');
    checkTextColors('rgb(232, 232, 234)', 'scrolled night');
    reader.configure({...cfg, flow:'scrolled', publisherPaintOnly:true, theme:'night'});
    await wait(); await wait();
    assert(getComputedStyle(document.getElementById('copy')).color === 'rgb(0, 0, 0)', 'scrolled publisher text restored');
    assert(getComputedStyle(document.getElementById('colored')).color === 'rgb(204, 34, 34)', 'scrolled publisher colored text restored');
    for (const [id, color] of Object.entries(publisherColors)) {
      assert(getComputedStyle(document.getElementById(id)).color === color, 'restore scrolled original color / ' + id);
    }
    // Full-page container backgrounds obey the same switch in every palette.
    for (const theme of ['night', 'sepia_dark', 'green_dark', 'custom:white']) {
      reader.configure({...cfg, preservePublisherBackground:true, flow:'scrolled', theme,
        backgroundColor:'#383838', autoTextColor:'#e8e8ea'});
      await wait(); await wait();
      assert(getComputedStyle(document.getElementById('paper')).backgroundImage.includes('gradient'),
        theme + ' preserves full-page container background');
      assert(getComputedStyle(document.getElementById('copy')).color === 'rgb(0, 0, 0)',
        theme + ' keeps readable original text over publisher background');
    }
    for (const flow of ['paginated', 'scrolled']) {
      reader.configure({...cfg, flow, publisherPaintOnly:true, theme:'day'});
      await wait(); await wait();
      const original = reader.currentPosition();
      reader.configure({...cfg, flow, publisherPaintOnly:true, theme:'publisher_night'});
      await wait(); await wait();
      const shade = document.getElementById('lumi-publisher-night-shade');
      assert(shade && getComputedStyle(shade).backgroundColor === 'rgba(0, 0, 0, 0.45)', flow + ' original night dims');
      assert(getComputedStyle(shade).pointerEvents === 'none', flow + ' shade permits interaction');
      assert(shade.parentElement === document.documentElement, flow + ' shade stays outside page clones');
      assert(getComputedStyle(document.documentElement).filter === 'none', flow + ' shade does not transform fixed backgrounds');
      assert(getComputedStyle(document.getElementById('copy')).color === 'rgb(0, 0, 0)', flow + ' shade preserves text colors');
      assert(getComputedStyle(document.getElementById('paper')).backgroundImage.includes('gradient'), flow + ' shade preserves paper');
      assert(reader.currentPosition().layoutRevision === original.layoutRevision, flow + ' shade does not repaginate');
      assert(reader.currentPosition().pageIndex === original.pageIndex, flow + ' shade keeps page');
      reader.configure({...cfg, flow, publisherPaintOnly:true, theme:'day'});
      await wait(); await wait();
      assert(!document.getElementById('lumi-publisher-night-shade'), flow + ' day removes shade');
      reader.configure({...cfg, flow, publisherPaintOnly:true, theme:'publisher_night'});
      reader.configure({...cfg, flow, theme:'night'});
      assert(!document.getElementById('lumi-publisher-night-shade'), flow + ' another suite removes shade');
    }
    reader.configure({...cfg, fontFamily:'monospace'});
    assert(reader.currentPosition().layoutRevision > before.layoutRevision, 'font change still repaginates');
    document.getElementById('alignment-result').textContent = JSON.stringify({passed:true,checks});
  } catch(error) {
    document.getElementById('alignment-result').textContent = JSON.stringify({passed:false,checks,error:error.message});
  }
}

async function verifyBackgrounds() {
  let checks = 0;
  const assert = (condition, label) => { if (!condition) throw Error(label); checks++; };
  const wait = () => new Promise(resolve => setTimeout(resolve, 40));
  const reader = window.LumiReader;
  try {
    reader.configure({theme:'day', preservePublisherBackground:true});
    for (let i=0;i<120 && !window.__messages.some(m=>m.type==='layoutStable');i++) await wait();
    assert(window.__messages.some(m=>m.type==='layoutStable'), 'background fixture ready');
    for (const flow of ['paginated', 'scrolled']) {
      for (const theme of ['night', 'sepia_dark', 'green_dark', 'custom:white']) {
        reader.configure({theme, flow, preservePublisherBackground:true, backgroundColor:'#383838', autoTextColor:'#e8e8ea'});
        await wait(); await wait();
        const layer = document.getElementById('lumi-publisher-background');
        const visibleLayer = layer && getComputedStyle(layer).display !== 'none';
        const paper = visibleLayer ? layer : document.querySelector(window.__backgroundTarget);
        assert(visibleLayer || document.body.dataset.lumiLayout === 'pre_paginated', theme + ' / ' + flow + ' publisher layer visible');
        assert(getComputedStyle(paper).backgroundImage.includes('gradient'), theme + ' / ' + flow + ' background retained');
        assert(getComputedStyle(paper).backgroundImage.includes('data:image/svg+xml'), theme + ' / ' + flow + ' background image retained');
        assert(getComputedStyle(document.getElementById('copy')).color === 'rgb(0, 0, 0)', theme + ' original contrast retained');
        reader.configure({theme, flow, preservePublisherBackground:false});
        await wait(); await wait();
        assert(!layer || getComputedStyle(layer).display === 'none', theme + ' switch hides book background');
        assert(getComputedStyle(document.querySelector(window.__backgroundTarget)).backgroundImage === 'none', theme + ' switch hides direct book background');
        assert(getComputedStyle(document.getElementById('copy')).color !== 'rgb(0, 0, 0)', theme + ' theme text on theme paper');
      }
      reader.configure({theme:'publisher_night', flow, publisherPaintOnly:true});
      await wait(); await wait();
      const shade = document.getElementById('lumi-publisher-night-shade');
      assert(!!shade, flow + ' original night shade exists');
      const rect = shade.getBoundingClientRect();
      assert(rect.width >= document.documentElement.clientWidth && rect.height >= innerHeight && rect.top === 0,
        flow + ' shade fills viewport: ' + JSON.stringify({rect:rect.toJSON(),innerWidth,innerHeight,clientWidth:document.documentElement.clientWidth}));
      if (flow === 'scrolled') {
        window.scrollTo(0, 200);
        await wait();
        assert(shade.getBoundingClientRect().top === 0, 'shade stays fixed during scroll');
        const layer = document.getElementById('lumi-publisher-background');
        if (layer && getComputedStyle(layer).display !== 'none') {
          assert(layer.getBoundingClientRect().top === 0, 'publisher background stays fixed');
        }
      }
    }
    reader.configure({theme:'day', publisherPaintOnly:true});
    assert(!document.getElementById('lumi-publisher-night-shade'), 'return to day removes shade');
    document.getElementById('alignment-result').textContent = JSON.stringify({passed:true,checks});
  } catch(error) {
    document.getElementById('alignment-result').textContent = JSON.stringify({passed:false,checks,error:error.message});
  }
}

const directory = mkdtempSync(join(tmpdir(), 'lumi-epub-alignment-'));
try {
  writeFileSync(join(directory, 'publisher.css'),
    '#blue { color: #123456 !important; } #gray { color: #333; } #green { color: green; }');
  for (const rootPaper of [true, false]) {
  const fixture = join(directory, rootPaper ? 'root-paper.html' : 'transparent-paper.html');
  writeFileSync(fixture, `<!doctype html><html ${rootPaper ? 'style="background-color:#fdfcfb!important"' : ''}><head><meta charset="utf-8">
    <link rel="stylesheet" href="publisher.css">
    <style>${readerCss}</style></head><body data-lumi-layout="reflowable" data-lumi-cover="true">
    <section id="paper" style="width:100%;min-height:100vh;background-color:#faf0dc!important;background-image:linear-gradient(white,#eee)!important">
    <p id="copy" style="color:#000000">Publisher <span id="colored" style="color:#cc2222">colored</span> text</p>
    <p id="gray">Dark gray body text</p><p id="important-black" style="color:#222!important">Inline black body text</p>
    <p id="near-gray" style="color:#303234">Near gray text</p><p id="dark-blue" style="color:#00000f!important">Very dark blue text</p>
    <p id="blue">Blue <strong>heading</strong></p>
    <h2 id="colored-heading" style="color:#123456">Colored heading</h2>
    <p style="color:black">Body <a id="colored-link" href="#copy" style="color:rebeccapurple!important">Link <em id="link-emphasis">emphasis</em></a>
      <span id="important-red" style="color:#cc2222!important">Red <b id="important-inherited" style="color:inherit!important">inherited accent</b></span></p>
    <p id="green">Green <span id="inherited"><em>inherited text</em></span>
      <span id="nested-black" style="color:black">Black text inside green</span></p>
    <p style="color:black">Body <span id="light" style="color:#f5f5f5">Light text</span>
      <span id="transparent" style="color:transparent">Transparent text</span></p>
    <pre><code id="code-black" style="color:black">Black code</code></pre>
    ${Array.from({length:30}, (_,i)=>`<p>${i} ${'Long publisher text for a paginated chapter. '.repeat(20)}</p>`).join('')}
    <svg id="picture" width="40" height="40"><rect width="40" height="40" fill="red"/><text id="svg-text" style="color:blue">SVG text</text></svg></section>
    <pre id="alignment-result"></pre>
    <script>window.__rootPaper=${rootPaper};window.requestAnimationFrame=f=>setTimeout(()=>f(performance.now()),16);window.__messages=[];window.lumiNative={postMessage:m=>__messages.push(JSON.parse(m))};</script>
    <script>${readerScript}</script><script>(${verifyThemes.toString()})()</script>
    </body></html>`);
  const result = spawnSync(process.env.EPUB_PROBE_BROWSER ||
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
      '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      '--user-data-dir=' + join(directory, 'profile'), '--virtual-time-budget=5000', '--run-all-compositor-stages-before-draw', '--dump-dom', pathToFileURL(fixture).href,
    ], { windowsHide: true, encoding: 'utf8', timeout: 30000, maxBuffer: 8 * 1024 * 1024 });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, result.stderr);
  const match = result.stdout.match(/<pre id="alignment-result">([^<]+)<\/pre>/);
  assert.ok(match, 'Browser did not produce an alignment result');
  const report = JSON.parse(match[1]);
  assert.ok(report.passed, report.error);
  console.log('PASS:', report.checks, 'real-browser night theme and paint-only checks, root paper:', rootPaper);
  }
  for (const target of ['html', 'body']) {
    for (const layout of ['reflowable', 'pre_paginated']) {
    const fixture = join(directory, target + '-' + layout + '-background.html');
    writeFileSync(fixture, `<!doctype html><html><head><meta charset="utf-8">
      ${layout === 'pre_paginated' ? '<meta name="viewport" content="width=600,height=800">' : ''}
      <style>${readerCss}</style><style>${target}{background-color:#fff;
        background-image:linear-gradient(#ffffff88,#aaaaaa88),url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='60' height='80'%3E%3Crect width='60' height='80' fill='%2399ccee'/%3E%3C/svg%3E");
        background-size:cover}body{color:#000}</style>
      </head><body data-lumi-layout="${layout}"><p id="copy">Original black text</p>
      ${Array.from({length:25},()=>`<p>${'Publisher text. '.repeat(70)}</p>`).join('')}
      <pre id="alignment-result"></pre>
      <script>window.__backgroundTarget='${target}';window.requestAnimationFrame=f=>setTimeout(()=>f(performance.now()),16);window.__messages=[];window.lumiNative={postMessage:m=>__messages.push(JSON.parse(m))};</script>
      <script>${readerScript}</script><script>(${verifyBackgrounds.toString()})()</script></body></html>`);
    const result = spawnSync(process.env.EPUB_PROBE_BROWSER ||
      'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
        '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--user-data-dir=' + join(directory, 'profile'), '--virtual-time-budget=7000',
        '--run-all-compositor-stages-before-draw', '--dump-dom', pathToFileURL(fixture).href,
      ], { windowsHide:true, encoding:'utf8', timeout:30000, maxBuffer:8*1024*1024 });
    if (result.error) throw result.error;
    assert.equal(result.status, 0, result.stderr);
    const match = result.stdout.match(/<pre id="alignment-result">([^<]+)<\/pre>/);
    assert.ok(match, 'Browser did not produce a background result');
    const report = JSON.parse(match[1]);
    assert.ok(report.passed, report.error);
    console.log('PASS:', report.checks, 'real-browser original background checks, target:', target, 'layout:', layout);
    }
  }
} finally {
  // Only remove the uniquely created test directory directly inside temp.
  const absolute = resolve(directory);
  assert.equal(dirname(absolute), resolve(tmpdir()));
  assert.ok(basename(absolute).startsWith('lumi-epub-alignment-'));
  rmSync(absolute, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
}
