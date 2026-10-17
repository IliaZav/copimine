const test=require('node:test'),assert=require('node:assert/strict');
let D={};try{D=require('../assets/js/domain-v4.js');}catch{}
test('auth challenge expires and has bounded attempts',()=>{
 assert.equal(typeof D.verifyChallenge,'function');
 let c={code:'123456',expiresAt:10000,attempts:0,used:false};
 assert.equal(D.verifyChallenge(c,'000000',1).ok,false);assert.equal(c.attempts,1);
 assert.equal(D.verifyChallenge(c,'123456',10001).ok,false);
 const locked={code:'123456',expiresAt:10000,attempts:0,used:false};for(let i=0;i<5;i++)D.verifyChallenge(locked,'000000',1);assert.equal(D.verifyChallenge(locked,'123456',2).reason,'locked');
 assert.equal(D.verifyChallenge(c,'123456',2).ok,true);assert.equal(D.verifyChallenge(c,'123456',3).ok,false);
});
test('launcher request rejects missing, expired and terminal states',()=>{
 assert.equal(typeof D.launcherState,'function');
 assert.equal(D.launcherState(null,100),'missing');
 assert.equal(D.launcherState({expiresAt:99,status:'pending'},100),'expired');
 assert.equal(D.launcherState({expiresAt:999,status:'used'},100),'used');
 assert.equal(D.launcherState({expiresAt:999,status:'pending'},100),'pending');
 assert.equal(D.launcherState({expiresAt:999,status:'revoked'},100),'revoked');
 assert.equal(D.launcherState({expiresAt:999,status:'denied'},100),'denied');
});
test('payment confirmation idempotent, expiry and status are enforced',()=>{
 assert.equal(typeof D.confirmPayment,'function');
 const p={id:'p',player:'a',amount:100,status:'pending',expiresAt:500},a={name:'a',CM:20};
 D.confirmPayment(p,a,100);assert.equal(a.CM,120);D.confirmPayment(p,a,100);assert.equal(a.CM,120);
 assert.throws(()=>D.confirmPayment({...p,status:'cancelled'},a,101));
 assert.throws(()=>D.confirmPayment({...p,status:'pending',expiresAt:10},a,101));
 assert.throws(()=>D.confirmPayment({...p,status:'pending',player:'someone-else'},a,101));
 assert.throws(()=>D.confirmPayment({...p,status:'pending',amount:Number.MAX_SAFE_INTEGER},a,101));
});
test('CMS publishes exact frozen revision and rejects revision conflicts',()=>{
 assert.equal(typeof D.publishPage,'function');
 const p={revision:1,draft:{title:'Rules',body:'Hello'},history:[]};D.publishPage(p,1,100);
 p.draft.body='Changed';assert.equal(p.published.body,'Hello');assert.throws(()=>D.publishPage(p,0,110));
});
test('content renderer escapes raw HTML and javascript links',()=>{
 assert.equal(typeof D.markdown,'function');const s=D.markdown('# Hi\n<script>alert(1)</script>\n[x](javascript:alert(1))');
 assert.ok(!s.includes('<script'));assert.ok(!s.includes('href="javascript:'));
});
test('statistics uses rows not random numbers and separates currencies',()=>{
 assert.equal(typeof D.statistics,'function');let r=D.statistics([{at:1000,total:12,currency:'AR',status:'delivered',name:'A',qty:2},{at:1001,total:7,currency:'CM',status:'queued',name:'B',qty:1},{at:1002,total:90,currency:'AR',status:'cancelled',name:'C',qty:1}],[],[],0,2000);
 assert.equal(r.revenue.AR,12);assert.equal(r.revenue.CM,7);assert.equal(r.count,2);
});
test('toast container uses the fixed overlay class rather than document flow',()=>{
 const html=require('node:fs').readFileSync(require('node:path').join(__dirname,'../index.html'),'utf8');
 assert.match(html,/<div\b(?=[^>]*id="toast-stack")(?=[^>]*class="toast-stack")[^>]*>/);
});
