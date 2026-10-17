/* Shared shell sources: official wordmark assets/img/logo.png and mobile
   monogram assets/img/icon.png from supplied launcher archive.
   Future game data must arrive via backend; no live APIs in this preview. */
/* Shared v4 shell and persisted local-only data. */
(function(){
'use strict';
const M=window.CM, D=window.CMDomain, S=M.state, E=M.esc;
const uid=(prefix='id')=>prefix+'-'+(crypto.randomUUID?crypto.randomUUID():Date.now()+'-'+Math.random().toString(36).slice(2));
const sampleTime=Date.UTC(2026,9,13,7,40);
function page(id,title,body){return{id,slug:id,revision:1,draft:{title,body,description:'',nav:true},published:{title,body,description:'',nav:true},publishedAt:sampleTime,publishedRevision:1,status:'published',history:[]};}
// Gallery uses local project materials: world.webp from archive + extra crops from
// the supplied world shot and local/public map screenshots saved into assets/img/.
const defaultGallery=[
 {id:'world-original',title:'Вечер на сервере',caption:'Общий вид мира CopiMine',src:'assets/img/world.webp',alt:'Ночной игровой пейзаж CopiMine',published:true,position:0},
 {id:'world-plaza',title:'Центральная площадь',caption:'Фрагмент основного мира',src:'assets/img/gallery-world-plaza.webp',alt:'Площадь и постройки в мире CopiMine вечером',published:true,position:1},
 {id:'world-tree',title:'Зелёная зона',caption:'Спокойная часть спавна с постройками',src:'assets/img/gallery-world-tree.webp',alt:'Пейзаж с деревьями и постройками в CopiMine',published:true,position:2},
 {id:'world-evening',title:'Вечерний маршрут',caption:'Игровой кадр с дальним планом',src:'assets/img/gallery-world-evening.webp',alt:'Вид на дальнюю часть игрового мира CopiMine',published:true,position:3},
 {id:'map-overview',title:'Карта мира',caption:'Общий опубликованный снимок карты',src:'assets/img/gallery-map-overview.jpg',alt:'Общий снимок карты мира CopiMine',published:true,position:4},
 {id:'map-central',title:'Центральный сектор карты',caption:'Участок с дорогами и застройкой',src:'assets/img/gallery-map-central.jpg',alt:'Центральный участок карты CopiMine',published:true,position:5},
 {id:'map-south',title:'Южное направление',caption:'Ещё один пример публикации карты',src:'assets/img/gallery-map-south.jpg',alt:'Южная часть опубликованной карты CopiMine',published:true,position:6}
];
function ensure(){
 const s=M.state;
 if(!s.v4Ready){
  s.version=4;s.v4Ready=true;s.config.serverIp='copimine.ru';s.config.headline='Строй. Торгуй.\nИграй с друзьями.';s.config.description='Minecraft-сервер с экономикой, выборами и событиями.';
  s.config.siteTitle='CopiMine';s.config.cmRate=1;s.config.topupPacks=[50,100,250,500,1000];
  s.config.gameVersion='26.3';s.config.minMemory=4;
  s.pages=[page('rules','Правила сервера','## Чужие вещи и постройки\n\nНе ломай чужие постройки и не забирай вещи без разрешения. Если участок общий, заранее согласуй изменения с участниками.\n\n## Честная игра\n\nНе используй читы, дюпы и ошибки сервера для получения преимущества. Найденную ошибку отправь в обращении, не публикуй способ её повторения.\n\n## Общение\n\nБез угроз, травли, спама и публикации личных данных. Спор по игре не повод переходить на личности.\n\n## Жалобы и ограничения\n\nУкажи игрока, место, примерное время и что произошло. Модератор проверит историю и объяснит решение. Обжаловать блокировку можно через обращение.'),page('help','Как получить покупку','## После покупки\n\nЗаказ сразу появится в личном кабинете. Получатель — привязанный к аккаунту Minecraft-ник.\n\n## В игре\n\nЗайди на сервер и открой меню покупок. Проверь свободные места в инвентаре и подтверди получение. Если ты не в сети, предмет останется в очереди.\n\n## Что делать при ошибке\n\nНе покупай тот же предмет повторно. Открой заказ и отправь обращение: его номер подставится автоматически.'),page('privacy','Данные аккаунта','## Для чего нужны контакты\n\nПочта или телефон используются для входа и восстановления доступа. Привязанный аккаунт VK — дополнительный способ входа.\n\n## Управление доступом\n\nВ настройках можно проверить подключённые устройства и завершить сессии.\n\nЭто проект текста для согласования владельцем перед публикацией рабочего сайта.')];
  s.gallery=M.clone(defaultGallery);
  s.faq=[{id:'start',question:'С чего начать?',answer:'Создай аккаунт, скачай сборку и добавь copimine.ru в список серверов.',published:true},{id:'delivery',question:'Когда придёт покупка?',answer:'После оформления она появится в очереди. Забери предмет через меню покупок в игре.',published:true},{id:'map',question:'Почему карта не в реальном времени?',answer:'Снимки публикуются раз в 12 часов с задержкой. Позиции игроков и подземелья не показываются.',published:true}];
  s.markers=[];s.zones=[];s.mapImage=null;s.mapPending=null;s.mapPublished={id:'journeymap-local-20261013',markers:[],zones:[],image:null,capturedAt:sampleTime,publishedAt:sampleTime+86400000};
  s.mapBounds={x:-80,z:-112,width:144,height:160};s.source.map='Локальные day-тайлы JourneyMap: исследованный участок тестового мира, не полный мир.';
  s.payments=[];s.cases=[];s.signals=[];s.inventorySnapshots=[];s.whitelist=[];s.jobs=[];s.priceHistory=[];s.backups=[];s.launcherRequests={};s.launcherDevices=[];s.contactIdentities=[];s.authChallenges={};s.siteSessions=[];s.campaigns=[];s.laws=[];s.pluginConfigs=[];s.serviceMetrics=[];s.treasury={balance:0,transactions:[]};s.adminTabs={};s.customRoles=[];
  s.players.forEach(p=>{p.linked=true;p.email=p.id==='demo-001'?'demo@copimine.example':'';});
  s.siteSessions.push({id:'browser-local',name:'Этот браузер',owner:'DemoBuilder',at:Date.now(),current:true,revoked:false});
  M.save();
 }

}
ensure();
const iconset=window.CMIcons||{};
Object.assign(iconset,{
 'mail':'<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 6 9 6 9-6"/>',
 'phone':'<path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6 19.8 19.8 0 0 1-3.1-8.7A2 2 0 0 1 4 2h3a2 2 0 0 1 2 1.7c.1 1 .4 2 .7 2.9a2 2 0 0 1-.5 2.1L8 9.9a16 16 0 0 0 6 6l1.2-1.2a2 2 0 0 1 2.1-.5c.9.3 1.9.6 2.9.7a2 2 0 0 1 1.8 2Z"/>',
 'external':'<path d="M15 3h6v6M10 14 21 3M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5"/>',
 'chart':'<path d="M3 3v18h18M7 14l4-4 4 3 5-7"/>',
 'images':'<rect x="3" y="3" width="15" height="15" rx="2"/><path d="M7 21h12a2 2 0 0 0 2-2V7M3 14l4-4 4 4 3-3 4 4"/><circle cx="13" cy="7" r="1"/>',
 'save':'<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h12l4 4v12a2 2 0 0 1-2 2Z"/><path d="M17 21v-8H7v8M7 3v5h8"/>',
 'send':'<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',
 'history':'<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5M12 7v5l4 2"/>',
 'link':'<path d="m10 13 4-4M8 16l-2 2a4.2 4.2 0 0 1-6-6l4-4a4.2 4.2 0 0 1 6 0M16 8l2-2a4.2 4.2 0 0 1 6 6l-4 4a4.2 4.2 0 0 1-6 0" transform="translate(1 0) scale(.92 1)"/>',
 'vote':'<path d="M9 12 6 9l6-6 6 6-6 6-3-3ZM3 13l3-2m12 0 3 2v8H3v-8Zm0 0h4m10 0h4"/>',
 'database':'<ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14a9 3 0 0 0 18 0V5M3 12a9 3 0 0 0 18 0"/>',
 'chevron-left':'<path d="m15 18-6-6 6-6"/>',
 'vk':'<path d="M3 7h3c.4 3.7 1.6 5.3 2.6 5.8V7h3v3.3c1-.1 2.4-1.7 3-3.3h3.1a16 16 0 0 1-3.8 5.3 12 12 0 0 1 4.5 5.7H15c-.7-1.8-2.1-3.3-3.4-3.5V18H10C5.6 18 3.3 13.8 3 7Z" stroke="none" fill="currentColor"/>'
});
window.CMIcons=iconset;
const table=(heads,rows)=>`<div class="table-wrap"><table><thead><tr>${heads.map(h=>`<th scope="col">${h}</th>`).join('')}</tr></thead><tbody>${rows.length?rows.map(r=>`<tr>${r.map(c=>`<td>${c}</td>`).join('')}</tr>`).join(''):`<tr><td colspan="${heads.length}" class="table-empty">Записей пока нет.</td></tr>`}</tbody></table></div>`;
const panel=(title,body,action='')=>`<section class="panel"><div class="panel-head"><h3>${title}</h3>${action}</div>${body}</section>`;
const field=(label,name,value='',type='text',extra='')=>`<label class="field">${label}<input name="${name}" type="${type}" value="${E(value)}" ${extra}></label>`;
const area=(label,name,value='',rows=4,extra='')=>`<label class="field">${label}<textarea name="${name}" rows="${rows}" ${extra}>${E(value)}</textarea></label>`;
const options=(values,current)=>values.map(v=>{let a=Array.isArray(v)?v:[v,v];return`<option value="${E(a[0])}" ${String(a[0])===String(current)?'selected':''}>${E(a[1])}</option>`;}).join('');
const select=(label,name,values,current)=>`<label class="field">${label}<select name="${name}">${options(values,current)}</select></label>`;
const actions=(caption='Сохранить')=>`<div class="form-error" role="alert"></div><button type="submit" class="btn primary">${icon('check')}${caption}</button>`;
const note=(text,kind='')=>`<div class="notice ${kind}">${icon(kind==='warn'?'alert':'info')}<div>${text}</div></div>`;
const fileData=file=>new Promise((res,rej)=>{const r=new FileReader();r.onload=()=>res(r.result);r.onerror=rej;r.readAsDataURL(file);});
async function imageData(file,max=1400){if(!['image/png','image/jpeg','image/webp'].includes(file.type)||file.size>10*1024*1024)throw Error('Нужен PNG, JPG или WebP до 10 МБ.');const url=await fileData(file),img=new Image();await new Promise((r,j)=>{img.onload=r;img.onerror=j;img.src=url;});const c=document.createElement('canvas'),k=Math.min(1,max/img.width);c.width=Math.round(img.width*k);c.height=Math.round(img.height*k);c.getContext('2d').drawImage(img,0,0,c.width,c.height);return c.toDataURL('image/webp',.86);}
window.CMV4={uid,ensure,table,panel,field,area,select,actions,note,fileData,imageData,beforeRender:ensure,after:[],afterRender(page,tab){document.querySelectorAll('.reveal').forEach(n=>n.classList.add('seen'));for(const fn of this.after)fn(page,tab);},navigateBack(){const next=M.state.authReturn||'account';delete M.state.authReturn;M.save();M.navTo(D.safeLocalRoute(next));}};
// Keep the header identical on public pages, cabinet and administration.
header=function(active){const p=M.me(),count=M.state.cart.reduce((n,x)=>n+x.qty,0);return`<div class="global-header"><div class="container"><header class="site-header"><a class="brand" href="#home" aria-label="CopiMine — главная"><img class="wordmark" src="${M.asset('assets/img/logo.png')}" width="162" height="33" alt="CopiMine"><img class="brand-mark" src="${M.asset('assets/img/icon.png')}" alt="CopiMine" width="28" height="34"></a><nav class="site-nav" aria-label="Основная навигация">${[['home','Главная'],['shop','Магазин'],['donate','Донат'],['map','Карта']].map(([r,t])=>`<a href="#${r}" class="${active===r?'active':''}">${t}</a>`).join('')}</nav><div class="header-actions"><a class="icon-btn header-cart" href="#cart" aria-label="Корзина${count?', '+count+' товаров':''}">${icon('bag')}<span data-cart-badge class="cart-dot ${count?'':'hidden'}">${count}</span></a>${p?`<a class="btn ghost" href="#account">${icon('user')}<span class="account-label">${E(p.name)}</span></a><button type="button" class="btn ghost header-signout" data-action="logout" title="Выйти" aria-label="Выйти из аккаунта">${icon('log-out')}<span class="logout-label">Выйти</span></button>`:`<a class="btn ghost login-link" href="#auth/login">Войти</a><a class="btn ghost registration-link" href="#auth/register">Регистрация</a>`}${M.btn('Играть','play','play','primary')}${M.iconBtn('menu','Открыть меню','menu','class="mobile-nav-toggle"')}</div></header></div></div>`;};
const oldReset=M.handlers['reset-confirm'];M.handlers['reset-confirm']=()=>{oldReset();ensure();M.render();};
M.handlers['export']=()=>{const out=M.clone(M.state);out.session=null;out.players.forEach(p=>{delete p.auth;delete p.email;delete p.phone;});out.authChallenges={};out.launcherRequests={};out.contactIdentities=[];out.siteSessions=[];delete out.authReturn;M.downloadFile('CopiMine-v4-data.json',JSON.stringify(out,null,2));};
M.handlers['demo-player']=()=>{M.startSession('demo-001',false);M.navTo('account');};
M.handlers['copy-ip']=()=>M.copyText(M.state.config.serverIp||'copimine.ru');
M.handlers.menu=()=>M.openModal('CopiMine',`<div class="menu-grid">${[['#home','Главная','grid'],['#shop','Магазин','bag'],['#donate','Донат','wallet'],['#map','Карта','map'],['#rules','Правила','file'],['#account','Кабинет','user'],['#settings/profile','Настройки','settings']].map(([r,t,i])=>M.link(t,r,i,'ghost')).join('')}${M.me()?M.btn('Выйти','logout','log-out','ghost'):M.link('Войти','#auth/login','log-in','primary')}</div>`);
})();
(function(){
const V=CMV4,M=CM;
V.attachLocalTiles=()=>{
 const svg=document.getElementById('map-svg');if(!svg||!V.localMaterials?.map?.available)return;
 const image=M.UI.page==='admin'?M.state.mapImage:M.state.mapPublished.image;if(image){document.getElementById('local-map-tiles')?.remove();return;}
 let group=document.getElementById('local-map-tiles');if(!group){group=document.createElementNS('http://www.w3.org/2000/svg','g');group.id='local-map-tiles';svg.insertBefore(group,document.getElementById('zone-layer'));}
 if(!group.childNodes.length)for(const t of V.localMaterials.map.tiles){const im=document.createElementNS('http://www.w3.org/2000/svg','image');for(const [k,v]of Object.entries({x:t.x,y:t.z,width:t.width,height:t.height,href:t.url}))im.setAttribute(k,v);group.append(im);}
};
V.after.push(()=>V.attachLocalTiles());
if(!window.CM_SERVER_MODE&&!window.CM_OFFLINE&&location.protocol!=='file:')fetch('/api/local-materials').then(r=>r.ok?r.json():null).then(a=>{if(a){V.localMaterials=a;V.attachLocalTiles();}}).catch(()=>{});
})();
