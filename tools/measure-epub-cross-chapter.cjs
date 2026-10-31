// Cross-chapter paging baseline. Uses the installed debug build only: never installs
// APKs and never touches library data or reading progress.
//   Fixture run:  node tools/measure-epub-cross-chapter.cjs --transition=slide --input=swipe --interval=250 --turns=80
//   Real reading: node tools/measure-epub-cross-chapter.cjs --capture=90   (flip pages on the phone meanwhile)
//   Existing log: node tools/measure-epub-cross-chapter.cjs --log=path/to/file.log
const { spawn, spawnSync } = require('node:child_process');
const { mkdirSync, readFileSync, writeFileSync } = require('node:fs');
const { join } = require('node:path');

const adb = process.env.ADB || 'F:/SDK/platform-tools/adb.exe';
const options = Object.fromEntries(process.argv.slice(2).map(argument => {
  const [key, value = 'true'] = argument.replace(/^--/, '').split(/=(.*)/s);
  return [key, value];
}));
const tags = ['EpubTurnTrace:D', 'EpubPerfFixture:D', 'AndroidRuntime:E'];
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

function adbSync(args) {
  const result = spawnSync(adb, args, { encoding: 'utf8', windowsHide: true, timeout: 30000 });
  if (result.error || result.status !== 0) throw result.error || Error(result.stderr || result.stdout);
  return result.stdout;
}

function parse(text) {
  return text.split(/\r?\n/).flatMap(line => {
    const body = line.split('EpubTurnTrace: ')[1];
    if (!body) return [];
    const event = {};
    for (const token of body.trim().split(' ')) {
      const split = token.indexOf('=');
      if (split > 0) event[token.slice(0, split)] = token.slice(split + 1);
    }
    return event.ev ? [event] : [];
  });
}

const number = value => value === undefined ? NaN : Number(value);
function stats(values) {
  const sorted = values.filter(Number.isFinite).sort((a, b) => a - b);
  if (!sorted.length) return null;
  const at = q => sorted[Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * q) - 1))];
  return { n: sorted.length, p50: at(0.5), p90: at(0.9), max: sorted[sorted.length - 1] };
}
function countBy(items, key) {
  const counts = {};
  for (const item of items) counts[item[key] ?? '-'] = (counts[item[key] ?? '-'] || 0) + 1;
  return counts;
}
// Position of a swipe relative to the chapter boundary it crosses or has just crossed.
function phase(swipe) {
  if (!swipe.to || swipe.to === '-') return 'no_target';
  const [fromChapter, fromPage] = (swipe.from || '').split('/');
  const [toChapter] = swipe.to.split('/');
  if (fromChapter !== toChapter) return 'cross';
  if (swipe.dir === '1' && (fromPage === '0' || fromPage === '1')) return 'after' + (Number(fromPage) + 1);
  return 'inner';
}

