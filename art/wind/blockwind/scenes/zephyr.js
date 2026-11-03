// Runtime port: ZephyrStrikeManager already knows each phase. Ascent streams and descent rings would be drawn by a
// client effect while the player has the active-strike state (a tracked flag or a start/end payload), from the
// player's interpolated position and velocity, instead of WIND_SLIPSTREAM/WIND_SHEAR_FEATHER particles. The landing
// burst needs one payload (centre, visual radius) in place of LANDING_BURSTS' eight ticks of particles. The worn wings
// are a proposal: a custom 3D wing layer instead of the texture on vanilla Elytra geometry (needs your OK, since the
// Elytra geometry is kept on purpose today). Cube budget: about 140 wings, 60 ascent, 90 descent, 430 at landing.
import{cube,streak,ring,puff,wisp,motes,spiral,wings,figure,flowAxes,torso,sphereDir,lookDir,EYE}from'../kit.js';
import{add,sub,mul,norm,len,clamp,lerp,smoothstep,rnd,TAU,UP}from'../engine.js';

// ---- Real numbers (WindAbilityHandler.castUltimate, ZephyrStrikeManager) -------------------------------------
const TICK=.05,LAUNCH_V=3.52,GRAVITY=.08,DRAG_Y=.98,DRAG_XZ=.91,LANDING_TICKS=8;
const T0=.5,ASCENT_SHOWN=.7,DESCENT=1.55,LAND=2.2,DURATION=3.8;
const ASCENT=(()=>{const out=[[0,0,0]];let p=[0,0,0],v=[LAUNCH_V,LAUNCH_V,0];
 for(let n=0;n<60;n++){p=add(p,v);v=[v[0]*DRAG_XZ,(v[1]-GRAVITY)*DRAG_Y,0];out.push(p)}return out})();
const APEX=Math.max(...ASCENT.map(p=>p[1]));
const LX=13,DIVE_FROM=[LX-4,26,0],LANDING=[LX,0,0];
const DIVE_V=len(sub(LANDING,DIVE_FROM))/((LAND-DESCENT)/TICK);           // blocks per tick at impact
const POWER=3+DIVE_V*2,RADIUS=clamp(1.8+POWER*.88,4.5,9);                  // createWindImpact
const BYSTANDERS=[[LX+3.4,0,1.2],[LX-1.8,0,-3.6],[LX+2,0,5.4]];

function body(t){
 const a=t-T0;
 if(a<=0)return[0,0,0];
 if(t<DESCENT){const f=Math.min(a,ASCENT_SHOWN)/TICK,i=Math.floor(f),k=f-i;return add(mul(ASCENT[i],1-k),mul(ASCENT[i+1],k))}
 if(t<LAND){const k=(t-DESCENT)/(LAND-DESCENT);return add(mul(DIVE_FROM,1-k*k),mul(LANDING,k*k))}
 return LANDING;
}
function velocity(t){return mul(sub(body(t+.02),body(t)),1/.02*TICK)}       // blocks per tick
const phase=t=>t<T0?'ready':t<DESCENT?'ascent':t<LAND?'descent':'landed';

