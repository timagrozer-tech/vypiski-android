"""A phone-like photo of a list sheet: the page lies at an angle on a dark table with a shadow (names invented) — for the sheet-straightening test."""
import sys,os
from PIL import Image,ImageDraw,ImageFont,ImageFilter
OUT=(sys.argv[1] if len(sys.argv)>1 else 'listcases').rstrip('/')+'/';os.makedirs(OUT,exist_ok=True)
import glob;F=(glob.glob('/usr/share/fonts/**/DejaVuSans.ttf',recursive=True)+['C:/Windows/Fonts/arial.ttf'])[0]
font=ImageFont.truetype(F,34)
rows=[('Тестов Владимир Евгеньевич','Отрыв, все отпуска'),('Образцов Максим Александрович','Зачисление, отрыв'),('Халилов Халим Шинабекович','Все отпуска, БВП'),
 ('Неизвестный Иван Иванович','Зачисление, отрыв'),('Командировочный Иван Петрович','Командировки, отрыв'),('Шевченко Павел Олегович','Периоды СОЧ')]
PW,PH=1240,1754;page=Image.new('L',(PW,PH),245);d=ImageDraw.Draw(page)
x0,x1,x2=70,700,1170;y=160;rh=90
for a,b in rows:
  d.rectangle([x0,y,x1,y+rh],outline=30,width=3);d.rectangle([x1,y,x2,y+rh],outline=30,width=3)
  tw=d.textlength(a,font=font);d.text((x0+(x1-x0-tw)/2,y+26),a,fill=25,font=font);d.text((x1+16,y+26),b,fill=25,font=font);y+=rh
# shadow: darker toward the lower right
sh=Image.linear_gradient('L').resize((PW,PH)).point(lambda v:255-int(v*0.45))
page=Image.composite(page,Image.new('L',(PW,PH),0),Image.new('L',(PW,PH),255));page=Image.blend(page,Image.new('L',(PW,PH),0),0)
from PIL import ImageChops
page=ImageChops.multiply(page,sh).convert('RGB')
W,H=2000,2600;bg=Image.new('RGB',(W,H),(70,52,40))
# where the page corners land in the photo (tl,tr,br,bl)
dst=[(330,260),(1690,380),(1840,2330),(170,2200)]
import numpy as np
def coeffs(src,dst):
  A=[];B=[]
  for (x,y),(u,v) in zip(dst,src):A.append([x,y,1,0,0,0,-u*x,-u*y]);B.append(u);A.append([0,0,0,x,y,1,-v*x,-v*y]);B.append(v)
  return np.linalg.solve(np.array(A,float),np.array(B,float)).tolist()
c=coeffs([(0,0),(PW,0),(PW,PH),(0,PH)],dst)
warped=page.transform((W,H),Image.PERSPECTIVE,c,resample=Image.BICUBIC,fillcolor=(0,0,0))
mask=Image.new('L',(PW,PH),255).transform((W,H),Image.PERSPECTIVE,c,resample=Image.BILINEAR,fillcolor=0)
bg.paste(warped,(0,0),mask);bg=bg.filter(ImageFilter.GaussianBlur(1.1))
bg.save(OUT+'Список под углом.jpg',quality=85)
