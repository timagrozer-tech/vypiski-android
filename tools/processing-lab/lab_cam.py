"""Замер фильтров и инструментов «как в CamScanner» (cam.py). Запуск: python3 lab_cam.py [img]
1) новые фильтры на наборе lab_doc (12 страниц: сгибы, тени, печать, подпись) — фон, оттенок бумаги, печать, CER;
2) различимость новых фильтров от прежних (средняя разница, порог 2 уровня);
3) муар/растр: страница с текстом на растровой плашке (точки периода 4 px) — шум плашки и CER до/после;
4) цветные пометки: доля оставшихся синих пикселей подписи/печати и CER печатного текста до/после.
"""
import cv2, numpy as np, random, sys, os
import enhance as E, hybrid as H, cam as C, lab_doc as L

A4 = 210 / 297
OUT = os.environ.get("OUT", "/tmp")


def paper_tint(out, marked):
    """Средний цвет чистой бумаги (B, G, R) — показывает, сохранён ли оттенок бумаги."""
    g = cv2.cvtColor(marked, cv2.COLOR_BGR2GRAY)
    m = cv2.erode((g > 250).astype(np.uint8), np.ones((9, 9), np.uint8)).astype(bool)
    o = cv2.resize(out, (marked.shape[1], marked.shape[0]), interpolation=cv2.INTER_AREA)
    return o[m].mean(0)


def filters():
    print("== 1. Новые фильтры (12 страниц lab_doc)")
    for name, f in (("«Осветлить» (Lighten)", "LIGHTEN"), ("«Без теней» (No Shadow)", "NO_SHADOW"), ("«Яркий цвет» (Magic Color)", "MAGIC_COLOR")):
        L.run(name, lambda i, q, f=f: C.render(i, q, f, aspect=A4))
    L.run("для сравнения: «Цветной»", lambda i, q: E.render(i, q, "COLOR", aspect=A4))
    L.run("для сравнения: «Документ»", lambda i, q: H.render(i, q, "DOCUMENT", aspect=A4))


def distinct():
    print("== 2. Различимость (средняя |разница|, мин. по 4 страницам; порог 2)")
    rnd = random.Random(7); np.random.seed(7)
    cases = [L.case(rnd, i)[:2] for i in range(4)]
    def outs(fn): return [fn(i, q) for i, q in cases]
    def to3(x): return x if x.ndim == 3 else cv2.cvtColor(x, cv2.COLOR_GRAY2BGR)
    base = {
        "Оригинал": outs(lambda i, q: E.render(i, q, "ORIGINAL", aspect=A4)),
        "Цветной": outs(lambda i, q: E.render(i, q, "COLOR", aspect=A4)),
        "Серый": outs(lambda i, q: E.render(i, q, "GRAY", aspect=A4)),
        "Документ": outs(lambda i, q: H.render(i, q, "DOCUMENT", aspect=A4)),
        "Фото": outs(lambda i, q: E.render(i, q, "PHOTO", aspect=A4)),
        "Книга": outs(lambda i, q: E.render(i, q, "BOOK", aspect=A4)),
    }
    new = {n: outs(lambda i, q, f=f: C.render(i, q, f, aspect=A4)) for n, f in (("Осветлить", "LIGHTEN"), ("Без теней", "NO_SHADOW"), ("Яркий цвет", "MAGIC_COLOR"))}
    allm = {**base, **new}
    worst = 1e9
    for a in new:
        for b in allm:
            if a == b or (b in new and list(new).index(b) < list(new).index(a)): continue
            d = max(float(np.abs(to3(x).astype(int) - to3(y).astype(int)).mean()) for x, y in zip(allm[a], allm[b]))
            worst = min(worst, d)
            print(f"  {a:11s} / {b:11s}: {d:5.2f}")
    print(f"  минимум: {worst:.2f}")


def halftone_case(rnd):
    """Текст поверх растровой плашки: точки периода 4 px (как офсетная печать серого 25 %), плюс муар от снимка."""
    import make_testdata as MT
    lines = MT.doc_text(rnd)
    clean = MT.render_page(rnd, lines)
    h, w = clean.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    dots = ((np.sin(xx * np.pi / 2) * np.sin(yy * np.pi / 2)) > 0.35)  # период 4 px
    tint = np.ones((h, w), np.float32)
    tint[dots] = 0.55
    page = (clean.astype(np.float32) * tint[..., None]).clip(0, 255).astype(np.uint8)
    return page, "\n".join(l for l in lines if l)


