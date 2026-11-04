// Shared void-glass primitives. Every function is a pure function of (mesh time, arguments, seed): no hidden state, no
// Math.random, so a Java renderer can reproduce the same pieces every frame (the Fire and Wind kits' contract).
//
// Look rules:
// - Void glass is dark see-through glass with a bright frame and stars inside (the glass and nebula bands carry their
//   own alpha). Cores are opaque void, ringed by a glowing corona that faces the viewer, so they read as eclipses.
// - Space falls inward. Streams accelerate toward a core, stretch along their fall and shrink to nothing as they
//   arrive; only bursts move outward, and they are short.
// - Pieces are few and large rather than many and small (hundreds of small cubes read as noise). They needn't be
//   cubes: plates, slabs and stepped lenses are all oriented boxes.
// - Anything that loops restarts while it is invisible. No dark outlines, glow halos or cross-shaped glints.
import{ATLAS,add,sub,mul,dot,cross,norm,len,clamp,lerp,smoothstep,rnd,TAU,UP}from'./engine.js';

/** Atlas rows in textures/blockspace.png. */
export const BAND={void:0,glass:1,nebula:2,rim:3,amethyst:4,blue:5,red:6,starlight:7};
// How much each band ignores scene light (1 = full-bright). Space glows faintly everywhere; amethyst is lit like a block.
const GLOW=[1,1,1,1,.55,1,1,1];
/** Bands that are always see-through glass (their texels carry alpha). */
const GLASSY=new Set([BAND.glass,BAND.nebula]);
/** Atlas frame rate; each piece offsets its frame by its seed so neighbours don't animate in sync. */
export const FPS=6;
/** Default extra opacity on glass pieces (the texture already makes the interior see-through). */
export const GLASS_ALPHA=.9;

export const AXES=[[1,0,0],[0,1,0],[0,0,1]];
export const easeOut=k=>1-(1-clamp(k))**2;
export const easeIn=k=>clamp(k)**2;
const frac=x=>x-Math.floor(x);

/** Right-handed basis whose second axis is dir; roll spins it around dir. */
export function basis(dir,roll=0){
 const y=norm(dir),ref=Math.abs(y[1])>.92?[1,0,0]:UP;
 let x=norm(cross(y,ref)),z=cross(x,y);
 if(roll){const c=Math.cos(roll),s=Math.sin(roll);[x,z]=[add(mul(x,c),mul(z,s)),sub(mul(z,c),mul(x,s))]}
 return[x,y,z];
}
/** Basis whose first axis runs along dir (texture u follows the flow on the long faces); second axis leans toward up. */
export function flowAxes(dir,up=UP,roll=0){
 const x=norm(dir);let ref=sub(up,mul(x,dot(up,x)));if(len(ref)<1e-3)ref=Math.abs(x[0])<.9?[1,0,0]:[0,0,1];
 let y=norm(sub(ref,mul(x,dot(ref,x)))),z=cross(x,y);
 if(roll){const c=Math.cos(roll),s=Math.sin(roll);[y,z]=[add(mul(y,c),mul(z,s)),sub(mul(z,c),mul(y,s))]}
 return[x,y,z];
}
/** Right-handed basis whose third axis is normal (a plate's face), first axis level where it can be. */
export function faceAxes(normal,roll=0){
 const z=norm(normal),ref=Math.abs(z[1])>.92?[1,0,0]:UP;
 let x=norm(cross(ref,z)),y=cross(z,x);
 if(roll){const c=Math.cos(roll),s=Math.sin(roll);[x,y]=[add(mul(x,c),mul(y,s)),sub(mul(y,c),mul(x,s))]}
 return[x,y,z];
}
/** Basis turned around the vertical axis by yaw (radians). Axis 0 points along [cos, 0, sin]; axis 2 is the right hand. */
export function yawAxes(yaw){const f=[Math.cos(yaw),0,Math.sin(yaw)];return[f,UP,cross(f,UP)]}
/** A tumbling basis: a fixed random orientation per seed, turning at rate (radians per second). */
export function tumble(seed,t,rate=1){
 const k=sphereDir(Math.floor(rnd(seed)*29),29);return basis(k,rnd(seed+.3)*TAU+t*rate).map(v=>rotate(v,UP,rnd(seed+.6)*TAU));
}
/** Rodrigues rotation of v around unit axis k by angle a. */
export function rotate(v,k,a){const c=Math.cos(a),s=Math.sin(a);return add(add(mul(v,c),mul(cross(k,v),s)),mul(k,dot(k,v)*(1-c)))}
/** Even directions on a sphere (golden spiral). */
export function sphereDir(i,count){const y=1-2*(i+.5)/count,r=Math.sqrt(Math.max(0,1-y*y)),a=i*2.399963;return[Math.cos(a)*r,y,Math.sin(a)*r]}
/** Unit vector from p toward the camera (for pieces that face the viewer, like coronas and the Blink slit). */
export const toEye=(m,p)=>norm(sub(m.eye,p));

