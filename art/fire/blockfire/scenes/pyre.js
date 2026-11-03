// Runtime port: PyreFrontEntity (40 ticks, synced position + yaw) keeps driving the wall through PyreFrontRenderer,
// which replaces FireSpellMeshes.submitPyre's cards with five block-fire columns. Each column finds its own ground
// in the client world with the server's rule (first solid block from the eye-level block down 3), blended over one
// block like the entity's 2-tick interpolation. The wake, the collapse at 40 blocks and the end spill outlive the
// entity, so a client PyreManager keyed to the cast tick and forward vector (S2C cast packet) draws them; the same
// manager draws the floor carpet per PYRE_FLAME cell from its slice tick and fan-out delay and shrinks it over its
// last 0.6 s (PyreFlameBlock's static block model cannot shrink, so it would become an invisible marker block).
// The coals stay the netherrack PYRE_COALS block. Particles kept: FIRE_IMPACT_RING + FIRE_EMBER on hit (the burst
// here); the burning target keeps vanilla's on-fire overlay. Budget: wall + wake + collapse about 150 cubes, carpet
// 2 cubes per cell (400 for all 200 cells); floor curls and the burning zombie's sparks only with particles on;
// no smoke. check.mjs peak about 575, under the 600 cap.
import{add,mul,norm,clamp,lerp,smoothstep,rnd,noise,TAU}from'../engine.js';
import{cube,flame,burst,puff,sparks,tumble,yawAxes,heatBand,figure,wandTip,EYE}from'../kit.js';

// ---- Gameplay numbers (FireAbilityHandler.castSecondary + PyreSchedulerEntity) ------------------------------
const TPS=20;          // server ticks per second
const T0=.3;           // cast moment in this loop
const WAVE=40;         // WAVE_LENGTH: the front moves 1 block per tick for 40 ticks (40 blocks in 2 s)
const GROUND=100;      // PYRE_GROUND_DURATION: coals and floor flames last 100 ticks (5 s) from the front's pass
const MAXDELAY=4;      // MAX_VISUAL_DELAY: outer lanes light up to 4 ticks after the middle
const WALL_H=2.8;      // the agreed wall height (the current cards are 2.8 in the middle, 2.45 at the sides)
const CASTER=[.5,0,.5];// standing in the middle of a block; the front starts from the eye (1.62) 1 block out
const ZOMBIE=[28.5,0,.5];
// The hit slab is 1 block deep (±0.5) around the front, measured from the caster's eye to the target's feet,
// so the zombie 28 blocks out is hit on tick 27, when the front reaches 28 blocks.
const HIT=T0+(ZOMBIE[0]-CASTER[0]-1)/TPS;
const END=T0+(WAVE-1)/TPS;   // the last slice: the front stops 40 blocks out and the wall collapses there

// ---- Test ground: a staggered 1-block step up, then a 1-block dip --------------------------------------------
// The server searches from the block at eye level down 3 (blocks y 1 … -2 for a caster standing at y 0), so the
// wall can climb ledges up to 2 blocks and drop into 1-block dips; deeper holes leave a gap in that slice.
function height(x,z){
 const bx=Math.floor(x),bz=Math.floor(z);
 if(bx>=(bz<0?9:8)&&bx<(bz>1?14:15))return 1;
 if(bx>=(bz>1?20:19)&&bx<24)return -1;
 return 0;
}

/** Ground under a moving column, blended between block centres over one block (the entity's 2-tick lerp). */
function laneGround(x,z){const a=x-.5,i=Math.floor(a);return lerp(height(i+.5,z),height(i+1.5,z),smoothstep(a-i))}

// The floor cells, mirrored from tick(): slice k samples w = -2..2 in half-block steps at k+1 blocks out; a cell's
// flame waits for the smallest delay that reaches it, and every cell ends GROUND ticks after its slice.
const CELLS=[];
for(let k=0;k<WAVE;k++){
 const fx=CASTER[0]+k+1,bx=Math.floor(fx),delays=new Map();
 for(let w=-2;w<=2;w+=.5){
  const bz=Math.floor(CASTER[2]+w),d=Math.min(MAXDELAY,Math.round(Math.abs(w)*2));
  if(!delays.has(bz)||delays.get(bz)>d)delays.set(bz,d);
 }
 for(const[bz,d]of delays){
  const y=height(bx+.5,bz+.5),seed=bx*31.7+bz*7.3+.5;
  CELLS.push({bx,bz,y,seed,coal:T0+k/TPS,lit:T0+(k+d)/TPS,out:T0+(k+GROUND)/TPS,curl:rnd(seed+.9)<.16});
 }
}

