// Chromium checks against responses exported by EpubTutorialDocumentTest.
// Isolated headless profile: never connects to an Android device or user's browser session.
const { readFileSync, readdirSync, mkdtempSync } = require('node:fs');
const { join } = require('node:path');
const { tmpdir } = require('node:os');
const { createServer } = require('node:http');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const directory = join(__dirname, '../app/build/reports/epub-diagnostics');
const chapters = readdirSync(directory).filter(name => /^tutorial-[0-9]+[.]xhtml$/.test(name));
assert.ok(chapters.length > 0, 'Run EpubTutorialDocumentTest first');
const currentScript = process.argv.includes('--current-script')
  ? Array.from(readFileSync(join(__dirname, '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8')
      .matchAll(/private const val READER_SCRIPT_PART_[123] = """([\s\S]*?)"""/g), match => match[1]).join('')
  : null;
if(currentScript!==null) assert.ok(currentScript.includes('reportLayoutStatus: reportLayoutStatus'));
const server = createServer((request, response) => {
  const name = request.url.slice(1);
  if (chapters.includes(name)) {
    response.setHeader('Content-Type', 'application/xhtml+xml; charset=utf-8');
    // Remap only the asset-loader origin to the local server.
    let html=readFileSync(join(directory, name),'utf8');
    if(currentScript!==null) html=html.replace(/(<script\b[^>]*id="lumi-reader-script"[^>]*>)[\s\S]*?(<\/script>)/,
      (_,open,close)=>open+'//<![CDATA[\n'+currentScript+'\n//]]>'+close);
    response.end(html.replaceAll('https://appassets.androidplatform.net', 'http://127.0.0.1:'+server.address().port));
  } else if (name.endsWith('/style.css')) {
    response.setHeader('Content-Type','text/css');response.end(readFileSync(join(directory,'style.css')));
  } else { response.setHeader('Content-Type', 'text/css'); response.end(''); }
});
let browser;
async function main() {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const profile = mkdtempSync(join(tmpdir(), 'lumi-epub-regression-'));
  browser = spawn(process.env.EPUB_PROBE_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
      '--remote-debugging-pipe', '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
      '--user-data-dir=' + profile], {windowsHide:true, stdio:['ignore','ignore','ignore','pipe','pipe']});
  let serial = 0, buffer = '';
  const pending = new Map();
  browser.stdio[4].on('data', chunk => {
    buffer += chunk.toString();
    let index;
    while ((index = buffer.indexOf('\0')) >= 0) {
      const message = JSON.parse(buffer.slice(0,index));buffer = buffer.slice(index+1);
      if (pending.has(message.id)) {
        const {resolve,reject,timer} = pending.get(message.id);pending.delete(message.id);clearTimeout(timer);
        if(message.error) reject(new Error(JSON.stringify(message.error))); else resolve(message.result);
      }
    }
  });
  const call = (method,params={},sessionId) => new Promise((resolve,reject) => {
    const id=++serial;
    const timer=setTimeout(()=>{pending.delete(id);reject(new Error('CDP timeout: '+method));},15000);
    pending.set(id,{resolve,reject,timer});
    browser.stdio[3].write(JSON.stringify({id,method,params,sessionId})+'\0');
  });
  const {targetId}=await call('Target.createTarget',{url:'about:blank'});
  const {sessionId}=await call('Target.attachToTarget',{targetId,flatten:true});
  await call('Page.enable',{},sessionId);
  await call('Page.addScriptToEvaluateOnNewDocument',{source:
    'window.__lumiMessages=[];window.lumiNative={postMessage:function(m){__lumiMessages.push(JSON.parse(m));}};'},sessionId);
  const evaluate = async expression => {
    const value=await call('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true},sessionId);
    if(value.exceptionDetails) throw new Error(JSON.stringify(value.exceptionDetails));
    return value.result.value;
  };
  let checks=0;
  for(const chapter of chapters) for(const flow of ['paginated','scrolled']) {
    await call('Page.navigate',{url:'http://127.0.0.1:'+server.address().port+'/'+chapter},sessionId);
    await new Promise(resolve=>setTimeout(resolve,80));
    const result=await evaluate('('+async function(flow) {
      const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
      for(let i=0;i<250 && (document.readyState!=='complete'||!window.LumiReader);i++) await delay(20);
      if(document.querySelector('parsererror') || !window.LumiReader) throw Error('Invalid chapter document '+location.href+' '+document.documentElement.outerHTML.slice(0,500));
      const reader=window.LumiReader;
      reader.configure({flow,configurationGeneration:1,canTurnPrevious:false,canTurnNext:false});
      const stable=async()=>{
        for(let i=0;i<150;i++) {
          const revision=reader.currentPosition().layoutRevision;
          if(__lumiMessages.some(m=>m.type==='layoutStable'&&m.payload.layoutRevision===revision)) return;
          await delay(20);
        }
        throw Error('No stable layout');
      };
      await stable();
      // Model A -> B -> A: the returning WebView is already configured. It must
      // acknowledge its existing stable layout without a reload or repagination.
      const reusedRevision=reader.currentPosition().layoutRevision;
      for(let returnVisit=0;returnVisit<4;returnVisit++) {
        const before=__lumiMessages.length;
        reader.configure({flow,configurationGeneration:1});
        reader.setHighlights([]);
        reader.reportLayoutStatus();
        const acknowledgement=__lumiMessages.slice(before).find(m=>m.type==='layoutStable');
        if(!acknowledgement || acknowledgement.payload.layoutRevision!==reusedRevision ||
          acknowledgement.payload.configurationGeneration!==1) throw Error('Reused chapter did not acknowledge stable layout');
        reader.goToPage(returnVisit%2?0:2147483647);
        const position=reader.currentPosition();
        if(position.pageIndex!==(returnVisit%2?0:position.pageCount-1))throw Error('Reused chapter destination incorrect');
      }
      reader.goToPage(0);
      const exact=document.querySelector('p').textContent.slice(0,12);
      const item={exact,start:{exact},ruleStyle:{fontWeight:700,italic:true,textColor:'#ff0000'}};
      reader.setHighlights([item]);
      const pendingStart=__lumiMessages.length;
      reader.reportLayoutStatus();
      if(__lumiMessages.slice(pendingStart).some(m=>m.type==='layoutStable'))throw Error('Unstable changed layout acknowledged too early');
      await stable();
      const span=document.querySelector('[data-lumi-rule-style]');
      if(!span) throw Error('Missing inline rule style');
      const revision=reader.currentPosition().layoutRevision;
      for(let i=0;i<5;i++){reader.setHighlights([item]);reader.setTtsHighlight(-1,-1,'');}
      if(span!==document.querySelector('[data-lumi-rule-style]')) throw Error('Repeated data rebuilt DOM');
      if(reader.currentPosition().layoutRevision!==revision) throw Error('Repeated data repaginated');
      const createRange=document.createRange.bind(document);
      let calls=0;
      document.createRange=function(){if(++calls===2)throw Error('injected range failure');return createRange();};
      reader.setHighlights([{exact,start:{exact},color:'#ff0000'},item]);
      document.createRange=createRange;
      await stable();
      reader.setHighlights([]);await stable();
      if(document.querySelector('[data-lumi-rule-style]'))throw Error('Inline styles not removed');
      if(location.pathname.endsWith('/tutorial-0.xhtml')) {
        const many=Array.from({length:600},()=>({exact,start:{exact},ruleStyle:{textColor:'#ff0000'}}));
        reader.setHighlights(many);await stable();
        reader.setHighlights(many);
        if(document.querySelectorAll('[data-lumi-rule-style]').length)throw Error('Paint-only rules mutated text');
        reader.setHighlights([]);await stable();
      }
      if(flow==='scrolled' && location.pathname.endsWith('/tutorial-0.xhtml')) {
        reader.configure({flow,configurationGeneration:2,canTurnPrevious:true,canTurnNext:true});
        await stable();
        const gesture=async direction=>{
          window.scrollTo(0,direction>0?document.scrollingElement.scrollHeight:0);
          await delay(40);
          const emit=(type,y)=>{
            const touch=new Touch({identifier:1,target:document.body,clientX:200,clientY:y});
            document.body.dispatchEvent(new TouchEvent(type,{bubbles:true,cancelable:true,
              touches:type==='touchend'?[]:[touch],changedTouches:[touch]}));
          };
          emit('touchstart',300);emit('touchmove',300-direction*100);emit('touchend',300-direction*100);
        };
        const turns=()=>__lumiMessages.filter(m=>m.type==='chapterTurn');
        const before=turns().length;
        await gesture(1);
        const assertVisible=()=>{
          if(Number(getComputedStyle(document.body).opacity)<=0)throw Error('Waiting chapter faded to blank');
        };
        assertVisible();
        await delay(240);assertVisible();
        if(turns().length!==before+1 || turns().at(-1).payload.direction!==1)throw Error('Next chapter request missing');
        await gesture(1);
        if(turns().length!==before+1)throw Error('Repeated input duplicated pending chapter request');
        reader.cancelChapterTurn();await delay(240);
        if(getComputedStyle(document.body).opacity!=='1' || document.body.style.transform!=='')throw Error('Cancelled turn did not restore original');
        await gesture(-1);
        if(turns().length!==before+2 || turns().at(-1).payload.direction!==-1)throw Error('Previous chapter request missing');
        reader.finishChapterTurn();
        if(document.body.style.opacity!=='1' || document.body.style.transform!=='' || document.body.style.transition!=='none')throw Error('Hidden outgoing chapter was not reset');
        await gesture(1);
        if(turns().length!==before+3)throw Error('Returning chapter cannot turn again');
        reader.finishChapterTurn();
      }
      return {flow,revision:reader.currentPosition().layoutRevision,
        failures:__lumiMessages.filter(m=>m.type==='highlightDiagnostic'&&m.payload.failed).length};
    }.toString()+')('+JSON.stringify(flow)+')');
    assert.equal(result.flow,flow);checks++;
    console.log(chapter,JSON.stringify(result));
  }
  console.log('PASS:',checks,'real-browser chapter/flow checks. Script:',currentScript===null?'compiled export':'current Kotlin source','Profile:',profile);
  await call('Browser.close');
}
main().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{
  if(browser && browser.exitCode===null) browser.kill();server.close();
});
