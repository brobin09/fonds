'use strict';
(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const h = (tag, attrs = {}, ...kids) => {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'class') e.className = v;
      else if (k === 'html') e.innerHTML = v;
      else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
      else if (v !== false && v != null) e.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) if (kid != null && kid !== false) e.append(kid.nodeType ? kid : document.createTextNode(kid));
    return e;
  };
  const fileUrl = (p) => (p ? 'file://' + encodeURI(p).replace(/#/g, '%23').replace(/\?/g, '%3F') : '');
  const roman = (n) => { const m = [[1000, 'M'], [900, 'CM'], [500, 'D'], [400, 'CD'], [100, 'C'], [90, 'XC'], [50, 'L'], [40, 'XL'], [10, 'X'], [9, 'IX'], [5, 'V'], [4, 'IV'], [1, 'I']]; let s = ''; for (const [v, r] of m) while (n >= v) { s += r; n -= v; } return s; };

  const state = {
    library: [], id: null, data: null, tab: 'browse', view: localStorage.getItem('view') || 'gallery',
    filter: '', sel: null, page: 0, hits: null, withItems: true,
  };

  function toast(msg, ms = 3200) {
    const t = $('#toast'); t.textContent = msg; t.classList.remove('hidden');
    clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.add('hidden'), ms);
  }

  // ------------------------------------------------------------ library

  async function refreshLibrary() {
    state.library = await window.fonds.list();
    const box = $('#library'); box.replaceChildren();
    for (const m of state.library) {
      box.append(h('div', { class: 'lib' + (m.id === state.id ? ' sel' : ''), onclick: () => openCollection(m.id), oncontextmenu: (e) => { e.preventDefault(); libMenu(m); } },
        h('b', {}, m.title), h('small', {}, `${m.items} items · ${m.pages} pages${m.edited ? ' · edited' : ''}`)));
    }
    if (!state.library.length) box.append(h('small', { style: 'color:var(--slate);padding:6px' }, 'No collections yet.'));
  }

  async function libMenu(m) {
    if (await window.fonds.remove(m.id, m.title)) {
      if (state.id === m.id) { state.id = null; state.data = null; closeDetail(); }
      await refreshLibrary(); render();
    }
  }

  async function importFiles(files) {
    for (const f of files) {
      let r = await window.fonds.importZip(f);
      if (!r.ok) { toast(r.error, 6000); continue; }
      toast(r.replaced ? `Updated “${r.manifest.title}”${r.keptEdits ? ' — your edits here were kept' : ''}.` : `Imported “${r.manifest.title}”.`);
      state.id = r.manifest.id;
      await refreshLibrary();
      await openCollection(r.manifest.id);
    }
  }

  async function openCollection(id, keep) {
    state.id = id;
    const { data } = await window.fonds.load(id);
    state.data = data;
    if (!keep) { state.tab = 'browse'; state.filter = ''; state.hits = null; closeDetail(); }
    await refreshLibrary();
    render();
  }

  // ------------------------------------------------------------ helpers

  const groupsOf = () => {
    const d = state.data;
    const byId = new Map(d.series.map((s) => [s.id, s]));
    const out = []; const idx = new Map();
    for (const f of d.folders) {
      const s = f.seriesId != null ? byId.get(f.seriesId) : null;
      const k = s ? s.id : 'none';
      if (!idx.has(k)) { const g = { series: s, folders: [] }; idx.set(k, g); out.push(g); }
      idx.get(k).folders.push(f);
    }
    return out.sort((a, b) => (a.series ? a.series.number : 1e9) - (b.series ? b.series.number : 1e9));
  };
  const fTitle = (f) => f.title || f.label || `Folder ${f.folder}`;
  const matches = (f, it) => {
    const q = state.filter.trim().toLowerCase(); if (!q) return true;
    const hay = [it.title, it.creator, it.recipient, it.date, it.summary, (it.people || []).join(' '), (it.places || []).join(' '), (it.subjects || []).join(' '), fTitle(f), it.pages.map((p) => p.text).join(' ')].join(' ').toLowerCase();
    return q.split(/\s+/).every((t) => hay.includes(t));
  };
  const statusDot = (it) => h('span', { class: 'dot ' + (it.reviewed ? 'ok' : Object.keys(it.pendingDrafts || {}).length ? 'pend' : '') });

  // ------------------------------------------------------------ main render

  function render() {
    const main = $('#main'); main.replaceChildren();
    if (state.hits) return renderHits(main);
    if (!state.data) {
      main.append(h('div', { class: 'empty' },
        h('div', { class: 'head', style: 'position:absolute;top:0;left:0;right:0;height:44px' }),
        h('h1', {}, 'Fonds for Mac'),
        h('p', {}, 'Your phone scans and describes. This is where you review it on the big screen.'),
        h('ol', {}, h('li', {}, 'On the phone: open a collection → Finding aid → Export → Complete package (ZIP).'),
          h('li', {}, 'Save it to Google Drive, AirDrop it, or email it to yourself.'),
          h('li', {}, 'Drop the ZIP here, or choose Open package.')),
        h('p', {}, h('button', { class: 'primary', onclick: pickAndImport }, 'Open package…'))));
      return;
    }
    const d = state.data, c = d.collection;
    const tabs = [['browse', 'Browse'], ['series', 'Series'], ['aid', 'Finding aid'], ['details', 'Details']];
    const head = h('div', { class: 'head' },
      h('h1', {}, c.title), h('div', { class: 'sub' }, [c.creator, c.dates, c.extent].filter(Boolean).join(' · ')),
      h('div', { class: 'bar' },
        h('div', { class: 'tabs' }, tabs.map(([k, l]) => h('button', { class: 'tab' + (state.tab === k ? ' on' : ''), onclick: () => { state.tab = k; render(); } }, l))),
        h('div', { class: 'sp' }),
        state.tab === 'browse' ? [
          h('input', { id: 'filter', type: 'search', placeholder: 'Filter items…', value: state.filter, style: 'width:200px', oninput: (e) => { state.filter = e.target.value; renderBody(); } }),
          h('button', { onclick: toggleView, title: '⌘G' }, state.view === 'gallery' ? 'List' : 'Gallery')] : null,
        h('button', { onclick: () => window.fonds.reveal(state.id) }, 'Show in Finder')));
    main.append(head, h('div', { class: 'body', id: 'body' }));
    renderBody();
  }

  function renderBody() {
    const body = $('#body'); if (!body) return;
    body.replaceChildren();
    ({ browse: browse, series: seriesTab, aid: aidTab, details: detailsTab })[state.tab](body);
  }

  function toggleView() { state.view = state.view === 'gallery' ? 'list' : 'gallery'; localStorage.setItem('view', state.view); renderBody(); render(); }

  // ------------------------------------------------------------ browse

  function browse(body) {
    let any = false;
    for (const g of groupsOf()) {
      const folders = g.folders.map((f) => ({ f, items: f.items.filter((it) => matches(f, it)) })).filter((x) => x.items.length || !state.filter);
      if (!folders.length) continue;
      any = true;
      const sec = h('div', { class: 'group' });
      if (g.series) sec.append(h('h2', {}, `Series ${roman(g.series.number)}. ${g.series.title}`), g.series.scopeNote ? h('p', { class: 'scope' }, g.series.scopeNote) : null);
      for (const { f, items } of folders) {
        sec.append(h('div', { class: 'folder' }, h('span', { class: 'tag' }, `Box ${f.box} · Folder ${f.folder}`), h('b', {}, fTitle(f)), h('small', {}, [f.dates, `${f.items.length} items`].filter(Boolean).join(' · '))));
        if (f.scopeNote && !state.filter) sec.append(h('div', { class: 'scope', style: 'color:var(--slate);font-size:13px;margin-bottom:6px' }, f.scopeNote));
        sec.append(state.view === 'gallery' ? gallery(f, items) : list(f, items));
      }
      body.append(sec);
    }
    if (!any) body.append(h('p', { style: 'color:var(--slate)' }, state.filter ? 'No items match.' : 'This collection has no items yet.'));
  }

  const isSel = (f, it) => state.sel && state.sel.box === f.box && state.sel.folder === f.folder && state.sel.seq === it.sequence;
  const thumb = (it) => it.pages[0] && it.pages[0].imagePath ? fileUrl(it.pages[0].imagePath) : '';

  function gallery(f, items) {
    return h('div', { class: 'grid' }, items.map((it) => h('div', { class: 'tile' + (isSel(f, it) ? ' sel' : ''), onclick: () => openItem(f, it) },
      h('div', { class: 'im', style: thumb(it) ? `background-image:url("${thumb(it)}")` : '' }, it.pages.length > 1 ? h('span', { class: 'badge' }, `${it.pages.length} pp`) : null),
      h('div', { class: 'm' }, h('div', {}, it.title), h('div', {}, statusDot(it), it.date || 'undated')))));
  }

  function list(f, items) {
    return h('div', { class: 'rows' }, items.map((it) => h('div', { class: 'row' + (isSel(f, it) ? ' sel' : ''), onclick: () => openItem(f, it) },
      thumb(it) ? h('img', { src: thumb(it) }) : h('div'),
      h('div', {}, h('div', { class: 't' }, statusDot(it), it.title), h('div', { class: 's' }, it.summary || '')),
      h('div', { class: 's' }, [it.creator, it.recipient].filter(Boolean).join(' → ')),
      h('div', { class: 's' }, it.date || ''))));
  }

  // ------------------------------------------------------------ series tab

  function seriesTab(body) {
    const d = state.data;
    if (!d.series.length) { body.append(h('p', { style: 'color:var(--slate)' }, 'No series yet. Suggest series from the Series tab on the phone, then export again.')); return; }
    for (const g of groupsOf()) {
      body.append(h('div', { class: 'card' },
        h('h2', { style: 'margin:0 0 4px' }, g.series ? `${roman(g.series.number)}. ${g.series.title}` : 'Unassigned folders'),
        g.series && g.series.scopeNote ? h('p', {}, g.series.scopeNote) : null,
        h('small', { style: 'color:var(--slate)' }, g.folders.map((f) => `Box ${f.box}/Folder ${f.folder}`).join(' · '))));
    }
  }

  // ------------------------------------------------------------ finding aid tab

  async function aidTab(body) {
    const id = state.id;
    const ex = (kind, label) => h('button', { onclick: async () => { const p = await window.fonds.exportAs(id, kind); if (p) toast(`Saved ${p.split('/').pop()}`); } }, label);
    body.append(h('div', { class: 'exports', style: 'margin-bottom:12px' },
      h('button', { class: 'primary', onclick: async () => { const p = await window.fonds.print(id, state.withItems); if (p) toast('PDF saved'); } }, 'Save as PDF…'),
      ex('html', 'HTML'), ex('ead', 'EAD (ArchivesSpace)'), ex('containers', 'Container list CSV'), ex('items', 'Item list CSV'), ex('json', 'JSON'),
      h('label', { style: 'display:flex;gap:6px;align-items:center;text-transform:none;letter-spacing:0;margin:0 0 0 8px' },
        h('input', { type: 'checkbox', style: 'width:auto', checked: state.withItems, onchange: (e) => { state.withItems = e.target.checked; renderBody(); } }), 'List every item')));
    const frame = h('iframe', { id: 'fa', sandbox: '' });
    frame.srcdoc = await window.fonds.preview(id, state.withItems);
    body.append(frame);
  }

  // ------------------------------------------------------------ details tab

  function detailsTab(body) {
    const c = state.data.collection;
    const fields = [['title', 'Title', 1], ['creator', 'Creator', 1], ['repository', 'Repository', 1], ['identifier', 'Identifier', 1], ['dates', 'Dates', 1], ['extent', 'Extent', 1],
      ['abstract', 'Abstract'], ['scopeContent', 'Scope and content'], ['bioHist', 'Biographical / historical note'], ['arrangement', 'Arrangement'],
      ['accessRestrict', 'Access'], ['useRestrict', 'Use'], ['preferredCitation', 'Preferred citation'], ['processingInfo', 'Processing information']];
    const drafts = c.pendingDrafts || {};
    const card = h('div', { class: 'card', style: 'max-width:760px' });
    for (const [k, label, one] of fields) {
      const el = one ? h('input', { value: c[k] || '' }) : h('textarea', { rows: 4 }, c[k] || '');
      el.addEventListener('change', async () => { await saveEdit({ scope: 'collection', fields: { [k]: el.value } }, true); toast('Saved'); });
      card.append(h('label', {}, label), el);
      const dk = { abstract: 'abstractText', scopeContent: 'scopeContent', arrangement: 'arrangement' }[k];
      if (dk && drafts[dk] && !c[k]) card.append(h('div', { class: 'draft' }, h('span', {}, h('small', {}, 'Draft from phone'), drafts[dk]),
        h('button', { onclick: async () => { await saveEdit({ scope: 'collection', fields: { [k]: drafts[dk] } }, true); renderBody(); } }, 'Accept')));
    }
    body.append(card);
  }

  async function saveEdit(edit, keepView) {
    const r = await window.fonds.edit(state.id, edit);
    state.data = r.data;
    await refreshLibrary();
    if (!keepView) render();
  }

  // ------------------------------------------------------------ item detail

  const LISTS = new Set(['people', 'places', 'organizations', 'subjects']);
  const DRAFT_KEYS = { title: 'title', date: 'date', dateSort: 'dateSort', docType: 'type', creator: 'creator', recipient: 'recipient', summary: 'summary', people: 'people', places: 'places', organizations: 'organizations', subjects: 'subjects' };
  const splitList = (s) => String(s || '').split(';').map((x) => x.trim()).filter(Boolean);

  function findItem() {
    if (!state.sel || !state.data) return null;
    const f = state.data.folders.find((x) => x.box === state.sel.box && x.folder === state.sel.folder);
    const it = f && f.items.find((x) => x.sequence === state.sel.seq);
    return it ? { f, it } : null;
  }

  function openItem(f, it, page = 0) {
    state.sel = { box: f.box, folder: f.folder, seq: it.sequence }; state.page = page;
    renderDetail(); if (state.tab === 'browse') renderBody();
  }
  function closeDetail() { state.sel = null; $('#detail').classList.add('hidden'); }

  async function renderDetail() {
    const found = findItem(); const el = $('#detail');
    if (!found) { el.classList.add('hidden'); return; }
    const { f, it } = found;
    el.classList.remove('hidden'); el.replaceChildren();
    const pg = it.pages[Math.min(state.page, it.pages.length - 1)];
    const edit = (fields) => saveEdit({ scope: 'item', key: `${f.box}/${f.folder}/${it.sequence}`, fields }, true).then(() => { renderDetail(); if (state.tab === 'browse') renderBody(); });
    const key = (k, label, multi) => {
      const raw = it[k];
      const val = Array.isArray(raw) ? raw.join('; ') : raw || '';
      const inp = multi ? h('textarea', { rows: 3 }, val) : h('input', { value: val });
      inp.addEventListener('change', () => edit({ [k]: LISTS.has(k) ? splitList(inp.value) : inp.value }));
      const out = [h('label', {}, label), inp];
      const dk = Object.keys(DRAFT_KEYS).find((x) => DRAFT_KEYS[x] === k);
      const dv = it.pendingDrafts && it.pendingDrafts[dk];
      if (dv && !val) out.push(h('div', { class: 'draft' }, h('span', {}, h('small', {}, `Draft${it.draftedBy ? ' · ' + it.draftedBy : ''}`), dv),
        h('button', { onclick: () => edit({ [k]: LISTS.has(k) ? splitList(dv) : dv }) }, 'Accept')));
      return out;
    };
    const parts = [
      h('div', { style: 'display:flex;gap:8px;align-items:center;margin-bottom:8px' },
        h('small', { style: 'color:var(--slate)' }, `Box ${f.box} · Folder ${f.folder} · Item ${it.sequence}`), h('div', { class: 'sp', style: 'flex:1' }),
        h('button', { class: 'ghost', onclick: closeDetail }, '✕')),
      pg && pg.imagePath ? h('div', { class: 'viewer' }, h('img', { src: fileUrl(pg.imagePath), onclick: () => lightbox(pg.imagePath) })) : h('div', { class: 'viewer' }, 'No image'),
      it.pages.length > 1 ? h('div', { class: 'pages' }, it.pages.map((p, i) => h('img', { class: i === state.page ? 'on' : '', src: fileUrl(p.imagePath), onclick: () => { state.page = i; renderDetail(); } }))) : null,
      h('label', { style: 'display:flex;gap:8px;align-items:center;text-transform:none;letter-spacing:0;font-size:13px;color:var(--ink)' },
        h('input', { type: 'checkbox', style: 'width:auto', checked: !!it.reviewed, onchange: (e) => edit({ reviewed: e.target.checked }) }), 'Reviewed'),
      key('title', 'Title'),
      h('div', { class: 'cols' }, h('div', {}, key('date', 'Date')), h('div', {}, key('type', 'Type'))),
      h('div', { class: 'cols' }, h('div', {}, key('creator', 'From / creator')), h('div', {}, key('recipient', 'To'))),
      key('summary', 'Summary', true), key('people', 'People', true), key('places', 'Places'), key('organizations', 'Organizations'), key('subjects', 'Subjects'),
      key('note', 'Processing note', true),
      h('label', {}, 'Citation'), h('div', { class: 'cite', id: 'cite' }, '…'),
      h('div', { style: 'margin-top:6px' }, h('button', { onclick: async () => { await navigator.clipboard.writeText($('#cite').textContent); toast('Citation copied'); } }, 'Copy citation')),
      h('label', {}, `Transcription${pg ? ` · page ${pg.sequence}` : ''}`),
      h('div', { class: 'transcript' }, (pg && pg.text) || '(no text)'),
      h('div', { style: 'margin-top:6px' }, h('button', { onclick: async () => { await navigator.clipboard.writeText(it.pages.map((p) => p.text || '').join('\n\n')); toast('Transcription copied'); } }, 'Copy all pages'))];
    el.append(...parts.flat().filter(Boolean));
    $('#cite').textContent = await window.fonds.cite(state.id, f.box, f.folder, it.sequence);
  }

  function lightbox(p) {
    const lb = h('div', { class: 'lightbox', onclick: () => lb.remove() }, h('img', { src: fileUrl(p) }));
    document.body.append(lb);
  }

  // ------------------------------------------------------------ search all

  async function globalSearch(q) {
    if (!q.trim()) { state.hits = null; render(); return; }
    state.hits = { q, list: await window.fonds.searchAll(q) };
    render();
  }
  function renderHits(main) {
    main.append(h('div', { class: 'head' }, h('h1', {}, `“${state.hits.q}”`), h('div', { class: 'sub' }, `${state.hits.list.length} results across all collections`), h('div', { class: 'bar' })));
    const body = h('div', { class: 'body' });
    for (const x of state.hits.list) body.append(h('div', { class: 'hit', onclick: async () => {
      state.hits = null; $('#globalSearch').value = '';
      await openCollection(x.collectionId);
      const f = state.data.folders.find((y) => y.box === x.box && y.folder === x.folder);
      const it = f && f.items.find((y) => y.sequence === x.sequence);
      if (f && it) openItem(f, it);
    } }, h('b', {}, x.title), h('small', {}, [x.collection, `Box ${x.box}/Folder ${x.folder}`, x.date].filter(Boolean).join(' · ')), x.snippet ? h('small', {}, '…' + x.snippet + '…') : null));
    if (!state.hits.list.length) body.append(h('p', { style: 'color:var(--slate)' }, 'Nothing found.'));
    main.append(body);
  }

  // ------------------------------------------------------------ wiring

  async function pickAndImport() { const files = await window.fonds.pick(); if (files.length) importFiles(files); }

  $('#importBtn').addEventListener('click', pickAndImport);
  let st; $('#globalSearch').addEventListener('input', (e) => { clearTimeout(st); st = setTimeout(() => globalSearch(e.target.value), 250); });

  window.fonds.onOpen((f) => importFiles([f]));
  window.fonds.onMenu((m) => {
    if (m === 'import') pickAndImport();
    else if (m === 'toggle' && state.data) toggleView();
    else if (m === 'find') { const f = $('#filter'); (f || $('#globalSearch')).focus(); }
    else if (m === 'print' && state.id) { state.tab = 'aid'; render(); }
  });

  let depth = 0;
  window.addEventListener('dragenter', (e) => { e.preventDefault(); depth++; $('#drop').classList.remove('hidden'); });
  window.addEventListener('dragleave', () => { if (--depth <= 0) { depth = 0; $('#drop').classList.add('hidden'); } });
  window.addEventListener('dragover', (e) => e.preventDefault());
  window.addEventListener('drop', (e) => {
    e.preventDefault(); depth = 0; $('#drop').classList.add('hidden');
    const files = [...e.dataTransfer.files].filter((f) => /\.zip$/i.test(f.name)).map((f) => window.fonds.pathFor(f));
    if (files.length) importFiles(files); else toast('Drop a Fonds package ZIP.');
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') { const lb = $('.lightbox'); if (lb) lb.remove(); else if (state.sel) closeDetail(); } });

  refreshLibrary().then(async () => { if (state.library[0]) await openCollection(state.library[0].id); else render(); });
})();
