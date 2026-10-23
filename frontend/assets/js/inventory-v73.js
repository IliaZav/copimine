/* Visual inventory editor for local review snapshots. No Minecraft command is sent.
   Codex: apply through the authenticated bridge using an expected snapshot fingerprint;
   reject stale item/NBT/quantity before altering the server inventory. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,W=CM5,D=CM73Domain,E=M.esc,H=M.handlers,F=M.forms;
const state=()=>M.state;let selected={player:'',kind:'inventory',slot:null},pending=null;
const getPlayer=id=>state().players.find(p=>p.id===id);
const permitted=p=>X.canAdmin()&&CM6Domain.canManage(M.me(),p);
const slotName=(i,kind)=>kind==='ender'?'Ячейка '+(i+1):({36:'Ботинки',37:'Поножи',38:'Нагрудник',39:'Шлем',40:'Левая рука'}[i]|| (i<9?'Быстрый доступ '+(i+1):'Ячейка '+(i+1)));
const itemName=item=>item?.name||W.materialName(item?.customId||item?.id)||'Предмет';
const snapshot=(p,kind)=>X.snapshot(p,kind);
function cell(p,kind,list,index){const it=list[index],active=selected.player===p.id&&selected.kind===kind&&selected.slot===index;
 return `<button type="button" class="cm73-slot ${it?'occupied':''} ${active?'selected':''}" data-action="v73-inv-slot" data-player="${E(p.id)}" data-kind="${kind}" data-slot="${index}" aria-label="${E(slotName(index,kind)+(it?': '+itemName(it)+', '+it.qty+' шт.':': пусто'))}" aria-pressed="${active}" title="${E(it?itemName(it):slotName(index,kind))}">${it?W.texture(it.customId||it.id,34):''}${it&&it.qty>1?`<b>${it.qty}</b>`:''}${it?.durability!=null?`<i class="cm73-durability" style="--durability:${Math.max(0,Math.min(1,it.durability))*100}%"></i>`:''}${index<9&&kind==='inventory'?`<small>${index+1}</small>`:''}</button>`;
}
function inspector(p,kind){const snap=snapshot(p,kind),it=D.slots(snap,kind)[selected.player===p.id&&selected.kind===kind?selected.slot:-1];
 if(!it)return `<div class="cm73-inspector-empty">${icon('mouse-pointer')}<strong>Выбери предмет</strong><p>Нажми на ячейку, чтобы увидеть свойства. ПКМ открывает действия.</p><small>На телефоне достаточно обычного нажатия.</small></div>`;
 return `<div class="cm73-inspector-title">${W.texture(it.customId||it.id,52)}<div><strong>${E(itemName(it))}</strong><span>${E(slotName(it.slot,kind))}</span></div></div><dl class="detail-specs"><div><dt>Количество</dt><dd>${it.qty} шт.</dd></div>${it.durability!=null?`<div><dt>Прочность</dt><dd>${Math.round(it.durability*100)}%</dd></div>`:''}</dl><code class="cm73-item-id">${E(it.customId||it.id)}</code>${Object.keys(it.enchantments||{}).length?`<div class="cm73-enchantments">${Object.entries(it.enchantments).map(([key,lvl])=>`<span>${E(({efficiency:'Эффективность',unbreaking:'Прочность',mending:'Починка',sharpness:'Острота',protection:'Защита',power:'Сила'})[key]||key)} ${E(lvl)}</span>`).join('')}</div>`:''}<div class="cm73-inspector-actions">${M.btn('Изъять предметы','v73-inv-remove','minus','cm73-danger',`data-player="${E(p.id)}" data-kind="${kind}" data-slot="${it.slot}" ${permitted(p)?'':'disabled'}`)}<button type="button" class="btn ghost small" data-action="v73-copy-item" data-id="${E(it.customId||it.id)}">${icon('copy')}Копировать ID</button></div>`;
}
function view(p,kind='inventory',compact=false){
 if(!X.canAdmin())return '';kind=kind==='ender'?'ender':'inventory';const snap=snapshot(p,kind),list=D.slots(snap,kind),n=list.filter(Boolean).length;
 return `<section class="cm73-inventory ${compact?'compact':''}" data-player="${E(p.id)}" data-kind="${kind}" data-search-target="inventory"><div class="cm73-inventory-head"><div><h2>Инвентари</h2><small>${E(p.name)} · ${snap?'Тестовый снимок · '+M.date(snap.at,true):'Снимок не получен'}</small></div>${M.btn('JSON','v73-inv-export','download','small ghost',`data-player="${E(p.id)}" data-kind="${kind}"`)}</div><div class="cm73-inventory-tabs" role="group" aria-label="Контейнер игрока">${[['inventory','Инвентарь','bag'],['ender','Эндер-сундук','package']].map(([key,name,ic])=>`<button type="button" data-action="v73-inv-tab" data-kind="${key}" data-player="${E(p.id)}" class="${kind===key?'active':''}" aria-pressed="${kind===key}">${icon(ic)}${name}</button>`).join('')}</div><div class="cm73-inventory-body"><div class="cm73-inventory-board">${kind==='inventory'?`<div class="cm73-gear"><span class="cm73-grid-label">Снаряжение</span><div>${[39,38,37,36,40].map(i=>`<label>${cell(p,kind,list,i)}<span>${slotName(i,kind)}</span></label>`).join('')}</div></div><span class="cm73-grid-label">Основной инвентарь</span><div class="cm73-slot-grid" role="group" aria-label="27 основных ячеек">${Array.from({length:27},(_,i)=>cell(p,kind,list,i+9)).join('')}</div><span class="cm73-grid-label">Быстрый доступ</span><div class="cm73-slot-grid">${Array.from({length:9},(_,i)=>cell(p,kind,list,i)).join('')}</div>`:`<span class="cm73-grid-label">Личный сундук · 27 ячеек</span><div class="cm73-slot-grid" role="group" aria-label="Эндер-сундук">${Array.from({length:27},(_,i)=>cell(p,kind,list,i)).join('')}</div>`}<p class="cm73-inventory-caption"><span>${n} / ${kind==='ender'?27:41} занято</span><span>${icon('mouse-pointer')}ПКМ — действия</span></p>${!snap?'<p class="cm73-hint">Пустая сетка не означает пустой инвентарь: снимок ещё не получен.</p>':''}</div><aside class="cm73-inspector" aria-live="polite">${inspector(p,kind)}</aside></div><div class="cm73-inventory-foot">${icon('shield')}Изменения требуют причины и сохраняются в журнале. Сейчас редактируются только тестовые снимки.</div></section>`;
}
function refreshSelection(){const p=getPlayer(selected.player);if(!p)return;document.querySelectorAll('.cm73-inventory').forEach(host=>{if(host.dataset.player!==p.id||host.dataset.kind!==selected.kind)return;host.querySelectorAll('.cm73-slot').forEach(b=>{const on=Number(b.dataset.slot)===selected.slot;b.classList.toggle('selected',on);b.setAttribute('aria-pressed',String(on));});host.querySelector('.cm73-inspector').innerHTML=inspector(p,selected.kind);});}
function open(p,kind='inventory',slot=null){closeContext();selected={player:p.id,kind,slot};M.openModal('Инвентарь · '+E(p.name),view(p,kind,true),'cm73-inventory-dialog');}
function select(b){selected={player:b.dataset.player,kind:b.dataset.kind,slot:Number(b.dataset.slot)};refreshSelection();}
H['v73-inv-slot']=b=>{closeContext();select(b);};
H['v73-inv-tab']=b=>{closeContext();const p=getPlayer(b.dataset.player),host=b.closest('.cm73-inventory');selected={player:p.id,kind:b.dataset.kind,slot:null};host.outerHTML=view(p,b.dataset.kind,host.classList.contains('compact'));};
H['v73-inv-export']=b=>{const p=getPlayer(b.dataset.player),snap=snapshot(p,b.dataset.kind);if(snap)M.downloadFile('inventory-'+p.name+'-'+b.dataset.kind+'.json',JSON.stringify(snap,null,2));else M.toast('Снимок ещё не получен.',true);};
H['v73-copy-item']=b=>M.copyText(b.dataset.id);
H['v73-inv-remove']=b=>{
 closeContext();const p=getPlayer(b.dataset.player),kind=b.dataset.kind||'inventory',slot=Number(b.dataset.slot),snap=snapshot(p,kind),it=D.slots(snap,kind)[slot];
 if(!p||!it||!permitted(p))return M.toast('Нельзя изменить эту ячейку.',true);
 pending={player:p.id,kind,slot,expected:D.fingerprint(snap,kind)};
 M.openModal('Изъять предметы',`<div class="cm73-item-detail-head">${W.texture(it.customId||it.id,56)}<div><strong>${E(itemName(it))}</strong><span>${E(p.name)} · ${E(slotName(slot,kind))}</span></div></div><form data-form="v73-inv-remove">${V.field('Сколько изъять','qty',it.qty,'number',`required min="1" max="${it.qty}" step="1"`)}<p class="hint">В стопке ${it.qty} шт. Остальные ячейки не изменятся.</p>${V.area('Причина — только для администрации','reason','',2,'required minlength="3" maxlength="240"')}<div class="form-error" role="alert"></div><div class="v71-dialog-actions">${M.btn('К инвентарю','v73-inv-back','arrow-left','ghost')}<button type="submit" class="btn cm73-danger">${icon('minus')}Изъять из снимка</button></div></form>`,'cm73-remove-dialog');
};
H['v73-inv-back']=()=>{if(pending)open(getPlayer(pending.player),pending.kind,pending.slot);};
F['v73-inv-remove']=async(f,data)=>{
 if(!pending)throw Error('Сначала выберите предмет.');const p=getPlayer(pending.player);if(!permitted(p))throw Error('Недостаточно прав.');
 const snap=snapshot(p,pending.kind),reason=String(data.get('reason')||'').trim();
 const result=await X.request('/admin/players/'+encodeURIComponent(p.id)+'/inventory/remove',{method:'POST',body:{kind:pending.kind,slot:pending.slot,quantity:Number(data.get('qty')),expectedVersion:snap?.version||'',reason,key:f.dataset.key||(f.dataset.key=X.uid('inventory'))}});
 const back={...pending};pending=null;await window.CMProduction.refreshInventory(p.id);M.closeModal();M.render();open(p,back.kind,back.slot);M.toast('Minecraft подтвердил удаление предмета.');
};
function closeContext(){document.getElementById('cm73-slot-menu')?.remove();}
function context(b,x,y){
 closeContext();select(b);const p=getPlayer(b.dataset.player),it=D.slots(snapshot(p,b.dataset.kind),b.dataset.kind)[Number(b.dataset.slot)];if(!it)return;
 const menu=document.createElement('div');menu.id='cm73-slot-menu';menu.className='cm73-slot-menu';menu.setAttribute('role','menu');
 menu.innerHTML=`<strong>${E(itemName(it))}</strong><small>${it.qty} шт. · ${E(slotName(it.slot,b.dataset.kind))}</small><button type="button" role="menuitem" data-action="v73-inv-remove" data-player="${E(p.id)}" data-kind="${b.dataset.kind}" data-slot="${it.slot}" ${permitted(p)?'':'disabled'}>${icon('minus')}Изъять предметы…</button><button type="button" role="menuitem" data-action="v73-copy-item" data-id="${E(it.customId||it.id)}">${icon('copy')}Копировать ID</button>`;
 // Native dialogs are in the top layer; keep this menu in that layer too.
 const dialog=b.closest('dialog');(dialog||document.body).append(menu);menu.style.left=Math.max(8,Math.min(x,innerWidth-252))+'px';menu.style.top=Math.max(8,Math.min(y,innerHeight-175))+'px';menu.querySelector('button:not(:disabled)')?.focus({preventScroll:true});
}
document.addEventListener('contextmenu',e=>{const b=e.target.closest('.cm73-slot');if(!b)return;e.preventDefault();context(b,e.clientX,e.clientY);});
document.addEventListener('pointerdown',e=>{if(!e.target.closest('#cm73-slot-menu')&&!e.target.closest('.cm73-slot'))closeContext();});
document.addEventListener('keydown',e=>{if(e.key==='Escape')closeContext();const b=e.target.closest?.('.cm73-slot');if(!b)return;
 if(e.key==='ContextMenu'||e.shiftKey&&e.key==='F10'){e.preventDefault();const r=b.getBoundingClientRect();context(b,r.left,r.bottom);}
 else if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].includes(e.key)){e.preventDefault();const list=[...b.closest('.cm73-inventory-board').querySelectorAll('.cm73-slot')],i=list.indexOf(b),step={ArrowLeft:-1,ArrowRight:1,ArrowUp:-9,ArrowDown:9}[e.key];list[Math.max(0,Math.min(list.length-1,i+step))].focus();}
});
const action=H['v6-player-action'];H['v6-player-action']=(b,e)=>{if(['inventory.remove','ender.remove'].includes(b.dataset.id)){const p=getPlayer(b.dataset.player)||X.player();if(p)open(p,b.dataset.id==='ender.remove'?'ender':'inventory');return;}return action?.(b,e);};
H['v6-slot-detail']=b=>{const p=getPlayer(b.dataset.player)||X.player();if(X.canAdmin()&&p)open(p,b.dataset.kind||'inventory',Number(b.dataset.slot));};
H['v6-remove-slot']=H['v6-slot-detail'];
W.inventoryView=p=>view(p,'inventory');
window.CM73Inventory={view,open};
})();