function summarize(events) {
  const of = name => events.filter(event => event.ev === name);
  const swipes = of('swipe').filter(swipe => swipe.path !== 'selection');
  const stalled = swipe => swipe.moved === '0' || number(swipe.waitMs) > 120;
  const stageOf = { doc_served: 'served', doc_finished: 'finished', js_ready: 'ready',
    js_layoutStable: 'stable', js_pagePrepared: 'prepared', slot_ready: 'slotReady' };
  const loads = [];
  const openLoads = new Map();
  for (const event of events) {
    if (event.ev === 'doc_load') {
      const load = { wv: event.wv, why: event.why, role: event.role, chapter: event.chapter, t: number(event.t) };
      openLoads.set(event.wv, load);
      loads.push(load);
      continue;
    }
    const load = openLoads.get(event.wv);
    const stage = stageOf[event.ev];
    if (!load || !stage || load[stage] !== undefined) continue;
    load[stage] = number(event.t) - load.t;
    if (event.ev === 'js_ready') load.timing = event.timing;
  }
  const byPhase = {};
  for (const swipe of swipes) (byPhase[phase(swipe)] ||= []).push(swipe);
  const stageEnd = load => load.slotReady ?? load.prepared ?? load.stable;
  return {
    swipes: {
      count: swipes.length,
      stalled: swipes.filter(stalled).length,
      notReadyAtIntent: swipes.filter(swipe => swipe.ready === '0').length,
      waitMs: stats(swipes.map(swipe => number(swipe.waitMs))),
      byPhase: Object.fromEntries(Object.entries(byPhase).map(([name, items]) => [name, {
        count: items.length, stalled: items.filter(stalled).length,
        waitMs: stats(items.map(item => number(item.waitMs)))
      }])),
      byPath: countBy(swipes, 'path'),
      byState: countBy(swipes, 'state'),
    },
    taps: { count: of('tap').length, notReady: of('tap').filter(tap => tap.ready === '0').length },
    turns: { started: countBy(of('turn_start'), 'src'), crossStarted: of('turn_start').filter(turn => turn.cross === '1').length,
      ended: countBy(of('turn_end'), 'out') },
    commits: { via: countBy(of('commit'), 'via'), handoffMs: stats(of('commit').map(commit => number(commit.handoffMs))),
      pageCommits: of('page_commit').length, crossCommits: of('page_commit').filter(commit => commit.cross === '1').length },
    resourcesByType: Object.fromEntries(Object.entries(of('resource').reduce((groups, entry) => {
      (groups[entry.ext] ||= []).push(entry);
      return groups;
    }, {})).map(([ext, items]) => [ext, { count: items.length, ms: stats(items.map(item => number(item.ms))),
      maxBytes: Math.max(...items.map(item => number(item.bytes) || 0)) }])),
    queue: { queued: countBy(of('queue'), 'src'), started: of('queue_start').length, rebased: of('queue_rebase').length,
      queuedMs: stats(of('queue_start').map(entry => number(entry.queuedMs))), dropped: countBy(of('queue_drop'), 'why') },
    documentLoads: {
      count: loads.length,
      byReason: countBy(loads, 'why'),
      stagesMs: Object.fromEntries(Object.values(stageOf).map(stage => [stage, stats(loads.map(load => load[stage]))])),
    },
    slotReadyMs: stats(of('slot_ready').map(slot => number(slot.prepMs))),
    invalidations: countBy(of('invalidate'), 'why'),
    preloadResets: of('preload_reset').length,
    inputCancels: countBy(of('input_cancel'), 'why'),
    reconfigures: of('reconfigure').map(event => `layoutChanged=${event.layoutChanged} ${event.diff}`),
    ignoredPageMessages: of('page_msg_ignored').length,
    staleUpdatesSkipped: of('stale_update').length,
    busyPressesResumedAsDrag: of('busy_resume').length,
    turnFrameGaps: {
      maxGapMs: stats(of('turn_end').filter(event => event.out === 'flip').map(event => number(event.maxGapMs))),
      turnsWithGapOver50ms: of('turn_end').filter(event => event.out === 'flip' && number(event.maxGapMs) > 50).length,
    },
    fallbacks: { tapJs: of('tap_fallback_js').length, apiJs: of('api_fallback_js').length,
      chapterTurn: of('chapter_turn_fallback').length, curlDirect: of('curl_fallback').length },
    legacyPreloads: { count: of('legacy_preload').length, ms: stats(of('legacy_preload').map(event => number(event.ms))) },
    warmPages: { created: of('warm_create').length, adopted: of('warm_adopt').length, discarded: of('warm_discard').length },
    slowestLoads: loads.filter(load => Number.isFinite(stageEnd(load)))
      .sort((a, b) => stageEnd(b) - stageEnd(a)).slice(0, 5)
      .map(load => `wv${load.wv} ${load.why} ch${load.chapter} finished=${load.finished} ready=${load.ready} ` +
        `stable=${load.stable} slot=${load.slotReady} ${load.timing || ''}`),
    worstSwipes: swipes.filter(stalled)
      .sort((a, b) => (number(b.waitMs) || number(b.heldMs)) - (number(a.waitMs) || number(a.heldMs))).slice(0, 8)
      .map(swipe => `${phase(swipe)} ${swipe.from}->${swipe.to} state=${swipe.state} ready=${swipe.ready} ` +
        `path=${swipe.path} moved=${swipe.moved} wait=${swipe.waitMs ?? '-'} held=${swipe.heldMs}`),
  };
}

