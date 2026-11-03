// Runtime port: a client-side FlamethrowerStreamRenderer (world render event) draws one stream per spraying
// player and replaces FireBuildManager.flameVisual's FIRE_FLAME_RIBBON particles. Client state per caster:
// spray start tick, heat (FireBuildPayload already sends it to the caster; trackers need a small broadcast of
// spraying + overheat tick), and the interpolated eye position and aim. Puffs are a pure function of
// (spray start, puff index), so every client sees the same stream. Each puff casts one block ray from the eye
// (like flameVisual's rays) once when it is born and caches the stop distance and face normal. For the local
// player in first person the renderer also thins puffs near the crosshair (sightScale below) on top of
// SpellViewClearance. Smoke and sparks are cubes here and are skipped with reduced particles; Minecraft's own
// on-fire overlay on targets stays. Cube budget: about 240 at full heat, capped at 450.
import{add,sub,mul,dot,cross,norm,len,clamp,smoothstep,rnd,noise,TAU,UP}from'../engine.js';
import{cube,flame,smoke,burst,blocks,figure,basis,rotate,heatBand,lookDir,wandTip,EYE}from'../kit.js';

// ---- Gameplay numbers (FireBuildRules, FireBuildManager, SpellCastVisuals) --------------------------
const RANGE=6;                                 // FireBuildRules.RANGE
const HEAT_PER_S=1.25*20,COOL_PER_S=1.5*20;    // HEAT_PER_TICK, COOL_PER_TICK
const UNLOCK_HEAT=25;                          // overheat lock lifts at this heat
const SPRAY=100/HEAT_PER_S;                    // 4 s from cold to overheat
const LOCK=(100-UNLOCK_HEAT)/COOL_PER_S;       // 2.5 s locked after overheat
const HIT_EVERY=10;                            // DAMAGE_INTERVAL in ticks
const IGNITE=2;                                // IGNITE_SECONDS
const WAKE=3;                                  // SpellCastVisuals.WAKE_BLEND: stream leaves the tip, joins the eye line by 3 blocks
const damageAt=ticks=>.5+2*clamp(ticks/60);    // FireBuildRules.flameDamage

// ---- Timeline ---------------------------------------------------------------------------------------
const T0=.3,TOVER=T0+SPRAY,TUNLOCK=TOVER+LOCK,DURATION=7.6;
const SPUTTER=.45;                             // visual-only warning before overheat
const HITS=Array.from({length:Math.ceil(SPRAY*20/HIT_EVERY)},(_,i)=>T0+(1+i*HIT_EVERY)/20);
const SPRAY_DAMAGE=HITS.reduce((sum,h,i)=>sum+damageAt(1+i*HIT_EVERY),0); // ≈ 13.2 over the 8 hits

// ---- Stage ------------------------------------------------------------------------------------------
const YAW=0,PITCH=-.1,EYE_P=[0,EYE,0],AIM=lookDir(YAW,PITCH);
const RIGHT=norm(cross(AIM,UP)),VUP=cross(RIGHT,AIM);
const TIP=wandTip([0,0,0],YAW,PITCH),TIP_OFF=sub(TIP,EYE_P);
const WALL={min:[4,0,0],max:[5,3,3]};          // stone wall covering the right half of the cone
const ZOMBIE=[3.2,0,-.75];

// ---- Stream shape -----------------------------------------------------------------------------------
const SPREAD=.3;                               // puff directions fill a cone just inside the damage cone (0.25 + 0.32 × distance)
const D0=.15;                                  // puffs start where flameVisual's first particle does
const EASE=2.2;                                // fast out of the nozzle, slowing toward the reach
const RATE0=80,RATE1=150;                      // puffs per second, cold → full heat
const ACC=(RATE1-RATE0)/(2*SPRAY);
const emitted=tau=>RATE0*tau+ACC*tau*tau;      // puffs born after tau seconds of spraying
const emitAt=k=>T0+(Math.sqrt(RATE0*RATE0+4*ACC*k)-RATE0)/(2*ACC);
const MAX_AGE=.66+.45+.95;

