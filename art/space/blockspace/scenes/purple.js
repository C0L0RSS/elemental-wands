// Runtime port: HollowPurpleChargeManager's charge visuals (two dying stars orbiting the anchor, their wakes, broken
// orbits, motes, the pinpoint) become one client effect started by a BlockSpaceEvents CHARGE message (caster id and
// start time); the anchor follows the caster, who is a tracked player, so the effect needs no further messages.
// HollowPurpleOrbEntity gets a block-space renderer (replacing EmptyEntityRenderer and the SPACE_ECLIPSE /
// SPACE_GRAVITY_LENS / SPACE_CONSUMPTION travel particles), which also needs its velocity for the wake. The launch
// flash and the final collapse are BlockSpaceEvents (LAUNCH at the anchor, COLLAPSE at the orb) replacing
// SPACE_FINAL_COLLAPSE and the collapse particles. Terrain erasure is the server's; the client draws nothing for it.
// The trail is world-space and lives about a second after the orb passes, so the renderer keeps the orb's spawn point
// and velocity (it flies straight) and a client effect finishes the trail after the entity is gone.
// Cube budget: about 60 while charging, 400 for the orb and its trail in flight, 300 in the collapse.
import{core,star,shell,inflow,chase,ring,spill,stars,piece,figure,blocks,lookDir,toEye,basis,flowAxes,easeIn,EYE}from'../kit.js';
import{add,sub,mul,norm,dot,len,cross,clamp,lerp,smoothstep,TAU,UP}from'../engine.js';

// ---- Real numbers (HollowPurpleChargeManager, HollowPurpleOrbEntity) -----------------------------------------
const TICK=.05,ASCENT=60*TICK,HOLD=10*TICK,RISE=10,ANCHOR=3;
const ORB_SPEED=1.3/TICK,ORB_RADIUS=5,LIFE=65*TICK,COLLAPSE=12*TICK;
const T=.3,T_LAUNCH=T+ASCENT+HOLD,T_COLLAPSE=T_LAUNCH+LIFE,DURATION=T_COLLAPSE+COLLAPSE+.5;
const PITCH=-.12,DIR=lookDir(0,PITCH),RIGHT=norm(cross([DIR[0],0,DIR[2]],UP)),ABOVE=norm(cross(RIGHT,DIR));

/** The caster's feet: a steady 10-block rise over the ascent, held, then slow falling after the launch. */
function casterAt(t){
 if(t<T)return[0,0,0];
 if(t<T+ASCENT)return[0,RISE*smoothstep((t-T)/ASCENT),0];
 if(t<T_LAUNCH)return[0,RISE,0];
 return[0,Math.max(0,RISE-1.2*(t-T_LAUNCH)),0];
}
const anchorAt=t=>add(casterAt(Math.min(t,T_LAUNCH)),[0,ANCHOR,0]);
const LAUNCH_AT=anchorAt(T_LAUNCH);
const orbAt=t=>add(LAUNCH_AT,mul(DIR,clamp(t-T_LAUNCH,0,LIFE)*ORB_SPEED));

/** Where the two dying stars are: they orbit the anchor (2.4 rad/s), closing from 3.35 to 2.15 blocks, then merge. */
function starOffset(t){
 const age=t-T,spread=age<ASCENT?lerp(3.35,2.15,age/ASCENT):lerp(2.15,0,clamp((age-ASCENT)/HOLD));
 const a=age*2.4;return add(mul(RIGHT,Math.cos(a)*spread),mul(UP,Math.sin(a)*spread*.34));
}

/** The charge: two stars circling closer, their wakes pulled after them, orbits and motes closing on the anchor. */
function drawCharge(m,t,embers){
 if(t<T||t>=T_LAUNCH)return;
 const age=t-T,c=anchorAt(t),progress=clamp(age/ASCENT),hold=clamp((age-ASCENT)/HOLD),grow=smoothstep(age/.3);
 for(const[s,band,seed]of[[1,'blue',3],[-1,'red',7]]){
  const at=time=>add(anchorAt(time),mul(starOffset(time),s));
  star(m,at(t),(.38+.12*progress)*grow*(1-hold*.35),{band,seed});
  chase(m,at,t,{from:T,every:.035,duration:.4,spread:.3,size:.2,seed:seed+1,band});
 }
 ring(m,c,[.3,1,.2],lerp(1.6,.7,progress)*grow,{count:10,size:.07,band:'rim',gaps:.35,phase:t*2.5,seed:11});
 ring(m,c,[-.35,1,-.25],lerp(2.1,1,progress)*grow,{count:12,size:.06,band:'glass',gaps:.4,phase:-t*1.8,seed:13});
 if(embers)inflow(m,c,t,{count:10,radius:2.4,period:1.1,seed:15,size:.07,band:'starlight'});
 if(hold>=.7)piece(m,c,.18+.1*Math.sin(t*40),{band:'starlight',seed:17,axes:basis(UP,Math.PI/4)});
}