def screen_noise(img, clean):
    g = img if img.ndim == 2 else cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    cg = cv2.cvtColor(clean, cv2.COLOR_BGR2GRAY)
    m = cv2.erode((cg > 250).astype(np.uint8), np.ones((9, 9), np.uint8)).astype(bool)
    return float(g[m].std())


def moire():
    print("== 3. Муар и растр: 6 страниц, текст на растровой плашке (точки 4 px)")
    rnd = random.Random(11); np.random.seed(11)
    rows = {k: [] for k in ("до", "0.5", "1.0")}
    for i in range(6):
        page, gt = halftone_case(rnd)
        for k in rows:
            out = page if k == "до" else C.descreen(page, float(k))
            # фон — места без текста: строки, где почти нет тёмных пикселей
            ink = cv2.dilate((cv2.cvtColor(page, cv2.COLOR_BGR2GRAY) < 90).astype(np.uint8), np.ones((15, 15), np.uint8)).astype(bool)
            og = cv2.cvtColor(out, cv2.COLOR_BGR2GRAY)
            rows[k].append((float(og[~ink].std()), L.ocr_cer(out, gt)))
        if i == 0 and "img" in sys.argv:
            cut = lambda x: x[200:500, 100:700]
            cv2.imwrite(f"{OUT}/cam-descreen.png", np.vstack([cut(page), np.full((8, 600, 3), 255, np.uint8), cut(C.descreen(page, 1.0))]))
    for k, v in rows.items():
        print(f"  сила {k:4s}: шум плашки {np.mean([a for a, _ in v]):5.1f}, CER {100 * np.mean([b for _, b in v]):5.2f}% (худш {100 * max(b for _, b in v):5.2f}%)")


def ink():
    print("== 4. Цветные пометки (подпись, печать) на 12 страницах lab_doc, после «Документа»")
    rnd = random.Random(2610); np.random.seed(2610)
    res = {"до": [], "после": []}
    for i in range(12):
        img, q, marked, sm, gm, gt, kind = L.case(rnd, i)
        doc = H.render(img, q, "DOCUMENT", aspect=A4)
        base = E.render(img, q, "COLOR", dn=False)
        for k, out in (("до", doc), ("после", C.remove_ink(doc, 1.0))):
            left = (H.preserved(out, sm, base) + H.preserved(out, gm, base)) / 2
            res[k].append((left, L.ocr_cer(out, gt), L.bg_stats(out, marked)[0]))
        if i == 1 and "img" in sys.argv:
            hh = 700; r = lambda x: cv2.resize(x, (int(x.shape[1] * hh / x.shape[0]), hh))
            cv2.imwrite(f"{OUT}/cam-ink.jpg", np.hstack([r(doc), np.full((hh, 10, 3), 255, np.uint8), r(C.remove_ink(doc, 1.0))]))
    for k, v in res.items():
        print(f"  {k:5s}: синих пикселей осталось {np.mean([a for a, _, _ in v]):.3f}, CER печатного текста {100 * np.mean([b for _, b, _ in v]):5.2f}%, фон {np.mean([c for _, _, c in v]):5.1f}")


if __name__ == "__main__":
    which = [a for a in sys.argv[1:] if a != "img"] or ["filters", "distinct", "moire", "ink"]
    for w in which: globals()[w]()
    if "img" in sys.argv:
        rnd = random.Random(2610); np.random.seed(2610)
        img, q, *_ = L.case(rnd, 2)
        hh = 600; r = lambda x: cv2.resize(x if x.ndim == 3 else cv2.cvtColor(x, cv2.COLOR_GRAY2BGR), (int(hh * 210 / 297), hh))
        tiles = [r(E.warp(img, q, A4)), r(C.render(img, q, "LIGHTEN", A4)), r(C.render(img, q, "NO_SHADOW", A4)), r(C.render(img, q, "MAGIC_COLOR", A4)), r(H.render(img, q, "DOCUMENT", aspect=A4))]
        cv2.imwrite(f"{OUT}/cam-filters.jpg", np.hstack(sum([[t, np.full((hh, 8, 3), 255, np.uint8)] for t in tiles], [])[:-1]))
