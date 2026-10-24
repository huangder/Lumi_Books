// Exercise the production selection handler without installing a test APK.
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const start = source.indexOf("  document.addEventListener('selectionchange'");
const handlerSource = source.slice(start, source.indexOf('  function scheduleRepagination()', start));

for (const scope of ['paragraph', 'line', 'sentence', 'character', 'word']) {
  let handler;
  const calls = [];
  const textNode = {};
  const selection = {
    isCollapsed: false, rangeCount: 1,
    getRangeAt: () => ({ cloneRange: () => ({}) }),
    collapse: (node, offset) => calls.push(['collapse', node, offset]),
    modify: (...args) => calls.push(args),
  };
  const ctx = vm.createContext({
    state: { longPressSelectionRange: scope },
    touchStartTime: 1000, touchStartX: 10, touchStartY: 20,
    initialSelectionRangeApplied: false, selectionDispatchTimer: 0,
    readerOwnsSelectionHandles: false,
    Date: { now: () => 1600 }, clearTimeout() {}, setTimeout: () => 1,
    window: { getSelection: () => selection },
    document: {
      body: { contains: () => true },
      caretRangeFromPoint: () => ({ startContainer: textNode, startOffset: 4 }),
      addEventListener: (_, fn) => { handler = fn; },
    },
  });
  vm.runInContext(handlerSource, ctx);
  handler();
  assert.equal(ctx.readerOwnsSelectionHandles, scope !== 'word',
    'script-adjusted selections need replacement touch handles');
  if (scope === 'word') assert.equal(calls.length, 0);
  else if (scope === 'character') assert.deepEqual(calls[1], ['extend', 'forward', 'character']);
  else assert.deepEqual(calls.slice(1), [
    ['move', 'backward', scope + 'boundary'], ['extend', 'forward', scope + 'boundary'],
  ]);
  const count = calls.length;
  handler();
  assert.equal(calls.length, count, 'a later handle drag must preserve its manually chosen range');
}
console.log('EPUB initial selection scopes and subsequent manual adjustment passed.');

// Dragging must use the selected text's real DOM endpoints, including soft wraps.
{
  const functionStart = source.indexOf('  function selectionHandleAnchors(');
  const functions = source.slice(functionStart, source.indexOf('  function scheduleRepagination()', functionStart));
  let selectedStart = 10, selectedEnd = 20, caretOffset = 15;
  const node = { nodeType: 3, parentElement: {} };
  const range = (start, end) => ({
    startContainer: node, startOffset: start, endContainer: node, endOffset: end,
    getClientRects: () => [{left: start * 2, right: end * 2, top: 30, bottom: 50}],
  });
  const ctx = vm.createContext({
    readerOwnsSelectionHandles: true,
    window: {
      getComputedStyle: () => ({direction:'ltr'}),
      getSelection: () => ({isCollapsed:false, rangeCount:1,
        getRangeAt: () => range(selectedStart, selectedEnd),
        setBaseAndExtent: (_, start, __, end) => { selectedStart = start; selectedEnd = end; },
      }),
    },
    document: {body:{contains:()=>true}, caretRangeFromPoint:()=>range(caretOffset, caretOffset)},
    textIndex: () => ({text:'x'.repeat(50)}),
    textOffsetForBoundary: (_, __, offset) => offset,
    rangeAtOffsets: (_, start, end) => range(start, end),
  });
  vm.runInContext(functions, ctx);
  const anchors = ctx.selectionHandleAnchors({}, 10, 20);
  assert.equal(anchors.start.x, 20);
  assert.equal(anchors.end.x, 40);
  assert.equal(ctx.moveSelectionHandle(true, 10, 40), true);
  assert.equal(selectedStart, 15);
  caretOffset = 30;
  ctx.moveSelectionHandle(false, 60, 40);
  assert.equal(selectedEnd, 30);
  caretOffset = 0;
  ctx.moveSelectionHandle(false, 0, 40);
  assert.equal(selectedEnd, selectedStart + 1, 'dragging cannot collapse the selection');
  ctx.readerOwnsSelectionHandles = false;
  assert.equal(ctx.moveSelectionHandle(true, 1, 1), false);
}
console.log('WebView replacement handle endpoints and manual dragging passed.');
