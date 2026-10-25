/* Production bindings for the approved v7.3.2 presentation. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,W=CM5,Q=CMReview,E=M.esc,H=M.handlers,F=M.forms;
const P=window.CMProduction={health:null};
const field=V.field,button=(label,action,iconName='refresh',className='')=>M.btn(label,action,iconName,className);
M.audit=()=>{};Q.scheduleSave=()=>{};Q.offline=false;
const allowedForms=new Set(['v7-login','v7-register','v7-code','v7-reset','v7-confirm-code','v7-link-email','v7-profile','v7-password','v7-maintenance','v7-site-ban','v73-recovery','v73-inv-remove','v71-transfer','v6-reauth','v6-profile','v6-password','v6-totp-setup','v6-totp-confirm','v6-cookie-settings','v6-player-action','v6-server-action','ticket','reply','v6-map-settings','production-account','production-account-edit','production-mc-code','production-mc-confirm','production-checkout','production-content','production-totp-disable']);
for(const key of Object.keys(F))if(!allowedForms.has(key))F[key]=()=>{throw Error('Эта операция пока не подключена. Изменения не сохранены.');};
for(const key of Object.keys(H))if(/^(demo|v6-demo|v7-test|payment-(success|simulate)|balance-|mark-delivered|delivery-|recipe-(save|publish|delete|import)|v7-feedback|v7-inbox|reset)/.test(key))H[key]=()=>M.toast('Операция недоступна.',true);
Q.request=(function(original){return async(path,body)=>{

 try{return await original(path,body);}catch(error){
   if(/Подтвердите пароль/.test(error.message))M.toast('Подтвердите вход в настройках безопасности и повторите действие.',true);
   throw error;
 }
};})(Q.request);
H['v7-vk']=undefined;
H['v7-totp']=()=>H['v6-totp-setup']();
H['v7-game-link']=()=>M.openModal('Связать Minecraft','<p>Зайдите на сервер и войдите через AuthMe. Код появится в личном сообщении в игре.</p><form data-form="production-mc-code">'+field('Ник Minecraft','name','','text','required pattern="[A-Za-z0-9_]{3,16}" autocomplete="off"')+V.actions('Получить код в игре')+'</form>');
F['production-mc-code']=async(f,d)=>{await X.request('/account/minecraft/request',{method:'POST',body:{name:d.get('name')}});M.openModal('Введите код из Minecraft','<form data-form="production-mc-confirm">'+field('Код из личного сообщения','code','','text','required minlength="6" maxlength="12" autocomplete="one-time-code"')+V.actions('Подтвердить привязку')+'</form>');};
F['production-mc-confirm']=async(f,d)=>{await X.request('/account/minecraft/confirm',{method:'POST',body:{code:d.get('code')}});await Q.reload();M.closeModal();M.toast('Профиль Minecraft подтверждён.');};

P.refreshInventory=async id=>{
 const r=await X.request('/admin/players/'+encodeURIComponent(id)+'/inventory'),p=M.state.players.find(p=>p.id===id),snap=r.snapshot;
 M.state.playerSnapshots=M.state.playerSnapshots.filter(x=>x.playerId!==id);
 for(const [kind,key,version] of [['inventory','inventory','inventoryVersion'],['ender','enderChest','enderVersion']]){
  M.state.playerSnapshots.push({id:snap.id+'-'+kind,playerId:id,player:p?.name||snap.player,kind,at:snap.capturedAt,source:'live',version:snap[version],slots:(snap[key]||[]).filter(x=>x.amount>0&&x.material!=='AIR').map(x=>({...x,id:'minecraft:'+x.material.toLowerCase(),qty:x.amount}))});
 }
 return snap;
};
H['production-inventory-refresh']=async b=>{await P.refreshInventory(b.dataset.player);M.render();M.toast('Получен снимок Minecraft.');};
H['v6-inventory-refresh']=H['production-inventory-refresh'];
const originalInventory=window.CM73?.inventory;

const beforeAdminOverview=W.adminExtensions.overview;
function metric(label,value){return '<section class="panel"><span class="label">'+label+'</span><strong class="production-metric">'+E(value)+'</strong></section>';}
P.refreshHealth=async()=>{P.health=await X.request('/admin/health');};
function gameStatus(game,health=P.health){
 if(health?.error)return 'Нет доступа к проверке';
 if(!health)return 'Проверяется';
 if(game.ready)return 'Подключён';
 if(game.online&&game.adapter)return 'Не все плагины готовы';
 if(game.online)return 'Игровой мост не отвечает';
 return 'Игровой сервер не отвечает';
}
function databaseStatus(health){
 if(health?.error)return 'Нет доступа к проверке';
 if(!health)return 'Проверяется';
 if(health.database==='postgresql')return 'Работает · PostgreSQL';
 if(health.database==='sqlite-test')return 'Тестовая база';
 return 'Нет данных';
}
W.adminExtensions.overview=()=>{
 const game=P.health?.game||M.state.game||{},owner=X.can('accounts.manage');
 return '<div class="v6-server-summary">'+metric('Игровой сервер',gameStatus(game))+metric('Онлайн',game.players??'—')+metric('База сайта',databaseStatus(P.health))+'</div><section class="panel mt"><h2>Управление</h2><div class="v6-actions-grid mt">'+[['players','Игроки и инвентари'],['shop','Каталог'],['world','Карта и расписание'],['moderation','Обращения'],['logs','Журнал действий'],['system','Подключения и аккаунты']].map(([id,text])=>M.link(text,'#admin/'+id,'arrow-right','')).join('')+'</div></section><section class="panel mt"><h2>Последние действия</h2>'+((M.state.audit||[]).length?V.table(['Время','Действие','Кто','Объект'],M.state.audit.slice(0,10).map(r=>[M.date(r.at,true),E(r.action),E(r.actor),E(r.target)])):M.empty('Записей пока нет','Здесь появятся подтверждённые действия.','history'))+'</section>';
};
W.adminExtensions.system=()=>{
 const h=P.health,g=h?.game||{};
 return '<section class="panel"><h2>Подключения</h2>'+V.table(['Сервис','Состояние'],[['База сайта',E(databaseStatus(h))],['Minecraft',E(gameStatus(g,h))],['Платежи','Не подключены']])+'<div class="actions mt">'+button('Проверить подключения','production-health','refresh','ghost small')+'</div></section>'+(X.can('accounts.manage')?'<section class="panel mt"><h2>Аккаунты сайта</h2><p class="hint">Создай аккаунт или измени роль и доступ. Все изменения записываются в журнал.</p>'+button('Создать аккаунт','production-account-new','plus','primary mt')+V.table(['Аккаунт','Роль','Доступ',''],M.state.players.filter(p=>!p.gameOnly).map(p=>[E(p.displayName||p.name),E(p.serverRole||p.role),p.enabled?'Открыт':'Закрыт',p.id!==M.me()?.id?button('Изменить','production-account-edit','edit','small').replace('data-action="production-account-edit"','data-action="production-account-edit" data-id="'+E(p.id)+'"'):'Текущий аккаунт']))+'</section>':'');
};
H['production-health']=async()=>{await P.refreshHealth();M.render();};
H['production-account-new']=()=>M.openModal('Создать аккаунт','<form data-form="production-account">'+field('Логин','name','','text','required pattern="[A-Za-z0-9_]{3,32}"')+field('Временный пароль','password','','password','required minlength="10" autocomplete="new-password"')+V.select('Роль','role',[['player','Игрок'],['helper','Помощник'],['moderator','Модератор'],['admin','Администратор']],'player')+field('Причина','reason','','text','required minlength="3"')+V.actions('Создать аккаунт')+'</form>');
F['production-account']=async(f,d)=>{await X.request('/admin/accounts',{method:'POST',body:Object.fromEntries(d)});await Q.reload();M.closeModal();M.toast('Аккаунт создан.');};
H['production-account-edit']=b=>{const p=M.state.players.find(x=>x.id===b.dataset.id);if(!p)return;M.openModal('Доступ: '+E(p.name),'<form data-form="production-account-edit"><input type="hidden" name="id" value="'+E(p.id)+'">'+V.select('Роль','role',[['player','Игрок'],['helper','Помощник'],['moderator','Модератор'],['admin','Администратор']],p.serverRole||p.role)+'<label class="check-row"><input type="checkbox" name="enabled" '+(p.enabled?'checked':'')+'>Доступ к сайту открыт</label>'+field('Причина','reason','','text','required minlength="3"')+V.actions('Сохранить доступ')+'</form>');};
F['production-account-edit']=async(f,d)=>{await X.request('/admin/accounts/'+encodeURIComponent(d.get('id')),{method:'PATCH',body:{role:d.get('role'),enabled:!!d.get('enabled'),reason:d.get('reason')}});await Q.reload();M.closeModal();M.toast('Доступ обновлён.');};

H['checkout']=()=>{
 if(!M.me())return M.navTo('auth/login');
 const items=M.state.cart.map(x=>({id:x.id,qty:x.qty}));
 if(!items.length)return M.toast('Корзина пуста.',true);
 M.openModal('Подтвердить покупку','<p>Покупка проводится игровым банком. Предметы будут доступны через выдачу артефактов на сервере.</p><form data-form="production-checkout">'+field('PIN игрового банка','pin','','password','required pattern="[0-9]{4,8}" inputmode="numeric" autocomplete="off"')+V.actions('Купить')+'</form>');
};
F['production-checkout']=async(f,d)=>{const items=M.state.cart.map(x=>({id:x.id,qty:x.qty}));const total=items.reduce((n,x)=>n+(M.item(x.id)?.price||0)*x.qty,0);await X.request('/checkout',{method:'POST',body:{items,pin:d.get('pin'),expectedTotal:total,idempotencyKey:f.dataset.key||(f.dataset.key=X.uid('checkout'))}});M.state.cart=[];await X.refresh();M.closeModal();M.toast('Игровой банк подтвердил покупку.');};
F['v6-totp-confirm']=async(f,d)=>{const r=await X.request('/auth/totp/confirm',{method:'POST',body:{totp:d.get('totp')}});await Q.reload();M.openModal('Резервные коды 2FA','<p>Сохраните эти одноразовые коды. Повторно они показаны не будут.</p><pre class="production-codes">'+E((r.backupCodes||[]).join('\n'))+'</pre>'+button('Сохранил коды','close','check','primary'));};

V.after.push((page,tab)=>{
 document.querySelectorAll('.v7-reviewbar,.v7-test-hint,.v7-local-mail-note,[data-action="v7-feedback"],[data-action="v7-review-options"]').forEach(n=>n.remove());
 const send=document.getElementById('v71-send-error');
 if(send&&!document.getElementById('production-bank-pin'))send.insertAdjacentHTML('beforebegin',field('PIN игрового банка','pin','','password','id="production-bank-pin" inputmode="numeric" pattern="[0-9]{4,8}" autocomplete="off" required'));
 if(page==='admin'&&tab==='players'){
  document.querySelectorAll('.cm73-inventory').forEach(n=>{if(!n.querySelector('[data-action="production-inventory-refresh"]'))n.querySelector('.cm73-inventory-head')?.insertAdjacentHTML('beforeend',button('Обновить из игры','production-inventory-refresh','refresh','small').replace('data-action="production-inventory-refresh"','data-action="production-inventory-refresh" data-player="'+E(n.dataset.player)+'"'));});
 }
 if(page==='admin'&&!P.health&&!P.loading){P.loading=true;P.refreshHealth().then(()=>M.render()).catch(e=>{P.health={error:e.message};}).finally(()=>P.loading=false);}
});
})();
