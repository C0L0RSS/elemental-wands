// Runtime port: FlashoverEmberRenderer draws the coal from state the entity already syncs (SETTLED, ARMED, PRIMED, age,
// the attachedPosition offset): a 9-cube lump (8 char chunks around a hot core cube) in flight with an 8-cube cooling
// trail from its lerped positions, and once armed a 6-cube heat tongue. It also needs the tick it settled and armed
// (remember client-side when the tracked flags flip) and the face it stuck to (one new tracked direction; up for a coal
// stuck to a creature). The blast needs a client effect: the server spawns EXPLOSION_EMITTER at each pop today; replace
// it with a small S2C "flashover pop" (position, face, seed) that starts a 2.6 s client ball at RADIUS 4. Particles
// that stay: the armed FIRE_EMBER mote every 12 ticks, the 18 FIRE_EMBER sparks per pop and the 4-mote disarm puff.
// Budget: about 17 cubes per coal, up to about 120 per blast (4 heart, 78 shell less those behind the surface,
// 8 crust, 18 sparks, 9 smoke); this scene peaks near 230.
import{add,sub,mul,dot,norm,clamp,lerp,smoothstep,rnd,TAU,UP}from'../engine.js';
import{cube,flame,smoke,blocks,heatBand,basis,tumble,rotate,sphereDir,figure,wandTip,lookDir,EYE}from'../kit.js';

// ---- Gameplay numbers (FlashoverRules / FlashoverEmberEntity) ----------------------------------------------
const TICK=1/20;
const SPEED=1.1,GRAVITY=.045,DRAG=.99;  // blocks per tick, launched from the eye 0.12 below eye height
const ARM=10*TICK;                        // ARM_TICKS: 0.5 s after it sticks
const POP=3*TICK;                         // POP_INTERVAL: 3 ticks between blasts
const RADIUS=4;                           // blast reach, to the nearest point of a hitbox
const THROW_GAP=20*TICK;                  // THROW_COOLDOWN: 1 s between throws

// ---- Layout: caster at the origin facing +x ------------------------------------------------------------------
const ZOMBIE=[10,0,0];                    // faces the caster; hitbox 0.6 wide, 1.95 tall
const ZBOX={x0:ZOMBIE[0]-.3,x1:ZOMBIE[0]+.3,y0:0,y1:1.95,z0:-.3,z1:.3};
const WALL={x0:3,x1:9,y0:0,y1:3,z0:-4,z1:-3}; // a stone wall to the caster's left; its face looks back at the field
const START=[0,EYE-.12,0];

// The entity's real path: tick by tick, 1.1 blocks per tick along the look, then drag 0.99 and gravity 0.045.
// The first block or so is pulled toward the wand tip so it leaves the wand, not the face.
function simulate(yaw,pitch){
 let p=START,v=mul(lookDir(yaw,pitch),SPEED);const pts=[p];
 for(let k=0;k<90;k++){
  const to=add(p,v),hit=firstHit(p,to);
  if(hit){pts.push(hit.point);return{pts,land:k+hit.f,rest:add(hit.point,mul(hit.normal,hit.lift)),normal:hit.normal,kind:hit.kind,v}}
  pts.push(to);p=to;v=sub(mul(v,DRAG),[0,GRAVITY,0]);
 }
 return{pts,land:90,rest:p,normal:UP,kind:'none',v};
}
function firstHit(a,b){
 let best=null;const take=(f,normal,kind,lift)=>{if(f>=0&&f<=1&&(!best||f<best.f))best={f,point:add(a,mul(sub(b,a),f)),normal,kind,lift}};
 // Ground (y = 0): the ember rests 0.18 out from the face it hit.
 if(a[1]>0&&b[1]<=0)take(a[1]/(a[1]-b[1]),UP,'ground',.18);
 // Wall face toward the field (z = WALL.z1).
 if(a[2]>WALL.z1&&b[2]<=WALL.z1){const f=(a[2]-WALL.z1)/(a[2]-b[2]),q=add(a,mul(sub(b,a),f));
  if(q[0]>=WALL.x0&&q[0]<=WALL.x1&&q[1]>=WALL.y0&&q[1]<=WALL.y1)take(f,[0,0,1],'wall',.18)}
 // Zombie front (x = ZBOX.x0): sticks 0.12 back along its flight, just outside the body.
 if(a[0]<ZBOX.x0&&b[0]>=ZBOX.x0){const f=(ZBOX.x0-a[0])/(b[0]-a[0]),q=add(a,mul(sub(b,a),f));
  if(q[1]>=ZBOX.y0&&q[1]<=ZBOX.y1&&q[2]>=ZBOX.z0&&q[2]<=ZBOX.z1)take(f,[-1,0,0],'zombie',.12)}
 return best;
}
/** Bisects the pitch that lands the ember where we want (measure rises with pitch). */
function aim(yaw,measure,want){
 let lo=-.7,hi=.7;
 for(let i=0;i<48;i++){const mid=(lo+hi)/2,r=simulate(yaw,mid);if(measure(r)<want)lo=mid;else hi=mid}
 const pitch=(lo+hi)/2;return{yaw,pitch,...simulate(yaw,pitch)};
}
const reachXZ=r=>r.kind==='ground'?Math.hypot(r.rest[0],r.rest[2]):r.kind==='none'?99:-99;
// Height where it struck the target; a ground landing short of the target reads as too low, past it as too high.
const heightOn=(kind,reach)=>r=>r.kind===kind?r.rest[1]:r.kind==='ground'&&Math.hypot(r.rest[0],r.rest[2])<reach?-99:99;

