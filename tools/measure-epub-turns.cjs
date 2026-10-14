// Uses the installed debug fixture activity. Never installs APKs or modifies book progress.
// node tools/measure-epub-turns.cjs [epub|native_css|native] [text|images|tutorial] [slide curl scroll fade none]
const { spawnSync } = require('node:child_process');
const { mkdirSync, writeFileSync } = require('node:fs');
const { join } = require('node:path');
const adb = process.env.ADB || 'F:/SDK/platform-tools/adb.exe';
const engine = process.argv[2] || 'epub';
const fixture = process.argv[3] || 'text';
const transitions = process.argv.slice(4);
const modes = transitions.length ? transitions : ['slide', 'curl', 'scroll', 'fade', 'none'];
const directory = join(__dirname, '../app/build/epub-performance', engine + '-' + fixture);
mkdirSync(directory, { recursive: true });
function command(args) {
  const result = spawnSync(adb, args, { encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 32 * 1024 * 1024 });
  if (result.error || result.status !== 0) throw result.error || Error(result.stderr || result.stdout);
  return result.stdout;
}
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const p95 = values => values.length ? values.sort((a,b)=>a-b)[Math.ceil(values.length * .95) - 1] : null;
function acknowledgedTurns(run) {
  const within = [], cross = [], skipped = [];
  let pending;
  for (const line of run.split('\n')) {
    const time = line.match(/(\d\d):(\d\d):(\d\d)\.(\d\d\d)/);
    if (!time) continue;
    const milliseconds = ((Number(time[1]) * 60 + Number(time[2])) * 60 + Number(time[3])) * 1000 + Number(time[4]);
    const intent = line.match(/intent=(\d+) chapter=(\d+) page=(\d+) count=(\d+)/);
    if (intent) {
      pending = {index:Number(intent[1]), chapter:Number(intent[2]), page:Number(intent[3]), count:Number(intent[4]), milliseconds};
      continue;
    }
    const position = line.match(/position engine=\w+ transition=\w+ chapter=(\d+) page=(\d+) count=(\d+)/);
    if (!position || !pending) continue;
    const chapter = Number(position[1]), page = Number(position[2]);
    if (chapter === pending.chapter && page === pending.page) continue;
    const expectedCross = pending.page + 1 >= pending.count;
    if (expectedCross ? chapter !== pending.chapter + 1 || page !== 0 : chapter !== pending.chapter || page !== pending.page + 1)
      skipped.push({input:pending.index, from:[pending.chapter,pending.page], to:[chapter,page]});
    (chapter === pending.chapter ? within : cross).push(milliseconds - pending.milliseconds);
    pending = null;
  }
  return {withinCount:within.length, withinP95:p95(within), crossCount:cross.length, crossP95:p95(cross), unexpectedPositions:skipped};
}
async function main() {
  const reports = [];
  for (const transition of modes) {
    const since = command(['shell', 'date', '+%m-%d_%H:%M:%S.000']).trim().replace('_', ' ');
    // Clear-top finishes earlier fixture activities and their automation before starting a run.
    command(['shell', 'am', 'start', '--activity-clear-top', '-n',
      'com.huangder.lumibooks/.ui.reader.EpubPerformanceActivity', '--es', 'engine', engine,
      '--es', 'transition', transition, '--es', 'fixture', fixture, '--ei', 'turns', '120']);
    command(['shell', 'dumpsys', 'gfxinfo', 'com.huangder.lumibooks', 'reset']);
    let logs = '', run = '', complete = false;
    for (let attempt = 0; attempt < 30; attempt++) {
      await wait(5000);
      logs = command(['logcat', '-d', '-v', 'threadtime', '-T', since, '-s', 'EpubPerfFixture:D',
        'ReaderTurnTiming:D', 'EpubPageTurnHost:D', 'EpubCurlCommit:D', 'NativeEpub:D']);
      const marker = `START engine=${engine} transition=${transition} fixture=${fixture} turns=120`;
      const start = logs.lastIndexOf(marker);
      if (start >= 0) {
        run = logs.slice(start);
        if (run.includes(`DONE engine=${engine} transition=${transition}`)) { complete = true; break; }
      }
      if (attempt % 6 === 0) console.log(`Collecting ${engine}/${transition}, ${attempt * 5 + 5}s`);
    }
    writeFileSync(join(directory, transition + '.log'), run || logs);
    writeFileSync(join(directory, transition + '-memory.txt'), command(['shell', 'dumpsys', 'meminfo', 'com.huangder.lumibooks']));
    writeFileSync(join(directory, transition + '-frames.txt'), command(['shell', 'dumpsys', 'gfxinfo', 'com.huangder.lumibooks', 'framestats']));
    const timings = [...run.matchAll(/firstFrameMs=([\d.]+) preloaded=(true|false) crossChapter=(true|false)/g)]
      .map(m => ({ms:Number(m[1]), preloaded:m[2]==='true', cross:m[3]==='true'}));
    const within = timings.filter(t=>t.preloaded && !t.cross), cross = timings.filter(t=>t.preloaded && t.cross);
    const snapshots = [...run.matchAll(/snapshot role=\w+ transition=\w+ durationMs=([\d.]+)/g)].map(m=>Number(m[1]));
    const nativeFrames = [...run.matchAll(/nativeFrameMs=([\d.]+)/g)].map(m=>Number(m[1]));
    const gaps = [...run.matchAll(/maxFrameGapMs=(\d+)/g)].map(m=>Number(m[1]));
    const report = {engine, fixture, transition, complete, inputs:[...run.matchAll(/intent=\d+ chapter=/g)].length,
      measured:timings.length, preloaded:timings.filter(t=>t.preloaded).length,
      withinCount:within.length, withinP95:p95(within.map(t=>t.ms)), crossCount:cross.length,
      crossP95:p95(cross.map(t=>t.ms)), cold:timings.filter(t=>!t.preloaded),
      nativeFrameCount:nativeFrames.length, nativeFrameP95:p95(nativeFrames),
      snapshotCount:snapshots.length, snapshotP95:p95(snapshots), maxCrossFrameGap:gaps.length ? Math.max(...gaps) : null,
      // Position notifications include animation completion; these are not first-frame timings.
      acknowledgement:acknowledgedTurns(run)};
    reports.push(report);
    writeFileSync(join(directory, 'summary.json'), JSON.stringify(reports, null, 2));
    console.log(JSON.stringify(report));
    if (!complete) throw Error('Fixture timed out: ' + transition);
  }
}
if (require.main === module) main().catch(error=>{console.error(error);process.exitCode=1;});
module.exports = { acknowledgedTurns };
