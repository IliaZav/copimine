/* Pure review helpers. Minecraft must enforce the same snapshot contract server-side. */
(function(root){
'use strict';
const normalize=x=>String(x??'').toLowerCase().replace(/ё/g,'е').replace(/[_\-]+/g,' ').replace(/\s+/g,' ').trim();
const keyboard=x=>{const en="qwertyuiop[]asdfghjkl;'zxcvbnm,.",ru='йцукенгшщзхъфывапролджэячсмитьбю';return String(x).toLowerCase().split('').map(c=>en.includes(c)?ru[en.indexOf(c)]:c).join('');};
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
 for(const candidate of [q,normalize(keyboard(q))])for(const raw of aliases){const a=normalize(raw);if(!a)continue;
  if(candidate===a)score=Math.min(score,0);
  else if(a.startsWith(candidate))score=Math.min(score,1+(a.length-candidate.length)/1000);
  else if(candidate.split(' ').every(w=>a.includes(w)))score=Math.min(score,2+a.length/1000);
 }
 return score;
}
const api={slots,fingerprint,remove,quote,rank,normalize,keyboard};root.CM73Domain=api;if(typeof module!=='undefined')module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