const T_THROW=[.3,.3+THROW_GAP,.3+2*THROW_GAP];
const T_DET=4.0;
const EMBERS=[
 {name:'ground',seed:11,...aim(Math.atan2(1.9,6.4),reachXZ,Math.hypot(6.4,1.9))},
 {name:'wall',seed:23,...aim(Math.atan2(WALL.z1,7),heightOn('wall',Math.hypot(7,WALL.z1)),1.55)},
 {name:'zombie',seed:37,...aim(0,heightOn('zombie',ZBOX.x0),1.05)},
].map((e,i)=>{
 const thrown=T_THROW[i],settled=thrown+e.land*TICK;
 // Pops follow throw order, 3 ticks apart; each blast reaches the zombie only if its hitbox is within 4 blocks.
 const near=[clamp(e.rest[0],ZBOX.x0,ZBOX.x1),clamp(e.rest[1],ZBOX.y0,ZBOX.y1),clamp(e.rest[2],ZBOX.z0,ZBOX.z1)];
 const hits=Math.hypot(...sub(near,e.rest))<=RADIUS;
 return{...e,thrown,settled,armed:settled+ARM,pop:T_DET+i*POP,hits};
});

// ---- Ember lump ----------------------------------------------------------------------------------------------
/**
 * A charred lump: eight char chunks in a loose 2x2x2 around a hot core cube, so the core shows through the cracks.
 * heat is the core's band (0..4), glow dims the crust while it arms, open spreads the chunks (wider, brighter cracks).
 */
function lump(m,p,axes,{seed=0,heat=1,glow=1,open=0,scale=1}={}){
 if(scale<=.01)return;
 cube(m,p,.24*scale*(1+open*.35),{band:heatBand(heat),seed,axes});
 const off=(.1+.045*open)*scale;
 for(let i=0;i<8;i++){
  const sx=i&1?1:-1,sy=i&2?1:-1,sz=i&4?1:-1,j=k=>(rnd(seed+i*1.7+k)-.5)*.03*scale;
  const c=add(p,add(mul(axes[0],sx*off+j(.1)),add(mul(axes[1],sy*off+j(.2)),mul(axes[2],sz*off+j(.3)))));
  // Each chunk is tipped a little on its own axis, which opens wedge-shaped cracks instead of a neat grid.
  const k=norm([rnd(seed+i*2.3)-.5,rnd(seed+i*2.9)-.5,rnd(seed+i*3.7)-.5]),tilt=.18+.12*rnd(seed+i*4.1);
  const s=.165*scale*(.88+.24*rnd(seed+i*5.3));
  cube(m,c,[s,s*(.85+.2*rnd(seed+i*6.1)),s],{band:'char',seed:seed+i+1,axes:axes.map(v=>rotate(v,k,tilt)),color:[glow,glow,glow,1]});
 }
}

