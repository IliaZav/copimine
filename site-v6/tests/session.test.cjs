'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const Session=require('../assets/js/auth-session.js');
class Store { constructor(){this.values=new Map()} getItem(k){return this.values.get(k)||null} setItem(k,v){this.values.set(k,String(v))} removeItem(k){this.values.delete(k)} }
const players=[{id:'demo-001',status:'active'},{id:'demo-002',status:'banned'}];
test('unchecked login survives reload in tab but not new tab',()=>{
 const local=new Store(),tab=new Store(),otherTab=new Store(),now=1700000000000;
 const session=Session.begin('demo-001',false,now);
 Session.persist(local,tab,session);
 assert.equal(local.getItem(Session.KEYS.durable),null);
 assert.equal(Session.restore(local,tab,players,now+60000).id,'demo-001');
 assert.equal(Session.restore(local,otherTab,players,now+60000),null);
});
test('remembered login persists across browser tabs and expires',()=>{
 const local=new Store(),tab=new Store(),otherTab=new Store(),now=1700000000000;
 Session.persist(local,tab,Session.begin('demo-001',true,now));
 assert.equal(Session.restore(local,otherTab,players,now+3600000).remember,true);
 assert.equal(Session.restore(local,otherTab,players,now+31*86400000),null);
});
test('logout removes durable and temporary sessions',()=>{
 const local=new Store(),tab=new Store(),now=1700000000000;
 Session.persist(local,tab,Session.begin('demo-001',true,now));
 Session.clear(local,tab);
 assert.equal(Session.restore(local,tab,players,now),null);
});
test('banned and unknown account cannot restore',()=>{
 const local=new Store(),tab=new Store(),now=1700000000000;
 Session.persist(local,tab,Session.begin('demo-002',true,now));
 assert.equal(Session.restore(local,tab,players,now),null);
 Session.persist(local,tab,Session.begin('deleted',true,now));
 assert.equal(Session.restore(local,tab,players,now),null);
});
test('saved account state excludes session and does not mutate input',()=>{
 const payload={version:4,session:{id:'demo-001'},cart:[{id:'item',qty:1}]};
 const safe=Session.stripSession(payload);
 assert.equal(safe.session,null);
 assert.equal(payload.session.id,'demo-001');
 assert.deepEqual(safe.cart,payload.cart);
});
