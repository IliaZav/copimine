const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const D = require('../assets/js/domain-v6.js');
const now = 2_000_000_000_000;
const player = {id:'p',name:'Player',role:'player',purchaseLimits:[]};
const item = (id, currency='AR', extra={}) => ({id,name:id,price:10,currency,enabled:true,visibility:'public',...extra});
const order = extra => ({playerId:'p',itemId:'a',qty:1,status:'delivered',at:now-1000,...extra});

test('roles deny self, peers, unknown actors and unknown target roles',()=>{
  assert.equal(D.canManage({id:'a',role:'owner'},player),true);
  assert.equal(D.canManage({id:'a',role:'junior_admin'},player),true);
  assert.equal(D.canManage(player,player),false);
  assert.equal(D.canManage({id:'a',role:'admin'},{id:'b',role:'admin'}),false);
  assert.equal(D.canManage({id:'a',role:'made-up'},player),false);
  assert.equal(D.canManage({id:'a',role:'owner'},{id:'b',role:'made-up'}),false);
});
test('cart combines duplicate lines, separates currencies and rejects private items',()=>{
  const catalog=[item('a'),item('b','CM'),item('secret','AR',{visibility:'admin'})];
  const result=D.validateCart([{id:'a',qty:2},{id:'b',qty:1},{id:'a',qty:3}],catalog,player,[],now);
  assert.equal(result.lines.length,2);
  assert.deepEqual(result.totals,{AR:50,CM:10});
  assert.throws(()=>D.validateCart([{id:'a',qty:64},{id:'a',qty:1}],catalog,player,[],now));
  assert.throws(()=>D.validateCart([{id:'secret',qty:1}],catalog,player,[],now));
});
test('global limits cover the sum of different cart items and expired limits stop applying',()=>{
  const limited={...player,purchaseLimits:[{id:'l',maximum:3,period:'day'}]};
  assert.throws(()=>D.validateCart([{id:'a',qty:2},{id:'b',qty:2}],[item('a'),item('b')],limited,[],now));
  limited.purchaseLimits[0].expiresAt=now;
  assert.equal(D.validateCart([{id:'a',qty:2},{id:'b',qty:2}],[item('a'),item('b')],limited,[],now).lines.length,2);
});
test('rolling limits include the boundary, retain failed orders until refund and honor resets',()=>{
  const limited={...player,purchaseLimits:[{maximum:10,itemId:'a',period:'day'}]};
  const orders=[order({at:now-86400000,qty:2}),order({at:now-86400001,qty:9}),order({status:'refunded',qty:9}),order({status:'failed',qty:3})];
  assert.equal(D.limitRemaining(limited,'a',orders,now),5);
  limited.purchaseLimits[0].resetAt=now-500;
  assert.equal(D.limitRemaining(limited,'a',orders,now),10);
});
test('lifetime item limits and owner binding include previous purchases',()=>{
  assert.throws(()=>D.validateCart([{id:'a',qty:2}],[item('a','AR',{limit:2})],player,[order({qty:1})],now));
  assert.throws(()=>D.validateCart([{id:'a',qty:2}],[item('a','AR',{ownerBound:true})],player,[],now));
  assert.equal(D.validateCart([{id:'a',qty:1}],[item('a','AR',{ownerBound:true})],player,[],now).lines.length,1);
});
test('partial refunds release only their verified quantity and native donation cap is one',()=>{
  const limited={...player,purchaseLimits:[{maximum:5,itemId:'a',period:'day'}]};
  assert.equal(D.limitRemaining(limited,'a',[order({qty:4,refundedQuantity:2,status:'partially_failed'})],now),3);
  assert.throws(()=>D.validateCart([{id:'a',qty:2}],[item('a','CM',{sourceType:'DONATION_SHOP',maxQuantity:1})],player,[],now));
});
test('sanction countdown distinguishes pending, expired, permanent and revoked records',()=>{
  assert.equal(D.activeSanction({status:'queued',expiresAt:now+60000},now),false);
  assert.equal(D.activeSanction({status:'active',expiresAt:now},now),false);
  assert.equal(D.activeSanction({status:'active',expiresAt:null},now),true);
  assert.equal(D.activeSanction({status:'active',expiresAt:null,revokedAt:now},now),false);
  assert.equal(D.remaining(now+59000,now),'59 с');
  assert.equal(D.remaining(now,now),'Срок истёк');
});
test('durations reject zero, fractions, unknown units and over a year',()=>{
  assert.equal(D.durationMs(1,'minutes'),60000);
  assert.equal(D.durationMs(1,'days',true),null);
  assert.throws(()=>D.durationMs(0,'hours'));
  assert.throws(()=>D.durationMs(1.5,'hours'));
  assert.throws(()=>D.durationMs(1,'months'));
  assert.throws(()=>D.durationMs(366,'days'));
});
test('CSV export neutralizes formula prefixes after whitespace and escapes cells',()=>{
  assert.equal(D.csv([[' =1+2','\ufeff@SUM(A1)','a"b']]),'"\' =1+2";"\'\ufeff@SUM(A1)";"a""b"');
});
test('material source has unique IDs, Russian names and excludes air from giving',()=>{
  const raw=JSON.parse(fs.readFileSync(path.join(__dirname,'../data/materials-v6.json'),'utf8'));
  const materials=Array.isArray(raw)?raw:raw.materials;
  assert.equal(materials.length,1658);
  assert.equal(new Set(materials.map(m=>m.id)).size,1658);
  assert.ok(materials.every(m=>/[А-Яа-яЁё]/.test(m.nameRu||m.name||'')));
  const air=materials.find(m=>m.id==='minecraft:air'||m.id==='air');
  assert.ok(air);
  assert.equal(air.giveable,false);
});
