// Runtime port: GravityBombRenderer draws gravityBomb() while the bomb flies, then the formed well from the entity's
// tracked state (WELL, IMPACT_AGE, COLLAPSE_START, BURST_START already give the opening, pull, buildup and burst clocks,
// including for late arrivals), replacing the crying-obsidian shell, the ribbons and the 480 billboard motes. No new
// network message is needed. Stars stay with the particle setting; the boundary, vortex and core do not.
// Cube budget: about 20 in flight, 130 while pulling, 160 at the burst.
import{gravityBomb,core,ring,inflow,spill,stars,piece,plate,figure,lookDir,basis,EYE}from'../kit.js';
import{add,sub,mul,clamp,lerp,smoothstep,rnd,TAU,UP}from'../engine.js';

// ---- Real numbers (GravityBombEntity, GravityWellManager) ----------------------------------------------------
const TICK=.05,RADIUS=4,DURATION_S=80*TICK,BUILDUP=8*TICK,BURST=12*TICK;
const T_THROW=.3,CORE_LIFT=.6,DURATION=6.5;

/** The bomb's flight, tick by tick: from 0.12 below the eye at 1.05 along the aim plus 0.22 up, gravity 0.06. */
const TRAJ=(()=>{
 let p=[0,EYE-.12,0],v=add(mul(lookDir(0),1.05),[0,.22,0]);const pts=[p];
 for(let i=0;i<100;i++){const to=add(p,v);if(to[1]<=0){const k=p[1]/(p[1]-to[1]);pts.push(add(p,mul(v,k)).map((x,j)=>j===1?.12:x));break}p=to;pts.push(p);v=add(mul(v,.99),[0,-.06,0])}
 return pts;
})();
const T_IMPACT=T_THROW+(TRAJ.length-1)*TICK,WELL=TRAJ[TRAJ.length-1],CORE=add(WELL,[0,CORE_LIFT,0]);
const T_COLLAPSE=T_IMPACT+DURATION_S,T_BURST=T_COLLAPSE+BUILDUP;
const bombAt=t=>{const f=clamp((t-T_THROW)/TICK,0,TRAJ.length-1),i=Math.min(TRAJ.length-2,Math.floor(f)),k=f-i;return add(mul(TRAJ[i],1-k),mul(TRAJ[i+1],k))};

// Two zombies inside the field, dragged toward the centre while it pulls (bounded inward speed, as in pullTargets).
const ZOMBIES=[[3.1,0,1.4],[-2.2,0,-2.6]].map(o=>({start:add([WELL[0],0,WELL[2]],o)}));
function zombieAt(z,t){
 const pull=clamp((t-T_IMPACT)/(T_BURST-T_IMPACT)),toward=sub([WELL[0],0,WELL[2]],z.start);
 return add(z.start,mul(toward,.62*smoothstep(pull)));
}

/** The formed well: a void core and corona, two broken orbits, a dark floor disk, a segmented boundary and a vortex. */
function drawWell(m,t,embers){
 const age=t-T_IMPACT;if(age<0||t>T_BURST)return;
 const grow=smoothstep(age/.5),build=clamp((t-T_COLLAPSE)/BUILDUP);
 const r=lerp(.3,.48,build)*grow;
 core(m,CORE,r,{seed:3,spin:1.4+build*3});
 ring(m,CORE,[.35,1,.15],.78*grow,{count:9,size:.07,band:'rim',gaps:.3,phase:t*2.4*(1+build*2),seed:5});
 ring(m,CORE,[-.4,1,.3],1.02*grow,{count:11,size:.06,band:'glass',gaps:.35,phase:-t*1.7*(1+build*2),seed:7});
 // Dark floor disk: three stepped void slabs under the core.
 for(let i=0;i<3;i++){const w=(1.9-i*.5)*grow*(1-build*.4);plate(m,add(WELL,[0,.02+i*.012,0]),UP,w,w,.03,{band:i===2?'nebula':'void',seed:11+i,roll:i*.5+t*.2})}
 // Segmented four-block boundary, low on the floor; it tightens a little in the buildup.
 ring(m,add(WELL,[0,.06,0]),UP,RADIUS*grow*(1-build*.12),{count:22,size:.11,depth:.05,band:'rim',gaps:.22,phase:t*.35,seed:13,flat:true});
 // The vortex: four arms of large glass plates spiralling in from the boundary to the core and swallowed there.
 inflow(m,CORE,t,{count:24,radius:RADIUS*grow,period:2.3,seed:17,size:.42,spin:3.2,normal:UP,lift:-CORE_LIFT+.08,k:build,band:'glass'});
 if(embers)inflow(m,CORE,t+.4,{count:14,radius:RADIUS*.9*grow,period:1.6,seed:19,size:.07,spin:4,normal:UP,lift:.3,k:build,band:'starlight'});
}

