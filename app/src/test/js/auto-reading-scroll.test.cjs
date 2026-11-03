// Run with: node --test app/src/test/js/auto-reading-scroll.test.cjs
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');

function reader() {
  const source = readFileSync(resolve(__dirname, '../../main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
  const start = source.indexOf('  var autoReadingTouchDown = false;');
  const end = source.indexOf('  function pageFromLocator(', start);
  assert.ok(start >= 0 && end > start);
  let now = 1000, frame, selected = false;
  const events = {};
  const state = { flow: 'scrolled', fixed: false, ready: true, autoScrollSpeed: 0 };
  const window = { scrollY: 100, getSelection: () => ({ isCollapsed: !selected }),
    scrollBy: (_, y) => { window.scrollY += y; } };
  const context = { state, window, Date: { now: () => now },
    document: { addEventListener: (name, callback) => { events[name] = callback; }, getElementById: () => null },
    requestAnimationFrame: callback => { frame = callback; return 1; },
    cancelAnimationFrame: () => { frame = null; },
    scrollMetrics: () => ({ nonScrollable: false, scrollY: window.scrollY, maxScrollY: 10000 }),
    post: () => {} };
  vm.createContext(context);
  vm.runInContext(source.slice(start, end), context);
  return { state, window, speed: value => context.setAutoScrollSpeed(value),
    touch: () => events.touchstart({}), release: () => events.touchend({ touches: [] }),
    select: value => { selected = value; },
    tick: time => { now = time; const callback = frame; frame = null; if (callback) callback(time); } };
}

test('touch and fling yield then resume at the original speed without a position jump', () => {
  const r = reader();
  r.speed(32); r.tick(1000); r.tick(1100);
  assert.ok(r.window.scrollY > 100);
  r.touch(); r.tick(1200);
  r.window.scrollY = 50; r.release(); r.tick(1300);
  r.window.scrollY = 40; r.tick(1400);
  r.tick(2000); assert.equal(r.window.scrollY, 40);
  r.tick(2100); assert.equal(r.window.scrollY, 40);
  r.tick(2200); assert.ok(r.window.scrollY > 40);
  assert.equal(r.state.autoScrollSpeed, 32);
});

test('explicit pause remains stopped after touch and waiting', () => {
  const r = reader();
  r.speed(32); r.tick(1000); r.speed(0);
  r.touch(); r.release(); r.tick(5000); r.tick(6000);
  assert.equal(r.state.autoScrollSpeed, 0);
  assert.equal(r.window.scrollY, 100);
});

test('selection suspends motion and clearing it resumes without catching up', () => {
  const r = reader();
  r.speed(32); r.tick(1000); r.select(true); r.tick(2000);
  assert.equal(r.window.scrollY, 100);
  r.select(false); r.tick(3000); assert.equal(r.window.scrollY, 100);
  r.tick(3100); assert.ok(r.window.scrollY > 100);
});
