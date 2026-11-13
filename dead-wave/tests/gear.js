// Weapons, attachments and first aid kits (solo). Usage: node gear.js /path/to/index.html
const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844}});const errs=[];pg.on('pageerror',e=>errs.push(e.stack));
await pg.goto('file://'+process.argv[2]);await pg.waitForTimeout(800);
await pg.click('#playBtn');await pg.click('#mcStart');await pg.waitForTimeout(800);
console.log('start',JSON.stringify(await pg.evaluate(()=>({aid:__dw.P.aid,att:__dw.DROPS.filter(d=>d.type==='att').length}))));
// every gun fires at a zombie
const res=await pg.evaluate(async()=>{const d=__dw,P=d.P,out={};P.hp=P.maxHp=99999;
  for(const g of Object.keys(d.GUNDEF)){if(g==='pistol')continue;for(const z of d.Z)z.dead=true;d.Z.length=0;
    const z=d.spawnZ('brute',P.x+60,P.y);z.hp=z.max=5000;P.slots[1]={g,mag:d.GUNDEF[g].mag,res:50};P.cur=1;P.aim=0;
    for(let i=0;i<4;i++){P.fireT=0;P.reload=0;d.fire();await new Promise(r=>setTimeout(r,180))}
    await new Promise(r=>setTimeout(r,500));out[g]=Math.round(5000-z.hp)}
  return out});
console.log('damage dealt',JSON.stringify(res));
// attachments
console.log('attach',JSON.stringify(await pg.evaluate(()=>{const d=__dw,P=d.P;P.slots[1]={g:'smg',mag:40,res:100};P.cur=1;
  const m0=d.magOfS(P.slots[1]);const a1=d.attach('extmag');const m1=d.magOfS(P.slots[1]);const a2=d.attach('extmag');const a3=d.attach('scope');const a4=d.attach('choke');
  const st=d.gunStats(P.slots[1]);return{m0,a1,m1,a2,a3,a4,att:P.slots[1].att,spread:+st.spread.toFixed(2)}})));
// swap an attachment from the ground with the use button
await pg.evaluate(()=>{const d=__dw,P=d.P;const q=d.drop('att',P.x,P.y+3,1,{g:'drum'});q.vx=q.vy=0;q.t=1});await pg.waitForTimeout(400);
console.log('use label:',await pg.textContent('#useBtn'));
await pg.keyboard.press('e');await pg.waitForTimeout(300);
console.log('after swap',JSON.stringify(await pg.evaluate(()=>({att:__dw.P.slots[1].att,mag:__dw.magOfS(__dw.P.slots[1]),onGround:__dw.DROPS.filter(d=>d.type==='att'&&d.g==='extmag').length}))));
await pg.screenshot({path:'gear_hud.png'});
// first aid
await pg.evaluate(()=>{const P=__dw.P;P.maxHp=100;P.hp=30;P.inv=99;for(const z of __dw.Z)z.dead=true});
await pg.keyboard.press('h');await pg.waitForTimeout(1200);await pg.screenshot({path:'gear_heal.png'});await pg.waitForTimeout(1800);
console.log('after aid',JSON.stringify(await pg.evaluate(()=>({hp:Math.round(__dw.P.hp),aid:__dw.P.aid}))));
console.log('ERR',errs.join('\n'));await b.close()})();
