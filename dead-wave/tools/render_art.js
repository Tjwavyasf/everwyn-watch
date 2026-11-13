// Renders the card pictures and the lobby backdrop from the game itself: real characters, maps and lighting,
// staged and captured. Writes art/lobby_bg.png and art/cards/*.png (replace any of them with your own art).
// Usage: node tools/render_art.js /path/to/dead-wave
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const ROOT=path.resolve(process.argv[2]||'.'),OUT=path.join(ROOT,'art');
const HIDE=`#hud,#banner,#pad,#modeScr,#title,#lobby,#over,#levelup,#hurtv{display:none!important}`;
async function page(b,w,h){const pg=await b.newPage({viewport:{width:w,height:h},deviceScaleFactor:1});const errs=[];pg.on('pageerror',e=>errs.push(e.message));pg.errs=errs;
  await pg.route('http://dw.test/**',r=>{const u=new URL(r.request().url());let p=path.join(ROOT,decodeURIComponent(u.pathname));if(p.endsWith('/'))p+='index.html';
    if(!fs.existsSync(p)||p.includes(path.sep+'art'+path.sep))return r.fulfill({status:404,body:''});const ct=p.endsWith('.png')?'image/png':p.endsWith('.json')?'application/json':'text/html';r.fulfill({status:200,body:fs.readFileSync(p),contentType:ct})});
  await pg.goto('http://dw.test/');await pg.waitForTimeout(2500);await pg.addStyleTag({content:HIDE});return pg}
async function shot(pg,file,setup,wait=900){await pg.evaluate(setup.fn,setup.arg);await pg.waitForTimeout(wait);if(setup.after){await pg.evaluate(setup.after,setup.arg);await pg.waitForTimeout(setup.afterWait||120)}
  await pg.screenshot({path:path.join(OUT,file)});console.log('wrote',file,pg.errs.length?'ERR '+pg.errs.join('|'):'')}
/* staging helpers that run in the page */
const H=`window.__st={
  start(mode,fac,map,zoom){const d=__dw;d.startRun(mode,fac,map);d.zoom(zoom);const P=d.P;P.hp=P.maxHp=1e9;P.inv=1e9;d.G.breakT=1e9;d.G.hideP=false;d.G.cap=true;d.DROPS.length=0;for(const z of d.Z)z.dead=true;d.Z.length=0;return P},
  walk(x,y){const d=__dw,t=d.tAt(x,y);return t>=0&&d.tile[t]<10},
  near(list,P){let b=null,bd=1e18;for(const o of list){const q=(o.x-P.x)**2+(o.y-P.y)**2;if(q<bd&&q>2500){bd=q;b=o}}return b},
  ring(P,n,r0,r1,types){const d=__dw,out=[];for(let i=0;i<n*4&&out.length<n;i++){const a=Math.random()*6.283,r=r0+Math.random()*(r1-r0),x=P.x+Math.cos(a)*r,y=P.y+Math.sin(a)*r;if(!this.walk(x,y))continue;const z=d.spawnZ(types[out.length%types.length],x,y);if(z)out.push(z)}return out},
  aimAt(P,z){if(z)P.aim=Math.atan2(z.y-P.y,z.x-P.x)},
  look(){const c=__dw.sheetFor('survivor');return c&&c.parts?{sheet:__dw.randomWardrobe(c)}:{}}};`;
