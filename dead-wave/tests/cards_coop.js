// Card lobby in co-op: the guest sees the host's picks live, can't change them, and goes in when the host starts.
const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const ctx=await b.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
const H=await ctx.newPage(),Gp=await ctx.newPage();const errs=[];
for(const [n,pg] of [['H',H],['G',Gp]]){pg.on('pageerror',e=>errs.push(n+': '+e.stack));pg.on('console',m=>{if(m.type()==='error')errs.push(n+' console: '+m.text())})}
const url='file://'+process.argv[2];await H.goto(url);await Gp.goto(url);await H.waitForTimeout(1200);
let toG=0,toH=0,bytes=0;
await H.exposeFunction('__toGuest',o=>{toG++;bytes+=JSON.stringify(o).length;Gp.evaluate(m=>window.__dw.guestRecv(m),o).catch(()=>{})});
await Gp.exposeFunction('__toHost',o=>{toH++;H.evaluate(m=>window.__dw.hostRecv(1,m),o).catch(()=>{})});
await H.evaluate(()=>{const d=window.__dw;d.NET.role='host';d.NET.code='TESTX';const conn={open:true,send:o=>window.__toGuest(o)};d.NET.conns.set(1,conn);d.REMOTES.set(1,{id:1,col:1,name:'Blue',live:false,x:0,y:0,tx:0,ty:0,aim:0,face:1,moving:false,run:false,roll:false,hp:100,maxHp:100,gun:'pistol',dead:false,anim:0,r:6})});
await H.evaluate(()=>{window.__dw.meta.mode='survival';window.__dw.meta.fac='zombies';window.__dw.meta.map='suburb'});
await H.click('#playBtn');await H.waitForTimeout(400);
await Gp.evaluate(()=>{const d=window.__dw;d.NET.role='guest';d.NET.code='TESTX';d.NET.hostConn={open:true,send:o=>window.__toHost(o)};d.NET.hostConn.send({t:'hello',v:1})});
await H.waitForTimeout(800);
const sel=pg=>pg.evaluate(()=>({open:!document.querySelector('#modeScr').hidden,on:[...document.querySelectorAll('#modeScr .mcard.on')].map(b=>b.querySelector('.ttl').textContent),start:!document.querySelector('#mcStart').hidden,wait:!document.querySelector('#mcWait').hidden,players:document.querySelector('#mcPlayers').textContent,name:document.querySelector('#mcName').textContent}));
console.log('host cards',JSON.stringify(await sel(H)));console.log('guest cards',JSON.stringify(await sel(Gp)));
await H.click('[data-mode="infection"]');await H.click('[data-map="forest"]');await H.waitForTimeout(400);
console.log('guest home after host picks',JSON.stringify(await Gp.evaluate(()=>({mode:document.querySelector('#hMode').textContent,facmap:document.querySelector('#hFacMap').textContent,deploy:document.querySelector('#deployBtn').textContent}))));
await Gp.evaluate(()=>{const b=document.querySelector('[data-mode="horde"]');if(b)b.click()});await H.waitForTimeout(200);
console.log('guest cannot change: host still',JSON.stringify(await H.evaluate(()=>window.__dw.meta.mode)));
await Gp.screenshot({path:'cards_guest.png'});
await H.click('#mcStart');await H.waitForTimeout(2500);
console.log('in match',JSON.stringify(await H.evaluate(()=>({gm:window.__dw.G.gm,map:window.__dw.TH.id}))),JSON.stringify(await Gp.evaluate(()=>({gm:window.__dw.G.gm,map:window.__dw.TH.id,cards:!document.querySelector('#modeScr').hidden}))));
console.log('msgs toG',toG,'toH',toH,'KB/s to guest',(bytes/1024/12).toFixed(1));
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
