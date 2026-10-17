const fs=require('node:fs/promises');
const assert=require('node:assert/strict');
const {chromium:pw}=require('playwright');
(async()=>{
 const {default:chromium}=await import('/tmp/copimine-browser-runtime/node_modules/@sparticuz/chromium/build/index.js');
 const browser=await pw.launch({args:chromium.args,executablePath:await chromium.executablePath(),headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1.5,reducedMotion:'reduce'});
 const report={at:new Date().toISOString(),tests:[],errors:[]};
 page.on('pageerror',e=>report.errors.push(e.message));
 page.setDefaultTimeout(5000);
 const base='http://127.0.0.1:4175/index.html';
 const route=async r=>{await page.goto(base+'#'+r,{waitUntil:'networkidle'});await page.waitForTimeout(80);};
 const test=async(name,fn)=>{try{await page.evaluate(()=>window.CM?.closeModal());const detail=await fn();report.tests.push({name,pass:true,detail});}catch(e){report.tests.push({name,pass:false,error:e.message});}console.log(JSON.stringify(report.tests.at(-1)));};
 await route('home');await page.locator('[data-action="v6-cookies-essential"]').click();
 await page.locator('[data-action="v6-demo-admin"]').click();
 await test('Inventory and ender use independent snapshots',async()=>{
  await route('admin/players?player=demo-001&sub=profile');
  assert.equal(await page.locator('.inventory-slot').count(),41);assert.equal(await page.locator('.v6-chest-slot').count(),27);
  return await page.evaluate(()=>({inventory:CM6.snapshot(CM6.player())?.id,ender:CM6.snapshot(CM6.player(),'ender')?.id||null,placeholderIcons:document.querySelectorAll('.inventory-slot .missing-material').length,materialImages:document.querySelectorAll('.inventory-slot img').length}));
 });
 await test('Global search handles typo and wrong keyboard layout',async()=>{
  await page.keyboard.press('Control+k');const input=page.locator('#v6-search-input');
  for(const q of ['bydtynfhm','инвинтарь']){await input.fill(q);await page.waitForTimeout(120);assert.match(await page.locator('#v6-search-results').innerText(),/Карточка игрока/);}
  await input.fill('Выдать предмет');await page.waitForTimeout(120);await page.keyboard.press('Enter');
  assert.equal(await page.locator('form[data-form="v6-player-action"] [name="action"]').inputValue(),'give');
  assert.equal(await page.locator('form[data-form="v6-player-action"] [name="playerId"]').inputValue(),'demo-001');
  return 'typo/layout result and action form';
 });
 await test('Give picker returns items with a stable chosen ID',async()=>{
  await route('admin/players?player=demo-001&sub=actions');
  await page.locator('[data-action="v6-player-action"][data-id="give"]').click();
  await page.locator('#v6-give-search').fill('алмазный меч');await page.waitForTimeout(140);
  const options=page.locator('#v6-item-results [role=option]');assert.ok(await options.count()>0);await options.first().click();
  const id=await page.locator('#v6-selected-item').inputValue();assert.ok(/diamond_sword/.test(id));
  const reason=page.locator('#modal textarea[name=reason]');assert.equal(await reason.getAttribute('required'),'');
  await page.screenshot({path:'/tmp/copimine-visual/v6-desktop-give-dialog.png',animations:'disabled'});
  return {id,results:await options.count()};
 });
 await test('Timed ban counts down and can be revoked with a reason',async()=>{
  await route('admin/players?player=demo-001&sub=sanctions');
  await page.locator('[data-action="v6-player-action"][data-id="ban"]').click();
  await page.locator('#modal [name=duration]').fill('1');await page.locator('#modal [name=unit]').selectOption('minutes');await page.locator('#modal [name=reason]').fill('Проверка формы на тестовом аккаунте');
  await page.locator('#modal [type=submit]').click();await page.waitForTimeout(80);
  const timer=page.locator('[data-sanction-until]').first();const before=await timer.innerText();await page.waitForTimeout(1200);const after=await timer.innerText();assert.notEqual(before,after);
  assert.ok(await page.locator('[data-action="v6-sanction-revoke"]').count());
  await page.screenshot({path:'/tmp/copimine-visual/v6-desktop-ban-active.png',fullPage:true,animations:'disabled'});
  await page.locator('[data-action="v6-sanction-revoke"]').first().click();await page.locator('#modal [name=reason]').fill('Проверка отмены завершена');await page.locator('#modal [type=submit]').click();await page.waitForTimeout(80);
  const status=await page.evaluate(()=>CM.state.sanctions[0].status);assert.equal(status,'revoked');
  return {before,after,status};
 });
 await test('Personal purchase limit creates and removes in finance',async()=>{
  await route('admin/players?player=demo-001&sub=finance');await page.locator('[data-action="v6-player-action"][data-id="purchase.limit"]').click();
  await page.locator('#modal [name=maximum]').fill('4');await page.locator('#modal [name=permanent]').check();await page.locator('#modal [name=reason]').fill('Проверка персонального лимита');await page.locator('#modal [type=submit]').click();await page.waitForTimeout(80);
  const limit=await page.evaluate(()=>CM6.player().purchaseLimits[0]);assert.equal(limit.maximum,4);assert.equal(limit.expiresAt,null);
  await page.locator('[data-action="v6-limit-remove"]').first().click();await page.locator('#modal [name=reason]').fill('Тестовый лимит больше не нужен');await page.locator('#modal [type=submit]').click();await page.waitForTimeout(80);
  assert.equal(await page.evaluate(()=>CM6.player().purchaseLimits.length),0);return limit;
 });
 await test('Shop cart changes count without replacing main and keeps focus',async()=>{
  await page.locator('[data-action="demo-player"]').click();await route('shop');
  await page.evaluate(()=>{window.__qaMain=document.querySelector('main');});
  const button=page.locator('[data-action=add-cart]').first();const id=await button.getAttribute('data-id');await button.click();
  const control=page.locator('[data-cart-control="'+id+'"]');assert.equal(await control.locator('output').innerText(),'1');
  await control.locator('[data-action="v6-cart-plus"]').click();assert.equal(await control.locator('output').innerText(),'2');
  assert.equal(await page.evaluate(()=>__qaMain===document.querySelector('main')),true);
  assert.equal(await page.evaluate(()=>document.activeElement?.dataset.action),'v6-cart-plus');
  await control.locator('[data-action="v6-cart-minus"]').click();await control.locator('[data-action="v6-cart-minus"]').click();assert.equal(await control.locator('[data-action=add-cart]').count(),1);
  return {id,mainPreserved:true};
 });
 await test('Product dialog cart keeps the dialog and keyboard focus',async()=>{
  await route('shop');const product=page.locator('[data-action=product]').first();const id=await product.getAttribute('data-id');await product.click();
  await page.evaluate(()=>{window.__qaDialog=document.querySelector('#modal .product-detail-v6');});
  await page.locator('#modal [data-action=add-cart]').click();await page.locator('#modal [data-action="v6-cart-plus"]').click();
  assert.equal(await page.locator('#modal output').innerText(),'2');assert.equal(await page.evaluate(()=>__qaDialog.isConnected),true);
  const focused=await page.evaluate(()=>({inModal:!!document.activeElement?.closest('#modal'),action:document.activeElement?.dataset.action}));
  assert.equal(focused.inModal,true);assert.equal(focused.action,'v6-cart-plus');return {id,focused};
 });
 await test('Donation preserves consent checkbox and custom input state',async()=>{
  await route('donate');await page.locator('[name=agree]').check();await page.evaluate(()=>{window.__qaDonation=document.querySelector('form[data-form="create-payment"]');});
  await page.locator('[data-action="donate-pack"][data-value="500"]').click();await page.locator('[data-action="payment-method"][data-method="card"]').click();
  assert.equal(await page.locator('[name=agree]').isChecked(),true);assert.equal(await page.evaluate(()=>__qaDonation.isConnected),true);
  await page.locator('#donation-amount').fill('357');assert.equal(await page.locator('#donate-total-cm').innerText(),'357 CM');assert.equal(await page.locator('[name=agree]').isChecked(),true);
  await page.locator('#donation-amount').fill('0');assert.equal(await page.locator('form[data-form="create-payment"] [type=submit]').isDisabled(),true);
  await page.locator('#donation-amount').fill('357');assert.equal(await page.locator('form[data-form="create-payment"] [type=submit]').isEnabled(),true);
  return {amount:await page.locator('#donate-total-cm').innerText(),method:await page.locator('#donate-method').innerText(),checkbox:true};
 });
 await test('Map renders all three real dimensions and coordinate navigation',async()=>{
  await route('map');const worlds=[];
  for(const id of ['overworld','nether','end']){await page.locator('[data-action="v6-map-world"][data-world="'+id+'"]').click();await page.waitForTimeout(300);
   worlds.push(await page.evaluate(()=>{const c=document.querySelector('#v6-map-canvas'),ctx=c.getContext('2d'),d=ctx.getImageData(0,0,c.width,c.height).data;let nonempty=0;const colors=new Set();for(let i=0;i<d.length;i+=4000){colors.add(d[i]+','+d[i+1]+','+d[i+2]);if(d[i]+d[i+1]+d[i+2]>75)nonempty++;}return {world:document.querySelector('.v6-world-tabs .active')?.dataset.world,width:c.width,height:c.height,colors:colors.size,nonempty,position:document.querySelector('.v6-map-position')?.innerText};}));
   await page.screenshot({path:'/tmp/copimine-visual/v6-desktop-map-'+id+'.png',animations:'disabled'});
  }
  for(const world of worlds)assert.ok(world.colors>8,JSON.stringify(world));
  await page.locator('#v6-map-goto [name=x]').fill('100');await page.locator('#v6-map-goto [name=z]').fill('200');await page.locator('#v6-map-goto [type=submit]').click();await page.waitForTimeout(80);
  assert.match(await page.locator('.v6-map-position').innerText(),/100.*200/);return worlds;
 });
 await test('Mobile dialog and map controls fit viewport',async()=>{
  await page.setViewportSize({width:390,height:844});await route('map');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),390);
  const canvas=await page.locator('#v6-map-canvas').boundingBox();assert.ok(canvas.height>=460);await page.locator('[data-action="v6-map-world"][data-world="nether"]').click();await page.waitForTimeout(180);await page.screenshot({path:'/tmp/copimine-visual/v6-mobile-map-nether.png',fullPage:true,animations:'disabled'});
  await page.locator('[data-action="v6-demo-admin"]').click();await page.waitForURL(/#admin\/players/);await page.waitForTimeout(120);await page.keyboard.press('Control+k');await page.locator('#v6-search-input').fill('бан');await page.waitForTimeout(120);const box=await page.locator('#modal').boundingBox();assert.ok(box.width<=390&&box.x>=0);await page.screenshot({path:'/tmp/copimine-visual/v6-mobile-search.png',animations:'disabled'});return {canvas,dialog:box};
 });
 await fs.writeFile('/tmp/copimine-visual/v6-interactions.json',JSON.stringify(report,null,2));
 console.log(JSON.stringify({passed:report.tests.filter(x=>x.pass).length,total:report.tests.length,errors:report.errors}));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
