/* Production bindings for the approved v7.3.2 presentation. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,W=CM5,Q=CMReview,E=M.esc,H=M.handlers,F=M.forms;
const P=window.CMProduction={health:null};
const field=V.field,button=(label,action,iconName='refresh',className='')=>M.btn(label,action,iconName,className);
M.audit=()=>{};Q.scheduleSave=()=>{};Q.offline=false;
const allowedForms=new Set(['v7-login','v7-register','v7-code','v7-reset','v7-confirm-code','v7-link-email','v7-profile','v7-password','v7-maintenance','v7-site-ban','v73-recovery','v73-inv-remove','v71-transfer','v6-reauth','v6-profile','v6-password','v6-totp-setup','v6-totp-confirm','v6-cookie-settings','v6-player-action','v6-server-action','ticket','reply','v6-map-settings','catalog-save','v6-role','production-account','production-account-edit','production-mc-code','production-mc-confirm','production-checkout','production-content','production-totp-disable','production-release','production-release-apply','production-release-rollback','production-release-report']);
for(const key of Object.keys(F))if(!allowedForms.has(key))F[key]=()=>{throw Error('Действие недоступно. Изменения не сохранены.');};
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
 return '<div class="v6-server-summary">'+metric('Игровой сервер',gameStatus(game))+metric('Онлайн',game.players??'—')+metric('База сайта',databaseStatus(P.health))+'</div><section class="panel mt"><h2>Управление</h2><div class="v6-actions-grid mt">'+[['players','Игроки и инвентари'],['shop','Каталог'],['world','Карта и расписание'],['requests','Обращения'],['moderation','Наказания'],['audit','Журнал действий'],['system','Подключения и аккаунты']].map(([id,text])=>M.link(text,'#admin/'+id,'arrow-right','')).join('')+'</div></section><section class="panel mt"><h2>Последние действия</h2>'+((M.state.audit||[]).length?V.table(['Время','Действие','Кто','Объект'],M.state.audit.slice(0,10).map(r=>[M.date(r.at,true),E(r.action),E(r.actor),E(r.target)])):M.empty('Записей пока нет','Здесь появятся подтверждённые действия.','history'))+'</section>';
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

const bytes=n=>{if(n==null)return '—';const units=['Б','КБ','МБ','ГБ','ТБ'];let v=Number(n),i=0;while(v>=1024&&i<units.length-1){v/=1024;i++;}return v.toFixed(v>=10||i===0?0:1)+' '+units[i];};
const can=permission=>!X.live?X.canAdmin():X.can(permission);
const releaseStatus={prepared:'Подготовлен',backed_up:'Копия создана',checked:'Проверен',blocked:'Есть ошибки',armed:'Ждёт установки',rollback_armed:'Ждёт отката'};
const ciStatus={pass:'Пройден',fail:'Ошибка',running:'Выполняется',skipped:'Не запускался'};
function spark(points){
 if(!points.length)return '';
 const step=100/Math.max(1,points.length-1);
 const path=points.map((p,i)=>(i?'L':'M')+(i*step).toFixed(2)+' '+(30-Math.min(20,Math.max(0,Number(p.tps)||0))/20*30).toFixed(2)).join(' ');
 return '<svg class="v6-spark" viewBox="0 0 100 30" preserveAspectRatio="none" width="100%" height="70" role="img" aria-label="TPS за последние '+points.length+' замеров"><path d="'+path+'" fill="none" stroke="currentColor" stroke-width="1.2" vector-effect="non-scaling-stroke"/></svg>';
}
P.refreshMonitor=async()=>{P.monitor=await X.request('/admin/monitor');};
P.refreshReleases=async()=>{P.releases=await X.request('/admin/releases');};

W.adminExtensions.monitor=()=>{
 if(!can('server.monitor'))return M.empty('Нужны права администратора','Мониторинг доступен администраторам и владельцу.','lock');
 const r=P.monitor;
 if(!r)return '<section class="panel" role="status"><h2>Мониторинг сервера</h2><p>Загружаем последний замер. Он собирается раз в минуту отдельным процессом, поэтому страница не нагружает игру.</p></section>';
 if(r.error)return '<section class="panel"><h2>Мониторинг сервера</h2><p class="hint">'+E(r.error)+'</p><div class="actions mt">'+button('Повторить','production-monitor-refresh','refresh','ghost small')+'</div></section>';
 const s=r.sample;
 if(!s)return '<section class="panel"><h2>Замеров пока нет</h2><p class="hint">Сборщик запускается вместе с сайтом. Первый замер появится в течение минуты.</p><div class="actions mt">'+button('Обновить','production-monitor-refresh','refresh','ghost small')+'</div></section>';
 const process=s.host?.process||{},system=s.host?.system||{},db=s.database||{},errors=s.errors||{};
 const worlds=s.worlds||[],history=r.history||[];
 const ages=r.stale==null?'—':r.stale<120?r.stale+' сек назад':Math.round(r.stale/60)+' мин назад';
 return '<div class="v6-server-summary">'
  +metric('TPS',s.tps==null?'—':Number(s.tps).toFixed(1))
  +metric('MSPT',s.mspt==null?'—':Number(s.mspt).toFixed(2)+' мс')
  +metric('Игроков онлайн',s.players)
  +metric('Служба',process.activeState==='active'?'Работает':E(process.activeState||'Нет данных'))
  +'</div>'
  +'<section class="panel mt"><div class="panel-head"><div><h2>TPS и задержка тика</h2><p class="hint">Замер '+(s.sampled_s?M.date(s.sampled_s*1000,true):'—')+' · обновление раз в '+Math.round((r.intervalSeconds||60)/60)+' мин · '+ages+(r.stale>300?' · сборщик молчит':'')+'</p></div>'+button('Обновить','production-monitor-refresh','refresh','ghost small')+'</div>'+spark(history)+'</section>'
  +'<div class="admin-grid mt">'
  +'<section class="panel"><h2>Память и диск</h2>'+V.table(['Показатель','Значение'],[
    ['Память сервера, всего',bytes(system.memoryTotal)],
    ['Занято на сервере',bytes(system.memoryUsed)+(system.memoryPercent!=null?' · '+system.memoryPercent+'%':'')],
    ['Процесс Minecraft',bytes(process.memoryBytes)],
    ['Свободно на диске',bytes(system.disk?.free)+(system.disk?' · занято '+system.disk.percent+'%':'')],
    ['Загрузка CPU',(system.load||[]).map(x=>x.toFixed(2)).join(' / ')||'—'],
    ['Ядер',system.cpuCount??'—'],
    ['Аптайм сервера',system.uptimeSeconds!=null?Math.floor(system.uptimeSeconds/86400)+' д '+Math.floor(system.uptimeSeconds%86400/3600)+' ч':'—'],
    ['Перезапусков службы',process.restarts??'—'],
    ['Служба запущена',E(process.startedAt||'—')]
  ])+'</section>'
  +'<section class="panel"><h2>База сайта</h2>'+V.table(['Показатель','Значение'],[
    ['Движок',db.engine==='postgresql'?'PostgreSQL':'SQLite (тест)'],
    ['Доступна',db.ok===false?'Нет':db.engine==='postgresql'?'Да':'—'],
    ['Соединений',db.connections!=null?db.connections+' из '+(db.maxConnections??'—'):'—'],
    ['Самое старое соединение',db.oldestSeconds!=null?db.oldestSeconds+' сек':'—'],
    ['Размер',bytes(db.bytes)]
  ])+'</section></div>'
  +'<section class="panel mt"><h2>Нагрузка по мирам</h2>'+(worlds.length?V.table(['Мир','Измерение','Загружено чанков'],worlds.map(w=>[E(w.name||'—'),E(w.dimension||'—'),w.chunks??'—'])):M.empty('Нет данных по мирам','Ответ игрового моста не содержит списка миров.','map'))+'<p class="hint mt">Количество сущностей игровой сервер пока не отдаёт: плагин-мост его не считает. Показаны загруженные чанки.</p></section>'
  +'<section class="panel mt"><h2>Ошибки плагинов</h2>'+(errors.available?(errors.count?V.table(['Источник','Предупреждений и ошибок'],Object.entries(errors.bySource||{}).sort((a,b)=>b[1]-a[1]).map(([k,v])=>[E(k),v]))+(errors.samples||[]).length?'<pre class="v6-log-samples">'+errors.samples.map(l=>E(l)).join('\n')+'</pre>':'':'<p class="hint">В последних '+errors.tail+' строках журнала предупреждений нет.</p>'):'<p class="hint">Журнал игры недоступен для чтения.</p>')+'</section>'
  +'<section class="panel mt"><h2>Плагины</h2><p class="hint">Версии читаются из установленных jar при каждом замере: '+s.plugins+' шт. Полный список с контрольными суммами — в разделе «Релизы».</p></section>';
};

W.adminExtensions.releases=()=>{
 if(!can('release.manage'))return M.empty('Нужен доступ владельца','Управление релизами доступно только владельцу: установка меняет игровой сервер.','lock');
 const r=P.releases;
 if(!r)return '<section class="panel" role="status"><h2>Центр управления релизами</h2><p>Читаем установленные плагины и контрольные суммы.</p></section>';
 if(r.error)return '<section class="panel"><h2>Центр управления релизами</h2><p class="hint">'+E(r.error)+'</p><div class="actions mt">'+button('Повторить','production-release-refresh','refresh','ghost small')+'</div></section>';
 const i=r.installed||{},plugins=i.plugins||[],staging=r.staging||[],ci=i.ci||null;
 const rows=(r.releases||[]).map(x=>{
  const id=E(x.id),state=releaseStatus[x.status]||x.status;
  const actions=(x.status==='blocked'?'':M.btn('Копия','production-release-backup','package','small','data-id="'+id+'"'))+(x.status==='prepared'||x.status==='blocked'?M.btn('Проверить','production-release-check','check','small','data-id="'+id+'"'):'')+(x.status==='checked'?M.btn('Установить','production-release-apply','server','small','data-id="'+id+'"'):'')+(x.backup?.jars?M.btn('Откатить','production-release-rollback','undo','small','data-id="'+id+'"'):'');
  return [E(x.version)+'<small>'+M.date(x.created_s*1000,true)+'</small>',M.pill(state,x.status==='blocked'?'danger':x.status==='armed'||x.status==='rollback_armed'?'':'green'),E(x.actor_name),E(x.reason||''),actions||'—'];
 });
 return '<section class="panel"><h2>Что установлено</h2>'+V.table(['Показатель','Значение'],[
   ['Коммит GitHub',i.commit?E(i.commit)+(i.commitSource?' <small>'+E(i.commitSource)+'</small>':''):'<span class="hint">не reported</span>'],
   ['Результат CI',ci?M.pill(ciStatus[ci.status]||ci.status,ci.status==='pass'?'green':ci.status==='fail'?'danger':'')+(ci.url?' '+M.link('Сборка',''+E(ci.url),'arrow-up-right','small'):''):'<span class="hint">не reported</span>'],
   ['Paper',E(i.paper||'—')],
   ['Minecraft',E(i.minecraft||'—')],
   ['Плагинов',plugins.length]
  ])+'<div class="actions mt">'+button('Обновить','production-release-refresh','refresh','ghost small')+button('Указать коммит и CI','production-release-report','edit','ghost small')+'</div><p class="hint mt">Коммит и CI берутся из данных деплоя: сайт не является git-репозиторием на сервере и не может узнать их сам.</p></section>'
  +'<section class="panel mt"><div class="panel-head"><div><h2>Установленные плагины</h2><p class="hint">Версии и SHA-256 читаются с диска.</p></div></div>'
  +V.table(['Плагин','Версия','API','Размер','SHA-256'],plugins.map(p=>[E(p.name)+'<small>'+E(p.file)+'</small>',E(p.version||'—'),E(p.apiVersion||'—'),bytes(p.size),'<code>'+E((p.sha256||'').slice(0,16))+'</code>']))+'</section>'
  +'<section class="panel mt"><div class="panel-head"><div><h2>Заготовка обновления</h2><p class="hint">Файлы из '+E(r.stagingPath||'')+'</p></div>'+button('Подготовить обновление','production-release-new','plus','primary small')+'</div>'
  +(staging.length?V.table(['Файл','Плагин','Версия','Проверка'],staging.map(p=>[E(p.file),E(p.name),E(p.version||'—'),p.expected?(p.verified?M.pill('Совпадает','green'):M.pill('Не совпадает','danger')):M.pill('Нет .sha256','')])):'<p class="hint">Каталог пуст. Положи новые jar в каталог заготовки на сервере — тогда кнопка соберёт из них релиз.</p>')+'</section>'
  +'<section class="panel mt"><h2>Релизы</h2>'+(rows.length?V.table(['Версия','Статус','Автор','Причина','Действия'],rows):M.empty('Релизов ещё не было','Подготовка, резервная копия и проверка выполняются по шагам.','history'))+'</section>'
  +'<section class="panel mt"><h2>Как применяется установка</h2><p class="hint">Кнопка «Установить» требует ввести слово подтверждения и создаёт задание. Файлы плагинов меняет отдельная команда на сервере от root — веб-приложение не имеет права писать в каталог плагинов, поэтому одно нажатие не может ничего сломать. Перед заменой команда сохраняет прежние jar и проверяет SHA-256.</p><pre class="v6-log-samples">'+E(r.installCommand||'')+'&lt;id&gt;</pre></section>';
};

H['production-monitor-refresh']=async()=>{try{await P.refreshMonitor();M.render();M.toast('Данные обновлены.');}catch(e){M.toast(e.message,true);}};
H['production-release-refresh']=async()=>{try{await P.refreshReleases();M.render();M.toast('Данные обновлены.');}catch(e){M.toast(e.message,true);}};
H['production-release-new']=()=>M.openModal('Подготовить обновление','<p>Шаги выполнятся по порядку: релиз → резервная копия плагинов и базы → проверка совместимости. Установка запускается отдельно и только словом подтверждения.</p><form data-form="production-release">'+field('Версия','version','','text','required pattern="[0-9A-Za-z._-]{3,40}" placeholder="2026.10.28-1"')+field('Причина','reason','','text','required minlength="3" maxlength="240"')+V.actions('Подготовить, сохранить копию и проверить')+'</form>');
F['production-release']=async(f,d)=>{
 await X.request('/admin/releases',{method:'POST',body:{version:d.get('version'),reason:d.get('reason')}});
 const list=await X.request('/admin/releases'),latest=(list.releases||[])[0];
 if(!latest)throw Error('Релиз не создан.');
 await X.request('/admin/releases/'+encodeURIComponent(latest.id)+'/backup',{method:'POST',body:{}});
 const check=await X.request('/admin/releases/'+encodeURIComponent(latest.id)+'/check',{method:'POST',body:{}});
 await P.refreshReleases();M.closeModal();M.render();
 M.toast(check.ok?'Обновление проверено. Можно устанавливать.':'Проверка нашла проблемы: установка заблокирована.',!check.ok);
};
H['production-release-backup']=async b=>{try{await X.request('/admin/releases/'+encodeURIComponent(b.dataset.id)+'/backup',{method:'POST',body:{}});await P.refreshReleases();M.render();M.toast('Резервная копия создана.');}catch(e){M.toast(e.message,true);}};
H['production-release-check']=async b=>{try{const r=await X.request('/admin/releases/'+encodeURIComponent(b.dataset.id)+'/check',{method:'POST',body:{}});await P.refreshReleases();M.render();M.toast(r.ok?'Совместимость подтверждена.':'Установка заблокирована: есть ошибки проверки.',!r.ok);}catch(e){M.toast(e.message,true);}};
H['production-release-apply']=b=>M.openModal('Установить обновление','<p>Будут заменены файлы плагинов и перезапущен игровой сервер. Перед заменой сохраняются прежние jar, откат доступен в этом же списке.</p><form data-form="production-release-apply"><input type="hidden" name="id" value="'+E(b.dataset.id)+'">'+field('Введите УСТАНОВИТЬ','confirm','','text','required autocomplete="off" placeholder="УСТАНОВИТЬ"')+field('Причина','reason','','text','required minlength="3" maxlength="240"')+V.actions('Создать задание на установку')+'</form>');
F['production-release-apply']=async(f,d)=>{const r=await X.request('/admin/releases/'+encodeURIComponent(d.get('id'))+'/apply',{method:'POST',body:{confirm:d.get('confirm'),reason:d.get('reason')}});await P.refreshReleases();M.closeModal();M.render();M.toast('Задание создано. Запуск: '+r.command);};
H['production-release-rollback']=b=>M.openModal('Откатить релиз','<p>Плагины вернутся к резервной копии этого релиза. Файлы и база не удаляются.</p><form data-form="production-release-rollback"><input type="hidden" name="id" value="'+E(b.dataset.id)+'">'+field('Введите ОТКАТИТЬ','confirm','','text','required autocomplete="off" placeholder="ОТКАТИТЬ"')+field('Причина','reason','','text','required minlength="3" maxlength="240"')+V.actions('Создать задание на откат')+'</form>');
F['production-release-rollback']=async(f,d)=>{const r=await X.request('/admin/releases/'+encodeURIComponent(d.get('id'))+'/rollback',{method:'POST',body:{confirm:d.get('confirm'),reason:d.get('reason')}});await P.refreshReleases();M.closeModal();M.render();M.toast('Задание на откат создано. Запуск: '+r.command);};
H['production-release-report']=()=>{const i=P.releases?.installed||{},ci=i.ci||{};M.openModal('Коммит и результат CI','<form data-form="production-release-report">'+field('Коммит GitHub','commit',i.commit||'','text','pattern="[0-9a-fA-F]{7,40}" autocomplete="off" placeholder="a90108f"')+V.select('Результат CI','status',[['pass','Пройден'],['fail','Ошибка'],['running','Выполняется'],['skipped','Не запускался']],ci.status||'skipped')+field('Ссылка на сборку','url',ci.url||'','url','maxlength="500"')+field('Кто сообщил','source',i.commitSource||'deploy','text','maxlength="40"')+V.actions('Сохранить')+'</form>');};
F['production-release-report']=async(f,d)=>{const ci={status:d.get('status'),url:d.get('url')||''};await X.request('/admin/releases/report',{method:'POST',body:{commit:d.get('commit'),source:d.get('source')||'deploy',ci}});await P.refreshReleases();M.closeModal();M.render();M.toast('Данные о коммите сохранены.');};

V.after.push((page,tab)=>{
 document.querySelectorAll('.v7-reviewbar,.v7-test-hint,.v7-local-mail-note,[data-action="v7-feedback"],[data-action="v7-review-options"]').forEach(n=>n.remove());
 const send=document.getElementById('v71-send-error');
 if(send&&!document.getElementById('production-bank-pin'))send.insertAdjacentHTML('beforebegin',field('PIN игрового банка','pin','','password','id="production-bank-pin" inputmode="numeric" pattern="[0-9]{4,8}" autocomplete="off" required'));
 if(page==='admin'&&tab==='players'){
  document.querySelectorAll('.cm73-inventory').forEach(n=>{if(!n.querySelector('[data-action="production-inventory-refresh"]'))n.querySelector('.cm73-inventory-head')?.insertAdjacentHTML('beforeend',button('Обновить из игры','production-inventory-refresh','refresh','small').replace('data-action="production-inventory-refresh"','data-action="production-inventory-refresh" data-player="'+E(n.dataset.player)+'"'));});
 }
 if(page==='admin'&&!P.health&&!P.loading){P.loading=true;P.refreshHealth().then(()=>M.render()).catch(e=>{P.health={error:e.message};}).finally(()=>P.loading=false);}
 if(page==='admin'&&tab==='monitor'&&!P.monitorLoad){P.monitorLoad=true;P.refreshMonitor().then(()=>M.render()).catch(e=>{P.monitor={sample:null,history:[],error:e.message};}).finally(()=>P.monitorLoad=false);}
 if(page==='admin'&&tab==='releases'&&!P.releasesLoad){P.releasesLoad=true;P.refreshReleases().then(()=>M.render()).catch(e=>{P.releases={error:e.message,installed:{},staging:[],releases:[]};}).finally(()=>P.releasesLoad=false);}
});
})();
