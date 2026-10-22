/* Stage 12.74 — «Таблица приказов»: who, which order, when, what.
 * Pure functions (no DOM): run on the page, in a worker and in Node tests.
 * An order is split into points (§, numbered points, Word auto-numbering); in every point the persons are found
 * (full name or surname with initials), brought to the nominative («рядовому Иванову И.И.» → ИВАНОВ И.И.),
 * and the point is classified (leave of which kind and for which year, appointment, transfer, enlistment, …).
 * Nothing is invented: when a form cannot be resolved for certain the row is marked «проверить»,
 * and the verbatim text of the point always travels with the row.
 */
(function(root){'use strict';
const VERSION=6; // 12.79.1: long lists keep only their own lines (was: the whole point in every row)
const lc=s=>String(s||'').toLowerCase().replace(/ё/g,'е');
const clean=s=>String(s||'').replace(/[   ]/g,' ').replace(/[ \t]+/g,' ').trim();

/* ---------- ranks ---------- */
// lemma → regexp of its forms (incl. «старший …», «гвардии …», «… юстиции», «… медицинской службы»)
const NOUN=s=>`${s}(?:а|у|ом|е)?`;
const RANKS=[
 ['генерал-полковник',NOUN('генерал-полковник')],['генерал-лейтенант',NOUN('генерал-лейтенант')],['генерал-майор',NOUN('генерал-майор')],
 ['капитан-лейтенант',NOUN('капитан-лейтенант')],
 ['капитан 1 ранга','капитан(?:а|у|ом|е)?\\s+1\\s*ранга'],['капитан 2 ранга','капитан(?:а|у|ом|е)?\\s+2\\s*ранга'],['капитан 3 ранга','капитан(?:а|у|ом|е)?\\s+3\\s*ранга'],
 ['подполковник',NOUN('подполковник')],['полковник',NOUN('полковник')],['майор',NOUN('майор')],
 ['старший лейтенант','старш(?:ий|его|ему|им|ем)\\s+'+NOUN('лейтенант')],['младший лейтенант','младш(?:ий|его|ему|им|ем)\\s+'+NOUN('лейтенант')],['лейтенант',NOUN('лейтенант')],
 ['капитан',NOUN('капитан')],
 ['старший прапорщик','старш(?:ий|его|ему|им|ем)\\s+'+NOUN('прапорщик')],['прапорщик',NOUN('прапорщик')],
 ['старший мичман','старш(?:ий|его|ему|им|ем)\\s+'+NOUN('мичман')],['мичман',NOUN('мичман')],
 ['старшина','старшин(?:а|ы|е|у|ой)'],['главный старшина','главн(?:ый|ого|ому|ым|ом)\\s+старшин(?:а|ы|е|у|ой)'],
 ['старший сержант','старш(?:ий|его|ему|им|ем)\\s+'+NOUN('сержант')],['младший сержант','младш(?:ий|его|ему|им|ем)\\s+'+NOUN('сержант')],['сержант',NOUN('сержант')],
 ['старший матрос','старш(?:ий|его|ему|им|ем)\\s+'+NOUN('матрос')],['матрос',NOUN('матрос')],
 ['ефрейтор',NOUN('ефрейтор')],['рядовой','рядов(?:ой|ого|ому|ым|ом)'],
 ['курсант',NOUN('курсант')],['солдат',NOUN('солдат')],
];
const RANK_RE=new RegExp(`(?:гвардии\\s+)?(${RANKS.map(r=>r[1]).join('|')})(?:\\s+(?:юстиции|медицинской\\s+службы|запаса|в\\s+отставке))?\\s+$`,'iu');
const RANK_EXACT=RANKS.map(([lemma,re])=>[lemma,new RegExp('^'+re+'$','iu')]);
function rankBefore(prefix){const m=RANK_RE.exec(lc(prefix).slice(-60));if(!m)return null;const w=m[1];
 for(const [lemma,re] of RANK_EXACT)if(re.test(w))return {rank:lemma,kase:rankCase(w)};return null}
function rankCase(w){w=lc(w).split(/\s+/).pop();
 if(/(ому|ему)$/.test(w))return 'dat';if(/(ого|его)$/.test(w))return 'gen';if(/(ым|им)$/.test(w))return 'ins';
 if(/старшин/.test(w)){if(/е$/.test(w))return 'dat';if(/у$/.test(w))return 'acc';if(/ы$/.test(w))return 'gen';if(/ой$/.test(w))return 'ins';return 'nom'}
 if(/(ой|ий|ый)$/.test(w))return 'nom';if(/у$/.test(w))return 'dat';if(/а$/.test(w))return 'gen';if(/ом$/.test(w))return 'ins';if(/е$/.test(w))return 'pre';return 'nom'}
// other «who» words right before a name: «служащему», «водителю», «гражданину», «военнослужащего»
function wordCase(prefix){const m=/([а-яё-]+)\s*$/u.exec(lc(prefix));if(!m)return null;const w=m[1];
 if(/(ому|ему)$/.test(w))return 'dat';if(/(ого|его)$/.test(w))return 'gen';
 if(/^(служащ|военнослужащ|гражданин|работник|водител|механик|техник|стрелк|наводчик|оператор|командир|начальник|заместител|помощник|инструктор|специалист|кладовщик|повар|делопроизводител|писар|связист)/.test(w)){if(/(у|ю|е)$/.test(w))return 'dat';if(/(а|я)$/.test(w))return 'gen'}
 return null}

/* ---------- verbs → the case they govern ---------- */
const DAT_VERBS=/(предостав|объяв|присво|выплат|установ|разреш|выдат|оказат|вручит|продлит|возложит|поручит|отказат)/;
const ACC_VERBS=/(назнач|зачисл|исключ|уволит|полагат|считат|направ|перевест|переместит|наград|освобод|допуст|командиров|отозват|отчисл|принят|включит|восстанов|поощр|премир|прикомандир)/;

/* ---------- surname → nominative ---------- */
// returns {nom, gender:'m'|'f'|'', sure}
function surnameNom(word,opts={}){
 const parts=lc(word).split('-');
 if(parts.length>1&&parts.every(x=>x.length>2)){const r=parts.map(x=>surnameOne(x,opts));return {nom:r.map(x=>x.nom).join('-'),gender:r.find(x=>x.gender)?.gender||opts.gender||'',sure:r.every(x=>x.sure)}}
 return surnameOne(word,opts)}
function surnameOne(word,{gender='',kase=''}={}){
 const w=lc(word);let nom=w,sure=true,g=gender;
 // indeclinable: Шевченко, Черных, Дурново
 if(/(ко|их|ых|аго|яго|ово)$/.test(w))return {nom:w,gender:g,sure:true};
 // feminine forms that can only be female
 if(/(ов|ев|ёв|ин|ын)ой$/.test(w))return {nom:w.replace(/ой$/,'а'),gender:'f',sure:true};
 if(/(ск|цк)ой$/.test(w)&&g!=='m')return {nom:w.replace(/ой$/,'ая'),gender:'f',sure:kase!=='nom'};
 if(/(ск|цк)ую$/.test(w))return {nom:w.replace(/ую$/,'ая'),gender:'f',sure:true};
 if(/(ая|яя)$/.test(w))return {nom:w,gender:'f',sure:true};
 if(/ую$/.test(w))return {nom:w.replace(/ую$/,'ая'),gender:'f',sure:false};
 // adjectival masculine
 if(/(ск|цк)(ого|ому|им|ом)$/.test(w))return {nom:w.replace(/(ого|ому|им|ом)$/,'ий'),gender:'m',sure:true};
 if(/(ск|цк)ий$/.test(w))return {nom:w,gender:'m',sure:true};
 if(/(ий|ый|ой)$/.test(w)&&g!=='f'){if(/ой$/.test(w)&&(kase&&kase!=='nom'))return {nom:w.replace(/ой$/,'ая'),gender:'f',sure:false};return {nom:w,gender:'m',sure:true}}
 if(/(его|ему)$/.test(w))return {nom:w.replace(/(его|ему)$/,'ий'),gender:'m',sure:false};
 if(/(ого|ому)$/.test(w))return {nom:w.replace(/(ого|ому)$/,'ый'),gender:'m',sure:false};
 // -ов/-ин family
 if(OV_END.test(w))return {nom:w,gender:g||'m',sure:g!=='f'};
 if(OV_INS.test(w))return {nom:w.replace(/(ым|е)$/,''),gender:'m',sure:true};
 if(OV_A.test(w)){ // Иванова: male gen/acc or female nom
  if(g==='f'||kase==='nom')return {nom:w,gender:'f',sure:g==='f'};
  return {nom:w.slice(0,-1),gender:'m',sure:g==='m'||kase==='gen'||kase==='acc'}}
 if(OV_U.test(w)){ // Иванову: male dat or female acc
  if(g==='f'||((kase==='acc'||kase==='gen')&&g!=='m'))return {nom:w.slice(0,-1)+'а',gender:'f',sure:g==='f'||kase==='gen'};
  return {nom:w.slice(0,-1),gender:'m',sure:g==='m'||kase==='dat'}}
 // female of consonant surnames do not decline: keep when female
 if(g==='f')return {nom:w,gender:'f',sure:!/(у|ю|ой|ей|е|ы|и)$/.test(w)||/(ко|их|ых)$/.test(w)};
 // masculine consonant surnames: Шевчука/Шевчуку/Шевчуком/Шевчуке
 const cons=/[бвгджзклмнпрстфхцчшщ]$/;
 if(cons.test(w))return {nom:w,gender:g||'m',sure:true};
 let m;
 if((m=/^(.+[бвгджзклмнпрстфхцчшщ])(а|у|ом|ем|е)$/.exec(w))&&kase!=='nom'){const stem=m[1];const fleeting=/(ц|к)$/.test(stem)&&/[бвгджзклмнпрстфхчшщ]ц$|[бвгджзклмнпрстфхчшщ]к$/.test(stem);
  return {nom:stem,gender:'m',sure:!fleeting&&!!kase}}
 if((m=/^(.+)(я|ю|ем|е)$/.exec(w))&&/й$/.test(m[1]+'й')&&kase&&kase!=='nom'&&/[аеиоуы]$/.test(m[1]))return {nom:m[1]+'й',gender:'m',sure:false}; // Гайдая → Гайдай
 if(/(а|я)$/.test(w))return {nom:w,gender:g,sure:false};
 return {nom:w,gender:g,sure:false}}

const OV_END=/(ов|ев|ёв|ин|ын)$/,OV_INS=/(ов|ев|ёв|ин|ын)(ым|е)$/,OV_A=/(ов|ев|ёв|ин|ын)а$/,OV_U=/(ов|ев|ёв|ин|ын)у$/;
/* patronymic → gender and case */
function patronymicInfo(p){const w=lc(p);
 if(/(вич|ьич|ич)$/.test(w))return {gender:'m',kase:'nom'};if(/ича$/.test(w))return {gender:'m',kase:'gen'};if(/ичу$/.test(w))return {gender:'m',kase:'dat'};if(/ичем$/.test(w))return {gender:'m',kase:'ins'};if(/иче$/.test(w))return {gender:'m',kase:'pre'};
 if(/(вна|чна)$/.test(w))return {gender:'f',kase:'nom'};if(/(вны|чны)$/.test(w))return {gender:'f',kase:'gen'};if(/(вне|чне)$/.test(w))return {gender:'f',kase:'dat'};if(/(вну|чну)$/.test(w))return {gender:'f',kase:'acc'};if(/(вной|чной)$/.test(w))return {gender:'f',kase:'ins'};
 return null}
const firstNameGender=n=>{const w=lc(n);if(/(а|я)$/.test(w)&&!/^(илья|никита|кузьма|фома|лука|савва|данила|гаврила|миша|саша|женя|ваня|паша|коля)$/.test(w))return 'f';if(/(ы|е|у|ю|ой|ей)$/.test(w))return '';return 'm'};

/* ---------- persons in a line ---------- */
const UP='А-ЯЁ',LO='а-яё';
const SUR=`(?:[${UP}][${LO}]+(?:-[${UP}][${LO}]+)?|[${UP}]{2,}(?:-[${UP}]{2,})?)`;
const FULL_RE=new RegExp(`(${SUR})\\s+([${UP}][${LO}]+)\\s+([${UP}][${LO}]+(?:вич|вн|чн|ич|ьич)[${LO}]{0,3})(?![${LO}])`,'gu');
const INIT_RE=new RegExp(`(?<![${UP}]\\.\\s?)(${SUR})\\s+([${UP}])\\.\\s?([${UP}])\\.?(?![${LO}])`,'gu');
const NOT_SURNAME=/^(приказ|приказываю|основание|командир|начальник|заместитель|врио|войсковой|части|часть|россии|российской|федерации|москва|отпуск|рапорт|список|приложение|выписка|верно|утверждаю|согласовано|г|гор|ул|пр|кв|д)$/u;
const MAYBE=/[А-ЯЁ][а-яёА-ЯЁ-]+\s+[А-ЯЁ]/u;
function personsIn(line,{verbCase=''}={}){
 const out=[],taken=[];const text=String(line);if(!MAYBE.test(text))return out;
 const add=(m,surname,name,patr,i1,i2)=>{const s=m.index,e=s+m[0].length;if(taken.some(([a,b])=>s<b&&e>a))return;if(NOT_SURNAME.test(lc(surname)))return;taken.push([s,e]);
  const prefix=text.slice(Math.max(0,s-70),s);const rk=rankBefore(prefix);const pi=patr?patronymicInfo(patr):null;
  const kase=pi?.kase||rk?.kase||wordCase(prefix)||verbCase||'';let gender=pi?.gender||(name?firstNameGender(name):'');
  const r=surnameNom(surname,{gender,kase});
  const initials=name?`${name[0]}.${patr[0]}.`:`${i1}.${i2}.`;
  out.push({at:s,end:e,raw:m[0],surname:r.nom.toLocaleUpperCase('ru-RU'),initials:initials.toLocaleUpperCase('ru-RU'),fio:`${r.nom.toLocaleUpperCase('ru-RU')} ${initials.toLocaleUpperCase('ru-RU')}`,
   full:name?`${cap(r.nom)} ${nameNom(name,pi)} ${patrNom(patr,pi)}`:'',gender:r.gender||gender,rank:rk?.rank||'',sure:r.sure,kase})};
 let m;FULL_RE.lastIndex=0;while((m=FULL_RE.exec(text)))add(m,m[1],m[2],m[3]);
 INIT_RE.lastIndex=0;while((m=INIT_RE.exec(text)))add(m,m[1],'','',m[2],m[3]);
 return out.sort((a,b)=>a.at-b.at)}
const cap=w=>w?w[0].toLocaleUpperCase('ru-RU')+w.slice(1):w;
function patrNom(p,pi){const w=lc(p);if(!pi)return cap(w);if(pi.gender==='m')return cap(w.replace(/(ича|ичу|ичем|иче)$/,'ич'));return cap(w.replace(/(вны|вне|вну|вной)$/,'вна').replace(/(чны|чне|чну|чной)$/,'чна'))}
function nameNom(n,pi){const w=lc(n);if(!pi||pi.kase==='nom')return cap(w);
 if(pi.gender==='f'){return cap(w.replace(/(ии)$/,'ия').replace(/ье$/,'ья').replace(/(ы|е|у|ой)$/,'а').replace(/(и|ю|ей)$/,'я'))}
 if(/(ия|ье|ию|ием|ии)$/.test(w))return cap(w.replace(/(ия|ье|ию|ием|ии)$/,'ий'));
 if(/(ея|ею|еем|ее|ея)$/.test(w))return cap(w.replace(/(ея|ею|еем|ее)$/,'ей'));
 if(/(ая|аю|аем|ае)$/.test(w))return cap(w.replace(/(ая|аю|аем|ае)$/,'ай'));
 if(/(ыя|ою|оем|ое|оя)$/.test(w))return cap(w.replace(/(оя|ою|оем|ое)$/,'ой'));
 if(/^(ильи|илье|илью|ильей|никиты|никите|никиту|никитой|кузьмы|фомы|луки|саввы|данилы|гаврилы)$/.test(w))return cap(w.replace(/(и|е|ю|ей)$/,'я').replace(/(ы|е|у|ой)$/,'а'));
 if(/[бвгдзклмнпрстф](?:я|ю|ем)$/.test(w))return cap(w.replace(/(я|ю|ем)$/,'ь')); // Игоря, Игорю, Игорем → Игорь
 const base=w.replace(/(ом|а|у|е)$/,'');const FLEET={'павл':'павел','льв':'лев'};return cap(FLEET[base]||base)}

/* ---------- dates ---------- */
const MONTHS=['январ','феврал','март','апрел','ма','июн','июл','август','сентябр','октябр','ноябр','декабр'];
const monthNum=w=>{w=lc(w);if(/^ма[йя]$/.test(w))return 5;for(let i=0;i<12;i++)if(i!==4&&w.startsWith(MONTHS[i]))return i+1;return 0};
const pad=n=>String(n).padStart(2,'0');
const DATE_SRC=String.raw`[«"„]?\s*(\d{1,2})\s*[»"“]?\s+([а-яё]+)\s+(\d{4})\s*(?:г\.?|года)?|(\d{1,2})\.(\d{1,2})\.(\d{4}|\d{2})(?:\s*г\.?)?|(\d{4})-(\d{2})-(\d{2})`;
const DATE_RE=new RegExp(DATE_SRC,'iu'),DATE_AT=new RegExp('^\\s*(?:'+DATE_SRC+')','iu');
function dateAt(s,anchored=false){const m=(anchored?DATE_AT:DATE_RE).exec(s);if(!m)return null;let d,mo,y;if(m[1]){d=+m[1];mo=monthNum(m[2]);y=+m[3];if(!mo)return null}else if(m[4]){d=+m[4];mo=+m[5];y=+m[6];if(y<100)y+=2000}else{y=+m[7];mo=+m[8];d=+m[9]}
 if(!(d>=1&&d<=31&&mo>=1&&mo<=12&&y>=1950&&y<=2100))return null;return {text:`${pad(d)}.${pad(mo)}.${y}`,index:m.index,len:m[0].length}}
function spanIn(s){const t=String(s);const re=/(?:^|[\s(,])с\s+/giu;let m,first=null;
 while((m=re.exec(t))){const rest=t.slice(m.index+m[0].length);const a=dateAt(rest,true);if(!a)continue;const tail=rest.slice(a.index+a.len);
  const by=/^\s*(?:г\.?|года)?\s*(?:по|до)\s+/iu.exec(tail);if(by){const b=dateAt(tail.slice(by[0].length),true);if(b)return {start:a.text,end:b.text}}
  if(!first)first={start:a.text,end:''}}
 return first}
function periodIn(s){const p=spanIn(s);return p&&p.start&&p.end?`с ${p.start} по ${p.end}`:''}

/* ---------- classification ---------- */
const CATS=[
 ['Отпуск',/отпуск/],
 ['Командировка',/командиров/],
 ['Изменение персональных данных',/(?:смен\S*|изменени\S*)\s+(?:фамили|имени|отчеств|персональн|анкетн|учетн\S*\s+данн)|в\s+связи\s+со\s+сменой|считать\s+.{0,80}\s+фамили/],
 ['Зачисление в списки',/зачисл\S*\s+в\s+списк|полагать\s+прибывш\S*\s+(?:для|к)\s+(?:дальнейшего\s+)?прохождени|прибывш\S*\s+из\s+(?!отпуска|командировки)|зачислить\s+в\s+распоряжени/],
 ['Исключение из списков',/исключ\S*\s+из\s+списк|полагать\s+убывш\S*\s+(?:для|к)\s+(?:дальнейшего\s+)?прохождени|исключить\s+из\s+распоряжени/],
 ['Назначение на должность',/назнач\S*\s+на\s+(?:воинск\S*\s+)?должност|назнач\S*\s+(?:временно\s+)?исполняющ|вступивш\S*\s+в\s+(?:исполнение|командование)|допустить\s+к\s+(?:временному\s+)?исполнени/],
 ['Освобождение от должности',/освобод\S*\s+от\s+(?:занимаемой\s+|воинской\s+)?должност|сдавш\S*\s+дела|освободить\s+от\s+исполнени/],
 ['Перемещение / перевод',/перевест|переведен|перемест|перемещен|прикомандир|откомандир|направить\s+для\s+дальнейшего|зачислить\s+в\s+распоряжени/],
 ['Увольнение',/уволит|увольнен|уволен/],
 ['Присвоение звания',/присвоит\S*\s+(?:очередное\s+|первое\s+)?(?:воинское\s+)?звани|присвоен\S*\s+звани/],
 ['Поощрение',/благодарност|поощр|наградит|наградить|грамот|ценным\s+подарком|премир/],
 ['Взыскание',/выговор|замечани|взыскан|предупрежден\S*\s+о\s+неполном/],
 ['Выплаты и надбавки',/выплат|надбавк|премию|премии|материальн\S*\s+помощ|подъемн|компенсац|денежн\S*\s+довольстви/],
 ['Классная квалификация',/классн\S*\s+квалификац/],
 ['Контракт',/контракт/],
];
/* action of a point: cancellation and change differ from a new grant */
const ACTIONS=[['отмена',/(?:^|\s)(?:отменить|отмена|признать\s+утратившим|считать\s+недействительн|аннулировать)/],['отзыв из отпуска',/отозвать\s+из\s+(?:основного\s+|дополнительного\s+)?отпуск|отзыв\S*\s+из\s+отпуск/],['продление',/продлить|продлени/],
 ['изменение',/(?:^|\s)(?:изменить|внести\s+изменени|перенести|изложить\s+в\s+(?:следующей|новой)\s+редакции|в\s+редакции|вместо\s+слов|читать\s+.{0,40}вместо)/]];
function actionOf(text){const t=lc(text).replace(/^\d+[.)]\s*/,'');for(const [a,re] of ACTIONS)if(re.test(t))return a;return 'новое'}
function refOrderIn(text){const t=String(text);const m=/(?:пункт\S*\s+([\d.,\s]+(?:и\s+\d+)?)\s+)?(?:§\s*(\d+)\s+)?приказ\S*/iu.exec(t);if(!m)return '';
 const tail=t.slice(m.index+m[0].length,m.index+m[0].length+160);const n=/№\s*([0-9]+(?:[-–/][0-9А-Яа-яЁё]+)*)/u.exec(tail);const d=dateAt(tail);
 if(!n&&!d)return '';return [m[1]?'пункт '+m[1].trim().replace(/[,\s]+$/,''):'',m[2]?'§ '+m[2]:'','приказ'+(n?' № '+n[1]:'')+(d?' от '+d.text:'')].filter(Boolean).join(', ')}
function classify(text){const t=lc(text);for(const [c,re] of CATS)if(re.test(t))return c;return 'Другое'}
const LEAVE_KINDS=[['основной',/основн/],['дополнительный',/дополнительн/],['учебный',/учебн/],['по семейным обстоятельствам',/семейн|личн\S*\s+обстоятельств/],['по болезни',/по\s+болезни|по\s+состоянию\s+здоровья|для\s+лечения|реабилитацион/],['по уходу за ребёнком',/уходу\s+за\s+реб/],['по беременности и родам',/беременност/],['без сохранения содержания',/без\s+сохранения/],['отпуск в связи с заключением брака',/заключени\S*\s+брака/]];
function leaveDetail(t,line,{withPeriod=true}={}){const s=lc(t),l=lc(line||'');
 const kinds=LEAVE_KINDS.filter(([,re])=>re.test(s)).map(([k])=>k);
 const ys=[...s.matchAll(/за\s+(\d{4})\s*(?:год|г\.?)/g)].map(x=>x[1]);
 let dur=/(\d{1,3})\s*(?:\(\s*[а-яё ]+\)\s*)?(сут(?:ок|ки)?|календарн\S*\s+дн\S*|дн(?:ей|я))/.exec(l)||/(\d{1,3})\s*(?:\(\s*[а-яё ]+\)\s*)?(сут(?:ок|ки)?|календарн\S*\s+дн\S*|дн(?:ей|я))/.exec(s);
 const per=periodIn(line||'')||periodIn(t);
 const move=/прибывш|вернувш|из\s+отпуска|по\s+окончании/.test(s)&&!/убывш|убыть/.test(s)?'прибыл из отпуска':/убывш|убыть|убыл/.test(s)?'убыл в отпуск':'';
 const parts=[];
 let what=kinds.length?kinds.map((k,i)=>k+(i===kinds.length-1?' отпуск':'')).join(' и '):'отпуск';
 if(kinds.length>1&&ys.length===kinds.length){what=kinds.map((k,i)=>`${k} за ${ys[i]}`).join(' и ');parts.push(what.replace(/ за /,' отпуск за '))}
 else parts.push(what+(ys.length?` за ${[...new Set(ys)].join(', ')}`:''));
 if(dur)parts.push(`${dur[1]} ${/сут/.test(dur[2])?'сут.':'дн.'}`);
 if(per&&withPeriod)parts.push(per);if(move)parts.push(move);
 return parts.join(', ')}
function positionDetail(t){const m=/должност\S*\s+(.{3,180}?)(?=,\s*(?:ВУС|вус|тарифн|тариф|штатн|оклад|\d{3}-\d{3})|[;:]|\s(?:гвардии\s+)?(?:рядов|ефрейтор|сержант|младш|старш|прапорщик|лейтенант|капитан|майор|подполковник|полковник|матрос|мичман|главн)\S*\s+[А-ЯЁ]|$)/u.exec(clean(t));return m?m[1].replace(/[,.\s]+$/,''):''}
function genericDetail(t,cat){const s=clean(t).replace(/^\d+[.)]\s*/,'');if(cat==='Назначение на должность'){const p=positionDetail(s);if(p)return 'на должность '+p}
 if(cat==='Присвоение звания'){const q=/звани\S*\s+[«"„]([^»"“]{2,60})[»"“]/iu.exec(s);if(q)return 'звание «'+q[1].trim()+'»';
  const m=/звани\S*\s+((?:(?:старш|младш|главн)\S*\s+)?(?:рядов|ефрейтор|сержант|старшин|прапорщик|лейтенант|капитан|майор|подполковник|полковник|матрос|мичман)[а-яё]*(?:\s+[1-3]\s*ранга)?)/iu.exec(s);if(m)return 'звание «'+lc(m[1])+'»'}
 if(cat==='Командировка'){const per=periodIn(s);const m=/(?:^|\s)(?:в|во|из)\s+((?:г\.|город[а-я]*|пос\.|п\.|с\.)\s*[А-ЯЁ][а-яё-]+(?:[ -][А-ЯЁ][а-яё-]+)?|в\/ч\s*\d+|войсков\S*\s+част\S*\s*\d+|[А-ЯЁ][а-яё-]{2,}(?:\s+[А-ЯЁ][а-яё-]+)?)/u.exec(s);return [m?m[1]:'',per].filter(Boolean).join(', ')}
 const cut=s.split(/(?<=[.;:])\s/)[0];return cut.length>220?cut.slice(0,217)+'…':cut}

/* ---------- order → points ---------- */
const SIGN=/^(командир|начальник|врио|в\.?\s*р\.?\s*и\.?\s*о|временно\s+исполняющ|заместитель|зам\.|начальник\s+штаба|верно|м\.\s*п\.|исп\.|отп\.|рассылка)/i;
const NUM=/^\s*(\d{1,4})\s*[.)]\s+/; // 12.77.4: items up to 9999 («2675.»)
function pointsOf(order){
 const paras=(Array.isArray(order.paragraphs)&&order.paragraphs.length?order.paragraphs:String(order.text||'').split('\n').map(text=>({kind:'p',text}))).map(p=>({...p,text:clean(p.text)}));
 let start=paras.findIndex(p=>/ПРИКАЗЫВАЮ|П\s*Р\s*И\s*К\s*А\s*З\s*Ы\s*В\s*А\s*Ю/u.test(p.text)||/^§\s*\d+/.test(p.text));
 if(start<0)start=Math.min(paras.length,5);else if(/ПРИКАЗЫВАЮ/u.test(paras[start].text)&&!/^§/.test(paras[start].text))start++;
 let end=paras.length;for(let i=paras.length-1;i>start;i--){const t=paras[i].text;if(!t)continue;if(SIGN.test(t)||/^(полковник|подполковник|майор|генерал|капитан)\b.*[А-ЯЁ]\.\s?[А-ЯЁ]?\.?\s?[А-ЯЁ][а-яё]+$/u.test(t)){end=i;continue}if(end<paras.length&&i<end-6)break}
 const points=[];let sec='',cur=null,lastAuto=0;
 const open=(num)=>{cur={section:sec,num,lines:[],basis:[]};points.push(cur)};
 for(let i=start;i<end;i++){const p=paras[i],t=p.text;if(!t)continue;
  const sm=/^§\s*(\d+)\s*(.*)$/.exec(t);if(sm){sec=sm[1];cur=null;lastAuto=0;if(sm[2]&&sm[2].length>3){open('');cur.lines.push(sm[2])}continue}
  if(/^основани/i.test(t)){if(cur)cur.basis.push(t);continue}
  if(/^(по\s+(строевой|личному\s+составу|боевой|кадрам|части)|по\s+[а-яё]+\s+части)\b.{0,40}$/i.test(t)&&t.length<60)continue;
  const nm=NUM.exec(t);
  if(nm){open(nm[1]);cur.lines.push(t);continue}
  if(p.list){open(String(++lastAuto));cur.lines.push(t);continue}
  if(!cur)open('');cur.lines.push(t)}
 return points.filter(p=>p.lines.length)}

