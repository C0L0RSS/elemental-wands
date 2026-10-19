// Fit regional generated artwork onto unchanged geometry and UV islands.
const fs=require('fs'),path=require('path');
// Sharp from npm, or another copy named by SHARP_PATH.
const sharp=require(process.env.SHARP_PATH||'sharp');
const here=__dirname,root=path.resolve(here,'../..'),layout=JSON.parse(fs.readFileSync(path.join(here,'layout.json')));
const dot=(a,b)=>a.reduce((s,n,i)=>s+n*b[i],0);
const sub=(a,b)=>a.map((n,i)=>n-b[i]);
const norm=a=>{const d=Math.hypot(...a);return a.map(n=>n/d)};
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
function rotate(v,r=[0,0,0]){
 let [x,y,z]=v;const [rx,ry,rz]=r.map((a,i)=>a*Math.PI/180*(i===2?1:-1));
 [y,z]=[y*Math.cos(rx)-z*Math.sin(rx),y*Math.sin(rx)+z*Math.cos(rx)];
 [x,z]=[x*Math.cos(ry)+z*Math.sin(ry),-x*Math.sin(ry)+z*Math.cos(ry)];
 [x,y]=[x*Math.cos(rz)-y*Math.sin(rz),x*Math.sin(rz)+y*Math.cos(rz)];return [x,y,z];
}
function axes(c,face){
 const [x,y,z]=c.origin,[w,h,d]=c.size,X=x+w,Y=y+h,Z=z+d;
 // UV origin, U axis, V axis in exactly the viewer's top-left PNG convention.
 const f={north:[[x,Y,z],[1,0,0],[0,-1,0]],south:[[X,Y,Z],[-1,0,0],[0,-1,0]],
 east:[[X,Y,z],[0,0,1],[0,-1,0]],west:[[x,Y,Z],[0,0,-1],[0,-1,0]],
 up:[[x,Y,Z],[1,0,0],[0,0,-1]],down:[[x,y,z],[1,0,0],[0,0,1]]}[face];
 const pivot=c.pivot||[0,0,0];return [rotate(sub(f[0],pivot),c.rotation).map((v,i)=>v+pivot[i]),rotate(f[1],c.rotation),rotate(f[2],c.rotation)];
}
function region(p,i){
 if(/wing_|primary_|secondary_|root_tertial|tail_/.test(p.bone))return null;
 if(['eye','mouth','mask','talon','skin'].includes(p.mat))return null;
 if(['upper_beak','lower_beak'].includes(p.bone)||p.mat.startsWith('horn'))return {id:7,flow:[0,0,-1],density:1};
 if(p.bone==='head'||p.bone.startsWith('crest'))return {id:p.mat==='ruff'||p.kind==='hackle'?1:0,flow:[0,-.22,1],density:1.3};
 if(p.bone.includes('cheek'))return {id:1,flow:[0,-.7,1],density:1.2};
 if(p.bone.startsWith('neck'))return {id:p.bone==='neck_tip_skin'?1:2,flow:[0,-1,.15],density:1};
 if(p.bone==='body'){
  if(i===4||i===5||i===7||i===9)return {id:4,flow:[0,0,1],density:1};
  if(i===2||i===3)return {id:5,flow:[0,-.15,1],density:1};
  return {id:3,flow:i===1||p.kind==='plate'?[0,-1,0]:[0,-.5,1],density:1};
 }
 return {id:6,flow:[0,-1,.1],density:1};
}
async function main(){
 const meta=await sharp(path.join(here,'material-regions-v2.png')).metadata();
 const sw=Math.floor(meta.width/4),sh=Math.floor(meta.height/2),panels=[];
 const names=['crown','cheek-nape','neck-hackles','breast','back-mantle','belly','leg-plumes','beak-horn'];
 fs.mkdirSync(path.join(here,'regions-v2'),{recursive:true});
 for(let i=0;i<8;i++){
  const tile=await sharp(path.join(here,'material-regions-v2.png')).extract({left:i%4*sw,top:Math.floor(i/4)*sh,width:sw,height:sh}).resize(32,64,{kernel:'nearest'}).ensureAlpha().raw().toBuffer();
  panels.push(tile);await sharp(tile,{raw:{width:32,height:64,channels:4}}).png().toFile(path.join(here,'regions-v2',names[i]+'.png'));
 }
 const previous=await sharp(path.join(here,'storm-roc-vulture-v1.png')).ensureAlpha().raw().toBuffer();
 const atlas=Buffer.from(previous),glowPath=path.join(root,'art/storm_roc_mod/candidate/storm_roc_glowmask.png');
 const glow=await sharp(glowPath).ensureAlpha().raw().toBuffer();
 let changed=0,preserved=0,cleared=0;const orientations=[];
 for(const [i,p] of layout.parts.entries()){
  const r=region(p,i);
  for(const [face,uv] of Object.entries(p.cube.uv)){
   const [x,y]=uv.uv,[w,h]=uv.uv_size;
   if(r){
    const [origin,U,V]=axes(p.cube,face),normal=norm(cross(U,V));
    let D=sub(r.flow,normal.map(n=>n*dot(r.flow,normal)));
    if(Math.hypot(...D)<.05)D=V;
    D=norm(D);const S=norm(cross(D,normal)),src=panels[r.id];
    orientations.push({part:i,bone:p.bone,face,region:names[r.id],flow_uv:[dot(U,D),dot(V,D)]});
    for(let v=-1;v<=h;v++)for(let u=-1;u<=w;u++){
     const point=origin.map((n,k)=>n+U[k]*(Math.max(0,Math.min(w-1,u))+.5)/p.density+V[k]*(Math.max(0,Math.min(h-1,v))+.5)/p.density);
     const sx=((Math.floor(dot(point,S)*r.density)%32)+32)%32,sy=((Math.floor(dot(point,D)*r.density)%64)+64)%64;
     const dest=((y+v)*layout.atlas[0]+x+u)*4;src.copy(atlas,dest,(sy*32+sx)*4,(sy*32+sx)*4+4);
    }
    changed++;
   }else preserved++;
   // Remove only the body's lightning/glow, including its one-pixel UV gutters.
   if(p.bone==='body')for(let v=-1;v<=h;v++)for(let u=-1;u<=w;u++){
    const dest=((y+v)*layout.atlas[0]+x+u)*4;if(glow[dest+3])cleared++;glow.fill(0,dest,dest+4);
   }
  }
 }
 for(const p of layout.parts.filter(p=>/wing_|primary_|secondary_|root_tertial|tail_/.test(p.bone)))for(const uv of Object.values(p.cube.uv)){
  const [x,y]=uv.uv,[w,h]=uv.uv_size;
  for(let v=-1;v<=h;v++)for(let u=-1;u<=w;u++){const k=((y+v)*layout.atlas[0]+x+u)*4;if(!atlas.subarray(k,k+4).equals(previous.subarray(k,k+4)))throw Error('Wing texture changed');}
 }
 const raw={width:layout.atlas[0],height:layout.atlas[1],channels:4};
 await sharp(atlas,{raw}).png().toFile(path.join(here,'storm-roc-vulture-v2.png'));
 await sharp(glow,{raw}).png().toFile(path.join(here,'storm-roc-glow-v2.png'));
 fs.writeFileSync(path.join(here,'mapping-v2.json'),JSON.stringify({changed,preserved,cleared_glow_texels:cleared,wings_and_tail:'byte-identical to v1',orientations},null,2)+'\n');
 console.log({changed,preserved,cleared_glow_texels:cleared,wings_and_tail:'byte-identical to v1'});
}
main().catch(e=>{console.error(e);process.exit(1)});
