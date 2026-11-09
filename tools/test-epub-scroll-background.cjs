// Real browser check: fixed publisher paper fills the viewport while only the
// foreground is clipped, including its hit testing. No device/test APK required.
const assert = require('node:assert/strict');
const {readFileSync, writeFileSync, mkdtempSync, rmSync} = require('node:fs');
const {join, resolve, dirname, basename} = require('node:path');
const {tmpdir} = require('node:os');
const {pathToFileURL} = require('node:url');
const {spawn} = require('node:child_process');
const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const parts = [...source.matchAll(/private const val READER_SCRIPT_PART_\w+ = """([\s\S]*?)"""/g)];
assert.equal(parts.length, 5);
const script = parts.map(m => m[1]).join('');
const css = source.match(/private const val READER_CSS = """([\s\S]*?)"""/)[1];
async function verify() {
  let checks = 0;
  function check(value, label) { if (!value) throw Error(label); checks++; }
  const wait = () => new Promise(r => setTimeout(r, 80));
  const reader = window.LumiReader;
  const config = {flow:'scrolled', publisherPaintOnly:true,
    scrollViewportInsets:{top:55, bottom:75}, insets:{left:20, right:20}};
  function foregroundAt(y) {
    const hit = document.elementFromPoint(innerWidth / 2, y);
    if (hit && hit.closest('#lumi-scroll-viewport-masks')) return false;
    return hit && (hit === document.body || document.body.contains(hit));
  }
  function paperFillsViewport(label) {
    const paper = document.getElementById('lumi-publisher-background');
    check(!!paper && getComputedStyle(paper).display !== 'none', label + ' paper exists');
    const box = paper.getBoundingClientRect();
    check(Math.abs(box.top) < 1 && box.height >= innerHeight && box.width >= document.documentElement.clientWidth,
      label + ' background reaches both viewport edges ' + JSON.stringify({box:box.toJSON(),innerHeight,innerWidth}));
    check(getComputedStyle(paper).backgroundImage.includes('data:image/svg+xml'), label + ' photo retained');
  }
  try {
    if (window.__fallback) window.ScrollTimeline = undefined;
    reader.configure(config);
    await wait(); await wait();
    paperFillsViewport('initial');
    const first = document.getElementById('first').getBoundingClientRect();
    check(first.top >= 55, 'first foreground starts below margin');
    if (!window.__fixed) {
      for (const y of [0, 180, 731, 1400]) {
        window.scrollTo(0, y);
        await wait(); await wait();
        paperFillsViewport('scroll ' + y);
        check(!foregroundAt(20), 'top margin cannot hit foreground at ' + y + ' ' + JSON.stringify({
          scrollY, clip:getComputedStyle(document.body).clipPath, rect:document.body.getBoundingClientRect().toJSON(),
          animations:document.body.getAnimations().map(a=>({playState:a.playState,time:String(a.currentTime),timeline:String(a.timeline.currentTime)}))}));
        check(!foregroundAt(innerHeight - 20), 'bottom margin cannot hit foreground at ' + y);
        check(foregroundAt(innerHeight / 2), 'body remains hittable at ' + y);
        const locator = reader.currentPosition().locator;
        let node = document.documentElement;
        for (const index of locator.domPath || []) node = node.childNodes[index];
        if (node && node.nodeType === Node.TEXT_NODE) {
          const range = document.createRange();
          range.setStart(node, Math.min(locator.textOffset, node.length - 1));
          range.setEnd(node, Math.min(locator.textOffset + 1, node.length));
          check(range.getBoundingClientRect().bottom > 55, 'saved locator skips clipped top content');
        }
      }
      check(getComputedStyle(document.body).clipPath === 'none', 'foreground does not wait for moving JS clip');
      check(!!document.getElementById('lumi-scroll-viewport-masks'), 'fixed paper masks cover both margins');
      // A large scroll jump is followed by a deliberate main-thread stall.
      // The margin masks retain their fixed geometry without any scroll handler.
      const masks = document.getElementById('lumi-scroll-viewport-masks');
      const oldRects = [...masks.children].map(n=>n.getBoundingClientRect().toJSON());
      window.scrollTo(0, 3000);
      const deadline = performance.now() + 100;
      while(performance.now() < deadline) {}
      check(!foregroundAt(20) && !foregroundAt(innerHeight-20), 'rapid scroll cannot expose foreground in margins');
      check(JSON.stringify([...masks.children].map(n=>n.getBoundingClientRect().toJSON())) === JSON.stringify(oldRects),
        'paper masks stay fixed while scroll processing is delayed');
      // Chapter overscroll translates the foreground. Its clip must continue
      // to follow the viewport through the drag and animated snap-back.
      window.scrollTo(0, document.scrollingElement.scrollHeight);
      await wait();
      const emit = (type, y) => {
        const touch = new Touch({identifier:1,target:document.body,clientX:200,clientY:y});
        document.body.dispatchEvent(new TouchEvent(type,{bubbles:true,cancelable:true,
          touches:type==='touchend'?[]:[touch],changedTouches:[touch]}));
      };
      emit('touchstart',300); emit('touchmove',200);
      await wait();
      check(getComputedStyle(document.body).opacity === '1', 'chapter drag keeps publisher paint opaque');
      check(!foregroundAt(20) && !foregroundAt(innerHeight - 20), 'chapter drag keeps foreground clip fixed');
      emit('touchend',200);
      await wait();
      check(getComputedStyle(document.body).opacity === '1', 'waiting for a neighbour keeps the current page opaque');
      reader.cancelChapterTurn();
      await wait(); await wait(); await wait();
      check(!foregroundAt(20) && !foregroundAt(innerHeight - 20), 'snap-back keeps foreground clip fixed');
      paperFillsViewport('chapter drag');
    }
    // Live settings path must retain the paper and update the clip immediately.
    reader.configure({...config, scrollViewportInsets:{top:95, bottom:105}});
    await wait(); await wait();
    paperFillsViewport('live margins');
    check(!foregroundAt(75) && !foregroundAt(innerHeight - 85), 'live margins update hit testing');
    reader.configure({...config, scrollViewportInsets:{top:0, bottom:0}});
    await wait(); await wait();
    check(getComputedStyle(document.body).clipPath === 'none', 'zero margins remove foreground clip');
    paperFillsViewport('zero margins');
    reader.configure({...config, flow:'paginated', scrollViewportInsets:{top:0, bottom:0}, insets:{top:55,bottom:75,left:20,right:20}});
    await wait(); await wait();
    check(getComputedStyle(document.body).clipPath === 'none', 'paging removes scroll clip');
    check(!document.body.getAnimations().some(a => a.timeline && typeof ScrollTimeline === 'function' && a.timeline instanceof ScrollTimeline),
      'paging cancels scroll timeline');
    return {passed:true, checks};
  } catch (error) {
    return {passed:false, checks, error:error.message};
  }
}
const directory = mkdtempSync(join(tmpdir(), 'lumi-scroll-paper-'));
let browser;
async function main() {
  browser = spawn(process.env.EPUB_PROBE_BROWSER ||
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', [
    '--headless=new','--no-first-run','--no-default-browser-check',
    '--disable-background-timer-throttling','--disable-renderer-backgrounding',
    '--user-data-dir='+join(directory,'profile'),'--remote-debugging-pipe'],
    {windowsHide:true,stdio:['ignore','ignore','ignore','pipe','pipe']});
  let serial = 0, buffer = '';
  const pending = new Map();
  browser.stdio[4].on('data', chunk => {
    buffer += chunk.toString();
    let end;
    while ((end = buffer.indexOf('\0')) >= 0) {
      const message = JSON.parse(buffer.slice(0,end)); buffer = buffer.slice(end+1);
      if (pending.has(message.id)) {
        const {resolve,reject,timer} = pending.get(message.id); pending.delete(message.id); clearTimeout(timer);
        if(message.error) reject(Error(JSON.stringify(message.error))); else resolve(message.result);
      }
    }
  });
  const call = (method,params={},sessionId) => new Promise((resolve,reject) => {
    const id = ++serial, timer = setTimeout(()=>{pending.delete(id);reject(Error('CDP timeout: '+method));},15000);
    pending.set(id,{resolve,reject,timer});
    browser.stdio[3].write(JSON.stringify({id,method,params,sessionId})+'\0');
  });
  const {targetId} = await call('Target.createTarget',{url:'about:blank'});
  const {sessionId} = await call('Target.attachToTarget',{targetId,flatten:true});
  await call('Page.enable',{},sessionId);
  const evaluate = async expression => {
    const value = await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true},sessionId);
    if(value.exceptionDetails) throw Error(JSON.stringify(value.exceptionDetails));
    return value.result.value;
  };
  for (const target of ['html','body']) for (const fixed of [false,true]) for (const fallback of [false,true]) {
    const fixture = join(directory, `${target}-${fixed}-${fallback}.html`);
    writeFileSync(fixture, `<!doctype html><html><head><meta charset="utf-8">
      ${fixed ? '<meta name="viewport" content="width=600,height=800">' : ''}
      <style>${css}</style><style>${target}{background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='60' height='80'%3E%3Crect width='60' height='80' fill='%2399ccee'/%3E%3C/svg%3E");background-size:cover}
      p{margin:0;padding:0;line-height:30px}</style></head>
      <body data-lumi-layout="${fixed?'pre_paginated':'reflowable'}"><p id="first">First line</p>
      ${Array.from({length:100},(_,i)=>`<p><a href="#first">${i} ${'Foreground text '.repeat(15)}</a></p>`).join('')}
      <script>window.__fixed=${fixed};window.__fallback=${fallback};window.lumiNative={postMessage:()=>{}};</script>
      <script>${script}</script></body>
      <pre id="result" style="position:fixed;display:none"></pre></html>`);
    await call('Page.navigate',{url:pathToFileURL(fixture).href},sessionId);
    const report = await evaluate(`(async()=>{
      for(let i=0;i<100 && (document.readyState!=='complete'||!window.LumiReader);i++) await new Promise(r=>setTimeout(r,20));
      return (${verify.toString()})();})()`);
    assert.ok(report.passed,`${target}/${fixed?'fixed':'reflow'}/${fallback?'fallback':'timeline'}: ${report.error}`);
    if (target === 'html' && !fixed && !fallback) {
      await evaluate(`(async()=>{LumiReader.configure({flow:'scrolled',publisherPaintOnly:true,
        scrollViewportInsets:{top:55,bottom:75}});await new Promise(r=>setTimeout(r,150));
        scrollTo(0,3000);await new Promise(r=>setTimeout(r,100));})()`);
      const shot = await call('Page.captureScreenshot',{format:'png'},sessionId);
      writeFileSync(join(directory,'mask.png'),Buffer.from(shot.data,'base64'));
      const {spawnSync} = require('node:child_process');
      const pixels = spawnSync('python',['-c',
        'from PIL import Image; import sys; im=Image.open(sys.argv[1]).convert("RGB"); w,h=im.size; '+
        'assert all(im.getpixel((x,y))==(153,204,238) for x in range(10,w-25,20) for y in list(range(1,54,5))+list(range(h-74,h-1,5))), "foreground leaked or publisher paper changed at margins"',
        join(directory,'mask.png')],{windowsHide:true,encoding:'utf8'});
      assert.equal(pixels.status,0,pixels.stderr);
      console.log('PASS fast-scroll screenshot pixels: publisher paper fills both margins');
      const header = await evaluate(`(async()=>{
        const image=document.createElement('img');image.id='authored-header';
        image.setAttribute('data-lumi-bleed','horizontal-top');
        image.src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='600' height='180'%3E%3Crect width='600' height='180' fill='%23cc4488'/%3E%3C/svg%3E";
        document.body.insertBefore(image,document.body.firstChild);await image.decode();
        LumiReader.configure({flow:'scrolled',publisherPaintOnly:true,scrollViewportInsets:{top:55,bottom:75}});
        scrollTo(0,0);await new Promise(r=>setTimeout(r,200));
        const rect=image.getBoundingClientRect();const hit=document.elementFromPoint(innerWidth/2,20);
        if(Math.abs(rect.top)>1||hit!==image)throw Error('authored top-bleed header was cropped by reader margin');
        const press=new MouseEvent('contextmenu',{bubbles:true,cancelable:true,clientX:innerWidth/2,clientY:20});
        image.dispatchEvent(press);
        if(!press.defaultPrevented)throw Error('visible header lost image preview interaction in margin');
        scrollTo(0,rect.height+300);await new Promise(r=>setTimeout(r,100));
        const margin=document.elementFromPoint(innerWidth/2,20);
        if(!margin.closest('#lumi-scroll-viewport-masks'))throw Error('body text leaked after header scrolled away');
        LumiReader.configure({flow:'paginated',publisherPaintOnly:true,insets:{top:55,bottom:75}});
        await new Promise(r=>setTimeout(r,150));
        if(image.style.zIndex==='2147482001')throw Error('header exemption leaked into paginated mode');
        return true;})()`);
      assert.ok(header);
      console.log('PASS authored full-bleed header remains visible above margin paper; body stays clipped');
    }
    console.log('PASS', target, fixed?'fixed':'reflow', fallback?'fallback':'timeline',report.checks,'checks');
  }
  await call('Browser.close');
}
main().catch(error=>{console.error(error);process.exitCode=1;}).finally(async()=>{
  if(browser && browser.exitCode===null) {
    browser.kill();
    await new Promise(resolve=>browser.once('exit',resolve));
  }
  assert.equal(dirname(resolve(directory)),resolve(tmpdir()));
  assert.ok(basename(directory).startsWith('lumi-scroll-paper-'));
  rmSync(directory,{recursive:true,force:true,maxRetries:5,retryDelay:100});
});
