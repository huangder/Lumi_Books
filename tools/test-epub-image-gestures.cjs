// Executes the production bridge handlers without requiring a device/test APK.
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const section = (start, end) => source.slice(source.indexOf(start), source.indexOf(end, source.indexOf(start)));
const handlers = {}, messages = [], timers = new Map();
let now = 1000, timerId = 0;
const ctx = vm.createContext({
  URL, encodeURIComponent, Date: { now: () => now }, Math, String, Number,
  document: {
    baseURI: 'https://book/OPS/chapter.xhtml', body: {}, documentElement: {},
    getElementById: () => null, elementsFromPoint: () => [],
    addEventListener: (name, handler) => handlers[name] = handler,
  },
  window: { devicePixelRatio: 1, getSelection: () => ({ isCollapsed: true, rangeCount: 0 }) },
  setTimeout: fn => { timers.set(++timerId, fn); return timerId; },
  clearTimeout: id => timers.delete(id),
  post: (type, payload) => messages.push({ type, payload }),
  viewportWidth: () => 1000, viewportHeight: () => 1000,
  isFootnoteReference: anchor => !!anchor.footnote,
  showFootnotePopover: () => Promise.resolve(true),
  pageX: () => 0,
});
vm.runInContext(`
  var state = { flow: 'paginated', fixed: false, nativePaging: true,
    page: 0, total: 3, transition: 'none', suppressClickUntil: 0 };
  var imageLongPressTimer = 0, imageLongPressTarget = null, imageLongPressTriggered = false;
  var touchPaging = false, pageStageActive = false, pageStageDurationOverride = 0;
  var scrollChapterDragDirection = 0;
  var touchStartX = 0, touchStartY = 0, touchLastX = 0, touchLastTime = 0,
    touchStartTime = 0, touchBaseX = 0, touchVelocityX = 0;
  function clearDocumentSelection() {}
`, ctx);
vm.runInContext(section('  function interactiveFromTarget(', '  function semanticTokens('), ctx);
vm.runInContext(section("  document.addEventListener('touchstart'", "  document.addEventListener('touchmove'"), ctx);
vm.runInContext(section("  document.addEventListener('touchmove'", "  document.addEventListener('touchend'"), ctx);
vm.runInContext(section("  document.addEventListener('touchend'", "  document.addEventListener('touchcancel'"), ctx);
vm.runInContext(section("  document.addEventListener('touchcancel'", "  document.addEventListener('submit'"), ctx);
vm.runInContext(section("  document.addEventListener('contextmenu'", "  document.addEventListener('selectionchange'"), ctx);

function image({ linked = false, svg = false, footnote = false, cover = false } = {}) {
  const anchor = linked ? { href: 'https://book/OPS/target.xhtml', footnote } : null;
  const element = {
    localName: svg ? 'image' : 'img', src: svg ? undefined : 'https://book/OPS/picture.jpg',
    naturalWidth: 800, naturalHeight: 1200,
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 1000, bottom: 1000 }),
    getAttribute: name => name === 'data-lumi-cover-media' && cover ? 'true' :
      name === 'href' && svg ? '../images/picture.jpg' : null,
    hasAttribute: () => false,
    closest: selector => {
      if (selector === 'img,image,svg') return element;
      if (selector.includes('a[href]')) return anchor;
      return null;
    },
  };
  return element;
}
function event(target, x, y = 400) {
  return { target, touches: [{ clientX: x, clientY: y }],
    changedTouches: [{ clientX: x, clientY: y }], clientX: x, clientY: y,
    preventDefault() {}, stopPropagation() {} };
}
function tap(target, x, endX = x, duration = 100) {
  messages.length = 0; now += 1000;
  handlers.touchstart(event(target, x)); now += duration;
  handlers.touchend(event(target, endX));
}
const plain = image();
for (const [x, zone] of [[100, 'left'], [500, 'center'], [900, 'right']]) {
  tap(plain, x);
  assert.deepEqual(messages, [{ type: 'tap', payload: messages[0].payload }]);
  assert.equal(messages[0].payload.zone, zone);
  handlers.click(event(plain, x));
  assert.equal(messages.length, 1, 'synthetic click must not turn twice');
}
tap(plain, 900, 800);
assert.equal(messages.length, 0, 'drag is not a tap');
tap(plain, 900, 900, 600);
assert.equal(messages.length, 0, 'long hold is not a tap');
const linked = image({ linked: true });
tap(linked, 900);
assert.equal(messages.length, 0);
handlers.click(event(linked, 900));
assert.equal(messages[0].type, 'link');
for (const target of [plain, linked, image({ svg: true })]) {
  messages.length = 0; now += 1000;
  handlers.touchstart(event(target, 500));
  for (const fn of [...timers.values()]) fn();
  timers.clear(); now += 550;
  handlers.touchend(event(target, 500));
  assert.equal(messages.length, 1);
  assert.equal(messages[0].type, 'image');
  assert.ok(messages[0].payload.source.endsWith('picture.jpg'));
  handlers.click(event(target, 500));
  assert.equal(messages.length, 1, 'long press must suppress click/link');
}
for (const kind of ['touchmove', 'touchcancel']) {
  messages.length = 0; now += 1000;
  handlers.touchstart(event(plain, 500));
  handlers[kind](event(plain, 530));
  assert.equal(timers.size, 0, `${kind} cancels pending image preview`);
}
const footnote = image({ linked: true, footnote: true });
tap(footnote, 900);
assert.equal(timers.size, 0, 'footnote marker does not arm image long press');
assert.equal(messages.length, 0, 'footnote tap does not turn page');
const cover = image({ linked: true, cover: true });
tap(cover, 900);
assert.equal(messages[0].payload.zone, 'right');
const overlay = { closest: () => null, matches: () => false };
ctx.document.elementsFromPoint = () => [overlay, plain];
messages.length = 0;
handlers.contextmenu(event(overlay, 500));
// Previous long press is reset by a new touch stream.
handlers.touchstart(event(overlay, 500));
handlers.contextmenu(event(overlay, 500));
assert.equal(messages.at(-1).type, 'image');
assert.equal(messages.at(-1).payload.source, plain.src);
console.log('EPUB image gestures: edge/center taps, no double dispatch, drag/hold, links, long press, SVG and overlay passed.');
