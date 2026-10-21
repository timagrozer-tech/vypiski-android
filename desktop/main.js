// Выписки — desktop shell for Windows 10/11 (Electron).
// The whole program is the same offline page as in the Android app (app/index.html), served from the private
// scheme app://vypiski — a secure origin, so IndexedDB, Web Workers and crypto.subtle work and the data is kept
// in the Windows user profile (%APPDATA%\Vypiski). Every network request is refused: documents never leave the PC.
'use strict';
const { app, BrowserWindow, protocol, net, session, shell, Menu, ipcMain, dialog, Notification, powerSaveBlocker } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const { pathToFileURL } = require('node:url');
const { Sources } = require('./sources');

const APP_DIR = path.join(__dirname, 'app');
const HOST = 'vypiski';
const START_URL = `app://${HOST}/index.html`;

protocol.registerSchemesAsPrivileged([
  { scheme: 'app', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true, codeCache: true } },
]);

if (!app.requestSingleInstanceLock()) { app.quit(); }
// no background connections of the browser engine itself (component updates, field trials, spell-check downloads)
for (const sw of ['disable-component-update', 'disable-background-networking', 'disable-domain-reliability', 'no-pings']) app.commandLine.appendSwitch(sw);
app.commandLine.appendSwitch('lang', 'ru'); // dates, pickers and system dialogs in Russian on any Windows
app.commandLine.appendSwitch('disable-features', 'SpellcheckService,AutofillServerCommunication,OptimizationHints,MediaRouter');

// the page tells the name of every file it saves (Chromium reports «download» for blob links on a custom scheme)
const dlNames = new Map();

/* ---------- settings (save folder) ---------- */
const cfgPath = () => path.join(app.getPath('userData'), 'settings.json');
function readCfg() { try { return JSON.parse(fs.readFileSync(cfgPath(), 'utf8')); } catch { return {}; } }
function writeCfg(c) { try { fs.mkdirSync(path.dirname(cfgPath()), { recursive: true }); fs.writeFileSync(cfgPath(), JSON.stringify(c, null, 2)); } catch { } }
const defaultSaveDir = () => path.join(app.getPath('downloads'), 'Выписки');
const saveDir = () => readCfg().saveDir || defaultSaveDir();
const describeDir = (d) => d === defaultSaveDir() ? 'Загрузки \\ Выписки' : d;

let win = null;
let sources = null;
let blocker = -1;

function uniquePath(dir, name) {
  const safe = String(name || 'file').replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_').slice(0, 150) || 'file';
  let p = path.join(dir, safe);
  if (!fs.existsSync(p)) return p;
  const ext = path.extname(safe), base = safe.slice(0, safe.length - ext.length);
  for (let i = 2; i < 1000; i++) { p = path.join(dir, `${base} (${i})${ext}`); if (!fs.existsSync(p)) return p; }
  return path.join(dir, `${base} ${Date.now()}${ext}`);
}

function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 860, minWidth: 900, minHeight: 600, show: false,
    title: 'Выписки', backgroundColor: '#f0f4f8',
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true, spellcheck: false },
  });
  win.once('ready-to-show', () => { win.maximize(); win.show(); });
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith(`app://${HOST}/`)) e.preventDefault(); });
  // the page asks before leaving while an import runs: ask the user instead of silently refusing to close
  win.webContents.on('will-prevent-unload', (e) => {
    const choice = dialog.showMessageBoxSync(win, {
      type: 'question', buttons: ['Закрыть', 'Остаться'], defaultId: 1, cancelId: 1, title: 'Выписки',
      message: 'Идёт загрузка приказов', detail: 'Если закрыть программу, загрузка прервётся. Уже сохранённые приказы останутся, продолжить можно будет позже.',
    });
    if (choice === 0) e.preventDefault();
  });
  // right-click menu: the usual cut / copy / paste
  win.webContents.on('context-menu', (_e, p) => {
    const items = [];
    if (p.isEditable) items.push({ label: 'Вырезать', role: 'cut', enabled: p.editFlags.canCut }, { label: 'Копировать', role: 'copy', enabled: p.editFlags.canCopy },
      { label: 'Вставить', role: 'paste', enabled: p.editFlags.canPaste }, { type: 'separator' }, { label: 'Выделить всё', role: 'selectAll' });
    else if (p.selectionText && p.selectionText.trim()) items.push({ label: 'Копировать', role: 'copy' }, { label: 'Выделить всё', role: 'selectAll' });
    if (items.length) Menu.buildFromTemplate(items).popup({ window: win });
  });
  win.loadURL(START_URL);
}