/** The burst: the core is gone, a rim ring and glass shards blow outward, a pinpoint marks where it was. */
function drawBurst(m,t,embers){
 const age=t-T_BURST;if(age<0||age>BURST)return;
 const k=age/BURST;
 ring(m,add(WELL,[0,.3,0]),UP,lerp(.5,RADIUS+.5,smoothstep(k*1.4)),{count:22,size:.16*(1-k),depth:.08,band:'rim',phase:.3,seed:23,flat:true});
 spill(m,CORE,age,{count:16,speed:8,duration:BURST,seed:25,size:.34});
 const pin=k/.3;if(pin<1)piece(m,CORE,.3*(1-pin),{band:'starlight',seed:27,axes:basis(UP,Math.PI/4)});
 if(embers)stars(m,CORE,age,{count:12,spread:3,duration:BURST,seed:29,inward:false});
}

export default{
 id:'well',name:'Gravity Well',slot:'Technique',duration:DURATION,
 summary:'The bomb is a void cube inside six glass plates. On impact the plates blow open and a vortex of large glass plates spirals across the floor into a hovering eclipse core, inside a segmented boundary at the 4-block pull radius. It collapses in a short buildup and bursts outward.',
 notes:'Thrown at 1.05 a tick plus 0.22 up (gravity 0.06): about 12 blocks on level ground. The well pulls for 4 s, then builds up for 0.4 s (the vortex speeds up, the core swells, the boundary tightens) and bursts for 0.6 s, dealing its 6 damage.\nThe stream takes about 2.3 s from the boundary to the core, like the current motes, so the field always reads as falling inward.\nThe boundary, vortex and core ignore the particle setting; only the stars are particles.',
 phases:[[0,'Ready'],[T_THROW,'Throw'],[T_IMPACT,'Impact · field opens'],[T_IMPACT+.5,'Pull · 4 blocks'],[T_COLLAPSE,'Buildup'],[T_BURST,'Burst']],
 area:{x0:-4,x1:20,z0:-8,z1:8},budget:280,
 cameras:{
  orbit:{target:add(WELL,[-2,.8,0]),yaw:.5,pitch:.42,distance:13},
  caster:{eye:[0,EYE,0],look:[WELL[0],.6,WELL[2]]},
  target:t=>{const z=zombieAt(ZOMBIES[0],t),eye=add(z,[0,1.7,0]);return{eye,look:CORE}},
 },
 draw(m,t,{embers}){
  figure(m,[0,0,0],0,{pose:t>=T_THROW&&t<T_THROW+.4?'cast':'idle'});
  const hurt=t>=T_BURST?clamp(1-(t-T_BURST)/.5):0;
  for(const z of ZOMBIES){const p=zombieAt(z,t),d=sub([WELL[0],0,WELL[2]],p);figure(m,p,Math.atan2(d[2],d[0])+Math.PI,{kind:'zombie',hurt})}
  if(t>=T_THROW&&t<T_IMPACT+.35){
   const open=clamp((t-T_IMPACT)/.35),p=open>0?add(WELL,[0,lerp(.2,CORE_LIFT,open),0]):bombAt(t);
   gravityBomb(m,p,t,{seed:31,open});
   if(embers&&open===0)stars(m,p,t,{count:3,spread:.45,duration:.35,seed:33,size:.04});
  }
  drawWell(m,t,embers);
  drawBurst(m,t,embers);
 },
};