/** Where a thrown ember is at time t, or null before the throw. */
function emberPos(e,t){
 const ticks=(t-e.thrown)/TICK;if(ticks<0)return null;
 if(ticks>=e.land)return e.rest;
 const i=Math.floor(ticks),f=ticks-i,a=e.pts[i],b=i+1<e.pts.length-1?e.pts[i+1]:e.pts[e.pts.length-1];
 const segF=i+1>=e.pts.length-1?clamp(f/Math.max(1e-3,e.land-i)):f;
 let q=add(a,mul(sub(b,a),segF));
 // Visual hand-off from the wand tip to the entity path over the first 3 ticks.
 const tip=wandTip([0,0,0],e.yaw,e.pitch),blend=1-smoothstep(ticks/3);
 q=add(q,mul(sub(tip,START),blend));
 // Ease onto the rest point (0.18 out from the face) as it lands.
 const settle=smoothstep((ticks-(e.land-1))/1);
 return add(q,mul(sub(e.rest,e.pts[e.pts.length-1]),settle));
}

/** Breath: the existing renderer's pulse, sin(age * 0.12) per tick, so about 2.6 s per breath. */
const breath=(t,seed)=>.5+.5*Math.sin((t/TICK)*.12+seed);

function drawEmber(m,e,t,ctx){
 const p=emberPos(e,t);if(!p||t>=e.pop)return;
 if(t<e.settled){
  // In flight: dim, spinning, with a short trail that shrinks into the lump (nothing pops off).
  lump(m,p,tumble(e.seed,t,11),{seed:e.seed,heat:1.2,glow:.55,open:.1,scale:.95});
  // Eight overlapping trail cubes (it covers about a block per tick), shrinking and cooling behind it.
  for(let j=1;j<=8;j++){
   const back=t-j*.008;if(back<e.thrown)break;
   const q=emberPos(e,back);cube(m,q,.2*(1-j/9.5),{band:heatBand(2.4-j*.25),seed:e.seed+40+j,axes:tumble(e.seed+j,t,8)});
  }
  return;
 }
 const axes=basis(e.normal,rnd(e.seed)*TAU);
 const since=t-e.settled,armedFor=t-e.armed,primed=t>=T_DET;
 if(armedFor<0){
  // Arming (0.5 s): dark crust, a dull core that warms as the half second runs out.
  const w=since/ARM;
  lump(m,p,axes,{seed:e.seed,heat:.6+.8*w,glow:.42+.1*w,open:.05});
  if(ctx.embers&&since<.25)thud(m,p,e.normal,since,e.seed);
  return;
 }
 const b=breath(t,e.seed),flare=Math.exp(-armedFor*9)*(armedFor<.6?1:0);
 let heat=2.5+1.1*b+1.5*flare,glow=.86+.14*b,open=.3+.35*b+.9*flare;
 if(primed){
  // Primed: the existing .7 + .3 sin(1.5 per tick) flicker, and the chunks start to part before the pop.
  const k=(t-T_DET)/Math.max(POP,e.pop-T_DET),fl=.7+.3*Math.sin((t/TICK)*1.5);
  heat=3.2+fl;glow=fl;open=.8+1.4*k;
 }
 lump(m,p,axes,{seed:e.seed,heat,glow,open});
 // Heat breathing off the top: a small tongue that grows and fades with each breath, always upright.
 const top=add(p,add(mul(e.normal,.06),[0,.13,0]));
 flame(m,top,[e.normal[0]*.25,1,e.normal[2]*.25],{width:.12+.03*b,length:.16+.26*b+.3*flare,seed:e.seed+5,heat:.45+.2*b,sway:.04,count:3,speed:.7,t});
 if(ctx.embers){
  // The armed FIRE_EMBER mote, one every 12 ticks.
  const per=12*TICK,u=((armedFor/per)%1+1)%1,n=Math.floor(armedFor/per);
  cube(m,add(top,[(rnd(e.seed+n)-.5)*.1,.15+u*.7,(rnd(e.seed+n+.5)-.5)*.1]),.06*(1-u),{band:heatBand(3-u*2),seed:e.seed+n,axes:tumble(e.seed+n,t,5)});
 }
}

