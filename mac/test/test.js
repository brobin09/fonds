'use strict';
const assert = require('assert'); const fs = require('fs'); const os = require('os'); const path = require('path'); const cp = require('child_process');
const lib = require('../lib');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'fonds-test-'));
const zip = path.join(tmp, 'sample.zip');
cp.execFileSync('python3', [path.join(__dirname, 'make-sample.py'), zip]);
const L = new lib.Library(path.join(tmp, 'lib'));
const r = L.importZip(zip);
assert(!r.replaced && r.manifest.items === 2 && r.manifest.pages === 3);
let { data } = L.load(r.manifest.id);
assert(data.folders[0].items[0].pages[0].imagePath.endsWith('item-001-p01.jpg'));
// edit + draft accept
({ data } = L.saveEdit(r.manifest.id, { scope: 'item', key: '1/1/2', fields: { title: 'Sermon note' } }));
assert.equal(data.folders[0].items[1].title, 'Sermon note');
assert(!('title' in data.folders[0].items[1].pendingDrafts));
({ data } = L.saveEdit(r.manifest.id, { scope: 'collection', fields: { abstract: 'Edited.' } }));
// re-import keeps edits, same id
const r2 = L.importZip(zip);
assert(r2.replaced && r2.keptEdits && r2.manifest.id === r.manifest.id);
data = L.load(r.manifest.id).data;
assert.equal(data.collection.abstract, 'Edited.');
assert.equal(L.list().length, 1);
// generators
const html = lib.findingAidHtml(data); assert(html.includes('Sample Papers') && html.includes('Series I. Correspondence') && html.includes('Durham'));
const x = lib.ead(data); assert(x.includes('<c level="item">') && x.includes('<container type="box">1</container>'));
assert(lib.itemCsv(data).split('\n').length >= 3);
assert(lib.containerCsv(data).includes('I. Correspondence'));
const c = lib.citation(data, data.folders[0], data.folders[0].items[0]);
assert(c.includes('Jane Doe to Mary Doe') && c.includes('May 3, 1921') && c.includes('box 1, folder 1'), c);
assert.equal(lib.search(data, 'train late').length, 1);
assert.equal(lib.search(data, 'zzz').length, 0);
// image export
const dest = path.join(tmp, 'imgs');
assert.equal(L.exportImages(r.manifest.id, dest), 3);
assert(fs.existsSync(path.join(dest, 'box-01/folder-001/item-001-p02.jpg')));
const dest2 = path.join(tmp, 'imgs2');
assert.equal(L.exportImages(r.manifest.id, dest2, { box: 1, folder: 1, seq: 1 }), 2);
assert(fs.existsSync(path.join(dest2, 'item-001-p01.jpg')));
// zip-slip guard
const AdmZip = require('adm-zip'); const z = new AdmZip(zip); z.addFile('../../evil.txt', Buffer.from('x')); const bad = path.join(tmp, 'bad.zip'); z.writeZip(bad);
L.importZip(bad); assert(!fs.existsSync(path.join(tmp, 'evil.txt')) && !fs.existsSync(path.join(tmp, 'lib', 'evil.txt')));
// not-a-fonds zip
const z2 = new AdmZip(); z2.addFile('foo.txt', Buffer.from('x')); z2.writeZip(path.join(tmp, 'no.zip'));
assert.throws(() => L.importZip(path.join(tmp, 'no.zip')), /data.json/);
fs.writeFileSync(path.join(tmp, 'sample-path.txt'), zip);
console.log('lib tests passed', tmp);
