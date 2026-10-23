"""CER/WER и время OCR на testdata/ocr для «Быстрого» (tessdata_fast, в APK) и «Высокой точности»
(tessdata_best, офлайн-пакет). Вход OCR — страница после обработки приложения (перспектива по эталонным
углам + фильтр; Python-копия PageRenderer из tools/processing-lab).

Запуск: python3 measure.py <папка tessdata_fast> <папка tessdata_best> > ../../reports/ocr-metrics.md
Использует tesseract CLI (на устройстве — Tesseract4Android с теми же моделями; версии движка могут отличаться,
см. отчёт).
"""
import cv2, numpy as np, os, sys, glob, subprocess, time, tempfile, platform
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "processing-lab"))
import enhance as E, hybrid as H

D = os.path.join(os.path.dirname(__file__), "..", "..", "testdata", "ocr")

def lev(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]

def norm(s): return " ".join(s.replace("ё", "е").replace("Ё", "Е").split())

def cer(ref, hyp): r = norm(ref); return lev(r, norm(hyp)) / max(1, len(r))
def wer(ref, hyp): r = norm(ref).split(); return lev(r, norm(hyp).split()) / max(1, len(r))

def ocr(img, tessdata, psm=4):
    with tempfile.NamedTemporaryFile(suffix=".png") as f:
        cv2.imwrite(f.name, img)
        t = time.time()
        out = subprocess.run(["tesseract", f.name, "stdout", "--tessdata-dir", tessdata, "-l", "rus+eng", "--psm", str(psm)],
                             capture_output=True, text=True).stdout
        return out, time.time() - t

def prep(img, q, how):
    if how == "photo": return img
    if how.startswith("HYBRID"): return H.render(img, q, "DOCUMENT")
    return E.render(img, q, how)

if __name__ == "__main__":
    fast, best = sys.argv[1], sys.argv[2]
    files = sorted(glob.glob(os.path.join(D, "*.jpg")))
    rows = []
    for how in ("photo", "ORIGINAL", "TEXT_BOOST", "HYBRID", "BW"):
        for mode, td in (("Быстрый", fast), ("Высокая точность", best)):
            if how in ("photo", "ORIGINAL", "BW") and mode == "Высокая точность": continue
            cs, ws, ts, per = [], [], [], []
            for f in files:
                img = cv2.imread(f); gt = open(f[:-4] + ".gt.txt", encoding="utf-8").read()
                q = np.float32([[float(v) for v in p.split(",")] for p in open(f[:-4] + ".quad.txt").read().strip().split(";")])
                text, t = ocr(prep(img, q, how), td)
                cs.append(cer(gt, text)); ws.append(wer(gt, text)); ts.append(t); per.append((os.path.basename(f), cs[-1]))
            rows.append((how, mode, np.mean(cs), max(cs), np.mean(ws), np.mean(ts), per))
            print(f"{how:10s} {mode:17s} CER {100*np.mean(cs):5.2f}% (худш {100*max(cs):5.2f}%)  WER {100*np.mean(ws):5.2f}%  {np.mean(ts):.2f} с", file=sys.stderr, flush=True)
    names = {"photo": "Снимок без обработки", "ORIGINAL": "Только перспектива", "TEXT_BOOST": "Усиленный текст", "HYBRID": "Hybrid HD «Документ»", "BW": "Чёрно-белый"}
    print("# Метрики OCR\n")
    print(f"Набор: `testdata/ocr`, {len(files)} синтетических обезличенных документов (кириллица, шрифты DejaVu; "
          "перспектива, тени, тусклый свет, размытие, шум, JPEG 88). Генератор: `tools/ocr-lab/make_testdata.py`.\n")
    v = subprocess.run(["tesseract", "--version"], capture_output=True, text=True).stdout.splitlines()[0]
    print(f"Движок: {v} (CLI, {platform.machine()}), `--psm 4`, языки rus+eng. Модели: `tessdata_fast` (в APK, SHA-256 — "
          "`core/ocr/src/main/assets/tessdata.sha256`) и `tessdata_best` (офлайн-пакет). На телефоне — Tesseract4Android 4.9.0 "
          "с теми же моделями; время на телефоне будет другим и измеряется отдельно.\n")
    print("CER/WER — расстояние Левенштейна по символам/словам после нормализации пробелов и «ё→е».\n")
    print("| Вход OCR | Модель | CER | CER (худший) | WER | Время/стр. |\n|---|---|---|---|---|---|")
    for how, mode, c, cm, w, t, _ in rows:
        print(f"| {names[how]} | {mode} | {100*c:.2f} % | {100*cm:.2f} % | {100*w:.2f} % | {t:.2f} с |")
    print("\nПо страницам (CER):\n")
    print("| Страница | " + " | ".join(f"{names[r[0]]} / {r[1]}" for r in rows) + " |")
    print("|---|" + "---|" * len(rows))
    for i in range(len(files)):
        print(f"| {rows[0][6][i][0]} | " + " | ".join(f"{100*r[6][i][1]:.1f} %" for r in rows) + " |")
