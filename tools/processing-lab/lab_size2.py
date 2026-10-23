"""Этап 17: оценка размера пробным сжатием. Уменьшенная копия страницы (сторона 600 px) сжимается тем же
качеством; размер масштабируется на число пикселей с показателем b (подбор на половине, проверка на другой)."""
import cv2, numpy as np, random, glob, json
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
        sw = 600; sh = int(sw * out.shape[0] / out.shape[1]); small = cv2.resize(out, (sw, sh), interpolation=cv2.INTER_AREA)
        for dpi in (150, 200, 300):
            w = int(210 / 25.4 * dpi); h = int(w * out.shape[0] / out.shape[1])
            o = cv2.resize(out, (w, h), interpolation=cv2.INTER_AREA)
            for qual in (60, 80, 92):
                n = len(cv2.imencode(".jpg", o, [cv2.IMWRITE_JPEG_QUALITY, qual])[1])
                ns = len(cv2.imencode(".jpg", small, [cv2.IMWRITE_JPEG_QUALITY, qual])[1])
                rows.append((flt, qual, (w * h) / (sw * sh), ns, n))
random.Random(1).shuffle(rows)
train = rows[: len(rows) // 2]; test = rows[len(rows) // 2:]
best = None
for b in np.arange(0.4, 1.01, 0.02):
    a = np.median([n / (ns * r ** b) for f, q, r, ns, n in train])
    e = np.median([abs(a * ns * r ** b - n) / n for f, q, r, ns, n in train])
    if best is None or e < best[0]: best = (e, a, b)
_, A, B = best
errs = np.array([(A * ns * r ** B - n) / n for f, q, r, ns, n in test])
print(json.dumps({"a": round(float(A), 4), "b": round(float(B), 3)}))
print(f"проверка ({len(errs)} стр.): медиана |e| {100*np.median(abs(errs)):.1f} %, 90-й процентиль {100*np.percentile(abs(errs),90):.1f} %, макс {100*abs(errs).max():.1f} %")
docs = [errs[i:i+5].mean() for i in range(0, len(errs) - 5, 5)]
print(f"документ из 5 стр.: медиана |e| {100*np.median(np.abs(docs)):.1f} %, 90-й процентиль {100*np.percentile(np.abs(docs),90):.1f} %")
