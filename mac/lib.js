'use strict';
// Core logic for Fonds for Mac: library storage, package import, finding-aid generators.
// No Electron imports here so it can be tested with plain node.
const fs = require('fs');
const path = require('path');
const AdmZip = require('adm-zip');

const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const pad = (n, w) => String(n).padStart(w, '0');

// ---------------------------------------------------------------- library

class Library {
  constructor(dir) {
    this.dir = dir;
    fs.mkdirSync(dir, { recursive: true });
  }
  p(id, ...rest) { return path.join(this.dir, id, ...rest); }

  list() {
    const out = [];
    for (const id of fs.readdirSync(this.dir)) {
      const m = this.p(id, 'manifest.json');
      if (fs.existsSync(m)) {
        try { out.push(JSON.parse(fs.readFileSync(m, 'utf8'))); } catch (_) { /* skip */ }
      }
    }
    return out.sort((a, b) => (b.imported || '').localeCompare(a.imported || ''));
  }

  // Extract a Fonds ZIP package into the library. Returns the manifest.
  // `replace` = id of an existing entry to overwrite (keeps local edits unless discardEdits).
  importZip(zipPath, opts = {}) {
    const zip = new AdmZip(zipPath);
    const entry = zip.getEntry('data.json');
    if (!entry) throw new Error('This ZIP has no data.json. Export it from Fonds using "Complete package (ZIP)".');
    const data = JSON.parse(entry.getData().toString('utf8'));
    if (!data.format || !String(data.format).startsWith('fonds-')) throw new Error('data.json is not a Fonds export.');

    const kind = (data.collection && data.collection.title) || 'Untitled';
    const ident = (data.collection && data.collection.identifier) || '';
    const key = slug(`${kind}-${ident}`) || 'collection';
    const existing = this.list().find((m) => m.key === key);
    const id = existing ? existing.id : `${key}-${Date.now().toString(36)}`;
    const root = this.p(id);
    const edited = existing && fs.existsSync(this.p(id, 'edits.json')) && !opts.discardEdits;

    // wipe old package files except edits
    if (existing) {
      for (const f of fs.readdirSync(root)) {
        if (f === 'edits.json') continue;
        fs.rmSync(path.join(root, f), { recursive: true, force: true });
      }
    }
    fs.mkdirSync(root, { recursive: true });
    for (const e of zip.getEntries()) {
      if (e.isDirectory) continue;
      const target = path.resolve(root, e.entryName);
      if (!target.startsWith(path.resolve(root) + path.sep)) continue; // zip-slip guard
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.writeFileSync(target, e.getData());
    }
    if (existing && opts.discardEdits) fs.rmSync(this.p(id, 'edits.json'), { force: true });

    const manifest = makeManifest(id, key, data, existing, edited);
    fs.writeFileSync(this.p(id, 'manifest.json'), JSON.stringify(manifest, null, 2));
    return { manifest, replaced: !!existing, keptEdits: !!edited };
  }

  // Package data merged with local edits; image paths made absolute.
  load(id) {
    const base = JSON.parse(fs.readFileSync(this.p(id, 'data.json'), 'utf8'));
    let edits = {};
    try { edits = JSON.parse(fs.readFileSync(this.p(id, 'edits.json'), 'utf8')); } catch (_) { /* none */ }
    const data = applyEdits(base, edits);
    for (const f of data.folders) for (const it of f.items) for (const pg of it.pages) {
      pg.imagePath = pg.image && fs.existsSync(this.p(id, pg.image)) ? this.p(id, pg.image) : '';
    }
    return { data, edits };
  }