/**
 * One void-glass piece: an oriented box. size is an edge length or [x, y, z] extents along axes. Glass and nebula are
 * always see-through (sorted, blended); other bands are solid unless alpha is below 1.
 */
export function piece(m,p,size,{band='glass',seed=0,axes=AXES,alpha=null,glow=null,color=[1,1,1],fps=FPS}={}){
 const s=typeof size==='number'?[size,size,size]:size;
 const row=BAND[band];if(row===undefined)throw Error('Unknown band: '+band);
 const glassy=GLASSY.has(row),a=alpha??(glassy?GLASS_ALPHA:1);
 if(a<=.02)return;
 const frame=Math.floor(m.time*fps+rnd(seed+.77)*4)%4;
 m.box(p,mul(s,.5),axes,glassy||a<1?ATLAS.airKey:ATLAS.key,{cell:[frame,row],emissive:glow??GLOW[row],color:[color[0],color[1],color[2],Math.min(1,a)],tone:glassy||a<1?'soft':true});
}
/** A flat plate facing normal: w x h across its face, thick through it. */
export function plate(m,p,normal,w,h,thick,opts={}){piece(m,p,[w,h,thick],{...opts,axes:faceAxes(normal,opts.roll||0)})}

/**
 * A void core: a stepped ball of opaque void (three crossing slabs around a cube), so its silhouette is round at any
 * angle. corona adds a broken ring of rim segments around it, facing the camera, so it reads as an eclipse; spin
 * turns the ring and the ball. r is the ball's radius.
 */
export function core(m,c,r,{seed=0,spin=1,corona=1,coronaBand='rim',segments=null,gaps=.18,t=m.time}={}){
 if(r<=.01)return;
 const ax=tumble(seed,t,spin*.6);
 piece(m,c,1.62*r,{band:'void',seed,axes:ax});
 for(let i=0;i<3;i++){const s=[1.22*r,1.22*r,1.22*r];s[i]=2*r;piece(m,c,s,{band:'void',seed:seed+i+1,axes:ax})}
 if(corona<=0)return;
 const n=toEye(m,c),count=segments??Math.max(8,Math.round(10+r*9));
 ring(m,c,n,r*1.28,{count,size:Math.max(.035,r*.16)*corona,depth:Math.max(.03,r*.1),stretch:1,band:coronaBand,phase:t*spin*.9+rnd(seed)*TAU,seed:seed+7,gaps,flat:true});
}

/**
 * count segments on a circle around normal. flat segments lie in the ring's plane (a corona, a field boundary);
 * otherwise they stand up like a band. gaps (0..1) drops that share of segments for a broken orbit.
 */
export function ring(m,center,normal,radius,{count=16,size=.12,depth=null,stretch=1,band='rim',alpha=null,phase=0,seed=0,gaps=0,flat=false}={}){
 if(radius<=1e-3||size<=1e-3)return;
 const n=norm(normal),a0=norm(cross(n,Math.abs(n[1])>.9?[1,0,0]:UP)),b0=cross(n,a0);
 const arc=TAU*radius/count;
 for(let i=0;i<count;i++){
  if(gaps&&rnd(seed+i*3.1)<gaps)continue;
  const a=phase+TAU*i/count,radial=add(mul(a0,Math.cos(a)),mul(b0,Math.sin(a))),tangent=cross(n,radial);
  const long=Math.min(arc*.86,size*stretch*3+arc*.4);
  // Right-handed: tangent x n = radial.
  piece(m,add(center,mul(radial,radius)),flat?[long,depth??size*.6,size]:[long,size,depth??size*.6],{band,seed:seed+i,axes:[tangent,n,radial],alpha});
 }
}

