const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844},deviceScaleFactor:2});const errs=[];pg.on('pageerror',e=>errs.push(e.stack));pg.on('console',m=>{if(m.type()!=='log')errs.push(m.type()+': '+m.text())});
await pg.goto('http://127.0.0.1:8765/index.html');await pg.waitForTimeout(2000);
console.log('sheets',await pg.evaluate(()=>JSON.stringify({ready:window.__dw.SHEETS.ready,chars:Object.keys(window.__dw.SHEETS.chars),err:window.__dw.SHEETS.errors})));
await pg.click('#charBtn');await pg.waitForTimeout(600);await pg.screenshot({path:'s_custom.png'});await pg.click('#cDone');
await pg.click('#playBtn');await pg.waitForTimeout(500);
await pg.evaluate(()=>{const d=window.__dw;d.P.hp=99999;d.P.maxHp=99999;for(let i=0;i<8;i++)d.spawnZ(['walker','runner','spitter','bloater','brute','walker','walker','walker'][i],d.P.x-70+i*20,d.P.y-60+(i%2)*20)});
for(let i=0;i<12;i++){await pg.waitForTimeout(200);await pg.evaluate(()=>{const d=window.__dw;if(!document.querySelector('#levelup').hidden)d.choose(0);d.P.hp=99999;let best=null,bd=1e9;for(const z of d.Z){const q=(z.x-d.P.x)**2+(z.y-d.P.y)**2;if(q<bd){bd=q;best=z}}if(best){d.P.aim=Math.atan2(best.y-d.P.y,best.x-d.P.x);d.fire()}})}
await pg.screenshot({path:'s_play.png'});
console.log(await pg.evaluate(()=>JSON.stringify({z:window.__dw.Z.length,corpses:window.__dw.CORPSES.length,kills:window.__dw.G.kills})));
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
