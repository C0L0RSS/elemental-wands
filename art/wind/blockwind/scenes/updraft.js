// Runtime port: UpdraftManager sends the launch point once (it already has UpdraftLaunchPayload for the owner; the
// effect needs a small broadcast to nearby players). Clients draw the floor puffs, the feet ring and the spiral from
// that point and the arrival tick, and the under-feet streaks from the launched player's own position while they
// rise. The 20 white smoke puffs and the WIND_AIR_RIBBON spiral become cubes; the safe-landing puff is new and
// optional (Updraft's fall protection ends at the first landing). Cube budget: about 170 at launch, 40 in flight.
import{cube,streak,ring,puff,spiral,wisp,motes,figure,EYE}from'../kit.js';
import{add,sub,mul,norm,clamp,lerp,smoothstep,rnd,TAU,UP}from'../engine.js';

// ---- Real numbers (UpdraftManager) -------------------------------------------------------------------------
const TICK=.05,UPWARD=1.343,GRAVITY=.08,DRAG=.98;   // about ten blocks of rise in clear space
const T0=.45,DURATION=4.2;
const HEIGHTS=(()=>{const out=[0];let y=0,v=UPWARD;for(let n=0;n<200&&(n<2||y>0);n++){y=Math.max(0,y+v);v=(v-GRAVITY)*DRAG;out.push(y)}return out})();
const AIR=(HEIGHTS.length-1)*TICK;                   // launch to landing
const APEX=Math.max(...HEIGHTS);
function height(t){const a=t-T0;if(a<=0||a>=AIR)return 0;const f=a/TICK,i=Math.floor(f),k=f-i;return lerp(HEIGHTS[i],HEIGHTS[i+1],k)}
function rising(t){const a=t-T0;return a>0&&a<AIR&&height(t+.01)>height(t)}
const PUFFS=20;

export default{
 id:'updraft',name:'Updraft',slot:'Technique',duration:DURATION,
 summary:`A ring of twenty cloud puffs rolls out across the floor while a coil of streaks winds up around your launch, and short speed lines hang under your feet as you rise about ${Math.round(APEX)} blocks.`,
 notes:'Real numbers: one 1.343 blocks/tick vertical launch with normal gravity and drag (about ten blocks in clear space); horizontal momentum and air control stay yours.\nThe floor puffs replace the twenty white smoke puffs; the coil replaces the rising Wind spiral and unwinds from the bottom once you clear it.\nThe small puff on landing is new and optional: it shows the one safe landing Updraft gives you.',
 phases:[[0,'Ready'],[T0,'Launch'],[T0+.6,'Rising'],[T0+AIR*.5,'Falling'],[T0+AIR,'Safe landing']],
 area:{x0:-6,x1:7,z0:-6,z1:7},budget:320,
 cameras:{
  orbit:{target:[0,4.2,0],yaw:-2.2,pitch:.12,distance:13},
  caster:t=>{const y=height(t);return{eye:[0,y+EYE,0],look:[4,y+.2,0]}},
  target:{eye:[6,EYE,3],look:[0,2.2,0]},
 },
 draw(m,t,{embers}){
  const y=height(t),age=t-T0,inAir=age>0&&age<AIR;
  figure(m,[0,y,0],0,{pose:inAir?'air':(age>-.15&&age<.1?'cast':'idle')});
  if(age<0)return;
  // Floor: twenty puffs rolling outward, low and spreading, mist on their undersides.
  for(let i=0;i<PUFFS;i++){
   const a=TAU*i/PUFFS+rnd(i)*.2,dir=[Math.cos(a),0,Math.sin(a)],r=.35+.15*rnd(i+3);
   puff(m,add(mul(dir,r),[0,.22,0]),age-rnd(i+7)*.05,{size:.42+.12*rnd(i+5),seed:i*3,duration:.85,drift:add(mul(dir,1.9+.4*rnd(i+9)),[0,.25,0]),lobes:3});
  }
  ring(m,[0,.08,0],UP,lerp(.3,1.7,smoothstep(age/.3)),{count:18,size:.08*(1-smoothstep(age/.35)),band:'gale',alpha:.9,phase:age});
  // Coil: winds up with you for the first 0.45 s, then unwinds from the bottom.
  const coilTop=Math.min(y+.8,5);
  spiral(m,[0,.1,0],{radius:.7,height:Math.max(.5,coilTop),turns:2,count:26,width:.15,phase:t*6,k:clamp((age-.45)/.6),seed:11});
  spiral(m,[0,.1,0],{radius:.7,height:Math.max(.5,coilTop),turns:2,count:26,width:.11,phase:t*6+Math.PI,k:clamp((age-.5)/.6),seed:41,alpha:.55});
  // Under-feet speed lines while rising fast.
  const v=(height(t+.02)-y)/.02;
  if(rising(t)&&v>4)for(let i=0;i<3;i++){
   const a=TAU*i/3+t*4,o=[Math.cos(a)*.2,0,Math.sin(a)*.2],l=Math.min(1.8,v*.08);
   streak(m,add([0,y-.05,0],o),add([0,y-.05-l,0],o),{width:.11,band:i?'streak':'gale',alpha:i?.65:1,seed:i,up:[1,0,0]});
  }
  if(embers){motes(m,[0,.4,0],age,{count:10,dir:UP,seed:4,duration:.8,speed:2});
   for(let i=0;i<4;i++)wisp(m,[Math.cos(i*1.57)*.5,.3,Math.sin(i*1.57)*.5],[Math.cos(i*1.57),.8,Math.sin(i*1.57)],age-.05*i,{seed:30+i,duration:.6,size:.08})}
  // Safe landing: a soft, small puff ring (optional; new).
  const land=t-(T0+AIR);
  if(land>=0)for(let i=0;i<6;i++){const a=TAU*i/6;puff(m,[Math.cos(a)*.35,.15,Math.sin(a)*.35],land,{size:.26,seed:60+i,duration:.5,drift:[Math.cos(a)*.7,.05,Math.sin(a)*.7],lobes:2})}
 },
};
