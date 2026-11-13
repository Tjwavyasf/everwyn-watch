// Card lobby / mode select: opens from PLAY, picking cards, starting a match. Screenshots in portrait and landscape.
// Usage: node cards.js /path/to/dead-wave   (serves the folder through Playwright routing so the sprite files load)
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const ROOT=path.resolve(process.argv[2]||'.');
(async()=>{const b=await chromium.launch();const errs=[];
for(const[name,w,h]of[['portrait',390,844],['landscape',844,390]]){
  const pg=await b.newPage({viewport:{width:w,height:h},deviceScaleFactor:2});pg.on('pageerror',e=>errs.push(name+': '+e.stack));
  await pg.route('http://dw.test/**',r=>{const u=new URL(r.request().url());let p=path.join(ROOT,decodeURIComponent(u.pathname));if(p.endsWith('/'))p+='index.html';
    if(!fs.existsSync(p))return r.fulfill({status:404,body:''});const ct=p.endsWith('.png')?'image/png':p.endsWith('.json')?'application/json':'text/html';r.fulfill({status:200,body:fs.readFileSync(p),contentType:ct})});
  await pg.goto('http://dw.test/');await pg.waitForTimeout(2500);
  await pg.click('#playBtn');await pg.waitForTimeout(800);
  await pg.click('[data-mode="horde"]');await pg.click('[data-fac="robots"]');await pg.click('[data-map="cyber"]');await pg.waitForTimeout(300);
  await pg.evaluate(()=>document.querySelector('#modeScr').scrollTop=0);await pg.screenshot({path:'cards_'+name+'.png'});
  console.log(name,'banner',await pg.textContent('#mcBanner'),'|',JSON.stringify(await pg.evaluate(()=>[__dw.meta.mode,__dw.meta.fac,__dw.meta.map])));
  await pg.click('#mcStart');await pg.waitForTimeout(1500);
  console.log(name,'started',JSON.stringify(await pg.evaluate(()=>({mode:__dw.G.mode,gm:__dw.G.gm,map:__dw.TH.id,fac:__dw.G.fac}))));
  await pg.close()}
console.log('ERR',errs.join('\n'));await b.close()})();
