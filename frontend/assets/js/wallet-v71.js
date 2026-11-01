/* Design-review economy transport. Replace /review/economy and /review/transfer
   with the authenticated EconomyCore adapter in Codex. Never post to Minecraft
   from the browser and never promote this demo ledger to a production balance. */
(function(){
'use strict';
const M=CM,A=CM71,Q=CMReview,W=CM5,V=CMV4,E=M.esc,H=M.handlers,F=M.forms,s=()=>M.state;
const historyFilterKey=()=>`copimine.transfer.filter.v1.${M.me()?.id||'guest'}`;
const savedHistoryFilter=()=>{try{return ['all','in','out'].includes(sessionStorage.getItem(historyFilterKey()))?sessionStorage.getItem(historyFilterKey()):'all';}catch{return 'all';}};
let loadedUser='',loading=false,error='',draft={to:'',amount:'',note:''},pending=null,filter=savedHistoryFilter();
A.wallet=null;
// Уведомления живут отдельно от игрового счёта: они нужны и без привязки Minecraft.
A.notices={items:[],unread:0};
let noticesAt=0;
let bankPeriod='week';
let openedOp=null;
const player=id=>s().players.find(p=>p.id===id);
const number=n=>Number(n||0).toLocaleString('ru-RU');
const uid=()=>CM6.uid('transfer');
const feePercent=()=>Q.offline?Number(s().config.transferFeePercent||0):Number(A.wallet?.feePercent||0);
const feeQuote=amount=>CM73Domain.quote(amount,feePercent());
// Intermediate input (minus sign, decimals, pasted text) is not a valid quote.
const previewQuote=amount=>{try{return feeQuote(amount);}catch{return {amount:0,fee:0,total:0,percent:feePercent(),invalid:true};}};
// A zero fee is deliberately absent from the interface, not shown as a badge or row.
const feeRows=(amount,fee,percent)=>fee>0?`<div class="cm73-fee-row"><dt>Комиссия ${percent}% ${A.help('Списывается с отправителя дополнительно. Дробную часть округляет игровая экономика.')}</dt><dd>${number(fee)} АР</dd></div><div><dt>Всего спишется</dt><dd>${number(amount+fee)} АР</dd></div>`:'';

function clearTransferError(){const alert=document.querySelector('#v71-transfer-form .form-error');if(alert)alert.textContent='';}
function offlineLedger(){return s().reviewEconomy||(s().reviewEconomy={balances:Object.fromEntries(s().players.map(p=>[p.id,Number(p.AR||0)])),transfers:[]});}
function offlineView(id){const ledger=offlineLedger();return {feePercent:Number(s().config.transferFeePercent||0),balance:ledger.balances[id]||0,recipients:s().players.filter(p=>p.id!==id).map(p=>({id:p.id,name:p.displayName||p.name,online:false})),transfers:ledger.transfers.filter(t=>t.sender===id||t.recipient===id).map(t=>({...t,incoming:t.recipient===id})),unread:ledger.transfers.filter(t=>t.recipient===id&&!t.seen).length,review_only:true};}
// Настройки уведомлений: transfer — входящие переводы, replies — ответы поддержки.
A.muted=kind=>(kind==='transfer'?s().reviewPersonal?.notifyTransfers===false:kind==='ticket-reply'?s().reviewPersonal?.notifyReplies===false:false);
A.unreadCount=()=>{const items=A.notices?.items||[];return items.filter(x=>!x.read&&!A.muted(x.type)).length;};
A.refreshNotices=async(force=false)=>{
 if(!Q.ready||!M.me()||Q.offline)return;if(!force&&Date.now()-noticesAt<15000)return;noticesAt=Date.now();
 try{const feed=await Q.request('notifications');if(Array.isArray(feed?.notifications)){A.notices={items:feed.notifications,unread:Number(feed.unread)||0};A.updateBadge();}}catch{}
};
A.refreshWallet=async(force=false)=>{
 const p=M.me(),user=Q.user;if(!Q.ready||!p||user?.id===p.id&&!user.gameLinkVerified||p.gameLinkVerified!==true||loading||(!force&&loadedUser===p.id))return;
 const id=p.id;loading=true;error='';
 try{const [economy,feed]=await Promise.allSettled(Q.offline?[Promise.resolve(offlineView(id)),Promise.resolve(null)]:[Q.request('economy'),Q.request('notifications')]);if(M.me()?.id!==id)return;if(economy.status==='fulfilled'){A.wallet=economy.value;loadedUser=id;p.AR=economy.value.balance;}else{error=economy.reason?.message||'Не удалось загрузить игровой счёт.';loadedUser=id;A.wallet={balance:p.AR,recipients:[],transfers:[],notifications:[],unread:0};}if(feed.status==='fulfilled'&&feed.value){A.wallet.notifications=feed.value.notifications||[];A.wallet.unread=Number(feed.value.unread)||0;A.notices={items:feed.value.notifications||[],unread:Number(feed.value.unread)||0};}}
 catch(e){error=e.message;loadedUser=id;if(M.me()?.id===id)A.wallet={balance:p.AR,recipients:[],transfers:[],notifications:[],unread:0};}
 finally{loading=false;if(M.me()?.id===id&&['account','settings'].includes(M.UI.page))M.render();else A.updateBadge();}
};
A.updateBadge=()=>{const n=A.unreadCount(),old=document.querySelector('.v71-notification-button');if(old){old.querySelector('b')?.remove();if(n){const b=document.createElement('b');b.textContent=String(n);old.append(b);old.setAttribute('aria-label','Уведомления: '+n+' непрочитанных');}else old.setAttribute('aria-label','Уведомления');}tabsBadge(n);};
// Счётчики на вкладках кабинета и в заголовке окна считаются по href непрочитанных уведомлений.
function routeOf(href){const m=String(href||'').match(/^#((?:account|settings)\/[a-z0-9-]+)/i);return m?m[1]:'';}
function tabsBadge(n){
 const items=A.notices?.items||[];
 const counts={};
 items.filter(x=>!x.read&&!A.muted(x.type)).forEach(x=>{const key=routeOf(x.href)||(String(x.href||'')==='#account'?'account/overview':'');if(key)counts[key]=(counts[key]||0)+1;});
 document.querySelectorAll('.v71-tabs>a[href^="#account/"],.v71-settings-side nav a[href^="#settings/"]').forEach(link=>{
  const here=routeOf(link.getAttribute('href'));
  const count=here==='settings/notifications'?n:(counts[here]||0);
  let badge=link.querySelector('.v71-tab-count');
  if(count){if(!badge){badge=document.createElement('span');badge.className='v71-tab-count';link.append(badge);}badge.textContent=String(count);}else badge?.remove();
 });
 const html=document.documentElement;
 if(!html.dataset.baseTitle)html.dataset.baseTitle=document.title;
 document.title=n?`(${n}) ${html.dataset.baseTitle}`:html.dataset.baseTitle;
}
function recipientList(query=''){
 const list=(A.wallet?.recipients||s().players.filter(p=>p.id!==M.me()?.id).map(p=>({id:p.id,name:p.displayName||p.name}))).filter(p=>(p.name+' '+(player(p.id)?.name||'')).toLowerCase().includes(query.trim().toLowerCase()));
 return list.length?list.map(p=>`<button type="button" data-action="v71-select-recipient" data-id="${E(p.id)}" class="v71-recipient ${draft.to===p.id?'selected':''}" aria-pressed="${draft.to===p.id}">${W.head(player(p.id),38)}<span><strong>${E(p.name)}</strong><small>${E(player(p.id)?.name||p.name)}</small></span>${icon(draft.to===p.id?'check-circle':'plus')}</button>`).join(''):`<div class="v71-recipient-empty">${icon('search')}<strong>Игрок не найден</strong><p>Проверь ник или выбери игрока из списка.</p></div>`;
}
function selectedRecipient(){return (A.wallet?.recipients||[]).find(p=>p.id===draft.to)||s().players.filter(p=>p.id!==M.me()?.id).map(p=>({id:p.id,name:p.displayName||p.name})).find(p=>p.id===draft.to);}
function summary(){const p=selectedRecipient();return `<span class="label">Получатель</span><div class="v71-recipient-summary">${p?W.head(player(p.id),52):`<span class="v71-placeholder-avatar">${icon('user')}</span>`}<div><strong>${p?E(p.name):'Выбери игрока'}</strong><small>${p?'Получатель увидит твой ник':'Начни с ника в форме слева'}</small></div></div><dl class="v71-transfer-totals"><div><dt>Доступно</dt><dd>${number(A.wallet?.balance??M.me()?.AR)} АР</dd></div>${feeRows(Number(draft.amount)||0,previewQuote(Number(draft.amount)||0).fee,feePercent())}<div class="v71-total"><dt>Получит игрок</dt><dd id="v71-summary-amount">${number(Number(draft.amount)||0)} АР</dd></div></dl>`;}
// Банк: слева разделы, справа круговая диаграмма. Счета AR и донат видны
// сразу, таблица отвечает «что и когда», детали открываются по клику.
function bankTab(){return (M.UI?.params?.get('tab'))||'overview';}
function periodRows(){
 const rows=A.wallet?.transfers||[];
 if(!bankPeriod)return rows;
 const days=bankPeriod==='week'?7:bankPeriod==='month'?30:0;
 if(!days)return rows;
 const edge=Date.now()-days*86400000;
 return rows.filter(t=>Number(t.at||t.created*1000)>=edge);
}
function totals(rows){let inc=0,out=0;for(const t of rows){const n=Number(t.amount||0);if(t.incoming)inc+=n;else out+=n;}return{incoming:inc,outgoing:out,net:inc-out};}
function opTitle(t){return t.note||(t.incoming?'Входящий перевод':'Исходящий перевод');}
// Развёртка скина: узнаётся форма и одежда, голова в 26px их не передаёт.
function skinBody(p){
 return p?.skin?.body||M.asset('assets/skins/steve-front.png');
}
function opIcon(t){
 return skinBody(player(t.incoming?t.fromId:t.toId));
}
function opRow(t,i){
 const inc=!!t.incoming,who=inc?t.fromName:t.toName;
 return '<tr class="op" data-op="'+i+'" tabindex="0">'
  +'<td><div class="bank-what"><img src="'+E(opIcon(t))+'" alt="" loading="lazy"><div><b>'+E(opTitle(t))+'</b><small>'+E(who||'—')+' · '+M.date(t.at||t.created*1000,true)+'</small></div></div></td>'
  +'<td class="bank-amt '+(inc?'in':'out')+'">'+number(t.amount)+' <small>АР</small></td></tr>';
}
A.transferRows=(rows,small=false)=>rows.length
 ?'<table class="bank-table"><thead><tr><th>За что</th><th class="right">Сумма</th></tr></thead><tbody>'+rows.map(opRow).join('')+'</tbody></table>'
 :'<p class="bank-empty">Операций нет</p>';
function history(){const rows=periodRows().filter(t=>filter==='all'||(filter==='in'?t.incoming:!t.incoming));return A.transferRows(rows);}

// Круговая диаграмма: раскрывается при отрисовке, сегменты — по направлению.
function ring(){
 const rows=periodRows(),t=totals(rows),total=t.incoming+t.outgoing;
 if(!total)return '<p class="bank-empty">Движений пока нет</p>';
 const part=t.outgoing/total,R=62,C=2*Math.PI*R;
 return '<div class="bank-ring"><svg viewBox="0 0 160 160" role="img" aria-label="Расходы '+Math.round(part*100)+'%, доходы '+Math.round((1-part)*100)+'%">'
  +'<circle class="track" cx="80" cy="80" r="'+R+'"/>'
  +'<circle class="out" cx="80" cy="80" r="'+R+'" stroke-dasharray="'+(C*part).toFixed(2)+' '+(C*(1-part)).toFixed(2)+'"/>'
  +'<circle class="in" cx="80" cy="80" r="'+R+'" stroke-dasharray="'+(C*(1-part)).toFixed(2)+' '+(C*part).toFixed(2)+'" stroke-dashoffset="'+(-C*part).toFixed(2)+'"/>'
  +'</svg><div class="bank-ring-mid"><b>−'+number(t.outgoing)+'</b><span>'+ (bankPeriod==='week'?'за неделю':bankPeriod==='month'?'за месяц':'всего') +'</span></div></div>'
  +'<ul class="bank-legend"><li class="out"><i></i>Расходы<b>'+number(t.outgoing)+'</b></li><li class="in"><i></i>Доходы<b>'+number(t.incoming)+'</b></li></ul>';
}
function accounts(){
 const ar=A.wallet?.balance??M.me()?.AR??0,cm=A.wallet?.donation??0;
 // Валюта читается иконкой, а не подписью: руда — AR, монета — донат.
 return '<section class="bank-accounts">'
  +'<div class="bank-acc"><div class="bank-acc-lbl"><img src="'+M.asset('assets/img/currency/ar-ore.png')+'" alt="Игровые AR" title="Игровые AR">Игровые</div><div class="bank-acc-sum"><b>'+number(ar)+'</b><img class="bank-acc-cur" src="'+M.asset('assets/img/currency/ar-ore.png')+'" alt=""></div><small>Валюта сервера для торговли</small></div>'
  +'<div class="bank-acc"><div class="bank-acc-lbl"><img src="'+M.asset('assets/img/currency/coin.png')+'" alt="Донат" title="Донат">Донат</div><div class="bank-acc-sum"><b>'+number(cm)+'</b><img class="bank-acc-cur" src="'+M.asset('assets/img/currency/coin.png')+'" alt=""></div><small>Для покупок в магазине</small></div>'
 +'</section>';
}
function recipientSelect(){
 const me=M.me()?.id,list=(s().players||[]).filter(p=>p.id!==me).slice(0,200);
 const cur=draft.to?player(draft.to):null;
 return '<div class="bank-pick" id="bank-pick"><button type="button" class="bank-pick-btn" data-action="bank-pick-toggle">'
  +(cur?'<img src="'+E(skinBody(cur))+'" alt="">':'<span class="bank-pick-face">'+icon('user')+'</span>')
  +'<span>'+(cur?E(cur.name):'Выбери игрока')+'</span>'+icon('chevron-down','bank-pick-caret')+'</button>'
  +'<div class="bank-pick-list">'
  +'<label class="bank-pick-search">'+icon('search')+'<input id="bank-pick-query" placeholder="Найти игрока" autocomplete="off"></label>'
  +list.map(p=>'<button type="button" data-action="bank-pick" data-id="'+E(p.id)+'"><img src="'+E(skinBody(p))+'" alt="" loading="lazy"><span>'+E(p.name)+'</span>'+(p.online?'<small>В сети</small>':'')+'</button>').join('')
  +'</div></div>';
}
function summaryBox(){
 const p=selectedRecipient(),amt=Number(draft.amount)||0,q=previewQuote(amt);
 return '<dl class="bank-sum"><div><dt>Получатель</dt><dd>'+(p?E(p.name):'—')+'</dd></div><div><dt>Сумма</dt><dd>'+number(amt)+' АР</dd></div><div><dt>Комиссия</dt><dd>'+(q.fee?number(q.fee)+' АР':'нет')+'</dd></div><div class="total"><dt>Получит</dt><dd id="v71-summary-amount">'+number(q.net??amt)+' АР</dd></div></dl>';
}
function overviewTab(){
 const rows=periodRows().slice(0,8);
 return '<div class="bank-split"><div class="bank-main"><section class="bank-card"><div class="bank-card-head"><h2>Доходы и расходы</h2></div>'+A.transferRows(rows)+'</section></div>'
  +'<aside class="bank-side"><section class="bank-card"><div class="bank-card-head"><h2>Куда ушло</h2></div>'+ring()+'</section></aside></div>';
}
function transferTab(){
 return '<div class="bank-split"><div class="bank-main"><section class="bank-card"><h2>Новый перевод</h2><form data-form="v71-transfer" id="v71-transfer-form" class="bank-form">'
  +'<label class="field">Кому'+recipientSelect()+'</label>'
  +'<label class="field">Сумма<span class="bank-amount"><input id="v71-transfer-amount" name="amount" inputmode="numeric" autocomplete="off" required maxlength="10" placeholder="0" value="'+E(draft.amount)+'"><span>АР</span></span></label>'
  +'<div class="bank-presets">'+[50,100,250,500,1000].map(n=>'<button type="button" data-action="v71-amount" data-amount="'+n+'">'+n+'</button>').join('')+'</div>'
  +'<label class="field">Сообщение <span class="quiet">Необязательно</span><input id="v71-transfer-note" name="note" maxlength="140" placeholder="За что" value="'+E(draft.note)+'"></label>'
  +'<div class="form-error" role="alert">'+E(error)+'</div>'
  +'<button class="btn primary large" type="submit" '+(loading?'disabled':'')+'>Перевести'+icon('arrow-right')+'</button></form>'
  +'<p class="bank-note">Перевод необратим. Проверь получателя перед отправкой.</p></section></div>'
  +'<aside class="bank-side"><section class="bank-card" id="v71-transfer-summary"><h2>Что получится</h2>'+summaryBox()+'</section></aside></div>';
}
function historyTab(){
 return '<div class="bank-split"><div class="bank-main"><section class="bank-card"><div class="bank-card-head"><h2>История</h2>'+M.btn('Экспорт','v71-export-transfers','download','small ghost')+'</div>'
  +'<div class="bank-filters" role="group">'+[['all','Все'],['in','Приход'],['out','Расход']].map(([k,l])=>'<button type="button" data-action="v71-history-filter" data-key="'+k+'" aria-pressed="'+(filter===k)+'" class="'+(filter===k?'active':'')+'">'+l+'</button>').join('')+'</div>'
  +'<div id="v71-history-rows">'+history()+'</div></section></div><aside class="bank-side"></aside></div>';
}
A.transferPage=()=>{
 const tab=bankTab();
 const body=tab==='transfer'?transferTab():tab==='history'?historyTab():overviewTab();
 return A.heading('Банк','Движение денег на счетах.',M.link('В кабинет','#account','arrow-left','ghost'))
 +M.accountTabs('bank')
 +'<div class="bank-switch">'+[['overview','Обзор'],['transfer','Перевод'],['history','История']].map(([k,l])=>'<a href="#account/bank?tab='+k+'" class="'+(tab===k?'on':'')+'">'+l+'</a>').join('')+'</div>'
 +accounts()+body;
};
// Выбор получателя из списка с головами.
H['bank-pick-toggle']=()=>document.getElementById('bank-pick')?.classList.toggle('open');
H['bank-pick']=b=>{
  draft.to=b.dataset.id;
  const pick=document.getElementById('bank-pick');
  if(pick){
    pick.classList.remove('open');
    const btn=pick.querySelector('.bank-pick-btn');
    if(btn){
      const pl=player(draft.to);
      btn.innerHTML='<img src="'+E(skinBody(pl))+'" alt=""><span>'+E(pl?.name||'')+'</span>'+icon('chevron-down','bank-pick-caret');
    }
  }
  const box=document.getElementById('v71-transfer-summary');
  if(box){
    const h=box.querySelector('h2');
    box.innerHTML='<h2>'+(h?h.textContent:'Что получится')+'</h2>'+summaryBox();
  }
};
// Детали операции: то, что строка не показывает — категория, точное время, комиссия.
H['bank-op']=b=>{
  const rows=sortedTransfers(), t=rows[Number(b.dataset.op)];
  if(!t)return;
  const inc=!!t.incoming, who=inc?t.fromName:t.toName, at=Number(t.at||t.created*1000);
  M.openModal('Операция',
    '<div class="bank-op-hero"><img src="'+E(opIcon(t))+'" alt=""><div class="bank-op-sum '+(inc?'in':'out')+'">'+number(t.amount)+' <small>АР</small></div><div class="bank-op-title">'+E(opTitle(t))+'</div></div>'
    +'<dl class="bank-op-rows">'
    +'<div><dt>Направление</dt><dd>'+(inc?'Зачисление':'Списание')+'</dd></div>'
    +'<div><dt>'+(inc?'От кого':'Кому')+'</dt><dd>'+E(who||'—')+'</dd></div>'
    +'<div><dt>Когда</dt><dd>'+new Date(at).toLocaleString('ru-RU',{day:'2-digit',month:'long',hour:'2-digit',minute:'2-digit'})+'</dd></div>'
    +'<div><dt>Сумма</dt><dd>'+number(t.amount)+' АР</dd></div>'
    +(t.fee?('<div><dt>Комиссия</dt><dd>'+number(t.fee)+' АР</dd></div>'):'<div><dt>Комиссия</dt><dd>Нет</dd></div>')
    +'<div><dt>Номер</dt><dd class="mono">'+E(t.id||'—')+'</dd></div>'
    +'</dl>');
};
function sortedTransfers(){return periodRows();}
H['v71-select-recipient']=b=>{draft.to=b.dataset.id;clearTransferError();document.querySelectorAll('.v71-recipient').forEach(n=>{n.classList.toggle('selected',n.dataset.id===draft.to);n.setAttribute('aria-pressed',String(n.dataset.id===draft.to));n.querySelector(':scope>svg:last-child').outerHTML=icon(n.dataset.id===draft.to?'check-circle':'plus');});document.getElementById('v71-transfer-summary').innerHTML=summary();document.getElementById('v71-transfer-amount')?.focus({preventScroll:true});};
H['v71-amount']=b=>{draft.amount=b.dataset.amount;const input=document.getElementById('v71-transfer-amount');input.value=draft.amount;input.dispatchEvent(new Event('input',{bubbles:true}));input.focus({preventScroll:true});};
H['v71-history-filter']=b=>{filter=b.dataset.key;try{sessionStorage.setItem(historyFilterKey(),filter);}catch{}document.querySelectorAll('[data-action="v71-history-filter"]').forEach(n=>{n.classList.toggle('active',n.dataset.key===filter);n.setAttribute('aria-pressed',String(n.dataset.key===filter));});document.getElementById('v71-history-rows').innerHTML=history();};
F['v71-transfer']=(form,data)=>{const p=selectedRecipient(),raw=String(data.get('amount')||'').replace(/\s/g,''),amount=Number(raw);if(!p)throw Error('Сначала выбери получателя.');if(!/^\d+$/.test(raw)||!Number.isSafeInteger(amount)||amount<1||amount>1000000)throw Error('Введи целое количество АР от 1 до 1 000 000.');const quoted=feeQuote(amount);if(quoted.total>(A.wallet?.balance??M.me()?.AR))throw Error('Недостаточно АР. Уменьши сумму.');draft={to:p.id,amount:String(amount),note:String(data.get('note')||'').trim()};pending={...draft,amount,key:uid(),expectedFee:quoted.fee};
 M.openModal('Подтверди перевод',`<div class="v71-confirm-identity">${W.head(player(p.id),56)}<div><small>Получатель</small><strong>${E(p.name)}</strong><span>${E(player(p.id)?.name||p.name)}</span></div></div><div class="v71-confirm-amount">${number(amount)} <span>АР</span></div>${draft.note?`<blockquote class="v71-transfer-comment">${E(draft.note)}</blockquote>`:''}<dl class="v71-transfer-totals">${feeRows(amount,quoted.fee,quoted.percent)}<div><dt>Останется на счёте</dt><dd>${number((A.wallet?.balance??M.me()?.AR)-quoted.total)} АР</dd></div></dl><p class="v71-inline-info">${icon('shield')}Проверь данные. Отменить перевод нельзя.</p><label class="field">PIN игрового банка<input id="production-bank-pin" type="password" inputmode="numeric" pattern="[0-9]{4,8}" autocomplete="off" required></label><div class="form-error" id="v71-send-error" role="alert"></div><div class="v71-dialog-actions">${M.btn('Назад','close','','ghost')}${M.btn('Отправить '+number(amount)+' АР','v71-send','send','primary')}</div>`,'v71-transfer-dialog');
};
H['v71-send']=async b=>{
 if(!pending||b.disabled)return;b.disabled=true;b.classList.add('is-loading');b.setAttribute('aria-busy','true');const data={...pending};
 try{let receipt;
  data.pin=document.getElementById('production-bank-pin')?.value||'';receipt=(await Q.request('transfer',data)).transfer;A.wallet=await Q.request('economy');
  const recipient=selectedRecipient();M.me().AR=A.wallet.balance;pending=null;draft={to:'',amount:'',note:''};M.render();M.openModal('Перевод выполнен',`<div class="v71-transfer-success"><div class="v71-success-mark">${icon('check')}</div><h2>${number(data.amount)} АР</h2><p>Отправлено <strong>${E(recipient?.name||receipt.toName||data.to)}</strong></p><small>Получатель увидит уведомление в кабинете.</small></div><dl class="v71-transfer-totals">${feeRows(data.amount,receipt.fee||0,receipt.fee_percent||0)}<div><dt>Квитанция</dt><dd><code>${E(receipt.id)}</code></dd></div><div><dt>Статус</dt><dd class="green">Выполнен</dd></div></dl><p class="hint">АР — игровая валюта: реальные деньги при переводе не списываются.</p><div class="v71-dialog-actions">${M.btn('Закрыть','close','','primary')}${M.link('История переводов','#account/transfers?history=1','history','ghost')}</div>`,'v71-transfer-dialog');}
 catch(err){document.getElementById('v71-send-error').textContent=err.message;b.disabled=false;b.classList.remove('is-loading');b.setAttribute('aria-busy','false');}
};
H['v71-transfer-detail']=b=>{const t=A.wallet?.transfers.find(t=>t.id===b.dataset.id);if(!t)return;M.openModal('Детали перевода',`<div class="v71-transfer-success"><span class="v71-transaction-icon ${t.incoming?'incoming':''}">${icon(t.incoming?'arrow-down-left':'arrow-up-right')}</span><h2>${t.incoming?'+':'-'}${number(t.amount)} АР</h2><p>${t.incoming?'Входящий перевод':'Исходящий перевод'}</p></div><dl class="v71-transfer-totals">${t.incoming?'':feeRows(t.amount,t.fee||0,t.fee_percent||0)}<div><dt>Отправитель</dt><dd>${E(t.fromName)}</dd></div><div><dt>Получатель</dt><dd>${E(t.toName)}</dd></div><div><dt>Дата</dt><dd>${M.date(t.at||t.created*1000)}</dd></div><div><dt>Квитанция</dt><dd><code>${E(t.id)}</code></dd></div><div><dt>Статус</dt><dd class="green">Выполнен</dd></div></dl>${t.note?`<blockquote class="v71-transfer-comment">${E(t.note)}</blockquote>`:''}<div class="v71-dialog-actions">${M.btn('Закрыть','close','','ghost')}${M.btn(t.incoming?'Ответный перевод':'Повторить перевод','v71-repeat-transfer','transfer','primary',`data-id="${E(t.id)}"`)}</div>`,'v71-transfer-dialog');};
H['v71-repeat-transfer']=b=>{const t=A.wallet?.transfers.find(t=>t.id===b.dataset.id);if(!t)return;draft={to:t.incoming?t.sender:t.recipient,amount:String(t.amount),note:''};M.closeModal();M.navTo('account/transfers');M.render();document.getElementById('v71-transfer-amount')?.focus();};
H['v71-export-transfers']=()=>M.downloadFile('CopiMine-transfers.json',JSON.stringify({review_only:true,transfers:A.wallet?.transfers||[]},null,2));
H['v71-notifications']=async()=>{await A.refreshNotices(true);const isOff=s().reviewPersonal?.notifyTransfers===false,unread=A.unreadCount();
 M.openModal('Уведомления',`<div class="v71-notification-head"><span>${unread} непрочитанных</span>${M.link('Настройки','#settings/notifications','settings','ghost small')}</div>${isOff||s().reviewPersonal?.notifyReplies===false?`<p class="v71-inline-info">Часть уведомлений отключена в настройках.</p>`:''}${notificationRows()}<div class="v71-dialog-actions">${M.btn('Прочитать всё','v71-notifications-read','check','ghost small')}${M.link('История переводов','#account/transfers?history=1','arrow-right','small')}</div>`,'v71-notification-dialog');};
H['v71-notification-open']=b=>{const n=(A.notices?.items||[]).find(item=>item.id===b.dataset.id);if(!n){H['v71-transfer-detail']?.(b);return;}if(!n.read&&!Q.offline){n.read=true;A.updateBadge();void Q.request('notifications/'+encodeURIComponent(n.id)+'/read',{}).catch(()=>{n.read=false;A.updateBadge();});}const href=String(n.href||'');if(/^#(?:account|settings|admin)\/[a-z0-9-]+(?:\?[a-z0-9_=&%.-]*)?$/i.test(href)){M.closeModal();M.navTo(href.slice(1));return;}M.openModal(n.title,`<p>${E(n.body)}</p><p class="hint mt">${M.date(n.createdAt)}</p><div class="v71-dialog-actions">${M.btn('Закрыть','close','','primary small')}</div>`);};
H['v71-notifications-read']=async()=>{if(Q.offline){offlineLedger().transfers.filter(t=>t.recipient===M.me().id).forEach(t=>t.seen=true);A.wallet=offlineView(M.me().id);M.save();}else{await Q.request('transfers-read',{});A.notices={items:(A.notices?.items||[]).map(n=>({...n,read:true})),unread:0};}A.updateBadge();M.toast('Уведомления прочитаны');H['v71-notifications']();};
V.after.push((page,tab)=>{
 if(M.me()?.id!==loadedUser){A.wallet=null;draft={to:'',amount:'',note:''};pending=null;filter=savedHistoryFilter();loadedUser='';}
 if(M.me()&&Q.ready&&!loading)void A.refreshWallet();
 if(M.me()&&Q.ready)void A.refreshNotices();
 setTimeout(()=>A.updateBadge(),0);
 document.getElementById('v71-recipient-query')?.addEventListener('input',e=>{document.getElementById('v71-recipients').innerHTML=recipientList(e.target.value);});
 document.getElementById('v71-transfer-amount')?.addEventListener('input',e=>{clearTransferError();draft.amount=e.target.value;const raw=draft.amount.replace(/\s/g,'');document.getElementById('v71-transfer-summary').innerHTML=summary();const quoted=previewQuote(Number(raw)||0);e.target.setAttribute('aria-invalid',String(Boolean(raw)&&(quoted.invalid||quoted.total>(A.wallet?.balance??M.me()?.AR))));});
 document.getElementById('v71-transfer-note')?.addEventListener('input',e=>{draft.note=e.target.value;document.getElementById('v71-note-counter').textContent=draft.note.length+' / 140';});
});
let openedTransferRoute='';
V.after.push((page,tab)=>{const id=M.UI.params.get('transfer');if(page==='account'&&tab==='transfers'&&id&&A.wallet?.transfers&&openedTransferRoute!==id){const transfer=A.wallet.transfers.find(row=>row.id===id);if(transfer){openedTransferRoute=id;setTimeout(()=>H['v71-transfer-detail']?.({dataset:{id}}),0);}}if(!id)openedTransferRoute='';});
// Refresh on return to the tab, not constant polling of the game server.
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&M.me()){void A.refreshWallet(true);void A.refreshNotices(true);}});
})();