  saveEdit(id, edit) {
    const file = this.p(id, 'edits.json');
    let edits = {};
    try { edits = JSON.parse(fs.readFileSync(file, 'utf8')); } catch (_) { /* none */ }
    edits.collection = edits.collection || {};
    edits.folders = edits.folders || {};
    edits.items = edits.items || {};
    if (edit.scope === 'collection') Object.assign(edits.collection, edit.fields);
    else if (edit.scope === 'folder') edits.folders[edit.key] = Object.assign(edits.folders[edit.key] || {}, edit.fields);
    else if (edit.scope === 'item') edits.items[edit.key] = Object.assign(edits.items[edit.key] || {}, edit.fields);
    fs.writeFileSync(file, JSON.stringify(edits, null, 2));
    const mp = this.p(id, 'manifest.json');
    const m = JSON.parse(fs.readFileSync(mp, 'utf8'));
    m.edited = true;
    fs.writeFileSync(mp, JSON.stringify(m, null, 2));
    return this.load(id);
  }

  remove(id) { fs.rmSync(this.p(id), { recursive: true, force: true }); }
}

function slug(s) { return String(s).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 60); }

function makeManifest(id, key, data, existing, edited) {
  const items = data.folders.reduce((n, f) => n + f.items.length, 0);
  const pages = data.folders.reduce((n, f) => n + f.items.reduce((m, i) => m + i.pages.length, 0), 0);
  return {
    id, key,
    title: data.collection.title || 'Untitled',
    creator: data.collection.creator || '',
    dates: data.collection.dates || '',
    extent: data.collection.extent || '',
    exported: data.exported || '',
    imported: new Date().toISOString(),
    folders: data.folders.length, items, pages,
    edited: !!(existing && existing.edited && edited),
  };
}

const folderKey = (f) => `${f.box}/${f.folder}`;
const itemKey = (f, it) => `${f.box}/${f.folder}/${it.sequence}`;

function applyEdits(base, edits) {
  const data = JSON.parse(JSON.stringify(base));
  Object.assign(data.collection, (edits && edits.collection) || {});
  for (const f of data.folders) {
    Object.assign(f, (edits && edits.folders && edits.folders[folderKey(f)]) || {});
    for (const it of f.items) {
      const e = edits && edits.items && edits.items[itemKey(f, it)];
      if (!e) continue;
      Object.assign(it, e);
      // accepted drafts disappear from pending
      for (const k of Object.keys(e)) if (it.pendingDrafts) delete it.pendingDrafts[k];
    }
  }
  return data;
}

// ---------------------------------------------------------------- helpers shared with generators

function groups(data) {
  const byId = new Map(data.series.map((s) => [s.id, s]));
  const out = [];
  const used = new Map();
  for (const f of data.folders) {
    const s = f.seriesId != null ? byId.get(f.seriesId) : null;
    const k = s ? s.id : 'none';
    if (!used.has(k)) { const g = { series: s || null, folders: [] }; used.set(k, g); out.push(g); }
    used.get(k).folders.push(f);
  }
  out.sort((a, b) => (a.series ? a.series.number : 1e9) - (b.series ? b.series.number : 1e9));
  return out;
}

function roman(n) {
  const m = [[1000, 'M'], [900, 'CM'], [500, 'D'], [400, 'CD'], [100, 'C'], [90, 'XC'], [50, 'L'], [40, 'XL'], [10, 'X'], [9, 'IX'], [5, 'V'], [4, 'IV'], [1, 'I']];
  let s = ''; for (const [v, r] of m) while (n >= v) { s += r; n -= v; } return s;
}

function folderTitle(f) { return f.title || f.label || `Folder ${f.folder}`; }

// ---------------------------------------------------------------- citations (Chicago)

function naturalDate(dacs) {
  const mo = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  const m = /^(\d{4})(?:-(\d{2}))?(?:-(\d{2}))?$/.exec((dacs || '').trim());
  if (!m) return dacs || '';
  if (!m[2]) return m[1];
  const mon = mo[parseInt(m[2], 10) - 1] || '';
  return m[3] ? `${mon} ${parseInt(m[3], 10)}, ${m[1]}` : `${mon} ${m[1]}`;
}

