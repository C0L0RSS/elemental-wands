// Runtime port: InfernoWaveEntity (server projectile, 1.5 blocks/tick from the eye, discarded past 15 blocks or on the
// first block hit) drawn by a block-fire FireWaveRenderer. The head and swept-back tongues live in the entity's frame and
// need only age + tickDelta and velocity, plus the wand-tip offset at launch if the head should bend out of the wand onto
// the eye line over its first 3 blocks (SpellCastVisuals.Wake does this for particles today but lives only on the server,
// so the offset would have to be synced once at spawn). Shed tail pieces stay in world space after the head leaves: one
// tumbling, shrinking, cooling cube particle each, spawned where emitInterpolatedWake spawns FIRE_FLAME_RIBBON today.
// Ignition, hit and wall bursts replace FIRE_IMPACT_RING + FIRE_FLAME_RIBBON at the existing server hooks (castPrimary,
// onEntityHit, onBlockHit), each as one "block-fire burst" particle event; max-travel discard would need the same event
// (it shows nothing today). Particles that stay as accents: FIRE_EMBER sparks, vanilla SMOKE and LAVA. Near-eye
// clearance (SpellViewClearance) applies to any first-person viewer, the target included. Cube budget 400 (peak ~290,
// just after the wall break).
import{add,sub,mul,norm,clamp,lerp,smoothstep,rnd,noise,cross,dot,TAU,UP}from'../engine.js';
import{cube,flame,burst,smoke,sparkBurst,blocks,figure,wandTip,heatBand,tumble,rotate,basis,sphereDir,AXES,EYE}from'../kit.js';

// ---- Gameplay numbers (InfernoWaveEntity, FireWaveRenderer, SpellCastVisuals, FireAbilityHandler) ---------------------
const SPEED=30;           // PROJECTILE_SPEED 1.5 blocks per tick
const MAX_TRAVEL=15;      // MAX_TRAVEL_DISTANCE
const GROW=.1;            // renderer grows the stream over 2 ticks
const WAKE_BLEND=3;       // the wake leaves the wand tip and joins the eye line over 3 blocks
const BURST_REACH=.45;    // cast flashes bloom this far past the wand tip
const HURT=.5;            // vanilla red hurt flash, 10 ticks

// ---- Staging ---------------------------------------------------------------------------------------------------------
const T0=.3;              // cast moment in this loop
const F=[1,0,0],R=[0,0,1];
const EYE_P=[0,EYE,0];
const TIP=wandTip([0,0,0],0),TIP_OFF=sub(TIP,EYE_P);
const IGNITION=add(TIP,mul(F,BURST_REACH));
const WALL_X=12;          // wall face; 12 blocks from the eye, inside the 15-block reach
const HEAD_LEAD=.2,NOSE=.55;
const ZOMBIES=[[5,0,.15],[8.5,0,-.12]];
// Visual contact when the head centre reaches the zombie's front face (hitbox half-width 0.3).
const HIT_T=ZOMBIES.map(z=>T0+(z[0]-.3-HEAD_LEAD)/SPEED);
const S_END=WALL_X-HEAD_LEAD-NOSE,T_END=T0+S_END/SPEED;
const SHED_DT=.0105;      // one shed sample per ~0.3 blocks of travel

/** Where the entity's centre is after s blocks, bent out of the wand tip like SpellCastVisuals.Wake.at. */
function pathPoint(s){s=Math.max(0,s);return add(add(EYE_P,mul(F,s)),mul(TIP_OFF,1-smoothstep(s/WAKE_BLEND)))}
const headAt=s=>pathPoint(s+HEAD_LEAD);
const tangentAt=s=>norm(sub(headAt(s+.05),headAt(s-.05)));
const growAt=t=>smoothstep((t-T0)/GROW);

// ---- Local helpers (candidates for kit.js) ---------------------------------------------------------------------------

/**
 * burst() with a direction: pieces lean toward dir (pierce follow-through), or with normal set they fold into a
 * hemisphere off a face and flatten along it (flat 0..1). flash scales the hot centre cluster.
 */
