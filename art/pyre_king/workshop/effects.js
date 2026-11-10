(()=>{
'use strict';
const {M,rad,norm,poseBones}=PyreCore;
const add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]),mul=(a,k)=>a.map(v=>v*k);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const smooth=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);};
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2],dist=(a,b)=>Math.hypot(...sub(a,b));
function inverseRigid(m){const o=M.id();for(let r=0;r<3;r++)for(let c=0;c<3;c++)o[c*4+r]=m[r*4+c];const p=M.dir(o,[-m[12],-m[13],-m[14]]);o[12]=p[0];o[13]=p[1];o[14]=p[2];return o;}
// The shortest rotation about point c that turns direction a onto direction b.
function turn(c,a,b){a=norm(a);b=norm(b);const v=cross(a,b),s=Math.hypot(...v);if(s<1e-9)return M.id();
 const [x,y,z]=mul(v,1/s),ang=Math.atan2(s,dot(a,b)),C=Math.cos(ang),S=Math.sin(ang),T=1-C;
 const r=new Float32Array([T*x*x+C,T*x*y+S*z,T*x*z-S*y,0,T*x*y-S*z,T*y*y+C,T*y*z+S*x,0,T*x*z+S*y,T*y*z-S*x,T*z*z+C,0,0,0,0,1]);
 return [M.t(...c),r,M.t(...mul(c,-1))].reduce(M.mul);}
// Two-bone IK, as rig.py solves the clips: the arm reaches the fist's new place on the grip
// without stretching, and the elbow keeps bending the way the sculpt bends it.
function reach(model,rest,matrices,side,delta){
 const idx=n=>model.bones.findIndex(b=>b.name===n),at=i=>M.apply(rest[i],model.bones[i].pivot);
 const ui=idx(side+'_upper_arm'),fi=idx(side+'_forearm'),hi=idx(side+'_hand');
 const S=at(ui),E=at(fi),H=at(hi),T=M.apply(delta,H),l1=dist(E,S),l2=dist(H,E);
 const n=norm(sub(T,S)),D=Math.min(Math.max(dist(T,S),Math.abs(l1-l2)+1e-4),l1+l2-1e-4);
 const hint=sub(E,S),m=norm(sub(hint,mul(n,dot(hint,n)))),ca=(l1*l1+D*D-l2*l2)/(2*l1*D);
 const E2=add(S,add(mul(n,l1*ca),mul(m,l1*Math.sqrt(Math.max(0,1-ca*ca)))));
 const upper=turn(S,sub(E,S),sub(E2,S)),fore=M.mul(turn(E2,sub(M.apply(upper,H),E2),sub(T,E2)),upper);
 matrices[ui]=M.mul(upper,rest[ui]);matrices[fi]=M.mul(fore,rest[fi]);
 return dist(M.apply(fore,H),T);
}
// A review rig exercise, not a proposed combat clip. The middle is exactly level.
function sweep(t){const u=((t%4)+4)%4;return u<.8?0:u<1.8?smooth((u-.8)):u<2.2?1:1-smooth((u-2.2)/1.8);}
function posed(model,manifest,t=0,motion=false,planted=false,clip=null){
 const rest=poseBones(model,clip,clip?Math.max(0,t):0).matrices,matrices=rest.slice(),si=model.bones.findIndex(b=>b.name==='sword');
 const king=model.id==='geometry.pyre_king',s=model.bones[si];
 if(motion){const g=norm(M.dir(rest[si],[0,1,0])),k=sweep(t),e0=Math.asin(Math.max(-1,Math.min(1,g[1]))),y0=Math.abs(g[0])+Math.abs(g[2])<1e-9?0:Math.atan2(g[2],g[0]);
  const elevation=e0*(1-k),yaw=y0-.35*k;
  const d=[Math.cos(elevation)*Math.cos(yaw),Math.sin(elevation),Math.cos(elevation)*Math.sin(yaw)];
  const rx=Math.asin(d[2]),rz=-Math.atan2(d[0],d[1]);
  // The blade turns about the middle of the grip, between the fists, as a swordsman turns it.
  const grip=add(s.pivot,[0,8.8/16,0]);
  const target=[M.t(...M.apply(rest[si],grip)),M.rz(rz),M.rx(rx),M.t(...mul(grip,-1))].reduce(M.mul);
  const delta=M.mul(target,inverseRigid(rest[si]));
  const carried=new Set(['sword','pommel_ring','sword_fire','right_hand','left_hand','right_wrist_shackle','hanging_wrist_chain']);
  model.bones.forEach((b,i)=>{if(carried.has(b.name))matrices[i]=M.mul(delta,rest[i]);});
  if(king)for(const side of ['right','left'])reach(model,rest,matrices,side,delta);
 }
 if(planted){const flip=M.mul(M.t(0,4.5,0),M.rz(Math.PI));for(let i=0;i<matrices.length;i++)matrices[i]=M.mul(flip,matrices[i]);}
 return {matrices,...blade(manifest,matrices[si],s)};
}
const PLANT=M.mul(M.t(0,4.5,0),M.rz(Math.PI));
// The blade's sockets and flame roots for the sword bone's world matrix R.
function blade(manifest,R,s){
 const bf=manifest.blade_fire;
 const sockets=bf.sockets.map(p=>M.apply(R,add(s.pivot,[-p[0]/16,p[1]/16,p[2]/16])));
 // Each flame roots partway toward the blade's uppermost edge at its socket (toward the
 // edge on a slanted blade, the top face on a level one; on the axis when upright), so the
 // upper half of the blade burns in one band while its lower edge still shows hot steel.
 const ux=norm(M.dir(R,[1,0,0])),uz=norm(M.dir(R,[0,0,1])),lean=v=>Math.tanh(v*8),f=bf.root_edge_fraction;
 const roots=sockets.map((p,i)=>{const [hw,ht]=bf.socket_half_extents[i];
  return add(p,add(mul(ux,f*hw/16*lean(ux[1])),mul(uz,ht/16*lean(uz[1]))));});
 return {sockets,roots};
}
// The same blade for a clip at time t, posing only the sword and its ancestors (the fire's
// history re-poses the clip several times a frame; the rest of the body does not matter).
const chains=new WeakMap();
function bladeAt(model,manifest,t,planted,clip){
 let chain=chains.get(model);
 if(!chain){const byName=new Map(model.bones.map(b=>[b.name,b]));const list=[];for(let b=byName.get('sword');b;b=b.parent!=null?byName.get(b.parent):null)list.unshift(b);
  chain={bones:list};chains.set(model,chain);}
 const m=poseBones(chain,clip,clip?Math.max(0,t):0).matrices,R=m[m.length-1];
 return blade(manifest,planted?M.mul(PLANT,R):R,chain.bones[chain.bones.length-1]);
}
// The minimal rotation that turns world up onto the unit direction d, applied to v. Upright
// flames keep their world X/Z sheets; a leaning flame carries them over with it.
function fromUp(d,v){if(d[1]<-.9999)return [v[0],-v[1],-v[2]];
 const w=[d[2],0,-d[0]],wv=cross(w,v);return add(add(v,wv),mul(cross(w,wv),1/(1+d[1])));}
