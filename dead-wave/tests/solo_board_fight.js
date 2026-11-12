const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844},deviceScaleFactor:2,hasTouch:true});
const errs=[];pg.on('pageerror',e=>errs.push(e.stack));pg.on('console',m=>{if(m.type()==='error')errs.push(m.text())});
const t0=Date.now();await pg.goto('file://'+process.argv[2]);await pg.waitForTimeout(1500);console.log('load',Date.now()-t0);
await pg.screenshot({path:'k1.png'});
await pg.click('#playBtn');await pg.waitForTimeout(800);
await pg.screenshot({path:'k2.png'});
// walk into nearest house door: teleport next to a door to test boarding
const info=await pg.evaluate(()=>{const d=window.__dw,P=d.P;let best=null,bd=1e9;for(const o of d.OPENS){if(o.kind!=='door'||o.vert)continue;const x=o.tx*16+8,y=o.ty*16+8,dd=(x-P.x)**2+(y-P.y)**2;if(dd<bd){bd=dd;best=o}}
  P.x=best.tx*16+8;P.y=best.ty*16+8-16;return {door:[best.tx,best.ty],house:best.house,planks:P.planks}});
await pg.waitForTimeout(300);
const use=await pg.evaluate(()=>document.querySelector('#useBtn').hidden?null:document.querySelector('#useBtn').textContent);console.log('use',use,JSON.stringify(info));
await pg.evaluate(()=>window.__dw.doUse());await pg.waitForTimeout(900);
console.log(JSON.stringify(await pg.evaluate(()=>{const d=window.__dw;const o=d.OPENS.find(o=>o.kind==='door'&&d.tAt(d.P.x,d.P.y+16)===o.i);return{boards:o&&o.boards,planks:d.P.planks,inside:d.P.inside}})));
await pg.screenshot({path:'k3.png'});
// start wave and spawn zombies, fight via fire
await pg.evaluate(()=>{const d=window.__dw;d.P.hp=9999;d.P.maxHp=9999;d.startWave(1)});
for(let i=0;i<30;i++){await pg.waitForTimeout(300);await pg.evaluate(()=>{const d=window.__dw;if(!document.querySelector('#levelup').hidden)d.choose(0);d.P.hp=9999;
  let best=null,bd=1e9;for(const z of d.Z){const q=(z.x-d.P.x)**2+(z.y-d.P.y)**2;if(q<bd){bd=q;best=z}}if(best){d.P.aim=Math.atan2(best.y-d.P.y,best.x-d.P.x);for(let k=0;k<3;k++)d.fire()}if(Math.random()<.2)d.shove()})}
await pg.screenshot({path:'k4.png'});
console.log(JSON.stringify(await pg.evaluate(()=>{const d=window.__dw;return{wave:d.G.wave,z:d.Z.length,kills:d.G.kills,ammo:d.P.slots.map(s=>s.g+':'+s.mag+'/'+s.res),boarded:d.OPENS.filter(o=>o.boards>0).map(o=>o.boards+'/'+Math.round(o.hp))}})));
const fps=await pg.evaluate(()=>new Promise(r=>{let n=0;const s=performance.now();const f=()=>{n++;if(performance.now()-s<2000)requestAnimationFrame(f);else r(n/2)};requestAnimationFrame(f)}));console.log('fps',fps);
console.log('ERR',errs.join('\n'));await b.close()})();
