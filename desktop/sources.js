// Выписки — «живые папки» для Windows-версии (12.77).
// Пока программа открыта, папки с приказами (локальные D:\Приказы и сетевые \\СЕРВЕР\Приказы) отслеживаются:
// fs.watch (события могут теряться или дублироваться, особенно по SMB) + полная сверка по расписанию.
// Модуль только ЧИТАЕТ папки: ничего не создаёт, не переименовывает и не удаляет в источниках.
// Новые и изменённые файлы передаются странице после проверки стабильности (размер и время изменения не меняются
// между двумя проверками) — частично скопированный файл не попадает в загрузку. Исчезновение файла или недоступность
// сети НЕ удаляет ничего из базы: источник помечается «недоступен», сверка повторяется позже.
// Когда программа закрыта, папки не отслеживаются (фоновой службы нет).
'use strict';
const fs = require('node:fs');
const fsp = fs.promises;
const path = require('node:path');
const crypto = require('node:crypto');

const DOC = /\.(docx|docm|doc|dot|dotx|rtf|zip)$/i;
const SKIP = /^~\$|^\.~lock|\.(tmp|part|crdownload|partial|download)$|^(thumbs\.db|desktop\.ini)$/i;
const MAX_FILES = 100000, MAX_DEPTH = 32, MAX_READ = 100 * 1024 * 1024;
const INTERVALS = [30, 60, 300, 900];

const isNetwork = (p) => /^\\\\[^\\]+\\[^\\]+/.test(p) || /^\/\/[^/]+\/[^/]+/.test(p);

/** Walks a source folder. Symbolic links and junctions are not followed (no cycles, no escape from the folder). */
async function scan(root, { maxFiles = MAX_FILES, maxDepth = MAX_DEPTH } = {}) {
  const st = await fsp.stat(root); // throws if the folder or the network share is unavailable
  if (!st.isDirectory()) throw Object.assign(new Error('не папка'), { code: 'ENOTDIR' });
  const files = new Map(); const errors = []; let links = 0, skipped = 0;
  const stack = [['', 0]];
  while (stack.length) {
    const [rel, depth] = stack.pop();
    let entries;
    try { entries = await fsp.readdir(path.join(root, rel), { withFileTypes: true }); }
    catch (e) { errors.push({ rel, code: e.code || 'ERR' }); continue; }
    for (const d of entries) {
      const r = rel ? rel + '/' + d.name : d.name;
      if (d.isSymbolicLink()) { links++; continue; }
      if (d.isDirectory()) { if (depth + 1 <= maxDepth) stack.push([r, depth + 1]); else skipped++; continue; }
      if (!d.isFile()) continue;
      if (SKIP.test(d.name) || !DOC.test(d.name)) { skipped++; continue; }
      try {
        const s = await fsp.lstat(path.join(root, r));
        if (s.isSymbolicLink()) { links++; continue; }
        files.set(r, { size: s.size, mtimeMs: Math.round(s.mtimeMs) });
      } catch (e) { errors.push({ rel: r, code: e.code || 'ERR' }); }
      if (files.size >= maxFiles) { errors.push({ rel: '', code: 'LIMIT' }); return { files, errors, links, skipped, truncated: true }; }
    }
  }
  return { files, errors, links, skipped, truncated: false };
}

/** Compares two scans by path, size and modification time (no hashing: the page skips known files itself). */
function diff(prev, cur) {
  const added = [], changed = [], missing = []; let same = 0;
  for (const [rel, f] of cur) { const p = prev.get(rel); if (!p) added.push(rel); else if (p.size !== f.size || p.mtimeMs !== f.mtimeMs) changed.push(rel); else same++; }
  for (const rel of prev.keys()) if (!cur.has(rel)) missing.push(rel);
  return { added, changed, missing, same };
}