// A smooth curve through a socket's recent positions: track[j] is where it was j*step seconds
// ago. Catmull-Rom tangents (half the chord across each sample; the newest and oldest take
// their own slice's chord, so the curve keeps its true speed at both ends instead of easing in),
// each limited to TANGENT_LIMIT times the shorter slice beside it, so a sudden stop or start
// (a short slice next to a long one) cannot throw the curve into a loop that wobbles as the
// samples slide along the motion.
const TANGENT_LIMIT=2;
function slopes(track){const J=track.length-1;return track.map((_,j)=>{const l=j>0?sub(track[j],track[j-1]):null,r=j<J?sub(track[j+1],track[j]):null;
 if(!l)return r||[0,0,0];if(!r)return l;const m=mul(add(l,r),.5),lm=Math.hypot(...m),cap=TANGENT_LIMIT*Math.min(Math.hypot(...l),Math.hypot(...r));
 return lm>cap?mul(m,cap/lm):m;});}
function along(track,m,age,step){const J=track.length-1;if(J<1)return track[0];
 const u=Math.max(0,Math.min(J,age/step)),j=Math.min(J-1,Math.floor(u)),f=u-j,f2=f*f,f3=f2*f;
 const b=track[j],c=track[j+1],mb=m[j],mc=m[j+1],h00=2*f3-3*f2+1,h10=f3-2*f2+f,h01=3*f2-2*f3,h11=f3-f2;
 return [0,1,2].map(k=>h00*b[k]+h10*mb[k]+h01*c[k]+h11*mc[k]);}
