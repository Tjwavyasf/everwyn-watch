const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const pg=await b.newPage({viewport:{width:390,height:844},deviceScaleFactor:2});const errs=[];pg.on('pageerror',e=>errs.push(e.stack));
await pg.goto('file://'+process.argv[2]);await pg.waitForTimeout(1000);
await pg.click('#charBtn');await pg.waitForTimeout(200);
let btns=await pg.$$('.opt button');await btns[5].click();btns=await pg.$$('.opt button');await btns[5].click();btns=await pg.$$('.opt button');await btns[9].click();await pg.waitForTimeout(400);
await pg.screenshot({path:'c1.png'});
await pg.click('#cDone');await pg.click('#playBtn');await pg.click('#mcStart');await pg.waitForTimeout(1500);await pg.screenshot({path:'c2.png'});
console.log(await pg.evaluate(()=>JSON.stringify(window.__dw.P.look)));
console.log('ERR',errs.join('\n'));await b.close()})();