// ---- The wall -------------------------------------------------------------------------------------------------
const LANES=[-2,-1,0,1,2].map(j=>({j,z:CASTER[2]+j,h:WALL_H*[.84,.93,1,.93,.84][j+2]}));
const LEAN=norm([.16,1,0]);   // tops lean a little into the direction of travel
/** Block centre of the slice the front is on: 1 block out at the cast, 40 blocks out at tick 39. */
const frontX=t=>CASTER[0]+1+TPS*clamp(t-T0,0,(WAVE-1)/TPS);
/** Rises out of the ground over its first two blocks, full height all the way to 40, then collapses in 0.3 s. */
function wallEnv(t){const a=t-T0;if(a<0||t>END+.3)return 0;return smoothstep(a/.1)*(1-smoothstep((t-END)/.3))}

function wall(m,t){
 const env=wallEnv(t);if(env<=0)return;
 const x=frontX(t),wide=.45+.55*env;
 for(const L of LANES){
  const y=laneGround(x,L.z),H=L.h*env*(.94+.12*noise(t*3.1+L.j*5.3)),seed=100+L.j*13;
  // Outer body: a broad column that cools to ember at the crown.
  flame(m,[x,y,L.z],LEAN,{width:.98*wide,length:H,count:7,heat:1.08,speed:1.4,sway:.05,flicker:.6,seed,t});
  // Hot core: a shorter, brighter column inside the lower half.
  flame(m,[x+.08,y,L.z],LEAN,{width:.66*wide,length:H*.52,count:4,heat:1.35,speed:1.7,sway:.03,flicker:.8,seed:seed+5,t});
 }
 // Lower tongues in the seams keep the wall closed without making it one slab.
 for(let g=0;g<4;g++){
  const a=LANES[g],b=LANES[g+1],z=(a.z+b.z)/2,y=Math.max(laneGround(x,a.z),laneGround(x,b.z));
  flame(m,[x-.12,y,z],LEAN,{width:.58*wide,length:(a.h+b.h)*.31*env,count:3,heat:1.15,speed:1.5,sway:.04,seed:170+g*11,t});
 }
}

/** Pieces left behind the moving wall: born inside a column, then shrink to nothing within ~6 blocks. */
function wake(m,t){
 const a=t-T0,life=.3,dt=.05;if(a<0||a>END-T0+life)return;
 for(const L of LANES){
  const first=Math.max(1,Math.ceil((a-life)/dt)),last=Math.floor(Math.min(a,END-T0)/dt);
  for(let e=first;e<=last;e++){
   const te=e*dt,age=a-te,envE=wallEnv(T0+te);if(age<0||age>=life||envE<=0)continue;
   const k=500+L.j*97+e*3.1,l=age/life,hf=.12+.6*rnd(k+1);
   const x0=frontX(T0+te)-.15+(rnd(k)-.5)*.3,y0=laneGround(x0,L.z)+hf*L.h*envE,z0=L.z+(rnd(k+2)-.5)*.6;
   const s=(.3+.2*rnd(k+3))*(1-.65*hf)*Math.pow(1-l,1.15)*Math.min(1,envE*1.4);
   cube(m,[x0,y0+.45*l,z0],s,{band:heatBand(2.9-2.7*l),seed:k,axes:tumble(k,age,4)});
  }
 }
}

