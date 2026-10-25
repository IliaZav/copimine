/* Review recovery UX. No password, PIN or authenticator secret is ever displayed.
   pin.reset / mfa.reset / minecraft.relink are explicit preview requests until Codex
   attaches the authenticated account and EconomyCore services. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,A=CM71,Q=CMReview,E=M.esc,H=M.handlers,F=M.forms,C=window.CM73;
const actions=[
 ['password.reset','Сменить пароль','key','Новый пароль действует сразу. Все старые сеансы завершатся.','Сменить пароль'],
 ['sessions.revoke','Завершить все сеансы','monitor','Выход со всех устройств этого аккаунта. Сам пароль остаётся прежним.','Завершить сеансы'],
 ['login.unlock','Снять блокировку попыток','refresh','Снять ограничение после неверных попыток входа. Это не отменяет блокировку аккаунта.','Снять ограничение']
];
let chosen='demo-001',accounts=[],accountsOwner='',accountsLoading=false;
const pby=id=>M.state.players.find(p=>p.id===id);
const sessionOwner=()=>X.live?X.sessionUser?.id||null:M.me()?.id||null;
const actorName=()=>X.live?X.sessionUser?.name||'Администратор':M.me()?.name||'Администратор';
function availableAccounts(){return Q.offline?[{id:'demo-001',username:'1',display_name:'DemoBuilder'}]:accounts;}
function accountPlayer(record){const p=pby(record.id)||{id:record.id,name:record.username,AR:0,CM:0,role:'Игрок',status:'active',purchaseLimits:[],provenance:'local-demo'};if(!pby(record.id))M.state.players.push(p);p.displayName=record.display_name;delete p.email;delete p.emailVerified;p.webBlocked=record.blocked;return p;}
function user(){const list=availableAccounts();list.forEach(accountPlayer);const selected=M.UI.params.get('player')||chosen,record=list.find(x=>x.id===selected)||list[0];if(record){chosen=record.id;return accountPlayer(record);}return pby(selected)||null;}
async function loadAccounts(){const owner=sessionOwner();if(!Q.ready||Q.offline||!X.canAdmin()||!owner||accountsLoading||accountsOwner===owner)return;accountsLoading=true;try{const result=await Q.request('recovery-accounts');if(sessionOwner()!==owner)return;accounts=Array.isArray(result.accounts)?result.accounts:[];accountsOwner=owner;accounts.forEach(accountPlayer);if(M.UI.page==='admin'&&M.UI.tab==='recovery')M.render();}catch(err){M.toast(err.message,true);}finally{accountsLoading=false;}}
const label=status=>({preview:'Проверка интерфейса',completed:'Выполнено локально',awaiting_player:'Ждём игрока',queued:'В очереди'}[status]||status);
C.recoveryView=()=>{
 const p=user(),options=availableAccounts().map(accountPlayer);
 if(!p)return `<div class="cm73-recovery">${M.empty('Нет аккаунтов для восстановления','Список аккаунтов пока пуст.','user')}</div>`;
 return `<div class="cm73-recovery"><section class="cm73-recovery-person"><div>${CM5.head(p,48)}<span><strong>${E(p?.displayName||p?.name||'Аккаунт')}</strong><small>${E(p?.name||'')}</small></span></div><label class="field">Выбрать аккаунт<select id="cm73-recovery-player">${options.map(x=>`<option value="${E(x.id)}" ${x.id===p?.id?'selected':''}>${E(x.displayName||x.name)}</option>`).join('')}</select></label><dl class="detail-specs"><div><dt>Minecraft</dt><dd>${p?.linked||p?.gameLinkVerified?'Привязан':'Не привязан'}</dd></div><div><dt>Статус</dt><dd>${p?.webBlocked?'Заблокирован':'Активен'}</dd></div></dl></section><div class="cm73-recovery-actions">${actions.map(([id,title,ic,desc])=>`<section class="cm73-recovery-action" data-search-target="${id}"><span class="cm73-action-icon">${icon(ic)}</span><div><h2>${({"password.reset":"Пароль","sessions.revoke":"Сеансы","login.unlock":"Вход"})[id]}</h2><p>${desc}</p></div>${M.btn(title,'v73-recovery-action','arrow-right','ghost small',`data-id="${id}" data-player="${E(p?.id||'')}"`)}</section>`).join('')}</div></div><section class="panel mt"><div class="panel-head"><h2>История действий</h2><span class="hint">Действие · причина · результат</span></div>${(M.state.reviewRecoveryLog||[]).length?V.table(['Время','Аккаунт','Действие','Причина','Результат'],M.state.reviewRecoveryLog.map(r=>[M.date(r.at,true),E(pby(r.player)?.name||r.player),E(actions.find(a=>a[0]===r.action)?.[1]||r.action),E(r.reason),M.pill(label(r.status),r.status==='completed'?'green':'')])):M.empty('Пока нет действий','','history')}</section>`;
};
H['v73-recovery-action']=b=>{const a=actions.find(a=>a[0]===b.dataset.id),p=pby(b.dataset.player);if(!a||!p||!X.canAdmin())return;
 M.openModal(a[1],`<div class="cm73-item-detail-head">${CM5.head(p,48)}<div><strong>${E(p.displayName||p.name)}</strong><small>${E(p.name)}</small></div></div><p>${a[3]}</p><form data-form="v73-recovery"><input type="hidden" name="player" value="${E(p.id)}"><input type="hidden" name="action" value="${a[0]}">${a[0]==='password.reset'?V.field('Новый пароль','newPassword','','password','required minlength="10" maxlength="256" autocomplete="new-password"')+V.field('Повтори пароль','repeatPassword','','password','required minlength="10" maxlength="256" autocomplete="new-password"'):''}${V.area('Причина','reason','',3,'required minlength="3" maxlength="240"')}<label class="radio-line"><input type="checkbox" name="verified" required>Аккаунт проверен, действие выбрано верно</label><div class="form-error" role="alert"></div><div class="v71-dialog-actions">${M.btn('Отмена','close','','ghost')}<button type="submit" class="btn ${a[0]==='password.reset'?'cm73-danger':'primary'}">${a[4]}</button></div></form>`,'cm73-recovery-dialog');
};
F['v73-recovery']=async(f,d)=>{
 if(!d.get('verified'))throw Error('Подтверди действие.');const body={player:d.get('player'),action:d.get('action'),reason:String(d.get('reason')||'').trim()};
 if(body.action==='password.reset'){if(d.get('newPassword')!==d.get('repeatPassword'))throw Error('Пароли не совпадают.');body.newPassword=String(d.get('newPassword')||'');}
 if(body.reason.length<3)throw Error('Укажи причину.');let result;
 if(Q.offline){result={...body,id:X.uid('recovery'),status:'preview',review_only:true};}
 else result=await Q.request('recovery',body);
 (M.state.reviewRecoveryLog||=[]).unshift({...body,...result,at:Date.now(),actor:actorName()});M.audit('Восстановление доступа',pby(body.player)?.name,body.reason);M.save();M.closeModal();M.render();
 M.toast(result.status==='preview'?'Тестовая заявка записана.':result.message||'Действие выполнено.');
};
V.after.push((page,tab)=>{if(X.canAdmin())void loadAccounts();else{accounts=[];accountsOwner='';}if(page==='admin'&&tab==='recovery')document.getElementById('cm73-recovery-player')?.addEventListener('change',e=>{chosen=e.target.value;M.navTo('admin/recovery?player='+encodeURIComponent(chosen));});});
})();
