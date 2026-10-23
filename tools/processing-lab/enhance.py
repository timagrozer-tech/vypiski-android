"""Копия алгоритмов PageRenderer (core:imageprocessing) на Python + варианты для сравнения.

Каждая функция здесь повторяет шаг Kotlin-кода один к одному (те же операции OpenCV и те же
параметры), чтобы цифры лаборатории относились к приложению. Меняя алгоритм в приложении,
меняйте его и здесь — и перезапускайте lab.py.
"""
import cv2, numpy as np, math


def odd(v): v = int(round(v)); return v if v % 2 == 1 else v + 1


def output_size(q, aspect=None):
    """Размер выпрямленного листа: длины противоположных сторон (максимум); при известном формате —
    высота по ширине из соотношения сторон формата. q — 4 точки по часовой стрелке от левой верхней."""
    d = lambda a, b: math.hypot(a[0] - b[0], a[1] - b[1])
    w = max(d(q[0], q[1]), d(q[3], q[2]))
    h = max(d(q[0], q[3]), d(q[1], q[2]))
    if aspect is not None:
        # формат известен и углы ему не противоречат (±15 %): площадь сохраняем, пропорции — как у формата
        a = aspect if (w <= h) == (aspect <= 1) else 1 / aspect
        if abs((w / h) / a - 1) <= 0.15:
            area = w * h
            w = math.sqrt(area * a); h = area / w
    return max(1, int(round(w))), max(1, int(round(h)))


def warp(img, q, aspect=None):
    w, h = output_size(q, aspect)
    M = cv2.getPerspectiveTransform(np.float32(q), np.float32([[0, 0], [w - 1, 0], [w - 1, h - 1], [0, h - 1]]))
    return cv2.warpPerspective(img, M, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)


def background(ch, variant="close_gauss"):
    """Оценка освещённого фона одного канала: убираем текст (максимум по окрестности), сглаживаем,
    считаем на уменьшенной копии (большая сторона 512) — быстро и не зависит от разрешения снимка."""
    h, w = ch.shape
    s = 512.0 / max(h, w)
    small = cv2.resize(ch, (max(1, int(w * s)), max(1, int(h * s))), interpolation=cv2.INTER_AREA)
    side = max(small.shape)
    if variant == "dilate_median":
        k = cv2.getStructuringElement(cv2.MORPH_RECT, (odd(side / 64), odd(side / 64)))
        bg = cv2.medianBlur(cv2.dilate(small, k), odd(side / 20))
    elif variant == "close_gauss":
        k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (odd(side / 30), odd(side / 30)))
        bg = cv2.GaussianBlur(cv2.morphologyEx(small, cv2.MORPH_CLOSE, k), (0, 0), side / 40)
    elif variant == "dilate_gauss":
        k = cv2.getStructuringElement(cv2.MORPH_RECT, (odd(side / 64), odd(side / 64)))
        bg = cv2.GaussianBlur(cv2.dilate(small, k), (0, 0), side / 50)
    else:
        raise ValueError(variant)
    return cv2.resize(bg, (w, h), interpolation=cv2.INTER_LINEAR)


def remove_shadows(ch, variant="close_gauss"):
    bg = background(ch, variant).astype(np.float32)
    out = ch.astype(np.float32) * 255.0 / np.maximum(bg, 1.0)
    return np.clip(out, 0, 255).astype(np.uint8)


def levels(gray, lo_pct=1.0, hi_pct=99.5, sample=None):
    """Растяжение уровней по процентилям гистограммы."""
    hist = cv2.calcHist([gray], [0], None, [256], [0, 256]).ravel()
    c = np.cumsum(hist); n = c[-1]
    lo = int(np.searchsorted(c, n * lo_pct / 100)); hi = int(np.searchsorted(c, n * hi_pct / 100))
    if hi - lo < 16: return gray
    lut = np.clip((np.arange(256) - lo) * 255.0 / (hi - lo), 0, 255).astype(np.uint8)
    return cv2.LUT(gray, lut)


