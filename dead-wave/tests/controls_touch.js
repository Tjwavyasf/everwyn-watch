// Touch controls in portrait and landscape: move stick + FIRE held together (multi-touch), Controls screen, left-handed layout.
// Usage: node controls_touch.js /path/to/dead-wave
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const ROOT=require('path').resolve(process.argv[2]||'.');
async function run(b,name,vw,vh){
  const ctx=await b.newContext({viewport:{width:vw,height:vh},hasTouch:true,isMobile:true});const pg=await ctx.newPage();const errs=[];pg.on('pageerror',e=>errs.push(e.stack));
  await pg.route('http://dw.test/**',r=>{const u=new URL(r.request().url());let p=path.join(ROOT,decodeURIComponent(u.pathname));if(p.endsWith('/'))p+='index.html';
    if(!fs.existsSync(p))return r.fulfill({status:404,body:''});const ct=p.endsWith('.png')?'image/png':p.endsWith('.json')?'application/json':'text/html';r.fulfill({status:200,body:fs.readFileSync(p),contentType:ct})});
  await pg.goto('http://dw.test/');await pg.waitForTimeout(2000);
  await pg.click('#playBtn');await pg.click('#mcStart');await pg.waitForTimeout(800);
  console.log(await pg.evaluate(()=>({hud:document.querySelector('#hud').className,c:__dw.ctl.usingMouse})));
  const cdp=await ctx.newCDPSession(pg);
  const fb=await pg.locator('#fireBtn').boundingBox();const fx=fb.x+fb.width/2,fy=fb.y+fb.height/2;
  const home=await pg.evaluate(()=>__dw.joyHome());
  const T=(type,pts)=>cdp.send('Input.dispatchTouchEvent',{type,touchPoints:pts});
  // start the wave, spawn some zombies near the player
  await pg.evaluate(()=>{const d=window.__dw;d.G.breakT=0});await pg.waitForTimeout(3000);
  const p0=await pg.evaluate(()=>[__dw.P.x,__dw.P.y]);
  await T('touchStart',[{x:home.x,y:home.y,id:1}]);
  for(let i=1;i<=6;i++){await T('touchMove',[{x:home.x+i*10,y:home.y,id:1}]);await pg.waitForTimeout(30)}
  await pg.waitForTimeout(600);
  const p1=await pg.evaluate(()=>[__dw.P.x,__dw.P.y,__dw.P.running]);
  await T('touchMove',[{x:home.x+20,y:home.y,id:1},{x:fx,y:fy,id:2}]);
  await pg.waitForTimeout(1500);
  const st=await pg.evaluate(()=>({kills:__dw.G.kills,mag:__dw.ctl.gun.mag,lock:!!__dw.ctl.AIMZ,fire:__dw.ctl.fb.on,z:__dw.Z.length}));
  await pg.screenshot({path:name+'_play.png'});
  await T('touchEnd',[]);await pg.waitForTimeout(200);
  const after=await pg.evaluate(()=>({fire:__dw.ctl.fb.on,mv:__dw.ctl.mv.on}));
  console.log(name,'moved',Math.round(p1[0]-p0[0]),'running',p1[2],JSON.stringify(st),JSON.stringify(after));
  // controls screen
  await pg.evaluate(()=>__dw.togglePause());await pg.click('#ctlBtn2');await pg.waitForTimeout(200);await pg.screenshot({path:name+'_ctl.png'});
  const nb=await pg.$$('#ctlOpts .opt button');await nb[3].click();await pg.click('#ctlDone');await pg.click('#resumeBtn');await pg.waitForTimeout(300);await pg.screenshot({path:name+'_lefty.png'});
  console.log(name,'ERR',errs.join('\n'));await ctx.close()}
(async()=>{const b=await chromium.launch();await run(b,'portrait',390,844);await run(b,'landscape',844,390);await b.close()})();
