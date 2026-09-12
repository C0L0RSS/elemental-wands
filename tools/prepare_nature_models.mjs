// Export the approved workshop into native Minecraft cuboids and render meshes.
// No bitmap is regenerated: Minecraft supplies pixel grain; face tints own the palette.
import fs from 'node:fs';
import path from 'node:path';
import {Mesh,add,sub,mul,norm,cross,mix} from '../art/nature/workshop/engine.js';
import {plant,seedPod,wrap,TREE_BLOCKS} from '../art/nature/workshop/models.js';
const base=path.resolve(import.meta.dirname,'../src/main/resources/assets/elementalwands');
const check=process.argv.includes('--check');
function save(name,value){const file=path.join(base,name),data=JSON.stringify(value)+'\n';if(check){if(!fs.existsSync(file)||fs.readFileSync(file,'utf8')!==data)throw Error('Nature export drift: '+name);}else{fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,data);}}
const rgb=c=>c.reduce((n,v)=>n*256+Math.max(0,Math.min(255,Math.round(v*255))),0);
class Cuboids extends Mesh {
 constructor(){super();this.elements=[];}
 bounds(points,c,texture='#grain'){
  if(this.plantScale)points=points.map(p=>[p[0]*.8,p[1]*.66,p[2]*.8]);
  const lo=[0,1,2].map(i=>this.precise?Math.min(...points.map(p=>p[i]))*16:Math.floor(Math.min(...points.map(p=>p[i]))*32)/2),hi=[0,1,2].map(i=>this.precise?Math.max(...points.map(p=>p[i]))*16:Math.ceil(Math.max(...points.map(p=>p[i]))*32)/2);
  // Model coordinates: one block is 16, origin centered on its bottom face.
  lo[0]+=8;lo[2]+=8;hi[0]+=8;hi[2]+=8;
  for(let i=0;i<3;i++){lo[i]=Math.max(-16,lo[i]);hi[i]=Math.min(32,Math.max(lo[i]+(this.precise?.001:.25),hi[i]));}
  // One texel per Minecraft pixel, rather than squeezing a whole texture onto every twig.
  const uvRange=(a,b)=>{let start=((a%16)+16)%16;return [start,Math.min(16,start+Math.max(.01,b-a))];};
  const faces=Object.fromEntries(['up','down','north','south','east','west'].map(f=>{
   let [u0,u1]=uvRange(f==='east'||f==='west'?lo[2]:lo[0],f==='east'||f==='west'?hi[2]:hi[0]);
   let [v0,v1]=f==='up'||f==='down'?uvRange(lo[2],hi[2]):uvRange(16-hi[1],16-lo[1]);
   return [f,{texture,uv:[u0,v0,u1,v1],...(c?{tintindex:rgb(c)}:{})}];
  }));
  // Stepped leaves overlap at their bases. Separate coplanar tops by a tiny,
  // deterministic fraction of a pixel so different leaf colors cannot shimmer.
  if(!this.precise){let lift=this.elements.length*.001;lo[1]+=lift;hi[1]+=lift;}
  this.elements.push({from:lo,to:hi,faces});
 }
 box(p,s,c,mat=0,rotation=0){let points=[];for(let x of [-1,1])for(let y of [-1,1])for(let z of [-1,1])points.push(add(p,[x*s[0]/2*Math.cos(rotation)+z*s[2]/2*Math.sin(rotation),y*s[1]/2,-x*s[0]/2*Math.sin(rotation)+z*s[2]/2*Math.cos(rotation)]));this.bounds(points,c);}
 tube(points,r,c,mat=0,sides=4,end=r*.5){for(let i=0;i<points.length-1;i++){let a=points[i],b=points[i+1],length=Math.hypot(...sub(b,a)),steps=Math.max(1,Math.ceil(length/.13));for(let j=0;j<steps;j++){let q=mix(a,b,j/steps),v=mix(a,b,(j+1)/steps),radius=r+(end-r)*(i+j/steps)/(points.length-1);this.bounds([q.map(x=>x-radius),q.map(x=>x+radius),v.map(x=>x-radius),v.map(x=>x+radius)],c);}}}
 leaf(a,b,width,c,mat=1){let dir=norm(sub(b,a)),side=norm(cross(dir,[0,1,.001])),up=norm(cross(side,dir)),thick=Math.max(.008,Math.min(.055,width*.14));for(let i=0;i<6;i++){let w=width*[.28,.62,.94,1,.72,.32][i],ps=[];for(let t of [i/6,(i+1)/6])for(let x of [-w,w])for(let y of [-thick,thick])ps.push(add(mix(a,b,t),add(mul(side,x),mul(up,y))));this.bounds(ps,c);}}
 model(){return {ambientocclusion:true,textures:{particle:'minecraft:block/oak_leaves',grain:'minecraft:block/white_concrete_powder'},elements:this.elements};}
}
for(let stage=0;stage<4;stage++){let m=new Cuboids();m.plantScale=true;plant(m,[0,0,0],[.15,.43,.72,1][stage],0,true,0);save(`models/block/nature_seedling_${stage}.json`,m.model());}
save('blockstates/nature_seedling.json',{variants:Object.fromEntries([0,1,2,3].map(i=>['stage='+i,{model:`elementalwands:block/nature_seedling_${i}`}]))});
for(let name of ['nature_roots','nature_raft']){
 const variants=[];
 for(let i=0;i<3;i++){
  let m=new Cuboids();
  if(name==='nature_raft'){ // Only the seedling anchor carries a flower.
   for(let k=0;k<4;k++){let a=k*Math.PI/2;m.leaf([0,.025,0],[Math.cos(a)*.47,.035,Math.sin(a)*.47],.25,k%2?[.19,.72,.13]:[.47,.89,.19]);}
   m.tube([[-.3,.065,-.35],[.22,.065,-.35],[.36,.065,-.2],[.36,.065,.24],[.21,.065,.36],[-.31,.065,.36],[-.37,.065,.18],[-.37,.065,-.2],[-.3,.065,-.35]],.025,[.18,.52,.10],0,4,.025);
  } else { // Paired stepped roots, twigs and leaves, without destination flowers.
   for(let axis=0;axis<2;axis++){let ps=[[-.5,.05,-.10],[-.23,.05,-.10],[-.23,.07,.08],[.17,.07,.08],[.17,.05,-.06],[.5,.05,-.06]].map(p=>axis?[p[2],p[1],p[0]]:p);m.tube(ps,.038,[.18,.52,.10],0,4,.038);}
   for(let k=0;k<3;k++){let a=k*2.4+i,v=[Math.cos(a)*.26,.065,Math.sin(a)*.26];m.box(add(v,[0,.075,0]),[.045,.15,.045],[.54,.37,.16]);m.leaf(v,add(v,[Math.cos(a+.5)*.27,.09,Math.sin(a+.5)*.27]),.075,[.19,.72,.13]);}
  }
  save(`models/block/${name}_${i}.json`,m.model());variants.push({model:`elementalwands:block/${name}_${i}`,y:i*90});
 }
 save(`blockstates/${name}.json`,{variants:{'':variants}});
}
function pixel(m,face,u,v,w,h,c,depth){let p=[0,.5,0],size;if(face==='up'){p[0]+=u;p[1]+=.501;p[2]+=v;size=[w,depth,h];}else if(face==='east'||face==='west'){p[0]+=face==='east'?.501:-.501;p[1]+=v;p[2]+=u;size=[depth,h,w];}else{p[0]+=u;p[1]+=v;p[2]+=face==='south'?.501:-.501;size=[w,h,depth];}m.precise=true;m.box(p,size,c);m.precise=false;}
for(let name of ['nature_heartwood','nature_flowering_leaves']){
 let m=new Cuboids(),leaf=name.includes('leaves');
 const faces=Object.fromEntries(['up','down','north','south','east','west'].map(f=>[f,{texture:leaf?'#leaves':f==='up'||f==='down'?'#top':'#log',...(leaf?{tintindex:0x6be036}:{})}]));
 m.elements.push({from:[0,0,0],to:[16,16,16],faces});
 for(let face of ['north','south','east','west',...(leaf?['up']:[])]){
  if(leaf)for(let [u,v]of [[-.1875,.125],[.1875,-.1875]]){pixel(m,face,u,v,.1875,.1875,[.60,.27,.86],.012);pixel(m,face,u,v,.3125,.0625,[.94,.20,.56],.02);pixel(m,face,u,v,.0625,.3125,[1,.45,.69],.025);pixel(m,face,u,v,.0625,.0625,[1,.80,.15],.035);}
  else{pixel(m,face,0,0,.50,.625,[.17,.25,.09],.01);for(let [u,v,w,h]of [[0,0,.125,.375],[-.125,0,.125,.125],[.125,0,.125,.125],[0,.25,.125,.0625],[0,-.25,.125,.0625]])pixel(m,face,u,v,w,h,[.66,1,.19],.035);}
 }
 let model=m.model();Object.assign(model.textures,{log:'minecraft:block/oak_log',top:'minecraft:block/oak_log_top',leaves:'minecraft:block/oak_leaves',particle:leaf?'minecraft:block/oak_leaves':'minecraft:block/oak_log'});
 save(`models/block/${name}.json`,model);save(`blockstates/${name}.json`,{variants:{'':{model:`elementalwands:block/${name}`}}});
}
// Exact approved seed and wraps; RGB is carried per vertex and Minecraft supplies grain.
const meshes={};function mesh(name,draw){let m=new Mesh();draw(m);meshes[name]=m.data.map(n=>Math.round(n*100000)/100000);}
mesh('seed',m=>seedPod(m,[0,0,0],.62,0));
for(let [type,w,h,boss]of [['human',.6,1.8,false],['wide',1.4,.9,false],['boss',3.2,5.2,true]])for(let stacks=1;stacks<=5;stacks++)for(let mode of stacks===5?['lingering','rooted','opening','resisting']:['lingering'])mesh(`${type}_${stacks}_${mode}`,m=>wrap(m,w,h,stacks,mode,0,boss));
save('nature/meshes.json',meshes);
save('nature/tree_layout.json',TREE_BLOCKS);
console.log(`Nature models ${check?'verified':'exported'} from approved workshop; ${Object.keys(meshes).length} render meshes; no bitmap changes.`);
