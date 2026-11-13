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
await H.click('#playBtn');await H.click('[data-mode="objectives"]');await H.click('#mcStart');await H.waitForTimeout(5500);
await H.evaluate(()=>{const P=__dw.P;P.hp=P.maxHp=99999});
const gob=await Gp.evaluate(()=>({ob:!!__dw.ob,sc:__dw.ob&&__dw.ob.sc,wave:document.querySelector('#waveno').textContent,left:document.querySelector('#left').textContent,targets:__dw.objTargets().length}));
const hob=await H.evaluate(()=>({sc:__dw.ob.sc,i:__dw.ob.i,k:__dw.objKind().k,targets:__dw.objTargets().length}));
console.log('guest',JSON.stringify(gob),'host',JSON.stringify(hob));
// guest walks onto the first collect item
const t=await H.evaluate(()=>__dw.objTargets()[0]);
await Gp.evaluate(([x,y])=>{const P=__dw.P;P.hp=P.maxHp=99999;P.x=x;P.y=y},[t.x,t.y]);
await H.waitForTimeout(2500);
console.log('after guest pickup host',JSON.stringify(await H.evaluate(()=>({i:__dw.ob.i,done:__dw.ob.pts.filter(p=>p.done).length,left:document.querySelector('#left').textContent}))),'guest',JSON.stringify(await Gp.evaluate(()=>document.querySelector('#left').textContent)));
console.log('msgs toG',toG,'toH',toH,'KB/s to guest',(bytes/1024/12).toFixed(1));
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