// A socket's recent path sampled densely enough to measure: pts[m] is where it was m*h seconds
// ago and len[m] how far it has travelled since then; at(age) is the curve itself.
function route(track,step,substeps){
 const m=slopes(track),n=(track.length-1)*substeps,h=step/substeps,pts=[],len=[0],at=age=>along(track,m,age,step);
 for(let i=0;i<=n;i++)pts.push(at(i*h));
 for(let i=1;i<=n;i++)len.push(len[i-1]+dist(pts[i],pts[i-1]));
 return {at,pts,len,h};
}
// The route of a point w of the way from route a's point to route b's.
function between(a,b,w){
 const mix=(p,q)=>p.map((v,k)=>v+(q[k]-v)*w),pts=a.pts.map((p,m)=>mix(p,b.pts[m])),len=[0];
 for(let m=1;m<pts.length;m++)len.push(len[m-1]+dist(pts[m],pts[m-1]));
 return {at:age=>mix(a.at(age),b.at(age)),pts,len,h:a.h};
}
// How far a route has travelled within `age` seconds, and the age at which it had travelled d.
function lengthAt(r,age){const n=r.len.length-1;if(n<1)return 0;const u=Math.max(0,Math.min(n,age/r.h)),m=Math.min(n-1,Math.floor(u));return r.len[m]+(r.len[m+1]-r.len[m])*(u-m);}
function ageAt(r,d){const L=r.len,n=L.length-1;if(d<=0||n<1)return 0;if(d>=L[n])return n*r.h;
 let m=0;while(L[m+1]<d)m++;const s=L[m+1]-L[m];return (m+(s>1e-12?(d-L[m])/s:0))*r.h;}
const at=(r,age)=>r.at(age);
// Limit every joint of a spine to turn at most maxTurn radians: where a segment bends further,
// it and everything after it rotate rigidly about the joint (lengths kept) until it does not.
function unfold(P,maxTurn){
 for(let k=1;k+1<P.length;k++){const a=sub(P[k],P[k-1]),b=sub(P[k+1],P[k]),la=Math.hypot(...a),lb=Math.hypot(...b);
  if(la<1e-6||lb<1e-6)continue;const A=mul(a,1/la),c=dot(A,b)/lb;if(c>=Math.cos(maxTurn))continue;
  let e=sub(b,mul(A,dot(A,b)));const el=Math.hypot(...e);
  e=el>1e-9?mul(e,1/el):norm(cross(A,Math.abs(A[1])<.9?[0,1,0]:[1,0,0]));
  const R=turn(P[k],b,add(mul(A,Math.cos(maxTurn)),mul(e,Math.sin(maxTurn))));
  for(let j=k+1;j<P.length;j++)P[j]=M.apply(R,P[j]);}
 return P;
}
function tangent(P,k){const d=sub(P[Math.min(P.length-1,k+1)],P[Math.max(0,k-1)]),l=Math.hypot(...d);return l>1e-7?mul(d,1/l):[0,1,0];}
function quadNormal(pts){const c=cross(sub(pts[1],pts[0]),sub(pts[3],pts[0])),l=Math.hypot(...c);if(l>1e-12)return mul(c,1/l);
 const e=cross(sub(pts[2],pts[0]),sub(pts[3],pts[1])),m=Math.hypot(...e);return m>1e-12?mul(e,1/m):[0,0,1];}
// One cutout sheet along a spine of points, root first: the texture's rounded root (v=v0) sits
// at the first point and its tip (v=0) at the last. side(k) is the half-width vector at point
// k and off(k) moves the sheet off the spine. Collinear segments cover exactly the quad they replace.
function strip(out,P,side,off,u,du,v0,alpha,trail){
 const K=P.length-1;
 for(let k=0;k<K;k++){
  const a=add(P[k],off(k)),b=add(P[k+1],off(k+1)),sa=side(k),sb=side(k+1);
  const pts=[sub(a,sa),add(a,sa),add(b,sb),sub(b,sb)],va=v0*(1-k/K),vb=v0*(1-(k+1)/K);
  out.push({pts,n:quadNormal(pts),uvs:[[u,va],[u+du,va],[u+du,vb],[u,vb]],bone:'world_fire',fire:true,alpha,trail});
 }
}
// While the sword is out on its chain, draw the chain from the wrist shackle to the pommel
// ring: heavy links that sag a little, textured like the short tether they replace.
// The curse's loose chains run from the chest to each marker bone a clip has moved off it
// (Unbound): every third link glows hot, each ends in a barbed hook, and scene() sets them
// alight. Returns the loose chains that are drawn.
const LOOSE_MIN=.6,HOOK=1.25;
function longChain(model,matrices,faces,bows){
 const at=name=>{const i=model.bones.findIndex(b=>b.name===name);return i<0?null:M.apply(matrices[i],model.bones[i].pivot);};
 const face=name=>{const f=faces.find(f=>f.bone===name);return f?f.uvs:[[0,0],[0,0],[0,0],[0,0]];};
 const iron=face('hanging_wrist_chain'),hot=face('chest_shard');
 const a=at('right_wrist_shackle'),b=at('sword');
 if(a&&b&&Math.hypot(...sub(b,a))>=.8)chainBetween(a,b,faces,iron);
 const loose=looseEnds(model,matrices).filter(c=>dist(c.from,c.to)>=LOOSE_MIN);
 for(const c of loose){c.bow=bows&&bows[c.index]||[0,0,0];chainBetween(c.from,c.to,faces,iron,hot,c.bow);
  const h=hookPoints(c.from,c.to,c.bow);
  for(let k=0;k+1<h.length;k++)beam(faces,h[k],h[k+1],.11-.012*k,k>=h.length-3?hot:iron);}
 return loose;
}
// Every loose-chain marker: where its chain leaves the chest and where the marker is.
function looseEnds(model,matrices){
 const out=[];
 model.bones.forEach((bone,i)=>{if(!bone.name.startsWith('unbound_chain_')||bone.parent==null)return;
  const pi=model.bones.findIndex(q=>q.name===bone.parent);if(pi<0)return;
  out.push({index:out.length,from:M.apply(matrices[pi],bone.pivot),to:M.apply(matrices[i],bone.pivot)});});
 return out;
}
// A chain's line from a to b, sagging a little: u runs 0 to 1. bow swings it out sideways by
// that much two thirds of the way along (a chain being swung round: its far end trails).
function chainLine(a,b,bow){const sag=Math.min(.6,.04*dist(a,b));
 return u=>{const p=add(add(a,mul(sub(b,a),u)),[0,-sag*4*u*(1-u),0]);return bow?add(p,mul(bow,6.75*u*u*(1-u))):p;};}
