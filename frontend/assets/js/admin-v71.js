/* Restored navigation from admin-web/frontend/assets/js/cabinet-runtime.js.
   Every source section has a screen below; old renderers keep their working
   local forms. New aliases don't replace game-specific tooling with empty cards. */
(function(){
'use strict';
const M=CM,A=CM71,V=CMV4,W=CM5,X=CM6,Q=CMReview,E=M.esc,H=M.handlers,F=M.forms,s=()=>M.state;
const priorAdmin=M.renderers.admin,ext=W.adminExtensions;
const entries=[
 ['overview','Обзор','','grid'],
 ['statistics','Аналитика сайта','Посещения, покупки и популярные предметы.','chart'],
 ['players','Игроки и аккаунты','Найди игрока, затем выбери действие.','users'],
 ['requests','Обращения и жалобы','Вопросы игроков, жалобы и проблемы с покупками.','chat'],
 ['moderation','Наказания','Действующие ограничения, сроки и отмена.','shield'],
 ['recovery','Восстановление','Смена пароля, активные сеансы и ограничения входа.','key'],
 ['shop','Каталог предметов','Предметы, цены, текстуры и лимиты покупок.','bag'],
 ['finance','Покупки и платежи','Заказы, выдача, пополнения и возвраты.','wallet'],
 ['world','Карты миров','Публикация карт и расписание обновлений.','map'],
 ['monitor','Мониторинг сервера','TPS, память, миры, база и ошибки плагинов.','activity'],
 ['releases','Релизы и обновления','Коммит, версии плагинов, CI и откат.','package'],
 ['mapart','Картины в мире','Загрузки, авторы и координаты картин.','grid'],
 ['narcotics','Наркотики','Рецепты котла, эффекты и текстуры предметов плагина.','package'],
 ['audit','Журнал действий','Кто, что изменил, когда и почему.','history'],
 ['admins','Команда','Учётные записи команды и роли.','users'],
 ['content','Контент сайта','Страницы, галерея, вопросы и навигация.','edit'],
 ['settings','Настройки сайта','Название, адрес и отображение сайта.','settings'],
 ['system','Сервер и службы','Состояние подключений и аккаунты сайта.','server']
];
const groups=[['Проект',['overview','statistics']],['Игроки',['players','requests','moderation','recovery']],['Экономика и мир',['shop','finance','world','mapart','narcotics']],['Сервер',['monitor','releases']],['Сайт и команда',['content','admins','audit','settings','system']]];
const legacyRoutes={performance:'statistics',inventories:'players',investigations:'moderation',anticheat:'moderation',whitelist:'players',bank:'finance',elections:'world',laws:'world',points:'world',recipes:'shop',logs:'audit',sources:'system',jobs:'system',backups:'system',maintenance:'system'};
A.adminEntries=entries;
function badge(id){const n=id==='requests'?s().tickets.filter(t=>t.status==='open').length:id==='finance'?s().orders.filter(o=>o.status==='queued').length:0;return n?`<small class="v71-nav-count">${n}</small>`:'';}
function nav(tab){return `<a class="v71-admin-brand" href="#home" aria-label="CopiMine — на главную страницу сайта" title="На главную CopiMine"><img src="${M.asset('assets/img/logo.webp')}" width="152" height="34" alt="CopiMine" decoding="async"><span>Управление</span></a><nav class="v71-admin-nav" aria-label="Разделы управления">${groups.map(([name,ids],index)=>{const folded=!!s().preferences?.navFolded?.[index]&&!ids.includes(tab);return `<section class="v71-nav-group ${folded?'folded':''}"><button class="v71-nav-caption" type="button" data-action="v71-nav-group" data-id="${index}" aria-expanded="${!folded}"><span>${name}</span>${icon('chevron-down')}</button><div>${ids.map(id=>{const row=entries.find(e=>e[0]===id);return `<a href="#admin/${id}" class="${tab===id?'active':''}" ${tab===id?'aria-current="page"':''} title="${E(row[1])}">${icon(row[3])}<span>${row[1]}</span>${badge(id)}</a>`;}).join('')}</div></section>`;}).join('')}</nav><div class="v71-admin-user">${W.head(M.me(),32)}<div><strong>${A.publicPlayer(M.me())}</strong><small>Серверный аккаунт</small></div><a href="#account" title="Личный кабинет" aria-label="Личный кабинет">${icon('user')}</a><button type="button" class="v71-exit-icon" data-action="logout" title="Выйти" aria-label="Выйти">${icon('log-out')}</button></div>`;}
function legacy(key,sub){return A.clean(V.legacyAdminContent(key,sub));}
function operations(key,sub){return A.clean(CMOperations.render(key,sub));}
function prior(key){return A.clean(priorAdmin(key),'#view');}
function subtabs(key,items,selected){return `<nav class="v71-tabs" aria-label="Подразделы">${items.map(([id,label])=>`<a href="#admin/${key}?sub=${id}" class="${id===selected?'active':''}">${label}</a>`).join('')}</nav>`;}
function playerRows(query='',scope='all'){
 let rows=s().players.filter(p=>[p.name,p.displayName,p.minecraftName,p.minecraftUuid,p.id,p.role,...(p.aliases||[])].join(' ').toLowerCase().includes(query.toLowerCase()));
 if(scope==='blocked')rows=rows.filter(p=>p.webBlocked||p.status==='banned');if(scope==='team')rows=rows.filter(p=>CM6Domain.roleLevel(p.role)>=10);
 return rows.length?V.table(['Игрок','Аккаунт Minecraft','Игровой баланс','Донатный баланс','Статус',''],rows.map(p=>[`<a href="#admin/players?player=${E(p.id)}" class="v71-player-link">${W.head(p,36)}<span><strong>${E(p.displayName||p.name)}</strong><small>${E(p.minecraftName||p.name)}</small></span></a>`,M.pill(p.gameLinkVerified||p.linked?'Привязан':'Не привязан',p.gameLinkVerified||p.linked?'green':''),`<strong class="numeric">${p.AR==null?'—':Number(p.AR).toLocaleString('ru-RU')} АР</strong>`,`<span class="numeric">${p.CM==null?'—':Number(p.CM).toLocaleString('ru-RU')} CM</span>`,M.pill(p.webBlocked||p.status==='banned'?'Заблокирован':'Активен',p.webBlocked||p.status==='banned'?'danger':'green'),M.link('Открыть',`#admin/players?player=${E(p.id)}`,'arrow-up-right','small ghost')])):M.empty('Ничего не найдено','Попробуй другой ник или ID.','search');
}
function players(){const q=X.board.get('playerQuery')||'',scope=X.board.get('playerScope')||'all';return `<div class="v71-player-directory"><div class="v71-directory-controls"><label class="input-wrap">${icon('search')}<input id="v71-player-search" type="search" value="${E(q)}" placeholder="Ник, игровой ник или ID" aria-label="Поиск игроков"></label><select id="v71-player-scope" aria-label="Фильтр аккаунтов"><option value="all"${scope==='all'?' selected':''}>Все аккаунты</option><option value="team"${scope==='team'?' selected':''}>Команда</option><option value="blocked"${scope==='blocked'?' selected':''}>Заблокирован</option></select><span class="hint">Аккаунтов: ${s().players.length}</span></div><div id="v71-player-rows">${playerRows(q,scope)}</div><div class="v71-inline-info">${icon('info')}Открой игрока: покупки, инвентари, наказания и действия — в его карточке.</div></div>`;}
function siteSettings(){return window.CMCMS?.siteSettingsView?.()||'<section class="panel" role="status"><h2>Настройки сайта</h2><p>Редактор сейчас недоступен. Обнови страницу и попробуй снова.</p></section>';}
const rewrite=html=>html.replaceAll('#admin/moderation?sub=tickets','#admin/requests').replaceAll('#admin/moderation?sub=cases','#admin/moderation').replaceAll('#admin/moderation?sub=anticheat','#admin/moderation').replaceAll('#admin/moderation?sub=sanctions','#admin/moderation').replaceAll('#admin/world?sub=elections','#admin/world').replaceAll('#admin/world?sub=laws','#admin/world').replaceAll('#admin/world?sub=points','#admin/world').replaceAll('#admin/system?sub=plugins','#admin/system').replaceAll('#admin/system?sub=jobs','#admin/system').replaceAll('#admin/system?sub=backups','#admin/system').replaceAll('#admin/access?sub=audit','#admin/audit').replaceAll('#admin/players?sub=inventory','#admin/players');
M.renderers.admin=(key='overview')=>{
 if(!X.canAdmin())return `<div class="admin-shell"><aside class="admin-sidebar" hidden></aside><div class="admin-main"><div class="breadcrumb"></div><main id="view" class="container">${A.heading(X.ready?'Страница недоступна':'Проверяем доступ…',X.ready?'Войди в свой аккаунт или вернись на сайт.':'Получаем подтверждение сеанса от сервера.')}<div class="actions">${M.link('Войти','#auth/login','user','primary')}${M.link('Главная','#home','arrow-left','ghost')}</div></main></div></div>`;
 let sub=M.UI.params.get('sub')||'';if(key==='access'||key==='security')key='admins';if(legacyRoutes[key])key=legacyRoutes[key];if(!entries.some(e=>e[0]===key))key='overview';
 let body='';const meta=entries.find(e=>e[0]===key)||entries[0];
 switch(key){
 case 'overview':body=ext.overview();break;
 case 'players':body=sub==='list'||!(M.UI.params.has('player')||X.board?.get('player'))?players():ext.players(sub);break;
 case 'requests':body=operations('moderation','tickets');break;
 case 'moderation':body=ext.moderation(sub);break;
 case 'recovery':body=window.CM73?.recoveryView?.()||prior(key);break;
 case 'audit':body=ext.logs(sub);break;
 case 'admins':body=ext.access(sub||'team');break;
 case 'settings':body=siteSettings();break;
 case 'statistics':body=(ext.statistics||prior)(sub||'overview');break;
 case 'content':body=prior('content');break;
 case 'world':body=ext.world(sub);break;
 case 'monitor':body=ext.monitor(sub);break;
 case 'releases':body=ext.releases(sub);break;
 case 'mapart':body='<div data-mapart-admin></div>';break;
 case 'narcotics':body='<div data-narcotics-admin></div>';break;
 case 'system':body=ext.system(sub);break;
 case 'shop':body=W.adminExtensions.shop(sub);break;
 case 'finance':body=ext.finance(sub||'orders');break;
 default:body=ext.overview();
 }
 return `<div class="admin-shell v7-admin-shell v71-admin-shell"><aside class="admin-sidebar">${nav(key)}</aside><div class="admin-main"><header class="admin-top"><div class="breadcrumb"><button type="button" class="icon-btn v71-admin-mobile-menu" data-action="v71-admin-mobile-menu" aria-label="Открыть меню">${icon('menu')}</button><span>CopiMine</span>${icon('chevron-right')}<strong aria-current="page">${meta[1]}</strong></div>${window.CM73Search?.box()||''}<div class="actions"><a href="#home" class="btn ghost small">${icon('arrow-up-right')}<span>Сайт</span></a></div></header><main id="view" class="admin-content"><div class="v71-admin-heading"><div><h1>${meta[1]}</h1>${meta[2]?`<p>${meta[2]}</p>`:''}</div></div><div class="v71-admin-surface" data-admin-section="${key}">${rewrite(body||M.empty('Нет данных','В этом разделе пока нет записей.'))}</div></main></div></div>`;
};
H['v71-nav-group']=b=>{const section=b.closest('.v71-nav-group'),folded=section.classList.toggle('folded');b.setAttribute('aria-expanded',String(!folded));(s().preferences.navFolded||={})[b.dataset.id]=folded;M.save();};
function closeAdminDrawer(){document.querySelector('.admin-sidebar')?.classList.remove('v71-open');document.body.classList.remove('v71-drawer-open');}
H['v71-admin-mobile-menu']=()=>{const sidebar=document.querySelector('.admin-sidebar');if(!sidebar)return;const open=sidebar.classList.toggle('v71-open');document.body.classList.toggle('v71-drawer-open',open);if(!document.getElementById('v71-sidebar-overlay')){const button=document.createElement('button');button.type='button';button.id='v71-sidebar-overlay';button.setAttribute('aria-label','Закрыть меню');button.addEventListener('click',closeAdminDrawer);document.body.append(button);}};
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&document.body.classList.contains('v71-drawer-open')){closeAdminDrawer();document.querySelector('[data-action="v71-admin-mobile-menu"]')?.focus();}});
function results(query){
 const q=query.trim().toLowerCase(),rank=(items,text)=>items.map(item=>({item,score:Math.min(X.search.score({title:text(item),detail:''},q),X.search.score({title:text(item),detail:''},X.search.keyboard(q)))})).filter(row=>Number.isFinite(row.score)).sort((a,b)=>a.score-b.score).map(row=>row.item);
 const sections=rank(entries,e=>e[1]+' '+e[2]),people=rank(s().players,p=>p.name+' '+p.displayName+' '+p.id),items=rank(s().catalog,p=>p.name+' '+p.id);
 const target=s().players.find(p=>p.id===M.UI.params.get('player'))||s().players.find(p=>p.role==='player')||s().players[0];
 const actions=q?rank(X.actions.filter(a=>a.scope!=='server'&&!a.requiresPhysicalAdmin),a=>a.label+' '+a.id+' '+(a.description||'')):[];
 const orders=q?rank(s().orders,o=>o.id+' '+o.name+' '+o.player):[];
 const groups=[
 ['Разделы',sections.slice(0,q?10:8).map(e=>[e[1],e[2],'#admin/'+e[0],e[3]])],
 ['Игроки',people.slice(0,4).map(p=>[p.name,p.id,'#admin/players?player='+encodeURIComponent(p.id),'user'])],
 ['Действия',actions.slice(0,6).map(a=>[a.label,'Для игрока: '+target.name,'#admin/players?sub=actions&player='+encodeURIComponent(target.id)+'&find='+encodeURIComponent(a.label),a.icon||'sliders'])],
 ['Предметы',q?items.slice(0,6).map(p=>[p.name,p.id,'#admin/shop?sub=catalog&focus='+encodeURIComponent(p.id),'bag']):[]],
 ['Заказы',orders.slice(0,4).map(o=>[o.id,o.player+' / '+o.name,'#admin/finance?sub=orders&focus='+encodeURIComponent(o.id),'bag'])]
 ];
 return `<div class="v71-search-results">${groups.map(([label,rows])=>rows.length?`<section><h3>${label}</h3>${rows.map(([name,desc,url,ic])=>`<a href="${E(url)}">${icon(ic)}<span><strong>${E(name)}</strong><small>${E(desc)}</small></span>${icon('arrow-right')}</a>`).join('')}</section>`:'').join('')||M.empty('Ничего не найдено','Попробуй другой запрос.','search')}</div>`;
}
H['admin-search']=H['admin-command']=()=>{if(!X.canAdmin())return;M.openModal('Поиск в панели',`<label class="input-wrap v71-command-input">${icon('search')}<input id="v71-command-query" type="search" placeholder="Игрок, предмет, раздел или действие" autocomplete="off" aria-label="Поиск"><kbd>Esc</kbd></label><div id="v71-command-results">${results('')}</div><p class="v71-command-foot">Стрелки — выбор, Enter — открыть, Esc — закрыть.</p>`,'v71-command-dialog');const input=document.getElementById('v71-command-query');input.addEventListener('input',()=>document.getElementById('v71-command-results').innerHTML=results(input.value));input.focus();};
document.addEventListener('keydown',e=>{if(!document.querySelector('#v71-command-query'))return;const nodes=[...document.querySelectorAll('.v71-search-results a')];if(['ArrowDown','ArrowUp'].includes(e.key)){e.preventDefault();const i=nodes.indexOf(document.activeElement);nodes[(i+(e.key==='ArrowDown'?1:-1)+nodes.length)%nodes.length]?.focus();}if(e.key==='Enter'&&document.activeElement.id==='v71-command-query'&&nodes[0]){e.preventDefault();nodes[0].click();}});
V.after.push((page,tab)=>{
 if(page!=='admin')return;
 const searchInput=document.getElementById('v71-player-search'),scopeSelect=document.getElementById('v71-player-scope'),rowsHost=document.getElementById('v71-player-rows');
 const paint=()=>{rowsHost.innerHTML=playerRows(searchInput.value,scopeSelect.value);};
 searchInput?.addEventListener('input',()=>{X.board?.set('playerQuery',searchInput.value||null);paint();});
 scopeSelect?.addEventListener('change',()=>{X.board?.set('playerScope',scopeSelect.value==='all'?null:scopeSelect.value);paint();});
 document.querySelectorAll('.admin-sidebar a').forEach(a=>a.addEventListener('click',closeAdminDrawer));
});

