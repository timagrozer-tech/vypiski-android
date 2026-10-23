import cv2, numpy as np, random, sys, time
import hybrid as H, enhance as E, lab

def run(fn, n=30, seed=11):
    rnd = random.Random(seed); np.random.seed(seed)
    st, sg, m_all, t = [], [], [], 0
    for i in range(n):
        img, q, marked, sm, gm, kind = H.make_case(rnd, i)
        t0 = time.time(); out = fn(img, q); t += time.time() - t0
        if out.ndim == 2: out = cv2.cvtColor(out, cv2.COLOR_GRAY2BGR)
        base = E.render(img, q, "COLOR", dn=False)
        st.append(H.preserved(out, sm, base)); sg.append(H.preserved(out, gm, base))
        m_all.append(lab.metrics(out, marked))
    f = lambda k: np.mean([m[k] for m in m_all])
    print(f"печать {np.mean(st):.3f} (худш {min(st):.3f})  подпись {np.mean(sg):.3f} (худш {min(sg):.3f})  "
          f"фон {f('bg_mean'):.1f}±{f('bg_std'):.1f}  текст {f('text_mean'):.1f}  F1 {f('f1'):.3f}  {1000*t/n:.0f} мс", flush=True)
    return st, sg

if __name__ == "__main__":
    for name, fn in [("TEXT_BOOST", lambda i, q: E.render(i, q, "TEXT_BOOST")),
                     ("BW", lambda i, q: cv2.cvtColor(E.render(i, q, "BW"), cv2.COLOR_GRAY2BGR)),
                     ("COLOR", lambda i, q: E.render(i, q, "COLOR"))] + \
                    [(f"HYBRID {p}", (lambda p: lambda i, q: H.render(i, q, p))(p)) for p in H.PROFILES]:
        print(f"{name:18s}", end=" "); run(fn)
    if len(sys.argv) > 1:
        rnd = random.Random(11); img, q, *_ = H.make_case(rnd, 2)
        S="/tmp/claude-0/-home-claude/92b306c4-d8e6-5527-a31a-ed0d561843f0/scratchpad/"
        a=E.warp(img,q); b=H.render(img,q,"DOCUMENT"); c=cv2.cvtColor(E.render(img,q,"BW"),cv2.COLOR_GRAY2BGR)
        hh=900; r=lambda x: cv2.resize(x,(int(x.shape[1]*hh/x.shape[0]),hh))
        cv2.imwrite(S+"hybrid.jpg", np.hstack([r(a),r(c),r(b)]))
