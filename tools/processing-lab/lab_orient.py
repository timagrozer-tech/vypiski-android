"""Ориентация страницы без OSD-API: (1) профили проекций — строки горизонтальны или вертикальны;
(2) 0° против 180° — средняя уверенность Tesseract на центральной полосе. Набор: 12 страниц × 4 поворота."""
import cv2, numpy as np, random, subprocess, tempfile, os, time
import lab_doc as L, hybrid as H
TESS = L.TESS

def lines_vertical(g):
    b = cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV, 25, 15)
    b = cv2.dilate(b, np.ones((1, 9), np.uint8))   # слить буквы строки по горизонтали
    rv = np.var(b.sum(axis=1)); cv_ = np.var(cv2.dilate(cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY_INV, 25, 15), np.ones((9, 1), np.uint8)).sum(axis=0))
    return cv_ > rv

def conf(g):
    h = g.shape[0]; strip = g[int(h * 0.2):int(h * 0.6)]
    with tempfile.NamedTemporaryFile(suffix=".png") as f:
        cv2.imwrite(f.name, strip)
        out = subprocess.run(["tesseract", f.name, "stdout", "--tessdata-dir", TESS, "-l", "rus+eng", "--psm", "6", "-c", "tessedit_create_tsv=1"], capture_output=True, text=True).stdout
    c = [float(l.split("\t")[10]) for l in out.splitlines()[1:] if len(l.split("\t")) > 11 and l.split("\t")[11].strip() and float(l.split("\t")[10]) >= 0]
    return np.mean(c) if c else 0

def detect(g):
    """Возвращает поворот (по часовой), который нужно применить, чтобы страница стала прямо."""
    base = [90, 270] if lines_vertical(g) else [0, 180]
    rot = {0: lambda x: x, 90: lambda x: cv2.rotate(x, cv2.ROTATE_90_CLOCKWISE), 180: lambda x: cv2.rotate(x, cv2.ROTATE_180), 270: lambda x: cv2.rotate(x, cv2.ROTATE_90_COUNTERCLOCKWISE)}
    s = {r: conf(rot[r](g)) for r in base}
    return max(s, key=s.get), s

if __name__ == "__main__":
    rnd = random.Random(77); np.random.seed(77); ok = 0; n = 0; t = 0
    for i in range(12):
        img, q, *_ = L.case(rnd, i)
        doc = cv2.cvtColor(H.render(img, q, "DOC2", aspect=210 / 297), cv2.COLOR_BGR2GRAY)
        doc = cv2.resize(doc, (doc.shape[1] * 1000 // doc.shape[0], 1000))
        for applied, fix in ((0, 0), (90, 270), (180, 180), (270, 90)):
            r = {0: doc, 90: cv2.rotate(doc, cv2.ROTATE_90_CLOCKWISE), 180: cv2.rotate(doc, cv2.ROTATE_180), 270: cv2.rotate(doc, cv2.ROTATE_90_COUNTERCLOCKWISE)}[applied]
            t0 = time.time(); got, s = detect(r); t += time.time() - t0
            n += 1; ok += got == fix
            if got != fix: print("ошибка: повернута на", applied, "найдено", got, s)
    print(f"ориентация верна: {ok}/{n}, {1000 * t / n:.0f} мс на страницу (CLI, x86)")
