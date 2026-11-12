const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844},deviceScaleFactor:1});
const errs=[];pg.on('pageerror',e=>errs.push(e.stack));
await pg.goto('file://'+process.argv[2]);await pg.waitForTimeout(1200);await pg.click('#playBtn');await pg.waitForTimeout(500);
// put player in a house center, board every opening of that house with 3 boards
await pg.evaluate(()=>{const d=window.__dw,P=d.P;const h=d.HOUSES[5];P.x=((h.x0+h.x1)/2|0)*16+8;P.y=((h.y0+h.y1)/2|0)*16+8;
  for(let k=0;k<3;k++){const i=d.tAt(P.x,P.y);if(d.tile[i]>=10)P.x+=16}
  for(const o of d.OPENS)if(o.house===h.id){o.boards=3;o.hp=40}P.hp=99999;P.maxHp=99999;d.startWave(6);window.__h=h.id});
let snaps=[];
for(let i=0;i<40;i++){await pg.waitForTimeout(500);const s=await pg.evaluate(()=>{const d=window.__dw;if(!document.querySelector('#levelup').hidden)d.choose(0);d.P.hp=99999;
  const ops=d.OPENS.filter(o=>o.house===window.__h);return{z:d.Z.length,boards:ops.map(o=>o.boards).join(''),inside:d.Z.filter(z=>{const i=d.tAt(z.x,z.y);return d.HOUSES[window.__h]&&z.x>d.HOUSES[window.__h].x0*16&&z.x<(d.HOUSES[window.__h].x1+1)*16&&z.y>d.HOUSES[window.__h].y0*16&&z.y<(d.HOUSES[window.__h].y1+1)*16}).length}});
  if(i%5==0)snaps.push(s);if(i==30)await pg.screenshot({path:'k5.png'})}
console.log(JSON.stringify(snaps));
const fps=await pg.evaluate(()=>new Promise(r=>{let n=0;const s=performance.now();const f=()=>{n++;if(performance.now()-s<2000)requestAnimationFrame(f);else r(n/2)};requestAnimationFrame(f)}));console.log('fps',fps,'zombies',await pg.evaluate(()=>window.__dw.Z.length));
console.log('ERR',errs.slice(0,3).join('\n'));await b.close()})();