/** Launch: the merged stars flash white and a ring blows out as the orb opens around the anchor. */
function drawLaunch(m,t){
 const age=t-T_LAUNCH;if(age<0||age>.45)return;
 const k=age/.45;
 piece(m,LAUNCH_AT,1.6*(1-smoothstep(k*1.6)),{band:'starlight',seed:19,axes:basis(UP,Math.PI/4+age*3)});
 ring(m,LAUNCH_AT,toEye(m,LAUNCH_AT),lerp(1,7,smoothstep(k)),{count:20,size:.22*(1-k),band:'rim',phase:.4,seed:21,flat:true});
}

/**
 * The orb in flight: a ten-block sphere marked by a sparse shell of nebula plates falling into a big eclipse core,
 * a blue and a red broken orbit (the two stars it was made from) and space dragged in behind it.
 */
function drawOrb(m,t,embers){
 const age=t-T_LAUNCH;if(age<0||age>LIFE)return;
 const c=orbAt(t),open=smoothstep(age/.2);
 core(m,c,2.1*open,{seed:23,spin:.8});
 shell(m,c,ORB_RADIUS*lerp(.5,1,open),{count:48,size:1.15,thick:.12,seed:25,spin:.5,keep:.75});
 inflow(m,c,t,{count:26,radius:ORB_RADIUS*1.05,period:.85,seed:27,size:.85,spin:1.2,inner:2});
 ring(m,c,add(UP,mul(DIR,.5)),3.2*open,{count:16,size:.16,depth:.12,band:'blue',gaps:.3,phase:t*2.2,seed:29});
 ring(m,c,add(UP,mul(DIR,-.6)),3.8*open,{count:18,size:.16,depth:.12,band:'red',gaps:.3,phase:-t*1.9,seed:31});
 chase(m,orbAt,t,{from:T_LAUNCH,until:T_COLLAPSE,every:.03,duration:.45,spread:3.4,size:.7,seed:33});
 if(embers)stars(m,c,t,{count:12,spread:ORB_RADIUS*1.3,duration:.8,seed:35,size:.12});
}

// ---- Trail ------------------------------------------------------------------------------------------------------
const TRAIL_LIFE=1.1;             // seconds a trail piece lasts after the orb passes: about 29 blocks of trail
const HELIX_STEP=.6;              // one helix piece per 0.6 blocks of travel, per strand
const TWIST=TAU/14;               // the two strands make a full turn every 14 blocks
const SEAL_STEP=4.5;              // a closing ring every 4.5 blocks

/**
 * The trail: two purple strands (bright violet rim and lavender amethyst) unwind behind it as a helix around its path,
 * and the tunnel of torn space seals behind it in rim rings that pull shut. Every piece is left in place where the orb
 * passed, then falls toward the path and shrinks away, so the trail tapers from the orb back.
 */
function drawTrail(m,t){
 if(t<T_LAUNCH+.1)return;
 const travelled=clamp(t-T_LAUNCH,0,LIFE)*ORB_SPEED;
 const from=Math.max(0,Math.floor(((t-TRAIL_LIFE)-T_LAUNCH)*ORB_SPEED/HELIX_STEP));
 for(let j=from;j*HELIX_STEP<=travelled;j++){
  const s=j*HELIX_STEP,born=T_LAUNCH+s/ORB_SPEED,k=(t-born)/TRAIL_LIFE;if(k<0||k>=1)continue;
  const grow=smoothstep(k/.06),fade=1-smoothstep((k-.3)/.7);
  const r=ORB_RADIUS*.85*(1-easeIn(k)*.8),axis=add(LAUNCH_AT,mul(DIR,s));
  for(const[strand,band]of[[0,'rim'],[1,'amethyst']]){
   const a=s*TWIST+strand*Math.PI,out=add(mul(RIGHT,Math.cos(a)),mul(ABOVE,Math.sin(a)));
   const along=add(DIR,mul(add(mul(RIGHT,-Math.sin(a)),mul(ABOVE,Math.cos(a))),r*TWIST));
   const size=.62*grow*fade;if(size<.02)continue;
   piece(m,add(axis,mul(out,r)),[HELIX_STEP*1.5,size*.55,size],{band,seed:j*2+strand,axes:flowAxes(along,out),glow:1});
  }
 }
 for(let j=Math.max(1,Math.floor(((t-.7)-T_LAUNCH)*ORB_SPEED/SEAL_STEP));j*SEAL_STEP<=travelled;j++){
  const s=j*SEAL_STEP,born=T_LAUNCH+s/ORB_SPEED,k=(t-born)/.7;if(k<0||k>=1)continue;
  ring(m,add(LAUNCH_AT,mul(DIR,s)),DIR,ORB_RADIUS*lerp(1,.08,smoothstep(k)),{count:16,size:.2*(1-k*.8),depth:.14,band:j%2?'rim':'nebula',phase:j*.7,seed:50+j,flat:true});
 }
}

