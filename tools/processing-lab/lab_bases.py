"""Этап 9: различимость базовых фильтров (пресеты с разными основами различаются уже фильтром)."""
import cv2, numpy as np, random, itertools
import enhance as E, hybrid as H
rnd = random.Random(5); np.random.seed(5)
cases = [H.make_case(rnd, i)[:2] for i in range(4)]
# цветной эталон: фото-градиенты (без листа) — различает цветные и серые основы
yy, xx = np.mgrid[0:800, 0:600]
photo = np.dstack([200 - xx * 120 // 600, 60 + yy * 150 // 800, 40 + xx * 180 // 600]).astype(np.uint8)
r2 = random.Random(9)
for _ in range(40):   # «предметы» — цветные прямоугольники и круги поверх градиента
    c = (r2.randint(0, 255), r2.randint(0, 255), r2.randint(0, 255)); x, y = r2.randint(0, 560), r2.randint(0, 760)
    if r2.random() < 0.5: cv2.rectangle(photo, (x, y), (x + r2.randint(10, 60), y + r2.randint(10, 60)), c, -1)
    else: cv2.circle(photo, (x, y), r2.randint(5, 30), c, -1)
cases.append((photo, None))
bases = ["ORIGINAL", "COLOR", "BOOK", "GRAY", "TEXT_BOOST", "BW", "RECEIPT", "PHOTO"] + [f"HYBRID_{p}" for p in H.PROFILES]
def out(b, img, q):
    o = H.render(img, q, b[7:]) if b.startswith("HYBRID_") else E.render(img, q, b)
    return o if o.ndim == 3 else cv2.cvtColor(o, cv2.COLOR_GRAY2BGR)
outs = {b: [out(b, i, q).astype(np.int16) for i, q in cases] for b in bases}
worst = (1e9, None)
for a, b in itertools.combinations(bases, 2):
    d = max(float(np.abs(x - y).mean()) for x, y in zip(outs[a], outs[b]))
    if d < worst[0]: worst = (d, (a, b))
    if d < 2: print("НЕРАЗЛИЧИМЫ", a, b, round(d, 2))
print(f"минимальная разница пары основ: {worst[0]:.2f} ({worst[1][0]} / {worst[1][1]}), пар {len(list(itertools.combinations(bases, 2)))}")
