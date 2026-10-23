"""«Документ» v2 (по запросу 2026-10-23): чистый электронный вид. Набор: 12 страниц с реальными шрифтами
(кириллица), синей печатью и подписью, перспективой, тенями, СГИБАМИ и зерном бумаги. Метрики: фон (среднее,
разброс по клеткам листа), сохранность печати, CER распознавания (tesseract, модели из APK)."""
import cv2, numpy as np, random, sys, os, subprocess, tempfile, time, math, json
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "ocr-lab"))
import enhance as E, hybrid as H, pages
import make_testdata as MT, measure as M

TESS = os.path.join(os.path.dirname(__file__), "..", "..", "core", "ocr", "src", "main", "assets", "tessdata")

def folds(rnd, w, h):
    """Сгибы листа: тёмная полоса с бликом рядом (как после складывания вчетверо) + зерно бумаги."""
    f = np.ones((h, w), np.float32)
    for vertical, pos in ((True, rnd.uniform(0.45, 0.55)), (False, rnd.uniform(0.3, 0.36)), (False, rnd.uniform(0.63, 0.7))):
        n = w if vertical else h; c = int(pos * n)
        idx = np.arange(n, dtype=np.float32)
        prof = 1 - rnd.uniform(0.10, 0.22) * np.exp(-((idx - c) / rnd.uniform(2, 5)) ** 2) + 0.05 * np.exp(-((idx - c - 8) / 6) ** 2)
        f *= prof[None, :] if vertical else prof[:, None]
    grain = cv2.GaussianBlur(np.random.default_rng(rnd.randint(0, 1 << 30)).normal(0, 0.035, (h, w)).astype(np.float32), (0, 0), 1.2)
    return f * (1 + grain)

def case(rnd, i):
    lines = MT.doc_text(rnd)
    clean = MT.render_page(rnd, lines)
    marked, sm, gm = H.add_marks(clean, rnd)
    paper = (marked.astype(np.float32) * folds(rnd, marked.shape[1], marked.shape[0])[..., None]).clip(0, 255).astype(np.uint8)
    kind = ["plain", "gradient", "shadow", "hard", "dim", "gradient"][i % 6]
    img, q = MT.photo(rnd, paper, kind)
    return img, q, marked, sm, gm, "\n".join(l for l in lines if l), kind

def bg_stats(out, clean):
    g = out if out.ndim == 2 else cv2.cvtColor(out, cv2.COLOR_BGR2GRAY)
    g = cv2.resize(g, (clean.shape[1], clean.shape[0]), interpolation=cv2.INTER_AREA)
    cg = cv2.cvtColor(clean, cv2.COLOR_BGR2GRAY)
    bgm = cv2.erode((cg > 250).astype(np.uint8), np.ones((9, 9), np.uint8)).astype(bool)
    H_, W_ = bgm.shape; m = np.zeros_like(bgm); m[int(H_*.03):int(H_*.97), int(W_*.03):int(W_*.97)] = True; bgm &= m
    v = g[bgm].astype(np.float32)
    # разброс по листу: средние по клеткам 8×8
    cells = [g[y:y+H_//8, x:x+W_//8][bgm[y:y+H_//8, x:x+W_//8]].mean() for y in range(0, H_ - H_//8 + 1, H_//8) for x in range(0, W_ - W_//8 + 1, W_//8) if bgm[y:y+H_//8, x:x+W_//8].sum() > 50]
    return float(v.mean()), float(np.std(cells)), float(np.percentile(v, 1))

def ocr_cer(img, gt):
    text, t = M.ocr(img, TESS); return M.cer(gt, text)

def run(name, fn, n=12, seed=2610):
    rnd = random.Random(seed); np.random.seed(seed)
    rows = []
    for i in range(n):
        img, q, marked, sm, gm, gt, kind = case(rnd, i)
        t0 = time.time(); out = fn(img, q); dt = time.time() - t0
        if out.ndim == 2: out = cv2.cvtColor(out, cv2.COLOR_GRAY2BGR)
        base = E.render(img, q, "COLOR", dn=False)
        mean, cellstd, p1 = bg_stats(out, marked)
        rows.append(dict(bg=mean, cell=cellstd, p1=p1, stamp=H.preserved(out, sm, base), sign=H.preserved(out, gm, base), cer=ocr_cer(out, gt), ms=dt * 1000))
    a = {k: float(np.mean([r[k] for r in rows])) for k in rows[0]}
    w = {k: float(min(r[k] for r in rows)) for k in ("bg", "stamp", "sign")}; wc = float(max(r["cer"] for r in rows))
    print(f"{name:26s} фон {a['bg']:5.1f} (мин {w['bg']:5.1f}) разброс клеток {a['cell']:4.1f} p1 {a['p1']:5.1f} | печать {a['stamp']:.3f} (мин {w['stamp']:.3f}) подпись {a['sign']:.3f} | CER {100*a['cer']:5.2f}% (худш {100*wc:5.2f}%) | {a['ms']:4.0f} мс", flush=True)
    return rows

if __name__ == "__main__":
    A4 = 210 / 297
    S = os.environ.get("OUT", "/tmp")
    if "all" in sys.argv:
        run("снимок (без обработки)", lambda i, q: i)
        run("только перспектива", lambda i, q: E.warp(i, q, A4))
        run("«Документ» v1 (было)", lambda i, q: H.render(i, q, "DOCUMENT_V1", aspect=A4))
        run("«Документ» v2 (стало)", lambda i, q: H.render(i, q, "DOCUMENT", aspect=A4))
    run("«Ч/Б» (с уровнями сканера)", lambda i, q: E.render(i, q, "BW", aspect=A4))
    run("«Серый»", lambda i, q: E.render(i, q, "GRAY", aspect=A4))
    run("«Цветной»", lambda i, q: E.render(i, q, "COLOR", aspect=A4))
    if "img" not in sys.argv: sys.exit(0)
    # сравнения «до/после» для отчёта
    rnd = random.Random(2610); np.random.seed(2610)
    for i in range(12):
        img, q, *_ = case(rnd, i)
        h = 900; r = lambda x: cv2.resize(x, (int(x.shape[1] * h / x.shape[0]), h))
        cv2.imwrite(f"{S}/compare-{i + 1:02d}.jpg", np.hstack([r(img), np.full((h, 12, 3), 255, np.uint8), r(H.render(img, q, "DOCUMENT", aspect=A4))]), [cv2.IMWRITE_JPEG_QUALITY, 85])