/**
 * A continuous inward stream around c: each piece starts at radius, spirals in faster and faster, stretches along its
 * fall and shrinks to nothing at the core, then restarts unseen at the rim. period is one piece's fall time. With
 * normal the stream lies in a disk (a vortex); without, pieces fall in from every direction. k (0..1) speeds up and
 * tightens the whole stream (a collapse). once makes a single fall instead of a loop: t is then the age, every piece
 * starts within the first third of period and none restarts.
 */
export function inflow(m,c,t,{count=12,radius=2,period=1.2,seed=0,size=.22,spin=2.4,normal=null,lift=0,band='glass',alpha=null,k=0,inner=.12,once=false}={}){
 const n=normal&&norm(normal),a0=n&&norm(cross(n,Math.abs(n[1])>.9?[1,0,0]:UP)),b0=n&&cross(n,a0);
 const at=(i,u)=>{
  // Lingers near the rim, then rushes in: r falls with u squared, so the fall visibly accelerates.
  const r=radius*(1-u*u)+inner*(1-u),ang=rnd(seed+i*1.31)*TAU+spin*u*u*(1+k);
  if(n){const radial=add(mul(a0,Math.cos(ang)),mul(b0,Math.sin(ang)));return add(c,add(mul(radial,r),mul(n,lift*(1-u))))}
  // From every direction: each piece falls along a curved path (its direction swings around a fixed side axis).
  const d=sphereDir(i,count),side=norm(cross(d,Math.abs(d[1])>.9?[1,0,0]:UP));
  return add(c,mul(rotate(d,side,ang*.3),r));
 };
 for(let i=0;i<count;i++){
  const u=once?(t/period-rnd(seed+i*.71)*.33)*1.5:clamp(frac(t/period*(1+k*1.5)+rnd(seed+i*.71)));
  if(u<=0||u>=1)continue;
  const grow=once?smoothstep(u/.08):smoothstep(u/.14),shrink=1-smoothstep((u-.72)/.28),s=size*grow*shrink*(.6+.4*rnd(seed+i*2.3));
  if(s<=.01)continue;
  const p=at(i,u),q=at(i,Math.min(1,u+.04)),dir=sub(q,p);if(len(dir)<1e-5)continue;
  const stretch=1+2.4*u*u;
  // In a disk the plates lie flat in it (a floor vortex reads from above); otherwise they turn freely.
  piece(m,p,n?[s*stretch,s*.18+.02,s*.8]:[s*stretch,s*.8,s*.18+.02],{band:i%5===2?'nebula':band,seed:seed+i,axes:flowAxes(dir,n||UP,n?0:rnd(seed+i)*TAU),alpha});
 }
}

/**
 * A short outward burst of shards from c: they fly out, slow, tumble and shrink away over duration. dir biases the
 * spray (null = every direction).
 */
export function spill(m,c,age,{count=10,speed=3,duration=.5,seed=0,size=.2,band='glass',dir=null,alpha=null,gravity=0}={}){
 const k=age/duration;if(k<0||k>=1)return;
 for(let i=0;i<count;i++){
  const d=dir?norm(add(mul(norm(dir),1.2),sphereDir(i,count))):sphereDir(i,count),v=speed*(.6+.5*rnd(seed+i));
  const p=add(add(c,mul(d,v*duration*easeOut(k)*.5)),[0,-gravity*age*age,0]),s=size*(1-smoothstep((k-.25)/.75))*(.6+.5*rnd(seed+i*1.9));
  if(s<=.01)continue;
  piece(m,p,[s,s,s*.25+.015],{band:i%4===3?'rim':band,seed:seed+i,axes:tumble(seed+i,age,6),alpha});
 }
}

