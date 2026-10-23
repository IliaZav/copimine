/* Pure, tested prototype rules. Production enforces these on the server. */
(function(root){
'use strict';
const HOUR=3600000;
function escape(s){return String(s??'').replace(/[&<>"']/g,x=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[x]));}
function safeUrl(s){try{const u=new URL(String(s));return u.protocol==='https:'&&!u.username&&!u.password;}catch{return false;}}
function eligibleSnapshot(list,now,delayHours=12){return list.filter(s=>s.ready===true&&Number.isFinite(s.capturedAt)&&s.capturedAt<=now-Math.max(12,delayHours)*HOUR).sort((a,b)=>b.capturedAt-a.capturedAt)[0]||null;}
function quote(cart,catalog){
 const totals={AR:0,CM:0},lines=[],qtys=new Map();
 for(const row of cart){if(!Number.isSafeInteger(row.qty)||row.qty<1)throw Error('Проверьте количество предметов.');qtys.set(row.id,(qtys.get(row.id)||0)+row.qty);}
 for(const [id,qty] of qtys){const item=catalog.find(x=>x.id===id);if(!item||!item.enabled||(item.visibility&&item.visibility!=='public'))throw Error('Один из предметов больше недоступен. Уберите его из корзины.');if(qty>Math.min(64,Number(item.maxQuantity)||64,item.sourceType==='DONATION_SHOP'?1:64))throw Error('Проверь количество: '+item.name);if(!Number.isSafeInteger(item.price)||item.price<0||!Object.hasOwn(totals,item.currency))throw Error('Некорректная цена в каталоге.');const amount=item.price*qty;if(!Number.isSafeInteger(amount))throw Error('Слишком большая сумма.');if(!Number.isSafeInteger(totals[item.currency]+amount))throw Error('Слишком большая общая сумма.');totals[item.currency]+=amount;lines.push({...item,qty,total:amount});}
 return {totals,lines};
}
function validPolygon(p){
 if(!Array.isArray(p)||p.length<3||p.length>80||p.some(v=>!Array.isArray(v)||v.length!==2||v.some(x=>!Number.isFinite(x))))return false;
 if(new Set(p.map(v=>v[0]+','+v[1])).size!==p.length)return false;
 let area=0;for(let i=0;i<p.length;i++)area+=p[i][0]*p[(i+1)%p.length][1]-p[(i+1)%p.length][0]*p[i][1];if(Math.abs(area)<1)return false;
 const orient=(a,b,c)=>(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
 const on=(a,b,c)=>Math.abs(orient(a,b,c))<1e-9&&c[0]>=Math.min(a[0],b[0])&&c[0]<=Math.max(a[0],b[0])&&c[1]>=Math.min(a[1],b[1])&&c[1]<=Math.max(a[1],b[1]);
 for(let i=0;i<p.length;i++)for(let j=i+1;j<p.length;j++){
  if(j===i+1||(i===0&&j===p.length-1))continue;
  const a=p[i],b=p[(i+1)%p.length],c=p[j],d=p[(j+1)%p.length];
  if((orient(a,b,c)*orient(a,b,d)<0&&orient(c,d,a)*orient(c,d,b)<0)||on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b))return false;
 }
 return true;
}
const api={escape,safeUrl,eligibleSnapshot,quote,validPolygon,HOUR};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.CMCore=api;
})(globalThis);
