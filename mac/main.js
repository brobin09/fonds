'use strict';
const { app, BrowserWindow, ipcMain, dialog, shell, Menu } = require('electron');
const path = require('path');
const fs = require('fs');
const lib = require('./lib');

let win = null;
let library = null;
let pendingOpen = [];

function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 840, minWidth: 900, minHeight: 600,
    titleBarStyle: 'hiddenInset', backgroundColor: '#f4f1ea',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false },
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.webContents.once('did-finish-load', () => {
    for (const f of pendingOpen) win.webContents.send('open-package', f);
    pendingOpen = [];
  });
  win.on('closed', () => { win = null; });
}

app.on('open-file', (e, file) => {
  e.preventDefault();
  if (win && win.webContents && !win.webContents.isLoading()) win.webContents.send('open-package', file);
  else pendingOpen.push(file);
});

function menu() {
  const t = [
    { role: 'appMenu' },
    { label: 'File', submenu: [
      { label: 'Open Package…', accelerator: 'CmdOrCtrl+O', click: () => win && win.webContents.send('menu', 'import') },
      { label: 'Find in Collection', accelerator: 'CmdOrCtrl+F', click: () => win && win.webContents.send('menu', 'find') },
      { label: 'Print Finding Aid…', accelerator: 'CmdOrCtrl+P', click: () => win && win.webContents.send('menu', 'print') },
      { type: 'separator' }, { role: 'close' } ] },
    { role: 'editMenu' },
    { label: 'View', submenu: [
      { label: 'Gallery / List', accelerator: 'CmdOrCtrl+G', click: () => win && win.webContents.send('menu', 'toggle') },
      { type: 'separator' }, { role: 'reload' }, { role: 'togglefullscreen' }, { role: 'toggleDevTools' } ] },
    { role: 'windowMenu' },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(t));
}

app.whenReady().then(() => {
  library = new lib.Library(path.join(app.getPath('userData'), 'library'));
  menu();
  try { if (process.platform === 'darwin' && app.dock) app.dock.setIcon(path.join(__dirname, 'build', 'icon.png')); } catch (_) { /* packaged app uses the bundle icon */ }

  ipcMain.handle('library:list', () => library.list());
  ipcMain.handle('library:pick', async () => {
    const r = await dialog.showOpenDialog(win, { title: 'Open Fonds package', properties: ['openFile', 'multiSelections'], filters: [{ name: 'Fonds package (ZIP)', extensions: ['zip'] }] });
    return r.canceled ? [] : r.filePaths;
  });
  ipcMain.handle('library:import', (_e, file, opts) => {
    try { return { ok: true, ...library.importZip(file, opts || {}) }; } catch (err) { return { ok: false, error: String(err.message || err) }; }
  });
  ipcMain.handle('library:load', (_e, id) => library.load(id));
  ipcMain.handle('library:edit', (_e, id, edit) => library.saveEdit(id, edit));
  ipcMain.handle('library:remove', async (_e, id, title) => {
    const r = await dialog.showMessageBox(win, { type: 'warning', buttons: ['Remove', 'Cancel'], defaultId: 1, cancelId: 1, message: `Remove “${title}” from Fonds for Mac?`, detail: 'This deletes the imported copy and any edits made here. Your phone and the original ZIP are not touched.' });
    if (r.response !== 0) return false;
    library.remove(id); return true;
  });
  ipcMain.handle('library:reveal', (_e, id) => shell.showItemInFolder(library.p(id, 'data.json')));
  ipcMain.handle('library:searchAll', (_e, q) => {
    const out = [];
    for (const m of library.list()) {
      const { data } = library.load(m.id);
      for (const h of lib.search(data, q)) out.push({ ...h, collectionId: m.id, collection: m.title });
    }
    return out;
  });
  ipcMain.handle('export', async (_e, id, kind) => {
    const { data } = library.load(id);
    const base = (data.collection.title || 'finding-aid').replace(/[^\w\- ]+/g, '').trim().replace(/\s+/g, '-');
    const map = {
      html: ['Finding aid (HTML)', 'html', () => lib.findingAidHtml(data)],
      ead: ['EAD 2002 (XML)', 'xml', () => lib.ead(data)],
      containers: ['Container list (CSV)', 'csv', () => lib.containerCsv(data)],
      items: ['Item list (CSV)', 'csv', () => lib.itemCsv(data)],
      json: ['Fonds data (JSON)', 'json', () => JSON.stringify(data, null, 2)],
    };
    const m = map[kind]; if (!m) return null;
    const r = await dialog.showSaveDialog(win, { defaultPath: `${base}.${m[1]}`, filters: [{ name: m[0], extensions: [m[1]] }] });
    if (r.canceled) return null;
    fs.writeFileSync(r.filePath, m[2](), 'utf8');
    shell.showItemInFolder(r.filePath);
    return r.filePath;
  });
  ipcMain.handle('print', async (_e, id, withItems) => {
    const { data } = library.load(id);
    const tmp = path.join(app.getPath('temp'), `fonds-print-${Date.now()}.html`);
    fs.writeFileSync(tmp, lib.findingAidHtml(data, { items: withItems }), 'utf8');
    const w = new BrowserWindow({ show: false });
    await w.loadFile(tmp);
    const r = await dialog.showSaveDialog(win, { defaultPath: `${(data.collection.title || 'finding-aid').replace(/[^\w\- ]+/g, '').trim().replace(/\s+/g, '-')}.pdf`, filters: [{ name: 'PDF', extensions: ['pdf'] }] });
    if (r.canceled) { w.destroy(); return null; }
    const pdf = await w.webContents.printToPDF({ printBackground: true, pageSize: 'Letter' });
    fs.writeFileSync(r.filePath, pdf); w.destroy();
    shell.showItemInFolder(r.filePath);
    return r.filePath;
  });
  ipcMain.handle('preview', (_e, id, withItems) => { const { data } = library.load(id); return lib.findingAidHtml(data, { items: withItems }); });
  ipcMain.handle('cite', (_e, id, box, folder, seq) => {
    const { data } = library.load(id);
    const f = data.folders.find((x) => x.box === box && x.folder === folder);
    const it = f && f.items.find((x) => x.sequence === seq);
    return it ? lib.citation(data, f, it) : '';
  });
  ipcMain.handle('aid', (_e, id) => { const { data } = library.load(id); return { accessTerms: lib.accessTerms(data), groups: lib.groups(data).map((g) => ({ series: g.series, folders: g.folders.map((f) => `${f.box}/${f.folder}`) })) }; });

  createWindow();
  app.on('activate', () => { if (!BrowserWindow.getAllWindows().length) createWindow(); });
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