/** When the front stops at 40 blocks, the collapsing columns throw their last pieces a block or two on. */
function spill(m,t){
 for(const L of LANES)for(let i=0;i<3;i++){
  const k=700+L.j*31+i*7.7,start=END-.04+rnd(k)*.2,age=t-start,life=.5;if(age<0||age>=life)continue;
  const l=age/life,xs=frontX(start),env=Math.max(.35,wallEnv(start)),gy=laneGround(xs,L.z);
  const dir=norm([1+rnd(k+2)*.6,.25+rnd(k+3)*.6,(rnd(k+4)-.5)*.7]),r=1.6*(1-Math.pow(1-l,2));
  const s=(.28+.18*rnd(k+5))*Math.pow(1-l,1.1);
  const p=add([xs,gy+(.25+.55*rnd(k+1))*L.h*env,L.z],mul(dir,r));p[1]=Math.max(p[1]-1.3*l*l,gy+s/2);
  cube(m,p,s,{band:heatBand(3-2.9*l),seed:k,axes:tumble(k,age,5)});
 }
}

// ---- The floor: netherrack coals and a carpet of flames a few inches high ----------------------------------------
const COAL=[.9,.44,.7,1]; // dirt tile tinted to netherrack's dull red; scene-lit like a real block
const FADE=.6;            // the carpet shrinks away over the last 0.6 s of its 5 s

function coalBlock(m,bx,bz,y){
 const e=.003,y0=y-1,y1=y;
 m.quad([[bx,y+e,bz+1],[bx+1,y+e,bz+1],[bx+1,y+e,bz],[bx,y+e,bz]],'dirt',{color:COAL});
 // The converted block's own sides where the ground drops away (step and dip edges).
 if(height(bx+1.5,bz+.5)<y){const X=bx+1+e;m.quad([[X,y0,bz+1],[X,y0,bz],[X,y1,bz],[X,y1,bz+1]],'dirt',{color:COAL})}
 if(height(bx-.5,bz+.5)<y){const X=bx-e;m.quad([[X,y0,bz],[X,y0,bz+1],[X,y1,bz+1],[X,y1,bz]],'dirt',{color:COAL})}
 if(height(bx+.5,bz+1.5)<y){const Z=bz+1+e;m.quad([[bx,y0,Z],[bx+1,y0,Z],[bx+1,y1,Z],[bx,y1,Z]],'dirt',{color:COAL})}
 if(height(bx+.5,bz-.5)<y){const Z=bz-e;m.quad([[bx+1,y0,Z],[bx,y0,Z],[bx,y1,Z],[bx+1,y1,Z]],'dirt',{color:COAL})}
}

/**
 * One cell of floor fire, two cubes. The bed is the flame sheet itself: 2-3 inches thick (the approved 0.075-block
 * cap), a little larger than its block and only slightly turned, so neighbours overlap into one ragged sheet whose
 * low fire-textured edges show from the side. On it one flamelet (its top 4-8 inches over the ground)
 * grows, flickers taller and shorter, and shrinks to nothing before it reappears somewhere else in the cell.
 * env 0..1 lights it up and dies it down. With particles on, a few cells also lift a tiny ember curl.
 */
function carpetCell(m,c,t,env,embers){
 const cx=c.bx+.5,cz=c.bz+.5,s=c.seed,cool=1-env;
 // Alternate cells sit a step higher, so overlapping tops never fight; the flicker is in thickness.
 const lift=((c.bx+c.bz)&1)?.018:0,bh=(.044+lift+.014*noise(t*3.7+s))*env,grow=.4+.6*env;
 const bw=(.96+.14*rnd(s+.3)+.05*noise(t*2.3+s*1.7))*grow,bd=(.96+.14*rnd(s+.35)+.05*noise(t*2.1+s*2.3))*grow;
 cube(m,[cx+(rnd(s+.1)-.5)*.14,c.y+bh/2,cz+(rnd(s+.2)-.5)*.14],[bw,bh,bd],
  {band:heatBand(.95-cool*1.2+1.2*noise(t*2.6+s*3)),seed:s,axes:yawAxes((rnd(s+.4)-.5)*.4)});
 // The flamelet: narrow and upright, flickering by height; it shrinks to nothing before it moves.
 const per=.42+.36*rnd(s+.5),ph=t/per+rnd(s+.6),life=Math.floor(ph),u=ph-life,k=s+life*13.7;
 const g=Math.pow(Math.sin(u*Math.PI),.6)*env,tw=(.18+.1*rnd(k))*g;
 const th=(.08+.05*rnd(k+1))*g*(.7+.5*noise(t*7.3+k));
 if(tw>.02)cube(m,[cx+(rnd(k+2)-.5)*.55,c.y+bh*.8+th/2,cz+(rnd(k+3)-.5)*.55],[tw,th,tw*(.8+.3*rnd(k+5))],
  {band:heatBand(3.7-cool*2-u*1.5),seed:s+1,axes:yawAxes(rnd(k+4)*TAU)});
 if(embers&&c.curl){
  const cp=.7+.4*rnd(s+.7),cu=((t/cp+rnd(s+.8))%1+1)%1,ck=s+Math.floor(t/cp+rnd(s+.8))*5.1,cs=.07*(1-cu)*env;
  if(cs>.012)cube(m,[cx+(rnd(ck)-.5)*.6,c.y+.06+.14*cu,cz+(rnd(ck+1)-.5)*.6],cs,{band:heatBand(3.2-cu*2.4),seed:s+2,axes:tumble(ck,t,4)});
 }
}

