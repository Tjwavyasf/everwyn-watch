// Wardrobe screen with the real sprite files (served from disk through Playwright routing): every tab, randomize, and the look in game.
// Usage: node wardrobe_http.js /path/to/dead-wave
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const ROOT=require('path').resolve(process.argv[2]||'.');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844}});const errs=[];pg.on('pageerror',e=>errs.push(e.stack));pg.on('console',m=>{if(m.type()!=='log')errs.push(m.text())});
await pg.route('http://dw.test/**',r=>{const u=new URL(r.request().url());let p=path.join(ROOT,decodeURIComponent(u.pathname));if(p.endsWith('/'))p+='index.html';
  if(!fs.existsSync(p))return r.fulfill({status:404,body:''});const ct=p.endsWith('.png')?'image/png':p.endsWith('.json')?'application/json':'text/html';r.fulfill({status:200,body:fs.readFileSync(p),contentType:ct})});
await pg.goto('http://dw.test/');await pg.waitForTimeout(2500);
await pg.click('#charBtn');await pg.waitForTimeout(1500);await pg.screenshot({path:'w1.png'});
for(let t=0;t<6;t++){const tb=await pg.$$('.wtabs button');await tb[t].click();await pg.waitForTimeout(150);
  for(let k=0;k<3;k++){const n=(await pg.$$('#opts .opt button')).length;for(let i=1;i<n;i+=2){const all=await pg.$$('#opts .opt button');if(all[i])await all[i].click()}}
  await pg.waitForTimeout(800);await pg.screenshot({path:'w_tab'+t+'.png'})}
for(let i=0;i<4;i++){await pg.click('#cRand');await pg.waitForTimeout(1200);await pg.locator('#cprev').screenshot({path:'r'+i+'.png'})}
await pg.click('#cDone');await pg.click('#playBtn');await pg.click('#mcStart');await pg.waitForTimeout(2500);await pg.screenshot({path:'game.png'});
console.log(await pg.evaluate(()=>JSON.stringify(window.__dw.P.look.sheet)));
console.log('ERR',errs.join('\n'));await b.close()})();
