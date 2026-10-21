// Bridge between the page and the desktop shell. Only these few calls are exposed; the page has no Node access.
'use strict';
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('VypiskiDesktop', {
  platform: process.platform,
  version: () => ipcRenderer.sendSync('app:version'),
  crashLog: () => ipcRenderer.sendSync('crash:log'),
  crashClear: () => ipcRenderer.sendSync('crash:clear'),
  showInFolder: (p) => ipcRenderer.invoke('file:show', p),
  nameDownload: (url, name) => ipcRenderer.sendSync('dl:name', url, name),
  openSaveFolder: () => ipcRenderer.invoke('folder:open'),
  exportFolder: () => ipcRenderer.invoke('folder:get'),
  chooseExportFolder: () => ipcRenderer.invoke('folder:choose'),
  resetExportFolder: () => ipcRenderer.invoke('folder:reset'),
  importBegin: (total, text) => ipcRenderer.send('import:begin', total, text),
  importProgress: (done, total, text) => ipcRenderer.send('import:progress', done, total, text),
  importEnd: (text) => ipcRenderer.send('import:end', text),
  printers: () => ipcRenderer.invoke('print:printers'),
  printHtml: (html, opts) => ipcRenderer.invoke('print:html', html, opts),
  printPdf: (html, name, landscape) => ipcRenderer.invoke('print:pdf', html, name, landscape),
  sources: {
    list: () => ipcRenderer.invoke('sources:list'),
    add: (p, label) => ipcRenderer.invoke('sources:add', p || '', label || ''),
    remove: (id) => ipcRenderer.invoke('sources:remove', id),
    mode: (id, m) => ipcRenderer.invoke('sources:mode', id, m),
    interval: (id, sec) => ipcRenderer.invoke('sources:interval', id, sec),
    scan: (id) => ipcRenderer.invoke('sources:scan', id),
    read: (id, rel) => ipcRenderer.invoke('sources:read', id, rel),
    onEvent: (cb) => ipcRenderer.on('sources:event', (_e, d) => cb(d)),
  },
  onSaved: (cb) => ipcRenderer.on('file:saved', (_e, d) => cb(d)),
});
