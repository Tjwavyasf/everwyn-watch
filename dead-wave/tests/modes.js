// Plays an Objectives run to the end (teleporting to each target) and checks Horde mode. Usage: node modes.js /path/to/index.html
const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844}});const errs=[];pg.on('pageerror',e=>errs.push(e.stack));
const url='file://'+process.argv[2];
const god=()=>pg.evaluate(()=>{const P=__dw.P;if(P){P.hp=P.maxHp=99999;P.inv=9}});
const tp=(x,y)=>pg.evaluate(([x,y])=>{const P=__dw.P;P.x=x;P.y=y},[x,y]);
// ---- objectives ----
await pg.goto(url);await pg.waitForTimeout(800);
await pg.click('#playBtn');await pg.click('[data-mode="objectives"]');console.log('mode card:',await pg.textContent('#mcBanner'));await pg.click('#mcStart');await pg.waitForTimeout(500);await god();
console.log('intro:',await pg.textContent('#banT'),'|',await pg.textContent('#left'));
await pg.waitForTimeout(4500);await god();
await pg.screenshot({path:'obj_start.png'});
const n=await pg.evaluate(()=>__dw.SCEN[__dw.ob.sc].steps.length);
for(let step=0;step<n;step++){
  for(let tries=0;tries<80;tries++){const st=await pg.evaluate(()=>{const o=__dw.ob,k=__dw.objKind();return{i:o.i,k:k&&k.k,dur:k&&k.dur,t:__dw.objTargets().map(p=>[p.x,p.y])}});
    if(st.i!==step||!st.k)break;await god();
    if(st.k==='hold'||st.k==='extract'){await tp(st.t[0][0],st.t[0][1]);await pg.evaluate(d=>{__dw.ob.prog=Math.max(__dw.ob.prog,d-2)},st.dur);await pg.waitForTimeout(300);
      if(step===n-1&&tries===0){await pg.waitForTimeout(100);await pg.screenshot({path:'obj_extract.png'})}await pg.waitForTimeout(2600)}
    else if(st.t.length){await tp(st.t[0][0],st.t[0][1]);await pg.waitForTimeout(st.k==='switch'?3500:500)}
    else await pg.waitForTimeout(300)}
  const line=await pg.textContent('#left');console.log('after step',step,'|',await pg.textContent('#waveno'),'|',line);
  await pg.waitForTimeout(3000);await god()}
await pg.waitForTimeout(500);
console.log('end screen:',await pg.textContent('#oTitle'),await pg.textContent('#oWaveL'),await pg.textContent('#oWave'),'coins',await pg.textContent('#oCoins'),'|',await pg.textContent('#oBest'));
await pg.screenshot({path:'obj_win.png'});
// ---- horde ----
await pg.click('#overMenu');await pg.waitForTimeout(300);await pg.click('#playBtn');await pg.click('[data-mode="horde"]');
console.log('mode card:',await pg.textContent('#mcBanner'),'|',await pg.textContent('#mcDesc'));
await pg.click('#mcStart');await pg.waitForTimeout(500);await god();
console.log('horde prep:',await pg.textContent('#waveno'),'|',await pg.textContent('#left'));
await pg.evaluate(()=>{__dw.G.breakT=0});await pg.waitForTimeout(4000);await god();
console.log('horde:',await pg.textContent('#waveno'),'|',await pg.textContent('#left'),'zombies',await pg.evaluate(()=>__dw.Z.length));
await pg.evaluate(()=>{__dw.G.hT=200;__dw.G.supplyT=0.1});await pg.waitForTimeout(1500);await god();
console.log('horde later:',await pg.textContent('#waveno'),'|',await pg.textContent('#banT'),'boss',await pg.evaluate(()=>!!__dw.G.boss),'drops near',await pg.evaluate(()=>__dw.DROPS.filter(d=>Math.hypot(d.x-__dw.P.x,d.y-__dw.P.y)<80&&['ammo','med','gren','gun'].includes(d.type)).length));
await pg.screenshot({path:'horde.png'});
await pg.evaluate(()=>{const P=__dw.P;P.hp=1;P.maxHp=100;P.inv=0;for(let i=0;i<5;i++)__dw.spawnZ('brute',P.x+10,P.y+6)});await pg.waitForTimeout(6000);
console.log('horde end:',await pg.textContent('#oTitle'),await pg.textContent('#oWaveL'),await pg.textContent('#oWave'),'|',await pg.textContent('#oBest'));
console.log('ERR',errs.join('\n'));await b.close()})();