/** A few spark chips kicked off where the ember lands. */
function thud(m,p,normal,age,seed){
 for(let i=0;i<5;i++){
  const d=norm(add(normal,[(rnd(seed+i*1.1)-.5)*1.6,.4,(rnd(seed+i*1.9)-.5)*1.6])),l=age/.25;
  cube(m,add(p,add(mul(d,.9*l),[0,-1.2*l*l,0])),.07*(1-l),{band:heatBand(3-l*2),seed:seed+i,axes:tumble(seed+i,age,6)});
 }
}

// ---- Blast: a ball of block fire at the real 4-block reach ---------------------------------------------------
const SWELL=.18;  // seconds to full size (the damage lands on the pop tick; the ball just shows its reach)
const SHELL=78;

function blast(m,e,age,ctx){
 if(age<0||age>2.6)return;
 const c=e.rest,seed=e.seed*13,surface=e.kind!=='zombie',k=clamp(age/SWELL),grow=1-Math.pow(1-k,3),after=Math.max(0,age-SWELL);
 // The heart: big hot cubes that bloom first and outlast the shell.
 for(let j=0;j<4;j++){
  const life=clamp(after/(.42+.1*j));if(life>=1)continue;
  const d=sphereDir(j,4),dir=surface&&dot(d,e.normal)<0?sub(d,mul(e.normal,2*dot(d,e.normal))):d;
  const s=(1.9-.2*j)*Math.min(1,age/.06+.25)*Math.pow(1-life,.75);
  // White-hot only for the first couple of ticks, then yellow and cooling.
  cube(m,add(add(c,mul(dir,.95*grow)),[0,.5*life,0]),s,{band:heatBand(3.3-j*.3+.9*(1-k)-life*3.2),seed:seed+j,axes:tumble(seed+j,age,1.4)});
 }
 // The shell: cubes thrown out to the 4-block edge, then cooling and falling back inward as they shrink away.
 for(let i=0;i<SHELL;i++){
  const d=sphereDir(i,SHELL);
  if(surface&&dot(d,e.normal)<-.08)continue;   // stuck to a block: the blast opens away from it
  // Most cubes ride the rim, their outer faces right at the 4-block reach; the rest are bigger, hotter filler inside.
  const rim=rnd(seed+i*2.1)<.7,s0=rim?.68+.42*rnd(seed+i*3.7):1.05+.5*rnd(seed+i*3.7);
  const reach=rim?RADIUS-s0*.45:RADIUS*(.38+.4*rnd(seed+i*4.3));
  const life=clamp((after-.08*rnd(seed+i*5.9))/(.46+.36*rnd(seed+i*6.7)));if(life>=1)continue;
  const pos=add(c,add(mul(d,reach*grow*(1-.55*smoothstep(life))),[0,.7*life*life,0]));
  if(pos[1]<-s0*.35)continue;                // below the ground: hidden anyway
  const s=s0*Math.min(1,k*1.5+.1)*Math.pow(1-life,.85);
  // Yellow inside, orange and deep at the rim (white-hot stays in the heart), then it cools through the bands as it falls in.
  const heat=(rim?2.1:2.6)+.9*rnd(seed+i*7.3)+.5*(1-k)-life*3.4;
  cube(m,pos,s,{band:heatBand(heat),seed:seed+i,axes:tumble(seed+i,age,2.2)});
 }
 // The crust: the eight char chunks blown off the lump.
 for(let i=0;i<8;i++){
  const l=age/.5;if(l>=1)break;
  const d=norm(add([i&1?1:-1,i&2?1:-1,i&4?1:-1],surface?mul(e.normal,1.2):[0,.4,0]));
  const q=add(c,add(mul(d,3.2*(1-Math.exp(-age*7))),[0,-1.5*age*age,0]));
  cube(m,q,.2*(1-l),{band:'char',seed:seed+60+i,axes:tumble(seed+60+i,age,9)});
 }
 if(!ctx.embers)return;
 // Smoke rising out of the middle as the fire falls in on itself (removed with reduced particles).
 const heart=add(c,surface?mul(e.normal,.9):[0,.3,0]);
 for(let i=0;i<9;i++){
  const start=.3+.32*rnd(seed+i*8.3),a=rnd(seed+i*9.1)*TAU,r=.3+1.3*rnd(seed+i*9.4);
  const q=add(heart,[Math.cos(a)*r,(rnd(seed+i*4.4)-.3)*1.2,Math.sin(a)*r]);
  smoke(m,[q[0],Math.max(q[1],.5),q[2]],age-start,{size:.5+.3*rnd(seed+i*9.7),seed:seed+200+i,rise:2.4+1.4*rnd(seed+i*3.1),duration:1.4+.5*rnd(seed+i*2.7)});
 }
 // The 18 FIRE_EMBER sparks per pop, as tiny cubes flung out and falling.
 for(let i=0;i<18;i++){
  const l=age/(.5+.3*rnd(seed+i*1.3));if(l>=1)continue;
  const d=norm(add(sphereDir(i,18),[0,.5,0])),q=add(c,add(mul(d,(2+2.2*rnd(seed+i*2.2))*(1-Math.pow(1-l,2))),[0,-1.4*l*l,0]));
  cube(m,q,.09*(1-l),{band:heatBand(3.8-l*3),seed:seed+300+i,axes:tumble(seed+300+i,age,7)});
 }
}