function splash(m,c,age,{dir=null,bias=.6,normal=null,flat=.5,radius=1,count=16,size=.32,duration=.5,up=.4,heat=1,seed=0,flash=1}={}){
 if(age<0)return;const life=age/duration;if(life>2.2)return;
 if(flash&&life<.42){
  const k=life/.42,s=size*2.2*flash*Math.pow(Math.sin(k*Math.PI),.6);
  cube(m,c,s,{band:heatBand(heat*4-k*1.5),seed,axes:tumble(seed,age,3)});
  cube(m,c,s*.84,{band:heatBand(heat*4-k*1.5-1),seed:seed+1,axes:tumble(seed+1,age,-3)});
 }
 for(let i=0;i<count;i++){
  const delay=rnd(seed+i*1.7)*.1,l=clamp((life-delay)/(1-delay),0,1);if(l<=0||l>=1)continue;
  let d=sphereDir(i,count);
  if(normal){const k=dot(d,normal);if(k<0)d=sub(d,mul(normal,2*k));d=sub(d,mul(normal,dot(d,normal)*flat))}
  if(dir)d=add(d,mul(dir,bias));
  d=norm(add(d,[0,up,0]));
  const r=radius*(1-Math.pow(1-l,2.2))*(.55+.45*rnd(seed+i*3.3));
  const p=add(add(c,mul(d,r)),[0,.45*l*l,0]);
  const s=size*(.6+.6*rnd(seed+i*4.9))*Math.min(1,l*8)*Math.pow(1-l,1.1);
  cube(m,p,s,{band:heatBand(heat*4-l*4.2+rnd(seed+i*2.3)*.6),seed:seed+i,axes:tumble(seed+i,age,4)});
 }
}

// ---- The fireball ------------------------------------------------------------------------------------------------------

/** Dense hot head: a bright nose and core, then two flickering shells that cool from the front to the back. */
function head(m,c,f,t,g){
 const r=norm(cross(f,UP)),u=cross(r,f);
 const toWorld=d=>add(add(mul(f,d[1]),mul(u,d[0])),mul(r,d[2]));
 // A slow roll about the side axis keeps the shell churning; heat is read after the roll so the nose stays hottest.
 const roll=t*5.5+.7,rolled=d=>rotate(d,r,roll);
 const flick=k=>.84+.3*noise(t*7.3+k*3.71);
 const coreAxes=AXES.map(v=>rotate(v,r,roll*.6));
 cube(m,add(c,mul(f,.1*g)),.7*g*flick(1),{band:'core',seed:1,axes:coreAxes});
 cube(m,add(c,mul(f,.42*g)),.44*g*flick(2),{band:'core',seed:2,axes:tumble(2,t,2.5)});
 for(let i=0;i<7;i++){
  const d=rolled(toWorld(sphereDir(i,7))),front=dot(d,f);
  const s=.42*g*flick(i+10)*(1-.22*Math.max(0,-front));
  cube(m,add(c,mul(d,.33*g)),s,{band:heatBand(3.05+.95*front),seed:10+i,axes:tumble(10+i,t,2)});
 }
 for(let i=0;i<10;i++){
  const d=rolled(toWorld(sphereDir(i,10))),front=dot(d,f);
  const s=.3*g*flick(i+30)*(1-.3*Math.max(0,-front));
  cube(m,add(c,mul(d,(.52+.06*front)*g)),s,{band:heatBand(2.25+1.25*front),seed:30+i,axes:tumble(30+i,t,3)});
 }
}

// Swept-back tongues: [angle around the flight axis, root offset, outward splay, length, width, heat]. The first is the
// long central tail; four chunky ones sit around it, nearly parallel so the tail tapers like a comet. The upper one
// is longest, since fire still rises.
const TONGUES=[[0,0,0,2.7,.6,1.05],
 [.15,.23,.04,2,.44,.95],[1.75,.26,.06,1.6,.42,.9],[3.3,.24,.05,1.25,.4,.85],[4.75,.26,.06,1.7,.42,.9]];
// Tongues only reach back over path the fireball has already covered, so they unspool from the wand and never
// trail back into the caster's face.
const TAIL_START=1.8;

/**
 * A swept-back tongue rooted inside the head: like kit.flame, pieces stream from a hidden root along dir, cool and
 * shrink to nothing at the tip, and the tongue flickers by length. Unlike flame, each piece starts lined up with the
 * tongue and tumbles a little more the further back it gets, so the tail churns instead of stacking into a slab.
 */
