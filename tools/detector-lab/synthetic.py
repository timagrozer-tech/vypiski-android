import cv2, numpy as np, math, random, time, sys

def order(pts):
    c = pts.mean(axis=0)
    ang = np.arctan2(pts[:,1]-c[1], pts[:,0]-c[0])
    p = pts[np.argsort(ang)]
    s = int(np.argmin(p[:,0]+p[:,1]))
    return np.roll(p, -s, axis=0)

def poly_area(p):
    x=p[:,0]; y=p[:,1]; return 0.5*abs(np.dot(x,np.roll(y,-1))-np.dot(y,np.roll(x,-1)))

def convex(p):
    s=0
    for i in range(4):
        a,b,c=p[i],p[(i+1)%4],p[(i+2)%4]
        cr=(b[0]-a[0])*(c[1]-b[1])-(b[1]-a[1])*(c[0]-b[0])
        sg=1 if cr>1e-9 else (-1 if cr<-1e-9 else 0)
        if sg==0: return False
        if s==0: s=sg
        elif sg!=s: return False
    return True

def angles_ok(p):
    for i in range(4):
        a,b,c=p[(i+3)%4],p[i],p[(i+1)%4]
        v1=a-b; v2=c-b
        cos=np.dot(v1,v2)/(np.linalg.norm(v1)*np.linalg.norm(v2)+1e-9)
        d=math.degrees(math.acos(max(-1,min(1,cos))))
        if d<50 or d>130: return False
    return True

def to_quad(c):
    hull=cv2.convexHull(c)
    peri=cv2.arcLength(hull,True)
    eps=0.02
    while eps<=0.08+1e-9:
        a=cv2.approxPolyDP(hull,eps*peri,True)
        if len(a)==4: return order(a.reshape(4,2).astype(float))
        if len(a)<4: break
        eps+=0.01
    return None

