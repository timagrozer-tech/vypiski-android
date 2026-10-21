// Bridge between the page and the desktop shell. Only these few calls are exposed; the page has no Node access.
'use strict';
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('VypiskiDesktop', {
  platform: process.platform,
  version: () => ipcRenderer.sendSync('app:version'),
  showInFolder: (p) => ipcRenderer.invoke('file:show', p),
  nameDownload: (url, name) => ipcRenderer.sendSync('dl:name', url, name),
  openSaveFolder: () => ipcRenderer.invoke('folder:open'),
  exportFolder: () => ipcRenderer.invoke('folder:get'),
  chooseExportFolder: () => ipcRenderer.invoke('folder:choose'),
  resetExportFolder: () => ipcRenderer.invoke('folder:reset'),
  importBegin: (total, text) => ipcRenderer.send('import:begin', total, text),
  importProgress: (done, total, text) => ipcRenderer.send('import:progress', done, total, text),
  importEnd: (text) => ipcRenderer.send('import:end', text),
  onSaved: (cb) => ipcRenderer.on('file:saved', (_e, d) => cb(d)),
});
