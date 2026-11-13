// Lobby: host + guest warm up in the lobby, the host changes match settings and starts, then returns everyone to the lobby.
const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const ctx=await b.newContext({viewport:{width:390,height:844},deviceScaleFactor:1});
const H=await ctx.newPage(),Gp=await ctx.newPage();const errs=[];
for(const [n,pg] of [['H',H],['G',Gp]]){pg.on('pageerror',e=>errs.push(n+': '+e.stack));pg.on('console',m=>{if(m.type()==='error')errs.push(n+' console: '+m.text())})}
const url='file://'+process.argv[2];await H.goto(url);await Gp.goto(url);await H.waitForTimeout(1200);
let toG=0,toH=0,bytes=0;
await H.exposeFunction('__toGuest',o=>{toG++;bytes+=JSON.stringify(o).length;Gp.evaluate(m=>window.__dw.guestRecv(m),o).catch(()=>{})});
await Gp.exposeFunction('__toHost',o=>{toH++;H.evaluate(m=>window.__dw.hostRecv(1,m),o).catch(()=>{})});
await H.evaluate(()=>{const d=window.__dw;d.NET.role='host';d.NET.code='TESTX';const conn={open:true,send:o=>window.__toGuest(o)};d.NET.conns.set(1,conn);d.REMOTES.set(1,{id:1,col:1,name:'Blue',live:false,x:0,y:0,tx:0,ty:0,aim:0,face:1,moving:false,run:false,roll:false,hp:100,maxHp:100,gun:'pistol',dead:false,anim:0,r:6})});
await H.evaluate(()=>{window.__dw.enterLobby()});await H.waitForTimeout(800);
await Gp.evaluate(()=>{const d=window.__dw;d.NET.role='guest';d.NET.code='TESTX';d.NET.hostConn={open:true,send:o=>window.__toHost(o)};d.NET.hostConn.send({t:'hello',v:1})});
await H.waitForTimeout(1500);
const st=pg=>pg.evaluate(()=>{const d=window.__dw;return{mode:d.G.mode,gm:d.G.gm,map:d.TH.id,fac:d.G.fac,racks:d.DROPS.filter(o=>o.type==='gun').length,bar:!document.querySelector('#lobbyBar').hidden,txt:document.querySelector('#lbbTxt').textContent,go:!document.querySelector('#lbbGo').hidden,wave:document.querySelector('#waveno').textContent}});
console.log('host lobby',JSON.stringify(await st(H)));console.log('guest lobby',JSON.stringify(await st(Gp)));
await H.screenshot({path:'lobby_host.png'});
// dying in the lobby: back in 3 seconds, keeps the gun
await H.evaluate(()=>{const P=window.__dw.P;P.slots[1]={g:'smg',mag:40,res:100};P.hp=1;P.inv=0;for(let i=0;i<3;i++)window.__dw.spawnZ('brute',P.x+5,P.y)});
await H.waitForTimeout(1500);const dead=await H.evaluate(()=>window.__dw.P.dead);await H.evaluate(()=>{for(const z of window.__dw.Z)z.dead=true;window.__dw.P.inv=9});await H.waitForTimeout(3000);
console.log('lobby death: was down',dead,'now',JSON.stringify(await H.evaluate(()=>({dead:window.__dw.P.dead,slots:window.__dw.P.slots.map(s=>s.g),over:!document.querySelector('#over').hidden}))));
// host picks Horde, Robots, Cyberpunk and starts
await H.evaluate(()=>{const m=window.__dw.meta;m.mode='survival';m.fac='zombies';m.map='suburb'});
await H.click('#lbbSet');await H.waitForTimeout(200);
const rowBtn=async(r,n)=>{for(let k=0;k<n;k++){const bs=await H.$$('#matchRows .opt');const b=(await bs[r].$$('button'))[1];await b.click()}};
await rowBtn(0,2);await rowBtn(1,1);await rowBtn(2,2);await H.screenshot({path:'lobby_settings.png'});await H.click('#matchDone');
await H.click('#lbbGo');await H.waitForTimeout(2000);
console.log('host match',JSON.stringify(await st(H)));console.log('guest match',JSON.stringify(await st(Gp)));
// end the match, back to the lobby
await H.evaluate(()=>window.__dw.togglePause());await H.click('#quitBtn');await H.waitForTimeout(800);
console.log('over: lobby button',await H.isVisible('#lobbyAgain'));await H.click('#lobbyAgain');await H.waitForTimeout(1500);
console.log('host back',JSON.stringify(await st(H)));console.log('guest back',JSON.stringify(await st(Gp)));
console.log('msgs toG',toG,'toH',toH,'KB/s to guest',(bytes/1024/12).toFixed(1));
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