def detect(gray, work=480, min_area=0.12, variant=None):
    scale=min(1.0, work/max(gray.shape))
    small=cv2.resize(gray,(int(gray.shape[1]*scale),int(gray.shape[0]*scale)),interpolation=cv2.INTER_AREA) if scale<1 else gray.copy()
    bl=cv2.GaussianBlur(small,(5,5),0)
    h,w=small.shape; fa=w*h
    k=cv2.getStructuringElement(cv2.MORPH_RECT,(3,3))
    med=float(np.median(bl)); lo=max(0,0.66*med); hi=max(min(255,1.33*med),lo+20)
    maps=[cv2.dilate(cv2.Canny(bl,lo,hi),k), cv2.dilate(cv2.Canny(bl,20,60),k)]
    _,o=cv2.threshold(bl,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    maps.append(cv2.morphologyEx(o,cv2.MORPH_CLOSE,cv2.getStructuringElement(cv2.MORPH_RECT,(9,9))))
    if variant: maps=variant(bl,maps)
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
            sc=qa/fa*rect*(1-0.15*touches)
            if best is None or sc>best[1]: best=(q,sc)
    if best is None: return None
    return best[0]/scale

def iou(a,b,w,h):
    m1=np.zeros((h,w),np.uint8); m2=np.zeros((h,w),np.uint8)
    cv2.fillPoly(m1,[a.astype(np.int32)],1); cv2.fillPoly(m2,[b.astype(np.int32)],1)
    i=np.logical_and(m1,m2).sum(); u=np.logical_or(m1,m2).sum(); return i/u if u else 0

def paper(rnd):
    w,h=420,594; t=235+rnd.randint(0,20)
    p=np.zeros((h,w,3),np.uint8); p[:]=(t-rnd.randint(0,9),t,t)
    y=60
    while y<h-80:
        cv2.rectangle(p,(40,y),(40+200+rnd.randint(0,139),y+7),(80,70,70),-1); y+=22+rnd.randint(0,7)
    if rnd.random()<0.5: cv2.circle(p,(300,h-110),45,(180,70,40),5)
    return p

def background(rnd,w,h,kind):
    base=175+rnd.randint(0,29) if kind=="low" else 30+rnd.randint(0,89)
    b=np.zeros((h,w,3),np.uint8); b[:]=(int(base*0.8),int(base*0.9),base)
    for _ in range(400 if kind=="busy" else 120):
        v=max(0,min(255,base+rnd.randint(-40,40)))
        x=rnd.random()*w; y=rnd.random()*h
        cv2.rectangle(b,(int(x),int(y)),(int(x+rnd.random()*40),int(y+rnd.random()*6)),(int(v*0.9),v,v),-1)
    return b

def make_case(rnd,i):
    portrait=rnd.random()<0.5
    w,h=(600,800) if portrait else (800,600)
    kind=["low","busy","shadow","blur","plain"][i%5]
    img=background(rnd,w,h,kind)
    s=min(w,h)*(0.45+rnd.random()*0.35); pw=s*0.75; ph=s*1.06
    cx=w/2+rnd.random()*w*0.12-w*0.06; cy=h/2+rnd.random()*h*0.12-h*0.06
    ang=math.radians(rnd.random()*50-25)
    def corner(dx,dy):
        jx=(rnd.random()-0.5)*0.14*pw; jy=(rnd.random()-0.5)*0.14*ph
        x=dx+jx; y=dy+jy
        return [cx+x*math.cos(ang)-y*math.sin(ang), cy+x*math.sin(ang)+y*math.cos(ang)]
    pts=np.array([corner(-pw/2,-ph/2),corner(pw/2,-ph/2),corner(pw/2,ph/2),corner(-pw/2,ph/2)])
    k=min(1,(w*0.94)/(pts[:,0].max()-pts[:,0].min()),(h*0.94)/(pts[:,1].max()-pts[:,1].min()))
    pts=np.array([[cx+(p[0]-cx)*k, cy+(p[1]-cy)*k] for p in pts])
    sx=w*0.03-pts[:,0].min() if pts[:,0].min()<w*0.03 else (w*0.97-pts[:,0].max() if pts[:,0].max()>w*0.97 else 0)
    sy=h*0.03-pts[:,1].min() if pts[:,1].min()<h*0.03 else (h*0.97-pts[:,1].max() if pts[:,1].max()>h*0.97 else 0)
    pts=pts+[sx,sy]
    pa=paper(rnd)
    M=cv2.getPerspectiveTransform(np.float32([[0,0],[420,0],[420,594],[0,594]]),pts.astype(np.float32))
    warped=cv2.warpPerspective(pa,M,(w,h)); mask=cv2.warpPerspective(np.full((594,420),255,np.uint8),M,(w,h))
    img[mask>0]=warped[mask>0]
    if kind=="shadow":
        g=np.linspace(110/255,0,int(w*0.6)); sh=np.zeros((h,w)); sh[:,:len(g)]=g
        img=(img*(1-sh[...,None])).astype(np.uint8)
    if kind=="blur":
        img=cv2.resize(cv2.resize(img,(w//4,h//4),interpolation=cv2.INTER_AREA),(w,h),interpolation=cv2.INTER_LINEAR)
    return img,pts,kind

def run(n=60, seed=291, **kw):
    rnd=random.Random(seed); res=[]
    for i in range(n):
        img,pts,kind=make_case(rnd,i)
        g=cv2.cvtColor(img,cv2.COLOR_BGR2GRAY)
        t=time.time(); d=detect(g,**kw); dt=time.time()-t
        v=iou(d,pts,img.shape[1],img.shape[0]) if d is not None else 0.0
        res.append((kind,v,dt))
    good=sum(1 for r in res if r[1]>=0.9)
    by={}
    for k,v,_ in res: by.setdefault(k,[]).append(v)
    print(f"IoU>=0.9: {good}/{n}  mean {np.mean([r[1] for r in res]):.3f}  ms/img {1000*np.mean([r[2] for r in res]):.1f}")
    print({k:f"{sum(1 for x in v if x>=0.9)}/{len(v)} min {min(v):.2f}" for k,v in by.items()})
    return res

if __name__=="__main__":
    run()
