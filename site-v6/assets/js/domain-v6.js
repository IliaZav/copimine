/* Shared validation for preview and interface. The server validates again. */
(function(root){
'use strict';
const levels={player:0,'игрок':0,helper:5,'хелпер':5,'помощник':5,junior_admin:10,'младший администратор':10,moderator:10,'модератор':10,admin:20,'администратор':20,'админ':20,owner:30,'владелец':30};
const roleLevel=role=>levels[String(role||'').trim().toLowerCase()]??-1;
const canManage=(actor,target)=>!!actor&&!!target&&actor.id!==target.id&&roleLevel(actor.role)>=10&&roleLevel(target.role)>=0&&roleLevel(actor.role)>roleLevel(target.role);
function integer(value,min=0,max=Number.MAX_SAFE_INTEGER){const n=Number(value);if(!Number.isSafeInteger(n)||n<min||n>max)throw Error('Укажи целое число от '+min+' до '+max+'.');return n;}
function durationMs(value,unit='hours',permanent=false){if(permanent)return null;const factor={minutes:60000,hours:3600000,days:86400000}[unit];if(!factor)throw Error('Выбери единицу времени.');const ms=integer(value,1,525600)*factor;if(ms>365*86400000)throw Error('Максимальный срок — 365 дней.');return ms;}
const activeSanction=(s,now=Date.now())=>s.status==='active'&&!s.revokedAt&&(s.expiresAt==null||s.expiresAt>now);
function remaining(until,now=Date.now()){if(until==null)return 'Бессрочно';const sec=Math.ceil((Number(until)-now)/1000);if(sec<=0)return 'Срок истёк';const d=Math.floor(sec/86400),h=Math.floor(sec%86400/3600),m=Math.floor(sec%3600/60),s=sec%60;return d?d+' д '+h+' ч':h?h+' ч '+m+' мин':m?m+' мин '+s+' с':s+' с';}
const usedQuantity=o=>Math.max(0,Number(o.qty||o.quantity||0)-Math.max(0,Number(o.refundedQuantity||0)));
const periodStart=(period,now)=>period==='day'?now-86400000:period==='week'?now-604800000:period==='month'?now-2592000000:0;
function applicableLimits(player,itemId,now=Date.now()){return(player?.purchaseLimits||[]).filter(l=>!l.removedAt&&(!l.expiresAt||l.expiresAt>now)&&(!l.itemId||l.itemId===itemId));}
function limitRemaining(player,itemId,orders,now=Date.now()){
 let available=Infinity;
 for(const l of applicableLimits(player,itemId,now)){
 const since=Math.max(periodStart(l.period,now),Number(l.resetAt)||0);
 const spent=orders.filter(o=>(o.player===player.name||o.playerId===player.id)&&(!l.itemId||o.item===l.itemId||o.itemId===l.itemId)&&!['cancelled','refunded'].includes(o.status)&&o.at>=since).reduce((sum,o)=>sum+usedQuantity(o),0);
 available=Math.min(available,Math.max(0,Number(l.maximum??l.max??0)-spent));
 }return available;
}
function validateCart(cart,catalog,player,orders=[],now=Date.now()){
 if(!Array.isArray(cart)||cart.length>40)throw Error('Проверь состав корзины.');
 const combined=new Map();
 for(const row of cart){const q=integer(row.qty,1,64);combined.set(row.id,(combined.get(row.id)||0)+q);}
 const lines=[],totals={AR:0,CM:0};
 for(const [id,qty] of combined){const p=catalog.find(x=>x.id===id);if(!p||!p.enabled||(p.visibility||'public')!=='public')throw Error('Предмет недоступен для покупки.');if(!['AR','CM'].includes(p.currency))throw Error('Неизвестная валюта.');integer(qty,1,Math.min(64,Number(p.maxQuantity)||64,p.sourceType==='DONATION_SHOP'?1:64,Number(p.limit)||64));if(p.ownerBound&&qty!==1)throw Error('Предмет с привязкой покупается по одному.');if(player&&Number(p.limit)>0){const purchased=orders.filter(o=>(o.player===player.name||o.playerId===player.id)&&(o.item===id||o.itemId===id)&&!['cancelled','refunded'].includes(o.status)).reduce((n,o)=>n+usedQuantity(o),0);if(purchased+qty>Number(p.limit))throw Error('Лимит покупки «'+p.name+'» исчерпан.');}const price=integer(p.price,0,1000000000);if(player&&qty>limitRemaining(player,id,orders,now))throw Error('Лимит покупки «'+p.name+'» исчерпан.');const total=price*qty;integer(total);totals[p.currency]+=total;integer(totals[p.currency]);lines.push({...p,qty,total});}
 if(player){const quantity=lines.reduce((n,l)=>n+l.qty,0);for(const l of applicableLimits(player,null,now)){const since=Math.max(periodStart(l.period,now),Number(l.resetAt)||0);const used=orders.filter(o=>(o.player===player.name||o.playerId===player.id)&&o.at>=since&&!['cancelled','refunded'].includes(o.status)).reduce((n,o)=>n+usedQuantity(o),0);if(quantity+used>Number(l.maximum??l.max??0))throw Error('Общий лимит покупок не позволяет оформить эту корзину.');}}
 return {lines,totals};
}
function cleanText(v,max=240){return String(v??'').replace(/[\u0000-\u001f\u007f]/g,' ').trim().slice(0,max);}
const normalize=value=>cleanText(value,1000).toLowerCase().replace(/ё/g,'е').replace(/[_:.-]+/g,' ').replace(/\s+/g,' ').trim();
function keyboard(value){const a='qwertyuiop[]asdfghjkl;\'zxcvbnm,.',b='йцукенгшщзхъфывапролджэячсмитьбю';return String(value).toLowerCase().split('').map(c=>a.includes(c)?b[a.indexOf(c)]:c).join('');}
function distance(a,b,max=2){if(Math.abs(a.length-b.length)>max)return max+1;let row=Array.from({length:b.length+1},(_,i)=>i);for(let i=1;i<=a.length;i++){const next=[i];for(let j=1;j<=b.length;j++)next[j]=Math.min(next[j-1]+1,row[j]+1,row[j-1]+(a[i-1]===b[j-1]?0:1));row=next;}return row[b.length];}
function search(query,haystack){const q=normalize(query),h=normalize(haystack);if(!q)return 1;let best=0;for(const candidate of new Set([q,normalize(keyboard(q))])){if(h===candidate)best=Math.max(best,100);else if(h.startsWith(candidate))best=Math.max(best,90);else if(h.includes(candidate))best=Math.max(best,75);else{const words=candidate.split(' '),terms=h.split(' ');if(words.every(w=>terms.some(t=>t.startsWith(w))))best=Math.max(best,65);else if(words.every(w=>terms.some(t=>w.length>=4&&distance(w,t,w.length>7?2:1)<= (w.length>7?2:1))))best=Math.max(best,40);}}return best;}
function mapInterval(minutes,enabled=true){if(!enabled)return 'Автообновление выключено';const m=Number(minutes)||720;if(m%1440===0)return 'Обновление раз в '+m/1440+' дн.';if(m%60===0)return 'Обновление раз в '+m/60+' ч';return 'Обновление раз в '+m+' мин';}
function csv(rows){return rows.map(row=>row.map(value=>{let s=String(value??'');if(/^[\s\uFEFF]*[=+@-]/.test(s))s="'"+s;return '"'+s.replace(/"/g,'""')+'"';}).join(';')).join('\r\n');}
const api={roleLevel,canManage,integer,durationMs,activeSanction,remaining,periodStart,limitRemaining,validateCart,cleanText,normalize,keyboard,search,mapInterval,csv};
root.CM6Domain=api;if(typeof module!=='undefined')module.exports=api;
})(typeof window==='undefined'?globalThis:window);

