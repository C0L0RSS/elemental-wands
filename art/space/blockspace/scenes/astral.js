// Runtime port: AstralOrbRenderer draws astralOrb() at the orb entity (replacing the amethyst-textured box and its
// SPACE_MOTE trail). AstralDoubleRenderer keeps the owner's skin and adds the void-glass shell over every part, the slow
// orbiting plates and the stars, replacing the violet wisp mesh and motes; it needs the double's age for the
// formation. The formation, the poof (destroyed, expired or consumed) and the swap rifts are BlockSpaceEvents
// (DOUBLE_FORM at the landing, DOUBLE_POOF at the chest, BLINK_FOLD/BLINK_UNFOLD for the swap) replacing
// AstralDoubleManager.poof's SPACE_MOTE and SPACE_IMPLOSION_RING. Echo bolts are ordinary Singularity Bolts.
// Cube budget: about 40 for the live double, 150 during the swap.
import{astralOrb,boltHead,chase,rift,poof,inflow,stars,plate,figure,wandTip,lookDir,EYE}from'../kit.js';
import{add,sub,mul,norm,len,clamp,lerp,smoothstep,rnd,TAU,UP}from'../engine.js';
import{drawImpact}from'./bolt.js';

// ---- Real numbers (AstralOrbEntity, AstralDoubleManager, SingularityBoltEntity) --------------------------------
const TICK=.05,BOLT_SPEED=.9/TICK,ECHO=.7,BEND_IN=3;
const T_TOSS=.3,T_SHOT=1.75,T_SWAP=3.05,DURATION=4.3;
const ZOMBIE=[9,0,-2.6],TARGET=add(ZOMBIE,[-.3,1.4,0]);
const EYE_POS=[0,EYE,0];

/** The orb's flight, tick by tick: thrown from 0.12 below the eye at 0.65 along the aim plus 0.18 up, gravity 0.06. */
const TRAJ=(()=>{
 let p=sub(EYE_POS,[0,.12,0]),v=add(mul(lookDir(0),.65),[0,.18,0]);const pts=[p];
 for(let i=0;i<100;i++){const to=add(p,v);if(to[1]<=0){const k=p[1]/(p[1]-to[1]);pts.push(add(p,mul(v,k)).map((x,j)=>j===1?.01:x));break}p=to;pts.push(p);v=add(mul(v,.99),[0,-.06,0])}
 return pts;
})();
const T_LAND=T_TOSS+(TRAJ.length-1)*TICK,FEET=[TRAJ[TRAJ.length-1][0],0,TRAJ[TRAJ.length-1][2]];
const orbAt=t=>{const f=clamp((t-T_TOSS)/TICK,0,TRAJ.length-1),i=Math.min(TRAJ.length-2,Math.floor(f)),k=f-i;return add(mul(TRAJ[i],1-k),mul(TRAJ[i+1],k))};

const DOUBLE_YAW=Math.atan2(TARGET[2]-FEET[2],TARGET[0]-FEET[0]),CASTER_YAW=Math.atan2(TARGET[2],TARGET[0]);
/** A bolt from a figure at feet aiming at TARGET: the eye line, bent out of its wand tip over three blocks. */
function shot(feet,yaw,t0){
 const eye=add(feet,[0,EYE,0]),d=norm(sub(TARGET,eye)),dist=len(sub(TARGET,eye))-.25;
 const tipOff=sub(wandTip(feet,yaw),eye);
 const at=s=>add(add(eye,mul(d,s)),mul(tipOff,1-smoothstep(s/BEND_IN)));
 return{t0,d,flight:dist/BOLT_SPEED,headAt:time=>at(clamp((time-t0)*BOLT_SPEED,0,dist))};
}
const SHOTS=[{...shot([0,0,0],CASTER_YAW,T_SHOT),scale:1,seed:3},{...shot(FEET,DOUBLE_YAW,T_SHOT),scale:.85,seed:7}];