def text_curve(gray, gamma=1.6):
    """Усиленный текст: тёмное темнее, фон остаётся белым (кривая x^gamma)."""
    lut = np.clip(255.0 * (np.arange(256) / 255.0) ** gamma, 0, 255).astype(np.uint8)
    return cv2.LUT(gray, lut)


def unsharp(img, sigma=1.2, amount=0.6):
    b = cv2.GaussianBlur(img, (0, 0), sigma)
    return cv2.addWeighted(img, 1 + amount, b, -amount, 0)


def binarize(gray, method="adaptive", C=12):
    side = max(gray.shape)
    if method == "otsu":
        _, o = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU); return o
    if method == "adaptive":
        return cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, odd(side / 40), C)
    if method == "fixed":
        _, o = cv2.threshold(gray, 160, 255, cv2.THRESH_BINARY); return o
    raise ValueError(method)


def denoise(img, method="median3"):
    if method == "none": return img
    if method == "median3": return cv2.medianBlur(img, 3)
    if method == "gauss": return cv2.GaussianBlur(img, (3, 3), 0)
    if method == "bilateral": return cv2.bilateralFilter(img, 5, 25, 5)
    if method == "nlm":
        return cv2.fastNlMeansDenoising(img, None, 7) if img.ndim == 2 else cv2.fastNlMeansDenoisingColored(img, None, 7, 7)
    raise ValueError(method)


def brightness_contrast(img, b=0.0, c=1.0):
    if b == 0 and c == 1: return img
    lut = np.clip((np.arange(256) - 128.0) * c + 128.0 + b * 100.0, 0, 255).astype(np.uint8)
    return cv2.LUT(img, lut)


def render(bgr, q, f, rotation=0, shadows=True, dn=True, b=0.0, c=1.0, aspect=None, cfg=None):
    """Полный рендер страницы по рецепту — порядок шагов как в PageRenderer."""
    cfg = cfg or {}
    img = warp(bgr, q, aspect) if q is not None else bgr.copy()
    if rotation:
        img = cv2.rotate(img, {90: cv2.ROTATE_90_CLOCKWISE, 180: cv2.ROTATE_180, 270: cv2.ROTATE_90_COUNTERCLOCKWISE}[rotation])
    if f == "ORIGINAL":
        return brightness_contrast(img, b, c)
    color = f in ("COLOR", "PHOTO", "BOOK")
    if not color: img = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    if dn and f != "PHOTO": img = denoise(img, cfg.get("denoise", "gauss"))
    if shadows and f != "PHOTO":
        v = cfg.get("bg", "close_gauss")
        img = cv2.merge([remove_shadows(ch, v) for ch in cv2.split(img)]) if color else remove_shadows(img, v)
    if f == "PHOTO":
        lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB); l, a, bb = cv2.split(lab)
        l = cv2.createCLAHE(2.0, (8, 8)).apply(l)
        img = cv2.cvtColor(cv2.merge([l, a, bb]), cv2.COLOR_LAB2BGR)
    elif color:
        g = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        hist = np.cumsum(cv2.calcHist([g], [0], None, [256], [0, 256]).ravel())
        lo = int(np.searchsorted(hist, hist[-1] * 0.01))
        lo = min(lo, 120)
        lut = np.clip((np.arange(256) - lo) * 255.0 / (255 - lo), 0, 255).astype(np.uint8)
        img = cv2.LUT(img, lut)
        if f == "BOOK": img = text_curve(img, 1.3)   # книга: кремовая бумага → белая, печать темнее
    elif f == "GRAY":
        img = levels(img)
    elif f == "TEXT_BOOST":
        img = unsharp(text_curve(levels(img), cfg.get("gamma", 1.8)), 1.0, cfg.get("sharp", 0.5))
    elif f in ("BW", "RECEIPT"):
        img = levels(img)
        img = brightness_contrast(img, b, c)
        o = binarize(img, cfg.get("bw", "adaptive"), cfg.get("C", 12) if f == "BW" else cfg.get("Crec", 8))
        if f == "RECEIPT":   # чек: бледная термопечать — штрихи утолщаются (эрозия белого 2×2)
            o = cv2.erode(o, np.ones((2, 2), np.uint8))
        return o
    return brightness_contrast(img, b, c)
