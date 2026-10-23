"""Этап 17: оценка размера экспорта. JPEG-размер страницы ≈ пиксели × k(качество, фильтр).
Подбор k на половине страниц, проверка погрешности на другой половине (в т. ч. OCR-набор)."""
import cv2, numpy as np, random, glob, os, json
import pages, enhance as E, hybrid as H
rnd = random.Random(17); np.random.seed(17)
imgs = []
for i in range(20):
    img, q, clean, kind = pages.make_case(rnd, i); imgs.append((img, q))
for f in sorted(glob.glob("../../testdata/ocr/*.jpg")):
    q = np.float32([[float(v) for v in p.split(",")] for p in open(f[:-4] + ".quad.txt").read().strip().split(";")])
    imgs.append((cv2.imread(f), q))
rows = []
for img, q in imgs:
    for flt in ("TEXT_BOOST", "COLOR", "BW", "HYBRID"):
        out = H.render(img, q, "DOCUMENT") if flt == "HYBRID" else E.render(img, q, flt)
        for dpi in (150, 200, 300):
            w = int(210 / 25.4 * dpi); h = int(w * out.shape[0] / out.shape[1])
            o = cv2.resize(out, (w, h), interpolation=cv2.INTER_AREA)
            for qual in (60, 80, 92):
                n = len(cv2.imencode(".jpg", o, [cv2.IMWRITE_JPEG_QUALITY, qual])[1])
                rows.append((flt, dpi, qual, w * h, n))
train = rows[: len(rows) // 2]; test = rows[len(rows) // 2:]
k = {}
for flt in ("TEXT_BOOST", "COLOR", "BW", "HYBRID"):
    for qual in (60, 80, 92):
        r = [n / px for f, d, qq, px, n in train if f == flt and qq == qual]
        k[f"{flt}:{qual}"] = float(np.median(r))
errs = []
for f, d, qq, px, n in test:
    est = k[f"{f}:{qq}"] * px; errs.append((est - n) / n)
errs = np.array(errs)
print(json.dumps({key: round(v, 4) for key, v in k.items()}, ensure_ascii=False))
print(f"погрешность на проверочной половине ({len(errs)} стр.): медиана |e| {100*np.median(abs(errs)):.1f} %, 90-й процентиль {100*np.percentile(abs(errs),90):.1f} %, макс {100*abs(errs).max():.1f} %")
# документ из 5 страниц: ошибки страниц частично компенсируются
docs = [errs[i:i+5].mean() for i in range(0, len(errs) - 5, 5)]
print(f"документ из 5 стр.: медиана |e| {100*np.median(np.abs(docs)):.1f} %, макс {100*np.max(np.abs(docs)):.1f} %")