function deviceTime() {
  return adbSync(['shell', 'date', '+%m-%d_%H:%M:%S.000']).trim().replace('_', ' ');
}

// Stream instead of dumping once: a long run can outgrow the device's logcat ring buffer.
function record(since) {
  const child = spawn(adb, ['logcat', '-v', 'threadtime', '-T', since, '-s', ...tags], { windowsHide: true });
  let text = '';
  child.stdout.setEncoding('utf8');
  child.stdout.on('data', chunk => { text += chunk; });
  return { text: () => text, stop: () => { child.kill(); return text; } };
}

async function main() {
  let text;
  let label;
  let leftForeground = false;
  if (options.log) {
    text = readFileSync(options.log, 'utf8');
    label = 'from-log';
  } else {
    const recording = record(deviceTime());
    if (options.capture) {
      label = 'capture';
      console.log(`Recording ${options.capture}s. Flip pages in the debug reader now.`);
      await wait(Number(options.capture) * 1000);
    } else {
      const turns = Number(options.turns || 80);
      const interval = Number(options.interval || 250);
      const transition = options.transition || 'slide';
      const input = options.input || 'swipe';
      const longPress = options['long-press'] || 'word';
      // A library book by title (percent-encoded so adb shell keeps it intact); "user" reads saved settings.
      const book = options.book ? ['--es', 'book_title', encodeURIComponent(options.book)] : [];
      label = `${options.book ? 'book-' : ''}${transition}-${input}-${interval}ms-${longPress}`;
      adbSync(['shell', 'am', 'start', '--activity-clear-top', '-n',
        'com.huangder.lumibooks/.ui.reader.EpubPerformanceActivity',
        '--es', 'engine', 'epub', '--es', 'transition', transition, '--es', 'fixture', options.fixture || 'text',
        '--ei', 'turns', String(turns), '--el', 'interval_ms', String(interval), '--es', 'input', input,
        '--es', 'long_press', longPress, ...book]);
      const deadline = Date.now() + 45000 + turns * (interval + 250);
      // Animations stop while the fixture is in the background; such a run only measures the pause.
      const foreground = () =>
        /EpubPerformanceActivity/.test(adbSync(['shell', 'dumpsys activity activities | grep -m1 ResumedActivity; true'])) &&
        !/isKeyguardShowing=true/.test(adbSync(['shell', 'dumpsys window | grep -m1 isKeyguardShowing; true']));
      while (Date.now() < deadline && !/DONE engine=epub|BOOK_MISSING|BOOK_UNSUPPORTED/.test(recording.text())) {
        if (/START engine=epub/.test(recording.text()) && !foreground()) {
          leftForeground = true;
          break;
        }
        await wait(1000);
      }
    }
    text = recording.stop();
  }
  const directory = join(__dirname, '../app/build/epub-performance/cross-chapter');
  mkdirSync(directory, { recursive: true });
  const complete = options.log || options.capture ? null : /DONE engine=epub/.test(text);
  const line = pattern => (text.match(pattern) || [''])[0].replace(/^.*EpubPerfFixture: /, '');
  const summary = { run: { complete, leftForeground, valid: complete !== false && !leftForeground,
    start: line(/[^\n]*START engine=[^\n]*/), book: line(/[^\n]*(BOOK|BOOK_MISSING|BOOK_UNSUPPORTED) [^\n]*/) },
    ...summarize(parse(text)) };
  writeFileSync(join(directory, label + '.log'), text);
  writeFileSync(join(directory, label + '.json'), JSON.stringify(summary, null, 2));
  console.log(JSON.stringify(summary, null, 2));
}

if (require.main === module) main().catch(error => { console.error(error); process.exitCode = 1; });
module.exports = { parse, summarize, phase };
