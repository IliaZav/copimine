/* Source-derived catalog, v7.3. Closed definitions are fetched only after admin login.
   The standalone HTML embeds them explicitly for offline UI review, NOT production. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,W=CM5,Q=CMReview,E=M.esc,H=M.handlers,F=M.forms;
const C=window.CM73=window.CM73||{};C.sourceCommit='13e8f2a4bb9baf65136d0f374324b5f7fc8e402f';
let privateRows=[],lastRows=null,lastRole=null,requested='',loading=false,query='',scope='all';
// Набор своих текстур: ссылка на архив появляется только когда что-то загружено.
let texturePack=0,textureAt=0;
const state=()=>M.state, publicRows=()=>window.CM6_CATALOG_SOURCE||[];
function syncCatalog(){
 const admin=X.canAdmin();if(lastRows===state().catalog&&lastRole===admin)return;
 const supplied=admin&&privateRows.length?privateRows:publicRows();
 const edits=new Map((state().catalog||[]).filter(p=>p.sourceCommit===C.sourceCommit).map(p=>[p.id,p]));
 state().catalog=supplied.map(p=>{const edit=edits.get(p.id);return edit?{...p,...edit,visibility:p.visibility,grantable:p.grantable,sourceType:p.sourceType,provider:p.provider}:M.clone(p);});
 // IDs removed from the verified source are retained only in order history, not offers.
 state().cart=state().cart.filter(x=>state().catalog.some(p=>p.id===x.id&&p.enabled&&p.visibility==='public'));
 for(const p of state().players||[])for(const limit of p.purchaseLimits||[]){delete limit.period;delete limit.expiresAt;}
 lastRows=state().catalog;lastRole=admin;
}
const prevBefore=V.beforeRender;V.beforeRender=function(){prevBefore?.();syncCatalog();};
async function loadPrivate(){
 if(!Q.ready||!X.canAdmin()||loading)return;
 const uid=M.me()?.id;if(requested===uid)return;loading=true;requested=uid;
 try{const packet=Q.offline?window.CM73_OFFLINE_CATALOG:await Q.request('catalog');
  if(!X.canAdmin()||M.me()?.id!==uid)return;
  if(packet?.items){privateRows=packet.items;lastRows=null;loading=false;syncCatalog();M.render();}
 }catch(err){requested='';C.catalogError=err.message;}finally{loading=false;}
}
V.after.push(()=>{if(!X.canAdmin()){privateRows=[];requested='';lastRole=null;}else void loadPrivate();});
C.catalogReady=()=>privateRows.length>0;
function list(){
 const source=state().catalog;
 const found=source.filter(p=>(scope==='all'||scope==='public'&&p.visibility==='public'||scope==='private'&&p.visibility!=='public')&&CM73Domain.rank(query,[p.name,p.id,p.material,p.category,p.adminSubtitle||'',...(p.aliases||[])])<Infinity);
 // Blank query means all rows here, unlike command search.
 const rows=query.trim()?found:source.filter(p=>scope==='all'||(scope==='public')===(p.visibility==='public'));
 if(!rows.length)return M.empty('Предмет не найден','Проверь название, ID или выбранный раздел.','search');
 return '<div class="cm73-catalog-table">'+V.table(['Предмет','Источник','Ограничения','Доступ',''],rows.map(p=>[
  `<div class="cm73-catalog-item" data-search-target="${E(p.id)}">${W.texture(p.id,38)}<span><strong>${E(p.name)}</strong><small>${E(p.adminSubtitle||p.id)}</small></span></div>`,
  `<span>${E(p.provider||'CopiMineArtifacts')}</span><small>${p.sourceType==='DONATION_SHOP'?'Донат':p.sourceType==='AR_SHOP'?'АР-лавка':p.grantable===false?'Награда':'Закрытый каталог'}</small>`,
  p.sourceType==='AR_SHOP'?`<span>${p.limit>0?p.limit+' на игрока':'Без лимита на игрока'}</span><small>${p.supplyLimit>0?'Тираж: '+p.supplyLimit:'Тираж не ограничен'}</small>`:'<span class="muted">—</span>',
  M.pill(p.visibility!=='public'?'Закрытый':p.enabled?'В магазине':'Скрыт',p.visibility==='public'&&p.enabled?'green':''),
  M.btn(p.visibility==='public'?'Изменить':'Сведения',p.visibility==='public'?'admin-product':'v73-item-info',p.visibility==='public'?'edit':'info','small ghost',`data-id="${E(p.id)}"`)
 ]))+'</div>';
}
async function loadTextures(){
 if(!X.canAdmin()||Date.now()-textureAt<30000)return;
 try{
  const r=await X.request('/admin/catalog/textures');
  texturePack=String(r.changed||0);textureAt=Date.now();
  const slot=document.getElementById('v73-texture-pack');
  if(slot)slot.innerHTML=Number(r.changed)>0
   ?`<a class="btn small ghost" href="/api/v6/admin/catalog/textures/pack.zip" download>${icon('download')}Набор текстур (${Number(r.changed)})</a>`
   :'<span class="hint">Свои текстуры ещё не загружены: открой товар и загрузи 16×16 картинку.</span>';
 }catch(error){textureAt=0;}
}
const oldShop=W.adminExtensions.shop;
W.adminExtensions.shop=sub=>{
 if(!X.canAdmin())return '';
 const requestedScope=M.UI.params.get('scope');if(['all','public','private'].includes(requestedScope))scope=requestedScope;
 if(sub==='materials'||sub==='delivery')return oldShop(sub);
 const all=state().catalog.length,closed=state().catalog.filter(p=>p.visibility!=='public').length;
 return `<nav class="tabs"><a href="#admin/shop" class="active">Каталог CopiMine</a><a href="#admin/shop?sub=materials">Предметы Minecraft</a><a href="#admin/shop?sub=delivery">Выдача заказов</a></nav><section class="panel cm73-catalog"><div class="panel-head"><div><strong>Предметы проекта</strong><p class="hint">Источник · <code>13e8f2a</code></p></div><label class="input-wrap">${icon('search')}<input id="v73-catalog-query" type="search" value="${E(query)}" placeholder="Название, ID или плагин" aria-label="Найти предмет"></label></div><div class="cm73-catalog-filters" role="group" aria-label="Доступ к предметам">${[['all','Все',all],['public','Магазин',all-closed],['private','Закрытые',closed]].map(([id,label,count])=>`<button type="button" data-action="v73-catalog-filter" data-scope="${id}" data-search-target="${id}" aria-pressed="${scope===id}" class="${scope===id?'active':''}">${label}<span>${count}</span></button>`).join('')}</div><div id="v73-catalog-rows">${list()}</div><div class="cm73-source-foot">${icon('lock')}Закрытые позиции не показываются игрокам. Награды нельзя выдать через магазин.<span id="v73-texture-pack">${Number(texturePack)>0?`<a class="btn small ghost" href="/api/v6/admin/catalog/textures/pack.zip" download>${icon('download')}Набор текстур (${Number(texturePack)})</a>`:'<span class="hint">Свои текстуры ещё не загружены: открой товар и загрузи 16×16 картинку.</span>'}</span><span>${loading?'Загружаем закрытые определения…':''}</span></div></section>`;
};
H['v73-catalog-filter']=b=>{scope=b.dataset.scope;query='';M.navTo('admin/shop?scope='+scope);};
H['v73-item-info']=b=>{const p=M.item(b.dataset.id);if(!p||!X.canAdmin())return;
 M.openModal(E(p.name),`<div class="cm73-item-detail-head">${W.texture(p.id,64)}<div><strong>${E(p.adminSubtitle||p.provider)}</strong><code>${E(p.id)}</code></div></div><p>${E(p.description||'Закрытое определение игрового плагина.')}</p><dl class="detail-specs"><div><dt>Материал</dt><dd>${E(p.material)}</dd></div><div><dt>Модель</dt><dd>${p.customModelData||'Обычная текстура'}</dd></div><div><dt>Перезарядка способности</dt><dd>${p.cooldown?p.cooldown+' с':'Нет'}</dd></div><div><dt>Доступ</dt><dd>Только администрация</dd></div></dl>${p.grantable===false?`<div class="cm73-hint">${icon('lock')}Только сервис наград ивента. Ручная выдача и продажа недоступны.</div>`:`<div class="cm73-hint">${icon('info')}Параметры берутся из ${E(p.source)}. Изменения в игре потребуют подключения соответствующего плагина.</div>`}<div class="v71-dialog-actions">${M.btn('Закрыть','close','','primary')}</div>`,'cm73-item-dialog');
};
V.after.push((page,tab)=>{if(page!=='admin'||tab!=='shop')return;
 document.getElementById('v73-catalog-query')?.addEventListener('input',e=>{query=e.target.value;document.getElementById('v73-catalog-rows').innerHTML=list();});
 const focus=M.UI.params.get('focus');if(focus){const item=M.item(focus);if(item){scope=item.visibility==='public'?'public':'private';query='';document.getElementById('v73-catalog-rows').innerHTML=list();}}
 void loadTextures();
});
// Do not offer reward-only/removed definitions through the give picker either.
const giveHandler=H['v6-item-select'];H['v6-item-select']=b=>{const item=M.item(b.dataset.id);if(item?.grantable===false)return M.toast('Этот предмет выдаётся только сервисом наград.',true);return giveHandler?.(b);};
// Public product metadata now uses permanent counts, never rolling purchase periods.
syncCatalog();
})();