/** Tiny twinkling starlight motes around c (the reduced-particle setting removes them). inward pulls them to c. */
export function stars(m,c,age,{count=8,spread=1,duration=1,seed=0,size=.06,inward=true}={}){
 for(let i=0;i<count;i++){
  const u=frac(age/duration+rnd(seed+i*.37)),d=sphereDir(i+Math.floor(rnd(seed+i)*5),count+5);
  const r=spread*(inward?(1-u)**1.3:u)*(.5+.5*rnd(seed+i*3.1));
  const tw=.55+.45*Math.sin((age*9+i*2.1)),s=size*tw*smoothstep(u/.15)*(1-smoothstep((u-.8)/.2));if(s<=.008)continue;
  piece(m,add(c,mul(d,r)),s,{band:'starlight',seed:seed+i,axes:basis(d,Math.PI/4)});
 }
}

/**
 * A wake pulled after a moving head: pieces are left along the path behind it (one every `every` seconds from `from`),
 * then dragged toward the head's current position, stretching along the pull and shrinking as they catch it.
 * headAt(time) gives the head's position; until is when shedding stops.
 */
export function chase(m,headAt,t,{from=0,until=Infinity,every=.03,duration=.32,spread=.35,size=.18,seed=0,band='glass',alpha=null}={}){
 const last=Math.min(t,until);
 for(let j=Math.max(0,Math.floor((t-duration-from)/every));from+j*every<=last;j++){
  const tb=from+j*every,a=t-tb;if(a<0||a>=duration)continue;
  const k=a/duration,id=seed+j*1.7;
  const birth=add(headAt(tb),mul(sphereDir(Math.floor(rnd(id)*23),23),spread*(.5+.5*rnd(id+.4))));
  const head=headAt(Math.min(t,until)),pull=easeIn(k)*.92,p=add(birth,mul(sub(head,birth),pull));
  const s=size*smoothstep(k/.12)*(1-smoothstep((k-.55)/.45));if(s<=.01)continue;
  const dir=sub(head,p);
  piece(m,p,[s*(1+1.2*k),s*.75,s*.2+.015],{band:j%4===1?'nebula':band,seed:id,axes:len(dir)>1e-4?flowAxes(dir,UP,rnd(id)*TAU):tumble(id,t),alpha});
 }
}

/**
 * The standing eclipse slit: a vertical lens of void rows framed by rim edges, turned to face the camera around the
 * vertical axis. open (0..1) widens it from a hairline; height is its full height from base (its bottom centre).
 */
export function slit(m,base,{height=2.2,width=.9,open=1,seed=0,rows=11,halo=true}={}){
 if(open<=.01)return;
 const e=sub(m.eye,base),fwd=norm([e[0],0,e[2]]),side=cross(UP,fwd),axes=[side,UP,fwd];
 const rowH=height/rows;
 for(let i=0;i<rows;i++){
  const v=(i+.5)/rows,shape=Math.pow(Math.sin(Math.PI*v),.75),w=Math.max(.04,width*open*shape);
  // Step the outline to sixteenths so the lens edge reads as pixel steps, not a smooth curve.
  const half=Math.round(w*8)/16+.03,p=add(base,[0,v*height,0]);
  piece(m,p,[half*2,rowH*1.02,.08],{band:'void',seed:seed+i,axes});
  for(const s of[-1,1]){
   piece(m,add(p,mul(side,s*(half+.035))),[.07,rowH*1.02,.1],{band:'rim',seed:seed+i*2+s,axes});
   if(halo)piece(m,add(p,mul(side,s*(half+.17))),[.16,rowH*.9,.06],{band:'glass',seed:seed+i*3+s,axes,alpha:.55*open});
  }
 }
}

/** A sphere of glass plates facing outward (an orb's shell); spin turns it around up. */
export function shell(m,c,r,{count=24,size=.6,thick=.06,seed=0,spin=.4,band='nebula',alpha=null,t=m.time,keep=1}={}){
 for(let i=0;i<count;i++){
  if(rnd(seed+i*1.13)>keep)continue;
  const d=rotate(sphereDir(i,count),UP,t*spin+rnd(seed)*TAU),s=size*(.75+.35*rnd(seed+i*2.7));
  plate(m,add(c,mul(d,r)),d,s,s*(.7+.3*rnd(seed+i)),thick,{band:i%6===0?'glass':band,seed:seed+i,roll:rnd(seed+i*.5)*TAU,alpha});
 }
}