function buildMenu() {
  const tpl = [
    { label: 'Файл', submenu: [
      { label: 'Открыть папку с выписками', click: () => { const d = saveDir(); fs.mkdirSync(d, { recursive: true }); shell.openPath(d); } },
      { type: 'separator' },
      { label: 'Выход', role: 'quit' },
    ] },
    { label: 'Правка', submenu: [
      { label: 'Отменить', role: 'undo' }, { label: 'Повторить', role: 'redo' }, { type: 'separator' },
      { label: 'Вырезать', role: 'cut' }, { label: 'Копировать', role: 'copy' }, { label: 'Вставить', role: 'paste' }, { label: 'Выделить всё', role: 'selectAll' },
    ] },
    { label: 'Вид', submenu: [
      { label: 'Крупнее', role: 'zoomIn', accelerator: 'CmdOrCtrl+=' }, { label: 'Мельче', role: 'zoomOut' }, { label: 'Обычный размер', role: 'resetZoom' },
      { type: 'separator' }, { label: 'Во весь экран', role: 'togglefullscreen' },
    ] },
    { label: 'Справка', submenu: [
      { label: 'Папка с данными программы', click: () => shell.openPath(app.getPath('userData')) },
      { label: 'О программе', click: () => dialog.showMessageBox(win, { type: 'info', title: 'Выписки', message: `Выписки ${app.getVersion()}`,
        detail: 'Приказы, выписки и таблица приказов.\nРаботает без интернета: документы не покидают компьютер.\nДанные хранятся в профиле пользователя Windows.' }) },
    ] },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(tpl));
}

app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });

