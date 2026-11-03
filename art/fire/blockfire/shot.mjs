// Screenshot one frame with headless Chrome: node shot.mjs <scene> <t> [cam=orbit] [out.png] [extra query, e.g. "dusk=1&yaw=1.2"]
// Needs the preview served: python3 -m http.server 8347 --bind 127.0.0.1 --directory art/fire
// Output defaults to .local-previews/blockfire/shots/<scene>-<cam>-<t>.png under the repo root.
// Drives Chrome over the DevTools protocol and waits for body[data-ready] plus two drawn frames, so shots taken in
// parallel (or while textures are still arriving) are never blank or half-loaded.
import{spawn}from'node:child_process';
import{existsSync,mkdirSync,readFileSync,rmSync,writeFileSync}from'node:fs';
import{dirname,resolve}from'node:path';
import{fileURLToPath}from'node:url';
import{tmpdir}from'node:os';

const here=dirname(fileURLToPath(import.meta.url)),root=resolve(here,'../../..');
const[scene,t='1',cam='orbit',outArg,extra='']=process.argv.slice(2);
if(!scene){console.error('usage: node shot.mjs <scene> <t> [cam] [out.png] [extra query]');process.exit(2)}
const out=resolve(outArg||`${root}/.local-previews/blockfire/shots/${scene}-${cam}-${t}.png`);
mkdirSync(dirname(out),{recursive:true});
const url=`http://127.0.0.1:8347/blockfire/?scene=${scene}&t=${t}&cam=${cam}&still=1${extra?'&'+extra:''}`;
const W=1280,H=760;
// Prefer Playwright's headless shell; fall back to Chrome.
const shell=`${process.env.HOME}/Library/Caches/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-mac-arm64/chrome-headless-shell`;
const chrome=existsSync(shell)?shell:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const profile=`${tmpdir()}/blockfire-chrome-${process.pid}`;
const proc=spawn(chrome,[...(chrome===shell?[]:['--headless=new']),'--disable-gpu-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader',
 '--hide-scrollbars',`--window-size=${W},${H}`,'--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{stdio:'ignore'});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const done=code=>{proc.kill('SIGKILL');setTimeout(()=>{try{rmSync(profile,{recursive:true,force:true})}catch{}process.exit(code)},200)};
setTimeout(()=>{console.error('shot timed out: '+url);done(1)},90000).unref();

try{
 // The browser writes its debugging port into the profile once it is listening.
 let port=null;
 for(let i=0;i<300&&!port;i++){await sleep(100);try{port=readFileSync(`${profile}/DevToolsActivePort`,'utf8').split('\n')[0]}catch{}}
 if(!port)throw Error('Chrome did not start');
 const page=(await(await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(p=>p.type==='page');
 const ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise((ok,no)=>{ws.onopen=ok;ws.onerror=no});
 let id=0;const pending=new Map();
 ws.onmessage=e=>{const msg=JSON.parse(e.data);if(msg.id&&pending.has(msg.id)){pending.get(msg.id)(msg);pending.delete(msg.id)}};
 const send=(method,params={})=>new Promise((ok,no)=>{const n=++id;pending.set(n,r=>r.error?no(Error(r.error.message)):ok(r.result));ws.send(JSON.stringify({id:n,method,params}))});
 const evaluate=async(expression)=>(await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true})).result.value;
 await send('Emulation.setDeviceMetricsOverride',{width:W,height:H,deviceScaleFactor:1,mobile:false});
 // python's http.server keeps a backlog of 5, so with several browsers loading at once a module request can be reset
 // and app.js never starts. If the document has loaded but the app hasn't come up, load the page again.
 let ready='';
 for(let attempt=0;attempt<8&&ready!=='true'&&ready!=='error';attempt++){
  if(attempt)await sleep(200+Math.random()*600);
  await send('Page.navigate',{url});
  for(let i=0;i<120&&ready!=='true'&&ready!=='error';i++){
   await sleep(100);
   let state=null;try{state=await evaluate('JSON.stringify([document.readyState,typeof window.__blockfire,document.body?.dataset.ready||""])')}catch{continue}
   const[doc,app,flag]=JSON.parse(state);ready=flag;
   if(doc==='complete'&&app==='undefined'&&i>=30)break;   // a module failed to load: retry
  }
 }
 if(ready==='error')throw Error('page error: '+await evaluate('document.getElementById("error").textContent'));
 if(ready!=='true')throw Error('page never became ready: '+url);
 // Still mode redraws once after 150 ms; wait past it and two more frames so the canvas holds the final image.
 await evaluate('new Promise(r=>setTimeout(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>r(1))),300))');
 const shot=await send('Page.captureScreenshot',{format:'png'});
 writeFileSync(out,Buffer.from(shot.data,'base64'));
 console.log(out);ws.close();done(0);
}catch(e){console.error(e.message);done(1)}
