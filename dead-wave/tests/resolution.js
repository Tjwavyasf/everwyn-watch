// Resolution: deploy from the home screen, then rotate the phone. Checks the game zoom is restored after the home scene,
// the canvas keeps the screen's shape after each rotation, and the HUD panels don't overlap. Usage: node resolution.js /path/to/dead-wave
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const ROOT=path.resolve(process.argv[2]||'.');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844},deviceScaleFactor:3,hasTouch:true,isMobile:true});const errs=[];pg.on('pageerror',e=>errs.push(e.stack));
await pg.route('http://dw.test/**',r=>{const u=new URL(r.request().url());let p=path.join(ROOT,decodeURIComponent(u.pathname));if(p.endsWith('/'))p+='index.html';
  if(!fs.existsSync(p))return r.fulfill({status:404,body:''});const ct=p.endsWith('.png')?'image/png':p.endsWith('.json')?'application/json':'text/html';r.fulfill({status:200,body:fs.readFileSync(p),contentType:ct})});
await pg.goto('http://dw.test/');await pg.waitForTimeout(3000);
const m=()=>pg.evaluate(()=>{const c=document.querySelector('#cv'),w=innerWidth,h=innerHeight,wb=document.querySelector('#wavebox').getBoundingClientRect(),vt=document.querySelector('#vitals').getBoundingClientRect();
  return{screen:w+'x'+h,canvas:c.width+'x'+c.height,shapeErr:+Math.abs(c.width/c.height-w/h).toFixed(3),pxPerWorld:+(w/c.width).toFixed(2),overlap:vt.top<wb.bottom&&vt.left<wb.right&&vt.right>wb.left&&vt.bottom>wb.top}});
console.log('home',JSON.stringify(await m()));
await pg.click('#deployBtn');await pg.waitForTimeout(1500);console.log('game portrait',JSON.stringify(await m()));await pg.screenshot({path:'res_portrait.png'});
await pg.setViewportSize({width:844,height:390});await pg.waitForTimeout(800);console.log('game landscape',JSON.stringify(await m()));await pg.screenshot({path:'res_landscape.png'});
await pg.setViewportSize({width:390,height:844});await pg.waitForTimeout(800);console.log('back to portrait',JSON.stringify(await m()));
console.log('ERR',errs.join('\n'));await b.close()})();