/* ---------- one order → rows ---------- */
function rowsOf(order){
 const rows=[];
 for(const pt of pointsOf(order)){
  const text=pt.lines.join('\n');const cat=classify(text);const tl=lc(text);const action=actionOf(text);const refOrder=action==='новое'?'':refOrderIn(text);
  const verbCase=DAT_VERBS.test(tl.slice(0,80))?'dat':ACC_VERBS.test(tl.slice(0,80))?'acc':'';
  const seen=new Map();
  /* 12.79.1: a long list (dozens or hundreds of people in one point) — each row keeps only the lead lines and its own line;
     before, every row held the whole point: memory and time grew as the square of the list (500 people ≈ 30 MB per order) */
  const big=pt.lines.length>8;let lead='',leadSpan;
  if(big){const ls=[];for(const l of pt.lines){if(personsIn(l,{verbCase}).length)break;ls.push(l);if(ls.length>=3)break}lead=ls.join('\n');leadSpan=spanIn(lead)||null}
  const gd=big?null:genericDetail(text,cat);
  for(const line of pt.lines){const ps=personsIn(line,{verbCase});const many=ps.length>8&&line.length>1500;let ctx=big?(lead&&line!==lead?lead+'\n'+line:line):text;
   const head=many?(()=>{const c=line.indexOf(':');return c>0&&c<400?line.slice(0,c+1):''})():'';
   for(const p of ps){if(many){let a=line.lastIndexOf(';',p.at)+1,b=line.indexOf(';',p.end);if(b<0)b=line.length;if(b-a>600){a=Math.max(a,p.at-120);b=Math.min(b,p.end+300)}
     const seg=line.slice(a,b).trim();ctx=(lead?lead+'\n':'')+(head&&a>head.length?head+' … ':'')+seg}
   const key=p.full||p.fio;const detail=cat==='Отпуск'?leaveDetail(ctx,line,{withPeriod:false}):(big?genericDetail(ctx,cat):gd);const span=spanIn(line)||(big?leadSpan:spanIn(text))||null;
   if(seen.has(key)){const r=seen.get(key);if(!r.details.includes(detail))r.details.push(detail);if(span&&!r.span)r.span=span;continue}
   const why=[];if(!p.sure)why.push('падеж фамилии');if(cat==='Другое')why.push('вид события');
   const r={fio:p.fio,full:p.full,rank:p.rank,gender:p.gender,category:cat,action,refOrder,details:[detail],span,section:pt.section,point:pt.num,text:ctx,basis:pt.basis.join(' '),sure:!why.length,why};seen.set(key,r);rows.push(r)}}
 }
 return rows}

