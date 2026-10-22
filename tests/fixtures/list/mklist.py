"""A photo-like picture of a list «ФИО | что нужно» (a two-column table, slight rotation, grey paper) — for the OCR test; names are invented."""
import sys,os
from PIL import Image,ImageDraw,ImageFont,ImageFilter
OUT=(sys.argv[1] if len(sys.argv)>1 else 'listcases').rstrip('/')+'/';os.makedirs(OUT,exist_ok=True)
F='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
if not os.path.exists(F):
  import glob;F=(glob.glob('/usr/share/fonts/**/DejaVuSans.ttf',recursive=True)+glob.glob('C:/Windows/Fonts/arial.ttf'))[0]
font=ImageFont.truetype(F,30)
ROWS=[('Тестов Владимир Евгеньевич','Отрыв, все отпуска'),('Образцов Максим Александрович','Зачисление, отрыв'),
 ('Халилов Халим Шинабекович','Все отпуска, БВП'),('Неизвестный Иван Иванович','Зачисление, отрыв'),
 ('Командировочный Иван Петрович','Командировки,'),('','отрыв')]
W,H=1500,1000;img=Image.new('RGB',(W,H),(214,212,218));d=ImageDraw.Draw(img)
x0,x1,x2=110,760,1390;y=120;rh=72
rows=[('Тестов Владимир Евгеньевич','Отрыв, все отпуска'),('Образцов Максим Александрович','Зачисление, отрыв'),('Халилов Халим Шинабекович','Все отпуска, БВП'),('Неизвестный Иван Иванович','Зачисление, отрыв'),('Командировочный Иван Петрович','Командировки, отрыв')]
for i,(a,b) in enumerate(rows):
  h=rh*(2 if i==4 else 1)
  d.rectangle([x0,y,x1,y+h],outline=(40,40,40),width=2);d.rectangle([x1,y,x2,y+h],outline=(40,40,40),width=2)
  tw=d.textlength(a,font=font);d.text((x0+(x1-x0-tw)/2,y+(h-36)/2),a,fill=(30,30,30),font=font)
  if i==4:d.text((x1+14,y+8),'Командировки,',fill=(30,30,30),font=font);d.text((x1+14,y+8+rh),'отрыв',fill=(30,30,30),font=font)
  else:d.text((x1+14,y+16),b,fill=(30,30,30),font=font)
  y+=h
img=img.rotate(0.8,resample=Image.BICUBIC,fillcolor=(90,90,90)).filter(ImageFilter.GaussianBlur(0.6))
img.save(OUT+'Список.jpg',quality=88)
