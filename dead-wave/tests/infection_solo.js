// Infection practice round (solo vs AI infected). Usage: node infection_solo.js /path/to/index.html
const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844}});const errs=[];pg.on('pageerror',e=>errs.push(e.stack));
await pg.goto('file://'+process.argv[2]);await pg.waitForTimeout(800);
for(let i=0;i<3;i++)await pg.click('#modeNext');
console.log('mode:',await pg.textContent('#modeName'));
await pg.click('#playBtn');await pg.waitForTimeout(3600);
const s=()=>pg.evaluate(()=>{const f=__dw.inf;return{ph:f.ph,t:+f.t.toFixed(1),role:f.ro[0],cine:!document.querySelector('#cine').hidden,hud:document.querySelector('#waveno').textContent,left:document.querySelector('#left').textContent,inHouse:__dw.P.inside>=0,z:__dw.Z.length}});
console.log('intro',JSON.stringify(await s()));await pg.screenshot({path:'inf_intro.png'});
await pg.waitForTimeout(3500);console.log('run',JSON.stringify(await s()),'banner',await pg.textContent('#banT'));
const x0=await pg.evaluate(()=>__dw.P.x);await pg.keyboard.down('d');await pg.waitForTimeout(800);await pg.keyboard.up('d');
console.log('moved during run',Math.round(await pg.evaluate(()=>__dw.P.x)-x0));
await pg.evaluate(()=>{__dw.inf.t=29.5;const P=__dw.P;for(let k=0;k<400;k++){const a=Math.random()*6.28,x=P.x+Math.cos(a)*120,y=P.y+Math.sin(a)*120,i=__dw.tAt(x,y);if(i>=0&&__dw.tile[i]<10){P.x=x;P.y=y;break}}});await pg.waitForTimeout(1200);
console.log('hunt',JSON.stringify(await s()),'banner',await pg.textContent('#banT'));
// the infected comes close: push it
await pg.evaluate(()=>{const z=__dw.inf.npc,P=__dw.P;z.x=P.x+20;z.y=P.y;P.aim=0;P.stam=100;P.shoveT=0});await pg.waitForTimeout(60);
await pg.keyboard.press('f');await pg.waitForTimeout(200);
console.log('stun after push',+(await pg.evaluate(()=>__dw.inf.npc.stun)).toFixed(1));
await pg.screenshot({path:'inf_hunt.png'});
// let it catch you
await pg.evaluate(()=>{const z=__dw.inf.npc,P=__dw.P;z.stun=0;z.x=P.x+6;z.y=P.y;P.inv=0;P.rollT=0});await pg.waitForTimeout(2500);
console.log('end:',await pg.textContent('#oTitle'),await pg.textContent('#oWaveL'),await pg.textContent('#oWave'),'coins',await pg.textContent('#oCoins'));
console.log('ERR',errs.join('\n'));await b.close()})();