/** Reads one file of a source, only inside the source folder (no «..», no links pointing outside). */
async function readInside(root, rel) {
  if (typeof rel !== 'string' || !rel || rel.includes('\0')) throw new Error('bad path');
  const parts = rel.split(/[\\/]+/);
  if (parts.some((p) => p === '..' || p === '' )) throw new Error('bad path');
  const full = path.resolve(root, ...parts);
  const base = path.resolve(root);
  if (!full.startsWith(base + path.sep)) throw new Error('outside of the source');
  const ls = await fsp.lstat(full);
  if (ls.isSymbolicLink() || !ls.isFile()) throw new Error('not a regular file');
  const [rf, rb] = await Promise.all([fsp.realpath(full), fsp.realpath(base)]);
  if (!rf.startsWith(rb + path.sep)) throw new Error('outside of the source');
  if (ls.size > MAX_READ) throw new Error('file too large');
  return fsp.readFile(full); // read-only
}

class Sources {
  /** @param {{dir:string, onEvent:(e:object)=>void, now?:()=>number, settleMs?:number}} o */
  constructor(o) {
    this.dir = o.dir; this.onEvent = o.onEvent || (() => {}); this.now = o.now || Date.now; this.settleMs = o.settleMs ?? 2000;
    this.list = []; this.state = new Map(); this.timers = new Map(); this.watchers = new Map(); this.busy = new Set();
    fs.mkdirSync(this.dir, { recursive: true });
    try { this.list = JSON.parse(fs.readFileSync(path.join(this.dir, 'sources.json'), 'utf8')); } catch { this.list = []; }
  }
  saveList() { const p = path.join(this.dir, 'sources.json'); fs.writeFileSync(p + '.tmp', JSON.stringify(this.list, null, 1)); fs.renameSync(p + '.tmp', p); }
  known(id) { try { return new Map(Object.entries(JSON.parse(fs.readFileSync(path.join(this.dir, `files-${id}.json`), 'utf8')))); } catch { return new Map(); } }
  saveKnown(id, m) { const p = path.join(this.dir, `files-${id}.json`); fs.writeFileSync(p + '.tmp', JSON.stringify(Object.fromEntries(m))); fs.renameSync(p + '.tmp', p); }
  get(id) { return this.list.find((s) => s.id === id); }
  describe() {
    return this.list.map((s) => ({ ...s, network: isNetwork(s.path), ...(this.state.get(s.id) || { state: s.mode === 'pause' ? 'paused' : 'new' }) }));
  }
  async add(dirPath, label) {
    if (typeof dirPath !== 'string' || !path.isAbsolute(dirPath) && !isNetwork(dirPath)) throw new Error('укажите полный путь к папке');
    const norm = path.resolve(dirPath);
    if (this.list.some((s) => path.resolve(s.path).toLowerCase() === norm.toLowerCase())) throw new Error('эта папка уже добавлена');
    const st = await fsp.stat(norm); if (!st.isDirectory()) throw new Error('это не папка');
    const s = { id: crypto.randomUUID(), path: norm, label: (label || path.basename(norm) || norm).slice(0, 60), mode: 'auto', interval: 300, added: new Date(this.now()).toISOString() };
    this.list.push(s); this.saveList(); this.arm(s); await this.scanNow(s.id, 'добавлена');
    return s;
  }
  remove(id) { // only the link to the folder is removed: files and the database stay untouched
    this.disarm(id); this.list = this.list.filter((s) => s.id !== id); this.saveList(); this.state.delete(id);
    try { fs.rmSync(path.join(this.dir, `files-${id}.json`), { force: true }); } catch { }
  }
  setMode(id, mode) { const s = this.get(id); if (!s || !['auto', 'manual', 'pause'].includes(mode)) return; s.mode = mode; this.saveList(); this.disarm(id); this.arm(s); this.patch(id, { state: mode === 'pause' ? 'paused' : (this.state.get(id)?.state === 'paused' ? 'ok' : this.state.get(id)?.state || 'ok') }); }
  setInterval(id, sec) { const s = this.get(id); if (!s || !INTERVALS.includes(+sec)) return; s.interval = +sec; this.saveList(); this.disarm(id); this.arm(s); }
  patch(id, p) { const cur = { ...(this.state.get(id) || {}), ...p }; this.state.set(id, cur); this.onEvent({ type: 'state', id, state: cur }); }
  start() { for (const s of this.list) { this.arm(s); if (s.mode !== 'pause') this.scanNow(s.id, 'запуск программы').catch(() => {}); } }
  stop() { for (const s of this.list) this.disarm(s.id); }
  arm(s) {
    if (s.mode !== 'auto') return;
    const t = setInterval(() => this.scanNow(s.id, 'сверка по расписанию').catch(() => {}), s.interval * 1000); t.unref?.(); this.timers.set(s.id, t);
    try {
      let deb = null;
      const w = fs.watch(s.path, { recursive: true, persistent: false }, () => { clearTimeout(deb); deb = setTimeout(() => this.scanNow(s.id, 'изменение в папке').catch(() => {}), 2000); });
      w.on('error', () => { try { w.close(); } catch { } this.watchers.delete(s.id); });
      this.watchers.set(s.id, w);
    } catch { /* no watcher (e.g. some network shares): the periodic reconciliation still works */ }
  }
  disarm(id) { clearInterval(this.timers.get(id)); this.timers.delete(id); try { this.watchers.get(id)?.close(); } catch { } this.watchers.delete(id); clearTimeout(this.retry?.[id]); }
  /** One reconciliation. Emits {type:'changes', ready:[{rel,size,mtimeMs}], missing:[rel]} when something is ready. */
  async scanNow(id, why = 'вручную') {
    const s = this.get(id); if (!s) return null;
    if (s.mode === 'pause' && why !== 'вручную') return null;
    if (this.busy.has(id)) return null; this.busy.add(id);
    this.patch(id, { state: 'scanning', why });
    try {
      const r = await scan(s.path);
      const prev = this.known(id); const pend = (this.pending ||= new Map()).get(id) || new Map();
      const d = diff(prev, r.files); const ready = [], waiting = [];
      for (const rel of [...d.added, ...d.changed]) {
        const f = r.files.get(rel); const p = pend.get(rel);
        const fresh = this.now() - f.mtimeMs < this.settleMs;
        if ((p && p.size === f.size && p.mtimeMs === f.mtimeMs) || (!fresh && !p)) ready.push({ rel, ...f }); else waiting.push(rel);
      }
      const nextPend = new Map(); for (const rel of waiting) nextPend.set(rel, r.files.get(rel)); this.pending.set(id, nextPend);
      // remember only what was handed over; files still being copied are compared again next time
      const known = new Map(prev); for (const x of ready) known.set(x.rel, { size: x.size, mtimeMs: x.mtimeMs });
      for (const rel of d.missing) known.delete(rel);
      this.saveKnown(id, known);
      const st = { state: s.mode === 'pause' ? 'paused' : 'ok', lastScanAt: new Date(this.now()).toISOString(), lastSuccessAt: new Date(this.now()).toISOString(), lastError: '', fails: 0,
        total: r.files.size, added: d.added.length, changed: d.changed.length, missing: (this.state.get(id)?.missingTotal || 0) + d.missing.length, missingTotal: (this.state.get(id)?.missingTotal || 0) + d.missing.length,
        waiting: waiting.length, errors: r.errors.length, links: r.links, truncated: r.truncated };
      this.patch(id, st);
      if (ready.length || d.missing.length) this.onEvent({ type: 'changes', id, label: s.label, ready, missing: d.missing, why });
      if (waiting.length) { const t = setTimeout(() => this.scanNow(id, 'проверка стабильности').catch(() => {}), this.settleMs + 500); t.unref?.(); }
      return { ...st, ready, missingList: d.missing, waiting };
    } catch (e) {
      const fails = (this.state.get(id)?.fails || 0) + 1;
      const delay = Math.min(15 * 60, 30 * 2 ** Math.min(fails - 1, 5));
      this.patch(id, { state: 'unavailable', lastScanAt: new Date(this.now()).toISOString(), lastError: e.code || e.message, fails, retryIn: delay });
      this.onEvent({ type: 'unavailable', id, label: s.label, code: e.code || 'ERR' });
      (this.retry ||= {})[id] = setTimeout(() => this.scanNow(id, 'повтор после недоступности').catch(() => {}), delay * 1000); this.retry[id].unref?.();
      return { state: 'unavailable', error: e.code || e.message };
    } finally { this.busy.delete(id); }
  }
  async read(id, rel) { const s = this.get(id); if (!s) throw new Error('нет источника'); return readInside(s.path, rel); }
}

module.exports = { Sources, scan, diff, readInside, isNetwork, INTERVALS };
