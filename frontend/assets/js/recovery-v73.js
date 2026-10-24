/* Review recovery UX. No password, PIN or authenticator secret is ever displayed.
   pin.reset / mfa.reset / minecraft.relink are explicit preview requests until Codex
   attaches the authenticated account and EconomyCore services. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,A=CM71,Q=CMReview,E=M.esc,H=M.handlers,F=M.forms,C=window.CM73;
const actions=[
 ['password.reset','Сбросить пароль сайта','key','Текущий пароль отзывается. Владелец задаст новый через подтверждённую почту.','Отозвать пароль и сеансы'],
 ['pin.reset','Сбросить PIN','lock','Банковский счёт и история операций сохраняются. Новый PIN игрок задаст в банке.','Проверить сброс PIN'],
 ['sessions.revoke','Завершить все сеансы','monitor','Выход со всех устройств этого аккаунта. Сам пароль остаётся прежним.','Завершить сеансы'],
 ['login.unlock','Снять блокировку попыток','refresh','Снять ограничение после неверных попыток входа. Это не отменяет бан аккаунта.','Снять ограничение'],
 ['mfa.reset','Сбросить второй фактор','shield','Только после ручной проверки владельца. Не раскрывает старый секрет аутентификатора.','Проверить сброс'],
 ['minecraft.relink','Проверить привязку Minecraft','link','Заявка на повторную проверку игровой личности. UUID и покупки не переносятся автоматически.','Создать заявку']
];
let chosen='demo-001',accounts=[],accountsOwner='',accountsLoading=false;
const pby=id=>M.state.players.find(p=>p.id===id);
const sessionOwner=()=>X.live?X.sessionUser?.id||null:M.me()?.id||null;
const actorName=()=>X.live?X.sessionUser?.name||'Администратор':M.me()?.name||'Администратор';
function availableAccounts(){return Q.offline?[{id:'demo-001',username:'1',display_name:'DemoBuilder',email:'player@example.test',email_verified:true},{id:'demo-vk',username:'3',display_name:'VK Player',email:null,email_verified:false}]:accounts;}
function accountPlayer(record){const p=pby(record.id)||{id:record.id,name:record.username,AR:0,CM:0,role:'Игрок',status:'active',purchaseLimits:[],provenance:'local-demo'};if(!pby(record.id))M.state.players.push(p);p.displayName=record.display_name;p.email=record.email||'';p.emailVerified=record.email_verified;p.webBlocked=record.blocked;return p;}
function user(){const list=availableAccounts();list.forEach(accountPlayer);const selected=M.UI.params.get('player')||chosen,record=list.find(x=>x.id===selected)||list[0];if(record){chosen=record.id;return accountPlayer(record);}return pby(selected)||null;}
async function loadAccounts(){const owner=sessionOwner();if(!Q.ready||Q.offline||!X.canAdmin()||!owner||accountsLoading||accountsOwner===owner)return;accountsLoading=true;try{const result=await Q.request('recovery-accounts');if(sessionOwner()!==owner)return;accounts=Array.isArray(result.accounts)?result.accounts:[];accountsOwner=owner;accounts.forEach(accountPlayer);if(M.UI.page==='admin'&&M.UI.tab==='recovery')M.render();}catch(err){M.toast(err.message,true);}finally{accountsLoading=false;}}
const label=status=>({preview:'Проверка интерфейса',completed:'Выполнено локально',awaiting_player:'Ждём игрока',queued:'В очереди'}[status]||status);
C.recoveryView=()=>{
 const p=user(),options=availableAccounts().map(accountPlayer);
 if(!p)return `<div class="cm73-recovery">${M.empty('Нет аккаунтов для восстановления','Список аккаунтов пока пуст.','user')}</div>`;
 return `<div class="cm73-recovery"><section class="cm73-recovery-person"><div>${CM5.head(p,48)}<span><strong>${E(p?.name||'Аккаунт')}</strong><small>Аккаунт для восстановления</small></span></div><label class="field">Выбрать аккаунт<select id="cm73-recovery-player">${options.map(x=>`<option value="${E(x.id)}" ${x.id===p?.id?'selected':''}>${E(x.displayName||x.name)}</option>`).join('')}</select></label><dl class="detail-specs"><div><dt>Почта</dt><dd>${E(p?.email||'Не привязана')}</dd></div><div><dt>Minecraft</dt><dd>${p?.linked||p?.gameLinkVerified?'Есть привязка':'Не подтверждён'}</dd></div><div><dt>Статус</dt><dd>${p?.webBlocked?'Заблокирован':'Активен'}</dd></div></dl><div class="cm73-hint">${icon('info')}В списке — локальные веб-аккаунты. Игровые профили без веб-аккаунта не дают права сбрасывать чужой доступ.</div></section><div class="cm73-recovery-actions">${actions.map(([id,title,ic,desc,button])=>`<section class="cm73-recovery-action" data-search-target="${id}"><span class="cm73-action-icon">${icon(ic)}</span><div><h2>${({"password.reset":"Пароль сайта","pin.reset":"Банковский PIN","sessions.revoke":"Активные сеансы","login.unlock":"Попытки входа","mfa.reset":"Второй фактор","minecraft.relink":"Игровая привязка"})[id]}</h2><p>${desc}</p>${['pin.reset','mfa.reset','minecraft.relink'].includes(id)?'<small>Сейчас — тестовая заявка, без изменений в игре.</small>':''}</div>${M.btn(title,'v73-recovery-action','arrow-right','ghost small',`data-id="${id}" data-player="${E(p?.id||'')}"`)}</section>`).join('')}</div></div><section class="panel mt"><div class="panel-head"><h2>История восстановления</h2><span class="hint">Действие · причина · результат</span></div>${(M.state.reviewRecoveryLog||[]).length?V.table(['Время','Аккаунт','Действие','Причина','Результат'],M.state.reviewRecoveryLog.map(r=>[M.date(r.at,true),E(pby(r.player)?.name||r.player),E(actions.find(a=>a[0]===r.action)?.[1]||r.action),E(r.reason),M.pill(label(r.status),r.status==='completed'?'green':'')])):M.empty('Пока нет действий','После подтверждения здесь появится запись с причиной.','history')}</section>`;
};
H['v73-recovery-action']=b=>{const a=actions.find(a=>a[0]===b.dataset.id),p=pby(b.dataset.player);if(!a||!p||!X.canAdmin())return;
 M.openModal(a[1],`<div class="cm73-item-detail-head">${CM5.head(p,48)}<div><strong>${E(p.name)}</strong><small>${E(p.email||'Без почты')}</small></div></div><p>${a[3]}</p><form data-form="v73-recovery"><input type="hidden" name="player" value="${E(p.id)}"><input type="hidden" name="action" value="${a[0]}">${V.area('Причина и результат проверки владельца','reason','',3,'required minlength="3" maxlength="240"')}<label class="radio-line"><input type="checkbox" name="verified" required>Принадлежность аккаунта проверена</label><p class="hint">Эта копия работает только с тестовой базой. Пароли и секреты не показываются.</p><div class="form-error" role="alert"></div><div class="v71-dialog-actions">${M.btn('Отмена','close','','ghost')}<button type="submit" class="btn ${a[0]==='password.reset'?'cm73-danger':'primary'}">${a[4]}</button></div></form>`,'cm73-recovery-dialog');
};
F['v73-recovery']=async(f,d)=>{
 if(!d.get('verified'))throw Error('Подтверди проверку владельца.');const body={player:d.get('player'),action:d.get('action'),reason:String(d.get('reason')||'').trim()};
 if(body.reason.length<3)throw Error('Укажи причину.');let result;
 if(Q.offline){result={...body,id:X.uid('recovery'),status:'preview',review_only:true};}
 else result=await Q.request('recovery',body);
 (M.state.reviewRecoveryLog||=[]).unshift({...body,...result,at:Date.now(),actor:actorName()});M.audit('Восстановление доступа',pby(body.player)?.name,body.reason);M.save();M.closeModal();M.render();
 M.toast(result.status==='awaiting_player'?'Пароль отозван. Игроку нужно восстановить доступ по почте.':result.status==='preview'?'Тестовая заявка записана. Игровой аккаунт не изменён.':'Действие выполнено в локальной базе.');
};
V.after.push((page,tab)=>{if(X.canAdmin())void loadAccounts();else{accounts=[];accountsOwner='';}if(page==='admin'&&tab==='recovery')document.getElementById('cm73-recovery-player')?.addEventListener('change',e=>{chosen=e.target.value;M.navTo('admin/recovery?player='+encodeURIComponent(chosen));});});
})();
