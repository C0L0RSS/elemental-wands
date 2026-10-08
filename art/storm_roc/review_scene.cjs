// Record the viewer's exact CPU-side WebGL draw submissions for offline QA.
// This is not a browser or WebGL conformance check.
const fs=require('fs'),vm=require('vm'),path=require('path');
const here=__dirname,root=path.resolve(here,'../..'),html=fs.readFileSync(path.join(root,'.local-previews/storm-roc/index.html'),'utf8');
const payload=JSON.parse(html.match(/<script id="model-data" type="application\/json">([\s\S]*?)<\/script>/)[1]);
const width=Number(process.argv[3]||1200),height=Math.round(width*820/1200),only=process.argv.slice(4);
let records=[],textures=[],units={},active=0,current,vertices=[],uniform={};
const gl=new Proxy({
 createTexture(){const t={id:textures.length};textures.push(t);return t;},bindTexture(k,t){units[active]=t;current=t;},activeTexture(v){active=v;},
 texImage2D(...a){const im=a.at(-1);current.src=im.src||null;current.solid=im.solid||[0,0,0,0];},
 createShader:()=>({}),getShaderParameter:()=>true,createProgram:()=>({}),getProgramParameter:()=>true,
 getUniformLocation:(p,n)=>n,getAttribLocation:()=>0,createVertexArray:()=>({}),createBuffer:()=>({}),
 uniform4fv:(n,v)=>uniform[n]=Array.from(v),uniform1f:(n,v)=>uniform[n]=v,uniform1i:(n,v)=>uniform[n]=v,
 uniformMatrix4fv:(n,t,v)=>uniform[n]=Array.from(v),bufferData:(k,v)=>vertices=Array.from(v),
 drawArrays(){records.push({vertices:[...vertices],uniform:{...uniform},texture:units[0]?.id,mask:units[1]?.id});},
 TEXTURE0:0,TEXTURE1:1,
},{get:(o,k)=>k in o?o[k]:/^[A-Z_0-9]+$/.test(k)?k:()=>{}});
let elements={};function element(){return {textContent:'',value:0,style:{},classList:{toggle(){},remove(){}},setAttribute(){},addEventListener(){},dataset:{},clientWidth:width,clientHeight:height,getContext(){return gl;}};}
elements['#model-data']={textContent:JSON.stringify(payload)};elements['#view']=element();
const document={querySelector:s=>elements[s]||(elements[s]=element()),querySelectorAll:()=>[],body:{dataset:{}},createElement:()=>{const c={solid:[0,0,0,0]};c.getContext=()=>({fillStyle:'#fff',fillRect(){c.solid=[255,255,255,255];}});return c;}};
class Image{set src(v){this._src=v;this.complete=true;queueMicrotask(()=>this.onload?.());}get src(){return this._src;}}
let source=fs.readFileSync(path.join(here,'viewer.js'),'utf8').split('main().catch')[0];
const cases=[['perched-side','perched','side',false,true,false,false],['three-quarter','perched','hero',false,true,false,false],['hunched-side','hunched','side',false,true,false,false],['hunched-front','hunched','front',false,true,false,false],['peck','peck','side',false,true,false,false],['flight-side','flight','flightSide',false,true,false,false],['flight-three-quarter','flight','flightHero',false,true,false,false],['flight-top','flight','top',false,true,false,false],['spread-top','spread','top',false,true,false,false],['face-profile','perched','side',true,true,false,false],['standing-side','screech','side',false,true,false,false],['screech','screech','hero',false,true,false,false],['grab','grab','hero',false,true,false,false],['hunched-overlay','hunched','side',false,true,false,'posture'],['flight-overlay','flight','flightSide',false,true,false,'posture']];
source+="\nfunction reviewLandmarks(vp){\n const mats=pose();\n function project(p){const c=[0,1,2,3].map(j=>vp[j]*p[0]+vp[4+j]*p[1]+vp[8+j]*p[2]+vp[12+j]);return [(c[0]/c[3]+1)*width/2,(1-c[1]/c[3])*height/2];}\n const head=bones.find(b=>b.name==='head');const headScreen=project(transform(mats.head,head.pivot).map(v=>v/16));\n let beakX=-Infinity,tailX=Infinity;\n for(const g of geometry){if(g.name!=='upper_beak'&&!g.name.startsWith('tail_'))continue;for(let i=0;i<g.data.length;i+=8){const q=project(transform(mats[g.name],Array.from(g.data.slice(i,i+3))).map(v=>v/16));if(g.name==='upper_beak')beakX=Math.max(beakX,q[0]);else tailX=Math.min(tailX,q[0]);}}\n return {headScreen,beakX,tailX};\n}\n";
source+='\n(async()=>{await main();const views={side:[90,0],front:[0,9],hero:[-32,15],top:[180,89.99],flightSide:[60,16],flightHero:[-35,30]};const output=[];for(const [name,clip,view,focus,ortho,charged,overlay] of cases){if(only.length&&!only.includes(name))continue;Object.assign(state,{clip,time:name===\"standing-side\"?0:name===\"flight-top\"?1.5:(name===\"flight-side\"||name===\"flight-overlay\")?0:clip===\"screech\"?1.7:clip===\"peck\"?.50:clip===\"grab\"?1.2:0,motion:false,focus,ortho,charged,overlay:false,distance:clip===\"perched\"?22:21});[state.yaw,state.pitch]=views[view].map(rad);reset();draw();const draws=take();output.push({name,overlay,focus,clip,anchors:reviewLandmarks(draws[0].uniform.vp),draws});}finish({width,height,textures,cases:output});})().catch(e=>{throw e});';
const target=process.argv[2];
const ctx={document,Image,window:{},devicePixelRatio:1,performance:{now:()=>0},requestAnimationFrame(){},console,Float32Array,queueMicrotask,cases,textures,width,height,only,reset:()=>records=[],take:()=>records,finish:o=>fs.writeFileSync(target,JSON.stringify(o))};
vm.runInNewContext(source,ctx,{filename:'viewer.js'});