// ---- Spell pieces shared by several scenes ----------------------------------------------------------------

/** A Singularity Bolt head: an eclipse core and a fast tilted broken orbit. age grows it in over its first ticks. */
export function boltHead(m,p,dir,age,{scale=1,seed=0,t=m.time}={}){
 core(m,p,.25*scale*smoothstep(age/.06+.3),{seed,spin:2.2,t});
 // The orbit leans only a little toward the flight, so from behind (the caster's view) it is a thin ellipse, not a hoop.
 ring(m,p,add(UP,mul(norm(dir),.25)),.44*scale,{count:8,size:.065*scale,depth:.05,band:'rim',gaps:.35,phase:-t*9,seed:seed+3});
}

/** How open a rift slit is age seconds after it appears: snaps open, holds, then pinches shut. */
export function openness(age,{open=.07,hold=.12,close=.32,peak=1}={}){
 if(age<0)return 0;
 if(age<open)return peak*smoothstep(age/open);
 return peak*(1-smoothstep((age-open-hold)/close));
}

/**
 * A Blink-style rift at feet, age seconds after it opened. Folding in, the space around it falls into the slit and a
 * ring pulls shut as it closes; unfolding (out), shards spill back out. The figure passing through is the scene's.
 */
export function rift(m,feet,age,{seed=0,embers=true,out=false,hold=.12}={}){
 if(age<0||age>.9)return;
 const c=add(feet,[0,1.1,0]);
 slit(m,feet,{open:openness(age,{hold}),seed});
 if(out){
  spill(m,c,age,{count:10,speed:2.2,duration:.42,seed:seed+2,size:.22});
  if(embers)stars(m,c,age,{count:8,spread:1.2,duration:.6,seed:seed+4,inward:false});
  return;
 }
 inflow(m,c,age,{count:14,radius:1.6,period:.38,seed:seed+2,size:.24,once:true});
 const close=(age-.07-hold)/.32;
 if(close>0&&close<1)ring(m,c,toEye(m,c),lerp(1.1,.05,smoothstep(close)),{count:12,size:.07,band:'rim',phase:age*4,seed:seed+4,flat:true});
 if(embers)stars(m,c,age,{count:8,spread:1.3,duration:.5,seed:seed+6});
}

/** A magical poof: void glass pulled into a point at c, a ring snapping shut and a last pinpoint of starlight. */
export function poof(m,c,age,{seed=0,embers=true,radius=1.2}={}){
 if(age<0||age>.7)return;
 inflow(m,c,age,{count:16,radius,period:.4,seed,size:.22,once:true});
 const k=age/.3;if(k<1)ring(m,c,toEye(m,c),lerp(radius,.05,smoothstep(k)),{count:12,size:.07,band:'rim',phase:age*5,seed:seed+3,flat:true});
 const pin=(age-.32)/.14;if(pin>0&&pin<1)piece(m,c,.24*(1-pin),{band:'starlight',seed,axes:basis(UP,Math.PI/4)});
 if(embers)stars(m,c,age,{count:10,spread:radius*1.2,duration:.5,seed:seed+5});
}

/** A burning star for Hollow Purple's charge: a stepped ball of one energy band around a starlight heart. */
export function star(m,c,r,{band='blue',seed=0,t=m.time}={}){
 if(r<=.01)return;
 const ax=tumble(seed,t,2.5);
 piece(m,c,1.6*r,{band,seed,axes:ax});
 for(let i=0;i<3;i++){const s=[1.2*r,1.2*r,1.2*r];s[i]=2*r;piece(m,c,s,{band,seed:seed+i+1,axes:ax})}
 piece(m,c,.9*r,{band:'starlight',seed:seed+5,axes:tumble(seed+5,t,-3)});
}

// ---- Models ----------------------------------------------------------------------------------------------

