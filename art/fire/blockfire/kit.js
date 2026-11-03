// Shared block-fire primitives. Every function is a pure function of (mesh time, arguments, seed):
// no hidden state, no Math.random, so the Java renderer can reproduce the same cubes every frame.
//
// Look rules (from the user's earlier fire feedback):
// - Flames flicker by changing length and width, never by whipping or travelling waves (that reads as tentacles).
// - Pieces that leave a flame shrink to nothing; they reset only while hidden inside the flame's base.
// - Chunky Minecraft-style cubes; contrast comes from the atlas bands and baked face shade, not alpha.
import{ATLAS,add,sub,mul,dot,cross,norm,len,clamp,lerp,smoothstep,rnd,noise,TAU,UP}from'./engine.js';

/** Atlas rows in textures/blockfire.png. */
export const BAND={core:0,yellow:1,orange:2,deep:3,ember:4,smoke:5,char:6,magma:7};
// Smoke takes scene light; char crust glows so its seams stay lit at night (the rock itself is dark).
const GLOW=[true,true,true,true,true,false,true,true];
/** Hot → cold fire bands; index 4 is hottest. */
export const HEAT=['ember','deep','orange','yellow','core'];
export const heatBand=i=>HEAT[clamp(Math.round(i),0,4)];
/** Atlas frame rate; each cube offsets its frame by its seed so neighbours don't flicker in sync. */
export const FPS=8;

export const AXES=[[1,0,0],[0,1,0],[0,0,1]];
/** Set-wide smoke size and opacity: smoke is a see-through accent beside the fire, never the main shape. */
export const SMOKE_SCALE=.7,SMOKE_ALPHA=.5;

/** Right-handed basis whose second axis is dir; roll spins the cube around it. */
export function basis(dir,roll=0){
 const y=norm(dir),ref=Math.abs(y[1])>.92?[1,0,0]:UP;
 let x=norm(cross(y,ref)),z=cross(x,y);
 if(roll){const c=Math.cos(roll),s=Math.sin(roll);[x,z]=[add(mul(x,c),mul(z,s)),sub(mul(z,c),mul(x,s))]}
 return[x,y,z];
}
/** Basis turned around the vertical axis by yaw (radians). Axis 0 points along [cos, 0, sin]. */
export function yawAxes(yaw){const f=[Math.cos(yaw),0,Math.sin(yaw)];return[f,UP,cross(f,UP)]}
/** Rodrigues rotation of v around unit axis k by angle a. */
export function rotate(v,k,a){const c=Math.cos(a),s=Math.sin(a);return add(add(mul(v,c),mul(cross(k,v),s)),mul(k,dot(k,v)*(1-c)))}
/** A tumbling basis: spins about a seeded axis at speed radians per second. */
export function tumble(seed,t,speed=2){
 const k=norm([rnd(seed+.1)-.5,rnd(seed+.2)-.5,rnd(seed+.3)-.5]),a=rnd(seed+.4)*TAU+t*speed*(rnd(seed+.5)<.5?-1:1);
 return AXES.map(v=>rotate(v,k,a));
}
/** Even directions on a sphere (golden spiral). */
export function sphereDir(i,count){const y=1-2*(i+.5)/count,r=Math.sqrt(Math.max(0,1-y*y)),a=i*2.399963;return[Math.cos(a)*r,y,Math.sin(a)*r]}

/**
 * One cube. size is a full edge length (number) or [x, y, z] extents along axes.
 * band names an atlas row; glow defaults to the band (only smoke takes scene light).
 * Smoke is see-through and skips the baked face shade; opaque, shaded grey cubes read as thrown stones.
 * alpha only applies to smoke (fire fades by shrinking).
 */
export function cube(m,p,size,{band='orange',seed=0,axes=AXES,glow=null,color=[1,1,1,1],alpha=SMOKE_ALPHA}={}){
 const s=typeof size==='number'?[size,size,size]:size;
 const row=BAND[band];if(row===undefined)throw Error('Unknown band: '+band);
 const frame=Math.floor(m.time*FPS+rnd(seed+.77)*4)%4;
 const isSmoke=row===BAND.smoke;
 m.box(p,mul(s,.5),axes,isSmoke?ATLAS.smokeKey:ATLAS.key,{cell:[frame,row],emissive:glow??GLOW[row],color:isSmoke?[color[0],color[1],color[2],alpha]:color,tone:!isSmoke});
}

