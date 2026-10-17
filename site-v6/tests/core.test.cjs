const test = require('node:test'); const assert = require('node:assert/strict');
const fs = require('node:fs');const p = '../assets/js/core.js';
const C=fs.existsSync(__dirname+'/'+p)?require(p):{};
test('snapshot selector rejects fresh and incomplete candidates',()=>{
 assert.equal(typeof C.eligibleSnapshot,'function');const n=1700000000000,H=3600000;
 const list=[{id:'old',capturedAt:n-25*H,ready:true},{id:'safe',capturedAt:n-12*H,ready:true},{id:'new',capturedAt:n-2*H,ready:true},{id:'broken',capturedAt:n-13*H,ready:false}];
 assert.equal(C.eligibleSnapshot(list,n).id,'safe');assert.equal(C.eligibleSnapshot([list[2]],n),null);
});
test('cart reprices from catalog and separates two currencies',()=>{
 assert.equal(typeof C.quote,'function');const cat=[{id:'a',price:10,currency:'AR',enabled:true,limit:3},{id:'d',price:100,currency:'CM',enabled:true,limit:1}];
 assert.deepEqual(C.quote([{id:'a',qty:2,price:1},{id:'d',qty:1}],cat).totals,{AR:20,CM:100});
 assert.throws(()=>C.quote([{id:'a',qty:-2}],cat));assert.throws(()=>C.quote([{id:'no',qty:1}],cat));assert.throws(()=>C.quote([{id:'a',qty:4}],cat));
 assert.throws(()=>C.quote([{id:'a',qty:2},{id:'a',qty:2}],cat));
});
test('escape and URL policies reject executable inputs',()=>{
 assert.equal(typeof C.escape,'function');assert.equal(C.escape('<img src=x>'),'&lt;img src=x&gt;');
 assert.equal(C.safeUrl('javascript:alert(1)'),false);assert.equal(C.safeUrl('https://discord.com/channels/1'),true);
});
test('polygon requires nonzero area and no crossing edges',()=>{
 assert.equal(typeof C.validPolygon,'function');assert.equal(C.validPolygon([[0,0],[100,0],[100,100],[0,100]]),true);
 assert.equal(C.validPolygon([[0,0],[0,0],[0,0]]),false);assert.equal(C.validPolygon([[0,0],[100,100],[0,100],[100,0]]),false);
});
test('polygon rejects repeated vertices and touching non-adjacent edges',()=>{
 assert.equal(C.validPolygon([[0,0],[100,0],[100,100],[50,0],[0,100]]),false);
 assert.equal(C.validPolygon([[0,0],[100,0],[100,100],[100,100],[0,100]]),false);
});
test('aggregate currency total cannot exceed safe integer precision',()=>{
 const cat=[{id:'a',price:Number.MAX_SAFE_INTEGER,currency:'AR',enabled:true,limit:1},{id:'b',price:2,currency:'AR',enabled:true,limit:1}];
 assert.throws(()=>C.quote([{id:'a',qty:1},{id:'b',qty:1}],cat));
});
