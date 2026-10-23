# Копия DocumentDetector.kt на Python (OpenCV) для быстрой настройки на синтетическом наборе.
# Не входит в приложение. Запуск: pip install opencv-python-headless numpy && python3 detector.py
import cv2, numpy as np, math
import synthetic as det
from synthetic import to_quad, convex, angles_ok, poly_area

def support(q, edges, n=40):
    """Доля точек на сторонах четырёхугольника, у которых в окрестности 2 px есть граница."""
    h,w=edges.shape; hit=0; tot=0
    for i in range(4):
        a=q[i]; b=q[(i+1)%4]
        for t in np.linspace(0.08,0.92,n):
            x=int(round(a[0]+(b[0]-a[0])*t)); y=int(round(a[1]+(b[1]-a[1])*t)); tot+=1
            if 0<=x<w and 0<=y<h and edges[y,x]>0: hit+=1
    return hit/tot

def detect(gray, work=480, min_area=0.12, power=2.0):
    scale=min(1.0, work/max(gray.shape))
    small=cv2.resize(gray,(int(gray.shape[1]*scale),int(gray.shape[0]*scale)),interpolation=cv2.INTER_AREA) if scale<1 else gray.copy()
    bl=cv2.GaussianBlur(small,(5,5),0)
    h,w=small.shape; fa=w*h
    k=cv2.getStructuringElement(cv2.MORPH_RECT,(3,3))
    med=float(np.median(bl)); lo=max(0,0.66*med); hi=max(min(255,1.33*med),lo+20)
    c1=cv2.Canny(bl,lo,hi); c2=cv2.Canny(bl,20,60)
    maps=[cv2.dilate(c1,k), cv2.dilate(c2,k)]
    _,o=cv2.threshold(bl,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    maps.append(cv2.morphologyEx(o,cv2.MORPH_CLOSE,cv2.getStructuringElement(cv2.MORPH_RECT,(9,9))))
    # опорная карта границ: объединение двух Canny, расширенное до ±2 px
    sup=cv2.dilate(cv2.bitwise_or(c1,c2),cv2.getStructuringElement(cv2.MORPH_RECT,(5,5)))
    best=None
    for m in maps:
        cs,_=cv2.findContours(m,cv2.RETR_LIST,cv2.CHAIN_APPROX_SIMPLE)
        for c in cs:
            if cv2.contourArea(c)<min_area*fa: continue
            q=to_quad(c)
            if q is None or not convex(q) or not angles_ok(q): continue
            qa=poly_area(q)
            if qa<min_area*fa or qa>0.995*fa: continue
            r=cv2.minAreaRect(q.astype(np.float32))
            rect=min(1.0,qa/max(1.0,r[1][0]*r[1][1]))
            touches=sum(1 for p in q if p[0]<2 or p[1]<2 or p[0]>w-3 or p[1]>h-3)
            sc=qa/fa*rect*(1-0.15*touches)*support(q,sup)**power
            if best is None or sc>best[1]: best=(q,sc)
    return None if best is None else best[0]/scale

if __name__ == "__main__":
    import functools
    det.detect = functools.partial(detect, power=1.0)
    for seed in (1, 2, 291):
        det.run(n=150 if seed != 291 else 60, seed=seed)