/**
 * A flame tongue built like real fire: a flickering hot base cluster, and a loop of cubes that rise from it
 * along dir, shrink, drift and cool through the heat bands until they vanish at the tip. Each rising cube
 * restarts hidden inside the base, so nothing pops. heat 1 starts yellow at the base; ~0.45 is an ember flame.
 * count overrides the number of rising cubes; speed scales how fast they climb.
 */
export function flame(m,root,dir,{width=.5,length=1.2,seed=0,heat=1,sway=.08,count=0,flicker=1,speed=1,t=m.time}={}){
 if(length<=.01||width<=.01)return;
 const d=norm(dir),roll=rnd(seed+.9)*TAU,[bx,,bz]=basis(d,roll),top=heat*3;
 const L=length*(1-.15*flicker+.3*flicker*noise(t*4.1+seed*7.1));
 const n=count||clamp(Math.round(L/width*2.2),3,8);
 // A slow lean shared by the whole tongue; it bends more toward the tip but never ripples.
 const leanA=(noise(t*1.6+seed*3.3)-.5)*2*sway,leanB=(noise(t*1.9+seed*5.9)-.5)*2*sway;
 const bw=width*(.92+.16*noise(t*6.7+seed*2.3)),base=add(root,mul(d,bw*.36));
 // The base is three overlapping cubes, each flickering on its own, so it never reads as one box.
 for(let j=0;j<3;j++){
  const a=roll+j*TAU/3,s=bw*(.68+.16*noise(t*6.1+seed*2.3+j*3.1));
  cube(m,add(base,add(mul(bx,Math.cos(a)*bw*.17),mul(bz,Math.sin(a)*bw*.17))),[s,s*.92,s],{band:heatBand(top-(j?.7:0)),seed:seed+j*9,axes:basis(d,roll+j*.55)});
 }
 const period=clamp(.32+L*.2,.32,1.1)/speed;
 for(let i=0;i<n;i++){
  const phase=t/period+i/n+rnd(seed+i*1.31)*.35/n,loop=Math.floor(phase),u=phase-loop,k=seed+i*4.1+loop*17.3;
  const w=bw*.74*Math.pow(1-u,.8)*(.85+.3*rnd(k));
  if(w<.012)continue;
  const side=add(mul(bx,(rnd(k+1.7)-.5)*width*.4*u+leanA*L*u*u),mul(bz,(rnd(k+2.9)-.5)*width*.4*u+leanB*L*u*u));
  const c=add(add(base,mul(d,L*Math.pow(u,.85))),side);
  cube(m,c,[w,w*1.15,w],{band:heatBand(top-u*2.5),seed:seed+i,axes:basis(d,roll+(rnd(k+3.3)-.5)*1.2+u*.6)});
 }
}

/**
 * A ball of fire that blooms and burns out: a bright flash cluster, then tumbling cubes thrown outward
 * that rise, cool through the heat bands and shrink to nothing, then optional smoke.
 * age in seconds since the burst; duration is the main life of the thrown pieces.
 */
export function burst(m,center,age,{radius=2,count=24,seed=0,duration=.9,size=.45,up=.35,heat=1,smoke:withSmoke=true,flash=true}={}){
 if(age<0)return;const life=age/duration;if(life>2.2)return;
 if(flash&&life<.45){
  const k=life/.45,s=size*2.3*Math.pow(Math.sin(k*Math.PI),.6);
  cube(m,center,s,{band:heatBand(heat*4-k*1.6),seed,axes:tumble(seed,age,3)});
  cube(m,center,s*.86,{band:heatBand(heat*4-k*1.6-1),seed:seed+1,axes:tumble(seed+1,age,-3)});
 }
 for(let i=0;i<count;i++){
  const delay=rnd(seed+i*1.7)*.12,l=clamp((life-delay)/(1-delay),0,1);if(l<=0||l>=1)continue;
  const dir=norm(add(sphereDir(i,count),[0,up,0])),r=radius*(1-Math.pow(1-l,2.2))*(.55+.45*rnd(seed+i*3.3));
  const p=add(add(center,mul(dir,r)),[0,.5*l*l,0]);
  const s=size*(.6+.6*rnd(seed+i*4.9))*Math.min(1,l*8)*Math.pow(1-l,1.1);
  cube(m,p,s,{band:heatBand(heat*4-l*4.2+rnd(seed+i*2.3)*.6),seed:seed+i,axes:tumble(seed+i,age,4)});
 }
 if(withSmoke)for(let i=0;i<Math.ceil(count/5);i++){
  const start=.3+rnd(seed+i*8.1)*.2,l=(life-start)/1.6;if(l<=0||l>=1)continue;
  const dir=norm(add(sphereDir(i*3+1,count),[0,.8,0]));
  smoke(m,add(add(center,mul(dir,radius*.65)),[0,.2,0]),l*1.6*duration,{size:size*.9,seed:seed+i+300,duration:1.6*duration,rise:radius*.8});
 }
}

