/* In-place function search. Query changes only the result region, never the URL,
   shell, user selection or data. Destructive actions cannot execute from a match. */
(function(){
'use strict';const W=CM5,M=CM,V=CMV4,D=CM5Domain,E=M.esc;
const data=[
['players','Карточка игрока','Игроки','players?sub=profile','user','player profile username nick user игрок ник профиль скин голова персонаж'],
['inventory','Инвентарь игрока','Игроки','players?sub=inventory','package','inventory items slots armor offhand hotbar инвентарь броня шлем вещи ячейки предметы рюкзак снимок дуюп'],
['playerlogs','Действия игрока','Игроки','players?sub=activity','history','history player logs actions действия игрока история сломал поставил украл блок контейнер'],
['password','Сброс пароля и PIN','Игроки','players?sub=access','lock','password pass pin reset recovery auth nlogin authme сброс пароля пароль пин банк забыл вход логин восстановление'],
['sessions','Отозвать сессии','Игроки','players?sub=access','monitor','session logout device revoke launcher сессии выйти устройства лаунчер завершить доступ'],
['whitelist','Заявки на доступ','Игроки','players?sub=whitelist','users','whitelist allowlist join вайтлист вход допуск заявка'],
['catalog','Каталог и цены','Магазин','shop?sub=catalog','bag','catalog shop price store item currency магазин каталог цена описание товар лавка артефакт'],
['orders','Заказы','Магазин','shop?sub=orders','bag','order purchase basket заказы покупки корзина оформление'],
['delivery','Очередь выдачи','Магазин','shop?sub=delivery','package','delivery claim grant reclaim queue выдача получить забрать очередь не пришло'],
['pricehistory','История цен','Магазин','shop?sub=history','history','prices history edit история цен кто менял стоимость'],
['balances','Балансы и переводы','Финансы','finance?sub=accounts','wallet','balance money ar cm credit debit баланс деньги валюта начислить списать перевод'],
['payments','Платежи и возвраты','Финансы','finance?sub=payments','wallet','payment refund donation sbp yookassa платежи оплата донат возврат сбп юкасса'],
['ledger','Журнал операций','Финансы','finance?sub=ledger','history','ledger transaction operations операции счета транзакции'],
['treasury','Казна','Финансы','finance?sub=treasury','wallet','treasury budget tax taxes bank казна бюджет налоги государство президент'],
['map','Метки и зоны карты','Мир','world?sub=map','map','map zone polygon marker point city region карта метка зона территория границы город координаты'],
['mappublish','Публикация карты','Мир','world?sub=map','clock','snapshot publish schedule render delay снимок публикация рендер задержка 12 часов'],
['elections','Выборы и кандидаты','Мир','world?sub=elections','vote','election vote candidate president stage program выборы кандидат президент этапы программа голосование ЦИК'],
['laws','Законы','Мир','world?sub=laws','file','laws politics veto approval законы политика согласование'],
['events','События','Мир','world?sub=events','clock','events rift schedule ивент события расписание анонс'],
['points','Игровые точки','Мир','world?sub=points','map','atm point shop location банкомат лавка точка'],
['tickets','Обращения и жалобы','Модерация','moderation?sub=tickets','chat','ticket support help report жалобы обращения репорт помощь поддержка'],
['cases','Расследования и откаты','Модерация','moderation?sub=cases','shield','coreprotect rollback restore ban grief case грифер расследование откат восстановить постройку украл'],
['anticheat','Сигналы античита','Модерация','moderation?sub=anticheat','shield','anticheat grim violation alerts fly speed античит читы нарушения сигналы'],
['logs','Журналы сервера','Журналы','logs','terminal','logs console terminal raw technical live realtime filter error stacktrace логи консоль ошибки поток технические'],
['recipes','Редактор рецептов','Плагины','recipes','package','recipe craft brew cauldron narcotics ingredients creative рецепты котёл варка ингредиенты наркотики крафт'],
['privateevents','Действия плагина','Плагины','recipes?sub=events','history','narcotics consumption confiscation effects overdose изъятие употребление эффекты передозировка логи варки'],
['cms','Страницы и правила','CMS','content?sub=pages&id=rules','file','cms rules pages policy terms text editor drafts текст правила страницы публикация черновик'],
['gallery','Галерея','CMS','content?sub=gallery','images','gallery photo image carousel screenshot галерея фото скриншот карусель картинки'],
['faq','Вопросы и ответы','CMS','content?sub=faq','chat','faq answers help questions вопросы ответы чаво'],
['home','Главная страница','CMS','content?sub=home','grid','homepage title description ip главная заголовок баннер описание'],
['downloads','Загрузки и релизы','CMS','content?sub=downloads','download','downloads modpack launcher version install archive файл архив моды скачать лаунчер релиз'],
['discord','Discord и ссылки','CMS','content?sub=links','external','discord invite links social дискорд приглашение ссылка'],
['statistics','Статистика','Обзор','statistics','chart','statistics stats charts analytics tps mspt online revenue статистика графики онлайн доходы нагрузка'],
['team','Роли и права','Команда','access','users','team roles permission access admin owner роли права доступ админ модератор'],
['audit','Аудит администрации','Команда','access?sub=audit','history','audit administrator security trail аудит администраторов кто менял'],
['messages','Письма и SMS','Команда','access?sub=messages','mail','mail sms email outbox otp recovery code почта смс письма код сброса'],
['notifications','Подключение почты и SMS','Система','system?sub=notifications','settings','smtp sms provider send mail email gateway почтовый сервер провайдер отправка'],
['plugins','Плагины и конфигурация','Система','system?sub=plugins','settings','plugins config registry reload yaml плагины конфиг перечитать параметры'],
['jobs','Очередь задач','Система','system?sub=jobs','clock','jobs task queue retry cron задачи очередь повтор планировщик'],
['backups','Резервные копии','Система','system?sub=backups','database','backup restore snapshot recovery export бэкап резервная копия восстановить'],
['integration','Службы и подключения','Система','system?sub=integrations','server','server rcon database postgres services health службы сервер база подключения']
];
const features=data.map(([id,title,group,path,ic,words])=>({id,title,group,path,ic,aliases:[...new Set([...words.split(' '),...['открыть','найти','показать','где','управление'].map(x=>x+' '+title),title+' '+group])]}));W.features=features;let query='';
function results(value){query=value;const root=document.getElementById('view');if(!root)return;if(!query.trim()){M.render();return;}const rows=D.rank(features,query,50);root.innerHTML=`<div class="function-search-heading"><h1>Поиск функций</h1><p>Найдено: ${rows.length}</p></div><div class="function-results">${rows.map(x=>`<a class="function-result" href="#admin/${x.path}" data-action="function-open" data-path="${x.path}"><span class="function-icon">${icon(x.ic)}</span><span><small>${E(x.group)}</small><strong>${E(x.title)}</strong><span class="hint">${E(x.aliases.slice(0,5).join(' · '))}</span></span>${icon('arrow-up-right')}</a>`).join('')||M.empty('Ничего не найдено','Попробуй название задачи: инвентарь, PIN, казна или логи.','search')}</div>`;}
M.handlers['function-open']=b=>{query='';const input=document.getElementById('function-search');if(input)input.value='';M.navTo('admin/'+b.dataset.path);};
M.handlers['admin-command']=()=>{document.getElementById('function-search')?.focus();};
V.after.push(page=>{if(page!=='admin')return;const top=document.querySelector('.admin-top'),button=top?.querySelector('.command-search');if(button){const wrap=document.createElement('label');wrap.className='function-search-field';wrap.innerHTML=icon('search')+'<input type="search" id="function-search" aria-label="Найти функцию" placeholder="Найти функцию…"><kbd>Ctrl K</kbd>';button.replaceWith(wrap);const input=wrap.querySelector('input');input.value=query;let timer;input.addEventListener('input',()=>{clearTimeout(timer);timer=setTimeout(()=>results(input.value),70);});input.addEventListener('keydown',e=>{if(e.key==='Escape'){input.value='';query='';M.render();input.focus();}if(e.key==='Enter'){document.querySelector('.function-result')?.click();}});}const input=document.getElementById('function-search');if(query&&input?.value===query)results(query);});
document.addEventListener('click',e=>{if(e.target.closest('.admin-sidebar a')||e.target.closest('.site-nav a')){query='';const input=document.getElementById('function-search');if(input)input.value='';}},true);
document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'&&M.UI.page==='admin'){e.preventDefault();document.getElementById('function-search')?.focus();}});
})();
