/* VK ID and verified email/SMS flows. Secrets and delivery stay on the server. */
(function () {
  'use strict';
  const M=window.CM,V=window.CMV4,X=window.CM6;
  if(!M||!V||!X)return;
  const R=M.renderers,H=M.handlers,F=M.forms,E=M.esc;
  const previousAuth=R.auth,previousSettings=R.settings;
  const channels=new Set(['email','sms']);
  const names={email:'Почта',sms:'Телефон',vk:'VK ID'};
  const genericMessage='Если контакт подтверждён, мы отправим код.';
  let providers=null,providerError='',providerRequest=null,providersAt=0;
  let privateOwner=null,privateEpoch=0,connections=null,contactRows=[],connectionsError='',connectionRequest=null,connectionsAt=0;
  let authFlow=null,contactFlow=null,tickTimer=null,vkData=null,vkError='',vkRequest=null,vkFinishing=false,vkStarting=false,authRemember=false;

  function owner(){return M.me()?.id||null;}
  function resetPrivate(){const id=owner();if(id!==privateOwner){privateOwner=id;privateEpoch++;connections=null;contactRows=[];connectionsError='';connectionRequest=null;connectionsAt=0;contactFlow=null;authFlow=null;authRemember=false;}return id;}
  const refreshBefore=X.refresh;
  X.refresh=async function(options={}){resetPrivate();try{return await refreshBefore.call(X,options);}finally{resetPrivate();}};
  function enabled(channel){return X.live&&providers?.[channel]?.enabled===true;}
  function unavailable(){return X.live?'Этот способ сейчас недоступен.':'Подключение доступно на работающем сайте. В автономном просмотре вход и отправка кодов отключены.';}
  function currentChannel(){const value=M.UI.params?.get('channel');return channels.has(value)?value:'email';}
  function currentPurpose(){return M.UI.tab==='recovery'?'recovery':'login';}
  function codeRoute(purpose,channel){return 'auth/'+(purpose==='recovery'?'recovery':'code')+'?channel='+channel;}
  function remaining(at){return Math.max(0,Math.ceil((at-Date.now())/1000));}
  function duration(seconds){return Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0');}
  function errorText(error){return ({provider_unconfigured:'Этот способ сейчас недоступен.',rate_limited:'Слишком много запросов. Подожди перед новой попыткой.',invalid_code:'Код не подошёл. Проверь цифры и срок его действия.',challenge_expired:'Срок кода истёк. Запроси новый.',contact_unavailable:'Этот контакт нельзя привязать. Проверь адрес или номер.',invalid_credentials:'Код или данные подтверждения не подошли. Проверь их и попробуй ещё раз.'})[error?.code]||error?.message||'Не удалось выполнить запрос. Попробуй позже.';}
  async function api(path,options){try{return await X.request(path,options);}catch(error){error.message=errorText(error);throw error;}}
  function shell(title,text,body){return '<section class="v6-auth"><div class="kicker">CopiMine</div><h1>'+E(title)+'</h1><p class="muted">'+E(text)+'</p><div class="panel">'+body+'</div></section>';}
  function noService(){return '<div class="notice">'+window.icon('info')+'<span>'+E(unavailable())+'</span></div><div class="actions mt">'+M.link('Войти с паролем','#auth/login','arrow-left','ghost')+'</div>';}
  function codeField(){return V.field('Код из сообщения','code','','text','required inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" minlength="6" maxlength="6"');}
  function totpField(required=false){return V.field(required?'Код приложения 2FA':'Код приложения 2FA, если подключено','totp','','text',(required?'required ':'')+'inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6"');}
  function remember(checked=false){return '<label class="radio-line mb"><input type="checkbox" name="remember" '+(checked?'checked':'')+'>Запомнить меня на этом устройстве</label>';}
  function mask(channel,value){value=String(value||'');if(channel==='sms')return value.length>5?value.slice(0,2)+' ••• ••• '+value.slice(-4):value;const at=value.indexOf('@');return at>0?value.slice(0,1)+'•••'+value.slice(at):value;}
  function flowFrom(result,purpose,channel,contact){if(!result?.challengeId)throw Error('Сервер не вернул запрос подтверждения. Повтори попытку.');const now=Date.now();return {id:result.challengeId,purpose,channel,contact,owner:owner(),expiresAt:now+Math.min(600000,Math.max(1000,Number(result.expiresInMs)||600000)),resendAt:now+Math.max(1000,Number(result.resendAfterMs)||60000),message:result.message||genericMessage,sending:false};}
  function timerHtml(flow,prefix){return '<div class="v6-code-timing"><span role="status" data-code-expiry="'+prefix+'">Код действует '+duration(remaining(flow.expiresAt))+'</span><button type="button" class="text-link" data-action="'+(prefix==='contact'?'v6-contact-resend':'v6-auth-code-resend')+'" data-code-resend="'+prefix+'" '+(remaining(flow.resendAt)?'disabled':'')+'>Повторить через '+remaining(flow.resendAt)+' с</button></div>';}
  function tick(){for(const [prefix,flow]of [['auth',authFlow],['contact',contactFlow]]){if(!flow)continue;const expiry=remaining(flow.expiresAt),cooldown=remaining(flow.resendAt);document.querySelectorAll('[data-code-expiry="'+prefix+'"]').forEach(n=>n.textContent=expiry?'Код действует '+duration(expiry):'Срок кода истёк');document.querySelectorAll('[data-code-resend="'+prefix+'"]').forEach(n=>{n.disabled=cooldown>0||flow.sending;n.textContent=cooldown?'Повторить через '+cooldown+' с':'Запросить новый код';});document.querySelectorAll('[data-code-submit="'+prefix+'"]').forEach(n=>{if(!expiry)n.disabled=true;});}}

  function providerSlot(){
    const ready=!X.live||providers!==null;
    const options=[['vk','VK ID','vk'],['email','Код на почту','mail'],['sms','Код по SMS','phone']];
    return '<div class="v6-auth-separator"><span>Другой способ входа</span></div><div class="v6-provider-options">'+options.map(([kind,label,ic])=>'<button type="button" class="btn" data-action="'+(kind==='vk'?'v6-vk-start':'v6-auth-code-open')+'" data-channel="'+kind+'" data-mode="login" '+(!enabled(kind)?'disabled':'')+'>'+window.icon(ic)+'<span>'+label+'</span></button>').join('')+'</div><p class="hint mt12" role="status">'+E(providerError||(!ready?'Проверяем доступные способы…':options.some(([kind])=>enabled(kind))?'Для кода нужна заранее подтверждённая почта или телефон.':unavailable()))+'</p>'+(providerError?M.btn('Проверить ещё раз','v6-auth-providers-retry','refresh','small mt12'):'');
  }
  function paintProviders(){const slot=document.getElementById('v6-auth-providers');if(slot)slot.innerHTML=providerSlot();const submit=document.querySelector('[data-form="v6-auth-code-request"] [type=submit]');if(submit)submit.disabled=!enabled(currentChannel());const vkRetry=document.querySelector('#v6-vk-result [data-action="v6-vk-start"]');if(vkRetry)vkRetry.disabled=!enabled('vk');const note=document.getElementById('v6-code-service');if(note)note.textContent=providerError||(!providers&&X.live?'Проверяем доступность…':enabled(currentChannel())?'Код действует 10 минут.':'Этот способ сейчас недоступен.');paintConnections();}
  async function loadProviders(force=false){
    if(!X.live){providers={vk:{enabled:false},email:{enabled:false},sms:{enabled:false}};paintProviders();return providers;}
    if(providerRequest)return providerRequest;
    if(!force&&providers&&Date.now()-providersAt<60000){paintProviders();return providers;}
    providerError='';
    const request=api('/auth/providers').then(data=>{providers={};for(const key of ['vk','email','sms'])providers[key]={enabled:data?.[key]?.enabled===true};providersAt=Date.now();return providers;}).catch(error=>{providerError=errorText(error);return null;}).finally(()=>{if(providerRequest===request)providerRequest=null;paintProviders();});
    providerRequest=request;return request;
  }
  H['v6-auth-providers-retry']=()=>loadProviders(true);
  H['v6-auth-code-open']=b=>{if(!channels.has(b.dataset.channel)||!enabled(b.dataset.channel))return M.toast(unavailable(),true);authRemember=!!document.querySelector('form[data-form="login"] [name=remember],form[data-form="register"] [name=remember]')?.checked;authFlow=null;M.navTo(codeRoute('login',b.dataset.channel));};

  function codeView(purpose){
    const channel=currentChannel();
    if(authFlow&&(authFlow.purpose!==purpose||authFlow.channel!==channel))authFlow=null;
    const heading=purpose==='recovery'?'Восстановить пароль':'Войти по коду';
    if(!X.live)return shell(heading,'Почта и телефон помогают вернуться в свой аккаунт.',noService());
    const tabs='<nav class="v6-auth-channel-tabs" aria-label="Способ подтверждения">'+[['email','Почта'],['sms','SMS']].map(([id,label])=>'<a href="#'+codeRoute(purpose,id)+'" class="'+(channel===id?'active':'')+'">'+label+'</a>').join('')+'</nav>';
    if(authFlow){const flow=authFlow;return shell(purpose==='recovery'?'Новый пароль':'Введи код',mask(channel,flow.contact),tabs+'<p class="hint mb">'+E(flow.message)+'</p><form data-form="v6-auth-code-confirm">'+codeField()+(purpose==='recovery'?V.field('Новый пароль','password','','password','required minlength="12" maxlength="256" autocomplete="new-password"'):'')+totpField()+(purpose==='login'?remember(authRemember):'')+'<p class="hint mb">Подтверждение нужно завершить в этом браузере.'+(purpose==='recovery'?' После смены пароля остальные сеансы будут завершены.':'')+'</p><div class="form-error" role="alert"></div><button type="submit" class="btn primary wide" data-code-submit="auth">'+(purpose==='recovery'?'Изменить пароль':'Войти')+'</button></form>'+timerHtml(flow,'auth')+'<div class="v6-auth-links">'+M.btn('Изменить контакт','v6-auth-code-change','arrow-left','text-link')+M.link('Войти с паролем','#auth/login','','text-link')+'</div>');}
    return shell(heading,purpose==='recovery'?'Укажи подтверждённый контакт своего аккаунта.':'Используй почту или телефон, подтверждённые в настройках.',tabs+'<form data-form="v6-auth-code-request">'+V.field(channel==='email'?'Почта':'Телефон в международном формате','contact','',channel==='email'?'email':'tel','required maxlength="'+(channel==='email'?254:32)+'" autocomplete="'+(channel==='email'?'email':'tel')+'" '+(channel==='sms'?'placeholder="+7 900 000-00-00"':''))+'<p id="v6-code-service" class="hint mb">'+(enabled(channel)?'Код действует 10 минут.':'Проверяем доступность…')+'</p><div class="form-error" role="alert"></div><button type="submit" class="btn primary wide" '+(!enabled(channel)?'disabled':'')+'>Получить код</button></form><div class="v6-auth-links">'+M.link('Войти с паролем','#auth/login','arrow-left','text-link')+(purpose==='login'?M.link('Забыли пароль?','#auth/recovery?channel='+channel,'','text-link'):'')+'</div><p class="hint mt">Если нет доступа к подтверждённым контактам, обратись к администрации.</p>');
  }
  F['v6-auth-code-request']=async(f,d)=>{const channel=currentChannel(),purpose=currentPurpose();if(!enabled(channel))throw Error(unavailable());const contact=String(d.get('contact')||'').trim(),result=await api('/auth/'+(purpose==='recovery'?'recovery':'code')+'/request',{method:'POST',body:{channel,contact}});authFlow=flowFrom(result,purpose,channel,contact);M.render();};
  F['v6-auth-code-confirm']=async(f,d)=>{const flow=authFlow;if(!flow||remaining(flow.expiresAt)===0)throw Error('Срок кода истёк. Запроси новый.');const body={challengeId:flow.id,code:d.get('code'),totp:d.get('totp')||undefined};if(flow.purpose==='recovery')body.password=d.get('password');else body.remember=!!d.get('remember');await api('/auth/'+(flow.purpose==='recovery'?'recovery/confirm':'code/login'),{method:'POST',body});authFlow=null;await X.refresh({silent:true});M.navTo('account');};
  H['v6-auth-code-change']=()=>{authFlow=null;M.render();};
  H['v6-auth-code-resend']=async()=>{const flow=authFlow;if(!flow||flow.sending||remaining(flow.resendAt))return;authRemember=!!document.querySelector('[data-form="v6-auth-code-confirm"] [name=remember]')?.checked;flow.sending=true;tick();try{const result=await api('/auth/'+(flow.purpose==='recovery'?'recovery':'code')+'/request',{method:'POST',body:{channel:flow.channel,contact:flow.contact}});if(authFlow!==flow)return;authFlow=flowFrom(result,flow.purpose,flow.channel,flow.contact);M.render();}finally{flow.sending=false;tick();}};

  function rememberVK(value){try{if(value===undefined)return sessionStorage.getItem('copimine-v6-vk-remember')==='true';if(value===null)sessionStorage.removeItem('copimine-v6-vk-remember');else sessionStorage.setItem('copimine-v6-vk-remember',String(!!value));}catch{}return false;}
  function vkUrl(value){let u;try{u=new URL(String(value||''));}catch{}if(!u||u.protocol!=='https:'||u.username||u.password||u.hostname!=='id.vk.ru'||u.port)throw Error('Сервер вернул неподходящий адрес VK ID. Переход отменён.');return u.href;}
  H['v6-vk-start']=async b=>{if(vkStarting)return;if(!enabled('vk'))return M.toast(unavailable(),true);const mode=b.dataset.mode==='link'?'link':'login',choice=!!document.querySelector('form[data-form="login"] [name=remember],form[data-form="register"] [name=remember]')?.checked;vkStarting=true;b.disabled=true;try{const result=await api('/auth/vk/start',{method:'POST',body:{mode,remember:choice}}),url=vkUrl(result.url);rememberVK(choice);location.assign(url);}finally{vkStarting=false;if(b.isConnected)b.disabled=false;}};
  async function finishVK(body){if(vkFinishing)return;vkFinishing=true;try{await api('/auth/vk/finish',{method:'POST',body});rememberVK(null);vkData=null;await X.refresh({silent:true});M.navTo('account');}finally{vkFinishing=false;}}
  function vkBody(){
    if(!X.live)return noService();
    if(vkError||vkData?.status==='error')return '<div class="notice">'+window.icon('info')+'<span>'+E(vkError||vkData.message||'Вход через VK не завершён.')+'</span></div><div class="actions mt">'+M.btn('Начать снова','v6-vk-start','vk','primary','data-mode="login" '+(!enabled('vk')?'disabled':''))+M.link('Войти с паролем','#auth/login','','ghost')+'</div>';
    if(!vkData)return '<p class="v6-auth-progress" role="status">Проверяем ответ VK ID…</p>';
    if(vkData.status==='new')return '<p class="mb">'+E(vkData.displayName||'VK ID подтверждён')+'. Для нового аккаунта CopiMine нужны ник, почта и пароль.</p><form data-form="v6-vk-finish">'+V.field('Ник на сайте','name','','text','required minlength="3" maxlength="32" autocomplete="username"')+V.field('Почта','email','','email','required maxlength="254" autocomplete="email"')+V.field('Пароль CopiMine','password','','password','required minlength="12" maxlength="256" autocomplete="new-password"')+remember(rememberVK())+'<label class="radio-line mb"><input type="checkbox" name="terms" required>Принимаю <a href="#rules">правила сервера</a></label>'+V.actions('Создать аккаунт')+'</form><p class="hint mt">Пароль VK здесь не нужен.</p>';
    if(vkData.status==='login'&&vkData.requiresTotp)return '<p class="mb">Для аккаунта '+E(vkData.displayName||'CopiMine')+' нужен код двухфакторной защиты.</p><form data-form="v6-vk-finish">'+totpField(true)+remember(rememberVK())+V.actions('Войти')+'</form>';
    return '<p class="v6-auth-progress" role="status">Завершаем вход…</p>';
  }
  function paintVK(){const root=document.getElementById('v6-vk-result');if(root)root.innerHTML=vkBody();}
  async function loadVK(){if(!X.live||vkRequest||vkData)return;vkError='';const request=api('/auth/vk/status').then(async data=>{if(!['new','login','linked','error'].includes(data?.status))throw Error('Запрос VK не найден или устарел. Начни вход снова.');vkData=data;if(M.UI.page!=='auth'||M.UI.tab!=='vk')return;if(data.status==='linked'){await X.refresh({silent:true});M.navTo('settings/connections');}else if(data.status==='login'&&!data.requiresTotp){paintVK();await finishVK({remember:rememberVK()});}else paintVK();}).catch(error=>{vkError=errorText(error);paintVK();}).finally(()=>{if(vkRequest===request)vkRequest=null;});vkRequest=request;return request;}
  F['v6-vk-finish']=async(f,d)=>{if(!vkData)throw Error('Запрос VK устарел. Начни вход снова.');const body={remember:!!d.get('remember')};if(vkData.status==='new'){if(!d.get('terms'))throw Error('Нужно принять правила сервера.');for(const key of ['name','email','password'])body[key]=d.get(key);}else if(vkData.requiresTotp)body.totp=d.get('totp');await finishVK(body);};

  R.auth=tab=>{
    if(X.live&&!X.ready&&['vk','code','recovery'].includes(tab))return '<div class="v6-loading">Проверяем сеанс…</div>';
    if(tab==='vk')return shell('Вход через VK ID','Подтверждение возвращается со страницы VK ID.','<div id="v6-vk-result">'+vkBody()+'</div>');
    if(tab==='code'||tab==='recovery')return codeView(tab==='recovery'?'recovery':'login');
    let html=previousAuth(tab);
    if(tab==='login'||tab==='register'||!tab){html=html.replace('<div class="v6-auth-links">','<div id="v6-auth-providers">'+providerSlot()+'</div><div class="v6-auth-links">');if(tab!=='register')html=html.replace('<a href="#account/support">Нужна помощь?</a>',M.link('Забыли пароль?','#auth/recovery','','text-link'));}
    return html;
  };

  function contactCard(channel){const row=contactRows.find(x=>x.channel===channel),verified=row?.verified===true,pending=contactFlow?.channel===channel&&contactFlow.owner===owner()&&remaining(contactFlow.expiresAt)>0;return '<article class="panel v6-contact-card"><div class="panel-head"><h3>'+names[channel]+'</h3>'+window.icon(channel==='email'?'mail':'phone')+'</div><p class="v6-contact-value">'+E(row?.masked||mask(channel,row?.value)||(channel==='email'?'Почта не добавлена':'Телефон не добавлен'))+'</p>'+M.pill(verified?'Подтверждён'+(channel==='email'?'а':''):row?'Не подтверждён'+(channel==='email'?'а':''):'Не подключено',verified?'green':'')+'<p class="hint mt12">Для входа по коду и восстановления доступа.</p><div class="actions mt">'+(pending?M.btn('Ввести код','v6-contact-pending','mail','small','data-channel="'+channel+'"'):'')+M.btn(row?'Изменить':'Добавить','v6-contact-start',verified?'edit':'plus','small','data-channel="'+channel+'" '+(!enabled(channel)?'disabled':''))+(row?M.btn('Удалить','v6-contact-remove','x','small','data-channel="'+channel+'"'):'')+'</div>'+(!enabled(channel)?'<p class="hint mt12">Отправка кодов сейчас недоступна.</p>':'')+'</article>';}
  function connectionHtml(){
    if(!X.live)return '<section class="panel"><h2>Способы входа</h2><p class="mt">'+E(unavailable())+'</p><div class="v6-provider-options mt">'+[['vk','VK ID'],['mail','Почта'],['phone','SMS']].map(([ic,label])=>'<button class="btn" type="button" disabled>'+window.icon(ic)+label+'</button>').join('')+'</div></section>';
    if(connectionsError)return '<section class="panel"><h2>Способы входа</h2><p class="mt" role="alert">'+E(connectionsError)+'</p>'+M.btn('Обновить','v6-connections-refresh','refresh','mt')+'</section>';
    if(!connections)return '<section class="panel"><p role="status">Получаем способы входа…</p></section>';
    const vk=connections.vk||{linked:false};
    return '<div class="v6-contact-grid">'+contactCard('email')+contactCard('sms')+'<article class="panel v6-contact-card"><div class="panel-head"><h3>VK ID</h3>'+window.icon('vk')+'</div><p class="v6-contact-value">'+E(vk.linked?(vk.displayName||'Аккаунт VK'):'VK не подключён')+'</p>'+M.pill(vk.linked?'Подключён':'Не подключён',vk.linked?'green':'')+'<p class="hint mt12">Вход через страницу подтверждения VK ID.</p><div class="actions mt">'+(vk.linked?M.btn('Отключить','v6-vk-unlink','x','small'):M.btn('Подключить VK','v6-vk-start','vk','small','data-mode="link" '+(!enabled('vk')?'disabled':'')))+'</div>'+(!enabled('vk')?'<p class="hint mt12">Вход через VK сейчас недоступен.</p>':'')+'</article></div><p class="hint mt">Подключённая 2FA продолжает защищать вход через VK и коды. Подтверждение контакта не меняет права аккаунта.</p>';
  }
  function paintConnections(){resetPrivate();const root=document.getElementById('v6-connections');if(root)root.innerHTML=connectionHtml();}
  async function loadConnections(force=false){const id=resetPrivate(),epoch=privateEpoch;if(!id||!X.live){paintConnections();return;}if(connectionRequest)return connectionRequest;if(!force&&connections&&Date.now()-connectionsAt<30000){paintConnections();return connections;}connectionsError='';const request=Promise.all([api('/account/connections'),api('/account/contacts')]).then(([linked,contacts])=>{if(id!==owner()||epoch!==privateEpoch)return null;connections=linked;contactRows=Array.isArray(contacts.contacts)?contacts.contacts:[];connectionsAt=Date.now();return connections;}).catch(error=>{if(id===owner()&&epoch===privateEpoch)connectionsError=errorText(error);return null;}).finally(()=>{if(connectionRequest===request)connectionRequest=null;paintConnections();});connectionRequest=request;return request;}
  H['v6-connections-refresh']=()=>Promise.all([loadConnections(true),loadProviders(true)]);
  R.settings=tab=>{
    if(tab==='contacts')tab='connections';
    if(tab==='connections'){if(!M.me())return R.auth('login');resetPrivate();return '<div class="page-head"><div><h1>Настройки</h1><p>Подтверждённые контакты и вход через VK.</p></div>'+M.link('В кабинет','#account','arrow-left','ghost')+'</div><nav class="tabs">'+[['profile','Профиль'],['security','Защита'],['connections','Способы входа'],['devices','Сеанс'],['interface','Интерфейс']].map(([id,label])=>'<a href="#settings/'+id+'" class="'+(id==='connections'?'active':'')+'">'+label+'</a>').join('')+'</nav><div class="settings-content" id="v6-connections">'+connectionHtml()+'</div>';}
    const html=previousSettings(tab);return html.includes('href="#settings/contacts"')?html.replaceAll('href="#settings/contacts"','href="#settings/connections"'):html.replace('</nav>','<a href="#settings/connections">Способы входа</a></nav>');
  };
  function passwordFields(){return V.field('Пароль CopiMine','password','','password','required autocomplete="current-password" maxlength="256"')+(M.me()?.mfaEnabled?totpField(true):'');}
  function openContact(channel,value){if(!channels.has(channel)||!enabled(channel))return M.toast(unavailable(),true);const row=contactRows.find(x=>x.channel===channel);M.openModal(channel==='email'?'Подтвердить почту':'Подтвердить телефон','<form data-form="v6-contact-request"><input type="hidden" name="channel" value="'+channel+'">'+V.field(channel==='email'?'Почта':'Телефон в международном формате','value',value??row?.value??(channel==='email'?M.me()?.email||'':''),channel==='email'?'email':'tel','required maxlength="'+(channel==='email'?254:32)+'" autocomplete="'+(channel==='email'?'email':'tel')+'"')+passwordFields()+'<p class="hint mb">Код подтвердит, что контакт принадлежит тебе.</p>'+V.actions('Получить код')+'</form>');}
  H['v6-contact-start']=b=>openContact(b.dataset.channel);
  function openContactCode(){const flow=contactFlow;if(!flow||flow.owner!==owner())return;M.openModal('Подтверди '+(flow.channel==='email'?'почту':'телефон'),'<p>'+E(mask(flow.channel,flow.contact))+'</p><p class="hint mt12 mb">'+E(flow.message)+'</p><form data-form="v6-contact-verify">'+codeField()+'<div class="form-error" role="alert"></div><button type="submit" class="btn primary wide" data-code-submit="contact">Подтвердить</button></form>'+timerHtml(flow,'contact'));tick();}
  H['v6-contact-pending']=b=>{if(contactFlow?.channel===b.dataset.channel)openContactCode();};
  F['v6-contact-request']=async(f,d)=>{const channel=d.get('channel');if(!enabled(channel))throw Error(unavailable());const value=String(d.get('value')||'').trim(),result=await api('/account/contacts/request',{method:'POST',body:{channel,value,password:d.get('password'),totp:d.get('totp')||undefined}});contactFlow=flowFrom(result,'contact',channel,value);openContactCode();};
  F['v6-contact-verify']=async(f,d)=>{const flow=contactFlow;if(!flow||flow.owner!==owner()||!remaining(flow.expiresAt))throw Error('Срок кода истёк. Запроси новый.');await api('/account/contacts/verify',{method:'POST',body:{challengeId:flow.id,code:d.get('code')}});contactFlow=null;await X.refresh({silent:true});await loadConnections(true);M.closeModal();M.render();M.toast('Контакт подтверждён.');};
  H['v6-contact-resend']=()=>{const flow=contactFlow;if(flow&&!remaining(flow.resendAt))openContact(flow.channel,flow.contact);};
  H['v6-contact-remove']=b=>{const channel=b.dataset.channel;if(!channels.has(channel)||!X.live)return;M.openModal('Удалить '+(channel==='email'?'почту':'телефон')+'?','<p>Этот контакт больше не будет доступен для входа по коду и восстановления пароля.</p><form data-form="v6-contact-remove" class="mt"><input type="hidden" name="channel" value="'+channel+'">'+passwordFields()+V.actions('Удалить контакт')+'</form>');};
  F['v6-contact-remove']=async(f,d)=>{await api('/account/contacts/remove',{method:'POST',body:{channel:d.get('channel'),password:d.get('password'),totp:d.get('totp')||undefined}});if(contactFlow?.channel===d.get('channel'))contactFlow=null;await X.refresh({silent:true});await loadConnections(true);M.closeModal();M.render();M.toast('Контакт удалён.');};
  H['v6-vk-unlink']=()=>{if(!X.live||!connections?.vk?.linked)return;M.openModal('Отключить VK ID?','<p>Вход через этот VK ID перестанет работать. Пароль CopiMine и подтверждённые контакты останутся доступны.</p><form data-form="v6-vk-unlink" class="mt">'+passwordFields()+V.actions('Отключить VK')+'</form>');};
  F['v6-vk-unlink']=async(f,d)=>{await api('/account/vk/unlink',{method:'POST',body:{password:d.get('password'),totp:d.get('totp')||undefined}});await loadConnections(true);M.closeModal();M.render();M.toast('VK ID отключён.');};

  V.after.push((page,tab)=>{
    resetPrivate();clearInterval(tickTimer);
    if(page==='auth'&&['login','register','code','recovery','vk',''].includes(tab))loadProviders();
    if(page==='auth'&&tab==='vk'&&(!X.live||X.ready))loadVK();
    else if(vkData||vkError){vkData=null;vkError='';}
    if(page==='settings'&&['connections','contacts'].includes(tab)){loadProviders();loadConnections();}
    if(page==='auth'||page==='settings'){tick();tickTimer=setInterval(tick,1000);}
    if(page!=='auth')authFlow=null;
  });
  X.authProviders={reload:()=>loadProviders(true),refreshConnections:()=>loadConnections(true)};
})();
