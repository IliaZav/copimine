/* Navigation search only: typing can open/highlight a screen, NEVER execute an action. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,A=CM71,W=CM5,E=M.esc,D=CM73Domain,H=M.handlers;
const queryKey='copimine.admin.search.query.v1';
let query=(()=>{try{return sessionStorage.getItem(queryKey)||'';}catch{return '';}})(),timer=0,keepFocus=false,lastUrl='',matches=[],notice='';
const aliases={
 overview:'обзор;главная;рабочий стол;сводка;состояние проекта;dashboard;home;summary',
 statistics:'аналитика;статистика;посещения;посетители;популярный предмет;самый популярный товар;воронка;конверсия;продажи;популярность;отчеты;метрики;analytics;conversion;sales',
 performance:'tps;mspt;тпс;мспт;память;оперативная память;ram;cpu;производительность;лаг;лаги;нагрузка;время тика;ресурсы сервера',
 players:'игрок;игроки;аккаунты;ник;никнейм;профиль игрока;uuid;учетные записи;users;player;players;поиск игрока',
 recovery:'восстановление;потерял доступ;восстановить аккаунт;восстановить доступ;recovery;recover;забыл пароль;проблемы со входом',
 requests:'обращения;репорты;заявки;жалобы;тикеты;поддержка;вопросы игроков;апелляции;appeal;support;tickets;report',
 moderation:'модерация;наказания;сроки наказаний;активные баны;нарушения;ограничения;бан лист;разбан;санкции;punishments;bans;unban',
 inventories:'инвентари;инвентарь;снимок инвентаря;ячейки;слоты;содержимое сумки;снаряжение;броня;левая рука;хотбар;inventory;slots;armor;hotbar',
 investigations:'расследования;coreprotect;корпротект;проверка действий;кто сломал;кто поставил;грифер;гриферство;история блоков;проверка построек',
 anticheat:'античит;grim;grimac;чит;читы;нарушения движения;проверки античита;anticheat;cheat;alerts',
 whitelist:'вайтлист;белый список;доступ на сервер;разрешенные игроки;пропуск;whitelist;allowlist',
 bank:'банк;экономика;ар;ar;счета;игровой баланс;игровые деньги;банковские счета;bank;economy;money;accounts',
 shop:'каталог;магазин;предметы;товары;артефакты;цены;цена предмета;лимиты;тираж;лимит покупок;catalog;shop;items;price;supply',
 finance:'покупки;заказы;донаты;платежи;транзакции;возвраты;чеки;квитанции;финансы;балансы сайта;orders;payments;refund;donation;ledger',
 elections:'выборы;кандидат;кандидаты;президент;дебаты;ц ик;цик;голосование;голоса;итоги;срок президента;election;votes',
 laws:'законы;закон;налог;налоги;указы;постановления;конституция;казна;treasury;laws;tax',
 world:'карта;карты;миры;обычный мир;незер;энд;край;обновить карту;таймер карты;частота обновления;маркеры;иконки игроков;map;world;nether;end',
 points:'точки;метки;банкоматы;банкомат;лавки;торговые точки;координаты лавки;места;atm;waypoints;markers',
 recipes:'рецепты;скрытые рецепты;котел;ингредиенты;варка;формулы;смеси;книги рецептов;recipes;brewing;cauldron',
 system:'службы;сервер;запуск сервера;стоп сервер;выключить сервер;остановить сервер;перезапуск;рестарт;systemd;rcon;server;start;stop;restart',
 logs:'логи;лог;журнал сервера;консоль;ошибки;предупреждения;стектрейс;stacktrace;console;logs;error;warning',
 audit:'аудит;история действий;журнал действий;кто изменил;кто выдал;действия команды;оператор;audit;history',
 sources:'источники;плагины;версии плагинов;сборки;jar;реестр;источник каталога;синхронизация;конфигурации;plugins;registry;sources',
 jobs:'очередь;задания;задачи;выдача;ожидает выдачи;повтор задачи;воркер;worker;queue;jobs;delivery',
 backups:'резервные копии;бэкап;бекап;копия базы;сохранить копию;backup;restore backup;экспорт проекта',
 admins:'команда;администраторы;админы;владелец;сотрудники;доступ команды;персонал;owner;staff;admins;roles',
 security:'безопасность;права;доступ;сессии;сеансы;cookie;куки;csrf;totp;2fa;mfa;матрица прав;permissions;security',
 content:'контент;страницы;галерея;скриншоты;фото;картинки;публикации;баннер;фон;вопросы ответы;faq;cms;content;gallery;screenshots',
 settings:'настройки;параметры;название сайта;ip;адрес сервера;версия minecraft;settings;config;preferences',
 maintenance:'техработы;технические работы;обслуживание;закрыть сайт;открыть сайт;ремонт сайта;maintenance;offline mode'
};
const special=[
 ['pin.reset','Сбросить PIN','recovery','пин;pin;сброс пин;сбросить пин;сброс pin;сбросить pin;забыл пин;банковский пин;код банка;reset pin;bank pin'],
 ['password.reset','Сбросить пароль сайта','recovery','пароль;сброс пароля;сбросить пароль;новый пароль;пароль сайта;отозвать пароль;reset password;password reset;forgot password'],
 ['sessions.revoke','Завершить сеансы','recovery','отозвать сеансы;завершить сеансы;выйти везде;все сессии;разлогинить;сброс сессий;украли куки;logout all;revoke sessions'],
 ['login.unlock','Снять блокировку попыток','recovery','попытки входа;слишком много попыток;разблокировать вход;сброс попыток;снять ограничение входа;rate limit;login unlock'],
 ['mfa.reset','Сбросить второй фактор','recovery','сброс totp;сброс 2fa;потерял телефон;аутентификатор;сброс второго фактора;reset mfa'],
 ['minecraft.relink','Проверить привязку Minecraft','recovery','перепривязка;потерял майнкрафт;привязка майнкрафт;смена uuid;игровой аккаунт;relink minecraft'],
 ['private','Закрытые предметы','shop?scope=private','скрытые;скрытые предметы;закрытые;закрытые предметы;админские предметы;награды;hidden items;private items'],
 ['transferFeePercent','Комиссия переводов','bank?sub=transfers','комиссия;комиссия перевода;процент перевода;transfer fee;fee'],
 ['gallery','Галерея проекта','content?sub=gallery','фотографии;загрузить фото;добавить скриншот;добавить фото;галерея проекта;порядок кадров;карусель;carousel;gallery'],
];
const actionAliases={
 'give':'выдать;выдача предмета;дать предмет;выдать предмет;give item;предмет игроку',
 'inventory.remove':'эндер сундук;эндерсундук;ender chest;enderchest;изъять;изъять предмет;убрать предмет;убрать из ячейки;удалить из инвентаря;remove item;очистить ячейку',
 'ender.remove':'эндер сундук;эндерсундук;изъять из эндер сундука;ender chest;enderchest',
 'tp.coords':'тп;телепорт;телепортация;тп координаты;телепорт на координаты;tp coords;teleport',
 'tp.spawn':'спавн;тп на спавн;вернуть на спавн;spawn',
 'tp.bed':'кровать;возрождение;тп кровать;точка возрождения;respawn;bed',
 'ban':'бан;забанить;бан игрока;заблокировать игрока;ban player',
 'mute':'мут;замутить;заглушить;отключить чат;выдать мут;mute',
 'heal':'хил;вылечить;здоровье;восстановить здоровье;heal',
 'feed':'еда;голод;накормить;сытость;feed',
 'money.adjust':'баланс;изменить баланс;выдать ар;снять ар;начислить;деньги игрока;set balance',
 'purchase.limit':'личный лимит;лимит игрока;ограничить покупки;лимит предмета;purchase limit'
};
function entries(){
 const target=X.player()?.id||'demo-001';
 const rows=A.adminEntries.map(e=>({id:e[0],title:e[1],url:'#admin/'+e[0],aliases:[e[1],...(aliases[e[0]]||'').split(';')],icon:e[3]}));
 for(const [id,title,route,a]of special)rows.push({id,title,url:'#admin/'+route,aliases:[title,...a.split(';')],icon:'search'});
 for(const a of X.actions.filter(a=>a.scope!=='server'))rows.push({id:a.id,title:a.label,url:'#admin/players?player='+encodeURIComponent(target)+'&sub=actions',aliases:[a.label,a.id,...(actionAliases[a.id]||'').split(';')],icon:a.icon||'sliders'});
 for(const p of M.state.players)rows.push({id:p.id,title:p.displayName||p.name,url:'#admin/players?player='+encodeURIComponent(p.id),aliases:[p.name,p.displayName||'',p.minecraftName||'',p.minecraftUuid||'',p.id],icon:'user',avatar:W.head(p,28)});
 for(const p of M.state.catalog)rows.push({id:p.id,title:p.name,url:'#admin/shop?scope='+(p.visibility==='public'?'public':'private'),aliases:[p.name,p.id,p.adminSubtitle||'',...(p.aliases||[])],icon:'package'});
 return rows;
}
function search(q){return entries().map(e=>({...e,score:D.rank(q,e.aliases)})).filter(e=>Number.isFinite(e.score)).sort((a,b)=>a.score-b.score).filter((e,i,rows)=>rows.findIndex(x=>x.id===e.id&&x.url===e.url)===i).slice(0,5);}
function status(){const box=document.getElementById('cm73-search-results');if(!box)return;box.hidden=!query.trim();box.innerHTML=notice?`<p role="status">${E(notice)}</p>`:matches.length?matches.map((m,i)=>`<button type="button" data-action="v73-search-go" data-index="${i}">${m.avatar||icon(m.icon)}<span>${E(m.title)}</span>${icon('arrow-right')}</button>`).join(''):`<p role="status">Совпадений нет. Попробуй название функции или ник.</p>`;}
function go(row){if(!row)return;const url=row.url+(row.url.includes('?')?'&':'?')+'focus='+encodeURIComponent(row.id);lastUrl=url;notice='Открыто: '+row.title;M.navTo(url.slice(1));status();highlight(row.id);}
function highlight(key){setTimeout(()=>{
 const main=document.querySelector('main');if(!main)return;
 let node=[...main.querySelectorAll('[data-search-target]')].find(n=>n.dataset.searchTarget===key)||[...main.querySelectorAll('[data-id]')].find(n=>n.dataset.id===key)||main.querySelector('[name="'+CSS.escape(key)+'"]')||main.querySelector('h1');
 if(!node)return;
 const group=node.closest('.v6-action-group');if(group){document.querySelectorAll('.v6-action-group').forEach(g=>g.hidden=g!==group);document.querySelectorAll('[data-action="v71-action-category"]').forEach((b,i)=>{const on=[...document.querySelectorAll('.v6-action-group')].indexOf(group)===i;b.classList.toggle('active',on);b.setAttribute('aria-pressed',String(on));});}
 document.querySelectorAll('.cm73-search-highlight').forEach(n=>n.classList.remove('cm73-search-highlight'));node.classList.add('cm73-search-highlight');node.scrollIntoView({block:'center',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'});
 },120);}
window.CM73Search={box:()=>`<div class="cm73-global-search"><label class="input-wrap">${icon('search')}<input type="text" id="cm73-search-input" value="${E(query)}" placeholder="Игрок, предмет или действие" aria-label="Поиск по панели" autocomplete="off" maxlength="100"><button type="button" data-action="v73-search-clear" aria-label="Очистить поиск" ${query?'':'hidden'}>${icon('x')}</button><kbd>Ctrl K</kbd></label><div id="cm73-search-results" class="cm73-search-results" hidden></div></div>`,search,aliases};
H['admin-search']=H['admin-command']=()=>{if(X.canAdmin()){keepFocus=true;document.getElementById('cm73-search-input')?.focus();}};
H['v73-search-clear']=()=>{query='';notice='';matches=[];clearTimeout(timer);try{sessionStorage.removeItem(queryKey);}catch{}const input=document.getElementById('cm73-search-input');if(input){input.value='';input.focus();}document.querySelector('[data-action="v73-search-clear"]')?.setAttribute('hidden','');status();document.querySelectorAll('.cm73-search-highlight').forEach(n=>n.classList.remove('cm73-search-highlight'));};
H['v73-search-go']=b=>{keepFocus=false;go(matches[Number(b.dataset.index)]);document.getElementById('cm73-search-results').hidden=true;};
document.addEventListener('pointerdown',e=>{if(!e.target.closest('.cm73-global-search')){keepFocus=false;const box=document.getElementById('cm73-search-results');if(box)box.hidden=true;}});
V.after.push((page)=>{if(page!=='admin'){query='';keepFocus=false;return;}const input=document.getElementById('cm73-search-input');if(!input)return;
 input.addEventListener('input',()=>{query=input.value;notice='';keepFocus=true;try{query?sessionStorage.setItem(queryKey,query):sessionStorage.removeItem(queryKey);}catch{}clearTimeout(timer);document.querySelector('[data-action="v73-search-clear"]').hidden=!query;matches=search(query);status();
  if(query.trim().length>=3&&matches.length&&matches[0].score<2)timer=setTimeout(()=>{const m=matches[0];if(m){const url=m.url+(m.url.includes('?')?'&':'?')+'focus='+encodeURIComponent(m.id);if(location.hash!==url)go(m);else highlight(m.id);}},420);
 });
 input.addEventListener('keydown',e=>{if(e.key==='Enter'&&matches[0]){e.preventDefault();clearTimeout(timer);go(matches[0]);}if(e.key==='Escape'){document.getElementById('cm73-search-results').hidden=true;input.blur();keepFocus=false;}if(e.key==='Tab')keepFocus=false;});
 if(keepFocus){input.focus({preventScroll:true});input.setSelectionRange(query.length,query.length);status();}
 const focus=M.UI.params.get('focus');if(focus)highlight(focus);
});
})();
