/* Pure review helpers. Minecraft must enforce the same snapshot contract server-side. */
(function(root){
'use strict';
const normalize=x=>String(x??'').toLowerCase().replace(/ё/g,'е').replace(/[_\-]+/g,' ').replace(/\s+/g,' ').trim();
// Раскладка в обе стороны: «ntktgjhn» → «телепорт» и «телепорт» → «ntktgjhn».
const keyboard=x=>{const en="qwertyuiop[]asdfghjkl;'zxcvbnm,.",ru='йцукенгшщзхъфывапролджэячсмитьбю';return String(x).toLowerCase().split('').map(c=>{const l=en.indexOf(c);if(l>=0)return ru[l];const r=ru.indexOf(c);return r>=0?en[r]:c;}).join('');};
// Опечатки: не больше max правок, длинные слова прощают две. Перестановка соседних букв — одна правка.
function edits(a,b,max){
 if(Math.abs(a.length-b.length)>max)return max+1;
 const rows=[[...Array(b.length+1).keys()]];
 for(let i=1;i<=a.length;i++){
  const cur=[i];
  for(let j=1;j<=b.length;j++){
   let n=Math.min(cur[j-1]+1,rows[i-1][j]+1,rows[i-1][j-1]+(a[i-1]===b[j-1]?0:1));
   if(i>1&&j>1&&a[i-1]===b[j-2]&&a[i-2]===b[j-1])n=Math.min(n,rows[i-2][j-2]+1);
   cur[j]=n;
  }
  rows.push(cur);
 }
 const d=rows[a.length][b.length];
 return d>max?max+1:d;
}
function typo(candidate,alias){
 if(candidate.length<4)return Infinity;
 const max=candidate.length>=9?2:1;
 const words=alias.split(' ').filter(Boolean),parts=[alias,...words];
 let best=max+1;
 for(const part of parts){const d=edits(candidate,part,max);if(d<best)best=d;}
 return best<=max?best:Infinity;
}
function slots(snapshot,kind='inventory'){
 const count=kind==='ender'?27:41,result=Array(count).fill(null);
 for(const [position,raw] of (snapshot?.slots||snapshot?.items||[]).entries()){
  if(!raw)continue;let slot=Number(raw.slot??position);
  if(slot>=100&&slot<=103)slot=slot-100+36;else if(slot===-106)slot=40;
  if(!Number.isInteger(slot)||slot<0||slot>=count)continue;
  const qty=Number(raw.qty??raw.quantity??raw.count??1);if(!Number.isInteger(qty)||qty<1||!raw.id||raw.id==='minecraft:air')continue;
  result[slot]={...raw,slot,qty};
 }return result;
}
const fingerprint=(snapshot,kind)=>JSON.stringify({id:snapshot?.id||'',at:snapshot?.at||0,slots:slots(snapshot,kind)});
function remove(snapshot,kind,slot,qty,expected){
 if(fingerprint(snapshot,kind)!==expected)throw Error('Снимок изменился. Обнови инвентарь и выбери предмет ещё раз.');
 const list=slots(snapshot,kind);slot=Number(slot);qty=Number(qty);
 if(!Number.isInteger(slot)||!list[slot])throw Error('В выбранной ячейке больше нет предмета.');
 if(!Number.isInteger(qty)||qty<1||qty>list[slot].qty)throw Error('Укажи количество в пределах выбранной стопки.');
 list[slot]=list[slot].qty===qty?null:{...list[slot],qty:list[slot].qty-qty};
 return {...snapshot,kind,slots:list,items:list.filter(Boolean)};
}
function quote(amount,percent=0){
 amount=Number(amount)||0;percent=Number(percent)||0;
 if(!Number.isSafeInteger(amount)||amount<0||amount>1000000||!Number.isFinite(percent)||percent<0||percent>100)throw Error('Проверь сумму и комиссию.');
 const fee=Math.max(0,Math.ceil(amount*percent/100-1e-10));return {amount,fee,total:amount+fee,percent};
}
function rank(query,aliases){
 const q=normalize(query);if(!q)return Infinity;let score=Infinity;
 // 0 точное, 1 начало, 2 все слова, 3+ опечатка — порядок держит быстрые совпадения выше.
 for(const candidate of [q,normalize(keyboard(q))])for(const raw of aliases){const a=normalize(raw);if(!a)continue;
  if(candidate===a)score=Math.min(score,0);
  else if(a.startsWith(candidate))score=Math.min(score,1+(a.length-candidate.length)/1000);
  else if(candidate.split(' ').every(w=>a.includes(w)))score=Math.min(score,2+a.length/1000);
  else{const d=typo(candidate,a);if(Number.isFinite(d))score=Math.min(score,3+d);}
 }
 return score;
}
const ITEM_SEARCH_ALIASES={
 zmei_gorynych:['змей','горыныч','меч дракона','незеритовый меч','критический удар','ослабление','урон'],
 repair_kit:['набор для ремонта','ремонт','починка','починить предмет','починить инструмент','ножницы','shears'],
 return_stone:['возвращение домой','домой','телепорт домой','точка возрождения','камень возврата','эхо-осколок'],
 angel_wings:['крылья','летать','полет','полёт','полёт на 15 секунд','перо','feather'],
 infinite_torch:['факел','свет','освещение','поставить факел','не расходуется','torch'],
 combat_crossbow:['лук','стрела','телепорт к стреле','телепорт по попаданию','перемещение к стреле','bow'],
 cobblestone_trail_bow:['лук','стрела','булыжник','дорожка из блоков','строить стрелой','bow'],
 explosive_crossbow:['арбалет','взрыв','взрывной болт','тнт','tnt','crossbow'],
 smena_bez_perekura_pickaxe:['кирка','спешка','скорость копания','копать быстрее','ускорение копки','netherite pickaxe'],
 lesnoy_bespredel_axe:['топор','лесоруб','рубка дерева','срубить дерево','дерево целиком','axe'],
 kopatel_transhey_shovel:['лопата','кладоискатель','песок','гравий','поиск руды','добыча руды','shovel'],
 fermer_bez_sna_hoe:['мотыга','ферма','фермер','грядка','обработать грядки','обработка 5 на 5','netherite hoe'],
 dezhurniy_argument_sword:['меч','ослабление','ослабить противника','эффект при ударе','бой','pvp','netherite sword'],
 vechniy_razgon_firework:['фейерверк','ракета','салют','бесконечные ракеты','ускорение фейерверком','firework rocket'],
 eternal_totem:['тотем','бессмертие','спасение от смерти','тотем бессмертия','воскрешение','totem'],
 treasurer_chestplate:['нагрудник','броня','казначей','эффект при уроне','защита','netherite chestplate'],
 copimine_miner_pickaxe:['кирка','шахтёр','шахтер','шахта','копать 3 на 3','копать 3x3','добыча блоков','netherite pickaxe'],
 craftsman_hammer:['молот','молот ветра','подбросить','подбрасывание','оглушение','mace'],
 batin_remen_sudnogo_dnya:['гром','молния','топор','удар молнией','ускорение игрока','netherite axe'],
 nu_ty_i_nakopal_blyat_pickaxe:['кирка','ловушка','закопать игрока','ловчий','удар по игроку','netherite pickaxe'],
 kosa_nalogovoy_inspekcii:['коса','казначей','вампиризм','кража ар','украсть ар','деньги с противника','netherite hoe'],
 kaska_prorab_huev:['каска','строитель','падение','защита от падения','скорость бега','netherite helmet'],
 mne_pohuy_ya_v_tanke_vest:['жилет','броня','защита','снижение урона','20 процентов урона','netherite chestplate'],
 ne_segodnya_suka_shield:['щит','блокировать удар','защита','ванильный щит','отражение удара','shield'],
 pohuy_na_debaffy_amulet:['амулет','очищение','снять негативный эффект','снять дебафф','замена эффектов','heart of the sea'],
 vremya_platit_nalogi_clock:['часы','казначей','налоги','освобождение от налогов','не платить налоги','clock'],
 gde_moy_lut_blyat_compass:['компас','телепортация','телепорт','направление взгляда','перемещение по взгляду','compass']
};
function productSearchTerms(item={}){
 const material=String(item.material||'').replace(/^minecraft:/i,'').replace(/[_-]+/g,' ');
 return [...new Set([item.name,item.id,String(item.id||'').replace(/[_-]+/g,' '),material,item.category,item.description,...(Array.isArray(item.aliases)?item.aliases:[]),...(ITEM_SEARCH_ALIASES[item.id]||[])].filter(Boolean).map(String))];
}
const api={slots,fingerprint,remove,quote,rank,normalize,keyboard,typo,productSearchTerms};root.CM73Domain=api;if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
