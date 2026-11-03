// Runtime port: GaleDaggerRenderer keeps its box model and flat colours and grows it from the grip while forging.
// The forging intro is client-only from
// the entity's age while unfired. GALE_DAGGER_TRAIL rings become cube rings spawned client-side every 1.2 blocks of
// the fired dagger's interpolated path; the hit puff needs the existing hit event. Cube budget: 3 x 77 dagger boxes
// (solid, a fixed mesh) plus about 90 trail and hit cubes.
import{dagger,cube,ring,puff,wisp,motes,streak,figure,flowAxes,EYE}from'../kit.js';
import{add,sub,mul,norm,len,dist,clamp,lerp,smoothstep,rnd,TAU,UP}from'../engine.js';

// ---- Real numbers (GaleDaggers, GaleDaggerEntity) ----------------------------------------------------------
const TICK=.05,SPEED=3/TICK,SPACING=3*TICK,HEIGHT=1.8;   // 60 blocks/s, shots 0.15 s apart
const PREPARE=.3,FORGE=.4,LAUNCH=1.45,DURATION=2.9;
const TARGET=[14,0,0],AIM=[TARGET[0]-.3,1.35,0];          // every shot converges on the crosshair's hit point
const RING_GAP=1.2,RING_LIFE=.32;

/** GaleDaggers.anchor: index 0 right, 1 centre and higher, 2 left (caster faces +x, right is +z). */
const anchor=i=>[0,HEIGHT+(i===1?.57:.24),(1-i)*.61];
const bob=(i,t)=>[0,Math.sin(t*3.1+i*2.1)*.03,0];
const shotTime=i=>LAUNCH+i*SPACING;
const flight=i=>dist(anchor(i),AIM)/SPEED;

function daggerAt(i,t){
 const s=shotTime(i);
 if(t<s)return{p:add(anchor(i),bob(i,t)),fwd:[1,0,0],roll:(i-1)*10.3*Math.PI/180,state:'held'};
 const a=t-s,dir=norm(sub(AIM,anchor(i))),d=Math.min(a*SPEED,dist(anchor(i),AIM));
 return{p:add(anchor(i),mul(dir,d)),fwd:dir,roll:0,state:a<flight(i)?'flying':'hit',hitAge:a-flight(i),dir};
}

export default{
 id:'daggers',name:'Gale Daggers',slot:'Technique',duration:DURATION,
 summary:'Three daggers are forged out of a gust over your head, keeping their approved pearl look. They fire 0.15 s apart and leave crisp cloud rings behind them.',
 notes:'Real numbers: anchors above and beside the head (centre 0.57 higher), shots 3 ticks apart at 3 blocks/tick, up to 40 blocks, 5 damage each with no falloff. All three converge on the crosshair point.\nThe dagger keeps its approved stepped model and flat pearl colours (an atlas-painted version was tried and dropped); only the effects around it are new.\nForging is new: curls of wind spiral in while the dagger builds from grip to tip.\nTrail rings expand and thin out over 0.32 s; each hit bursts a small cloud.',
 phases:[[0,'Ready'],[PREPARE,'Forge'],[PREPARE+FORGE,'Prepared'],[LAUNCH,'Volley'],[LAUNCH+.6,'Clear']],
 area:{x0:-4,x1:17,z0:-5,z1:5},budget:420,
 cameras:{
  orbit:{target:[2.5,1.8,0],yaw:-2.3,pitch:.22,distance:6.5},
  caster:{eye:[0,EYE,0],look:[10,2.2,0]},
  target:{eye:[TARGET[0],EYE,0],look:[0,2,0]},
 },
 draw(m,t,{embers}){
  const castPose=(t>PREPARE-.05&&t<PREPARE+.25)||(t>LAUNCH-.05&&t<LAUNCH+.5);
  figure(m,[0,0,0],0,{pose:castPose?'cast':'idle',pitch:t>LAUNCH-.1?.05:.5});
  const hits=[0,1,2].map(i=>t-shotTime(i)-flight(i)).filter(a=>a>=0);
  const lastHit=hits.length?Math.min(...hits):9;
  figure(m,add(TARGET,[Math.min(.6,hits.length*.18),0,0]),Math.PI,{kind:'zombie',hurt:lastHit<.25?1:0});
  if(t<PREPARE)return;
  for(let i=0;i<3;i++){
   const forge=clamp((t-PREPARE-i*.06)/FORGE),dg=daggerAt(i,t);
   // Forging: curls of air spiral in to the anchor while the blade builds from the grip out.
   if(forge<1){
    for(let j=0;j<6;j++){
     const a=TAU*j/6+forge*5+i,r=lerp(.9,.05,smoothstep(forge)),p=add(anchor(i),[Math.cos(a)*r*.4+(1-forge)*-.2,Math.sin(a)*r*.5,Math.cos(a+1)*r]);
     cube(m,p,.09*(1-forge)+.02,{band:j%2?'streak':'cloud',seed:i*10+j,axes:flowAxes([-Math.sin(a),Math.cos(a),0]),alpha:.75});
    }
   }
   if(dg.state==='held'||dg.state==='flying')dagger(m,dg.p,dg.fwd,UP,{roll:dg.roll,grow:forge});
   if(dg.state==='hit'&&dg.hitAge<.12)dagger(m,dg.p,dg.fwd,UP,{grow:1-dg.hitAge/.12});
   if(dg.state==='held')continue;
   // Trail: rings left every RING_GAP blocks where the dagger passed, plus a short streak behind it.
   const a=t-shotTime(i),travelled=Math.min(a*SPEED,dist(anchor(i),AIM));
   for(let d=.8;d<travelled;d+=RING_GAP){
    const age=a-d/SPEED;if(age>RING_LIFE)continue;
    const k=age/RING_LIFE;
    ring(m,add(anchor(i),mul(dg.dir,d)),dg.dir,lerp(.1,.48,smoothstep(k*1.4)),{count:10,size:.055*(1-k),band:d%2.4<1.2?'gale':'cloud',alpha:.85,phase:d,seed:i*20+d});
   }
   if(dg.state==='flying')streak(m,add(dg.p,mul(dg.dir,-.6)),add(dg.p,mul(dg.dir,-1.8)),{width:.05,alpha:.6,seed:i});
   if(dg.state==='hit'){
    puff(m,dg.p,dg.hitAge,{size:.3,seed:70+i,duration:.5,drift:add(mul(dg.dir,.5),[0,.2,0]),lobes:3});
    if(embers){motes(m,dg.p,dg.hitAge,{count:6,dir:mul(dg.dir,-1),seed:80+i,duration:.4});wisp(m,dg.p,[-1,.4,i-1],dg.hitAge,{seed:90+i,duration:.4,size:.07})}
   }
  }
 },
};
