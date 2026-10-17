/* Pure domain rules for the offline UX prototype; not an authorization server. */
(function(root){
'use strict';
const cp=x=>JSON.parse(JSON.stringify(x));
function verifyChallenge(c,code,now){
 if(!c||c.used)return{ok:false,reason:'used'};
 if(now>=c.expiresAt)return{ok:false,reason:'expired'};
 if(c.attempts>=5)return{ok:false,reason:'locked'};
 c.attempts++;
 if(String(code)!==c.code)return{ok:false,reason:c.attempts>=5?'locked':'invalid'};
 c.used=true;return{ok:true};
}
function launcherState(r,now){if(!r)return'missing';if(['used','denied','revoked'].includes(r.status))return r.status;if(now>=r.expiresAt)return'expired';return r.status;}
function confirmPayment(p,a,now){if(!p||p.player!==a.name)throw Error('Получатель платежа не совпадает.');if(p.status==='paid')return false;if(p.status!=='pending'||now>=p.expiresAt)throw Error('Этот платёж уже недоступен. Создай новый.');if(!Number.isSafeInteger(p.amount)||p.amount<1)throw Error('Некорректная сумма.');if(!Number.isSafeInteger(a.CM+p.amount))throw Error('Лимит баланса превышен.');a.CM+=p.amount;p.status='paid';p.paidAt=now;return true;}
function publishPage(p,expected,now){if(p.revision!==expected)throw Error('Запись изменилась. Обнови редактор.');if(!p.draft.title?.trim()||!p.draft.body?.trim())throw Error('Нужны заголовок и текст.');p.history=p.history||[];if(p.published)p.history.unshift({...cp(p.published),at:p.publishedAt,revision:p.publishedRevision});p.published=cp(p.draft);p.publishedAt=now;p.publishedRevision=p.revision;p.status='published';return p;}
function escape(s){return String(s??'').replace(/[&<>"']/g,x=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[x]));}
function markdown(s){const inline=s=>escape(s).replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>').replace(/`([^`]+)`/g,'<code>$1</code>');return String(s||'').split(/\n\s*\n/).map(block=>{if(/^### /.test(block))return'<h3>'+inline(block.slice(4))+'</h3>';if(/^## /.test(block))return'<h2>'+inline(block.slice(3))+'</h2>';if(/^# /.test(block))return'<h2>'+inline(block.slice(2))+'</h2>';if(block.split('\n').every(l=>/^[-*] /.test(l)))return'<ul>'+block.split('\n').map(l=>'<li>'+inline(l.slice(2))+'</li>').join('')+'</ul>';return'<p>'+inline(block).replace(/\n/g,'<br>')+'</p>';}).join('');}
function statistics(orders,tickets,payments,from,to){const o=orders.filter(x=>x.at>=from&&x.at<=to&&!['cancelled','refunded'].includes(x.status)),t=tickets.filter(x=>x.at>=from&&x.at<=to),p=payments.filter(x=>x.status==='paid'&&x.paidAt>=from&&x.paidAt<=to);const revenue={AR:0,CM:0};let tops={};for(const r of o){revenue[r.currency]=(revenue[r.currency]||0)+r.total;tops[r.name]=(tops[r.name]||0)+r.qty;}const response=t.filter(x=>x.firstReplyAt>x.at).map(x=>(x.firstReplyAt-x.at)/3600000);return {revenue,count:o.length,paid:p.reduce((a,x)=>a+x.amount,0),tickets:t.length,open:t.filter(x=>x.status!=='closed').length,responseHours:response.length?response.reduce((a,n)=>a+n,0)/response.length:null,tops:Object.entries(tops).sort((a,b)=>b[1]-a[1]),orders:o};}
function series(rows,days,now,value){const day=86400000,end=new Date(now);end.setUTCHours(23,59,59,999);const result=[];for(let i=days-1;i>=0;i--){const hi=end.getTime()-i*day,lo=hi-day+1;const rr=rows.filter(x=>x.at>=lo&&x.at<=hi);result.push({at:hi,value:rr.reduce((a,r)=>a+(value?value(r):1),0)});}return result;}
function safeLocalRoute(path){return typeof path==='string'&&/^(account|cart|donate|launcher-link|settings)(\/[^?#]*)?(\?.*)?$/.test(path)?path:'account';}
const api={verifyChallenge,launcherState,confirmPayment,publishPage,markdown,statistics,series,safeLocalRoute};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.CMDomain=api;
})(globalThis);