/**
 * A smoke puff that swells, rises and shrinks away. Lit by the scene (not glowing).
 * It stays upright and only turns slowly about the vertical: a flattened body with a smaller cap, like a cloud
 * lump. (Single grey cubes that tumble read as thrown rocks.) scale multiplies the size without touching timing.
 */
export function smoke(m,p,age,{size=.5,seed=0,rise=1.4,duration=1.2,scale=1}={}){
 const l=age/duration;if(l<=0||l>=1)return;
 const s=SMOKE_SCALE*size*scale*Math.pow(Math.sin(l*Math.PI),.7)*(.8+.4*rnd(seed));if(s<=.01)return;
 const q=add(p,[(rnd(seed+1)-.5)*.6*l,rise*l,(rnd(seed+2)-.5)*.6*l]);
 const tilt=(rnd(seed+3)-.5)*.3,axes=yawAxes(rnd(seed+4)*TAU+age*(rnd(seed+5)<.5?-.5:.5)).map(v=>rotate(v,[1,0,0],tilt));
 const alpha=SMOKE_ALPHA*Math.min(1,(1-l)*2.5);
 cube(m,q,[s*1.15,s*.78,s*1.15],{band:'smoke',seed,axes,alpha});
 // The cap sits up and to one side, rising a little faster, so the puff's outline is lumpy rather than a box.
 const a=rnd(seed+6)*TAU,c=s*.62;
 cube(m,add(q,add(mul(axes[0],Math.cos(a)*s*.22),add(mul(axes[2],Math.sin(a)*s*.22),mul(axes[1],s*(.42+.25*l))))),[c*1.1,c*.8,c*1.1],{band:'smoke',seed:seed+7,axes,alpha});
}

/**
 * One-shot spark cubes (FIRE_EMBER stand-ins) thrown up and out from c, falling under g (negative g floats them up).
 * dir with bias throws them that way (a hit's follow-through, a splash off a wall). Each winks out as it cools.
 */
export function sparkBurst(m,c,age,{count=12,speed=2.5,up=3,life=.65,seed=0,size=.08,g=8,dir=null,bias=0,cool=2.4}={}){
 if(age<=0)return;
 for(let i=0;i<count;i++){
  const li=life*(.6+.7*rnd(seed+i*1.3)),l=age/li;if(l<=0||l>=1)continue;
  const a=rnd(seed+i*2.1)*TAU,h=speed*(.35+.65*rnd(seed+i*3.7)),v=up*(.45+.7*rnd(seed+i*4.9));
  let vel=[Math.cos(a)*h,v,Math.sin(a)*h];if(dir)vel=add(vel,mul(dir,bias*speed));
  const p=add(c,[vel[0]*age,vel[1]*age-g*age*age/2,vel[2]*age]);
  cube(m,p,size*(1-l)*(.7+.6*rnd(seed+i*5.3)),{band:heatBand(3.6-l*cool),seed:seed+i,axes:tumble(seed+i,age,6)});
 }
}

/** A compact tumbling flame puff (main cube plus three satellites) for streams and trails. heat 0..4 band index. */
export function puff(m,p,age,{size=.4,seed=0,heat=3,spin=3}={}){
 if(size<=.01)return;
 const axes=tumble(seed,age,spin);
 cube(m,p,size,{band:heatBand(heat),seed,axes});
 for(let k=0;k<3;k++){
  const o=axes[k],sign=rnd(seed+k*2.9)<.5?-1:1;
  cube(m,add(p,mul(o,sign*size*.48)),size*.55,{band:heatBand(heat-.7),seed:seed+k+1,axes});
 }
}