/** The Astral Double's orb: an amethyst crystal (two crossed, stepped prisms) inside a tumbling void-glass cube. */
export function astralOrb(m,p,t,{scale=1,seed=0}={}){
 const ax=tumble(seed,t,3),ax2=tumble(seed+5,t,-2.2);
 piece(m,p,[.12*scale,.3*scale,.12*scale],{band:'amethyst',seed,axes:ax});
 piece(m,p,[.24*scale,.12*scale,.12*scale],{band:'amethyst',seed:seed+1,axes:ax});
 piece(m,p,.08*scale,{band:'starlight',seed:seed+2,axes:ax});
 piece(m,p,.3*scale,{band:'glass',seed:seed+3,axes:ax2,alpha:.8});
}

/**
 * The Gravity Bomb rebuilt in void glass: a void cube inside six glass plates that part with each pulse, three rim
 * satellites on a tilted orbit. open (0..1) blows the plates outward for the impact.
 */
export function gravityBomb(m,p,t,{seed=0,open=0,scale=1}={}){
 const ax=tumble(seed,t,2.4),pulse=.5+.5*Math.sin(t*TAU*1.6),gap=(.2+.03*pulse+open*1.4)*scale;
 const fade=1-smoothstep((open-.3)/.7);
 core(m,p,.13*scale*(1-open*.5),{seed,spin:2,corona:0,t});
 piece(m,p,.11*scale,{band:'rim',seed:seed+9,axes:ax});
 if(fade>.01)for(let i=0;i<3;i++)for(const s of[-1,1]){
  const n=mul(ax[i],s),q=add(p,mul(n,gap));
  plate(m,q,n,.36*scale*fade,.36*scale*fade,.05*scale,{band:'glass',seed:seed+i*2+s,roll:i*.7});
 }
 const tilt=norm(add(ax[1],mul(ax[0],.5)));
 ring(m,p,tilt,.42*scale*(1+open),{count:3,size:.06*scale,band:'rim',phase:t*5,seed:seed+20});
}

// ---- Figures and terrain (shared with the Fire and Wind studies) -----------------------------------------

export const EYE=1.62;
export function lookDir(yaw,pitch=0){return[Math.cos(yaw)*Math.cos(pitch),Math.sin(pitch),Math.sin(yaw)*Math.cos(pitch)]}
/** The mod's held-wand tip estimate (SpellCastVisuals): ahead of the eye, toward the casting hand, a little low. */
export function wandTip(pos,yaw,pitch=0){
 const look=lookDir(yaw,pitch),right=[-Math.sin(yaw),0,Math.cos(yaw)],up=cross(right,look);
 return add(add(pos,[0,EYE,0]),add(mul(look,.75),add(mul(right,.38),mul(up,-.32))));
}

const SKIN={player:{head:[.78,.58,.44],body:[.18,.55,.62],legs:[.24,.22,.55],arm:[.78,.58,.44],sleeve:[.18,.55,.62]},
 zombie:{head:[.36,.6,.33],body:[.18,.55,.6],legs:[.26,.25,.56],arm:[.36,.6,.33],sleeve:[.36,.6,.33]}};

/**
 * A blocky humanoid standing at pos (feet) facing yaw. pose: 'idle' | 'cast' (right arm aimed) | 'air' (legs apart).
 * lean tips the whole body forward (radians). hurt 0..1 tints it red like a hit mob. glass (0..1) wraps every part in
 * a slightly larger void-glass box, for the Astral Double; build (0..1) assembles it from the feet up.
 * Seen from its own eyes the body vanishes in the near-eye clearance; the tip piece is left off there.
 */
