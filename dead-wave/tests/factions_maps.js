// Screenshots every map with each enemy faction, all six enemy types lined up, and fires the sci-fi guns.
// Usage: node factions_maps.js /path/to/index.html   (writes fm_<map>_<faction>.png)
const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844}});const errs=[];pg.on('pageerror',e=>errs.push(e.stack));
await pg.goto('file://'+process.argv[2]);await pg.waitForTimeout(800);
const combos=[['suburb','robots'],['city','zombies'],['cyber','robots'],['forest','monsters'],['city','monsters'],['suburb','monsters']];
for(const[map,fac]of combos){
  await pg.evaluate(([map,fac])=>{__dw.meta.map=map;__dw.meta.fac=fac},[map,fac]);
  if(await pg.isVisible('#overMenu'))await pg.click('#overMenu');
  await pg.click('#playBtn');await pg.click('#mcStart');await pg.waitForTimeout(700);
  const info=await pg.evaluate(()=>{const d=__dw,P=d.P;P.hp=P.maxHp=99999;d.G.breakT=999;for(const z of d.Z)z.dead=true;d.Z.length=0;
    const types=['walker','runner','spitter','bloater','brute','boss'];types.forEach((t,i)=>{const z=d.spawnZ(t,P.x-60+i*24,P.y-40+(i%2)*14);if(z){z.stun=99;z.spd=0}});
    return{map:d.TH.id,fac:d.G.fac,z:d.Z.length,lamps:0}});
  await pg.waitForTimeout(900);await pg.screenshot({path:`fm_${map}_${fac}.png`});
  // kill one of each to see death frames
  await pg.evaluate(()=>{for(const z of __dw.Z.slice(0,3))z.hp=1});
  console.log(JSON.stringify(info),'boss bar:',await pg.textContent('#bossbar span'));
  await pg.evaluate(()=>{__dw.P.hp=0.0001;__dw.P.maxHp=100;__dw.P.inv=0});await pg.evaluate(()=>{const P=__dw.P;for(let i=0;i<3;i++)__dw.spawnZ('brute',P.x+5,P.y)});await pg.waitForTimeout(2500)}
// sci-fi guns
const dmg=await pg.evaluate(async()=>{const d=__dw;if(d.G.mode!=='play')return'not playing';return null});
if(await pg.isVisible('#overMenu'))await pg.click('#overMenu');
await pg.evaluate(()=>{__dw.meta.map='suburb';__dw.meta.fac='zombies'});await pg.click('#playBtn');await pg.click('#mcStart');await pg.waitForTimeout(600);
const res=await pg.evaluate(async()=>{const d=__dw,P=d.P,out={};P.hp=P.maxHp=99999;
  for(const g of['laser','arc','rail','plasma']){for(const z of d.Z)z.dead=true;d.Z.length=0;const zs=[];for(let i=0;i<4;i++){const z=d.spawnZ('brute',P.x+50+i*12,P.y+(i%2)*8);z.hp=z.max=5000;z.stun=99;zs.push(z)}
    P.slots[1]={g,mag:d.GUNDEF[g].mag,res:20};P.cur=1;P.aim=0;for(let i=0;i<3;i++){P.fireT=0;P.reload=0;d.fire();await new Promise(r=>setTimeout(r,250))}
    await new Promise(r=>setTimeout(r,500));out[g]=zs.map(z=>Math.round(5000-z.hp))}return out});
console.log('sci-fi damage per target',JSON.stringify(res));
await pg.screenshot({path:'fm_scifi.png'});
console.log('ERR',errs.slice(0,5).join('\n'));await b.close()})();