(async()=>{fs.mkdirSync(path.join(OUT,'cards'),{recursive:true});const b=await chromium.launch();
  // ---------- mode cards (3:4) ----------
  let pg=await page(b,300,400);await pg.addScriptTag({content:H});
  await shot(pg,'cards/mode_survival.png',{fn:()=>{const st=__st,d=__dw,P=st.start('survival','zombies','suburb',118);
    const h=d.HOUSES.filter(h=>h.w>=8).sort((a,b)=>((a.x0+a.x1)/2*16-P.x)**2+((a.y0+a.y1)/2*16-P.y)**2-((b.x0+b.x1)/2*16-P.x)**2-((b.y0+b.y1)/2*16-P.y)**2)[0];
    P.x=(h.x0+h.x1+1)/2*16;P.y=(h.y0+h.y1+1)/2*16+4;for(let k=0;k<20&&!st.walk(P.x,P.y);k++)P.x+=8;d.HOUSES.forEach(q=>{if(q.id===h.id)q.roofA=0});P.slots[1]={g:'shotgun',mag:8,res:40};P.cur=1;
    for(const o of d.OPENS)if(o.house===h.id){o.boards=3;o.hp=o.max=60;const dx=o.tx===h.x0?-1:o.tx===h.x1?1:0,dy=o.ty===h.y0?-1:o.ty===h.y1?1:0;
      for(let k=0;k<(o.kind==='door'?3:1);k++){const z=d.spawnZ(k===2?'brute':'walker',o.tx*16+8+dx*20+(k-1)*9,o.ty*16+8+dy*20+Math.abs(k-1)*4*dy)}}
    const door=d.OPENS.find(o=>o.house===h.id&&o.kind==='door');if(door)P.aim=Math.atan2(door.ty*16+8-P.y,door.tx*16+8-P.x)},
    after:()=>{const d=__dw;d.DROPS.length=0;d.P.shootT=.4},afterWait:30},1400);
  await shot(pg,'cards/mode_objectives.png',{fn:()=>{const st=__st,d=__dw,P=st.start('objectives','zombies','suburb',125);d.G.ob.nextAt=1e9;d.objStep(3);
    const p=d.ob.pts[0];P.x=p.x;P.y=p.y+6;d.ob.prog=d.SCEN[d.ob.sc].steps[3].dur*.6;d.ob.boss=1;for(const z of d.Z)z.dead=true;d.Z.length=0;
    const zs=st.ring(P,9,70,120,['walker','walker','runner']);P.slots[1]={g:'ar',mag:30,res:90};P.cur=1;st.aimAt(P,zs[0])},
    after:()=>{const d=__dw;d.DROPS.length=0;d.P.shootT=.4},afterWait:30},900);
  await shot(pg,'cards/mode_horde.png',{fn:()=>{const st=__st,d=__dw,P=st.start('horde','zombies','city',150);d.G.state='fight';d.G.breakT=0;d.G.supplyT=1e9;
    const zs=st.ring(P,26,34,110,['walker','walker','runner','walker','bloater','brute']);P.slots[1]={g:'lmg',mag:80,res:200};P.cur=1;st.aimAt(P,zs[0]);for(const z of zs)z.spd*=.25},
    after:()=>{const d=__dw;d.DROPS.length=0;d.P.shootT=.4},afterWait:30},700);
  await shot(pg,'cards/mode_infection.png',{fn:()=>{const st=__st,d=__dw,P=st.start('infection','zombies','suburb',120);for(const z of d.Z)z.dead=true;d.Z.length=0;
    const f=d.inf;f.solo=false;f.ph='hunt';f.t=40;d.NET.role='host';const pos=[[-26,10],[22,-8],[-6,-26],[30,18]];
    pos.forEach(([dx,dy],i)=>{const id=i+1;d.REMOTES.set(id,{id,col:id,name:['Orange','Blue','Red','White','Green'][id],live:true,x:P.x+dx,y:P.y+dy,tx:P.x+dx,ty:P.y+dy,aim:Math.atan2(-dy,-dx),face:dx<0?1:-1,moving:id===3,run:false,roll:false,hp:100,maxHp:100,gun:'pistol',dead:false,anim:0,r:6,look:st.look()});f.ro[id]=id===3?'i':'s'});
    f.ro[0]='s';f.st={3:f.t+3}},after:()=>{},wait:600},700);
  await pg.evaluate(()=>{__dw.NET.role=null;__dw.REMOTES.clear()});await pg.close();
  // ---------- enemy cards (square) ----------
  pg=await page(b,300,300);await pg.addScriptTag({content:H});
  for(const[f,map]of[['zombies','suburb'],['robots','cyber'],['monsters','forest']])
    await shot(pg,`cards/fac_${f}.png`,{fn:([f,map])=>{const st=__st,d=__dw,P=st.start('survival',f,map,135);d.G.hideP=true;
      const add=(t,dx,dy,v)=>{const z=d.spawnZ(t,P.x+dx,P.y+dy);if(z){z.stun=1e9;z.dir='down';if(v!==undefined)z.var=v}return z};
      add('boss',0,-6);add('brute',-34,8);add('runner',34,8);add('walker',-18,30,0);add('walker',18,30,1);add('spitter',0,40)},arg:[f,map],after:()=>{__dw.DROPS.length=0}},1200);
  await pg.close();
  // ---------- map cards (square) ----------
  pg=await page(b,300,300);await pg.addScriptTag({content:H});
  for(const map of['suburb','city','cyber','forest'])
    await shot(pg,`cards/map_${map}.png`,{fn:(map)=>{const st=__st,d=__dw,P=st.start('survival','zombies',map,240);d.G.hideP=true;
      let s=null;if(map==='suburb'||map==='cyber'){const hs=d.HOUSES.filter(h=>!h.store);const h=hs[Math.floor(hs.length*.37)];s={x:(h.x0+h.x1+1)/2*16,y:(h.y1+3)*16}}
      else{s=st.near(d.FIRES.filter(o=>st.walk(o.x,o.y+20)),P);if(s)s={x:s.x,y:s.y+10}}if(!s)s=P;P.x=s.x;P.y=s.y;
      st.ring(P,3,40,100,['walker']).forEach(z=>z.spd*=.3)},arg:map,after:()=>{__dw.DROPS.length=0}},1200);
  await pg.close();
  // ---------- lobby backdrop (wide) ----------
  pg=await page(b,1280,720);await pg.addScriptTag({content:H});
  await shot(pg,'lobby_bg.png',{fn:()=>{const st=__st,d=__dw,P=st.start('survival','zombies','city',340);d.G.hideP=true;
    const s=st.near(d.FIRES.filter(o=>st.walk(o.x,o.y+40)),P)||P;P.x=s.x+40;P.y=s.y+30;st.ring(P,14,60,260,['walker','walker','runner','brute']).forEach(z=>z.spd*=.3)}},1500);
  await pg.close();await b.close()})();