function tongue(m,root,dir,{width=.5,length=1.5,seed=0,heat=1,t=m.time,speed=2.6,count=0}={}){
 if(length<=.01||width<=.01)return;
 const d=norm(dir),roll=rnd(seed+.9)*TAU,[bx,,bz]=basis(d,roll),top=heat*3;
 const L=length*(.82+.3*noise(t*4.3+seed*7.1)),n=count||clamp(Math.round(L/width*2),3,7);
 const period=clamp(.32+L*.2,.32,1.1)/speed;
 for(let i=0;i<n;i++){
  const phase=t/period+i/n+rnd(seed+i*1.31)*.35/n,loop=Math.floor(phase),u=phase-loop,k=seed+i*4.1+loop*17.3;
  const w=width*.8*Math.pow(1-u,.85)*(.85+.3*rnd(k));
  if(w<.012)continue;
  const side=add(mul(bx,(rnd(k+1.7)-.5)*width*.5*u),mul(bz,(rnd(k+2.9)-.5)*width*.5*u));
  const spinAxis=norm([rnd(k+4.1)-.5,rnd(k+4.3)-.5,rnd(k+4.7)-.5]),spin=(1.2+1.4*rnd(k+5.3))*u;
  const axes=basis(d,roll+(rnd(k+3.3)-.5)*1.2).map(v=>rotate(v,spinAxis,spin));
  cube(m,add(add(root,mul(d,L*Math.pow(u,.85))),side),[w,w*1.12,w],{band:heatBand(top-u*2.7),seed:seed+i,axes});
 }
}

function tongues(m,c,f,t,g,s){
 const r=norm(cross(f,UP)),u=cross(r,f),room=Math.max(0,s+HEAD_LEAD-TAIL_START);
 TONGUES.forEach(([a,off,splay,L,w,heat],i)=>{
  const radial=add(mul(u,Math.cos(a)),mul(r,Math.sin(a)));
  const root=add(add(c,mul(f,-.12*g)),mul(radial,off*g));
  const dir=norm(add(add(mul(f,-1),mul(radial,splay)),[0,.03,0]));
  tongue(m,root,dir,{width:w*g,length:Math.min(L,room)*g,seed:200+i*13,heat,t});
 });
}

/** Pieces that peel off the tongues, stay behind in the world, drift a little, rise, cool and shrink to nothing. */
function trail(m,t,embers){
 const n=Math.floor((T_END-T0)/SHED_DT);
 for(let i=0;i<n;i++){
  const tb=T0+.035+i*SHED_DT;if(tb>T_END||t<tb)continue;
  const sb=SPEED*(tb-T0),g=growAt(tb),age=t-tb;
  for(let k=0;k<2;k++){
   const seed=500+i*7.31+k*3.17,life=.38+.3*rnd(seed);const l=age/life;if(l>=1)continue;
   const back=.5+(k?1.9:1.1)*rnd(seed+1),a=rnd(seed+2)*TAU,radial=add(mul(UP,Math.cos(a)),mul(R,Math.sin(a)));
   const along=sb+HEAD_LEAD-back*g;if(along<TAIL_START)continue;
   const birth=add(pathPoint(along),mul(radial,(.08+(.22+.16*back)*rnd(seed+3))*g));
   // Momentum carries a piece forward a little (about half a block) before it hangs and rises.
   const drift=mul(F,10*.05*(1-Math.exp(-age/.05))),out=mul(radial,.3*l),rise=[0,.15*l+.45*l*l,0];
   // Pieces shed in the first few blocks stay small, so the caster's own view isn't filled with them.
   const near=.4+.6*smoothstep((along-TAIL_START)/3);
   const s=(k?.24:.34)*(.75+.5*rnd(seed+4))*g*near*Math.pow(1-l,1.15);
   cube(m,add(add(birth,drift),add(out,rise)),s,{band:heatBand(3.2-k*.5-l*3.4+rnd(seed+5)*.4),seed,axes:tumble(seed,age,5)});
  }
  if(!embers)continue;
  if(i%2===0)sparkBurst(m,pathPoint(sb-.6),age,{count:1,speed:.9,up:.5,g:-2,life:.55,seed:900+i,size:.06});
 }
}

// ---- The wall break ----------------------------------------------------------------------------------------------------
const FACE=[WALL_X-.15,EYE,0],N=[-1,0,0];

/** The tongues' pieces carry on into the wall, slow against it and fan out across the face. */
function crumple(m,age){
 if(age<0)return;
 for(let i=0;i<18;i++){
  const seed=1500+i*5.3,life=.32+.2*rnd(seed),l=age/life;if(l>=1)continue;
  const back=.2+2.4*rnd(seed+1),a=rnd(seed+2)*TAU,radial=add(mul(UP,Math.cos(a)),mul(R,Math.sin(a)));
  const s0=.36*(1-back/3.2)*(.7+.5*rnd(seed+3));
  const p0=add(headAt(S_END-back),mul(radial,(.1+.3*back)*rnd(seed+4)));
  const x=Math.min(p0[0]+20*.07*(1-Math.exp(-age/.07)),WALL_X-s0*.5-.02);
  const spread=1-Math.pow(1-l,2);
  const p=[x,p0[1]+radial[1]*.9*spread+.4*l*l,p0[2]+radial[2]*1.1*spread];
  cube(m,p,s0*Math.pow(1-l,1.1),{band:heatBand(3.3-l*3.4),seed,axes:tumble(seed,age,5)});
 }
}

