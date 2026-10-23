import numpy as np, hybrid as H, lab_hybrid as L, itertools, contextlib, io
base = dict(H.PROFILES["DOCUMENT"])
for sat, vmax, grow in itertools.product((40, 50, 60), (235, 245), (2, 3)):
    H.PROFILES["T"] = {**base, "sat_min": sat, "ink_v_max": vmax, "mask_grow": grow}
    print(f"sat {sat} v {vmax} grow {grow}:", end=" "); L.run(lambda i, q: H.render(i, q, "T"), n=15)
