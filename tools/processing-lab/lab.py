"""Измерение качества обработки на синтетических страницах (см. README)."""
import cv2, numpy as np, random, time, sys
import pages, enhance as E


def metrics(out, clean):
    g = out if out.ndim == 2 else cv2.cvtColor(out, cv2.COLOR_BGR2GRAY)
    g = cv2.resize(g, (clean.shape[1], clean.shape[0]), interpolation=cv2.INTER_AREA)
    cg = cv2.cvtColor(clean, cv2.COLOR_BGR2GRAY)
    ink = cg < 128
    bgm = cv2.erode((cg > 250).astype(np.uint8), np.ones((9, 9), np.uint8)).astype(bool)
    m = np.zeros_like(bgm); H, W = bgm.shape; m[int(H*.03):int(H*.97), int(W*.03):int(W*.97)] = True
    bgm &= m; ink &= m
    bg = g[bgm].astype(np.float32); tx = g[ink].astype(np.float32)
    pred = g < 128
    tp = (pred & ink).sum(); fp = (pred & bgm).sum(); fn = (~pred & ink).sum()
    return dict(bg_mean=float(bg.mean()), bg_std=float(bg.std()), bg_p1=float(np.percentile(bg, 1)),
                text_mean=float(tx.mean()), f1=float(2 * tp / max(1, 2 * tp + fp + fn)),
                bg_err=float(fp / max(1, bgm.sum())))


def run(f, n=30, seed=7, **kw):
    rnd = random.Random(seed); np.random.seed(seed)
    rows = []; t = 0
    for i in range(n):
        img, q, clean, kind = pages.make_case(rnd, i)
        t0 = time.time(); out = E.render(img, q, f, **kw); t += time.time() - t0
        m = metrics(out, clean); m["kind"] = kind; rows.append(m)
    agg = {k: float(np.mean([r[k] for r in rows])) for k in rows[0] if k != "kind"}
    agg["worst_f1"] = float(min(r["f1"] for r in rows)); agg["worst_bg_p1"] = float(min(r["bg_p1"] for r in rows))
    agg["ms"] = 1000 * t / n
    return agg, rows


def show(name, agg):
    print(f"{name:34s} фон {agg['bg_mean']:5.1f}±{agg['bg_std']:4.1f} p1 {agg['bg_p1']:5.1f}(худш {agg['worst_bg_p1']:5.1f}) "
          f"текст {agg['text_mean']:5.1f} F1 {agg['f1']:.3f}(худш {agg['worst_f1']:.3f}) фон-ош {agg['bg_err']*100:4.2f}% {agg['ms']:4.0f}мс", flush=True)


if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "base"
    if which == "base":
        show("ORIGINAL", run("ORIGINAL")[0])
        show("GRAY без теней", run("GRAY", shadows=False)[0])
        for v in ("dilate_median", "close_gauss", "dilate_gauss"):
            show(f"GRAY {v}", run("GRAY", cfg={"bg": v})[0])
    if which == "filters":
        cg = {"bg": "close_gauss"}
        for f in ("COLOR", "BOOK", "GRAY", "TEXT_BOOST", "PHOTO"):
            show(f, run(f, cfg=cg)[0])
        for g in (1.3, 1.6, 2.0):
            show(f"TEXT_BOOST gamma {g}", run("TEXT_BOOST", cfg={**cg, "gamma": g})[0])
        for m, C in (("otsu", 0), ("adaptive", 8), ("adaptive", 12), ("adaptive", 18), ("fixed", 0)):
            show(f"BW {m} C={C}", run("BW", cfg={**cg, "bw": m, "C": C})[0])
        for d in ("none", "median3", "gauss", "bilateral"):
            show(f"GRAY denoise {d}", run("GRAY", cfg={**cg, "denoise": d})[0])
    if which == "aspect":
        rnd = random.Random(7); errs = []
        for i in range(30):
            img, q, clean, kind = pages.make_case(rnd, i)
            w, h = E.output_size(q); errs.append(abs(w / h - pages.PW / pages.PH) / (pages.PW / pages.PH))
        print(f"ошибка пропорций без формата: средн {100*np.mean(errs):.1f}% макс {100*max(errs):.1f}%")
    if which == "final":
        for f in ("ORIGINAL", "COLOR", "BOOK", "GRAY", "TEXT_BOOST", "BW", "RECEIPT", "PHOTO"):
            show(f, run(f)[0])
        rnd = random.Random(3); img, q, clean, kind = pages.make_case(rnd, 2)
        a = E.render(img, q, "TEXT_BOOST"); b = E.render(img, q, "TEXT_BOOST")
        print("повторный рендер побитно совпадает:", bool((a == b).all()))
        for f in ("ORIGINAL", "TEXT_BOOST", "BW", "COLOR"):
            cv2.imwrite(f"/tmp/claude-0/-home-claude/92b306c4-d8e6-5527-a31a-ed0d561843f0/scratchpad/lab-{f}.jpg", E.render(img, q, f))
        cv2.imwrite("/tmp/claude-0/-home-claude/92b306c4-d8e6-5527-a31a-ed0d561843f0/scratchpad/lab-input.jpg", img)
