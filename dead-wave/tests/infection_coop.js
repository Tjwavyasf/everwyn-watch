// Infection with 5 players (host + 4 guests over fake connections): opening scene, patient zero, carrier, push stun,
// infecting, an emergency meeting, and the end of the round. Usage: node infection_coop.js /path/to/index.html
const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const ctx=await b.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
const N=4,H=await ctx.newPage(),Gs=[];for(let i=0;i<N;i++)Gs.push(await ctx.newPage());const errs=[];
for(const [n,pg] of [['H',H],...Gs.map((g,i)=>['G'+(i+1),g])]){pg.on('pageerror',e=>errs.push(n+': '+e.stack))}
const url='file://'+process.argv[2];await H.goto(url);for(const g of Gs)await g.goto(url);await H.waitForTimeout(1200);
for(let i=0;i<N;i++){const id=i+1,G=Gs[i];
  await H.exposeFunction('__toGuest'+id,o=>{G.evaluate(m=>window.__dw.guestRecv(m),o).catch(()=>{})});
  await G.exposeFunction('__toHost',o=>{H.evaluate(([id,m])=>window.__dw.hostRecv(id,m),[id,o]).catch(()=>{})});
  await H.evaluate(id=>{const d=window.__dw;d.NET.role='host';d.NET.code='TESTX';d.NET.conns.set(id,{open:true,send:o=>window['__toGuest'+id](o)});
    d.REMOTES.set(id,{id,col:id,name:['Orange','Blue','Red','White','Green'][id],live:false,x:0,y:0,tx:0,ty:0,aim:0,face:1,moving:false,run:false,roll:false,hp:100,maxHp:100,gun:'pistol',dead:false,anim:0,r:6})},id);
  await G.evaluate(()=>{const d=window.__dw;d.NET.role='guest';d.NET.code='TESTX';d.NET.hostConn={open:true,send:o=>window.__toHost(o)};d.NET.hostConn.send({t:'hello',v:1})})}
await H.waitForTimeout(500);
await H.click('#playBtn');await H.click('[data-mode="infection"]');await H.click('#mcStart');await H.waitForTimeout(2500);
const all=[H,...Gs];
const role=pg=>pg.evaluate(()=>{const f=window.__dw.inf;const me=window.__dw.NET.role==='guest'?window.__dw.NET.myId:0;return f?{me,ph:f.ph,pz:f.pz,r:f.ro[me],carry:!!f.iCarry,left:document.querySelector('#left').textContent}:null});
const rs=[];for(const pg of all)rs.push(await role(pg));console.log('intro',JSON.stringify(rs.map(r=>[r.me,r.ph,r.r,r.carry])),'pz',rs[0].pz);
const pos=await Promise.all(all.map(pg=>pg.evaluate(()=>[Math.round(window.__dw.P.x),Math.round(window.__dw.P.y)])));console.log('everyone in one house',JSON.stringify(pos));
await Gs[0].screenshot({path:'infc_intro.png'});
const host=await H.evaluate(()=>({pz:__dw.inf.pz,carrier:__dw.inf.carrier}));console.log('host knows carrier',host.carrier,'carrier count on clients',rs.filter(r=>r.carry).length);
// skip to the hunt
for(const [i,pg] of all.entries()){if(i===host.pz)continue;await pg.evaluate(k=>{const P=__dw.P;for(let n=0;n<600;n++){const a=Math.random()*6.28,r=90+k*25,x=P.x+Math.cos(a)*r,y=P.y+Math.sin(a)*r,t=__dw.tAt(x,y);if(t>=0&&__dw.tile[t]<10){P.x=x;P.y=y;break}}},i)}
await H.waitForTimeout(400);
await H.evaluate(()=>{__dw.inf.ph='run';__dw.inf.t=29.6});await H.waitForTimeout(1500);
const pzPage=all[host.pz];const victimId=[0,1,2,3,4].find(i=>i!==host.pz&&i!==host.carrier);const victim=all[victimId];
console.log('hunt',JSON.stringify(await role(pzPage)),'| survivor sees:',(await role(victim)).left);
// a survivor pushes patient zero
const pzPos=await pzPage.evaluate(()=>[__dw.P.x,__dw.P.y]);
await victim.evaluate(([x,y])=>{const P=__dw.P;P.x=x-12;P.y=y;P.aim=0;P.stam=100;P.shoveT=0;__dw.shove()},pzPos);await H.waitForTimeout(600);
console.log('dbg host',JSON.stringify(await H.evaluate(([v,pz])=>{const p=i=>i===0?__dw.P:__dw.REMOTES.get(i);return{v:[p(v).x|0,p(v).y|0],pz:[p(pz).x|0,p(pz).y|0],st:__dw.inf.st}},[victimId,host.pz])),'victim local',JSON.stringify(await victim.evaluate(()=>[__dw.P.x|0,__dw.P.y|0,__dw.P.shoveT])));
console.log('patient zero stunned:',await pzPage.evaluate(()=>+(__dw.P.stunT||0).toFixed(1)));
// patient zero catches the victim once the stun wears off
await H.evaluate(pz=>{const f=__dw.inf;f.st={}},host.pz);await pzPage.evaluate(()=>{__dw.P.stunT=0});
await H.waitForTimeout(200);
const vPos=await victim.evaluate(()=>[__dw.P.x,__dw.P.y]);await pzPage.evaluate(([x,y])=>{__dw.P.x=x+5;__dw.P.y=y},vPos);await H.waitForTimeout(900);
console.log('victim now:',JSON.stringify(await role(victim)),'banner',await victim.textContent('#banT'));
// meeting: a survivor calls it, survivors vote for the carrier
const caller=all[[0,1,2,3,4].find(i=>i!==host.pz&&i!==victimId)];
await caller.click('#meetBtn').catch(async()=>{await caller.evaluate(()=>{})});await H.waitForTimeout(600);
console.log('meeting open on all:',JSON.stringify(await Promise.all(all.map(pg=>pg.evaluate(()=>!document.querySelector('#meetScr').hidden)))));
await caller.screenshot({path:'infc_meet.png'});
const carrierName=await H.evaluate(c=>__dw.REMOTES.get(c)?__dw.REMOTES.get(c).name:'Orange',host.carrier);
for(const pg of all){await pg.evaluate(name=>{const bs=[...document.querySelectorAll('#meetList button')];const b=bs.find(x=>x.textContent.startsWith(name))||bs[bs.length-1];if(b&&!b.disabled)b.click()},carrierName)}
await H.waitForTimeout(1500);
console.log('after vote:',await H.textContent('#banT'),'|',await H.textContent('#banS'),'carrier role',await H.evaluate(c=>__dw.inf.ro[c],host.carrier));
// time runs out: survivors win
await H.evaluate(()=>{__dw.inf.t=299.5});await H.waitForTimeout(1500);
for(const [i,pg] of all.entries())console.log('end',i,await pg.textContent('#oTitle'),await pg.textContent('#oWave'));
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