export function figure(m,pos,yaw,{kind='player',pose='idle',pitch=0,hurt=0,lean=0,wand=kind==='player',glass=0,build=1,seed=0}={}){
 const c=SKIN[kind],tint=v=>[lerp(v[0],1,hurt*.6),lerp(v[1],.15,hurt*.7),lerp(v[2],.15,hurt*.7),1];
 let axes=yawAxes(yaw);const r0=axes[2];
 const hip=add(pos,[0,.75,0]);
 if(lean)axes=axes.map(v=>rotate(v,r0,-lean));
 const[f,u,r]=axes;
 const self=!!m.clearance&&len(sub(add(pos,[0,EYE,0]),m.eye))<.5;
 const at=(fw,y,rt)=>add(hip,add(mul(f,fw),add(mul(u,y-.75),mul(r,rt))));
 const air=pose==='air';
 let n=0;
 const part=(p,size,col,ax=axes)=>{
  const g=build>=1?1:smoothstep((build*2.2-(p[1]-pos[1])/1.9));if(g<=.01)return;
  const sz=mul(size,g);n++;
  if(glass<1||build<1)m.box(p,mul(sz,.5),ax,'white',{color:tint(col)});
  if(glass>0)piece(m,p,mul(sz,1.12),{band:'glass',seed:seed+n,axes:ax,alpha:glass});
 };
 for(const s of[-1,1]){
  const swing=air?.5*s:0,legAx=[rotate(f,r,-swing),rotate(u,r,-swing),r];
  part(add(hip,mul(legAx[1],-.375)).map((v,i)=>v+r[i]*s*.125),[.25,.75,.25],c.legs,legAx);
 }
 const bodyY=1.125;
 part(at(0,bodyY,0),[.25,.75,.5],c.body);
 part(at(0,bodyY+.625,0),[.5,.5,.5],c.head);
 if(kind==='zombie'){for(const s of[-1,1])part(at(.375,bodyY+.25,s*.375),[.75,.25,.25],c.arm);return}
 part(at(0,bodyY,-.375),[.25,.75,.25],c.sleeve);
 const shoulder=at(0,bodyY+.3,.375);
 if(pose==='cast'||air){
  const tip=pose==='cast'?wandTip(pos,yaw,pitch):add(shoulder,mul(norm(add(f,mul(u,.6))),.9));
  const toTip=sub(tip,shoulder),aim=norm(toTip),reach=len(toTip),armLen=Math.min(.62,reach*.6);
  part(add(shoulder,mul(aim,armLen/2)),[.25,armLen,.25],c.sleeve,basis(aim));
  if(wand){const hand=add(shoulder,mul(aim,armLen)),wl=Math.max(.15,reach-armLen);
   part(add(hand,mul(aim,wl/2)),[.07,wl,.07],[.42,.28,.16],basis(aim));
   if(!self&&build>=1)piece(m,tip,.11,{band:'void',seed:3,axes:basis(aim,Math.PI/4)});}
 }else part(at(0,bodyY,.375),[.25,.75,.25],c.sleeve);
}

/** Plain stone blocks filling the unit cells from min to max (exclusive); skip(x, y, z) leaves a cell out. */
export function blocks(m,min,max,{tone=.5,seed=0,skip=null}={}){
 for(let x=min[0];x<max[0];x++)for(let y=min[1];y<max[1];y++)for(let z=min[2];z<max[2];z++){
  if(skip&&skip(x,y,z))continue;
  const g=tone+.1*rnd(seed+x*7.1+y*3.3+z*1.9);
  m.box([x+.5,y+.5,z+.5],[.5,.5,.5],AXES,'white',{color:[g,g*.99,g*1.03,1]});
 }
}

/** Block terrain from a column-height function (integer heights; grass tops, dirt sides). */
export function terrain(m,area,height=()=>0){
 for(let x=area.x0;x<area.x1;x++)for(let z=area.z0;z<area.z1;z++){
  const h=height(x+.5,z+.5);
  const checker=((x+z)%2===0)?1:.94;
  m.quad([[x,h,z+1],[x+1,h,z+1],[x+1,h,z],[x,h,z]],'grass',{color:[checker,checker,checker,1]});
  for(const[dx,dz]of[[1,0],[-1,0],[0,1],[0,-1]]){
   const nh=height(x+.5+dx,z+.5+dz);if(nh>=h)continue;
   for(let y=nh;y<h;y++){
    const x0=dx===1?x+1:x,z0=dz===1?z+1:z;
    const pts=dx!==0?[[x0,y,dx>0?z+1:z],[x0,y,dx>0?z:z+1],[x0,y+1,dx>0?z:z+1],[x0,y+1,dx>0?z+1:z]]
     :[[dz>0?x:x+1,y,z0],[dz>0?x+1:x,y,z0],[dz>0?x+1:x,y+1,z0],[dz>0?x:x+1,y+1,z0]];
    m.quad(pts,'dirt',{color:[checker,checker,checker,1]});
   }
  }
 }
}
