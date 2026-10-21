// node tests/unit/sources.test.cjs — «живые папки» Windows-версии (desktop/sources.js), без Electron.
'use strict';
const fs = require('node:fs'), path = require('node:path'), os = require('node:os'), crypto = require('node:crypto');
const { Sources, scan, diff, readInside, isNetwork } = require('../../desktop/sources.js');
let pass = 0, fail = 0;
const check = (name, ok, d = '') => { ok ? pass++ : fail++; console.log((ok ? 'PASS ' : 'FAIL ') + name + (d ? ' — ' + d : '')); };
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'vyp-src-'));
const root = path.join(tmp, 'Приказы'); const outside = path.join(tmp, 'чужая');
const w = (rel, data = 'x', ago = 60000) => { const p = path.join(root, rel); fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, data); const t = new Date(Date.now() - ago); fs.utimesSync(p, t, t); return p; };
const sha = (p) => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
(async () => {
  w('2024/Январь/Приказ № 1.docx', 'a'); w('2024/Январь/~$Приказ № 1.docx', 'lock'); w('2025/Март/Приказ № 50.docx', 'b'); w('2025/Март/архив.zip', 'z');
  w('2025/Март/заметка.txt', 't'); w('2025/Март/загрузка.docx.part', 'p'); w('2025/Март/Приказ 51.tmp', 'p');
  fs.mkdirSync(outside); fs.writeFileSync(path.join(outside, 'секрет.docx'), 's');
  let linkOk = true; try { fs.symlinkSync(root, path.join(root, '2025', 'петля'), 'dir'); fs.symlinkSync(path.join(outside, 'секрет.docx'), path.join(root, 'ссылка.docx')); } catch { linkOk = false; }
  const before = Object.fromEntries(['2024/Январь/Приказ № 1.docx', '2025/Март/Приказ № 50.docx'].map((r) => [r, sha(path.join(root, r))]));

  const r = await scan(root);
  const names = [...r.files.keys()].sort();
  check('Вложенные годы/месяцы, кириллица; только DOC/DOCX/RTF/ZIP', JSON.stringify(names) === JSON.stringify(['2024/Январь/Приказ № 1.docx', '2025/Март/Приказ № 50.docx', '2025/Март/архив.zip']), names.join(' | '));
  check('Временные ~$, .tmp, .part пропущены', !names.some((n) => /~\$|\.tmp|\.part/.test(n)));
  if (linkOk) check('Ссылки и петли не обходятся', r.links >= 2 && !names.some((n) => n.includes('петля') || n.includes('ссылка')), 'links=' + r.links);

  check('diff: новые / изменённые / пропавшие', JSON.stringify(diff(new Map([['a', { size: 1, mtimeMs: 1 }], ['b', { size: 1, mtimeMs: 1 }]]), new Map([['a', { size: 1, mtimeMs: 2 }], ['c', { size: 1, mtimeMs: 1 }]]))) === JSON.stringify({ added: ['c'], changed: ['a'], missing: ['b'], same: 0 }));

  // reading stays inside the source
  const ok = await readInside(root, '2025/Март/Приказ № 50.docx').then((b) => b.toString() === 'b', () => false);
  check('Чтение файла источника', ok);
  for (const bad of ['../чужая/секрет.docx', '2025/../../чужая/секрет.docx', '/etc/passwd', 'ссылка.docx', '']) {
    const res = await readInside(root, bad).then(() => 'read', (e) => e.message);
    check(`Отказ: «${bad}»`, res !== 'read', res);
  }

  // manager: first scan, new file, file being copied, missing file, unavailable folder
  const events = []; const dir = path.join(tmp, 'state');
  const S = new Sources({ dir, onEvent: (e) => events.push(e), settleMs: 1500 });
  const s = await S.add(root, 'Приказы');
  let ch = events.filter((e) => e.type === 'changes');
  check('Первое сканирование передаёт все приказы', ch.length === 1 && ch[0].ready.length === 3, JSON.stringify(ch.map((e) => e.ready.length)));
  check('Тип источника: локальная / сетевая', !isNetwork(root) && isNetwork('\\\\SERVER\\Приказы') && isNetwork('//server/share'));
  events.length = 0;
  let x = await S.scanNow(s.id); check('Повторная сверка без изменений — ничего не передаёт', x.ready.length === 0 && !events.some((e) => e.type === 'changes'));
  w('2025/Апрель/Приказ № 60.docx', 'new');
  x = await S.scanNow(s.id); check('Новый файл появляется без ручного импорта', x.ready.map((f) => f.rel).join() === '2025/Апрель/Приказ № 60.docx', JSON.stringify(x.ready));
  const partial = w('2025/Апрель/Приказ № 61.docx', 'part1', 0);
  x = await S.scanNow(s.id); check('Файл, который ещё копируется, ждёт стабилизации', x.ready.length === 0 && x.waiting.includes('2025/Апрель/Приказ № 61.docx'), JSON.stringify(x.waiting));
  fs.appendFileSync(partial, 'part2'); x = await S.scanNow(s.id); check('Размер изменился — снова ждём', x.ready.length === 0);
  await new Promise((r) => setTimeout(r, 1700)); x = await S.scanNow(s.id); check('Файл стабилен — передан один раз', x.ready.map((f) => f.rel).join() === '2025/Апрель/Приказ № 61.docx', JSON.stringify(x.ready));
  fs.rmSync(path.join(root, '2024/Январь/Приказ № 1.docx'));
  x = await S.scanNow(s.id); check('Удалённый файл — только отметка «пропал», без удаления данных', x.missingList.join() === '2024/Январь/Приказ № 1.docx' && x.ready.length === 0);
  // network loss: the folder disappears, nothing is reported as deleted
  const moved = root + '-offline'; fs.renameSync(root, moved); events.length = 0;
  x = await S.scanNow(s.id);
  check('Папка/сеть недоступна: состояние «недоступен», без списка «пропавших»', x.state === 'unavailable' && !events.some((e) => e.type === 'changes'), JSON.stringify(x));
  check('Повтор с увеличивающейся паузой', S.describe()[0].retryIn >= 30);
  fs.renameSync(moved, root); events.length = 0;
  x = await S.scanNow(s.id);
  check('Сеть вернулась: сверка без дублей', x.state === 'ok' && x.ready.length === 0, JSON.stringify(x.ready));
  // restart keeps the known list
  S.stop(); const S2 = new Sources({ dir, onEvent: () => {}, settleMs: 1500 });
  x = await S2.scanNow(s.id); check('После перезапуска программы — без повторной передачи', x.ready.length === 0 && S2.describe().length === 1);
  S2.setMode(s.id, 'pause'); check('Пауза', S2.describe()[0].mode === 'pause' && (await S2.scanNow(s.id, 'сверка по расписанию')) === null);
  S2.remove(s.id); check('Удаление источника не трогает файлы', fs.existsSync(path.join(root, '2025/Март/Приказ № 50.docx')) && S2.describe().length === 0);
  check('Исходные файлы не изменились (SHA-256)', sha(path.join(root, '2025/Март/Приказ № 50.docx')) === before['2025/Март/Приказ № 50.docx']);
  S2.stop();
  fs.rmSync(tmp, { recursive: true, force: true });
  console.log(`${pass}/${pass + fail}`); process.exit(fail ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
