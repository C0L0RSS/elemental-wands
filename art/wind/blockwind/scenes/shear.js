// Runtime port: VacuumBladeEntity's renderer draws blade() at the entity's interpolated position (direction from its
// velocity, the wing index from the MIRRORED flag plus a new INDEX tracked value for the centre blade), replacing the
// AnimatedSpellBillboardRenderer sprite. Tip trails need the last ~6 positions per blade, kept client-side like the Fire
// flame history. The dissolve and the hit burst are a short client effect started by the entity's discard (a small
// "blade ended" payload with position, direction and whether it hit). WIND_SLIPSTREAM/WIND_SHEAR_FEATHER/WIND_MOTE
// wake particles and the cast WIND_BURST_RING/WIND_AIR_RIBBON are replaced by cubes; a few motes stay as particles.
// Cube budget: about 26 per blade in flight, 90 for three blades plus trails, 140 at a hit.
import{blade,cube,puff,ring,wisp,motes,figure,wandTip,lookDir,EYE}from'../kit.js';
import{add,sub,mul,norm,len,clamp,lerp,smoothstep,rnd,TAU,UP}from'../engine.js';

// ---- Real numbers (WindFanRules, VacuumBladeEntity, SpellCastVisuals) -------------------------------------
const TICK=.05,SPEED=2.5/TICK,RANGE=7,FAN=8*Math.PI/180,BEND_IN=3;   // 50 blocks/s, 7 blocks: 0.14 s of flight
const CASTS=[{t:.3,yaw:0,target:true},{t:1.45,yaw:-.32,target:false}];
const TARGET=[5,0,0],HIT_RADIUS=.3;
const DURATION=2.6,DISSOLVE=.24,ECHO_GAP=.75;
const EYE_POS=[0,EYE,0];

/** The blade's true eye-line path, bent from the wand tip over the first three blocks (SpellCastVisuals.Wake). */
function bladePos(cast,wing,d){
 const dir=lookDir(cast.yaw+wing*FAN),tip=wandTip([0,0,0],cast.yaw),onPath=add(EYE_POS,mul(dir,d));
 const bend=smoothstep(d/BEND_IN);return add(mul(tip,1-bend),mul(onPath,bend));
}
/** Where a blade stops: the zombie for the centre blade of the first cast, otherwise full range. */
function stopAt(cast,wing){
 if(cast.target&&wing===0)return TARGET[0]-HIT_RADIUS;
 return RANGE;
}
/** Tilt each blade a little around its flight line so the fan reads as three blades, not one wide arc. */
const upFor=(dir,wing)=>{const side=norm([-dir[2],0,dir[0]]);return norm(add(UP,mul(side,-.38+wing*.22)))};