/** Tiny rising spark cubes on looping lives around origin (stand-ins for ember particles). */
export function sparks(m,origin,t,{count=10,spread=.6,rise=1.6,seed=0,size=.08}={}){
 for(let i=0;i<count;i++){
  const period=.7+rnd(seed+i*1.9)*.7,u=((t/period+rnd(seed+i*2.7))%1+1)%1,a=rnd(seed+i*3.1)*TAU,r=spread*Math.sqrt(rnd(seed+i*4.3));
  const p=add(origin,[Math.cos(a)*r+Math.sin(u*5+i)*.08,rise*u,Math.sin(a)*r+Math.cos(u*4+i)*.08]);
  cube(m,p,size*(1-u)*(.7+.6*rnd(seed+i*5.7)),{band:heatBand(3.4-u*2.6),seed:seed+i,axes:tumble(seed+i,t,5)});
 }
}

/**
 * A ring of upright flame tongues around center (Fire Leap wave, meteor shock ring).
 * ground(x, z) returns the surface y under each tongue; lean tips them outward.
 */
export function flameRing(m,center,radius,t,{count=0,height=.6,width=.32,seed=0,heat=1,lean=.25,ground=null}={}){
 const n=count||Math.max(8,Math.round(radius*TAU/.42));
 for(let i=0;i<n;i++){
  const a=(i+rnd(seed+i)*.4)/n*TAU,x=center[0]+Math.cos(a)*radius,z=center[2]+Math.sin(a)*radius;
  const y=ground?ground(x,z):center[1];
  flame(m,[x,y,z],[Math.cos(a)*lean,1,Math.sin(a)*lean],{width:width*(.8+.4*rnd(seed+i*2.2)),length:height*(.75+.5*rnd(seed+i*3.4)),seed:seed+i*7,heat,sway:.05,t});
 }
}

// ---- Scenery and figures -------------------------------------------------------------------------

/** Minecraft eye height and the server's wand-tip estimate (SpellCastVisuals: 0.75 forward, 0.38 side, 0.32 down). */
export const EYE=1.62;
export function lookDir(yaw,pitch=0){return[Math.cos(yaw)*Math.cos(pitch),Math.sin(pitch),Math.sin(yaw)*Math.cos(pitch)]}
export function wandTip(pos,yaw,pitch=0){
 const look=lookDir(yaw,pitch),right=[-Math.sin(yaw),0,Math.cos(yaw)],up=cross(right,look);
 return add(add(pos,[0,EYE,0]),add(mul(look,.75),add(mul(right,.38),mul(up,-.32))));
}

const SKIN={player:{head:[.78,.58,.44],body:[.18,.55,.62],legs:[.24,.22,.55],arm:[.78,.58,.44],sleeve:[.18,.55,.62]},
 zombie:{head:[.36,.6,.33],body:[.18,.55,.6],legs:[.26,.25,.56],arm:[.36,.6,.33],sleeve:[.36,.6,.33]}};

/**
 * A blocky humanoid standing at pos (feet) facing yaw. pose: 'idle' | 'cast' (right arm aimed) | 'crouch' | 'air'.
 * hurt 0..1 tints it red the way Minecraft flashes a hit mob. Seen from its own eyes (a first-person camera), the
 * body vanishes in the near-eye clearance and only the arm and wand show; the glowing tip cube is left off there,
 * since 0.75 blocks from the eye it reads as a big block in the corner of the screen.
 */