function groundFire(m,t,embers){
 for(const c of CELLS){
  if(t>=c.coal&&t<c.out)coalBlock(m,c.bx,c.bz,c.y);
  if(t<c.lit||t>=c.out)continue;
  const env=smoothstep((t-c.lit)/.12)*smoothstep((c.out-t)/FADE);
  if(env>.01)carpetCell(m,c,t,env,embers);
 }
}

// ---- Figures --------------------------------------------------------------------------------------------------
function caster(m,t,cam){
 const casting=t>=T0-.1&&t<T0+1;
 if(cam!=='caster')figure(m,CASTER,0,{pose:casting?'cast':'idle'});
 // A short ignition at the wand tip so the wall has a visible cause (no cast effect exists in game today).
 const a=t-T0+.04;if(a>=0&&a<.24)puff(m,wandTip(CASTER,0),a,{size:.2*Math.pow(1-a/.24,.8),seed:31,heat:3.6,spin:6});
}

/** Vanilla knockback from the hit pushes the zombie about 0.7 block away from the caster. */
function zombiePos(t){const a=t-HIT;return[ZOMBIE[0]+(a>0?.7*(1-Math.pow(1-clamp(a/.35),2)):0),0,ZOMBIE[2]]}

function zombie(m,t,embers,cam){
 const a=t-HIT,pos=zombiePos(t),self=cam==='target';
 // Red flash on the hit, then the once-a-second burn ticks while it is on fire.
 let hurt=a>=0&&a<.4?1-a/.4:0;
 if(a>=.9&&a<5.1){const f=a%1;if(f<.2)hurt=Math.max(hurt,.55*(1-f/.2))}
 // In the target view you are the zombie, so its own body is not drawn (first person).
 if(!self)figure(m,pos,Math.PI,{kind:'zombie',hurt});
 if(a<0)return;
 // The hit: FIRE_IMPACT_RING + FIRE_EMBER at the body in game (no smoke); a short tumbling burst here.
 if(!self)burst(m,[pos[0]-.25,1.05,pos[2]],a,{radius:1.3,count:14,size:.36,duration:.5,seed:41,up:.5,smoke:false});
 else burst(m,[pos[0]-.9,.85,pos[2]],a,{radius:.8,count:8,size:.22,duration:.4,seed:41,up:.6,smoke:false,flash:false});
 if(self)return;
 // Set on fire for 5 s (vanilla's burning overlay in game; small block flames stand in for it here).
 const fe=smoothstep(a/.15)*smoothstep((5-a)/.3);if(fe<=0)return;
 const parts=[[-.2,.05,-.18,.42,.95],[.18,.55,.22,.36,.8],[-.05,1.25,.05,.34,.75]];
 parts.forEach(([dx,dy,dz,w,l],i)=>flame(m,add(pos,[dx,dy,dz]),[0,1,0],{width:w*fe,length:l*fe,count:3,heat:.95,sway:.04,seed:60+i*9,t}));
 if(embers)sparks(m,add(pos,[0,.4,0]),t,{count:5,spread:.4,rise:1.8,seed:77,size:.07});
}