/** The head squashes flat on the stone: a hot disc that spreads across the face, cools and shrinks away. */
function pancake(m,age){
 if(age<0||age>.4)return;
 const k=smoothstep(age/.05)*(1-smoothstep((age-.04)/.2));
 if(k>.02){
  const s=1.15*k;
  cube(m,[WALL_X-.16,EYE,0],[.3,s,s*1.1],{band:heatBand(4-age*6),seed:1601,axes:yawSpin(age*.6)});
  cube(m,[WALL_X-.26,EYE,0],[.24,s*.8,s*.85],{band:'core',seed:1602,axes:yawSpin(-age*.8+.4)});
 }
 for(let i=0;i<14;i++){
  const seed=1610+i*4.3,a=(i+.4*rnd(seed))/14*TAU,life=.24+.12*rnd(seed+1),l=age/life;if(l>=1)continue;
  const r=(.35+1.5*(1-Math.pow(1-l,2.4)))*(.8+.35*rnd(seed+2)),sz=(.42+.18*rnd(seed+3))*Math.min(1,l*10)*Math.pow(1-l,1.2);
  const p=[WALL_X-sz*.55-.03,EYE+Math.sin(a)*r*.85+.3*l*l,Math.cos(a)*r];
  cube(m,p,[sz*.75,sz,sz],{band:heatBand(3.8-l*3.2),seed,axes:yawSpin(a+l*1.4)});
 }
}
/** Axes with the cube spun in the wall plane (around the wall normal, x). */
function yawSpin(a){const c=Math.cos(a),s=Math.sin(a);return[[1,0,0],[0,c,s],[0,-s,c]]}

/** Tongues that flare up the face and die down by shrinking. */
function wallFlames(m,age){
 if(age<0)return;
 for(let i=0;i<8;i++){
  const seed=1700+i*9.1,a=i/8*TAU+rnd(seed)*.6,r=.35+.75*rnd(seed+1);
  const env=smoothstep(age/.08)*(1-smoothstep((age-.15-.2*rnd(seed+2))/(.5+.3*rnd(seed+3))));if(env<=.02)continue;
  const root=[WALL_X-.2,EYE+Math.sin(a)*r*.8-.3,Math.cos(a)*r*1.2];
  flame(m,root,[-.3,1,Math.cos(a)*.15],{width:.46*(.6+.4*env),length:(1.1+.8*rnd(seed+4))*env,seed:seed|0,heat:.95,sway:.04,t:age});
 }
}

/** Embers stuck to the stone that glow and shrink away. */
function scorch(m,age){
 if(age<0)return;
 for(let i=0;i<10;i++){
  const seed=1900+i*3.7,a=rnd(seed)*TAU,r=1.15*Math.sqrt(rnd(seed+1));
  const k=1-smoothstep((age-.25-.6*rnd(seed+2))/(.7+.4*rnd(seed+3)));if(k<=0)continue;
  const s=(.14+.12*rnd(seed+4))*k*Math.min(1,age/.05);
  cube(m,[WALL_X-.03,EYE+Math.sin(a)*r*.8,Math.cos(a)*r],[.06,s,s],{band:'ember',seed});
 }
}

