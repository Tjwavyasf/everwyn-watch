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
await H.click('#playBtn');await H.click('#mcStart');await H.waitForTimeout(1500);
const gs=await Gp.evaluate(()=>{const d=window.__dw;return{mode:d.G.mode,seed:d.G.seed,px:d.P&&Math.round(d.P.x),drops:d.DROPS.length,others:d.OTHERS.size}});
const hs=await H.evaluate(()=>{const d=window.__dw;return{seed:d.G.seed,drops:d.DROPS.length,remote:[...d.REMOTES.values()].map(r=>[r.live,Math.round(r.x),Math.round(r.y)])}});
console.log('guest',JSON.stringify(gs),'host',JSON.stringify(hs));
// same map?
console.log('map equal',await H.evaluate(()=>Array.from(window.__dw.tile.slice(0,20000)).join('').length)===await Gp.evaluate(()=>Array.from(window.__dw.tile.slice(0,20000)).join('').length), (await H.evaluate(()=>{let h=0;for(const v of window.__dw.tile)h=(h*31+v)|0;return h}))===(await Gp.evaluate(()=>{let h=0;for(const v of window.__dw.tile)h=(h*31+v)|0;return h})));
// start wave, guest shoots
await H.evaluate(()=>{const d=window.__dw;d.P.hp=99999;d.P.maxHp=99999;d.G.breakT=0});
await Gp.evaluate(()=>{const d=window.__dw;d.P.slots[1]={g:'shotgun',mag:5,res:20};d.P.planks=5;d.P.gren=2;d.P.hp=1;d.P.inv=0});
await H.waitForTimeout(500);
await H.evaluate(()=>{const d=window.__dw;const r=d.REMOTES.get(1);for(let i=0;i<4;i++)d.spawnZ('brute',r.x+4,r.y+4)});
for(let i=0;i<30;i++){await H.waitForTimeout(300);if(await Gp.evaluate(()=>window.__dw.P.dead))break;await Gp.evaluate(()=>{window.__dw.P.hp=Math.min(window.__dw.P.hp,1);window.__dw.P.inv=0})}
await H.waitForTimeout(800);
console.log('guest after down',JSON.stringify(await Gp.evaluate(()=>{const d=window.__dw;return{dead:d.P.dead,slots:d.P.slots.map(s=>s.g),planks:d.P.planks,gren:d.P.gren,banner:document.querySelector('#banS').textContent}})));
console.log('host drops near guest',JSON.stringify(await H.evaluate(()=>{const d=window.__dw,r=d.REMOTES.get(1);return d.DROPS.filter(o=>!o.got&&Math.hypot(o.x-r.x,o.y-r.y)<60&&['gun','planks','gren'].includes(o.type)).map(o=>o.type+(o.g?':'+o.g:'')+'x'+o.v)})));
console.log('guest sees drops',JSON.stringify(await Gp.evaluate(()=>window.__dw.DROPS.filter(o=>['gun','planks','gren'].includes(o.type)).map(o=>o.type+(o.g?':'+o.g:'')))));
// shared pause: guest pauses
await Gp.evaluate(()=>window.__dw.togglePause());await H.waitForTimeout(400);
const t0=await H.evaluate(()=>window.__dw.G.time);await H.waitForTimeout(800);
console.log('paused by guest',JSON.stringify(await H.evaluate(()=>({p:window.__dw.G.paused,scr:!document.querySelector('#pause').hidden,by:document.querySelector('#pauseBy').textContent}))),JSON.stringify(await Gp.evaluate(()=>({p:window.__dw.G.paused,scr:!document.querySelector('#pause').hidden}))),'host time frozen',(await H.evaluate(()=>window.__dw.G.time))===t0);
await H.screenshot({path:'pz_host.png'});
await H.evaluate(()=>window.__dw.togglePause());await H.waitForTimeout(400);
console.log('resumed by host',JSON.stringify(await H.evaluate(()=>window.__dw.G.paused)),JSON.stringify(await Gp.evaluate(()=>({p:window.__dw.G.paused,scr:!document.querySelector('#pause').hidden}))));
await H.evaluate(()=>{const d=window.__dw;for(const z of d.Z)z.dead=true;d.G.spawnLeft=0;d.G.bossPending=0});await H.waitForTimeout(1500);
console.log('after clear',JSON.stringify(await Gp.evaluate(()=>({dead:window.__dw.P.dead,state:window.__dw.G.state,slots:window.__dw.P.slots.map(s=>s.g)}))));
console.log('msgs toG',toG,'toH',toH,'KB/s to guest',(bytes/1024/12).toFixed(1));
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