function landing(m,t,embers){
 const a=t-LAND;if(a<0||a>1.4)return;
 const p=clamp(a/(LANDING_TICKS*TICK)),R=RADIUS*(.12+.88*(1-(1-p)**2)),fade=1-smoothstep((a-.35)/.6);
 // Shock dome: a quick hemisphere of air cubes that pops and thins.
 const dome=clamp(a/.2);
 if(dome<1)for(let i=0;i<34;i++){const d=sphereDir(i,68);if(d[1]<0)continue;
  cube(m,add(LANDING,mul(d,.4+2.4*smoothstep(dome))),.32*(1-dome),{band:i%3?'cloud':'gale',seed:i,axes:flowAxes(d),alpha:.7});}
 // Outer front: a rolling wall of bright overlapping cloud clusters racing out to the visual radius (grey cubes in a
 // ring read as a stone circle), with radial streaks behind it.
 const N=30;
 for(let i=0;i<N;i++){
  const ang=TAU*(i+rnd(i)*.4)/N+a*.17,dir=[Math.cos(ang),0,Math.sin(ang)],jitter=.35*(rnd(i+5)-.5);
  const c=add(LANDING,add(mul(dir,R+jitter),[0,.38+.18*rnd(i+1),0]));
  puff(m,c,a-rnd(i+2)*.05,{size:(.7+.3*rnd(i+3))*(1-p*.25),seed:i*7,duration:1.05,drift:[0,.35,0],spread:.35,lobes:4,alpha:.68});
  if(i%3===0)streak(m,add(LANDING,add(mul(dir,R*.5),[0,.22,0])),add(LANDING,add(mul(dir,R*.88),[0,.28,0])),{width:.13*fade,band:i%2?'streak':'gale',seed:i+200,alpha:.75});
 }
 // Inner front at 0.62 R: a crisp ring of gale slabs.
 ring(m,add(LANDING,[0,.12,0]),UP,R*.62,{count:26,size:.11*fade,band:'gale',alpha:.95,phase:-a*.72,seed:300});
 // Central column: stacked turning rings that sink from 3.8 blocks as the burst spreads.
 const H=.45+(1-p)*3.4;
 for(let i=0;i<4;i++)ring(m,add(LANDING,[0,.3+H*i/4,0]),UP,.55+.25*i+p*.6,{count:10,size:.1*fade*(1-p*.5),band:i%2?'streak':'cloud',alpha:.75,phase:a*(4+i)*(i%2?-1:1),seed:320+i*7});
 if(embers){motes(m,add(LANDING,[0,.6,0]),a,{count:16,spread:2,speed:4,seed:9,duration:.9});
  for(let i=0;i<8;i++){const ang=TAU*i/8;wisp(m,add(LANDING,[Math.cos(ang)*R*.5,.4,Math.sin(ang)*R*.5]),[Math.cos(ang),.25,Math.sin(ang)],a-.05,{seed:400+i,duration:.6,size:.1,length:.9})}}
}

