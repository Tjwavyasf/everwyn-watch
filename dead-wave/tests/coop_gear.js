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
console.log('guest dead before?',await Gp.evaluate(()=>window.__dw.P.dead));await Gp.evaluate(()=>{const d=window.__dw;d.P.slots[1]={g:'smg',mag:5,res:20,att:{optic:'scope',mag:'extmag'}};d.P.hp=1;d.P.inv=0});
await H.waitForTimeout(500);
await H.evaluate(()=>{const d=window.__dw;const r=d.REMOTES.get(1);for(let i=0;i<4;i++)d.spawnZ('brute',r.x+4,r.y+4)});
for(let i=0;i<30;i++){await H.waitForTimeout(300);if(await Gp.evaluate(()=>window.__dw.P.dead))break;await Gp.evaluate(()=>{window.__dw.P.hp=Math.min(window.__dw.P.hp,1);window.__dw.P.inv=0})}
await H.waitForTimeout(800);
console.log('dropped gun on host',JSON.stringify(await H.evaluate(()=>{const d=window.__dw,r=d.REMOTES.get(1);return d.DROPS.filter(o=>o.type==='gun'&&Math.hypot(o.x-r.x,o.y-r.y)<90).map(o=>[o.g,o.att,Math.round(Math.hypot(o.x-r.x,o.y-r.y))])})),
  'guest sees',JSON.stringify(await Gp.evaluate(()=>window.__dw.DROPS.filter(o=>o.type==='gun'&&o.g==='smg'&&o.att&&o.att.optic).map(o=>o.att))));
// host clears zombies, walks to the guest and uses a first aid kit
await H.evaluate(()=>{const d=window.__dw;const r=d.REMOTES.get(1);for(const z of d.Z)if(Math.hypot(z.x-r.x,z.y-r.y)<200)z.dead=true;d.G.spawnLeft=60;d.P.x=r.x+6;d.P.y=r.y;d.P.aid=1;d.P.slots[1]={g:'ar',mag:30,res:150}});
await H.waitForTimeout(300);
console.log('host use label:',await H.textContent('#useBtn'));
await H.evaluate(()=>window.__dw.useAid());await H.waitForTimeout(3200);
console.log('after revive guest',JSON.stringify(await Gp.evaluate(()=>({dead:window.__dw.P.dead,hp:Math.round(window.__dw.P.hp)}))),'host aid',await H.evaluate(()=>window.__dw.P.aid),'banner',await Gp.textContent('#banT'));
// guest grabs the gun back
await Gp.evaluate(()=>{const d=window.__dw;const g=d.DROPS.find(o=>o.type==='gun'&&o.g==='smg'&&o.att&&o.att.optic);if(g){d.P.x=g.x;d.P.y=g.y}});await H.waitForTimeout(2500);
console.log('dbg',JSON.stringify(await Gp.evaluate(()=>{const d=window.__dw;return{p:[Math.round(d.P.x),Math.round(d.P.y),d.P.dead],g:d.DROPS.filter(o=>o.type==='gun').map(o=>[o.g,Math.round(o.x),Math.round(o.y),o.req,+o.t.toFixed(1)]).filter(a=>Math.hypot(a[1]-d.P.x,a[2]-d.P.y)<40)}})));
console.log('guest gun back',JSON.stringify(await Gp.evaluate(()=>window.__dw.P.slots.map(s=>[s.g,s.att||{}]))));
console.log('msgs toG',toG,'toH',toH,'KB/s to guest',(bytes/1024/12).toFixed(1));
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
