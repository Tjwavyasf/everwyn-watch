// Home screen: the camp scene, challenges and deploy panel in landscape and portrait, a squad of 3 standing together
// (host + 2 guests over fake connections), and DEPLOY starting the match. Usage: node home.js /path/to/dead-wave
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const ROOT=path.resolve(process.argv[2]||'.');
const route=pg=>pg.route('http://dw.test/**',r=>{const u=new URL(r.request().url());let p=path.join(ROOT,decodeURIComponent(u.pathname));if(p.endsWith('/'))p+='index.html';
  if(!fs.existsSync(p))return r.fulfill({status:404,body:''});const ct=p.endsWith('.png')?'image/png':p.endsWith('.json')?'application/json':'text/html';r.fulfill({status:200,body:fs.readFileSync(p),contentType:ct})});
(async()=>{const b=await chromium.launch();const errs=[];
for(const[name,w,h]of[['landscape',844,390],['portrait',390,844]]){const pg=await b.newPage({viewport:{width:w,height:h},deviceScaleFactor:2});pg.on('pageerror',e=>errs.push(name+': '+e.stack));await route(pg);
  await pg.goto('http://dw.test/');await pg.waitForTimeout(3000);await pg.screenshot({path:'home_'+name+'.png'});
  console.log(name,JSON.stringify(await pg.evaluate(()=>({home:__dw.G.mode,ch:document.querySelectorAll('#chList li').length,mode:document.querySelector('#hMode').textContent,squad:document.querySelector('#hSquad').textContent}))));await pg.close()}
// squad: host + 2 guests
const ctx=await b.newContext({viewport:{width:844,height:390},deviceScaleFactor:2});const H=await ctx.newPage(),G1=await ctx.newPage(),G2=await ctx.newPage();
for(const[n,pg]of[['H',H],['G1',G1],['G2',G2]]){pg.on('pageerror',e=>errs.push(n+': '+e.stack));await route(pg);await pg.goto('http://dw.test/')}
await H.waitForTimeout(3000);
for(const[id,G]of[[1,G1],[2,G2]]){await H.exposeFunction('__toGuest'+id,o=>{G.evaluate(m=>window.__dw.guestRecv(m),o).catch(()=>{})});
  await G.exposeFunction('__toHost',o=>{H.evaluate(([id,m])=>window.__dw.hostRecv(id,m),[id,o]).catch(()=>{})});
  await H.evaluate(id=>{const d=window.__dw;d.NET.role='host';d.NET.code='TESTX';d.NET.conns.set(id,{open:true,send:o=>window['__toGuest'+id](o)});
    d.REMOTES.set(id,{id,col:id,name:['Orange','Blue','Red'][id],live:false,x:0,y:0,tx:0,ty:0,aim:0,face:1,moving:false,run:false,roll:false,hp:100,maxHp:100,gun:'pistol',dead:false,anim:0,r:6})},id);
  await G.evaluate(()=>{const d=window.__dw;d.NET.role='guest';d.NET.code='TESTX';d.NET.hostConn={open:true,send:o=>window.__toHost(o)};
    const c=d.sheetFor('survivor');d.meta.look={sheet:d.randomWardrobe(c)};d.NET.hostConn.send({t:'hello',v:1,look:d.meta.look})})}
await H.waitForTimeout(1500);
const sq=pg=>pg.evaluate(()=>({names:(window.__dw.HOME||{}).actors?null:null,squad:document.querySelector('#hSquad').textContent,deploy:document.querySelector('#deployBtn').textContent,host:document.querySelector('#hostBtn').textContent}));
console.log('host',JSON.stringify(await sq(H)),'guest',JSON.stringify(await sq(G1)));
await H.screenshot({path:'home_squad_host.png'});await G1.screenshot({path:'home_squad_guest.png'});
await H.click('#deployBtn');await H.waitForTimeout(2500);
console.log('after deploy',JSON.stringify(await H.evaluate(()=>window.__dw.G.mode)),JSON.stringify(await G1.evaluate(()=>window.__dw.G.mode)));
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
