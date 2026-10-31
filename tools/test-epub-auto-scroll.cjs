// Exercise the production scroll metrics and animation loop with a deterministic frame clock.
// Run: node tools/test-epub-auto-scroll.cjs
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { runInNewContext } = require('node:vm');

const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
function productionFunction(name, nextName) {
  const start = source.indexOf(`  function ${name}(`);
  const end = source.indexOf(`  function ${nextName}(`, start);
  assert.ok(start >= 0 && end > start, `Missing production function ${name}`);
  return source.slice(start, end);
}

function reader({ height = 600, scrollHeight = 2000, innerHeight = height, y = 0 } = {}) {
  const root = { clientHeight: height, scrollHeight, scrollTop: y };
  const state = { flow: 'scrolled', fixed: false, ready: true, paginating: false,
    viewportHeight: height, autoScrollSpeed: 0, autoScrollFrame: 0, autoScrollLastTime: 0 };
  const messages = [];
  const frames = new Map();
  let frameId = 0;
  let time = 0;
  const window = { innerHeight, scrollY: y, devicePixelRatio: 2,
    scrollBy(_x, distance) {
      root.scrollTop = Math.max(0, Math.min(root.scrollHeight - Math.max(height, innerHeight),
        root.scrollTop + distance));
      window.scrollY = root.scrollTop;
    }
  };
  const context = { state, window, document: { scrollingElement: root,
    documentElement: root, body: { scrollHeight } },
    requestAnimationFrame(callback) { frames.set(++frameId, callback); return frameId; },
    cancelAnimationFrame(id) { frames.delete(id); },
    post(type, payload) { messages.push({ type, payload }); }
  };
  runInNewContext(productionFunction('scrollMetrics', 'applyScrollability') +
    productionFunction('setAutoScrollSpeed', 'pageFromLocator'), context);
  return { state, root, window, messages, setSpeed: context.setAutoScrollSpeed,
    frame() {
      time += 100;
      const callbacks = [...frames.values()];
      frames.clear();
      callbacks.forEach(callback => callback(time));
    },
    finish() {
      for (let count = 0; frames.size && count < 10000; count++) this.frame();
      assert.equal(frames.size, 0, 'Animation must finish');
    }
  };
}

// The old check added innerHeight to scrollY, stopping a full viewport too early.
for (const options of [
  { scrollHeight: 2000, y: 810 },
  { scrollHeight: 850, y: 0 }, // Shorter than two screens: used to stop immediately.
  { scrollHeight: 2000, innerHeight: 750, y: 510 } // Resized WebView layout viewport.
]) {
  const book = reader(options);
  book.setSpeed(320);
  book.frame();
  assert.equal(book.messages.length, 0, 'Unread final screen must keep scrolling');
  book.finish();
  const end = options.scrollHeight - Math.max(600, options.innerHeight || 600);
  assert.ok(book.root.scrollTop >= end - 1, 'Last content must reach the viewport');
  assert.equal(book.messages.filter(message => message.type === 'autoScrollEnd').length, 1);
  assert.equal(book.state.autoScrollSpeed, 0);
}

for (const field of ['ready', 'paginating']) {
  const book = reader({ scrollHeight: 600 });
  book.state[field] = field === 'paginating';
  book.setSpeed(320);
  for (let i = 0; i < 5; i++) book.frame();
  assert.equal(book.messages.length, 0, 'Unfinished layout must not report the end');
  book.root.scrollHeight = 1800;
  book.state[field] = field === 'ready';
  book.frame();
  assert.equal(book.root.scrollTop, 0, 'Layout wait must reset the frame clock');
  book.finish();
  assert.ok(book.root.scrollTop >= 1199);
  assert.equal(book.messages.length, 1);
}

const paused = reader();
paused.setSpeed(320);
paused.frame();
paused.frame();
paused.setSpeed(0);
const pausePosition = paused.root.scrollTop;
paused.frame();
assert.equal(paused.root.scrollTop, pausePosition);
assert.equal(paused.messages.length, 0, 'Pause must not report the end');
paused.setSpeed(640);
paused.finish();
assert.equal(paused.messages.length, 1);

for (const scrollHeight of [500, 600]) {
  const short = reader({ scrollHeight });
  short.setSpeed(32);
  short.frame();
  assert.equal(short.messages.length, 1, 'A settled single-screen chapter is already at its end');
}
console.log('PASS: EPUB auto-scroll final screen, short chapters, resized viewport, layout wait and pause/resume.');