/** Close to overheat the nozzle coughs: emission drops out in short gaps that widen until it stops. */
function nozzleGate(e){
 if(e<TOVER-SPUTTER)return 1;if(e>=TOVER)return 0;
 const q=(e-(TOVER-SPUTTER))/SPUTTER;
 return smoothstep((noise(e*10+3.7)-(.18+.55*q))/.12);
}

/** First block the ray from o along unit d meets: the ground (y = 0) or the wall. */
function rayHit(o,d){
 let best=Infinity,n=null;
 if(d[1]<-1e-6){const g=-o[1]/d[1];if(g>0){best=g;n=[0,1,0]}}
 let t0=-Infinity,t1=Infinity,axis=0,sign=0;
 for(let i=0;i<3;i++){
  if(Math.abs(d[i])<1e-9){if(o[i]<WALL.min[i]||o[i]>WALL.max[i]){t0=Infinity;break}continue}
  let a=(WALL.min[i]-o[i])/d[i],b=(WALL.max[i]-o[i])/d[i],s=-1;
  if(a>b){[a,b]=[b,a];s=1}
  if(a>t0){t0=a;axis=i;sign=s}
  t1=Math.min(t1,b);
 }
 if(t0<=t1&&t0>0&&t0<best){best=t0;n=[0,0,0];n[axis]=sign}
 return{t:best,n};
}

/** The visual path: a straight eye-line ray, pulled onto the wand tip near the caster (SpellCastVisuals.Wake). */
const pathAt=(o,dir,s)=>add(add(o,mul(dir,s)),mul(TIP_OFF,1-smoothstep(s/WAKE)));

/**
 * First person only: your own stream thins out around the crosshair so you can aim through it.
 * Puffs within about 0.25 rad of the sight line shrink to a third; everything off-axis keeps its size.
 */
function sightScale(p,cam){
 if(cam!=='caster')return 1;
 const v=sub(p,EYE_P),along=dot(v,AIM);if(along<.3)return .7;
 const ang=len(sub(v,mul(AIM,along)))/along;
 // The first two blocks of the jet also run a little slimmer so the nozzle doesn't crowd the lower right.
 return (.3+.7*smoothstep((ang-.05)/.22))*(.7+.3*smoothstep((along-.9)/1.6));
}