export default {
 id:'pyre',name:"Dragon's Pyre",slot:'Secondary',
 duration:7.8,
 summary:'A wall of five fire columns, 2.8 blocks tall and 5 blocks wide, rushes 40 blocks along the ground in just 2 '+
  'seconds, stepping up the ledge and down into the dip as it goes. The zombie it runs into takes a hit and catches fire. '+
  'Behind the wall the ground turns to netherrack under a carpet of flames only a few inches high, which burns for 5 '+
  'seconds and then dies down, nearest first.',
 notes:'What you are seeing\n'+
  '• The wall is five columns side by side, one per block of width. Each column finds the ground under itself, so the '+
  'wall steps up and down with the terrain instead of floating. The ledge here is staggered, so you can watch the '+
  'columns climb it one after another.\n'+
  '• Every column is block fire: a hot yellow core low down, with cubes rising, shrinking and cooling to dark ember at '+
  'the top. The tops flicker taller and shorter; nothing waves.\n'+
  '• Bits that fall behind the moving wall shrink to nothing within a few blocks. At 40 blocks the wall stops, sinks '+
  'into the ground in a third of a second and throws its last pieces a block or two forward.\n'+
  '• The carpet is two cubes per block: a glowing sheet of fire 2 to 3 inches thick (the height you approved before), '+
  'and one small flame on it whose top reaches 4 to 8 inches. That flame grows, flickers taller and shorter, and shrinks '+
  'away before popping up somewhere else in the block. With particles on, a few tiny embers lift about a fifth of a '+
  'block. There is no smoke: grey cubes over the wall read as flying rocks.\n'+
  '• Target view is the zombie\'s eyes. The wall\'s top is a block above a player\'s head, so it reads as a wall '+
  'coming at you, not a wave along the floor; for a moment as it passes, your whole screen is fire.\n\n'+
  'Real numbers from the game\n'+
  '• Speed: 1 block per tick, so 20 blocks a second. It covers 40 blocks in 2 seconds, then it is gone.\n'+
  '• Hit zone: 5 blocks wide (2.5 each side of your aim) and 1 block deep, at full strength the whole way. Each enemy '+
  'is hit once for 6 damage (3 hearts, before Fire level bonuses) and set on fire for 5 seconds. The zombie here, 28 '+
  'blocks away, is hit 1.35 seconds after the cast.\n'+
  '• Ground: the blocks under the wall turn to netherrack coals for 5 seconds, and enemies standing on them take a '+
  'little damage, like magma. The low flames on top last the same 5 seconds. The middle lights first and the sides up '+
  'to a fifth of a second later, which gives the carpet its slight V just behind the wall.\n'+
  '• Following the ground: the game looks for ground from your eye level down, between 2 blocks above and 1 block '+
  'below where you stood, so the wall can climb ledges up to 2 blocks high and drop into dips 1 block deep. Anything '+
  'outside that is skipped for that block of travel. The search follows your aim, so cast it looking level.\n'+
  '• Standing on your own carpet during those 5 seconds gives you Regeneration and Speed III. Cooldown: 10 seconds.',
 phases:[[0,'Ready'],[T0,'Cast: the wall rises a block ahead'],[.62,'Climbs the ledge'],[1.17,'Drops into the dip'],
  [HIT,'Hits the zombie and sets it on fire'],[END,'Stops at 40 blocks and collapses'],[END+.35,'Carpet burns for 5 s'],
  [T0+5-FADE,'Dies down, nearest first'],[T0+(WAVE-1+GROUND)/TPS,'Gone: the ground turns back']],
 area:{x0:-5,x1:46,z0:-8,z1:9},
 terrain:height,
 budget:600,
 cameras:{
  orbit:{target:[13,1,0],yaw:-1.3,pitch:.3,distance:17},
  caster:{eye:[CASTER[0],EYE,CASTER[2]],look:[26,.3,CASTER[2]]},
  // The zombie's own eyes at player height, looking back at the caster.
  target:t=>{const p=zombiePos(t);return{eye:[p[0]-.2,EYE,p[2]],look:[CASTER[0],1.1,CASTER[2]]}},
 },
 draw(m,t,ctx){
  caster(m,t,ctx.cam);
  zombie(m,t,ctx.embers,ctx.cam);
  groundFire(m,t,ctx.embers);
  wall(m,t);
  wake(m,t);
  spill(m,t);
 },
};