/* ---------- merge rows of one order: same person + same category → one row, details on separate lines ---------- */
function mergeRows(rows){
 // initials-only mentions join the one full name with the same initials; two different full names stay apart
 const fullsByFio=new Map();for(const r of rows)if(r.full){const a=fullsByFio.get(r.fio)||new Set();a.add(r.full);fullsByFio.set(r.fio,a)}
 const m=new Map();
 for(const r of rows){const fulls=fullsByFio.get(r.fio);let who=r.full||(fulls&&fulls.size===1?[...fulls][0]:r.fio);const twins=fulls&&fulls.size>1;
  const k=who+'|'+r.category+'|'+r.action;const e=m.get(k);
  if(!e){const why=[...r.why];if(twins&&!r.full)why.push('несколько людей с такими инициалами');
   m.set(k,{...r,full:r.full||(who!==r.fio?who:''),details:[...r.details],texts:[r.text],refs:[[r.section,r.point]],why,sure:!why.length,spans:r.span?[r.span]:[],bases:r.basis?[r.basis]:[]});continue}
  for(const d of r.details)if(!e.details.includes(d))e.details.push(d);if(!e.texts.includes(r.text))e.texts.push(r.text);e.refs.push([r.section,r.point]);if(r.span)e.spans.push(r.span);if(r.basis&&!e.bases.includes(r.basis))e.bases.push(r.basis);
  for(const w of r.why)if(!e.why.includes(w))e.why.push(w);e.sure=!e.why.length;if(!e.rank&&r.rank)e.rank=r.rank}
 return [...m.values()]}

