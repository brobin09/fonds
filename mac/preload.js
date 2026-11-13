'use strict';
const { contextBridge, ipcRenderer, webUtils } = require('electron');
contextBridge.exposeInMainWorld('fonds', {
  list: () => ipcRenderer.invoke('library:list'),
  pick: () => ipcRenderer.invoke('library:pick'),
  importZip: (file, opts) => ipcRenderer.invoke('library:import', file, opts),
  load: (id) => ipcRenderer.invoke('library:load', id),
  edit: (id, edit) => ipcRenderer.invoke('library:edit', id, edit),
  remove: (id, title) => ipcRenderer.invoke('library:remove', id, title),
  reveal: (id) => ipcRenderer.invoke('library:reveal', id),
  searchAll: (q) => ipcRenderer.invoke('library:searchAll', q),
  exportAs: (id, kind) => ipcRenderer.invoke('export', id, kind),
  print: (id, withItems) => ipcRenderer.invoke('print', id, withItems),
  savePage: (src, name) => ipcRenderer.invoke('img:savePage', src, name),
  exportImages: (id, filter) => ipcRenderer.invoke('img:exportAll', id, filter),
  preview: (id, withItems) => ipcRenderer.invoke('preview', id, withItems),
  cite: (id, box, folder, seq) => ipcRenderer.invoke('cite', id, box, folder, seq),
  accessTerms: (id) => ipcRenderer.invoke('aid', id),
  pathFor: (file) => webUtils.getPathForFile(file),
  onOpen: (cb) => ipcRenderer.on('open-package', (_e, f) => cb(f)),
  onMenu: (cb) => ipcRenderer.on('menu', (_e, m) => cb(m)),
});
