"""Hybrid HD Pro (этап 8): маски текст / печать / подпись / фон и сборка результата.

Копия core:imageprocessing HybridRenderer один к одному. Только детерминированные фильтры OpenCV —
ничего не дорисовывается и не «восстанавливается» генеративно.
"""
import cv2, numpy as np, math, random
import enhance as E, pages

PROFILES = {
    # 10 параметров; значения выбраны в lab_hybrid (см. README)
    "DOCUMENT": dict(shadows=1, denoise=1, text_gamma=1.8, sharpen=0.5, bg_white=0.92, sat_min=60, ink_v_max=235, color_boost=1.25, mask_grow=2, text_bw=0),
    "CONTRAST": dict(shadows=1, denoise=1, text_gamma=2.4, sharpen=0.7, bg_white=0.88, sat_min=60, ink_v_max=235, color_boost=1.4, mask_grow=2, text_bw=1),
    "SOFT":     dict(shadows=1, denoise=1, text_gamma=1.3, sharpen=0.3, bg_white=0.95, sat_min=60, ink_v_max=235, color_boost=1.1, mask_grow=1, text_bw=0),
}


def masks(norm_rgb, p):
    """norm_rgb — выровненное по освещению изображение (BGR в Python). Возвращает (цвет, текст)."""
    hsv = cv2.cvtColor(norm_rgb, cv2.COLOR_BGR2HSV)
    s, v = hsv[..., 1], hsv[..., 2]
    color = ((s >= p["sat_min"]) & (v <= p["ink_v_max"])).astype(np.uint8) * 255
    color = cv2.morphologyEx(color, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
    if p["mask_grow"] > 0:
        k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * p["mask_grow"] + 1,) * 2)
        color = cv2.dilate(color, k)
    gray = cv2.cvtColor(norm_rgb, cv2.COLOR_BGR2GRAY)
    side = max(gray.shape)
    text = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY_INV, E.odd(side / 40), 12)
    text = cv2.dilate(text, np.ones((3, 3), np.uint8))       # края букв (сглаживание) — тоже текст
    text[color > 0] = 0
    return color, text


def render(bgr, q, profile="DOCUMENT", aspect=None):
    p = PROFILES[profile]
    img = E.warp(bgr, q, aspect) if q is not None else bgr.copy()
    if p["denoise"]: img = cv2.GaussianBlur(img, (3, 3), 0)
    if p["shadows"]: img = cv2.merge([E.remove_shadows(c) for c in cv2.split(img)])
    color, text = masks(img, p)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    g = E.levels(gray)
    if p["text_bw"]:
        t = np.where(g < 160, 0, 255).astype(np.uint8)
        t = cv2.GaussianBlur(t, (3, 3), 0)                       # мягкий край вместо «лесенки»
    else:
        t = E.unsharp(E.text_curve(g, p["text_gamma"]), 1.0, p["sharpen"])
    out = np.full_like(img, 255)
    # фон: всё, что светлее порога, — белое; остальное (не текст и не цвет) — как серое
    bg_thr = int(255 * p["bg_white"])
    rest = (text == 0) & (color == 0)
    gg = np.where(g >= bg_thr, 255, g).astype(np.uint8)
    out[rest] = cv2.cvtColor(gg, cv2.COLOR_GRAY2BGR)[rest]
    tt = cv2.cvtColor(t, cv2.COLOR_GRAY2BGR)
    out[text > 0] = tt[text > 0]
    # цветные чернила: исходный цвет с усилением насыщенности
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV).astype(np.float32)
    hsv[..., 1] = np.clip(hsv[..., 1] * p["color_boost"], 0, 255)
    ci = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)
    out[color > 0] = ci[color > 0]
    return out


