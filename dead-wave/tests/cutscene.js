// Story intros: plays the Survival intro (screenshot of each shot), checks the world is frozen while it plays and runs
// after it ends; then a Horde/Robots/Cyberpunk intro skipped with the button. Usage: node cutscene.js /path/to/dead-wave
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const ROOT=path.resolve(process.argv[2]||'.');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844},deviceScaleFactor:2});const errs=[];pg.on('pageerror',e=>errs.push(e.stack));
await pg.route('http://dw.test/**',r=>{const u=new URL(r.request().url());let p=path.join(ROOT,decodeURIComponent(u.pathname));if(p.endsWith('/'))p+='index.html';
  if(!fs.existsSync(p))return r.fulfill({status:404,body:''});const ct=p.endsWith('.png')?'image/png':p.endsWith('.json')?'application/json':'text/html';r.fulfill({status:200,body:fs.readFileSync(p),contentType:ct})});
await pg.goto('http://dw.test/?story=1');await pg.waitForTimeout(2500);
await pg.click('#playBtn');await pg.click('[data-mode="survival"]');await pg.click('[data-fac="zombies"]');await pg.click('[data-map="suburb"]');await pg.click('#mcStart');
const st=()=>pg.evaluate(()=>({on:__dw.CUT.on,shot:__dw.CUT.i,text:document.querySelector('#cutText').textContent,breakT:+__dw.G.breakT.toFixed(1),hud:getComputedStyle(document.querySelector('#hud')).display}));
for(let i=0;i<4;i++){await pg.waitForTimeout(i===0?3200:600);const s=await st();await pg.screenshot({path:`cut_${i}.png`});console.log('shot',JSON.stringify(s));
  await pg.waitForTimeout(2500);await pg.mouse.click(200,700);await pg.waitForTimeout(150);await pg.mouse.click(200,700)}
await pg.waitForTimeout(5500);const end=await st();console.log('after intro',JSON.stringify(end));
const b0=end.breakT;await pg.waitForTimeout(1500);console.log('world running after intro:',(await st()).breakT<b0);
// second match, skipped
await pg.evaluate(()=>__dw.togglePause());await pg.click('#quitBtn');await pg.waitForTimeout(500);await pg.click('#overMenu');await pg.waitForTimeout(300);
await pg.click('#playBtn');await pg.click('[data-mode="horde"]');await pg.click('[data-fac="robots"]');await pg.click('[data-map="cyber"]');await pg.click('#mcStart');
await pg.waitForTimeout(9000);await pg.screenshot({path:'cut_robots.png'});console.log('robots intro',JSON.stringify(await st()));
await pg.click('#cutSkip');await pg.waitForTimeout(400);console.log('after skip',JSON.stringify(await st()));
console.log('ERR',errs.join('\n'));await b.close()})();