function drawCast(m,t,cast,embers){
 const age=t-cast.t;if(age<0||age>1.2)return;
 const tip=wandTip([0,0,0],cast.yaw),fwd=lookDir(cast.yaw);
 // Cast bloom just past the wand tip: a small pressure ring and two curls.
 ring(m,add(tip,mul(fwd,.35)),fwd,lerp(.08,.3,smoothstep(age/.18)),{count:9,size:.045*(1-smoothstep(age/.22)),band:'gale',alpha:.85,phase:age*2});
 if(embers)for(let i=0;i<2;i++)wisp(m,add(tip,mul(fwd,.2)),add(fwd,[0,.3*(i?1:-1),.4*(i?-1:1)]),age,{seed:cast.t*10+i,duration:.4,size:.06,length:.45});
 for(const wing of[-1,0,1]){
  const dir=lookDir(cast.yaw+wing*FAN),stop=stopAt(cast,wing),flight=stop/SPEED;
  const d=Math.min(stop,age*SPEED),k=clamp((age-flight)/DISSOLVE),up=upFor(dir,wing);
  const seed=cast.t*31+wing*7+7;
  const shape={radius:wing===0?1.05:.9,width:.5,depth:.16};
  if(k<1)blade(m,bladePos(cast,wing,d),dir,up,{...shape,seed,k});
  // Echoes: two fainter, smaller, already-peeling copies of the blade just behind it, like motion blur made of
  // cubes. Continuous streaks read as wires at 50 blocks per second, and loose cubes read as debris.
  for(let e=1;e<=2;e++){
   const de=d-e*ECHO_GAP;if(de<.4)continue;
   blade(m,bladePos(cast,wing,de),dir,up,{...shape,width:shape.width*(1-e*.22),seed:seed+e*97,k:Math.min(1,k+.28*e),alpha:.42/e,echo:true});
  }
  // Ending: a missed blade breaks into two small cloud clusters at full range; the hit is drawn below.
  if(age>=flight&&!(cast.target&&wing===0)){
   const end=bladePos(cast,wing,stop),side=norm([-dir[2],0,dir[0]]);
   for(const s of[-1,1])puff(m,add(end,mul(side,s*.45)),age-flight,{size:.28,seed:seed+s,duration:.55,drift:add(mul(dir,.6),[0,.15,0])});
  }
 }
 if(cast.target){
  const hitAge=age-stopAt(cast,0)/SPEED,chest=[TARGET[0]-.25,1.25,0];
  puff(m,chest,hitAge,{size:.42,seed:91,duration:.6,drift:[.8,.25,0],lobes:5});
  ring(m,chest,fwd,lerp(.3,1.2,smoothstep(hitAge/.35)),{count:14,size:.09*(1-smoothstep(hitAge/.4)),alpha:.75,phase:hitAge*1.5,seed:5});
  if(embers){motes(m,chest,hitAge,{count:8,dir:fwd,seed:17,duration:.5});for(let i=0;i<3;i++)wisp(m,chest,[1,.2*(i-1),.5*(i-1)],hitAge,{seed:50+i,duration:.5,size:.08})}
 }
}

export default{
 id:'shear',name:'Sky Shear',slot:'Primary',duration:DURATION,
 summary:'Three stepped crescent blades fan out from the wand and cut 7 blocks in 0.14 s. Each faces you as it flies, so it reads as a crescent from your own eyes and as a swept-back bow from the side.',
 notes:'Real numbers: blades at −8°, 0°, +8°, 2.5 blocks per tick, a strict 7-block range, one hit per enemy per cast (the centre blade takes the zombie at 5 blocks).\nThe bright rim is solid; the body behind it is see-through cloud. Two fainter, already-peeling echoes follow each blade, like motion blur made of cubes.\nA missed blade peels apart from its tips and breaks into two little clouds at full range. A hit bursts a cloud and a pressure ring on the target.\nSecond cast (1.45 s) aims left with nothing in the way, to show full range.',
 phases:[[0,'Ready'],[.3,'Cast · hit at 5 blocks'],[.75,'Clear'],[1.45,'Cast · full 7-block range'],[1.9,'Clear']],
 area:{x0:-4,x1:10,z0:-6,z1:6},budget:400,
 cameras:{
  orbit:{target:[3.2,1.3,0],yaw:-2.1,pitch:.32,distance:7.2},
  caster:{eye:EYE_POS,look:[6,1.45,0]},
  target:{eye:[TARGET[0],EYE,0],look:[0,1.3,0]},
 },
 draw(m,t,{embers}){
  const castAge=Math.min(...CASTS.map(c=>t>=c.t?t-c.t:9));
  const yaw=t>=CASTS[1].t?CASTS[1].yaw:CASTS[0].yaw;
  figure(m,[0,0,0],yaw,{pose:castAge<.6?'cast':'idle'});
  const hitAge=t-CASTS[0].t-stopAt(CASTS[0],0)/SPEED;
  const knock=hitAge>0?Math.min(.45,hitAge*3):0;
  figure(m,add(TARGET,[knock,0,0]),Math.PI,{kind:'zombie',hurt:hitAge>0&&hitAge<.3?1:0});
  for(const c of CASTS)drawCast(m,t,c,embers);
 },
};
