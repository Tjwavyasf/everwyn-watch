const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const ctx=await b.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
const H=await ctx.newPage(),Gp=await ctx.newPage();const errs=[];
for(const [n,pg] of [['H',H],['G',Gp]]){pg.on('pageerror',e=>errs.push(n+': '+e.stack));pg.on('console',m=>{if(m.type()==='error')errs.push(n+' console: '+m.text())})}
const url='file://'+process.argv[2];await H.goto(url);await Gp.goto(url);await H.waitForTimeout(1200);
let toG=0,toH=0,bytes=0;
await H.exposeFunction('__toGuest',o=>{toG++;bytes+=JSON.stringify(o).length;Gp.evaluate(m=>window.__dw.guestRecv(m),o).catch(()=>{})});
await Gp.exposeFunction('__toHost',o=>{toH++;H.evaluate(m=>window.__dw.hostRecv(1,m),o).catch(()=>{})});
await H.evaluate(()=>{const d=window.__dw;d.NET.role='host';d.NET.code='TESTX';const conn={open:true,send:o=>window.__toGuest(o)};d.NET.conns.set(1,conn);d.REMOTES.set(1,{id:1,col:1,name:'Blue',live:false,x:0,y:0,tx:0,ty:0,aim:0,face:1,moving:false,run:false,roll:false,hp:100,maxHp:100,gun:'pistol',dead:false,anim:0,r:6})});
await Gp.evaluate(()=>{const d=window.__dw;d.NET.role='guest';d.NET.code='TESTX';d.NET.hostConn={open:true,send:o=>window.__toHost(o)};d.NET.hostConn.send({t:'hello',v:1})});
await H.waitForTimeout(400);
await H.click('#playBtn');await H.waitForTimeout(1500);
const gs=await Gp.evaluate(()=>{const d=window.__dw;return{mode:d.G.mode,seed:d.G.seed,px:d.P&&Math.round(d.P.x),drops:d.DROPS.length,others:d.OTHERS.size}});
const hs=await H.evaluate(()=>{const d=window.__dw;return{seed:d.G.seed,drops:d.DROPS.length,remote:[...d.REMOTES.values()].map(r=>[r.live,Math.round(r.x),Math.round(r.y)])}});
console.log('guest',JSON.stringify(gs),'host',JSON.stringify(hs));
// same map?
console.log('map equal',await H.evaluate(()=>Array.from(window.__dw.tile.slice(0,20000)).join('').length)===await Gp.evaluate(()=>Array.from(window.__dw.tile.slice(0,20000)).join('').length), (await H.evaluate(()=>{let h=0;for(const v of window.__dw.tile)h=(h*31+v)|0;return h}))===(await Gp.evaluate(()=>{let h=0;for(const v of window.__dw.tile)h=(h*31+v)|0;return h})));
// start wave, guest shoots
await H.evaluate(()=>{const d=window.__dw;d.P.hp=99999;d.P.maxHp=99999;d.G.breakT=0});
await H.evaluate(()=>{const d=window.__dw;const r=d.REMOTES.get(1);for(let i=0;i<6;i++)d.spawnZ('walker',r.x+60+i*6,r.y-40+i*12)});
for(let i=0;i<40;i++){await H.waitForTimeout(250);
  await Gp.evaluate(()=>{const d=window.__dw;if(!document.querySelector('#levelup').hidden)d.choose(0);let best=null,bd=1e9;for(const z of d.Z){const q=(z.x-d.P.x)**2+(z.y-d.P.y)**2;if(q<bd){bd=q;best=z}}if(best&&bd<250*250){d.P.aim=Math.atan2(best.y-d.P.y,best.x-d.P.x);d.fire()}if(Math.random()<.1)d.shove()});
  await H.evaluate(()=>{const d=window.__dw;d.P.hp=99999;if(!document.querySelector('#levelup').hidden)d.choose(0)});
  if(i===30){await H.screenshot({path:'m1.png'});await Gp.screenshot({path:'m2.png'})}}
console.log(JSON.stringify(await Gp.evaluate(()=>{const d=window.__dw;return{wave:d.G.wave,ghosts:d.Z.length,kills:d.G.kills,hp:Math.round(d.P.hp),lvl:d.P.lvl,xp:d.P.xp,ammo:d.P.slots[0].mag,dead:d.P.dead}})),JSON.stringify(await H.evaluate(()=>{const d=window.__dw;return{wave:d.G.wave,z:d.Z.length,kills:d.G.kills,lvl:d.P.lvl}})));
await H.evaluate(()=>{const d=window.__dw;const r=d.REMOTES.get(1);for(let i=0;i<8;i++)d.spawnZ('brute',r.x+4,r.y+4)});
await H.waitForTimeout(6000);
console.log('after brutes',JSON.stringify(await Gp.evaluate(()=>({hp:Math.round(window.__dw.P.hp),dead:window.__dw.P.dead,left:document.querySelector('#left').textContent}))),JSON.stringify(await H.evaluate(()=>[...window.__dw.REMOTES.values()].map(r=>r.dead))));
await H.evaluate(()=>{const d=window.__dw;for(const z of d.Z)z.dead=true;d.G.spawnLeft=0;d.G.bossPending=0});await H.waitForTimeout(1500);
console.log('after clear',JSON.stringify(await Gp.evaluate(()=>({hp:Math.round(window.__dw.P.hp),dead:window.__dw.P.dead,state:window.__dw.G.state,planks:window.__dw.P.planks}))));
console.log('msgs toG',toG,'toH',toH,'KB/s to guest',(bytes/1024/12).toFixed(1));
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
