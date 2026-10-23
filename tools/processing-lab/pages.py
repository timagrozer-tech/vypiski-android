"""Синтетические страницы для настройки обработки (этап 7).

Каждый случай: чистая страница (эталон: белый фон, тёмный «текст») и «снимок» той же страницы
с неравномерным освещением, цветным оттенком, шумом и сжатием JPEG. Углы листа на снимке известны,
поэтому качество улучшения измеряется отдельно от качества поиска углов.
"""
import cv2, numpy as np, random, math

PW, PH = 840, 1188  # A4, ~100 dpi


def clean_page(rnd):
    p = np.full((PH, PW, 3), 255, np.uint8)
    y = 90
    fonts = [cv2.FONT_HERSHEY_SIMPLEX, cv2.FONT_HERSHEY_DUPLEX, cv2.FONT_HERSHEY_COMPLEX]
    words = "PRIKAZ RAPORT SPRAVKA dogovor nomer data podpis 65384 komandir 2026 utverzhdayu".split()
    cv2.putText(p, "SPRAVKA No " + str(rnd.randint(1, 999)), (220, y), fonts[1], 1.3, (20, 20, 20), 2, cv2.LINE_AA)
    y += 70
    while y < PH - 200:
        x = 70
        while x < PW - 160:
            w = rnd.choice(words)
            s = rnd.choice([0.55, 0.62, 0.7])
            th = 1 if s < 0.6 else rnd.choice([1, 2])
            col = (rnd.randint(10, 60),) * 3
            (tw, _), _ = cv2.getTextSize(w, fonts[0], s, th)
            if x + tw > PW - 70: break
            cv2.putText(p, w, (x, y), rnd.choice(fonts), s, col, th, cv2.LINE_AA)
            x += tw + rnd.randint(10, 18)
        y += rnd.randint(30, 38)
    # таблица и подпись
    cv2.rectangle(p, (70, PH - 180), (PW - 70, PH - 110), (40, 40, 40), 1)
    cv2.line(p, (PW // 2, PH - 180), (PW // 2, PH - 110), (40, 40, 40), 1)
    if rnd.random() < 0.6:  # синяя печать
        cv2.circle(p, (PW - 200, PH - 260), 70, (160, 60, 30), 3, cv2.LINE_AA)
    return p


def illumination(rnd, w, h, kind):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    f = np.ones((h, w), np.float32)
    if kind in ("shadow", "hard"):
        # тень от руки/телефона: мягкий край
        a = rnd.uniform(0, math.pi)
        d = (xx - w / 2) * math.cos(a) + (yy - h / 2) * math.sin(a)
        edge = rnd.uniform(-0.2, 0.2) * w
        soft = 0.02 * w if kind == "hard" else 0.12 * w
        depth = rnd.uniform(0.35, 0.55)
        f *= 1 - depth / (1 + np.exp(-(d - edge) / soft))
    if kind in ("gradient", "shadow", "hard", "dim"):
        gx, gy = rnd.uniform(-0.35, 0.35), rnd.uniform(-0.35, 0.35)
        f *= 1 + gx * (xx / w - 0.5) + gy * (yy / h - 0.5)
    # виньетирование
    r = np.sqrt(((xx - w / 2) / w) ** 2 + ((yy - h / 2) / h) ** 2)
    f *= 1 - rnd.uniform(0.1, 0.3) * r ** 2
    if kind == "dim": f *= 0.55
    return np.clip(f, 0.05, 1.2)


KINDS = ["plain", "gradient", "shadow", "hard", "dim"]


def make_case(rnd, i):
    kind = KINDS[i % len(KINDS)]
    clean = clean_page(rnd)
    w, h = 1200, 1600
    # сцена: стол + лист в перспективе
    bg = np.zeros((h, w, 3), np.uint8); bg[:] = (rnd.randint(60, 120), rnd.randint(70, 130), rnd.randint(80, 140))
    s = rnd.uniform(0.80, 0.92)
    pw, ph = w * s, w * s * PH / PW
    if ph > h * 0.92: k = h * 0.92 / ph; pw *= k; ph *= k
    cx, cy = w / 2, h / 2
    jit = lambda: rnd.uniform(-0.05, 0.05) * pw
    pts = np.float32([[cx - pw / 2 + jit(), cy - ph / 2 + jit()], [cx + pw / 2 + jit(), cy - ph / 2 + jit()],
                      [cx + pw / 2 + jit(), cy + ph / 2 + jit()], [cx - pw / 2 + jit(), cy + ph / 2 + jit()]])
    M = cv2.getPerspectiveTransform(np.float32([[0, 0], [PW, 0], [PW, PH], [0, PH]]), pts)
    paper = clean.astype(np.float32)
    # бумага не идеально белая, тёплый/холодный оттенок
    tint = np.array([rnd.uniform(0.85, 1.0), rnd.uniform(0.9, 1.0), rnd.uniform(0.92, 1.0)], np.float32)
    paper = paper * tint
    warped = cv2.warpPerspective(paper, M, (w, h), flags=cv2.INTER_LINEAR)
    mask = cv2.warpPerspective(np.full((PH, PW), 255, np.uint8), M, (w, h))
    img = bg.astype(np.float32); img[mask > 0] = warped[mask > 0]
    img *= illumination(rnd, w, h, kind)[..., None]
    img += np.random.default_rng(rnd.randint(0, 1 << 30)).normal(0, 4.0, img.shape)
    img = np.clip(img, 0, 255).astype(np.uint8)
    ok, enc = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, 88])
    img = cv2.imdecode(enc, cv2.IMREAD_COLOR)
    return img, pts, clean, kind