/** The live double: the skin under a void-glass shell, four nebula plates slowly circling its waist, rising stars. */
function drawDouble(m,t,embers){
 if(t<T_LAND||t>T_SWAP)return;
 const age=t-T_LAND,build=clamp(age/.45),sheen=lerp(1,.42,smoothstep((age-.3)/.4));
 figure(m,FEET,DOUBLE_YAW,{pose:t>=T_SHOT&&t<T_SHOT+.6?'cast':'idle',glass:sheen,build,seed:31});
 // Formation: the floor's void glass is pulled up into the body as it assembles.
 if(age<.55)inflow(m,add(FEET,[0,.9,0]),age,{count:12,radius:1.3,period:.45,seed:33,size:.2,once:true});
 const grow=smoothstep((age-.25)/.3);
 for(let i=0;i<4;i++){
  const a=t*.9+i*TAU/4,r=.72+.06*Math.sin(t*2+i),c=add(FEET,[Math.cos(a)*r,.95+.25*Math.sin(t*1.3+i*1.7),Math.sin(a)*r]);
  const s=.34*grow*(.8+.2*rnd(i));if(s<.02)continue;
  plate(m,c,[Math.cos(a),.25,Math.sin(a)],s,s*.75,.05,{band:i%2?'nebula':'glass',seed:40+i,roll:t*.6+i});
 }
 if(embers)stars(m,add(FEET,[0,1,0]),t,{count:6,spread:1,duration:1.6,seed:45,inward:false,size:.05});
}

export default{
 id:'astral',name:'Astral Double',slot:'Technique',duration:DURATION,
 summary:'A small amethyst crystal in a tumbling void-glass cube is tossed and lands; the floor\'s glass is pulled up into a copy of the caster, their own skin under a dark starry shell. It echoes every Singularity Bolt, and recasting swaps places with it in a pair of rifts.',
 notes:'The orb flies about 7 blocks on level ground (thrown at 0.65 a tick plus 0.18 up, gravity 0.06), then the double assembles from the feet up in under half a second.\nAt 1.75 s the caster fires and the double fires its echo (70% strength, drawn a little smaller) at the same crosshair point.\nAt 3.05 s the caster recasts: they fold into a rift and unfold where the double stood, and the double is consumed in a poof. Destruction by a hit and expiry use the same poof.\nIn game the double keeps the owner\'s real skin under the glass.',
 phases:[[0,'Ready'],[T_TOSS,'Toss'],[T_LAND,'Lands · double forms'],[T_SHOT,'Bolt + echo'],[T_SWAP,'Recast · swap']],
 area:{x0:-4,x1:13,z0:-7,z1:5},budget:260,
 cameras:{
  orbit:{target:[4.5,1,-1],yaw:.25,pitch:.3,distance:12},
  caster:t=>{const at=t<T_SWAP?[0,0,0]:FEET,yaw=t<T_TOSS+.4?0:CASTER_YAW,eye=add(at,[0,EYE,0]);return{eye,look:add(eye,lookDir(t<T_SWAP?yaw:DOUBLE_YAW))}},
  target:{eye:add(ZOMBIE,[0,1.7,0]),look:[3,1.2,0]},
 },
 draw(m,t,{embers}){
  const hit=T_SHOT+Math.min(SHOTS[0].flight,SHOTS[1].flight),hurt=t>=hit?clamp(1-(t-hit)/.5):0;
  figure(m,ZOMBIE,Math.atan2(-ZOMBIE[2],-ZOMBIE[0]),{kind:'zombie',hurt});
  if(t<T_SWAP)figure(m,[0,0,0],t<T_TOSS+.4?0:CASTER_YAW,{pose:(t>=T_TOSS&&t<T_TOSS+.35)||(t>=T_SHOT&&t<T_SHOT+.6)||(t>=T_SWAP-.1)?'cast':'idle'});
  else figure(m,FEET,DOUBLE_YAW,{glass:clamp(1-(t-T_SWAP)/.35)*.9,build:clamp((t-T_SWAP)/.12),seed:51});
  if(t>=T_TOSS&&t<T_LAND){const p=orbAt(t);astralOrb(m,p,t,{seed:9});if(embers)stars(m,p,t,{count:3,spread:.4,duration:.4,seed:11,size:.04})}
  drawDouble(m,t,embers);
  for(const s of SHOTS){
   const age=t-s.t0;
   if(age>=0&&age<=s.flight){boltHead(m,s.headAt(t),s.d,age,{scale:s.scale,seed:s.seed});chase(m,s.headAt,t,{from:s.t0+.06,until:s.t0+s.flight,every:.03,duration:.3,spread:.45*s.scale,size:.2*s.scale,seed:s.seed})}
  }
  for(const s of SHOTS)drawImpact(m,t,s.t0+s.flight,s.headAt(s.t0+s.flight),s.seed*5,embers);
  rift(m,[0,0,0],t-T_SWAP,{seed:61,embers});
  rift(m,FEET,t-T_SWAP,{seed:63,embers,out:true,hold:.16});
  poof(m,add(FEET,[0,1.1,0]),t-T_SWAP,{seed:65,embers});
 },
};
