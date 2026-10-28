/* Live website editor. Drafts and publication are always server-owned. */
(function(){
'use strict';
const M=CM,V=CMV4,X=CM6,Q=CMReview,E=M.esc,H=M.handlers,F=M.forms,s=()=>M.state;
const previous=window.CMCMS.render;
const cache={owner:'',data:null,versions:null,settings:null,settingsVersion:'',loading:false,error:'',loadedAt:0};
const textField=(label,name,value,attrs='')=>V.field(label,name,value,'text',attrs);
const reasonField=()=>V.field('Причина изменения','reason','','text','required minlength="3" maxlength="500" placeholder="Например: исправил инструкцию"');
const note=(copy)=>`<div class="cm-cms-note">${icon('info')}<p>${copy}</p></div>`;
const failPanel=()=>`<section class="panel cm-cms-state"><h2>Не удалось загрузить содержимое</h2><p>${E(cache.error||'Проверь подключение и права на контент.')}</p>${M.btn('Повторить','cm-cms-retry','refresh','small')}</section>`;
function reload(){cache.loadedAt=0;cache.error='';M.render();}
async function load(force=false){
 const owner=Q.user?.id||'';if(owner!==cache.owner){cache.owner=owner;cache.data=null;cache.versions=null;cache.settings=null;cache.settingsVersion='';cache.loadedAt=0;}
 if(cache.loading||(!force&&cache.loadedAt&&Date.now()-cache.loadedAt<30000))return;
 cache.loading=true;cache.error='';
 try{const result=await X.request('/admin/content');if(owner!==Q.user?.id)return;cache.data=result.data;cache.versions=result.versions;cache.loadedAt=Date.now();}
 catch(error){cache.error=error.message;}
 finally{cache.loading=false;M.render();}
}
async function loadSettings(){
 const owner=Q.user?.id||'';if(owner!==cache.owner){cache.owner=owner;cache.data=null;cache.versions=null;cache.settings=null;cache.settingsVersion='';cache.loadedAt=0;}
 if(cache.settings||cache.loading)return;
 cache.loading=true;cache.error='';
 try{const result=await X.request('/admin/site-settings');if(owner!==Q.user?.id)return;cache.settings=result.data;cache.settingsVersion=result.version;}
 catch(error){cache.error=error.message;}
 finally{cache.loading=false;M.render();}
}
function publicPage(p){return typeof p.published==='object'?p.published:(p.publishedContent||null);}
function isPublished(p){return p.published===true||typeof p.published==='object'||!!p.publishedContent;}
function draftPage(p){return p.draft||publicPage(p)||{title:'',body:'',description:'',nav:false};}
function pageTabs(active=M.UI.params.get('sub')||'pages'){const settings=Q.user?.permissions?.includes('settings.manage');return `<div class="cm-cms-tabs" role="tablist" aria-label="Материалы сайта"><a href="#admin/content?sub=pages" class="${active==='pages'?'active':''}">Страницы</a><a href="#admin/content?sub=faq" class="${active==='faq'?'active':''}">Вопросы</a><a href="#admin/content?sub=gallery" class="${active==='gallery'?'active':''}">Галерея</a>${settings?`<a href="#admin/content?sub=settings" class="${active==='settings'?'active':''}">Главная и ссылки</a>`:''}</div>`;}
function pagesView(){
 const list=cache.data.pages||[],id=M.UI.params.get('id'),selected=list.find(p=>p.id===id)||list[0];
 if(!selected)return pageTabs()+`<section class="panel cm-cms-state"><h2>Страниц пока нет</h2><p>Создай первую страницу или проверь настройки содержимого.</p>${M.btn('Создать страницу','cm-cms-new-page','plus','primary')}</section>`;
 const draft=draftPage(selected),live=publicPage(selected);
 return pageTabs()+`<div class="cm-cms-layout"><aside class="panel cm-cms-list"><div class="cm-cms-list-head"><div><h2>Страницы</h2><p>${list.length} ${list.length===1?'материал':list.length<5?'материала':'материалов'}</p></div>${M.btn('Новая','cm-cms-new-page','plus','small')}</div><nav aria-label="Выбрать страницу">${list.map(p=>`<a href="#admin/content?sub=pages&id=${encodeURIComponent(p.id)}" class="cm-cms-page-link ${p.id===selected.id?'active':''}"><span>${icon('file')}<span><strong>${E(draftPage(p).title||p.slug)}</strong><small>${E(p.slug)}</small></span></span><i class="${isPublished(p)?'is-live':''}">${isPublished(p)?'На сайте':'Черновик'}</i></a>`).join('')}</nav></aside><section class="cm-cms-editor panel"><div class="cm-cms-editor-head"><div><span class="cm-cms-overline">/${E(selected.slug)}</span><h2>${E(draft.title||'Новая страница')}</h2><p>${live?'Опубликованный текст останется на сайте, пока не выйдет новая версия.':'Эту страницу увидят посетители после публикации.'}</p></div><span class="cm-cms-status ${live?'is-live':''}">${live?'Опубликована':'Черновик'}</span></div><form id="cm-cms-page-form"><input type="hidden" name="id" value="${E(selected.id)}"><div class="cm-cms-fields">${textField('Заголовок','title',draft.title,'required maxlength="100"')}${textField('Короткое описание','description',draft.description||'','maxlength="220"')}</div><label class="field cm-cms-body-field">Текст страницы<textarea name="body" rows="14" required maxlength="16000" aria-describedby="cm-cms-markdown-help">${E(draft.body)}</textarea></label><p id="cm-cms-markdown-help" class="cm-cms-help">Поддерживаются заголовки с #, списки с - и выделение **жирным**. HTML-код не принимается.</p><label class="cm-cms-checkbox"><input type="checkbox" name="nav" ${draft.nav?'checked':''}><span>Показывать ссылку в верхнем меню</span></label>${reasonField()}<div class="form-error cm-cms-error" role="alert"></div><div class="cm-cms-actions">${M.btn('Сохранить черновик','cm-cms-save-page','save','primary')}<span class="cm-cms-actions-note">${live?'На сайте останется текущая версия.':'Сайт пока не изменится.'}</span>${live?M.btn('Снять с сайта','cm-cms-unpublish-page','eye','ghost'):''}${M.btn('Опубликовать','cm-cms-publish-page','send',live?'ghost':'primary')}</div></form>${live?`<details class="cm-cms-preview"><summary>Текст на сайте</summary><article class="prose">${CMDomain.markdown(live.body||'')}</article></details>`:''}</section></div>`;
}
function faqView(){
 const list=cache.data.faq||[];
 return pageTabs()+`<section class="panel cm-cms-collection"><div class="cm-cms-collection-head"><div><h2>Частые вопросы</h2><p>На главной показываются только опубликованные ответы.</p></div>${M.btn('Добавить вопрос','cm-cms-edit-faq','plus','primary')}</div>${list.length?`<div class="cm-cms-faq-list">${list.map(q=>`<article><div><span class="cm-cms-status ${q.published?'is-live':''}">${q.published?'На сайте':'Скрыт'}</span><h3>${E(q.question)}</h3><p>${E(q.answer)}</p></div>${M.btn('Изменить','cm-cms-edit-faq','edit','small','data-id="'+E(q.id)+'"')}</article>`).join('')}</div>`:M.empty('Вопросов пока нет','Добавь ответ, который часто нужен игрокам.','message')}</section>`;
}
function galleryImageSrc(item){
 if(String(item.src||'').startsWith('/api/v6/gallery/'))return item.published?item.src:'/api/v6/admin/content/gallery/image/'+encodeURIComponent(item.id);
 return M.asset(item.src);
}
function galleryView(){
 const list=cache.data.gallery||[];
 return pageTabs()+`<section class="panel cm-cms-collection"><div class="cm-cms-collection-head"><div><h2>Галерея</h2><p>Подписи и публикация снимков на главной странице.</p></div>${M.btn('Загрузить изображение','cm-cms-upload-gallery','upload','primary')}</div>${note('Загруженные снимки хранятся закрыто, пока ты не опубликуешь их. Поддерживаются PNG, JPEG, WebP и GIF до 4 МБ.')}${list.length?`<div class="cm-cms-gallery">${list.map(g=>`<article><img src="${E(galleryImageSrc(g))}" alt="${E(g.alt)}" loading="lazy"><div><span class="cm-cms-status ${g.published?'is-live':''}">${g.published?'На сайте':'Скрыто'}</span><h3>${E(g.title)}</h3><p>${E(g.caption)}</p><span class="cm-cms-file">${E(g.src)}</span><div class="cm-cms-card-actions">${M.btn('Подпись','cm-cms-edit-gallery','edit','small','data-id="'+E(g.id)+'"')}${M.btn(g.published?'Скрыть':'Опубликовать','cm-cms-toggle-gallery','eye',g.published?'ghost small':'primary small','data-id="'+E(g.id)+'"')}</div></div></article>`).join('')}</div>`:M.empty('Изображений пока нет','Загрузи первый снимок в галерею.','image')}</section>`;
}
function settingsView(active='settings'){
 if(!Q.user?.permissions?.includes('settings.manage'))return pageTabs(active)+`<section class="panel cm-cms-state"><h2>Настройки сайта недоступны</h2><p>Изменять заголовок и ссылки может только владелец сайта.</p></section>`;
 if(!cache.settings){if(!cache.loading&&!cache.error)loadSettings();return cache.error?failPanel():pageTabs(active)+`<section class="panel cm-cms-state"><span class="cm-cms-spinner" aria-hidden="true"></span><p>Загружаем текст главной страницы…</p></section>`;}
 const c=cache.settings;
 return pageTabs(active)+`<div class="cm-cms-settings-grid"><section class="panel"><div class="cm-cms-collection-head"><div><h2>Первый экран</h2><p>Заголовок, короткое описание и данные для входа в игру.</p></div></div><form id="cm-cms-settings-form">${textField('Название сайта','siteTitle',c.siteTitle||'CopiMine','required maxlength="50"')}${V.area('Заголовок','headline',c.headline||'',3,'required maxlength="80"')}${V.area('Описание','description',c.description||'',3,'required maxlength="220"')}<div class="cm-cms-fields">${textField('Адрес сервера','serverIp',c.serverIp||'copimine.ru','required maxlength="253"')}${textField('Версия Minecraft','gameVersion',c.gameVersion||'26.3','required maxlength="24"')}</div>${V.field('Приглашение Discord','discordInvite',c.discordInvite||'','url','placeholder="https://discord.gg/…" maxlength="180"')}${reasonField()}<div class="form-error cm-cms-error" role="alert"></div><div class="cm-cms-actions">${M.btn('Сохранить','cm-cms-save-settings','save','primary')}<span class="cm-cms-actions-note">Параметры сохраняются на сервере.</span></div></form></section><aside class="panel cm-cms-settings-note"><h2>Как это увидят игроки</h2><div class="cm-cms-copy-preview"><span class="cm-cms-overline">Minecraft · ${E(c.siteTitle||'CopiMine')}</span><strong>${E(c.headline||'')}</strong><p>${E(c.description||'')}</p><span>${E(c.serverIp||'')}</span><small>Minecraft ${E(c.gameVersion||'')}</small></div><p>Проверь адрес сервера перед сохранением. Платёжные данные и секреты здесь не нужны.</p></aside></div>`;
}
function render(sub='pages'){
 if(!X.live)return previous(sub);
 if(!Q.user?.permissions?.includes('content.manage'))return cache.error?failPanel():`<section class="panel cm-cms-state"><h2>Нет доступа к содержимому</h2><p>Обратись к владельцу сайта, чтобы проверить свою роль.</p></section>`;
 if(cache.owner!==Q.user.id||!cache.data){if(!cache.loading&&!cache.error)load();return cache.error?failPanel():`<section class="panel cm-cms-state"><span class="cm-cms-spinner" aria-hidden="true"></span><p>Загружаем сохранённые материалы…</p></section>`;}
 return sub==='faq'?faqView():sub==='gallery'?galleryView():sub==='settings'?settingsView():pagesView();
}
window.CMCMS.render=render;
window.CMCMS.siteSettingsView=()=>settingsView('settings').replace(pageTabs('settings'),'');
const previousAdminContent=CM5.adminExtensions.content;
CM5.adminExtensions.content=sub=>X.live?render(sub||'pages'):previousAdminContent(sub);
H['cm-cms-retry']=()=>{cache.data=null;cache.settings=null;reload();};
function pageItem(form,old,action){
 const d=new FormData(form),draft={title:String(d.get('title')||'').trim(),body:String(d.get('body')||'').trim(),description:String(d.get('description')||'').trim(),nav:!!d.get('nav')};
 if(!draft.title||!draft.body)throw Error('Заполни заголовок и текст страницы.');
 return{id:old.id,slug:old.slug,draft,published:isPublished(old),...(action==='publish'?{publish:true}:action==='unpublish'?{unpublish:true}:{})};
}
async function persist(section,item,reason){
 if(String(reason||'').trim().length<3)throw Error('Укажи причину изменения.');
 const r=await X.request('/admin/content/'+section,{method:'PATCH',body:{item,reason:String(reason).trim(),expectedVersion:cache.versions[section]}});
 const rows=cache.data[section],i=rows.findIndex(x=>x.id===item.id);if(i<0)rows.push(r.item);else rows[i]=r.item;
 cache.versions[section]=r.version;cache.loadedAt=Date.now();syncPublic(section,r.item);return r.item;
}
function syncPublic(section,item){
 const rows=s()[section]||(s()[section]=[]),index=rows.findIndex(row=>row.id===item.id);let publicRow=null;
 if(section==='pages'){
  const content=item.published===true?item.publishedContent:typeof item.published==='object'?item.published:null;
  if(content)publicRow={id:item.id,slug:item.slug,published:content,publishedRevision:item.publishedRevision,publishedAt:item.publishedAt};
 }else if(item.published===true){
  publicRow=section==='gallery'?{id:item.id,src:item.src,title:item.title,caption:item.caption,alt:item.alt,position:item.position,published:true}:{id:item.id,question:item.question,answer:item.answer,published:true};
 }
 if(index>=0)rows.splice(index,1);
 if(publicRow)rows.push(publicRow);
}
async function savePage(action){
 const form=document.getElementById('cm-cms-page-form'),old=cache.data?.pages?.find(p=>p.id===new FormData(form||document.createElement('form')).get('id'));
 if(!form||!old)throw Error('Страница изменилась. Обнови редактор.');
 const reason=new FormData(form).get('reason');await persist('pages',pageItem(form,old,action),reason);
 if(action==='unpublish')M.toast('Страница снята с сайта.');else if(action==='publish')M.toast('Новая версия опубликована.');else M.toast('Черновик сохранён. На сайте осталась опубликованная версия.');
 M.render();
}
H['cm-cms-save-page']=()=>savePage('save');H['cm-cms-publish-page']=()=>savePage('publish');H['cm-cms-unpublish-page']=()=>savePage('unpublish');
H['cm-cms-new-page']=()=>M.openModal('Новая страница',`<form data-form="cm-cms-create-page">${textField('Заголовок','title','','required maxlength="100"')}${textField('Адрес страницы','slug','','required pattern="[a-z0-9][a-z0-9-]{1,39}" placeholder="about-us"')}<p class="cm-cms-help">Латинские буквы, цифры и дефис.</p><label class="field">Текст страницы<textarea name="body" rows="8" required maxlength="16000"></textarea></label>${reasonField()}<div class="form-error" role="alert"></div>${V.actions('Создать черновик')}</form>`);
F['cm-cms-create-page']=async(f,d)=>{
 const slug=String(d.get('slug')||'').trim(),title=String(d.get('title')||'').trim(),body=String(d.get('body')||'').trim();if(!/^[a-z0-9][a-z0-9-]{1,39}$/.test(slug))throw Error('Проверь адрес страницы: только латинские буквы, цифры и дефис.');
 const item={id:'page-'+slug,slug,published:false,draft:{title,body,description:'',nav:false}},r=await X.request('/admin/content/pages',{method:'PATCH',body:{item,reason:d.get('reason'),expectedVersion:cache.versions.pages}});
 cache.data.pages.push(r.item);cache.versions.pages=r.version;cache.loadedAt=Date.now();M.closeModal();M.navTo('admin/content?sub=pages&id='+encodeURIComponent(r.item.id));M.toast('Черновик создан.');
};
H['cm-cms-edit-faq']=b=>{
 const q=cache.data.faq.find(x=>x.id===b.dataset.id)||{id:'',question:'',answer:'',published:false};
 M.openModal(q.id?'Изменить вопрос':'Добавить вопрос',`<form data-form="cm-cms-save-faq"><input type="hidden" name="id" value="${E(q.id)}">${textField('Вопрос','question',q.question,'required maxlength="150"')}${V.area('Ответ','answer',q.answer,5,'required maxlength="1800"')}<label class="cm-cms-checkbox"><input type="checkbox" name="published" ${q.published?'checked':''}><span>Показывать на главной</span></label>${reasonField()}<div class="form-error" role="alert"></div>${V.actions('Сохранить')}</form>`);
};
F['cm-cms-save-faq']=async(f,d)=>{
 const id=String(d.get('id')||'')||'faq-'+(crypto.randomUUID?crypto.randomUUID():Date.now()),item={id,question:String(d.get('question')||'').trim(),answer:String(d.get('answer')||'').trim(),published:!!d.get('published')};
 await persist('faq',item,d.get('reason'));M.closeModal();M.render();M.toast('Ответ сохранён.');
};
H['cm-cms-edit-gallery']=b=>{
 const g=cache.data.gallery.find(x=>x.id===b.dataset.id);if(!g)return;
 M.openModal('Подпись изображения',`<form data-form="cm-cms-save-gallery"><input type="hidden" name="id" value="${E(g.id)}">${textField('Название','title',g.title,'required maxlength="80"')}${textField('Подпись','caption',g.caption||'','maxlength="180"')}${textField('Описание для экранного диктора','alt',g.alt,'required maxlength="200"')}<label class="field">Порядок показа<input name="position" type="number" min="0" max="1000" step="1" value="${Number(g.position)||0}" required></label><p class="cm-cms-file">${E(g.src)}</p>${reasonField()}<div class="form-error" role="alert"></div>${V.actions('Сохранить')}</form>`);
};
F['cm-cms-save-gallery']=async(f,d)=>{
 const old=cache.data.gallery.find(x=>x.id===d.get('id'));if(!old)throw Error('Изображение уже удалено.');
 const item={...old,title:String(d.get('title')||'').trim(),caption:String(d.get('caption')||'').trim(),alt:String(d.get('alt')||'').trim(),position:Number(d.get('position'))};
 await persist('gallery',item,d.get('reason'));M.closeModal();M.render();M.toast('Описание изображения сохранено.');
};
 H['cm-cms-upload-gallery']=()=>M.openModal('Загрузить изображение',`<form data-form="cm-cms-upload-gallery"><label class="field">Файл изображения<input type="file" name="image" data-cm-gallery-upload accept="image/png,image/jpeg,image/webp,image/gif" required></label><p class="cm-cms-help">PNG, JPEG, WebP или GIF. Не больше 4 МБ и 8 000 × 8 000 пикселей. Файл сохраняется в WebP, у анимированного GIF берётся первый кадр.</p>${textField('Название','title','','required maxlength="80"')}${textField('Подпись','caption','','maxlength="180"')}${textField('Описание для экранного диктора','alt','','required maxlength="200"')}<label class="field">Порядок показа<input name="position" type="number" min="0" max="1000" step="1" value="0" required></label><label class="cm-cms-checkbox"><input type="checkbox" name="published"><span>Опубликовать сразу</span></label>${reasonField()}<div class="form-error" role="alert"></div>${V.actions('Загрузить')}</form>`);
function readGalleryFile(file){
 if(!file)throw Error('Выбери файл изображения.');
 if(file.size>4*1024*1024)throw Error('Размер файла не должен превышать 4 МБ.');
 if(!['image/png','image/jpeg','image/webp','image/gif'].includes(file.type))throw Error('Поддерживаются PNG, JPEG, WebP и GIF.');
 return new Promise((resolve,reject)=>{const reader=new FileReader();reader.onerror=()=>reject(Error('Не удалось прочитать файл.'));reader.onload=()=>{const value=String(reader.result||'');const comma=value.indexOf(',');if(comma<0)return reject(Error('Не удалось прочитать изображение.'));resolve(value.slice(comma+1));};reader.readAsDataURL(file);});
}
F['cm-cms-upload-gallery']=async(f,d)=>{
 const file=f.querySelector('[name="image"]')?.files?.[0],image=await readGalleryFile(file),reason=String(d.get('reason')||'').trim();
 if(reason.length<3)throw Error('Укажи причину изменения.');
 const r=await X.request('/admin/content/gallery/image',{method:'POST',body:{image,title:String(d.get('title')||'').trim(),caption:String(d.get('caption')||'').trim(),alt:String(d.get('alt')||'').trim(),position:Number(d.get('position')),published:!!d.get('published'),reason,expectedVersion:cache.versions.gallery}});
 cache.data.gallery.push(r.item);cache.data.gallery.sort((a,b)=>(a.position||0)-(b.position||0));cache.versions.gallery=r.version;cache.loadedAt=Date.now();syncPublic('gallery',r.item);M.closeModal();M.render();M.toast(r.item.published?'Изображение загружено и опубликовано.':'Изображение загружено. Теперь его можно опубликовать.');
};
H['cm-cms-toggle-gallery']=async b=>{
 const old=cache.data.gallery.find(x=>x.id===b.dataset.id);if(!old)return;
 M.openModal(old.published?'Скрыть изображение?':'Опубликовать изображение?',`<p>${E(old.title)} будет ${old.published?'скрыто':'показано'} на главной странице.</p><form data-form="cm-cms-toggle-gallery"><input type="hidden" name="id" value="${E(old.id)}"><input type="hidden" name="published" value="${old.published?'0':'1'}">${reasonField()}<div class="form-error" role="alert"></div>${V.actions(old.published?'Скрыть':'Опубликовать')}</form>`);
};
F['cm-cms-toggle-gallery']=async(f,d)=>{const old=cache.data.gallery.find(x=>x.id===d.get('id'));if(!old)throw Error('Изображение уже удалено.');await persist('gallery',{...old,published:d.get('published')==='1'},d.get('reason'));M.closeModal();M.render();M.toast('Видимость изображения обновлена.');};
H['cm-cms-save-settings']=async()=>{
 const form=document.getElementById('cm-cms-settings-form');if(!form)throw Error('Форма уже закрыта.');const d=new FormData(form),values={};for(const k of ['siteTitle','headline','description','serverIp','gameVersion','discordInvite'])values[k]=String(d.get(k)||'').trim();if(values.headline.length<1||values.description.length<1)throw Error('Заполни заголовок и описание.');if(!cache.settingsVersion)throw Error('Настройки не загружены. Обнови раздел.');
 const r=await X.request('/admin/site-settings',{method:'PATCH',body:{values,reason:d.get('reason'),expectedVersion:cache.settingsVersion}});cache.settings=r.data;cache.settingsVersion=r.version;Object.assign(s().config,r.data);M.render();M.toast('Тексты сайта сохранены.');
};
V.after.push((page,tab)=>{
 if(page!=='admin'||tab!=='content'||!X.live)return;
 const sub=M.UI.params.get('sub')||'pages';
 if(sub==='settings'&&!cache.settings&&!cache.loading&&!cache.error)loadSettings();
 else if(sub!=='settings'&&!cache.data&&!cache.loading&&!cache.error)load();
});
function renderPublicContentPage(slug){
 const row=(s().pages||[]).find(item=>item?.slug===slug&&item.published&&typeof item.published==='object');
 if(!row)return `<div class="page-head"><div><h1>Страница не найдена</h1><p>Этот материал не опубликован или был удалён.</p></div></div>${M.link('На главную','#home','arrow-left','ghost small')}`;
 const published=row.published;
 return `<div class="page-head"><div><h1>${E(published.title||'Информация')}</h1>${published.description?`<p>${E(published.description)}</p>`:''}</div></div><article class="panel cm-public-page help-copy">${CMDomain.markdown(published.body||'')}</article>`;
}
M.renderers.rules=()=>renderPublicContentPage('rules');
M.renderers.privacy=()=>`<div class="page-head"><div><span class="kicker">Документы</span><h1>Политика конфиденциальности</h1><p>Как CopiMine обращается с данными посетителей и игроков. Версия от 27 октября 2026 года.</p></div></div><article class="panel cm-public-page cm-legal">
<p>Эта политика описывает, какие данные получает сайт copimine.ru, зачем они нужны, сколько хранятся и как ты можешь управлять своим выбором. Мы собираем минимум данных: всё, что можно посчитать обезличенно, считается обезличенно.</p>
<h2>1. Кто отвечает за обработку</h2>
<p>Сайт copimine.ru администрирует команда проекта CopiMine. Вопросы о данных, доступе, исправлении и удалении принимаются через раздел «Помощь» в личном кабинете и через Discord-сервер проекта. Почтовый домен пока не подключён, поэтому переписка ведётся в этих каналах.</p>
<h2>2. Какие данные мы обрабатываем</h2>
<ul>
<li><strong>Аккаунт:</strong> логин сайта, отображаемое имя, почта, хэш пароля, роль, привязка Minecraft, даты создания и последнего входа, настройки интерфейса.</li>
<li><strong>Безопасность:</strong> сессии входа, время создания и срок действия, IP-адрес и user-agent на момент входа, журнал действий администрации.</li>
<li><strong>Покупки и баланс:</strong> заказы, выданные предметы, балансы AR и CM, история переводов, обращения в поддержку. Платёжный сервис пока не подключён, поэтому данные банковских карт сайт не получает и не хранит.</li>
<li><strong>Статистика сайта:</strong> просмотры публичных страниц, действия в магазине, тип устройства, браузер, операционная система, язык, размер экрана, домен предыдущей страницы и utm-метки. Подробный состав указан ниже.</li>
<li><strong>Согласие на cookie:</strong> какие категории разрешены, время выбора, версия политики, источник выбора и строка браузера.</li>
</ul>
<p>Мы не собираем в статистике IP-адрес, точное местоположение, содержимое полей форм, поисковые запросы, имена игроков и данные других сайтов.</p>
<h2>3. Cookie и локальное хранилище</h2>
<div class="cm-legal-table"><table>
<thead><tr><th>Название</th><th>Категория</th><th>Зачем</th><th>Срок</th></tr></thead>
<tbody>
<tr><td><code>cm_site_session</code></td><td>Обязательные</td><td>Вход в аккаунт и защита запросов</td><td>12 часов или 30 дней, если включено «Оставаться в аккаунте»</td></tr>
<tr><td><code>cm_site_anon</code></td><td>Обязательные</td><td>Защита форм до входа</td><td>24 часа</td></tr>
<tr><td><code>cm_consent</code></td><td>Обязательные</td><td>Твой выбор категорий cookie</td><td>1 год</td></tr>
<tr><td><code>cm_consent_id</code></td><td>Обязательные</td><td>Номер записи о согласии</td><td>1 год</td></tr>
<tr><td><code>cm_visitor</code></td><td>Аналитика</td><td>Случайный псевдоним посетителя для подсчёта уникальных и вернувшихся</td><td>1 год</td></tr>
<tr><td><code>localStorage: copimine.consent.v2</code></td><td>Обязательные</td><td>Копия выбора cookie в браузере</td><td>1 год</td></tr>
<tr><td><code>localStorage: copimine.*</code></td><td>Настройки</td><td>Тема, анимации, корзина, последние разделы</td><td>До очистки данных сайта</td></tr>
</tbody>
</table></div>
<p>Корзина хранится в браузере и не передаётся на сервер до оформления заказа. Отключить аналитику и настройки можно в любой момент через кнопку «Настройки cookies» в подвале сайта. После отказа удаляется псевдоним <code>cm_visitor</code> и очередь неотправленных событий.</p>
<h2>4. Что именно попадает в статистику</h2>
<div class="cm-legal-table"><table>
<thead><tr><th>Поле</th><th>Пример</th><th>Зачем</th></tr></thead>
<tbody>
<tr><td>Страница</td><td>home, shop, cart, donate, map, rules, support</td><td>Понимать, какие разделы нужны людям</td></tr>
<tr><td>Событие</td><td>page_view, product_view, add_to_cart</td><td>Улучшать каталог и оформление</td></tr>
<tr><td>Предмет и количество</td><td>arena_pass, 1</td><td>Понимать спрос без привязки к аккаунту</td></tr>
<tr><td>Время</td><td>дата и время в UTC</td><td>Строить отчёты по дням</td></tr>
<tr><td>Псевдоним посетителя</td><td>случайный идентификатор в cookie</td><td>Считать уникальных и вернувшихся</td></tr>
<tr><td>Сеанс</td><td>случайный идентификатор вкладки</td><td>Считать глубину визита</td></tr>
<tr><td>Устройство</td><td>компьютер, телефон, планшет</td><td>Проверять адаптивность</td></tr>
<tr><td>Браузер и система</td><td>Chrome, Firefox; Windows, Android</td><td>Исправлять совместимость</td></tr>
<tr><td>Язык и экран</td><td>ru-RU; 1920x1080</td><td>Проверять вёрстку и тексты</td></tr>
<tr><td>Источник</td><td>домен предыдущей страницы, utm_source, utm_medium, utm_campaign</td><td>Оценивать эффективность ссылок</td></tr>
</tbody>
</table></div>
<p>Идентификатор посетителя на сервере хранится только как необратимый хэш. По нему нельзя восстановить имя, почту или IP-адрес. Статистика принимается только при явном согласии и не связывает просмотры с аккаунтом.</p>
<h2>5. Зачем мы это делаем</h2>
<ul>
<li>Вход, защита аккаунта, покупки и поддержка — исполнение соглашения с пользователем.</li>
<li>Статистика посещений и настройки интерфейса — твоё согласие, которое можно отозвать.</li>
<li>Журналы безопасности и аудит действий администрации — защита сайта и законные требования.</li>
<li>Ответы на обращения и исправление ошибок — законный интерес в поддержке проекта.</li>
</ul>
<h2>6. Сколько храним</h2>
<ul>
<li>События статистики — до 400 дней, затем удаляются автоматически.</li>
<li>Записи о согласии — 3 года, чтобы подтвердить выбор или его отзыв.</li>
<li>Сессии входа — 12 часов, с «Оставаться в аккаунте» — 30 дней; отозванные сессии не действуют.</li>
<li>Аккаунт, покупки и обращения — пока существует аккаунт и пока это нужно для расчётов и защиты.</li>
<li>Локальные настройки и cookie — до истечения срока или очистки данных сайта в браузере.</li>
</ul>
<h2>7. Кому передаём</h2>
<p>Данные не продаются и не передаются рекламным сетям. Доступ к серверу имеют только администраторы проекта. Хостинг-провайдер обрабатывает технические данные для работы сервера. Если платёжный сервис будет подключён, данные платежа будет обрабатывать выбранный провайдер по своим правилам — об этом появится отдельное уведомление.</p>
<h2>8. Твои права</h2>
<ul>
<li>Узнать, какие данные о тебе хранятся, и получить копию.</li>
<li>Исправить неточные данные в настройках аккаунта или через поддержку.</li>
<li>Удалить аккаунт и связанные данные, если нет законных оснований их хранить.</li>
<li>Отозвать согласие на аналитику и настройки в любой момент.</li>
<li>Обратиться с жалобой в уполномоченный орган по защите прав субъектов персональных данных.</li>
</ul>
<p>Запросы обрабатываются через раздел «Помощь» в личном кабинете или Discord. Мы отвечаем в разумный срок и можем попросить подтвердить, что запрос исходит от владельца аккаунта.</p>
<div class="cm-legal-actions">${M.btn('Настройки cookies','v6-cookies','settings','ghost')}${M.link('Мои данные и удаление аккаунта','#settings/privacy','arrow-right','ghost')}</div>
<h2>9. Безопасность</h2>
<p>Сайт работает по HTTPS. Пароли хранятся только в виде стойкого хэша, сессионные cookie помечены HttpOnly и SameSite, идентификаторы статистики хэшируются на сервере. Доступ администраторов ограничен ролями и записывается в журнал.</p>
<h2>10. Дети</h2>
<p>Сайт не предназначен для детей младше 13 лет. Если аккаунт создан ребёнком, данные обрабатываются только с согласия законного представителя.</p>
<h2>11. Изменения политики</h2>
<p>Актуальная версия всегда доступна по адресу <a href="#privacy">copimine.ru/#privacy</a>. Дата версии указана в начале страницы. Существенные изменения отражаются в тексте и, когда это требуется, сопровождаются новым запросом согласия.</p>
<h2>12. Контакты</h2>
<p>Поддержка: раздел «Помощь» в личном кабинете. Сообщество: Discord-сервер CopiMine. Для вопросов о данных укажи логин сайта и суть запроса — так мы быстрее найдём нужные записи.</p>
</article>`;
M.renderers.page=slug=>renderPublicContentPage(slug);
})();
