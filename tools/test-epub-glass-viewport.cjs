// Exercises the actual reader viewport functions against another window's keyboard.
// No APK, device, library data or dependencies are required: node --test this-file.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');

const source = readFileSync(join(__dirname,
  '../app/src/main/java/com/huangder/lumibooks/util/epub/EpubDocumentTransformer.kt'), 'utf8');
const start = source.indexOf('  function viewportWidth()');
const end = source.indexOf('  function updateReadingAxis()', start);
const freeze = source.match(/setViewportFrozen: function \(frozen\) \{([\s\S]*?)\n    \},/);
assert.ok(start >= 0 && end > start && freeze, 'Reader viewport API must be present');

function fixture() {
  const repaginations = [];
  const environment = {
    viewportFrozen: false, resizeTimer: 7,
    state: { viewportWidth: 850, viewportHeight: 820, stableLayoutRevision: 1 },
    window: { visualViewport: { width: 850, height: 820 }, outerWidth: 850, outerHeight: 820 },
    document: { documentElement: { clientWidth: 850, clientHeight: 820 } },
    clearTimeout: () => {},
    scheduleRepagination: cause => repaginations.push(cause),
  };
  vm.createContext(environment);
  vm.runInContext(source.slice(start, end) +
    '\nfunction setViewportFrozen(frozen) {' + freeze[1] + '\n}', environment);
  return { environment, repaginations };
}

test('a note keyboard does not change the page dimensions or trigger repagination on close', () => {
  const { environment: e, repaginations } = fixture();
  e.setViewportFrozen(true);
  e.window.visualViewport.height = 480;
  assert.equal(e.viewportWidth(), 850);
  assert.equal(e.viewportHeight(), 820);
  e.window.visualViewport.height = 820;
  e.setViewportFrozen(false);
  assert.deepEqual(repaginations, []);
});

test('a genuine window rotation is applied after the independent window closes', () => {
  const { environment: e, repaginations } = fixture();
  e.setViewportFrozen(true);
  e.window.visualViewport.width = 820;
  e.window.visualViewport.height = 850;
  assert.equal(e.viewportWidth(), 850);
  assert.equal(e.viewportHeight(), 820);
  e.setViewportFrozen(false);
  assert.equal(e.viewportWidth(), 820);
  assert.equal(e.viewportHeight(), 850);
  assert.deepEqual(repaginations, ['viewport-resume']);
});

test('an uninitialized page can still obtain its first viewport while unfocused', () => {
  const { environment: e } = fixture();
  e.state.viewportWidth = 0;
  e.state.viewportHeight = 0;
  e.setViewportFrozen(true);
  assert.equal(e.viewportWidth(), 850);
  assert.equal(e.viewportHeight(), 820);
});
