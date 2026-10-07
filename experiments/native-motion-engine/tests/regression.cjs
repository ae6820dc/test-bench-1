/* npm install playwright, then: node experiments/native-motion-engine/tests/regression.cjs
 * Chromium path may be supplied with CHROMIUM_PATH; --functional-only skips the two stress runs.
 * Test instrumentation is never shipped in the HTML.
 */
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'../../..');
const html=fs.readFileSync(path.join(root,'experiments/native-motion-engine/sample1.html'),'utf8');
const reference=fs.readFileSync(path.join(root,'source/14-ui19/sample1.html'),'utf8');
const rule=reference.slice(reference.indexOf('  function step('),reference.indexOf('\n  function canMove'));
assert(html.includes(rule),'Reference move/merge rule must remain identical');
const injected=html.replace('  /* initialize */',`  window.harness={state,engine,views,cellsFor,buildCells,fullRender,doMove,movable,pickDir,startAuto,stopAuto,newGame,snapshot,restoreSnapshot,saveGame,loadGame,switchGrid,setSpeed,step,validateSnapshot,
    storageKeys:[MANUAL_SAVE_KEY,SLOT_SAVE_KEY],get auto(){return isAutoRunning;}};
  /* initialize */`);
const results={checks:[],stress:{},frames:{},errors:[]};
function log(name){results.checks.push(name);console.log('PASS '+name);}
(async()=>{
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox']});
const context=await browser.newContext({viewport:{width:480,height:800}}),page=await context.newPage();
page.on('pageerror',e=>results.errors.push(e.message));
await page.route('http://motion.test/**',r=>r.fulfill({contentType:'text/html',body:injected}));
await page.addInitScript(()=>{let seed=2468;Math.random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};});
await page.goto('http://motion.test/');
await page.waitForTimeout(50);
async function idle(){await page.waitForFunction(()=>!harness.engine.transaction && !harness.engine.movements.size);}
async function invariant(){assert(await page.evaluate(()=>{
 const h=harness,s=h.state,e=h.engine,valid=new Set(h.cellsFor(s.mi).map(c=>c.join(',')));
 return new Set(s.tiles.map(t=>t.id)).size===s.tiles.length && new Set(s.tiles.map(t=>t.q+','+t.r)).size===s.tiles.length &&
  s.tiles.every(t=>valid.has(t.q+','+t.r)&&t.v===1n&&t.id<=s.uid) && s.hist.length<=10 &&
  h.views.size===s.tiles.length+(e.transaction ? e.transaction.dead.length : 0) &&
  document.querySelectorAll('#board .tw').length===h.views.size && (e.transaction || e.movements.size===0);
}),'Tile/DOM invariant');}
async function fixture(mi=7,tiles=[{q:1,r:2},{q:2,r:2}]){
 await page.evaluate(({mi,tiles})=>{
  const h=harness;h.stopAuto();h.state.mi=mi;h.newGame();
  h.state.tiles=tiles.map((t,i)=>({id:i+1,v:1n,q:t.q,r:t.r,randColor:90+i*80}));h.state.uid=tiles.length;h.state.hist=[];h.state.score=0n;h.state.moveCount=0;
  h.fullRender();
 },{mi,tiles});
}
// Equivalence of the preserved rule across all grids and directions, including colors.
const equivalence=await page.evaluate(rule=>{
 const refFactory=Function('state','cset','key','TILE_VALUE','randomColor','isAutoRunning','return '+rule.trim());
 const h=harness;
 for(let mi=0;mi<14;mi++){
  h.stopAuto();h.state.mi=mi;h.newGame();const cells=h.cellsFor(mi),dirs=mi<7 ? [[0,-1],[1,-1],[1,0],[0,1],[-1,1],[-1,0]] : [[0,-1],[1,-1],[1,0],[1,1],[0,1],[-1,1],[-1,0],[-1,-1]];
  for(const auto of [false,true]) for(const d of dirs){
   const tiles=cells.filter((_,i)=>i%3===0).map(([q,r],i)=>({id:i+1,v:1n,q,r,randColor:i%360}));
   let rng=0;const original=Math.random;Math.random=()=>((++rng%71)/71);
   if(auto) h.startAuto();else h.stopAuto();
   const a=tiles.map(t=>({...t})),b=tiles.map(t=>({...t}));rng=0;
   const result=h.step(a,d);rng=0;
   const ref=refFactory(h.state,new Set(cells.map(c=>c.join(','))),(q,r)=>q+','+r,1n,()=>Math.floor(Math.random()*360),auto);
   const expected=ref(b,d);Math.random=original;h.stopAuto();
   const json=x=>JSON.stringify(x,(_,v)=>typeof v==='bigint' ? v+'n' : v);
   if(json([a,result])!==json([b,expected])) return false;
  }
 }
 return true;
},rule);
assert(equivalence);log('Reference rules: all 14 grids, every direction, manual and Random Auto');
// No duration timer can release a paused motion; each necessary animation must finish.
await fixture();await page.evaluate(()=>{harness.setSpeed(0);harness.doMove(0);for(const r of harness.engine.movements) r.animation.pause();});
const pending=await page.evaluate(()=>harness.engine.movements.size);assert(pending>=2);
await page.waitForTimeout(380);assert.equal(await page.evaluate(()=>harness.state.moveCount),1);assert(await page.evaluate(()=>!!harness.engine.transaction));
await page.evaluate(()=>[...harness.engine.movements][0].animation.finish());await page.waitForTimeout(30);
assert(await page.evaluate(()=>!!harness.engine.transaction));
await page.evaluate(()=>{for(const r of harness.engine.movements)r.animation.finish();});await idle();await invariant();
assert.equal(await page.evaluate(()=>harness.state.uid),3);log('Actual finished-event barrier: elapsed duration and one completion cannot release a step');
// Verify native duration/easing/keyframes and real visible interpolation.
for(let speed=0;speed<4;speed++){
 await fixture();const detail=await page.evaluate(speed=>{harness.setSpeed(speed);harness.doMove(0);const r=[...harness.engine.movements][0];const timing=r.animation.effect.getTiming();const keys=r.animation.effect.getKeyframes();return {duration:timing.duration,easing:timing.easing,keys};},speed);
 assert.equal(detail.duration,[300,150,100,75][speed]);assert.equal(detail.easing,'linear');await idle();await invariant();
 const scale=await page.evaluate(()=>{const r=[...harness.engine.actions].find(r=>r.kind==='scale');return r ? r.animation.effect.getKeyframes().map(k=>({transform:k.transform,offset:k.offset})) : null;});
 assert(scale);assert.deepEqual(scale.map(k=>k.transform),['scale(0)','scale(1.1)','scale(1)']);assert.equal(scale[1].offset,2/3);
}
await fixture();await page.evaluate(()=>harness.setSpeed(0));
results.frames=await page.evaluate(async()=>{
 const h=harness,el=h.views.values().next().value.el,frames=[];h.doMove(0);
 await new Promise(resolve=>{function sample(t){frames.push({t,transform:getComputedStyle(el).transform});if(h.engine.transaction)requestAnimationFrame(sample);else resolve();}requestAnimationFrame(sample);});
 return {count:frames.length,distinct:new Set(frames.map(f=>f.transform)).size,elapsedMs:frames.at(-1).t-frames[0].t,maxGapMs:Math.max(...frames.slice(1).map((f,i)=>f.t-frames[i].t))};
});assert(results.frames.distinct>=5);log('Four native durations, linear motion, 2:1 scale phases and 1.1 peak; real animation frames');
// Manual moves on EVERY board; every Box diagonal is exercised.
for(let mi=0;mi<14;mi++) for(const direction of mi<7 ? [0,1,2,3,4,5] : [0,1,2,3,4,5,6,7]){
 await page.evaluate(({mi,direction})=>{const h=harness;h.stopAuto();h.state.mi=mi;h.newGame();h.doMove(direction);for(const r of h.engine.movements)r.animation.finish();},{mi,direction});
 await idle();await invariant();
}
log('Manual movements on every Hex/Box grid, including all Box diagonals');
// Browser keyboard and actual CDP touch input use the production event handlers.
await fixture();await page.locator('#board').click({position:{x:20,y:20}});await page.keyboard.press('ArrowUp');await idle();
assert.equal(await page.evaluate(()=>harness.state.moveCount),1);
const cdp=await context.newCDPSession(page);await cdp.send('Emulation.setTouchEmulationEnabled',{enabled:true});
await fixture();const box=await page.locator('#wrap').boundingBox(),x=box.x+box.width/2,y=box.y+box.height/2;
await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]});
await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x,y:y-90}]});
await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await idle();
assert.equal(await page.evaluate(()=>harness.state.moveCount),1);log('Desktop keyboard and Chromium touch dispatch');
await fixture();await page.evaluate(()=>{harness.setSpeed(0);harness.doMove(0);for(let i=0;i<10;i++)harness.doMove(2);});
assert.equal(await page.evaluate(()=>harness.state.moveCount),1);await idle();log('Manual input cannot overlap an active motion');
for(let i=0;i<12;i++){
 await page.evaluate(()=>harness.startAuto());await page.waitForTimeout(25);await page.evaluate(()=>{harness.stopAuto();harness.startAuto();harness.stopAuto();});await invariant();
 assert.equal(await page.evaluate(()=>harness.engine.actions.size),0);assert.equal(await page.evaluate(()=>harness.engine.autoFrame),0);
}
log('Rapid Auto start/stop: no active actions, stale continuation or duplicate spawn');
// Storage namespace, atomic save in motion, full BigInt/Auto restore and grid slots.
await page.evaluate(()=>localStorage.setItem('hex2048_manual_save_data_v14_ui19_v1','untouched sentinel'));
await fixture();await page.evaluate(()=>{harness.state.score=123456789012345678901234567890n;harness.startAuto();});await page.waitForTimeout(25);
await page.locator('#bSave').click();assert.equal(await page.locator('#bSave').textContent(),'Saved!');
const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem(harness.storageKeys[0])));
assert(saved.autoRunning);assert(BigInt(saved.score.slice(0,-1))>=123456789012345678901234567890n);
await page.evaluate(()=>{harness.switchGrid(2);harness.newGame();harness.loadGame();});await invariant();
assert.equal(await page.evaluate(()=>harness.state.mi),saved.mi);assert(await page.evaluate(()=>harness.auto));
await page.evaluate(()=>harness.stopAuto());
assert.equal(await page.evaluate(()=>localStorage.getItem('hex2048_manual_save_data_v14_ui19_v1')),'untouched sentinel');
// Fresh-game and load commands invalidate the old transaction even while its promises settle.
await fixture();await page.evaluate(()=>{harness.setSpeed(0);harness.doMove(0);harness.newGame();});
const fresh=await page.evaluate(()=>harness.snapshot());await page.waitForTimeout(350);await invariant();
assert.equal(await page.evaluate(()=>harness.state.moveCount),0);
assert.equal(await page.evaluate(()=>harness.state.uid),fresh.uid);
assert.equal(await page.evaluate(()=>harness.engine.actions.size),0);
await page.evaluate(()=>{harness.saveGame();harness.doMove(harness.pickDir());harness.loadGame();});
await page.waitForTimeout(350);await invariant();
assert.equal(await page.evaluate(()=>harness.state.moveCount),0);
assert.equal(await page.evaluate(()=>harness.engine.actions.size),0);
log('New game and load during motion discard stale callbacks without duplicate spawns');
await page.evaluate(()=>{window.originalSetItem=Storage.prototype.setItem;Storage.prototype.setItem=function(){throw new DOMException('Quota test','QuotaExceededError');};harness.saveGame();});
assert.equal(await page.locator('#bSave').textContent(),'Error');await invariant();
await page.evaluate(()=>{Storage.prototype.setItem=window.originalSetItem;delete window.originalSetItem;});
log('Storage write failure reports Error without disrupting the game');
for(let i=0;i<10;i++){
 await page.evaluate(i=>{harness.startAuto();harness.switchGrid(i%2 ? 7 : 2);harness.startAuto();},i);await page.waitForTimeout(25);await invariant();
}
await page.evaluate(()=>harness.stopAuto());await page.waitForTimeout(350);await invariant();log('Grid changes in motion; isolated saves, BigInt and Auto restoration');
await fixture();await page.evaluate(()=>{harness.doMove(0);const r=[...harness.engine.movements][0];r.animation.cancel();});await idle();await invariant();
assert.equal(await page.evaluate(()=>harness.engine.actions.size),0);log('External animation.cancel recovers without a stuck transaction');
// Synthetic visibility/page lifecycle events verify application policy, not OS tab scheduling.
await fixture();await page.evaluate(()=>{
 harness.setSpeed(0);harness.doMove(0);window.fakeHidden=true;
 Object.defineProperty(document,'hidden',{configurable:true,get:()=>window.fakeHidden});document.dispatchEvent(new Event('visibilitychange'));
});
await page.waitForTimeout(380);assert(await page.evaluate(()=>!!harness.engine.transaction));
assert(await page.evaluate(()=>[...harness.engine.actions].every(r=>r.animation.playState==='paused')));
await page.evaluate(()=>{window.fakeHidden=false;document.dispatchEvent(new Event('visibilitychange'));});await idle();await invariant();
await page.evaluate(()=>{harness.startAuto();window.dispatchEvent(new Event('pagehide'));});assert.equal(await page.evaluate(()=>harness.engine.actions.size),0);
await page.evaluate(()=>window.dispatchEvent(new Event('pageshow')));await page.waitForTimeout(25);await page.evaluate(()=>harness.stopAuto());await invariant();
log('Visibility pause/resume and pagehide/pageshow policy (synthetic lifecycle events)');
await fixture();await page.evaluate(()=>harness.startAuto());await page.waitForTimeout(25);
await page.setViewportSize({width:390,height:844});await page.waitForTimeout(40);await invariant();
await page.evaluate(()=>harness.stopAuto());
for(const viewport of [{width:320,height:568},{width:844,height:390},{width:1280,height:800}]){
 await page.setViewportSize(viewport);await page.waitForTimeout(40);const bounds=await page.locator('#board').boundingBox();assert(bounds.x>=0&&bounds.y>=0&&bounds.x+bounds.width<=viewport.width+.1&&bounds.y+bounds.height<=viewport.height+.1);
}
log('Resize/orientation changes in motion, small mobile and desktop bounds');
await page.emulateMedia({reducedMotion:'reduce'});await fixture();await page.evaluate(()=>harness.doMove(0));await idle();await invariant();await page.emulateMedia({reducedMotion:'no-preference'});
const corruption=await page.evaluate(()=>{
 const h=harness,s=h.snapshot(),a={...s,tiles:[s.tiles[0],s.tiles[0]]},b={...s,score:'oops'},c={...s,started:false};
 const before=h.state.mi;return !h.restoreSnapshot(a)&&!h.restoreSnapshot(b)&&!h.restoreSnapshot(c)&&h.state.mi===before;
});assert(corruption);log('Reduced motion and rejected corrupt save snapshots');
await page.evaluate(()=>{harness.newGame();harness.saveGame();});const reloadSaved=await page.evaluate(()=>harness.snapshot());
await page.reload();await page.waitForTimeout(40);assert.equal(await page.evaluate(()=>harness.state.uid),reloadSaved.uid);await invariant();log('Actual page reload restores the namespaced manual save');
// Accelerated stress still creates real WAAPI Animation objects and resolves their finished promises.
if (!process.argv.includes('--functional-only')) {
await page.evaluate(()=>{harness.stopAuto();harness.state.mi=13;harness.newGame();});
results.stress.accelerated=await page.evaluate(async()=>{
 const h=harness;h.startAuto();let overlaps=0,maxNodes=0,maxActions=0;
 for(let i=0;i<3000;i++){
  // Drive manually to avoid rAF pacing in this separate, explicitly accelerated stress test.
  if(h.engine.autoFrame){cancelAnimationFrame(h.engine.autoFrame);h.engine.autoFrame=0;}
  if(!h.engine.transaction)h.doMove(h.pickDir());
  if([...h.engine.actions].some(r=>r.kind==='scale'))overlaps++;
  for(const r of [...h.engine.movements])r.animation.finish();
  await Promise.resolve();await Promise.resolve();await Promise.resolve();
  maxNodes=Math.max(maxNodes,h.views.size);maxActions=Math.max(maxActions,h.engine.actions.size);
  const s=h.state;
  if(new Set(s.tiles.map(t=>t.q+','+t.r)).size!==s.tiles.length || h.views.size!==s.tiles.length || h.engine.transaction)throw Error('Stress invariant at '+i);
 }
 h.stopAuto();return {moves:h.state.moveCount,completed:h.engine.completed,overlaps,maxNodes,maxActions,finalActions:h.engine.actions.size,hist:h.state.hist.length};
});assert.equal(results.stress.accelerated.moves,3000);assert(results.stress.accelerated.overlaps>0);await invariant();
log('3,000 accelerated WAAPI moves: no lost tiles, ghost leaks, overlapping transactions or deadlocks');
// Real-time Auto: no .finish() acceleration. At least 2,000 native-duration steps.
await page.setViewportSize({width:480,height:800});await page.waitForTimeout(40);
await page.evaluate(()=>{harness.stopAuto();harness.state.mi=2;harness.newGame();harness.setSpeed(3);});
await cdp.send('HeapProfiler.collectGarbage');
await cdp.send('Performance.enable');
const beforeMetrics=await cdp.send('Performance.getMetrics');
const started=Date.now();await page.evaluate(()=>harness.startAuto());
for(let segment=1;segment<=4;segment++){
 await page.waitForFunction(target=>harness.state.moveCount>=target,segment*500,{timeout:120000});
 await invariant();console.log('REAL AUTO '+segment*500+' moves');
}
await page.evaluate(()=>harness.stopAuto());await invariant();
await cdp.send('HeapProfiler.collectGarbage');
const afterMetrics=await cdp.send('Performance.getMetrics');
const metric=(m,name)=>m.metrics.find(v=>v.name===name)?.value;
results.stress.realTime={moves:await page.evaluate(()=>harness.state.moveCount),elapsedMs:Date.now()-started,
 heapBefore:metric(beforeMetrics,'JSHeapUsedSize'),heapAfter:metric(afterMetrics,'JSHeapUsedSize'),
 domNodesBefore:metric(beforeMetrics,'Nodes'),domNodesAfter:metric(afterMetrics,'Nodes'),
 final:await page.evaluate(()=>({tiles:harness.state.tiles.length,views:harness.views.size,actions:harness.engine.actions.size,movements:harness.engine.movements.size,hist:harness.state.hist.length,autoFrame:harness.engine.autoFrame}))};
assert(results.stress.realTime.moves>=2000);assert.equal(results.stress.realTime.final.actions,0);
log('2,000+ real-time Very Fast Auto moves with DOM/heap sampling and natural finished events');
}
assert.deepEqual(results.errors,[]);results.browser=await browser.version();
console.log(JSON.stringify(results,null,2));
if(process.env.RESULT_PATH)fs.writeFileSync(process.env.RESULT_PATH,JSON.stringify(results,null,2)+'\n');
await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