// ---- Caster aim --------------------------------------------------------------------------------------------------
const mid=EMBERS.reduce((s,e)=>add(s,mul(e.rest,1/3)),[0,0,0]);
const DET_AIM={yaw:Math.atan2(mid[2],mid[0]),pitch:Math.atan2(mid[1]-EYE,Math.hypot(mid[0],mid[2]))};
const KEYS=[[0,{yaw:EMBERS[0].yaw-.12,pitch:EMBERS[0].pitch+.05}],[T_THROW[0]-.04,EMBERS[0]],[T_THROW[0]+.45,EMBERS[0]],
 [T_THROW[1]-.04,EMBERS[1]],[T_THROW[1]+.45,EMBERS[1]],[T_THROW[2]-.04,EMBERS[2]],[T_THROW[2]+.6,EMBERS[2]],
 [T_DET-.12,DET_AIM],[99,DET_AIM]];
function aimAt(t){
 for(let i=0;i<KEYS.length-1;i++){const[a,A]=KEYS[i],[b,B]=KEYS[i+1];
  if(t<b){const u=smoothstep((t-a)/(b-a));return{yaw:lerp(A.yaw,B.yaw,u),pitch:lerp(A.pitch,B.pitch,u)}}}
 return DET_AIM;
}
const casterCam=t=>{const a=aimAt(t),eye=[0,EYE,0];return{eye,look:add(eye,mul(lookDir(a.yaw,a.pitch),10))}};

/** Minecraft's red hurt tint holds for 10 ticks after each hit, and stays on while the body falls. */
function zombieHurt(t){let h=0;for(const e of EMBERS)if(e.hits&&t>=e.pop&&t<e.pop+.5)h=1;return t>=DEATH?1:h}

// A fresh-level zombie (20 health, 2 armor) keeps a sliver after 12 + 8 and drops on the third pop.
const DEATH=EMBERS[2].pop;
/** Death as Minecraft draws it: tips sideways about its own facing, sqrt-eased to 90° over 12.5 ticks; gone at 20 ticks. */
const deathTip=t=>t<DEATH?0:Math.min(1,Math.sqrt(Math.max(0,(t-DEATH)/TICK-1)/20*1.6))*Math.PI/2;
const GONE=DEATH+20*TICK;
/** A mesh wrapper that turns every box about a pivot, so kit.figure can lie down without touching the kit. */
function turned(m,pivot,axis,angle){
 const R=v=>rotate(v,axis,angle);
 return{time:m.time,box:(c,h,axes,key,o)=>m.box(add(pivot,R(sub(c,pivot))),h,axes.map(R),key,o),quad:(...a)=>m.quad(...a)};
}
function drawZombie(m,t,ctx){
 if(t<GONE){figure(turned(m,ZOMBIE,[-1,0,0],-deathTip(t)),ZOMBIE,Math.PI,{kind:'zombie',hurt:zombieHurt(t)});return}
 // The vanilla death poof: a few pale smoke cubes where the body was (removed with reduced particles).
 if(!ctx.embers)return;
 for(let i=0;i<7;i++){const a=i*2.4,r=.35+.25*rnd(i+.3);
  smoke(m,add(ZOMBIE,[Math.sin(a)*r*.6,.3+.25*rnd(i+.7),Math.cos(a)*r+.9]),t-GONE-.04*i,{size:.32,seed:500+i,rise:.8,duration:.7})}
}