let currentActionGroup=0;
function selectActionGroup(index){
 currentActionGroup=index;X.board?.set('actionGroup',index);
 document.querySelectorAll('.v6-action-group').forEach((node,i)=>node.hidden=i!==index);
 document.querySelectorAll('[data-action="v71-action-category"]').forEach((node,i)=>{node.classList.toggle('active',i===index);node.setAttribute('aria-pressed',String(i===index));});
}
H['v71-action-category']=button=>{
 const input=document.getElementById('v6-action-search');
 if(input){input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));}
 selectActionGroup(Number(button.dataset.index));
};
V.after.push((page,tab)=>{
 if(page!=='admin'||tab!=='players')return;
 const groups=[...document.querySelectorAll('.v6-action-group')],host=document.querySelector('.v6-action-groups');
 if(!host||!groups.length)return;
 host.classList.add('v71-action-workspace');
 const nav=document.createElement('nav');nav.className='v71-action-categories';nav.setAttribute('aria-label','Категории действий');
 nav.innerHTML=groups.map((g,i)=>`<button type="button" data-action="v71-action-category" data-index="${i}"><span>${E(g.querySelector('h2').firstChild.textContent)}</span><b>${g.querySelectorAll('[data-action-search]').length}</b></button>`).join('');
 host.before(nav);const savedGroup=X.board?.get('actionGroup');currentActionGroup=Number.isInteger(savedGroup)?savedGroup:currentActionGroup;selectActionGroup(Math.min(currentActionGroup,groups.length-1));
 const empty=document.createElement('div');empty.className='v71-action-empty';empty.hidden=true;empty.innerHTML=M.empty('Действие не найдено','Попробуй другой запрос.','search');host.append(empty);
 document.getElementById('v6-action-search').addEventListener('input',e=>{X.board?.set('actionSearch',e.target.value.trim()||null);
  if(!e.target.value.trim()){empty.hidden=true;selectActionGroup(currentActionGroup);return;}
  nav.querySelectorAll('button').forEach(n=>{n.classList.remove('active');n.setAttribute('aria-pressed','false');});
  empty.hidden=groups.some(g=>!g.hidden);
 });
 const find=M.UI.params.get('find')||X.board?.get('actionSearch');if(find){const input=document.getElementById('v6-action-search');input.value=find;input.dispatchEvent(new Event('input',{bubbles:true}));}
});
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>M.render(),{once:true});else M.render();
})();