/** One puff of the stream, born at emitAt(k): flies, grows and cools, then splashes on a block or turns to smoke. */
function streamPuff(m,k,t,ctx){
 const e=emitAt(k),age=t-e;if(age<0||age>MAX_AGE)return;
 const sd=k*7.31+11;if(rnd(sd+4.4)>nozzleGate(e))return;
 // Heat at birth: hotter puffs leave the tip whiter and stay yellow further out.
 const hk=clamp((e-T0)/SPRAY),top=3.25+.9*hk+.5*(rnd(sd+.05)-.5),grow=.94+.18*hk;
 const a=rnd(sd+.1)*TAU,r=Math.sqrt(rnd(sd+.2))*.95,radial=add(mul(RIGHT,Math.cos(a)),mul(VUP,Math.sin(a)));
 const dir=norm(add(AIM,mul(radial,r*SPREAD))),o=add(EYE_P,mul(radial,r*.06));
 const life=.5+.16*rnd(sd+.3),reach=RANGE*(.92+.1*rnd(sd+.4)),j=.8+.4*rnd(sd+.5);
 const hit=rayHit(o,dir),stop=Math.max(D0+.05,hit.t-.2),blocked=stop<reach;
 const uHit=blocked?1-Math.pow(1-(stop-D0)/(reach-D0),1/EASE):1;
 const sAt=u=>D0+(reach-D0)*(1-Math.pow(1-u,EASE));
 const fOf=s=>clamp((s-D0)/(RANGE-D0));
 // A tight jet of small cubes at the nozzle that blooms toward the reach; each puff grows out of the tip flame.
 const widthAt=s=>j*grow*(.075+.48*Math.pow(fOf(s),1.15))*smoothstep((s-D0)/.4);
 // Yellow (or white-hot) at the tip, orange through the middle, red at the reach. Never the dark ember band in flight.
 const bandAt=s=>Math.max(1,top-2.7*fOf(s));
 const u=age/life;
 if(u<Math.min(uHit,1)){
  // In flight: a little stretched along its path while fast, tumbling more as it slows.
  const s=sAt(u),p=pathAt(o,dir,s),along=norm(sub(pathAt(o,dir,s+.05),p)),speed=Math.pow(1-u,EASE-1);
  let w=widthAt(s)*sightScale(p,ctx.cam);if(!blocked)w*=1-smoothstep((u-.7)/.3);
  const kx=norm([rnd(sd+.8)-.5,rnd(sd+.9)-.5,rnd(sd+1)-.5]),th=age*5*smoothstep((u-.08)/.45);
  const ax=basis(along,rnd(sd+.6)*TAU+age*(rnd(sd+.7)<.5?-3:3)).map(v=>rotate(v,kx,th));
  const hb=bandAt(s);
  cube(m,p,[w,w*(1+.8*speed),w],{band:heatBand(hb),seed:sd,axes:ax});
  if(w>.2)for(let i=0;i<2;i++){
   const side=rnd(sd+i*1.9+5)<.5?-1:1;
   cube(m,add(p,mul(ax[i*2],side*w*.5)),w*.55,{band:heatBand(hb-.8),seed:sd+i+1,axes:ax});
  }
  if(ctx.embers&&rnd(sd+3)<.14){
   // A spark flicks out of the side of the stream, shrinking as it goes.
   const u0=.15+.35*rnd(sd+3.1),sa=(age-u0*life)/.35;
   if(sa>0&&sa<1&&u0<uHit){
    const start=pathAt(o,dir,sAt(u0)),drift=add(add(mul(radial,.9),mul(dir,1.1)),[0,.6,0]);
    cube(m,add(start,mul(drift,1.3*(1-Math.pow(1-sa,2)))),.075*(1-sa),{band:heatBand(3.6-2*sa),seed:sd+7,axes:ax});
   }
  }
 }
 if(blocked){
  // Splash: the puff flattens against the block and spreads across it, climbing walls, then burns out.
  const ageH=uHit*life,sDur=.3+.15*rnd(sd+1.1),v=(age-ageH)/sDur;
  if(v<0)return;
  const n=hit.n,surf=add(o,mul(dir,hit.t)),wall=n[1]===0;
  let tan=sub(dir,mul(n,dot(dir,n)));
  let jit=[rnd(sd+1.2)-.5,rnd(sd+1.3)-.5,rnd(sd+1.4)-.5];jit=sub(jit,mul(n,dot(jit,n)));
  if(wall)tan=add(tan,[0,.55,0]);
  tan=norm(add(tan,mul(jit,.9)));
  const spread=(.4+.7*rnd(sd+1.5))*(.6+.6*(1-uHit));
  const wH=Math.max(widthAt(stop),.12),hbH=bandAt(stop);
  const placeAt=vv=>{
   const ws=wH*(1+.3*Math.sin(Math.min(vv*2.5,1)*Math.PI/2))*Math.pow(1-vv,.75);
   let c=add(add(surf,mul(n,.12+ws*.3)),mul(tan,spread*(1-Math.pow(1-vv,2))));
   if(wall)c=[c[0],clamp(c[1],.15,WALL.max[1]+.2),clamp(c[2],WALL.min[2]+.1,WALL.max[2]-.1)];
   return{c,ws};
  };
  if(v<1){
   const{c,ws:w0}=placeAt(v),ws=w0*sightScale(c,ctx.cam),ax=basis(n,rnd(sd+1.6)*TAU+age*2.5);
   cube(m,c,[ws*1.15,ws*.5,ws*1.15],{band:heatBand(hbH-.2-1.6*v),seed:sd,axes:ax});
   if(ws>.18)cube(m,add(c,add(mul(tan,ws*.6),mul(n,ws*.2))),ws*.5,{band:heatBand(hbH-.9-1.6*v),seed:sd+5,axes:ax});
  }
  if(ctx.embers&&rnd(sd+2.1)<.2){
   const{c}=placeAt(.85),q=add(c,mul(n,.2));
   smoke(m,q,age-ageH-sDur*.85,{size:.3*j*grow*sightScale(q,ctx.cam),seed:sd+300,rise:1.1,duration:.75});
  }
  return;
 }
 // Free puffs that reach the end of the range turn to smoke: it starts inside the shrinking puff, drifts on a little and rises.
 if(ctx.embers&&rnd(sd+2)<.2){
  const sa=age-.76*life,dur=.65+.15*rnd(sd+2.2);
  if(sa>0&&sa<dur){
   const p=add(pathAt(o,dir,sAt(.76)),mul(dir,.35*smoothstep(sa/dur)));
   smoke(m,[p[0],Math.max(p[1],.3),p[2]],sa,{size:.36*j*grow*sightScale(p,ctx.cam),seed:sd+200,rise:1,duration:dur});
  }
 }
}

