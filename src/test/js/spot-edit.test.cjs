const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const html = readFileSync('src/main/resources/META-INF/resources/index.html', 'utf8');
function source(name) {
  const match = html.match(new RegExp(`  (?:async )?function ${name}\\([^]*?\\n  }`));
  assert.ok(match, `Function ${name} exists`);
  return match[0];
}

test('closing pending review then editing a saved spot sends PUT to the saved spot', async () => {
  const fields = new Map();
  const element = id => {
    if (!fields.has(id)) fields.set(id, { value: '', hidden: false, classList: { add() {}, remove() {} } });
    return fields.get(id);
  };
  const calls = [];
  const context = vm.createContext({
    document: { getElementById: element, querySelectorAll: () => [{ value: 'W', checked: true }] },
    allSpots: [{ spot: { id: 'existing', name: 'Existing beach', latitude: 58.5, longitude: 17.5,
      type: 'FLAT_WATER', difficulty: 'INTERMEDIATE', minWindSpeed: 5, idealWindSpeed: 8,
      maxWindSpeed: 15, bestDirections: ['W'] } }],
    map: { closePopup() {}, getCenter: () => ({ lat: 58, lng: 17 }) },
    addMode: false, clearAddMarker() {}, setStatus() {}, loadSpots: async () => {}, renderPending() {},
    alert(message) { throw new Error(message); },
    authFetch: async (url, request) => { calls.push({ url, ...request }); return { ok: true }; }
  });
  vm.runInContext(['closeModal', 'openEdit', 'saveSpot'].map(source).join('\n'), context);
  element('edit-pending-id').value = 'pending-previous';
  element('pending-save-note').hidden = false;
  element('btn-discard-pending').hidden = false;
  vm.runInContext("closeModal(); openEdit('existing');", context);
  await vm.runInContext('saveSpot()', context);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/spots/existing');
  assert.equal(calls[0].method, 'PUT');
  assert.equal(element('edit-pending-id').value, '');
  assert.equal(element('pending-save-note').hidden, true);
  assert.equal(element('btn-discard-pending').hidden, true);
});