export function figure(m,pos,yaw,{kind='player',pose='idle',pitch=0,hurt=0,wand=kind==='player'}={}){
 const c=SKIN[kind],axes=yawAxes(yaw),[f,,r]=axes,tint=v=>[lerp(v[0],1,hurt*.6),lerp(v[1],.15,hurt*.7),lerp(v[2],.15,hurt*.7),1];
 const self=!!m.clearance&&len(sub(add(pos,[0,EYE,0]),m.eye))<.5;
 const at=(fw,y,rt)=>add(pos,add(mul(f,fw),add([0,y,0],mul(r,rt))));
 const crouch=pose==='crouch'?.25:0,air=pose==='air';
 const part=(p,size,col,ax=axes)=>m.box(p,mul(size,.5),ax,'white',{color:tint(col)});
 const legSwing=air?.5:0;
 for(const s of[-1,1]){
  const legAxes=air?[rotate(f,r,-legSwing*s),UP,r].map(v=>rotate(v,r,-legSwing*s)):axes;
  part(at(0,.375+crouch*.3,s*.125),[.25,.75-crouch*.6,.25],c.legs,air?[rotate(f,r,-legSwing*s),rotate(UP,r,-legSwing*s),r]:axes);
 }
 const bodyY=.75+.375-crouch*.4;
 part(at(crouch*.1,bodyY,0),[.25,.75,.5],c.body);
 part(at(crouch*.15,bodyY+.625,0),[.5,.5,.5],c.head);
 // Left arm hangs (zombies raise both arms).
 const armUp=kind==='zombie'?[f,UP,r]:null;
 if(kind==='zombie'){for(const s of[-1,1])part(at(.375,bodyY+.25,s*.375),[.75,.25,.25],c.arm)}
 else part(at(0,bodyY,-.375),[.25,.75,.25],c.sleeve);
 if(kind==='zombie')return;
 const shoulder=at(0,bodyY+.3,.375),eyeP=add(pos,[0,EYE-crouch*.4,0]);
 if(pose==='cast'||pose==='air'){
  const tip=pose==='cast'?wandTip([pos[0],pos[1]-crouch*.4,pos[2]],yaw,pitch):add(shoulder,mul(norm(add(f,[0,.6,0])),.9));
  const toTip=sub(tip,shoulder),aim=norm(toTip),reach=len(toTip),armLen=Math.min(.62,reach*.6);
  part(add(shoulder,mul(aim,armLen/2)),[.25,armLen,.25],c.sleeve,basis(aim));
  if(wand){const hand=add(shoulder,mul(aim,armLen)),wl=Math.max(.15,reach-armLen);
   part(add(hand,mul(aim,wl/2)),[.07,wl,.07],[.42,.28,.16],basis(aim));
   if(!self)cube(m,tip,.12,{band:'yellow',seed:3,axes:basis(aim,Math.PI/4)});}
 }else part(at(0,bodyY,.375),[.25,.75,.25],c.sleeve);
}

/**
 * Plain stone blocks filling the unit cells from min to max (exclusive), e.g. a wall for a spell to hit.
 * Grey-tinted white texture, scene-lit like real blocks; each block's shade varies a little.
 */
export function blocks(m,min,max,{tone=.5,seed=0}={}){
 for(let x=min[0];x<max[0];x++)for(let y=min[1];y<max[1];y++)for(let z=min[2];z<max[2];z++){
  const g=tone+.1*rnd(seed+x*7.1+y*3.3+z*1.9);
  m.box([x+.5,y+.5,z+.5],[.5,.5,.5],AXES,'white',{color:[g,g*.99,g*1.03,1]});
 }
}

/**
 * Block terrain from a column-height function (integer heights; grass tops, dirt sides).
 * Returns nothing; use surfaceY(height) for the walkable y at a point.
 */
export function terrain(m,area,height=()=>0){
 for(let x=area.x0;x<area.x1;x++)for(let z=area.z0;z<area.z1;z++){
  const h=height(x+.5,z+.5),c=[x+.5,h-.5,z+.5];
  const checker=((x+z)%2===0)?1:.94;
  m.quad([[x,h,z+1],[x+1,h,z+1],[x+1,h,z],[x,h,z]],'grass',{color:[checker,checker,checker,1]});
  // Exposed sides where the neighbour is lower.
  for(const[dx,dz]of[[1,0],[-1,0],[0,1],[0,-1]]){
   const nh=height(x+.5+dx,z+.5+dz);if(nh>=h)continue;
   for(let y=nh;y<h;y++){
    const x0=dx===1?x+1:x,x1=dx===-1?x:x+1,z0=dz===1?z+1:z,z1=dz===-1?z:z+1;
    const pts=dx!==0?[[x0,y,dx>0?z+1:z],[x0,y,dx>0?z:z+1],[x0,y+1,dx>0?z:z+1],[x0,y+1,dx>0?z+1:z]]
     :[[dz>0?x:x+1,y,z0],[dz>0?x+1:x,y,z0],[dz>0?x+1:x,y+1,z0],[dz>0?x:x+1,y+1,z0]];
    m.quad(pts,'dirt',{color:[checker,checker,checker,1]});
   }
  }
 }
}
