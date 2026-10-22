"""Synthetic order «по строевой части» with § lead + roman subsections and a sample excerpt for its first leave item (structure like a real unit order; all names invented)."""
import sys,os
from docx import Document
from docx.shared import Pt,Mm
from docx.enum.text import WD_ALIGN_PARAGRAPH as A
OUT=(sys.argv[1] if len(sys.argv)>1 else 'leavecases').rstrip('/')+'/';os.makedirs(OUT,exist_ok=True)
def doc():
  d=Document();s=d.sections[0];s.top_margin=Mm(20);s.bottom_margin=Mm(20);s.left_margin=Mm(30);s.right_margin=Mm(10);return d
def P(d,t,al=A.JUSTIFY,fl=False):
  p=d.add_paragraph();r=p.add_run(t);r.font.size=Pt(14);p.alignment=al;p.paragraph_format.space_after=Pt(0)
  if fl:p.paragraph_format.first_line_indent=Mm(12.5)
HEAD=['КОМАНДИРА ВОЙСКОВОЙ ЧАСТИ 11111','по строевой части','« 19 » октября 2026 г. № 338 ДСП','с. Энское']
ITEMS=[('1. Рядового Тестова Владимира Евгеньевича, СА-570942, гранатометчика мотострелковой роты 1 мотострелкового батальона, в отпуск по болезни (30 суток) с выездом в с. Началово Астраханской области с 17 октября по 15 ноября 2026 года. Исключить с продовольственного обеспечения с 20 октября 2026 года.','Основание: рапорт № 16972 от 17 октября 2026 года, заключение ВВК № 139/3536.'),
 ('2. Лейтенанта Халилова Халима Шинабековича, ВС-162226, командира стрелкового взвода, в основной отпуск за 2025 год (12 суток) с выездом в г. Энск с 20 октября по 31 октября 2026 года.','Основание: рапорт № 17017 от 19 октября 2026 года.'),
 ('3. Сержанта Образцова Максима Александровича, МТ-926082, старшего стрелка 2 мотострелковой роты, в основной отпуск за 2026 год (10 суток) с выездом в г. Энск с 20 октября по 29 октября 2026 года.','Основание: рапорт № 17018 от 19 октября 2026 года.')]
SIGN=[['ВрИО КОМАНДИРА ВОЙСКОВОЙ ЧАСТИ 11111','гвардии подполковник','С.Командиров'],['ВрИО НАЧАЛЬНИКА ШТАБА ВОЙСКОВОЙ ЧАСТИ 11111','гвардии подполковник','А.Штабной']]
d=doc()
for t in ['Для служебного пользования','Экз. № 1','','ПРИКАЗ']+HEAD+['','','§ 5','','Полагать прибывшими и приступившими к исполнению служебных обязанностей:','I. Из отпуска:',
 '1. Рядового Тестова Владимира Евгеньевича, СА-570942, гранатометчика мотострелковой роты 1 мотострелкового батальона, из основного отпуска за 2026 год.','Основание: рапорт № 17001 от 18 октября 2026 года.','','§ 6','','Полагать убывшими:','I. В отпуск:']:P(d,t,A.CENTER if not t[:2].isdigit() else A.JUSTIFY)
for a,b in ITEMS:P(d,a,fl=True);P(d,b)
P(d,'II. В командировку:',fl=True);P(d,'1. Сержанта Командировочного Ивана Петровича, АВ-111111, водителя, в г. Энск с 20 октября по 25 октября 2026 года.',fl=True);P(d,'Основание: рапорт № 17020 от 19 октября 2026 года.')
P(d,'');P(d,'')
for i,b in enumerate(SIGN):
  if i:P(d,'')
  P(d,b[0],A.CENTER);P(d,b[1],A.CENTER);P(d,b[2],A.RIGHT)
d.save(OUT+'Приказ 338 от 19.10.2026.docx')
# the sample excerpt: one blank after the header, one before signatures, one before «ВЕРНО:»
d=doc()
for t in ['ВЫПИСКА ИЗ ПРИКАЗА']+HEAD:P(d,t,A.CENTER)
P(d,'',A.CENTER);P(d,'§ 6',A.CENTER);P(d,'',A.CENTER)
P(d,'Полагать убывшими:',fl=True);P(d,'I. В отпуск:',fl=True);P(d,ITEMS[0][0],fl=True);P(d,ITEMS[0][1]);P(d,'')
for i,b in enumerate(SIGN):
  if i:P(d,'')
  P(d,b[0],A.CENTER);P(d,b[1],A.CENTER);P(d,b[2],A.RIGHT)
P(d,'');P(d,'ВЕРНО:',A.LEFT)
for t in ['ВрИО НАЧАЛЬНИКА ОТДЕЛЕНИЯ КОМПЛЕКТОВАНИЯ','ЗАМЕСТИТЕЛЯ НАЧАЛЬНИКА ШТАБА','ВОЙСКОВОЙ ЧАСТИ 11111','гвардии лейтенант']:P(d,t,A.CENTER)
P(d,'С.Заверитель',A.RIGHT)
d.save(OUT+'Выписка отпуск образец.docx')