/** Final collapse (12 ticks): the shell rushes into the core, the core pinches to a point, a lens ring blows out. */
function drawCollapse(m,t,embers){
 const age=t-T_COLLAPSE;if(age<0||age>COLLAPSE+.2)return;
 const c=orbAt(T_COLLAPSE);
 const r=2.1*(1-smoothstep(age/(5*TICK)));if(r>.02)core(m,c,r,{seed:23,spin:3});
 inflow(m,c,age,{count:34,radius:ORB_RADIUS,period:5*TICK,seed:37,size:.9,once:true});
 const pin=(age-5*TICK)/(3*TICK);if(pin>0&&pin<1)piece(m,c,.9*(1-pin),{band:'starlight',seed:39,axes:basis(UP,Math.PI/4)});
 const lens=(age-7*TICK)/(5*TICK+.2);
 if(lens>0&&lens<1)ring(m,c,toEye(m,c),lerp(.5,8,smoothstep(lens)),{count:24,size:.24*(1-lens),band:'rim',phase:.2,seed:41,flat:true});
 if(embers&&lens>0)spill(m,c,age-7*TICK,{count:14,speed:10,duration:.4,seed:43,size:.4,band:'nebula'});
}

// A stone hill in the orb's path, cut by the ten-block sphere as it passes (outside the protected boss realms).
const HILL={x0:26,x1:38,z0:-6,z1:6},hillTop=(x,z)=>Math.round(9-.06*((x-32)**2)-.08*z*z);
function erased(x,y,z,t){
 if(t<T_LAUNCH)return false;
 const a=LAUNCH_AT,b=orbAt(t),ab=sub(b,a),p=[x+.5,y+.5,z+.5],k=clamp(dot(sub(p,a),ab)/Math.max(1e-6,dot(ab,ab)));
 return len(sub(p,add(a,mul(ab,k))))<ORB_RADIUS;
}
const ZOMBIE=[31,hillTop(31,1.5)+1,1.5];

export default{
 id:'purple',name:'Hollow Purple',slot:'Ultimate',duration:DURATION,
 summary:'The caster rises ten blocks while a blue and a red star circle closer and closer above them, then merge in a white flash into a ten-block sphere of falling void glass around a huge eclipse core. It tears through everything in its path, trailing a purple double helix as the tunnel seals behind it, and finally collapses into a point.',
 notes:'Charge: 3 s rising plus 0.5 s held; the stars orbit 3 blocks above the caster at 2.4 rad/s, closing from 3.35 to 2.15 blocks, then to nothing.\nOrb: 10-block diameter at 26 blocks per second for 3.25 s (about 85 blocks), then a 0.6 s collapse. It erases terrain along its path (not in the boss realms), as the hill shows; the erasure is the server\'s.\nThe shell is only a few dozen large plates so the horizon stays readable, and every plate is falling inward.\nTrail: two purple strands, bright violet and lavender, unwind behind the orb as a twisting double helix around its path, and rim rings seal the torn tunnel shut every 4.5 blocks. Each piece stays where the orb passed, falls toward the path and fades within about a second, so the trail is roughly 29 blocks long and tapers back from the orb.',
 phases:[[0,'Ready'],[T,'Charge · rising'],[T+ASCENT,'Hold · stars merge'],[T_LAUNCH,'Launch'],[T_LAUNCH+.4,'Orb · 26 blocks/s'],[T_COLLAPSE,'Final collapse']],
 area:{x0:-6,x1:96,z0:-14,z1:14},budget:520,
 cameras:{
  orbit:{target:[14,8,0],yaw:.95,pitch:.22,distance:42},
  caster:t=>{const eye=add(casterAt(t),[0,EYE,0]);return{eye,look:add(eye,DIR)}},
  // A bystander on the ground near the caster: they watch the wind-up, then turn to follow the orb.
  target:t=>({eye:[6,1.7,7],look:t<T_LAUNCH+.2?anchorAt(t):orbAt(t)}),
 },
 draw(m,t,{embers}){
  blocks(m,[HILL.x0,0,HILL.z0],[HILL.x1,10,HILL.z1],{tone:.48,skip:(x,y,z)=>y>=hillTop(x+.5,z+.5)||erased(x,y,z,t)});
  const zombieGone=erased(ZOMBIE[0]-.5,ZOMBIE[1]-.5+1,ZOMBIE[2]-.5,t);
  if(!zombieGone)figure(m,ZOMBIE,Math.PI,{kind:'zombie'});
  figure(m,casterAt(t),0,{pose:t>=T&&t<T_LAUNCH+.3?'cast':'idle',pitch:PITCH});
  drawCharge(m,t,embers);
  drawLaunch(m,t);
  drawOrb(m,t,embers);
  drawTrail(m,t);
  drawCollapse(m,t,embers);
 },
};
