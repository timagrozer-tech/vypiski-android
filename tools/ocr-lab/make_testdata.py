"""Набор testdata/ocr: синтетические обезличенные служебные документы (кириллица, реальные шрифты),
сфотографированные «камерой» (перспектива, тень, шум, JPEG). Эталонный текст — рядом (.gt.txt),
углы листа — .quad.txt (чтобы OCR измерялся отдельно от поиска углов).

Все ФИО, номера и даты вымышлены. Повторный запуск с тем же зерном даёт те же файлы.
"""
import cv2, numpy as np, random, math, os, sys
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "processing-lab"))
import pages  # освещение и сцена

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "testdata", "ocr")
FONTS = ["/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
         "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf"]
SURN = ["Иванов", "Петров", "Сидоренко", "Кузнецов", "Смирнов", "Волков", "Фёдоров", "Морозов", "Лебедев", "Ковалёв"]
NAME = ["Иван Иванович", "Пётр Сергеевич", "Алексей Николаевич", "Дмитрий Андреевич", "Сергей Викторович", "Олег Юрьевич"]
RANK = ["рядовой", "ефрейтор", "младший сержант", "сержант", "старший лейтенант", "капитан", "майор"]

def doc_text(rnd):
    fio = f"{rnd.choice(SURN)} {rnd.choice(NAME)}"
    d = f"{rnd.randint(1, 28):02d}.{rnd.randint(1, 12):02d}.20{rnd.randint(22, 26)}"
    kind = rnd.choice(["СПРАВКА", "РАПОРТ", "ВЫПИСКА ИЗ ПРИКАЗА"])
    lines = [kind, f"№ {rnd.randint(10, 999)} от {d}", ""]
    body = {
        "СПРАВКА": [f"Выдана: {rnd.choice(RANK)} {fio}, в том, что он",
                    "действительно проходит военную службу по контракту",
                    f"в войсковой части 65384 с {d} по настоящее время.",
                    "Справка выдана для предъявления по месту требования."],
        "РАПОРТ": ["Командиру войсковой части 65384",
                   f"Прошу Вашего ходатайства о предоставлении мне основного",
                   f"отпуска за 2026 год продолжительностью {rnd.randint(10, 30)} суток",
                   f"с выездом в город {rnd.choice(['Тверь', 'Псков', 'Омск', 'Казань'])}.",
                   f"{rnd.choice(RANK).capitalize()} {fio}"],
        "ВЫПИСКА ИЗ ПРИКАЗА": [f"командира войсковой части 65384 № {rnd.randint(100, 400)}",
                               "по строевой части",
                               f"§ {rnd.randint(1, 20)}. Полагать прибывшим и вступившим в должность",
                               f"{rnd.choice(RANK)} {fio}, назначенного приказом",
                               f"от {d}, зачислить в списки личного состава."],
    }[kind]
    lines += body + ["", "Командир войсковой части 65384", f"полковник {rnd.choice(['А. Петров', 'В. Соколов', 'Н. Орлов'])}"]
    return lines

def render_page(rnd, lines):
    W, H = pages.PW, pages.PH
    img = Image.new("RGB", (W, H), (255, 255, 255))
    d = ImageDraw.Draw(img)
    font = rnd.choice(FONTS)
    size = rnd.choice([22, 24, 26])
    f = ImageFont.truetype(font, size); fb = ImageFont.truetype(font.replace(".ttf", "-Bold.ttf") if "Condensed" not in font else font, size + 4)
    y = 90
    for i, l in enumerate(lines):
        if not l: y += size; continue
        ff = fb if i == 0 else f
        tw = d.textlength(l, font=ff)
        x = (W - tw) / 2 if i < 2 else 70
        d.text((x, y), l, font=ff, fill=(20, 20, 25))
        y += int(size * 1.6)
    return cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)

def photo(rnd, clean, kind):
    w, h = 1200, 1600
    bg = np.zeros((h, w, 3), np.uint8); bg[:] = (rnd.randint(60, 120), rnd.randint(70, 130), rnd.randint(80, 140))
    s = rnd.uniform(0.82, 0.92); pw, ph = w * s, w * s * pages.PH / pages.PW
    if ph > h * 0.92: k = h * 0.92 / ph; pw *= k; ph *= k
    cx, cy = w / 2, h / 2; jit = lambda: rnd.uniform(-0.05, 0.05) * pw
    q = np.float32([[cx - pw / 2 + jit(), cy - ph / 2 + jit()], [cx + pw / 2 + jit(), cy - ph / 2 + jit()],
                    [cx + pw / 2 + jit(), cy + ph / 2 + jit()], [cx - pw / 2 + jit(), cy + ph / 2 + jit()]])
    M = cv2.getPerspectiveTransform(np.float32([[0, 0], [pages.PW, 0], [pages.PW, pages.PH], [0, pages.PH]]), q)
    paper = clean.astype(np.float32) * np.array([rnd.uniform(.85, 1), rnd.uniform(.9, 1), rnd.uniform(.92, 1)], np.float32)
    warped = cv2.warpPerspective(paper, M, (w, h)); mask = cv2.warpPerspective(np.full((pages.PH, pages.PW), 255, np.uint8), M, (w, h))
    img = bg.astype(np.float32); img[mask > 0] = warped[mask > 0]
    img *= pages.illumination(rnd, w, h, kind)[..., None]
    img += np.random.default_rng(rnd.randint(0, 1 << 30)).normal(0, 4.0, img.shape)
    if kind == "blur": img = cv2.GaussianBlur(img, (0, 0), 1.2)
    img = np.clip(img, 0, 255).astype(np.uint8)
    return img, q

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    rnd = random.Random(2026); np.random.seed(2026)
    kinds = ["plain", "gradient", "shadow", "hard", "dim", "blur"]
    for i in range(12):
        lines = doc_text(rnd); kind = kinds[i % len(kinds)]
        img, q = photo(rnd, render_page(rnd, lines), kind if kind != "blur" else "gradient")
        if kind == "blur": img = cv2.GaussianBlur(img, (0, 0), 1.1)
        base = os.path.join(OUT, f"page{i + 1:02d}-{kind}")
        cv2.imwrite(base + ".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, 88])
        open(base + ".gt.txt", "w", encoding="utf-8").write("\n".join(l for l in lines if l) + "\n")
        open(base + ".quad.txt", "w").write(";".join(f"{x:.1f},{y:.1f}" for x, y in q) + "\n")
    print("готово:", OUT)