export default{
 id:'zephyr',name:'Zephyr Strike',slot:'Ultimate',duration:DURATION,
 summary:'You are flung skyward on feathered 3D wings with streams pouring off them, then dive back through tightening pressure rings and slam down in a dome of air and a rolling ring of cloud that races out across the ground.',
 notes:`Real numbers: the launch is 3.52 blocks/tick up and forward (about ${Math.round(APEX)} blocks of rise in game). This loop cuts the top of the arc: it shows the first 0.7 s, then the dive.\nLanding: power 3 + 2 × speed, so this dive gives a ${RADIUS.toFixed(1)}-block visual radius, and both fronts reach it in 8 ticks.\nThe wings are a proposal. Today the strike wears a texture on vanilla Elytra geometry; these are a separate 3D wing model with painted feathers. Tell me if you'd rather keep the Elytra shape.`,
 phases:[[0,'Ready'],[T0,'Launch'],[T0+ASCENT_SHOWN,'(top of the arc cut)'],[DESCENT,'Dive'],[LAND,'Impact'],[LAND+.6,'Settle']],
 area:{x0:-4,x1:24,z0:-9,z1:10},budget:700,
 cameras:{
  orbit:{target:[9,3,0],yaw:-2.05,pitch:.2,distance:16},
  caster:t=>{const p=add(body(t),[0,EYE,0]);return phase(t)==='descent'?{eye:p,look:add(LANDING,[0,0,0])}:{eye:p,look:add(p,[5,t<T0?-.6:1.5,0])}},
  target:{eye:[LX+7,EYE,3],look:[LX,2.2,0]},
 },
 draw(m,t,{embers}){
  const ph=phase(t),p=body(t),v=velocity(t),speed=len(v);
  // Out of view during the cut: nothing to draw but the scenery.
  const hidden=ph==='ascent'&&t-T0>ASCENT_SHOWN;
  const lean=ph==='descent'?.5:ph==='ascent'?-.15:0;
  if(!hidden){
   figure(m,p,0,{pose:ph==='ascent'||ph==='descent'?'air':(t>T0-.15&&t<T0+.05?'cast':'idle'),lean});
   const worn=t>=T0&&t<LAND+.4;
   if(worn)wings(m,p,0,{lean,spread:ph==='descent'?1:ph==='ascent'?.25:lerp(1,.6,clamp((t-LAND)/.3)),flap:ph==='descent'?Math.sin(t*14)*.4:0,seed:5});
  }
  for(const[i,b]of BYSTANDERS.entries()){
   const a=t-LAND,out=norm(sub(b,LANDING)),shove=a>0?Math.min(1.6,a*6)*(1-.1*i):0,hop=a>0&&a<.5?Math.sin(a/.5*Math.PI)*.8:0;
   figure(m,add(b,add(mul(out,shove),[0,hop,0])),Math.atan2(-out[2],-out[0]),{kind:'zombie',hurt:a>0&&a<.3?1:0});
  }
  // Launch: a big pressure ring along the launch line and puffs bursting off the ground.
  const la=t-T0;
  if(la>=0&&la<1){
   const dir=norm([1,1,0]);
   ring(m,add([0,.9,0],mul(dir,.4)),dir,lerp(.6,2.2,smoothstep(la/.4)),{count:20,size:.16*(1-smoothstep(la/.5)),phase:la,seed:1});
   for(let i=0;i<10;i++){const a=TAU*i/10,d=[Math.cos(a),0,Math.sin(a)];
    puff(m,add(mul(d,.4),[0,.2,0]),la-rnd(i)*.06,{size:.45,seed:i*5,duration:.9,drift:add(mul(d,2.4),[0,.3,0]),lobes:3});}
  }
  // Ascent: streams pour back off each wing root along the velocity.
  if(ph==='ascent'&&!hidden&&speed>.18){
   const dir=norm(v),{back,axes}=torso(p,0,lean),side=axes[2];
   for(const s of[-1,1])for(let lane=0;lane<4;lane++){
    const root=add(back,mul(side,s*(.55+lane*.14))),l=.9+lane*.35+speed*.25;
    streak(m,add(root,mul(dir,-.2-lane*.3)),add(root,mul(dir,-.2-lane*.3-l)),{width:.14-lane*.02,band:lane%2?'streak':'gale',alpha:lane%2?.65:.9,seed:lane*2+s});
   }
   if(embers)for(let i=0;i<3;i++)wisp(m,add(back,mul(side,(i-1)*.7)),mul(dir,-1),(la*3+i/3)%1,{seed:i+40,duration:1,size:.08,length:.7});
  }
  // Dive: two compression rings tighten around the body and slide up it as you fall.
  if(ph==='descent'){
   const c=add(p,[0,.95,0]);
   for(let k=0;k<3;k++){
    const cyc=((t-DESCENT)*3.2+k/3)%1;
    ring(m,add(c,[0,lerp(-.6,1.1,cyc),0]),UP,lerp(1.9,.55,cyc),{count:16,size:.1*Math.sin(cyc*Math.PI),band:k%2?'streak':'cloud',alpha:.8,phase:t*3.6*(k%2?-1:1),seed:500+k});
   }
   streak(m,add(c,[0,1.4,0]),add(c,[0,4.2,0]),{width:.08,band:'gale',alpha:.9,up:[1,0,0]});
   for(const s of[-1,1])streak(m,add(c,[0,1,s*.5]),add(c,[0,3.4,s*.7]),{width:.06,alpha:.6,up:[1,0,0],seed:s});
  }
  landing(m,t,embers);
 },
};