# ── синтетика: печать и подпись с эталонными масками ──
def add_marks(clean, rnd):
    h, w = clean.shape[:2]
    stamp = np.zeros((h, w), np.uint8); sign = np.zeros((h, w), np.uint8)
    blue = (rnd.randint(140, 190), rnd.randint(40, 80), rnd.randint(20, 50))      # BGR: синий
    cx, cy, r = w - 230 + rnd.randint(-30, 30), h - 300 + rnd.randint(-30, 30), rnd.randint(70, 90)
    cv2.circle(stamp, (cx, cy), r, 255, 4, cv2.LINE_AA); cv2.circle(stamp, (cx, cy), r - 14, 255, 2, cv2.LINE_AA)
    for k in range(10):
        a = -math.pi * 0.9 + k * 0.2
        cv2.putText(stamp, "V/CH65384"[k % 9], (int(cx + (r - 9) * math.cos(a)) - 5, int(cy + (r - 9) * math.sin(a)) + 5),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.45, 255, 1, cv2.LINE_AA)
    cv2.putText(stamp, "*", (cx - 10, cy + 10), cv2.FONT_HERSHEY_SIMPLEX, 1.2, 255, 2, cv2.LINE_AA)
    # подпись: несколько кривых Безье синей пастой
    sx, sy = 150 + rnd.randint(0, 80), h - 260 + rnd.randint(-20, 20)
    pts = []
    for t in np.linspace(0, 1, 120):
        pts.append((sx + 260 * t + 25 * math.sin(14 * t + rnd.random()), sy + 30 * math.sin(9 * t) * (1 - t) + 10 * math.cos(23 * t)))
    cv2.polylines(sign, [np.int32(pts)], False, 255, 2, cv2.LINE_AA)
    ink = (rnd.randint(120, 170), rnd.randint(50, 90), rnd.randint(30, 60))
    out = clean.astype(np.float32)
    for m, col in ((stamp, blue), (sign, ink)):
        a = (m.astype(np.float32) / 255.0 * 0.85)[..., None]
        out = out * (1 - a) + np.array(col, np.float32) * a          # полупрозрачно поверх текста
    return out.astype(np.uint8), stamp > 128, sign > 128


def make_case(rnd, i):
    img, q, clean, kind = pages.make_case(rnd, i)
    # перерисовываем ту же сцену, но с печатью и подписью на листе
    marked, sm, gm = add_marks(clean, rnd)
    # вставка листа с отметками в сцену той же геометрией
    h, w = img.shape[:2]
    M = cv2.getPerspectiveTransform(np.float32([[0, 0], [pages.PW, 0], [pages.PW, pages.PH], [0, pages.PH]]), np.float32(q))
    diff = cv2.warpPerspective((marked.astype(np.int16) - clean.astype(np.int16)).astype(np.float32), M, (w, h))
    img2 = np.clip(img.astype(np.float32) + diff * (img.astype(np.float32) / 255.0 + 0.15), 0, 255).astype(np.uint8)
    return img2, q, marked, sm, gm, kind


def ink_pixels(img, mask):
    """Пиксели эталонной маски, видимые на [img] как синие чернила."""
    o = cv2.resize(img, (mask.shape[1], mask.shape[0]), interpolation=cv2.INTER_AREA)
    core = cv2.erode(mask.astype(np.uint8), np.ones((2, 2), np.uint8)).astype(bool)
    if core.sum() == 0: core = mask
    hsv = cv2.cvtColor(o, cv2.COLOR_BGR2HSV)
    return core & (hsv[..., 2] < 225) & (hsv[..., 0] > 95) & (hsv[..., 0] < 135) & (hsv[..., 1] > 40)


def preserved(out, mask, base):
    """Доля сохранённых пикселей эталонной маски: из пикселей печати/подписи, различимых как синие чернила
    на входе (только перспектива и выравнивание света — [base]), сколько остались такими же на выходе.
    Пиксели, потерянные ещё на снимке (JPEG, пересечение с чёрным текстом), алгоритму не засчитываются."""
    eligible = ink_pixels(base, mask)
    if eligible.sum() == 0: return 1.0
    return float((ink_pixels(out, mask) & eligible).sum() / eligible.sum())
