// Runtime port: SingularityBoltEntity (server projectile, 0.9 blocks/tick, 24-block reach, 3-block splash) is drawn by a
// block-space renderer at its interpolated position, replacing EmptyEntityRenderer and the SPACE_SINGULARITY /
// SPACE_BROKEN_ORBIT / SPACE_CONSUMPTION / SPACE_MOTE travel particles. The wake needs the last ~10 positions per
// bolt, kept client-side like Fire's flame history, plus the wand-tip offset at launch (SpellCastVisuals.Wake bends the
// first three blocks). The cast bloom, the impact and the max-range fizzle are short client effects started by a
// BlockSpaceEvents message (CAST, HIT with the splash radius, FIZZLE) at the existing hooks. Astral Double echo bolts are
// the same entity at 70% strength, drawn a little smaller. Stars stay with the particle setting.
// Cube budget: about 45 per bolt in flight, 120 at an impact.
import{boltHead,core,ring,inflow,chase,stars,spill,piece,figure,wandTip,lookDir,basis,EYE}from'../kit.js';
import{add,sub,mul,clamp,lerp,smoothstep,UP}from'../engine.js';

// ---- Real numbers (SingularityBoltEntity, SpaceAbilityHandler, SpellCastVisuals) ---------------------------------
const SPEED=.9*20,RANGE=24,BEND_IN=3,BLOOM=.45;
const CASTS=[{t:.3,yaw:0,hit:6.7},{t:1.3,yaw:-.34,hit:null}];   // the second misses and fizzles at full range
const ZOMBIE=[7,0,.1],DURATION=3.2;
const EYE_POS=[0,EYE,0],HURT=.5;

/** Where a bolt is after s blocks: the eye line, bent out of the wand tip over the first three blocks. */
function pathPoint(cast,s){
 const dir=lookDir(cast.yaw),tipOff=sub(wandTip([0,0,0],cast.yaw),EYE_POS);
 return add(add(EYE_POS,mul(dir,s)),mul(tipOff,1-smoothstep(s/BEND_IN)));
}
const reach=cast=>cast.hit??RANGE;
const headAt=(cast,time)=>pathPoint(cast,clamp((time-cast.t)*SPEED,0,reach(cast)));

/** The bolt in flight: an eclipse core, a fast tilted broken orbit and space dragged in behind it. */
function drawBolt(m,t,cast,embers,scale=1){
 const age=t-cast.t,flight=reach(cast)/SPEED;if(age<0||age>flight)return;
 const p=headAt(cast,t),seed=cast.t*13;
 boltHead(m,p,lookDir(cast.yaw),age,{scale,seed});
 // Shedding starts a block out, so the wake never joins the wand in a solid rod.
 chase(m,time=>headAt(cast,time),t,{from:cast.t+.06,until:cast.t+flight,every:.028,duration:.3,spread:.45*scale,size:.2*scale,seed});
 if(embers)stars(m,p,t,{count:5,spread:.8*scale,duration:.5,seed:seed+5,size:.05});
}

/** Cast bloom just past the wand tip: a small ring pulled shut and a few stars sucked in. */
function drawCast(m,t,cast,embers){
 const age=t-cast.t;if(age<0||age>.3)return;
 const fwd=lookDir(cast.yaw),p=add(wandTip([0,0,0],cast.yaw),mul(fwd,BLOOM)),k=age/.22;
 // Small: it sits about a block from the caster's eye, where anything wider than the wand fills the view.
 if(k<1)ring(m,p,fwd,lerp(.28,.04,smoothstep(k)),{count:8,size:.035*(1-k*.6),band:'rim',phase:age*6,seed:cast.t*7,flat:true});
 if(embers)stars(m,p,age,{count:5,spread:.5,duration:.3,seed:cast.t*9,size:.045});
}

/**
 * Direct hit: the core swells as it swallows the contact, then the space it disturbed falls back in and the core
 * pinches off in a pinpoint. (The user removed the flat splash ring that swept out along the ground.)
 */
export function drawImpact(m,t,hitT,at,seed,embers){
 const age=t-hitT;if(age<0||age>.75)return;
 const r=age<.08?lerp(.25,.5,age/.08):.5*(1-smoothstep((age-.08)/.37));
 if(r>.02)core(m,at,r,{seed,spin:3});
 inflow(m,at,age-.06,{count:14,radius:1.7,period:.42,seed:seed+1,size:.26,once:true});
 const pin=(age-.45)/.12;if(pin>0&&pin<1)piece(m,at,.26*(1-pin),{band:'starlight',seed,axes:basis(UP,Math.PI/4)});
 if(embers)spill(m,at,age,{count:8,speed:2.4,duration:.35,seed:seed+2,size:.09,band:'nebula'});
}

/** Max range: the core folds shut and pinches to a point. (No flat ring: the user removed the horizontal circles.) */
function drawFizzle(m,t,endT,at,seed){
 const age=t-endT;if(age<0||age>.35)return;
 const k=age/.22;
 if(k<1)core(m,at,.25*(1-k),{seed});
 const pin=(age-.18)/.14;if(pin>0&&pin<1)piece(m,at,.18*(1-pin),{band:'starlight',seed,axes:basis(UP,Math.PI/4)});
}

export default{
 id:'bolt',name:'Singularity Bolt',slot:'Primary',duration:DURATION,
 summary:'A black star with a glowing corona and a broken orbit, dragging the space behind it into itself. On a hit it swells, pulls the space around it back in and pinches shut.',
 notes:'First cast hits the zombie 7 blocks away; the second flies its full 24 blocks and pinches shut.\n18 blocks per second, so a bolt crosses the scene in under half a second. The wake pieces are left behind along the path and pulled after the bolt, so the trail falls into it rather than streaming out of it.\nThe corona always faces you, so the core reads as an eclipse from any side.',
 phases:[[0,'Ready'],[.3,'Cast · hit at 7 blocks'],[.67,'Impact'],[1.3,'Cast · miss'],[2.63,'Full range · fizzle']],
 area:{x0:-4,x1:26,z0:-11,z1:5},budget:260,
 cameras:{
  orbit:{target:[5,1.2,-1.5],yaw:.55,pitch:.32,distance:13},
  caster:t=>{const c=t<1.2?CASTS[0]:CASTS[1];return{eye:EYE_POS,look:add(EYE_POS,lookDir(c.yaw))}},
  target:{eye:add(ZOMBIE,[0,1.7,0]),look:[0,1.3,0]},
 },
 draw(m,t,{embers}){
  const hitAt=CASTS[0].t+CASTS[0].hit/SPEED,hurt=t>=hitAt?clamp(1-(t-hitAt)/HURT):0;
  const aim=t>=1.2?CASTS[1].yaw:CASTS[0].yaw;
  figure(m,[0,0,0],aim,{pose:'cast'});
  figure(m,ZOMBIE,Math.PI,{kind:'zombie',hurt});
  for(const cast of CASTS){
   drawCast(m,t,cast,embers);
   drawBolt(m,t,cast,embers);
   const endT=cast.t+reach(cast)/SPEED,end=headAt(cast,endT);
   if(cast.hit)drawImpact(m,t,endT,end,cast.t*17,embers);else drawFizzle(m,t,endT,end,cast.t*19);
  }
 },
};
