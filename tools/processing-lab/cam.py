"""Фильтры и инструменты по образцу набора CamScanner (2026-10-24) — копия Kotlin один к одному.

Фильтры (PageRenderer): «Осветлить» (LIGHTEN), «Яркий цвет» (MAGIC_COLOR), «Без теней» (NO_SHADOW).
Инструменты (AdjustEngine, ручные параметры): «Убрать муар и растр» (descreen), «Убрать цветные пометки» (inkRemoval).
Аналоги остальных фильтров уже были: «Оригинал», «Серый», «Ч/Б», «Документ» (≈ Magic Pro / Omnifix).
"""
import cv2, numpy as np
import enhance as E

BG_SIDE = 512


def scanner_lut(hist, white_frac, gamma):
    c = np.cumsum(hist); b = int(np.searchsorted(c, c[-1] * 0.005))
    w = max(b + 16, int(255 * white_frac))
    return np.clip(255.0 * np.clip((np.arange(256) - b) / (w - b), 0, 1) ** gamma, 0, 255).astype(np.uint8)


def lighten_lut(hist):
    """«Осветлить»: белая точка — 97-й процентиль яркости (бумага), не ниже 160; гамма 0.85 (средние тона светлее).
    Свет и тени не выравниваются — снимок остаётся похожим на исходный, только светлее и чище."""
    c = np.cumsum(hist); w = max(160, int(np.searchsorted(c, c[-1] * 0.97)))
    return np.clip(255.0 * np.clip(np.arange(256) / w, 0, 1) ** 0.85, 0, 255).astype(np.uint8)


def luma_hist(rgb):
    g = cv2.cvtColor(rgb, cv2.COLOR_BGR2GRAY)
    return cv2.calcHist([g], [0], None, [256], [0, 256]).ravel()


def no_shadow(img):
    """«Без теней»: деление на фон по каналам, затем умножение на цвет бумаги (90-й процентиль фона канала,
    самый светлый канал поднят не ниже 240): тень уходит, оттенок бумаги остаётся (у «Цветного» бумага белая)."""
    chs = cv2.split(img)
    bgs = [E.background(ch).astype(np.float32) for ch in chs]
    paper = [float(np.percentile(b, 90)) for b in bgs]
    lift = max(1.0, 240.0 / max(max(paper), 1.0))
    return cv2.merge([np.clip(ch.astype(np.float32) * (p * lift) / np.maximum(b, 1.0), 0, 255).astype(np.uint8) for ch, b, p in zip(chs, bgs, paper)])


def render(bgr, q, f, aspect=None):
    img = E.warp(bgr, q, aspect) if q is not None else bgr.copy()
    img = cv2.GaussianBlur(img, (3, 3), 0)
    if f == "LIGHTEN":
        return cv2.LUT(img, lighten_lut(luma_hist(img)))
    if f == "NO_SHADOW":
        return no_shadow(img)
    if f == "MAGIC_COLOR":
        # «Яркий цвет»: выравнивание света, уровни (белая 0.97, гамма 1.0) на все каналы, насыщенность ×1.6,
        # нерезкая маска 0.3 — цвет сохраняется везде (логотипы, маркер, фото), не только у печатей и подписей
        img = cv2.merge([E.remove_shadows(ch) for ch in cv2.split(img)])
        img = cv2.LUT(img, scanner_lut(luma_hist(img), 0.97, 1.0))
        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV); h, s, v = cv2.split(hsv)
        s = cv2.convertScaleAbs(s, alpha=1.6)
        img = cv2.cvtColor(cv2.merge([h, s, v]), cv2.COLOR_HSV2BGR)
        return E.unsharp(img, 1.0, 0.3)
    raise ValueError(f)


# ---- инструменты: чистая математика на float, как AdjustEngine (Kotlin) ----

def box(c, r):
    """Сепарабельное среднее (2r+1)², края — повтор крайнего пикселя (как AdjustEngine.boxBlur)."""
    return cv2.blur(c, (2 * r + 1, 2 * r + 1), borderType=cv2.BORDER_REPLICATE)


def descreen(img, k):
    """«Убрать муар и растр»: среднее 3×3, смешанное с силой k (гасит точки печати и муар периода 3–4 px),
    затем нерезкая маска радиус 1, сила 0.6·k — края букв возвращаются. Радиус 2 и больше размывал текст (CER до 42 %)."""
    if k <= 0: return img
    x = img.astype(np.float32)
    x = x + (box(x, 1) - x) * k
    x = np.clip(x + (x - box(x, 1)) * 0.6 * k, 0, 255)
    return x.astype(np.uint8)


def ink_mask(img):
    """Цветные чернила (ручка, маркер, печать): насыщенность ≥ 0.30, ярче 40 (не чёрный текст), темнее 245 (не бумага)."""
    x = img.astype(np.float32)
    mx = x.max(2); mn = x.min(2)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1), 0)
    return (sat >= 0.30) & (mx > 40) & (mx < 245)


def remove_ink(img, k):
    """«Убрать цветные пометки»: пиксели цветных чернил (+1 px вокруг) заменяются фоном вокруг них —
    нормированное среднее радиусом 7 по пикселям без чернил; где их нет — белое. Сила k — смешивание."""
    if k <= 0: return img
    m = ink_mask(img).astype(np.uint8)
    m = cv2.dilate(m, np.ones((3, 3), np.uint8)).astype(np.float32)
    keep = 1 - m
    x = img.astype(np.float32)
    num = box(x * keep[..., None], 7); den = box(keep, 7)[..., None]
    fill = np.where(den > 0.05, num / np.maximum(den, 1e-6), 255)
    out = x + (fill - x) * (m[..., None] * k)
    return np.clip(out, 0, 255).astype(np.uint8)
