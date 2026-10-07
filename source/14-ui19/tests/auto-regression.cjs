const {chromium}=require('playwright');
const fs=require('fs');
const path=require('path');
const assert=require('assert/strict');
const original=fs.readFileSync(path.join(__dirname,'../../14/sample1.html'),'utf8');
const updated=fs.readFileSync(path.join(__dirname,'../sample1.html'),'utf8');
const script=s=>s.match(/<script>([\s\S]*?)<\/script>/)[1];
const functions=s=>Object.fromEntries(script(s).split(/(?=  function )/).slice(1).map(x=>[x.match(/function (\w+)/)[1],x.slice(0,x.indexOf('\n  }')+4)]));
const before=functions(original), after=functions(updated);
for(const name of Object.keys(before)) if(name!=='saveGame') assert.equal(after[name],before[name],`Changed engine function: ${name}`);
// saveGame is the only allowed changed existing function (UI feedback).
function instrument(html){
 for(const name of ['doMove','render','spawn','pickDir','runAutoLoop','fullRender']) html=html.replace(new RegExp(`function ${name}\\(([^)]*)\\) \\{`),`function ${name}($1) { window.trace.push({name:'${name}',time:Date.now(),tiles:state.tiles.map(t=>[t.id,String(t.v),t.q,t.r,t.randColor,!!t.dead]),score:String(state.score),step:autoStep});`);
 return html.replace('  if (!loadGame()) newGame();','  window.testApi = { state, startAuto, stopAuto, newGame, makeSnapshot };\n  if (!loadGame()) newGame();');
}
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox']});
 async function setup(html,clock=true){
  const context=await browser.newContext({viewport:{width:480,height:800}});
  const page=await context.newPage();
  await page.addInitScript(()=>{window.trace=[];let seed=123456;Math.random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};});
  if(clock) { await page.clock.install({time:new Date('2026-10-07T12:00:00Z')}); await page.clock.pauseAt(new Date('2026-10-07T12:00:10Z')); }
  await page.route('http://test.local/**',route=>route.fulfill({contentType:'text/html',body:instrument(html)}));
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://test.local/');
  return {page,context,errors};
 }
 let scenarios=0;
 for(const mi of Array.from({length:14},(_,i)=>i)) {
  const a=await setup(original),b=await setup(updated);
  for(const p of [a.page,b.page]) await p.evaluate(mi=>{testApi.state.mi=mi;testApi.newGame();trace=[];testApi.startAuto('Random');},mi);
  for(const p of [a.page,b.page]) await p.clock.runFor(73000);
  const ta=await a.page.evaluate(()=>trace),tb=await b.page.evaluate(()=>trace);
  assert.deepEqual(tb,ta,`Auto trace mismatch grid ${mi}`);
  assert(ta.filter(x=>x.name==='doMove').length>=100);
  const times=ta.filter(x=>x.name==='doMove').map(x=>x.time);
  for(let i=1;i<times.length;i++) assert.equal(times[i]-times[i-1],690);
  assert.deepEqual(a.errors,[]);assert.deepEqual(b.errors,[]);
  scenarios++;console.log(`Passed Auto scenario ${scenarios}`);await a.context.close();await b.context.close();
 }
 for(const mi of [2,7]) for(const mode of ['Corner','Swing','Swirl']){
  const a=await setup(original),b=await setup(updated);
  for(const p of [a.page,b.page]) await p.evaluate(({mi,mode})=>{testApi.state.mi=mi;testApi.newGame();trace=[];testApi.startAuto(mode);},{mi,mode});
  for(const p of [a.page,b.page]) await p.clock.runFor(18000);
  assert.deepEqual(await b.page.evaluate(()=>trace),await a.page.evaluate(()=>trace));
  scenarios++;console.log(`Passed Auto scenario ${scenarios}`);await a.context.close();await b.context.close();
 }
 // Real Chromium animation sampling: retain actual requestAnimationFrame timing.
 const live=await setup(updated,false);
 await live.page.waitForTimeout(400);
 const samples=await live.page.evaluate(async()=>{
  const nodes=[...document.querySelectorAll('#board .tw')];
  const values=[];testApi.startAuto('Random');
  await new Promise(resolve=>{const start=performance.now();function frame(){values.push({t:performance.now()-start,positions:nodes.map(e=>getComputedStyle(e).transform)});if(performance.now()-start<300)requestAnimationFrame(frame);else resolve();}requestAnimationFrame(frame);});
  testApi.stopAuto();return values;
 });
 assert(samples.length>=8,`Too few frames: ${samples.length}`);
 const moving=samples[0].positions.map((_,i)=>new Set(samples.map(s=>s.positions[i])).size);
 assert(moving.some(n=>n>=8),'No visible intermediate transform states');
 await live.page.waitForTimeout(500);
 await live.page.locator('#bAuto').click();assert(await live.page.locator('#bAuto').evaluate(e=>e.classList.contains('on')));
 await live.page.waitForTimeout(720);await live.page.locator('#bAuto').click();
 assert.equal(await live.page.locator('#bAuto').evaluate(e=>e.classList.contains('on')),false);
 await live.page.locator('#bSave').click();assert.equal(await live.page.locator('#bSave').textContent(),'Saved!');
 const saved=await live.page.evaluate(()=>JSON.parse(localStorage.getItem('hex2048_manual_save_data_v14_ui19_v1')));
 await live.page.reload();assert.equal(await live.page.locator('#score').textContent(),saved.score.replace('n',''));
 await live.page.locator('#bGrid').click();assert.equal(await live.page.locator('#mm div').count(),14);
 await live.page.locator('#mm div').nth(7).click();assert.equal(await live.page.locator('#board .cw').count(),16);
 for(const viewport of [{width:390,height:844},{width:1280,height:800}]){await live.page.setViewportSize(viewport);const bounds=await live.page.locator('#board').boundingBox();assert(bounds.x>=0&&bounds.y>=0&&bounds.x+bounds.width<=viewport.width&&bounds.y+bounds.height<=viewport.height);}
 assert.deepEqual(live.errors,[]);
 console.log(JSON.stringify({unchangedFunctions:Object.keys(before).length-1,autoScenarios:scenarios,randomMovesPerGrid:106,autoIntervalMs:690,realAnimationFrames:samples.length,distinctTransforms:moving,ui:'Auto toggle, Save/reload, 14 grids, mobile/desktop passed'},null,2));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
