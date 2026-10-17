const fs=require('node:fs/promises');
const {chromium:pw}=require('playwright');
(async()=>{
 const {default:chromium}=await import('/tmp/copimine-browser-runtime/node_modules/@sparticuz/chromium/build/index.js');
 const browser=await pw.launch({args:chromium.args,executablePath:await chromium.executablePath(),headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1.5,reducedMotion:'reduce'});
 const errors=[], failed=[];
 page.on('pageerror',error=>errors.push(error.message));
 page.on('response',res=>{if(res.status()>=500)failed.push({status:res.status(),url:res.url()})});
 const base=process.env.COPIMINE_QA_URL||'http://127.0.0.1:4175/index.html';
 await page.goto(base,{waitUntil:'networkidle'});
 await page.screenshot({path:'/tmp/copimine-visual/v6-desktop-cookie.png',fullPage:false,animations:'disabled'});
 const consent=page.locator('[data-action="v6-cookies-essential"]');
 if(await consent.count())await consent.click();
 const admin=page.locator('[data-action="v6-demo-admin"]');
 if(await admin.count())await admin.click();
 else throw new Error('Demo admin action missing; need ready integration or authorized server login.');
 const routes=[['profile','admin/players?player=demo-001&sub=profile'],['actions','admin/players?player=demo-001&sub=actions'],['sanctions','admin/players?player=demo-001&sub=sanctions'],['finance','admin/players?player=demo-001&sub=finance'],['analytics','admin/statistics'],['map','map'],['donate','donate'],['shop','shop'],['account','account']];
 const report={capturedAt:new Date().toISOString(),runs:[]};
 for(const [device,viewport]of [['desktop',{width:1440,height:1000}],['mobile',{width:390,height:844}]]){
  await page.setViewportSize(viewport);
  await page.locator('[data-action="v6-demo-admin"]').click();
  for(const [name,route]of routes){
   if(name==='account')await page.locator('[data-action="demo-player"]').click();
   const before=errors.length;
   await page.goto(base+'#'+route,{waitUntil:'networkidle'});
   await page.waitForTimeout(80);
   const facts=await page.evaluate(()=>{
    const visible=el=>{const r=el.getBoundingClientRect();return r.width>0&&r.height>0&&r.bottom>0&&r.top<innerHeight;};
    return {title:document.title,heading:[...document.querySelectorAll('h1,h2')].map(n=>n.textContent.trim()).slice(0,8),viewportWidth:innerWidth,documentWidth:document.documentElement.scrollWidth,bodyWidth:document.body.scrollWidth,
    brokenImages:[...document.images].filter(img=>visible(img)&&img.complete&&img.naturalWidth===0).map(img=>img.getAttribute('src')),
    inventorySlots:document.querySelectorAll('.inventory-slot').length,enderSlots:document.querySelectorAll('.ender-slot,.v6-chest-slot').length,
    mainText:document.querySelector('main')?.innerText.slice(0,500)};
   });
   const path='/tmp/copimine-visual/v6-'+device+'-'+name+'.png';
   await page.screenshot({path,fullPage:true,animations:'disabled'});
   report.runs.push({device,route,path,...facts,errors:errors.slice(before)});
  }
 }
 report.errors=errors;report.serverFailures=failed;
 await fs.writeFile('/tmp/copimine-visual/v6-report.json',JSON.stringify(report,null,2));
 console.log(JSON.stringify(report));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});