/** The wand tip: a short forward flame while spraying, coughs near overheat, then a dark cooling tip that smokes. */
function nozzle(m,t,ctx){
 if(t>=T0&&t<TOVER+.15){
  const h=clamp((t-T0)/SPRAY),on=smoothstep((t-T0)/.08)*(t<TOVER?1:1-(t-TOVER)/.15),g=.3+.7*nozzleGate(t);
  const fp=ctx.cam==='caster'?.65:1;   // first person: a slimmer nozzle flame next to the eye
  flame(m,TIP,AIM,{width:(.2+.07*h)*on*fp,length:(.5+.25*h)*on*(t<TOVER?g:1),seed:5,heat:1.1+.3*h,count:4,sway:.02,speed:1.8,t});
 }
 if(t>=TOVER&&t<TUNLOCK){
  const k=t-TOVER;
  cube(m,TIP,.155,{band:k<.25?'deep':k<.6?'ember':'char',seed:21,axes:basis(AIM,Math.PI/4)});
 }
 if(!ctx.embers)return;
 // Overheat: the wand coughs one last puff of smoke forward and up, with a few dying sparks.
 // In first person the cough is smaller so it doesn't hide the target for that second.
 const fs=ctx.cam==='caster'?.55:1;
 for(let i=0;i<9;i++){
  const age=t-TOVER-i*.03,side=(rnd(400+i)-.5)*.45,fwd=.25+.6*rnd(405+i);
  smoke(m,add(TIP,add(mul(AIM,fwd),add(mul(RIGHT,side),[0,.08*(i%4),0]))),age,{size:(.3+.22*rnd(410+i))*fs,seed:420+i,rise:1.3,duration:1.1});
 }
 for(let i=0;i<6;i++){
  const sa=(t-TOVER-.02*i)/.45;if(sa<=0||sa>=1)continue;
  const d=norm(add(add(mul(AIM,.8),mul(RIGHT,rnd(430+i)-.5)),[0,.7+.5*rnd(440+i),0]));
  cube(m,add(TIP,mul(d,.8*(1-Math.pow(1-sa,2)))),.07*(1-sa),{band:heatBand(3-2.5*sa),seed:450+i});
 }
 // Locked while cooling: thin wisps rise from the tip.
 for(let i=0;i<5;i++){
  const age=t-(TOVER+.8+i*.4);if(age>0&&t<TUNLOCK+.6)smoke(m,add(TIP,[0,.1,0]),age,{size:.14,seed:460+i,rise:.75,duration:.8});
 }
 if(t>=TUNLOCK)burst(m,add(TIP,mul(AIM,.08)),t-TUNLOCK,{radius:.3,count:5,size:.08,duration:.3,smoke:false,seed:77});
}

/** Ready again: a short flicker of flame at the wand tip that grows and dies back. */
function pilot(m,t){
 const q=(t-TUNLOCK)/.6;if(q<=0||q>=1)return;
 const k=Math.sin(q*Math.PI);
 flame(m,TIP,[AIM[0]*.4,1,AIM[2]*.4],{width:.13*k,length:.24*k,seed:31,heat:1,count:3,sway:.02,t});
}

/** Stand-in for Minecraft's own burning: small flames on the zombie while it is ignited. */
function burning(m,t){
 const from=HITS[0]+.1,to=HITS[HITS.length-1]+IGNITE;if(t<from||t>to)return;
 const k=smoothstep((t-from)/.2)*(1-smoothstep((t-(to-.35))/.35));
 const spots=[[-.16,.05,.12],[.1,.55,-.24],[-.16,.95,-.05],[.05,1.35,.2]];
 spots.forEach(([fx,y,fz],i)=>flame(m,add(ZOMBIE,[fx,y,fz]),[0,1,0],{width:.24*k,length:.5*k,seed:600+i*13,heat:.95,sway:.04,t}));
}