export default {
 id:'flashover',name:'Flashover',slot:'Secondary',
 duration:6.6,
 summary:'Throw up to three hot coals that stick to the ground, walls or enemies, give each half a second to arm, then press Alternate to set them all off. Each coal swells into a ball of block fire exactly as big as its real 4-block blast, pop, pop, pop, three ticks apart, then the fire cools, falls in on itself and turns to smoke.',
 notes:'What you are watching: the caster throws three coals a second apart (onto the grass, onto the stone wall, onto the zombie) and then sets them all off.\n\n'
  +'The coal is a lump of charred rock: eight dark chunks around a glowing core that shows through the cracks. In flight it tumbles with a short, cooling trail. It is fast, 1.1 blocks per tick (about 22 blocks a second), and gravity pulls it down, so all three of these land in under half a second. It sticks where it hits: just off the face of a block, or just in front of a body (and rides along if that creature walks off).\n\n'
  +'Arming takes half a second (10 ticks) after it sticks. Until then the coal is dark and dull. When it arms, the cracks flare open and a small flame starts breathing off the top, swelling and fading about every 2.6 seconds, with a spark every 12 ticks. That breathing flame is the "this one is live" tell. Any player who is not the caster\'s ally can hit a coal to break it without setting it off. Target view shows what the zombie sees from its own eyes.\n\n'
  +'Detonate (the Alternate key) sets off every armed coal within 32 blocks, in the order they were thrown, 3 ticks (0.15 s) apart. Each ball is drawn at the real reach: 4 blocks to the nearest edge of a hitbox. A coal on a block blows outward, away from it, and the block shields anything behind it.\n\n'
  +'Blasts stack on one target: 12, then 8 more, then 4 more, 24 in all (12 hearts), and more at higher Fire levels. One blast wounds a creeper and two kill it. The zombie stands inside all three. At a fresh Fire level its 2 armor points leave it a sliver of health after the second pop, so it flashes red twice and drops on the third. At higher levels two pops are enough, and the coal stuck to it is lost with the body before it can go off.\n\n'
  +'Each slot comes back 6 seconds after its coal goes off, or 2 seconds after a coal is broken or lost. A coal nobody sets off burns out after 30 seconds. Turning off Embers & smoke removes the sparks, smoke and death poof but keeps the coals, the trail and the fire balls.',
 phases:[[0,'Aim'],[T_THROW[0],'Throw 1: ground'],[EMBERS[0].settled,'Sticks, dim while arming (0.5 s)'],[EMBERS[0].armed,'Armed: cracks open, heat breathes'],
  [T_THROW[1],'Throw 2: wall'],[T_THROW[2],'Throw 3: zombie'],[EMBERS[2].armed,'All three armed, breathing'],[T_DET,'Detonate: pops 3 ticks apart'],
  [DEATH,'Third pop: the zombie drops'],[T_DET+.6,'Collapse into smoke']],
 area:{x0:-5,x1:16,z0:-9,z1:9},
 budget:500,
 cameras:{
  orbit:{target:[6.3,1.5,-.6],yaw:-.4,pitch:.24,distance:10.5},
  caster:casterCam,
  // The zombie's eyes, just in front of its face, looking back at the caster: the ground and wall coals are in view.
  target:{eye:[ZBOX.x0-.05,EYE,0],look:[0,1.1,-.9]},
 },
 draw(m,t,ctx){
  blocks(m,[WALL.x0,WALL.y0,WALL.z0],[WALL.x1,WALL.y1,WALL.z1],{tone:.48});
  const a=aimAt(t);
  figure(m,[0,0,0],a.yaw,{pose:'cast',pitch:a.pitch});
  drawZombie(m,t,ctx);
  // Detonation tell at the wand: a short flare on the button press.
  const da=t-T_DET;
  if(da>=0&&da<.25){const tip=wandTip([0,0,0],a.yaw,a.pitch);cube(m,tip,.2*Math.sin(da/.25*Math.PI),{band:heatBand(4-da*8),seed:7,axes:tumble(7,t,6)})}
  for(const e of EMBERS){drawEmber(m,e,t,ctx);blast(m,e,t-e.pop,ctx)}
 },
};
export{EMBERS as __embers};