export default {
 id:'inferno',name:'Inferno Wave',slot:'Primary',
 duration:2.4,
 summary:'A rolling fireball of block fire leaves your wand and crosses the 12 blocks to the wall in under half a second. It burns straight through both zombies, throwing a small burst on each, sheds cooling pieces along its path, and breaks apart in a bigger burst on the wall.',
 notes:'The real numbers: it flies 1.5 blocks a tick (30 blocks a second) straight along your aim from eye height, and is gone after 15 blocks (half a second) or the first block it touches. It pierces: each enemy it passes through is hit once for 6 damage (3 hearts) and set on fire for 3 seconds. You can cast again after 1 second.\n'
  +'The fireball: a white-hot core and yellow nose about 1.4 blocks across, rolling as it flies, with five chunky flame tongues swept back behind it (the longest about 2.7 blocks), roughly the same size as today\'s stream (1.7 wide, 3.6 long). The tongues cool from yellow to deep orange and their pieces tumble and shrink to nothing at the tips. It grows to full size over its first 2 ticks and bends out of the wand tip onto your aim over the first 3 blocks, like today\'s trail.\n'
  +'The trail: pieces peel off the tail and stay behind, carried about half a block forward, then rising a little, cooling and shrinking away within about half a second, so the whole path glows briefly. Pieces near you start smaller, so your own view stays clear.\n'
  +'The bursts: a small one just past the wand tip at the cast (about a quarter of a second). Each hit throws a small burst on the target, flung forward the way the fireball is going. At the wall the fireball squashes flat into a hot disc 3 to 4 blocks across, a bigger burst splashes back off the stone, flames lick up the face for most of a second, and embers stuck to the wall fade over about a second and a half. Running out of range in open air would use the same break without the wall.\n'
  +'From the ground: the target camera is the first zombie\'s eyes, 5 blocks out. The flash at the wand and the ball coming head-on are the only warning; the hit lands under a quarter of a second (3 to 4 ticks) after the cast. Cubes that reach the viewer\'s eye shrink away, as they do in first person in game.\n'
  +'Worth knowing: the real hit is just your aim line, widened by up to a third of a block around each enemy, so the fireball looks a bit wider than what it hits. At eye height over flat ground it leaves no floor fire (aimed at the ground, its 2-second fire blocks are separate and not shown here).',
 phases:[[0,'Aiming'],[T0,'Cast: ignition at the wand'],[T0+.035,'In flight, 30 blocks a second'],[HIT_T[0],'Burns through the first zombie'],
  [HIT_T[1],'Burns through the second'],[T_END,'Breaks apart on the wall'],[T_END+.45,'Burning out']],
 area:{x0:-5,x1:17,z0:-7,z1:7},
 budget:400,
 cameras:{
  orbit:{target:[6,1.3,0],yaw:-.3,pitch:.18,distance:8},
  caster:{eye:EYE_P,look:[10,1.45,0]},
  target:{eye:[ZOMBIES[0][0],EYE,ZOMBIES[0][2]],look:[0,1.45,0]},
 },
 draw(m,t,ctx){
  blocks(m,[WALL_X,0,-3],[WALL_X+1,3,3],{tone:.5,seed:1});
  figure(m,[0,0,0],0,{pose:'cast'});
  ZOMBIES.forEach((z,i)=>{
   // The target camera stands in the first zombie's place, so it is not drawn there.
   if(ctx.cam==='target'&&i===0)return;
   const since=t-HIT_T[i];
   figure(m,z,Math.PI,{kind:'zombie',hurt:since>=0&&since<HURT?1-since/HURT:0});
  });

  // Ignition just past the wand tip.
  burst(m,IGNITION,t-T0,{radius:.5,count:8,seed:7,duration:.24,size:.16,up:.25,smoke:false});
  if(ctx.embers)sparkBurst(m,IGNITION,t-T0,{count:5,speed:1.2,up:1,g:4,life:.45,seed:40,dir:F,bias:1.2,size:.07});

  // The fireball in flight.
  if(t>=T0&&t<T_END){
   const s=SPEED*(t-T0),g=growAt(t),c=headAt(s),f=tangentAt(s);
   tongues(m,c,f,t,g,s);
   head(m,c,f,t,g);
  }
  trail(m,t,ctx.embers);

  // A forward-thrown burst on each zombie it burns through.
  ZOMBIES.forEach((z,i)=>{
   const age=t-HIT_T[i],c=[z[0],1.23,z[2]];
   splash(m,c,age,{dir:F,bias:.7,radius:1,count:16,size:.32,duration:.5,up:.45,seed:60+i*40,flash:.9});
   if(ctx.embers){sparkBurst(m,c,age,{count:8,speed:2.2,up:1.8,g:6,life:.6,seed:80+i*40,dir:F,bias:1.4,size:.07});smoke(m,add(c,[.2,.5,0]),age-.25,{size:.3,seed:95+i*40,rise:1,duration:1.1})}
  });

  // The wall break.
  const age=t-T_END;
  splash(m,FACE,age,{normal:N,flat:.55,radius:2.2,count:34,size:.5,duration:.75,up:.3,seed:300,flash:1.5});
  pancake(m,age);
  crumple(m,age);
  wallFlames(m,age);
  scorch(m,age);
  if(ctx.embers){
   sparkBurst(m,FACE,age,{count:14,speed:2.6,up:2.4,g:6,life:.8,seed:340,dir:N,bias:1.1,size:.07});
   for(let i=0;i<5;i++)smoke(m,add(FACE,[-.4-.3*rnd(360+i),.3+.4*rnd(370+i),(rnd(380+i)-.5)*1.4]),age-.25-i*.06,{size:.55,seed:390+i,rise:1.4,duration:1.4});
  }
 },
};
