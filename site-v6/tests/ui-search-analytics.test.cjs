const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');
function setup() {
  const listeners = new Map(), storage = new Map(), sent = [];
  const document = { readyState:'loading', addEventListener(type, fn) { listeners.set(type, fn); } };
  const M = { handlers:{}, esc:String, state:{players:[],catalog:[],orders:[],payments:[],analyticsEvents:[]}, save() {}, money:(n,c)=>n+' '+c, downloadFile(){} };
  const X = { live:true, consent:null, canAdmin:()=>true, async request(route, options) { sent.push({route,options}); return {}; } };
  const window = { CM:M, CMV4:{table(){}}, CM5:{adminExtensions:{}}, CM6:X, addEventListener(){} };
  const context = vm.createContext({window, document, location:{hash:'#shop'}, sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)}, crypto:webcrypto, URLSearchParams, setTimeout:()=>1, clearTimeout(){}, console });
  for (const file of ['search-v6.js','analytics-v6.js']) vm.runInContext(fs.readFileSync(path.join(__dirname,'../assets/js',file),'utf8'),context,{filename:file});
  return {context,X,M,listeners,storage,sent};
}
test('search handles Russian typo, swapped keyboard and adjacent transposition', () => {
  const {X} = setup(), S=X.search;
  const row={title:'Карточка игрока',detail:'Профиль и эндер-сундук',aliases:'инвентарь инвентари inventory profile'};
  assert.equal(S.keyboard('bydtynfhm'),'инвентарь');
  assert.ok(Number.isFinite(S.score(row,S.normalize('инвинтарь'))));
  assert.ok(Number.isFinite(S.score(row,S.normalize(S.keyboard('bydtynfhm')))));
  assert.equal(S.score(row,'автомобиль'),Infinity);
  assert.equal(S.distance('инвентарь','инвентраь'),1);
});
test('analytics requires consent and rejects private pages even with forged page payload', () => {
  const {X,context,storage} = setup();
  assert.equal(X.track('cart_add',{itemId:'minecraft:stone',quantity:1}),false);
  assert.equal(storage.size,0);
  X.consent={analytics:true};
  context.location.hash='#admin/players?player=secret';
  assert.equal(X.track('page_view',{page:'shop',player:'secret'}),false);
  assert.equal(X.analytics.queueSize(),0);
  context.location.hash='#account';
  assert.equal(X.track('page_view'),false);
});
test('analytics sends only allowed fields in a bounded batch', async () => {
  const {X,sent} = setup(); X.consent={analytics:true};
  for(let i=0;i<75;i++) X.track('cart_add',{itemId:'minecraft:stone',quantity:3000,query:'private',player:'secret',ip:'192.0.2.1'});
  assert.equal(X.analytics.queueSize(),40);
  await X.analytics.flush();
  assert.equal(sent.length,1);
  assert.equal(sent[0].route,'/analytics/events');
  assert.equal(sent[0].options.method,'POST');
  assert.equal(sent[0].options.body.consent,true);
  assert.equal(sent[0].options.body.events.length,40);
  const event=sent[0].options.body.events[0];
  assert.deepEqual(Object.keys(event).sort(),['at','itemId','page','quantity','type','visitor']);
  assert.equal(event.quantity,2304);
  assert.equal(event.page,'shop');
  assert.equal(X.analytics.queueSize(),0);
});
test('withdrawing consent removes queued events and session token', async () => {
  const {X,listeners,storage,sent} = setup(); X.consent={analytics:true};
  X.track('page_view'); assert.equal(X.analytics.queueSize(),1); assert.equal(storage.size,1);
  X.consent={analytics:false}; listeners.get('cm-consent')();
  assert.equal(X.analytics.queueSize(),0); assert.equal(storage.size,0);
  await X.analytics.flush(); assert.equal(sent.length,0);
});
test('financial summary counts delivered lines, distinct orders and confirmed payments', () => {
  const {X,M} = setup(); const at=Date.now();
  M.state.orders=[
    {id:'pending',player:'demo',item:'stone',qty:2,total:99,currency:'AR',status:'queued',at},
    {id:'partial',player:'demo',item:'stone',qty:2,total:10,currency:'AR',status:'delivered',orderStatus:'partially_failed',at},
    {id:'partial',player:'demo',item:'tool',qty:1,total:100,currency:'AR',status:'refunded',orderStatus:'partially_failed',at},
    {id:'cm',player:'demo',item:'tool',qty:1,total:20,currency:'CM',status:'delivered',at},
    {id:'cm',player:'demo',item:'stone',qty:1,total:5,currency:'CM',status:'delivered',at},
    {id:'cancelled',player:'demo',item:'tool',qty:1,total:99,currency:'CM',status:'cancelled',at},
    {id:'refund',player:'demo',item:'tool',qty:1,total:90,currency:'CM',status:'refunded',at}
  ];
  M.state.payments=[{status:'paid',rubles:50,at},{status:'completed',rubles:20,at},{status:'pending',rubles:900,at},{status:'test_completed',rubles:700,at}];
  const d=X.analytics.localSummary('day');
  assert.equal(d.revenue.AR,10); assert.equal(d.revenue.CM,25); assert.equal(d.rubles,70);
  assert.equal(d.orders,2); assert.equal(d.items,4); assert.equal(d.buyers,1);
  assert.equal(d.pending,1); assert.equal(d.failed,2); assert.equal(d.refunded,1);
});
test('analytics year range and old all alias fit the server date limit', () => {
  const {X} = setup();
  for (const range of ['year','all']) {
    const b=X.analytics.bounds(range);
    assert.equal((b.end-b.start)/86400000,365);
    assert.notEqual(b.from,'1970-01-01');
  }
});
test('removing analytics permission hides cached reports, blocks CSV and discards late responses', async () => {
  const {X,M,context} = setup();let allowed=true,resolve,downloads=0;
  M.me=()=>({id:'same-staff-account'});M.btn=()=>'';M.empty=title=>title;M.render=()=>{};M.downloadFile=()=>downloads++;
  context.window.icon=()=>'';context.location.hash='#admin/statistics?range=day';X.can=permission=>permission==='analytics.read'&&allowed;
  X.request=()=>new Promise(done=>{resolve=done;});
  X.analytics.render();await new Promise(setImmediate);resolve({visits:731,orders:0,revenue:{AR:0,CM:0}});await new Promise(setImmediate);
  assert.match(X.analytics.render(),/731/);
  allowed=false;assert.equal(X.analytics.render(),'Нет доступа');M.handlers['v6-analytics-export']();assert.equal(downloads,0);
  allowed=true;assert.doesNotMatch(X.analytics.render(),/731/);await new Promise(setImmediate);
  allowed=false;resolve({visits:842,orders:0,revenue:{AR:0,CM:0}});await new Promise(setImmediate);
  assert.equal(X.analytics.render(),'Нет доступа');allowed=true;assert.doesNotMatch(X.analytics.render(),/842/);
});