app.whenReady().then(() => {
  if (process.platform === 'win32') app.setAppUserModelId('ru.vypiski.desktop');
  const ses = session.defaultSession;
  // no spell-check dictionaries are downloaded (that would be the only outgoing connection)
  ses.setSpellCheckerEnabled(false);
  ses.setSpellCheckerLanguages([]);
  ses.setSpellCheckerDictionaryDownloadURL('https://localhost.invalid/');
  // the app's own files only
  protocol.handle('app', (req) => {
    const u = new URL(req.url);
    if (u.host !== HOST) return new Response('', { status: 403 });
    let rel = decodeURIComponent(u.pathname);
    if (rel === '/' || rel === '') rel = '/index.html';
    const file = path.normalize(path.join(APP_DIR, rel));
    if (!file.startsWith(APP_DIR + path.sep)) return new Response('', { status: 403 });
    return net.fetch(pathToFileURL(file).toString());
  });
  // no network at all
  ses.webRequest.onBeforeRequest({ urls: ['http://*/*', 'https://*/*', 'ws://*/*', 'wss://*/*', 'ftp://*/*'] }, (_d, cb) => cb({ cancel: true }));
  ses.setPermissionRequestHandler((_wc, perm, cb) => cb(perm === 'clipboard-sanitized-write'));
  ses.setPermissionCheckHandler((_wc, perm) => perm === 'clipboard-sanitized-write');
  // downloads (DOCX, Excel, backups) go straight to the save folder; the page shows «Сохранено» with «Показать в папке»
  ses.on('will-download', (_e, item) => {
    const dir = saveDir();
    try { fs.mkdirSync(dir, { recursive: true }); } catch { }
    const asked = dlNames.get(item.getURL()); dlNames.delete(item.getURL());
    const full = uniquePath(dir, asked || item.getFilename());
    item.setSavePath(full);
    item.once('done', (_ev, state) => {
      if (win && !win.isDestroyed()) win.webContents.send('file:saved', { ok: state === 'completed', path: full, name: path.basename(full), where: describeDir(dir) + ' \\ ' + path.basename(full) });
    });
  });

  ipcMain.on('app:version', (e) => { e.returnValue = app.getVersion(); });
  ipcMain.on('dl:name', (e, url, name) => { if (typeof url === 'string' && typeof name === 'string') { dlNames.set(url, name); if (dlNames.size > 50) dlNames.delete(dlNames.keys().next().value); } e.returnValue = true; });
  ipcMain.handle('file:show', (_e, p) => { if (typeof p === 'string' && p.startsWith(saveDir())) shell.showItemInFolder(p); });
  ipcMain.handle('folder:open', () => { const d = saveDir(); fs.mkdirSync(d, { recursive: true }); return shell.openPath(d); });
  ipcMain.handle('folder:get', () => describeDir(saveDir()));
  ipcMain.handle('folder:choose', async () => {
    const r = await dialog.showOpenDialog(win, { title: 'Куда сохранять выписки и копии', properties: ['openDirectory', 'createDirectory'], defaultPath: saveDir() });
    if (r.canceled || !r.filePaths[0]) return { cancelled: true };
    const c = readCfg(); c.saveDir = r.filePaths[0]; writeCfg(c);
    return { ok: true, where: describeDir(c.saveDir) };
  });
  ipcMain.handle('folder:reset', () => { const c = readCfg(); delete c.saveDir; writeCfg(c); return describeDir(defaultSaveDir()); });
  // 12.77 print center: list of printers, printing without the system dialog to a chosen printer, PDF into the save folder.
  // The page sends ready HTML; it is written to a private temp file, printed by a hidden window with scripts disabled, then deleted.
  ipcMain.handle('print:printers', async () => { try { return (await win.webContents.getPrintersAsync()).map((p) => ({ name: p.name, displayName: p.displayName, isDefault: !!p.isDefault })); } catch { return []; } });
  const withPrintWindow = async (html, fn) => {
    if (typeof html !== 'string' || html.length > 80 * 1024 * 1024) throw new Error('bad document');
    const dir = path.join(app.getPath('temp'), 'vypiski-print'); fs.mkdirSync(dir, { recursive: true });
    const file = path.join(dir, `p-${process.pid}-${Date.now()}-${Math.random().toString(36).slice(2)}.html`);
    fs.writeFileSync(file, html, 'utf8');
    const w = new BrowserWindow({ show: false, webPreferences: { javascript: false, sandbox: true, contextIsolation: true, nodeIntegration: false, spellcheck: false } });
    try { await w.loadFile(file); return await fn(w); } finally { if (!w.isDestroyed()) w.destroy(); fs.rm(file, { force: true }, () => {}); }
  };
  ipcMain.handle('print:html', async (_e, html, o = {}) => {
    try {
      return await withPrintWindow(html, (w) => new Promise((res) => w.webContents.print({
        silent: !o.dialog && typeof o.deviceName === 'string' && !!o.deviceName, deviceName: o.dialog ? undefined : o.deviceName || undefined,
        copies: Math.max(1, Math.min(20, o.copies | 0 || 1)), landscape: !!o.landscape, printBackground: true, margins: { marginType: 'none' }, pageSize: 'A4',
      }, (ok, reason) => res({ ok, reason: ok ? '' : String(reason || 'cancelled') }))));
    } catch (e) { return { ok: false, reason: e.message }; }
  });
  ipcMain.handle('print:pdf', async (_e, html, name, landscape) => {
    try {
      const data = await withPrintWindow(html, (w) => w.webContents.printToPDF({ landscape: !!landscape, printBackground: true, pageSize: 'A4', margins: { marginType: 'none' }, preferCSSPageSize: true, generateDocumentOutline: false }));
      const dir = saveDir(); fs.mkdirSync(dir, { recursive: true });
      const full = uniquePath(dir, String(name || 'Выписки.pdf'));
      const tmp = full + '.part'; fs.writeFileSync(tmp, data); fs.renameSync(tmp, full); // atomic: no half-written PDF under the final name
      return { ok: true, path: full, where: describeDir(dir) + ' \\ ' + path.basename(full) };
    } catch (e) { return { ok: false, reason: e.message }; }
  });
  // 12.77 «живые папки»: локальные и сетевые папки с приказами отслеживаются, пока программа открыта (только чтение)
  sources = new Sources({ dir: path.join(app.getPath('userData'), 'sources'), onEvent: (e) => { if (win && !win.isDestroyed()) win.webContents.send('sources:event', e); } });
  ipcMain.handle('sources:list', () => sources.describe());
  ipcMain.handle('sources:add', async (_e, p, label) => {
    try {
      if (!p) { const r = await dialog.showOpenDialog(win, { title: 'Папка с приказами (только чтение)', properties: ['openDirectory'] }); if (r.canceled || !r.filePaths[0]) return { cancelled: true }; p = r.filePaths[0]; }
      const s = await sources.add(String(p), label); return { ok: true, id: s.id };
    } catch (e) { return { ok: false, reason: e.code === 'ENOENT' ? 'папка не найдена или сеть недоступна' : e.message }; }
  });
  ipcMain.handle('sources:remove', (_e, id) => { sources.remove(String(id)); return true; });
  ipcMain.handle('sources:mode', (_e, id, m) => { sources.setMode(String(id), String(m)); return true; });
  ipcMain.handle('sources:interval', (_e, id, sec) => { sources.setInterval(String(id), +sec); return true; });
  ipcMain.handle('sources:scan', (_e, id) => sources.scanNow(String(id), 'вручную'));
  ipcMain.handle('sources:read', async (_e, id, rel) => { const b = await sources.read(String(id), String(rel)); return new Uint8Array(b.buffer, b.byteOffset, b.byteLength); });
  // long imports: keep the PC awake and show progress on the taskbar button
  ipcMain.on('import:begin', () => { if (blocker < 0) blocker = powerSaveBlocker.start('prevent-app-suspension'); if (win) win.setProgressBar(0.001); });
  ipcMain.on('import:progress', (_e, done, total) => { if (win && total > 0) win.setProgressBar(Math.max(0.001, Math.min(1, done / total))); });
  ipcMain.on('import:end', (_e, text) => {
    if (blocker >= 0) { powerSaveBlocker.stop(blocker); blocker = -1; }
    if (!win) return;
    win.setProgressBar(-1);
    if (!win.isFocused()) {
      win.flashFrame(true);
      if (Notification.isSupported()) { const n = new Notification({ title: 'Выписки', body: String(text || 'Загрузка приказов завершена') }); n.on('click', () => { win.show(); win.focus(); }); n.show(); }
    }
  });

  buildMenu();
  createWindow();
  win.webContents.once('did-finish-load', () => sources.start());
});

app.on('before-quit', () => { try { sources?.stop(); } catch { } });

app.on('window-all-closed', () => app.quit());