export default {
 id:'flamethrower',name:'Flamethrower',slot:'Primary',
 duration:DURATION,
 summary:'Hold to pour a cone of tumbling flame cubes out of the wand. They leave the tip small and yellow, swell and turn orange then red as they fly, and shrink into smoke right at the 6-block reach. Cubes that meet a wall or the ground splash and spread across it instead of passing through. After 4 seconds of spraying the wand overheats: it coughs, puffs smoke and stays locked for 2.5 seconds while it cools.',
 notes:'Reach: 6 blocks. The hit cone is half a block wide at your eyes and about 4 blocks wide at full reach; the flame cubes fill that same cone.\n'+
  'Damage: a hit every half second, starting at 0.5 (a quarter heart) and climbing to 2.5 (over a heart) after 3 seconds of spraying. Every hit also sets the target on fire for 2 seconds. A full 4-second spray lands '+HITS.length+' hits for about '+Math.round(SPRAY_DAMAGE)+' damage, so this 20-health zombie is left on about '+Math.round(20-SPRAY_DAMAGE)+' health (before burning) and still on fire.\n'+
  'Heat: 4 seconds of spraying takes the wand from cold to overheat. The stream gets denser as it heats (80 cubes a second at first, 150 at the end) and hotter: near overheat the cubes leave the tip white-hot and stay yellow further out. Letting go early drains the heat bar at 30% a second and starts the usual 1-second wand cooldown.\n'+
  'Overheat: the stream stops, the wand tip goes dark and smokes, and it is locked until it cools to a quarter heat, 2.5 seconds later. A short flicker of flame at the tip says it is ready again.\n'+
  'Walls: the fire cannot reach through blocks. Each cube stops just short of the first block in its path (like the game\'s line-of-sight check) and spreads over that face, climbing walls. Here the wall takes the right half of the cone and the ground catches the bottom edge.\n'+
  'Your own view (Caster eye): your stream starts small at the wand and thins out around the crosshair, so you can still see what you are burning.\n'+
  'Target view: an enemy standing a couple of blocks past the 6-block reach, off to the side. They see the caster behind a wall of fire that stops short of them.\n'+
  'Not gameplay: the sputter in the last half second before overheat is a visual warning only (damage does not drop), and the small flames on the zombie stand in for Minecraft\'s own burning effect.',
 phases:[[0,'Wand ready'],[T0,'Spraying · heat building'],[T0+3,'Full damage'],[TOVER-SPUTTER,'About to overheat'],[TOVER,'Overheat · sputters out'],[TOVER+.6,'Locked while the wand cools'],[TUNLOCK,'Ready again']],
 area:{x0:-5,x1:11,z0:-6,z1:6},
 budget:450,
 cameras:{
  orbit:{target:[3,1.1,.2],yaw:-2.3,pitch:.36,distance:8.6},
  caster:{eye:EYE_P,look:add(EYE_P,mul(AIM,8))},
  target:{eye:[8.6,EYE,-3.5],look:[0,1.3,0]},
 },
 draw(m,t,ctx){
  blocks(m,WALL.min,WALL.max,{tone:.48});
  figure(m,[0,0,0],YAW,{pose:'cast',pitch:PITCH});
  let hurt=0;for(const h of HITS)if(t>=h)hurt=Math.max(hurt,1-(t-h)/.4);
  figure(m,ZOMBIE,Math.PI,{kind:'zombie',hurt:clamp(hurt)});
  burning(m,t);
  nozzle(m,t,ctx);
  pilot(m,t);
  if(t>=T0){
   const k0=Math.max(0,Math.ceil(emitted(clamp(t-MAX_AGE-T0,0,SPRAY)))),k1=Math.floor(emitted(clamp(t-T0,0,SPRAY))+1e-9);
   for(let k=k0;k<=k1;k++)streamPuff(m,k,t,ctx);
  }
 },
};