function citation(data, f, it) {
  const c = data.collection;
  const who = [it.creator && it.recipient ? `${it.creator} to ${it.recipient}` : it.creator || ''].filter(Boolean)[0];
  const head = [who, it.title].filter(Boolean).join(', ');
  const date = it.date ? naturalDate(it.dateSort || it.date) || it.date : '';
  const loc = f.box ? `box ${f.box}, folder ${f.folder}` : '';
  const src = (it.source && (it.source.collection || it.source.repository)) ? [it.source.location, it.source.collection, it.source.repository] : [loc, c.title, c.repository];
  return [head, date].filter(Boolean).join(', ') + (src.filter(Boolean).length ? ', ' + src.filter(Boolean).join(', ') : '') + '.';
}

// ---------------------------------------------------------------- generators

function csv(rows) {
  return rows.map((r) => r.map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`).join(',')).join('\r\n') + '\r\n';
}

function containerCsv(data) {
  const rows = [['Series', 'Box', 'Folder', 'Title', 'Dates', 'Items', 'Scope note']];
  for (const g of groups(data)) for (const f of g.folders) {
    rows.push([g.series ? `${roman(g.series.number)}. ${g.series.title}` : '', f.box, f.folder, folderTitle(f), f.dates, f.items.length, f.scopeNote]);
  }
  return csv(rows);
}

function itemCsv(data) {
  const rows = [['Box', 'Folder', 'Item', 'Title', 'Date', 'Sort date', 'Type', 'Creator', 'Recipient', 'Summary', 'People', 'Places', 'Organizations', 'Subjects', 'Pages', 'Reviewed', 'Source repository', 'Source collection', 'Source location', 'Citation']];
  for (const f of data.folders) for (const it of f.items) {
    const s = it.source || {};
    rows.push([f.box, f.folder, it.sequence, it.title, it.date, it.dateSort, it.type, it.creator, it.recipient, it.summary,
      (it.people || []).join('; '), (it.places || []).join('; '), (it.organizations || []).join('; '), (it.subjects || []).join('; '),
      it.pages.length, it.reviewed ? 'yes' : 'no', s.repository || '', s.collection || '', s.location || '', citation(data, f, it)]);
  }
  return csv(rows);
}

function paragraphs(t) { return String(t || '').split(/\n\s*\n/).map((x) => x.trim()).filter(Boolean); }

function findingAidHtml(data, opts = {}) {
  const c = data.collection;
  const items = opts.items !== false;
  const row = (k, v) => (v ? `<tr><th>${esc(k)}</th><td>${esc(v)}</td></tr>` : '');
  const section = (title, text) => (text && text.trim() ? `<h2>${esc(title)}</h2>${paragraphs(text).map((p) => `<p>${esc(p)}</p>`).join('')}` : '');
  let body = `<h1>${esc(c.title)}</h1><p class="sub">${esc([c.creator, c.dates].filter(Boolean).join(' · '))}</p>`;
  body += `<table class="meta">${row('Repository', c.repository)}${row('Identifier', c.identifier)}${row('Dates', c.dates)}${row('Extent', c.extent)}${row('Access', c.accessRestrict)}${row('Use', c.useRestrict)}</table>`;
  body += section('Abstract', c.abstract) + section('Biographical / historical note', c.bioHist) + section('Scope and content', c.scopeContent) + section('Arrangement', c.arrangement);
  body += section('Preferred citation', c.preferredCitation) + section('Processing information', c.processingInfo);
  body += '<h2>Container list</h2>';
  for (const g of groups(data)) {
    if (g.series) body += `<h3>Series ${roman(g.series.number)}. ${esc(g.series.title)}</h3>${g.series.scopeNote ? `<p class="scope">${esc(g.series.scopeNote)}</p>` : ''}`;
    body += '<table class="cl"><thead><tr><th>Box</th><th>Folder</th><th>Title</th><th>Dates</th></tr></thead><tbody>';
    for (const f of g.folders) {
      body += `<tr class="f"><td>${f.box}</td><td>${f.folder}</td><td>${esc(folderTitle(f))}${f.scopeNote ? `<div class="scope">${esc(f.scopeNote)}</div>` : ''}</td><td>${esc(f.dates)}</td></tr>`;
      if (items) for (const it of f.items) {
        body += `<tr class="i"><td></td><td></td><td>${esc(it.title)}${it.summary ? `<div class="scope">${esc(it.summary)}</div>` : ''}</td><td>${esc(it.date)}</td></tr>`;
      }
    }
    body += '</tbody></table>';
  }
  const idx = accessTerms(data);
  for (const [label, list] of Object.entries(idx)) if (list.length) body += `<h2>${esc(label)}</h2><p>${list.map(esc).join(' · ')}</p>`;
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><title>${esc(c.title)} — Finding aid</title><style>
@page{margin:0.8in}body{font:11pt/1.5 Georgia,'Times New Roman',serif;color:#1c1b19;max-width:7in;margin:0 auto;padding:24px}
h1{font-size:22pt;margin:.2em 0 0}.sub{color:#555;margin-top:2pt}h2{font-size:13pt;border-bottom:1px solid #999;padding-bottom:2pt;margin-top:1.6em}h3{font-size:11.5pt;margin:1.2em 0 .2em}
table{border-collapse:collapse;width:100%}.meta th{text-align:left;width:1.3in;font-weight:normal;color:#666;vertical-align:top}.meta td,.meta th{padding:1pt 4pt}
.cl th{text-align:left;font-size:9pt;text-transform:uppercase;letter-spacing:.06em;color:#666;border-bottom:1px solid #999}.cl td{padding:3pt 4pt;vertical-align:top;border-bottom:1px solid #ddd}
.cl td:nth-child(1),.cl td:nth-child(2){width:.5in}.cl td:nth-child(4){width:1.3in}tr.i td:nth-child(3){padding-left:16pt;color:#333}.scope{font-size:9.5pt;color:#555}
</style></head><body>${body}</body></html>`;
}

function accessTerms(data) {
  const top = (key) => {
    const counts = new Map();
    for (const f of data.folders) for (const it of f.items) for (const v of it[key] || []) counts.set(v, (counts.get(v) || 0) + 1);
    return [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).slice(0, 40).map((e) => e[0]).sort((a, b) => a.localeCompare(b));
  };
  return { 'Names': top('people'), 'Organizations': top('organizations'), 'Places': top('places'), 'Subjects': top('subjects') };
}

function ead(data) {
  const c = data.collection;
  const x = [];
  const ind = (n) => '  '.repeat(n);
  const leaf = (n, tag, text, attrs = '') => { if (text) x.push(`${ind(n)}<${tag}${attrs}>${esc(text)}</${tag}>`); };
  x.push('<?xml version="1.0" encoding="UTF-8"?>');
  x.push('<ead xmlns="urn:isbn:1-931666-22-9" xmlns:xlink="http://www.w3.org/1999/xlink">');
  x.push('  <eadheader><eadid>' + esc(c.identifier || slug(c.title)) + '</eadid><filedesc><titlestmt>');
  x.push(`    <titleproper>${esc(c.title)}</titleproper>`);
  x.push('  </titlestmt></filedesc></eadheader>');
  x.push('  <archdesc level="collection">');
  x.push('    <did>');
  leaf(3, 'unittitle', c.title);
  leaf(3, 'origination', c.creator, ' label="creator"');
  leaf(3, 'unitid', c.identifier);
  leaf(3, 'unitdate', c.dates, ' type="inclusive"');
  leaf(3, 'physdesc', c.extent);
  leaf(3, 'repository', c.repository);
  x.push('    </did>');
  const note = (tag, head, text) => {
    if (!text || !text.trim()) return;
    x.push(`    <${tag}><head>${esc(head)}</head>`);
    for (const p of paragraphs(text)) x.push(`      <p>${esc(p)}</p>`);
    x.push(`    </${tag}>`);
  };
  note('abstract', 'Abstract', c.abstract);
  note('bioghist', 'Biographical / Historical Note', c.bioHist);
  note('scopecontent', 'Scope and Content', c.scopeContent);
  note('arrangement', 'Arrangement', c.arrangement);
  note('accessrestrict', 'Access', c.accessRestrict);
  note('userestrict', 'Use', c.useRestrict);
  note('prefercite', 'Preferred Citation', c.preferredCitation);
  note('processinfo', 'Processing Information', c.processingInfo);
  x.push('    <dsc>');
  const folderXml = (f, n) => {
    x.push(`${ind(n)}<c level="file"><did>`);
    leaf(n + 1, 'unittitle', folderTitle(f));
    leaf(n + 1, 'unitdate', f.dates);
    x.push(`${ind(n + 1)}<container type="box">${f.box}</container><container type="folder">${f.folder}</container>`);
    x.push(`${ind(n)}</did>`);
    if (f.scopeNote) { x.push(`${ind(n)}<scopecontent><p>${esc(f.scopeNote)}</p></scopecontent>`); }
    for (const it of f.items) {
      x.push(`${ind(n + 1)}<c level="item"><did>`);
      leaf(n + 2, 'unittitle', it.title);
      leaf(n + 2, 'unitdate', it.date, it.dateSort ? ` normal="${esc(it.dateSort)}"` : '');
      x.push(`${ind(n + 1)}</did>`);
      if (it.summary) x.push(`${ind(n + 1)}<scopecontent><p>${esc(it.summary)}</p></scopecontent>`);
      x.push(`${ind(n + 1)}</c>`);
    }
    x.push(`${ind(n)}</c>`);
  };
  for (const g of groups(data)) {
    if (g.series) {
      x.push(`      <c level="series"><did><unittitle>${esc(g.series.title)}</unittitle><unitid>${roman(g.series.number)}</unitid></did>`);
      if (g.series.scopeNote) x.push(`        <scopecontent><p>${esc(g.series.scopeNote)}</p></scopecontent>`);
      g.folders.forEach((f) => folderXml(f, 4));
      x.push('      </c>');
    } else g.folders.forEach((f) => folderXml(f, 3));
  }
  x.push('    </dsc>', '  </archdesc>', '</ead>');
  return x.join('\n') + '\n';
}

// ---------------------------------------------------------------- search

function search(data, q) {
  const terms = String(q || '').toLowerCase().split(/\s+/).filter(Boolean);
  if (!terms.length) return [];
  const hits = [];
  for (const f of data.folders) for (const it of f.items) {
    const text = (it.pages || []).map((p) => p.text || '').join(' ');
    const hay = [it.title, it.creator, it.recipient, it.summary, it.date, it.type, (it.people || []).join(' '), (it.places || []).join(' '), (it.organizations || []).join(' '), (it.subjects || []).join(' '), it.note, f.title, f.label].join(' ').toLowerCase();
    const all = hay + ' ' + text.toLowerCase();
    if (terms.every((t) => all.includes(t))) {
      let snippet = '';
      const idx = text.toLowerCase().indexOf(terms[0]);
      if (idx >= 0 && !hay.includes(terms[0])) snippet = text.slice(Math.max(0, idx - 50), idx + 90).replace(/\s+/g, ' ');
      hits.push({ box: f.box, folder: f.folder, sequence: it.sequence, title: it.title, date: it.date, snippet });
    }
  }
  return hits;
}

module.exports = { Library, findingAidHtml, ead, containerCsv, itemCsv, citation, search, groups, roman, folderKey, itemKey, accessTerms, applyEdits, naturalDate };
