(()=>{
'use strict';
const {M,rad,norm,poseBones}=PyreCore;
const add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]),mul=(a,k)=>a.map(v=>v*k);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const smooth=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);};
function inverseRigid(m){const o=M.id();for(let r=0;r<3;r++)for(let c=0;c<3;c++)o[c*4+r]=m[r*4+c];const p=M.dir(o,[-m[12],-m[13],-m[14]]);o[12]=p[0];o[13]=p[1];o[14]=p[2];return o;}
function frame(v){const y=norm(v),x=norm(cross(Math.abs(y[2])>.9?[1,0,0]:[0,0,1],y)),z=cross(x,y);return new Float32Array([...x,0,...y,0,...z,0,0,0,0,1]);}
// A review rig exercise, not a proposed combat clip. The middle is exactly level.
function sweep(t){const u=((t%4)+4)%4;return u<.8?0:u<1.8?smooth((u-.8)):u<2.2?1:1-smooth((u-2.2)/1.8);}
function posed(model,manifest,t=0,motion=false,planted=false,clip=null){
 const rest=poseBones(model,clip,clip?Math.max(0,t):0).matrices,matrices=rest.slice(),si=model.bones.findIndex(b=>b.name==='sword');
 const king=model.id==='geometry.pyre_king',s=model.bones[si];
 if(motion){const g=norm(M.dir(rest[si],[0,1,0])),k=sweep(t),e0=Math.asin(Math.max(-1,Math.min(1,g[1]))),y0=Math.abs(g[0])+Math.abs(g[2])<1e-9?0:Math.atan2(g[2],g[0]);
  const elevation=e0*(1-k),yaw=y0-.35*k;
  const d=[Math.cos(elevation)*Math.cos(yaw),Math.sin(elevation),Math.cos(elevation)*Math.sin(yaw)];
  const rx=Math.asin(d[2]),rz=-Math.atan2(d[0],d[1]);
  const target=[M.t(...M.apply(rest[si],s.pivot)),M.rz(rz),M.rx(rx),M.t(...mul(s.pivot,-1))].reduce(M.mul);
  const delta=M.mul(target,inverseRigid(rest[si]));
  const carried=new Set(['sword','pommel_ring','sword_fire','right_hand','left_hand','right_wrist_shackle','hanging_wrist_chain']);
  model.bones.forEach((b,i)=>{if(carried.has(b.name))matrices[i]=M.mul(delta,rest[i]);});
  // Aim each forearm from its fixed elbow to the moving hand. This simple test
  // stretches the forearms; production combat will need full two-hand IK.
  if(king)for(const side of ['right','left']){
   const fi=model.bones.findIndex(b=>b.name===side+'_forearm'),hi=model.bones.findIndex(b=>b.name===side+'_hand');
   const elbow=model.bones[fi].pivot,hand=model.bones[hi].pivot,next=M.apply(delta,hand),a=sub(hand,elbow),b=sub(next,elbow);
   const stretch=[M.t(...elbow),frame(b),M.s(1,Math.hypot(...b)/Math.hypot(...a),1),inverseRigid(frame(a)),M.t(...mul(elbow,-1))].reduce(M.mul);
   matrices[fi]=M.mul(stretch,rest[fi]);
  }
 }
 if(planted){const flip=M.mul(M.t(0,4.5,0),M.rz(Math.PI));for(let i=0;i<matrices.length;i++)matrices[i]=M.mul(flip,matrices[i]);}
 const sockets=manifest.blade_fire.sockets.map(p=>M.apply(matrices[si],add(s.pivot,[-p[0]/16,p[1]/16,p[2]/16])));
 return {matrices,sockets};
}
function block(center,size,frameIndex,alpha,trail=false,frames=8){
 const [x,y,z]=sub(center,mul(size,.5)),[X,Y,Z]=add(center,mul(size,.5));
 // Front/back cutouts clear the blade's surfaces so it cannot hide the hot
 // roots; one central cross sheet keeps the tongue visible from the side.
 const quads=[[[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[0,0,-1]],
  [[X,y,Z],[x,y,Z],[x,Y,Z],[X,Y,Z],[0,0,1]],
  [[center[0],y,z],[center[0],y,Z],[center[0],Y,Z],[center[0],Y,z],[1,0,0]]];
 const u=frameIndex/frames,v=0,du=1/frames,dv=1;
 return quads.map(q=>({pts:q.slice(0,4),n:q[4],uvs:[[u,v+dv],[u+du,v+dv],[u+du,v],[u,v]],bone:'world_fire',fire:true,alpha,trail}));
}
// While the sword is out on its chain, draw the chain from the wrist shackle to the pommel
// ring: heavy links that sag a little, textured like the short tether they replace.
function longChain(model,matrices,faces){
 const at=name=>{const i=model.bones.findIndex(b=>b.name===name);return i<0?null:M.apply(matrices[i],model.bones[i].pivot);};
 const a=at('right_wrist_shackle'),b=at('sword');if(!a||!b)return;
 const length=Math.hypot(...sub(b,a));if(length<.8)return;
 const source=faces.find(f=>f.bone==='hanging_wrist_chain');const uvs=source?source.uvs:[[0,0],[0,0],[0,0],[0,0]];
 const n=Math.max(4,Math.round(length/.32)),sag=Math.min(.6,.04*length);
 const point=u=>add(add(a,mul(sub(b,a),u)),[0,-sag*4*u*(1-u),0]);
 for(let i=0;i<n;i++){
  const p=point(i/n),q=point((i+1)/n),d=norm(sub(q,p)),side=norm(cross(d,Math.abs(d[1])>.9?[1,0,0]:[0,1,0])),up=cross(side,d);
  const w=i%2?.05:.11,h=i%2?.11:.05,c=mul(add(p,q),.5),half=Math.hypot(...sub(q,p))*.62;
  const corner=(s,x,y)=>add(add(add(c,mul(d,s*half)),mul(side,x*w)),mul(up,y*h));
  const quads=[[[-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1]],[[-1,-1,1],[-1,1,1],[1,1,1],[1,-1,1]],
   [[-1,-1,-1],[-1,-1,1],[1,-1,1],[1,-1,-1]],[[-1,1,-1],[1,1,-1],[1,1,1],[-1,1,1]]];
  for(const qd of quads){const pts=qd.map(([s,x,y])=>corner(s,x,y));faces.push({bone:'long_chain',pts,n:norm(cross(sub(pts[1],pts[0]),sub(pts[2],pts[0]))),uvs});}
 }
}
function scene(model,manifest,t=.65,motion=false,planted=false,clip=null){
 const pose=posed(model,manifest,t,motion,planted,clip);
 const faces=model.faces.filter(f=>!f.empty).map(f=>{const m=pose.matrices[f.bone];return {...f,bone:model.bones[f.bone].name,pts:f.pts.map(p=>M.apply(m,p)),n:norm(M.dir(m,f.n))};});
 longChain(model,pose.matrices,faces);
 const flames=[];const noise=(t,i)=>.5+.27*Math.sin(t*7.3+i*2.71)+.23*Math.sin(t*11.9+i*7.13);
 const bf=manifest.blade_fire,peaks=new Map(bf.peak_sockets),frames=bf.atlas_grid[0];
 const hash=i=>{const x=Math.sin(i*127.1+311.7)*43758.5453;return x-Math.floor(x);};
 function tongue(p,i,age=0,tall=0,sockets=pose.sockets){
  const fade=1-age/bf.trail_lifetime_seconds,n=noise(t,i+(tall?31:0));
  // Every socket carries a low, soft lick of irregular size, nudged along the blade so
  // the row has no rhythm; together they make one envelope. Only the peaks and the
  // flames at the hilt have white-hot roots.
  const next=sockets[Math.min(i+1,sockets.length-1)],nudge=tall?0:.45*hash(i);
  const q=add(p,mul(sub(next,p),nudge));
  // The developer wants the whole blade on fire: every socket burns. Licks stay
  // upright and only a little wider than their spacing, so they read as flames, not shelves.
  const base=.22+.18*hash(i+17)-.04*Math.abs(i-8)/8;
  const height=(tall||base)*(.84+.28*n)*fade;
  const width=(tall?.30+.16*hash(i+9):.27+.11*hash(i+5))*(.88+.18*noise(t+.3,i))*fade;
  const hot=tall||i<2;
  const pool=hot?bf.tongue_frames:bf.lick_frames;
  const frameIndex=pool[((Math.floor(t*8)+i+(tall?2:0))%4+4)%4];
  flames.push(...block(add(q,[0,height*.5-(tall?.10:.15)+age*.8,0]),
    [width,height,.24*fade],frameIndex,age>0?.40*fade:1,age>0,frames));
 }
 const active=bf.active_socket_indices;
 active.forEach(i=>{tongue(pose.sockets[i],i);if(peaks.has(i))tongue(pose.sockets[i],i,0,peaks.get(i));});
 if(motion||clip)for(const age of [.05,.10,.15]){
  if(t<age)continue;
  const past=posed(model,manifest,t-age,motion,planted,clip),older=posed(model,manifest,Math.max(0,t-age-.02),motion,planted,clip);
  active.forEach(i=>{const p=past.sockets[i];if(Math.hypot(...sub(p,older.sockets[i]))/.02>bf.trail_speed_threshold_blocks_per_second)tongue(p,i,age,0,past.sockets);});
 }
 return {faces,flames,sockets:pose.sockets};
}
globalThis.PyreEffects={scene,posed,sweep};
})();