function extract(order){
 const rows=mergeRows(rowsOf(order));
 return rows.map(r=>{const sp=r.spans.length===1?r.spans[0]:r.spans.length?{start:r.spans.map(x=>x.start).filter(Boolean).join('\n'),end:r.spans.map(x=>x.end).filter(Boolean).join('\n')}:{start:'',end:''};
  const ref=r.refs.map(([s,p])=>(s?'§ '+s:'')+(p?(s?', ':'')+'п. '+p:'')).filter(Boolean).join('; ');
  return {key:[r.full||r.fio,r.category,r.action,ref].join('|'),fio:r.fio,full:r.full,rank:r.rank,category:r.category,action:r.action,refOrder:r.refOrder||'',detail:r.details.join('\n'),start:sp.start||'',end:sp.end||'',
   ref,text:r.texts.join('\n\n'),basis:r.bases.join('\n'),check:!r.sure,why:r.why.join(', ')}})}

const CATEGORIES=[...CATS.map(c=>c[0]),'Другое'];
const ACTION_LIST=['новое','продление','изменение','отмена','отзыв из отпуска'];
const api={VERSION,extract,pointsOf,personsIn,surnameNom,patronymicInfo,classify,actionOf,refOrderIn,leaveDetail,periodIn,spanIn,dateAt,rankBefore,CATEGORIES,ACTIONS:ACTION_LIST};
root.VypiskiRegister=Object.freeze(api);
if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