// How far each loose chain bows as it is swung. A swung chain's far end trails behind the
// rest, and the chain has weight: it takes a moment to bend when the swing starts, and when
// the far end stops the rest swings on past it, bows the other way a little and settles. So
// the bow is the far end's sideways velocity over the last second passed through a slightly
// springy filter (a damped oscillator's response, BOW_PERIOD s and BOW_DAMPING), times
// BOW_SECONDS, eased toward at most BOW_SHARE of the chain's length.
const BOW_SECONDS=.09,BOW_SHARE=.22,BOW_PERIOD=.55,BOW_DAMPING=.45,BOW_STEP=.05,BOW_SAMPLES=20;
const BOW_WEIGHTS=(()=>{const w0=2*Math.PI/BOW_PERIOD,wd=w0*Math.sqrt(1-BOW_DAMPING*BOW_DAMPING),w=[];
 for(let k=0;k<BOW_SAMPLES;k++){const a=(k+.5)*BOW_STEP;w.push(Math.exp(-BOW_DAMPING*w0*a)*Math.sin(wd*a));}
 const sum=w.reduce((x,y)=>x+y,0);return w.map(x=>x/sum);})();
// The marker bones and their ancestors alone, for posing the chains' ends at other moments.
const markerRigs=new WeakMap();
function endsAt(model,clip,time){
 let rig=markerRigs.get(model);
 if(!rig){const byName=new Map(model.bones.map(b=>[b.name,b])),keep=new Set();
  for(const b of model.bones)if(b.name.startsWith('unbound_chain_'))for(let q=b;q;q=q.parent!=null?byName.get(q.parent):null)keep.add(q.name);
  rig={bones:model.bones.filter(b=>keep.has(b.name))};markerRigs.set(model,rig);}
 return looseEnds(rig,poseBones(rig,clip,Math.max(0,time)).matrices);
}
function chainBows(model,clip,t,matrices){
 const now=looseEnds(model,matrices);if(!clip||!now.some(c=>dist(c.from,c.to)>=LOOSE_MIN))return null;
 const past=[now];for(let k=1;k<=BOW_SAMPLES;k++)past.push(endsAt(model,clip,t-k*BOW_STEP));
 return now.map((c,i)=>{let v=[0,0,0];
  for(let k=0;k<BOW_SAMPLES;k++)v=add(v,mul(sub(past[k][i].to,past[k+1][i].to),BOW_WEIGHTS[k]/BOW_STEP));
  const d=norm(sub(c.to,c.from));v=sub(v,mul(d,dot(v,d)));v[1]=0;
  const size=Math.hypot(...v)*BOW_SECONDS,cap=BOW_SHARE*dist(c.from,c.to);
  return size>1e-6&&cap>1e-6?mul(v,cap*Math.tanh(size/cap)/Math.hypot(...v)):[0,0,0];});
}
// The hook on a loose chain's far end, lying flat: a shank carrying on along the chain, then a
// curve round to the left (the way the chains sweep) and a barb pointing back toward him.
function hookPoints(a,b,bow){
 const line=chainLine(a,b,bow),d=norm(sub(b,line(.97))),flat=Math.hypot(d[0],d[2]);
 const s=flat>1e-3?norm(cross([0,1,0],d)):[1,0,0],at=(x,y)=>add(b,add(mul(d,x*HOOK),mul(s,y*HOOK)));
 return [b,at(.55,0),at(.82,.2),at(.86,.5),at(.66,.74),at(.34,.68)];
}
// A square bar from p to q, half-width w.
function beam(faces,p,q,w,uvs){
 const d=norm(sub(q,p)),side=norm(cross(d,Math.abs(d[1])>.9?[1,0,0]:[0,1,0])),up=cross(side,d);
 const corner=(e,x,y)=>add(add(e,mul(side,x*w)),mul(up,y*w));
 for(const [x0,y0,x1,y1] of [[-1,-1,1,-1],[1,-1,1,1],[1,1,-1,1],[-1,1,-1,-1]]){
  let pts=[corner(p,x0,y0),corner(p,x1,y1),corner(q,x1,y1),corner(q,x0,y0)],n=norm(cross(sub(pts[1],pts[0]),sub(pts[3],pts[0])));
  if(dot(n,sub(mul(add(pts[0],pts[2]),.5),mul(add(p,q),.5)))<0){pts=[pts[1],pts[0],pts[3],pts[2]];n=mul(n,-1);}   // faces outward
  faces.push({bone:'long_chain',pts,n,uvs});}
}
function chainBetween(a,b,faces,uvs,hot,bow){
 const length=Math.hypot(...sub(b,a));
 const n=Math.max(4,Math.round(length/.32)),point=chainLine(a,b,bow);
 for(let i=0;i<n;i++){
  const p=point(i/n),q=point((i+1)/n),d=norm(sub(q,p)),side=norm(cross(d,Math.abs(d[1])>.9?[1,0,0]:[0,1,0])),up=cross(side,d);
  const w=i%2?.05:.11,h=i%2?.11:.05,c=mul(add(p,q),.5),half=Math.hypot(...sub(q,p))*.62;
  const corner=(s,x,y)=>add(add(add(c,mul(d,s*half)),mul(side,x*w)),mul(up,y*h));
  const quads=[[[-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1]],[[-1,-1,1],[-1,1,1],[1,1,1],[1,-1,1]],
   [[-1,-1,-1],[-1,-1,1],[1,-1,1],[1,-1,-1]],[[-1,1,-1],[1,1,-1],[1,1,1],[-1,1,1]]];
  for(const qd of quads){const pts=qd.map(([s,x,y])=>corner(s,x,y));faces.push({bone:'long_chain',pts,n:norm(cross(sub(pts[1],pts[0]),sub(pts[2],pts[0]))),uvs:hot&&i%3===1?hot:uvs});}
 }
}
// opts.travel is the body's own world velocity in blocks/s, for clips that play in place (a walk
// or run): the fire moves through still air, so its history drifts back at that speed.
function scene(model,manifest,t=.65,motion=false,planted=false,clip=null,opts=null){
 const pose=posed(model,manifest,t,motion,planted,clip);
 const faces=model.faces.filter(f=>!f.empty).map(f=>{const m=pose.matrices[f.bone];return {...f,bone:model.bones[f.bone].name,pts:f.pts.map(p=>M.apply(m,p)),n:norm(M.dir(m,f.n))};});
 const loose=longChain(model,pose.matrices,faces,chainBows(model,clip,t,pose.matrices));
 const flames=[],fire=[];const noise=(t,i)=>.5+.27*Math.sin(t*7.3+i*2.71)+.23*Math.sin(t*11.9+i*7.13);
 const bf=manifest.blade_fire,sw=bf.swing,peaks=new Map(bf.peak_sockets),frames=bf.atlas_grid[0],du=1/frames;
 const hash=i=>{const x=Math.sin(i*127.1+311.7)*43758.5453;return x-Math.floor(x);};
 const drift=(opts&&opts.travel||[0,0,0]).map(v=>+v||0);
 // The blade's recent motion: the same pose solved a few times, history_step_seconds apart.
 // Looping clips wrap; one-shots and the test sweep never sample before time zero.
 const step=sw.history_step_seconds,samples=sw.history_samples,span=samples*step;
 const moving=motion||!!clip||drift.some(v=>v!==0);
 const past=a=>clip&&clip.loop?((t-a)%clip.animation_length+clip.animation_length)%clip.animation_length:Math.max(0,t-a);
 const history=[pose];
 for(let j=1;j<=samples+1;j++)history.push(!moving?pose:motion?posed(model,manifest,past(j*step),motion,planted,clip):bladeAt(model,manifest,past(j*step),planted,clip));
 // A slice in which any socket outruns teleport_speed is a jump, not a swing (a parent switch, a
 // teleport, a loop seam). A jump can end partway through the next newer slice, and an
 // interpolated one smears over several, so the cut also takes that next slice and any newer
 // ones beside it that still run faster than half that speed; one extra sample past the window
 // catches the tail of a jump that has just aged out of it. The fire forgets everything before
 // the cut, so it never traces the jump.
 const slice=history.slice(1).map((p,j)=>Math.max(...p.sockets.map((q,s)=>dist(q,history[j].sockets[s])))/step);
 let cut=slice.findIndex(v=>v>sw.teleport_speed_blocks_per_second);
 if(cut>=0){cut=Math.max(0,cut-1);while(cut>0&&slice[cut-1]>sw.teleport_speed_blocks_per_second/2)cut--;for(let k=cut+1;k<=samples;k++)history[k]=history[cut];}
 history.length=samples+1;
 const blades=history.map(p=>norm(sub(p.sockets[p.sockets.length-1],p.sockets[0])));
 // Each flame root at every sampled age, carried back by the body's own travel.
 const track=i=>history.map((p,j)=>sub(p.roots[i],mul(drift,j*step)));
 const ramp=([a,b],v)=>v>a?smooth(Math.log(v/a)/Math.log(b/a)):0;
 // How a root has been travelling, read over speed_samples equal slices of the window: speed is
 // the fastest slice, velocity and now the latest. stream, how hard the air pulls the fire (rise
 // lost, thinning, curl, flutter), ramps with each slice's speed and is averaged with weights
 // falling linearly with age, so it builds quickly when a swing starts and eases back to zero
 // over the whole window after it stops, never in a step. Ramps run in log(speed), so the
 // response spreads over a wide range of speeds instead of one narrow band.
 function motionOf(r){
  if(!moving)return {velocity:[0,0,0],speed:0,now:0,stream:0};
  const N=sw.speed_samples,h=span/N;let stream=0,total=0,speed=0;
  for(let k=1;k<=N;k++){const v=(lengthAt(r,k*h)-lengthAt(r,(k-1)*h))/h,w=N+1-k;speed=Math.max(speed,v);
   stream+=w*ramp(sw.stream_speed_blocks_per_second,v);total+=w;}
  const velocity=mul(sub(r.pts[0],r.pts[1]),1/r.h);
  return {velocity,speed,now:Math.hypot(...velocity),stream:stream/total};
 }
 const active=bf.active_socket_indices,n=pose.roots.length,roots=pose.roots.map((_,i)=>route(track(i),step,sw.path_substeps));
 // Licks are nudged 0..45% of the way to the next root so the row has no rhythm; peaks and the
 // wake use the plain roots.
 const licks=active.map(i=>between(roots[i],roots[Math.min(i+1,n-1)],.45*hash(i))),plain=active.map(i=>roots[i]);
 const lickMotion=licks.map(motionOf),plainMotion=plain.map(motionOf);
 function tongue(i,tall=0){
  const index=active.indexOf(i),r=tall?plain[index]:licks[index],m=tall?plainMotion[index]:lickMotion[index];
  const n=noise(t,i+(tall?31:0)),tg=sw.tongue;
  // Every socket carries a low, soft lick of irregular size, rooted on the blade's upper
  // edge; together they make one fringe. Only the peaks and the hilt flames have white-hot roots.
  const base=.28+.2*hash(i+17)-.04*Math.abs(i-8)/8;
  const height=(tall||base)*(.84+.28*n),thin=1-tg.thinning*m.stream;
  const width=(tall?.30+.16*hash(i+9):.30+.12*hash(i+5))*(.88+.18*noise(t+.3,i))*thin,depth=.24*thin;
  const pool=tall||i<2?bf.tongue_frames:bf.lick_frames,u=pool[((Math.floor(t*8)+i+(tall?2:0))%4+4)%4]/frames;
  // Burning gas leaves the steel and stays where the blade was, rising. A flame lives `life`
  // seconds; over that life its root travelled `travelled` blocks. Moving, the flame lies back
  // along the newest `reach` blocks of that path (lean of the way), reach easing toward a limit
  // set by its own height, so a fast swing lays the flames back instead of growing them without
  // end, and its rise drops toward rise_kept of its height. Distances are measured along the
  // path itself, so a flame always spans the motion it just made, however fast it was before.
  const life=Math.min(span,tg.seconds*(.75+.5*hash(i+23))),travelled=lengthAt(r,life);
  const lean=moving?ramp(sw.lean_speed_blocks_per_second,travelled/life):0;
  const limit=height*tg.stretch+tg.reach_blocks,reach=limit*Math.tanh(travelled/limit);
  const rise=height*(1-m.stream*(1-tg.rise_kept)),bend=tg.curl*m.stream,flutter=tg.flutter_blocks*m.stream,phase=i*2.3+(tall?1.1:0);
  // The rounded root sinks just below the edge, so the flame grows out of the steel. A flame
  // that is not leaning at all is one straight quad per sheet, exactly the approved still fire.
  const K=lean>0?tg.segments:1,o=r.pts[0],sink=tall?.06:.07,P=[];
  for(let k=0;k<=K;k++){const x=k/K,d=mul(sub(at(r,ageAt(r,x*reach)),o),lean);
   // Dragged downward (the blade rising), the flame stays near level instead of hanging.
   if(d[1]<0)d[1]*=tg.sag;
   let q=add(add(o,d),[0,rise*(x-bend*x*(1-x))-sink,0]);const a=flutter*x*x;
   // flutter (blocks) waves the dragged flame along the blade and up and down, growing toward
   // its tip, so a fast flame wavers like fire in a gale instead of lying as a straight streak.
   if(a)q=add(add(q,mul(blades[0],a*Math.sin(t*9.1+phase+x*3.3))),[0,.6*a*Math.sin(t*13.7+phase*1.7+x*4.1),0]);
   P.push(q);}
  // A moving flame is never shorter than the same flame standing still: when the path it lies
  // along is shorter than the rise it gave up, the rise comes back until the tip is height away.
  const floor=()=>{const c=sub(P[K],P[0]),flat=c[0]*c[0]+c[2]*c[2];
   if(c[1]*c[1]+flat<height*height){const e=Math.sqrt(Math.max(0,height*height-flat))-c[1];P.forEach((p,k)=>{p[1]+=e*k/K;});}};
  // Where the path doubles back (the blade reversing), the strip would fold over itself: no
  // joint turns more than max_turn_degrees; the rest of the flame swings straighter instead.
  floor();unfold(P,tg.max_turn_degrees*Math.PI/180);floor();
  const T=P.map((_,k)=>tangent(P,k)),A=T.map(d=>fromUp(d,[1,0,0])),B=T.map(d=>fromUp(d,[0,0,1]));
  // Front and back sheets clear the blade's faces so it cannot hide the hot roots; a cross
  // sheet keeps the tongue visible from the side.
  strip(flames,P,k=>mul(A[k],width/2),k=>mul(B[k],-depth/2),u,du,1,1,false);
  strip(flames,P,k=>mul(A[k],-width/2),k=>mul(B[k],depth/2),u,du,1,1,false);
  strip(flames,P,k=>mul(B[k],depth/2),()=>[0,0,0],u,du,1,1,false);
  fire.push({socket:i,tall:!!tall,root:P[0],tip:P[K],spine:P,height,velocity:m.velocity,speed:m.speed,now:m.now,lean,stream:m.stream});
 }
 // The swing's wake: one sheet across the whole arc the blade swept, from the hilt root to the
 // tip root at each age back to the same roots at the next age (rows), so it reads from any
 // side that does not see the swing edge-on. Its window is set by the tip: the tip's newest path
 // eases toward max_length_blocks, and every root is taken over the same seconds, so the hilt
 // sweeps a short arc and the tip a long one. The sheet fades in from the blade (no hard start),
 // curls up by rise_blocks and fades out toward its old edge, and glows.
 function wake(){
  const st=sw.stream,n=plain.length,tip=plain[n-1],drawn=lengthAt(tip,Math.min(span,st.seconds));
  if(drawn<1e-4)return;
  const W=ageAt(tip,st.max_length_blocks*Math.tanh(drawn/st.max_length_blocks)),K=st.rows;if(W<1e-5)return;
  const V=plain.map((r,s)=>{const col=[];for(let k=0;k<=K;k++){const x=k/K,ph=s*1.9+.7,a=st.flutter_blocks*plainMotion[s].stream*x*x;
   col.push(add(at(r,W*x),[0,st.rise_blocks*x*x+.6*a*Math.sin(t*13.7+ph*1.7+x*4.1),0]));}return col;});
  // Opacity: as bright as the blade was fast when it passed there (wake_speed ramp), dimming as
  // that fire ages out of the window; none at the blade (the cores cover it, and the sheet never
  // starts on a hard edge), full by fade_in of the way back, held to hold, gone at the old edge.
  // Fire rises: the part of a rising blade's sheet left hanging below its root fades out over
  // drop_fade_blocks, so it never hangs as a curtain.
  const A=plain.map((r,s)=>{const col=[];for(let k=0;k<=K;k++){const x=k/K,a=W*x,h=r.h;
   const v=(lengthAt(r,a+h)-lengthAt(r,Math.max(0,a-h)))/(a+h-Math.max(0,a-h)),drop=r.pts[0][1]-V[s][k][1];
   col.push(ramp(sw.wake_speed_blocks_per_second,v)*(1-smooth(a/span))*smooth(x/st.fade_in)*(1-smooth((x-st.hold)/(1-st.hold)))*(1-smooth(drop/st.drop_fade_blocks)));}return col;});
  // One lick frame (the middle frame_span of its width) spans columns_per_frame sockets, its
  // broad root along the blade and its tongue back along the sweep: v runs from v_start at the
  // blade to v_end at the old edge, so the sheet is continuous near the blade and breaks into a
  // few ragged tongues behind.
  const G=st.columns_per_frame,v=k=>st.v_start+(st.v_end-st.v_start)*k/K;
  for(let s=0;s+1<n;s++){
   const g=Math.floor(s/G),u0=bf.lick_frames[((Math.floor(t*8)+g)%4+4)%4]/frames+du*(1-st.frame_span)/2,f=du*st.frame_span/G;
   const ua=u0+f*(s-g*G),ub=u0+f*(s+1-g*G);
   for(let k=0;k<K;k++){
    const fade=[A[s][k],A[s+1][k],A[s+1][k+1],A[s][k+1]],peak=Math.max(...fade);if(peak<.01)continue;
    const pts=[V[s][k],V[s+1][k],V[s+1][k+1],V[s][k+1]],normal=quadNormal(pts),uvs=[[ua,v(k)],[ub,v(k)],[ub,v(k+1)],[ua,v(k+1)]];
    // Two layers on the same quad: a normally blended tint that turns what is behind it the
    // colour of fire (on a pale sky additive light alone washes out), then an additive glow.
    flames.push({pts,n:normal,uvs,bone:'world_fire',fire:true,alpha:peak*st.tint,alphas:fade.map(a=>a*st.tint),trail:true});
    flames.push({pts,n:normal,uvs,bone:'world_fire',fire:true,alpha:peak*st.glow,alphas:fade.map(a=>a*st.glow),blend:'add',trail:true});
   }
  }
 }
 active.forEach(i=>{tongue(i);if(peaks.has(i))tongue(i,peaks.get(i));});
 if(moving)wake();
 // The loose chains burn along their whole length: a low flame every half block or so and a
 // tall one on each hook. A flame stands upright where its chain is still; where the chain
 // moves it lies back along the way it came (up to 1.1 blocks) and loses part of its rise.
 if(loose.length&&clip){
  const dt=.03,then=endsAt(model,clip,t-dt);
  const flame=(root,prev,i,tall)=>{
   const v=mul(sub(root,prev),1/dt),speed=Math.hypot(...v),k=Math.min(1,speed/30);
   const h=(tall?.95:.34+.3*hash(i+41))*(.84+.28*noise(t,i+60)),w=(tall?.5:.3)*(.88+.18*noise(t+.3,i));
   const tip=add(add(root,speed>1e-6?mul(v,-Math.min(.05,1.1/speed)):[0,0,0]),[0,h*(1-.45*k),0]),d=norm(sub(tip,root));
   const pool=tall?bf.tongue_frames:bf.lick_frames,u=pool[((Math.floor(t*8)+i)%4+4)%4]/frames,P=[add(root,[0,-.05,0]),tip];
   strip(flames,P,()=>mul(fromUp(d,[1,0,0]),w/2),()=>[0,0,0],u,du,1,1,false);
   strip(flames,P,()=>mul(fromUp(d,[0,0,1]),w/2),()=>[0,0,0],u,du,1,1,false);};
  for(const c of loose){const o=then[c.index],now=chainLine(c.from,c.to,c.bow),was=chainLine(o.from,o.to,c.bow);
   const n=Math.max(2,Math.round(dist(c.from,c.to)/.6));
   for(let j=0;j<n;j++){const id=c.index*40+j,u=(j+.5+.5*(hash(id+7)-.5))/n;flame(now(u),was(u),id,false);}
   const h=hookPoints(c.from,c.to,c.bow),g=hookPoints(o.from,o.to,c.bow);
   flame(h[2],g[2],c.index*40+38,true);flame(h[4],g[4],c.index*40+39,false);}
 }
 return {faces,flames,sockets:pose.sockets,fire};
}
globalThis.PyreEffects={scene,posed,sweep,reach};
})();
