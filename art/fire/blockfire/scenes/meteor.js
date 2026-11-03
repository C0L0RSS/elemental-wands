// Runtime port: MeteorManager (server) spawns the METEOR_CORE FallingBlockEntity 35 blocks above the aimed point
// and calls explode() when it lands. FireMeteorRenderer.submitMeteor draws the falling ball from the entity alone:
// age (spin, grow-in, tongue clock), position (fall) and vertical velocity (how far the tongues sweep back). The
// shell grid, core and tongue roots are fixed tables; chunks shed in flight are keyed to the tick they leave, so
// every viewer sees the same shower. The warning ring needs the landing point and spawn height (descent progress):
// today the server spawns FIRE_METEOR_WARNING particles every 4 ticks; a client ring renderer needs a small payload
// (brand pos, spawn Y, entity id) or can raycast down from the entity itself. The impact (flash, dome, shock ring,
// magma chunks, scorch) needs a client effect started by the explosion: a MeteorImpactPayload (pos, server tick)
// or a short-lived effect entity; after that everything is a pure function of age.
// Particles that stay: FIRE_EMBER sparks and LARGE_SMOKE (accents), the vanilla explosion particles, and the real
// vanilla fire blocks that createExplosion places. Replaced by cubes: FIRE_METEOR_WARNING, FIRE_METEOR_IMPACT,
// FIRE_IMPACT_RING, FIRE_FLAME_RIBBON and the LAVA particles (now the magma chunks).
// Cube budget: 1500 (about 1000 while falling, about 1250 at the impact peak; embers off removes ~70).
import{cube,flame,smoke,sparkBurst,figure,tumble,heatBand,yawAxes,basis,sphereDir,wandTip,AXES,EYE}from'../kit.js';
import{add,mul,norm,len,clamp,lerp,smoothstep,rnd,noise,TAU,UP}from'../engine.js';

// ---- Real numbers (FireAbilityHandler, MeteorManager, FireSpellMeshes.submitMeteor) ------------------------
const TICK=.05;
const RANGE=25;                                  // AbstractWandItem.DEFAULT_RANGE: how far you can aim
const SPAWN_HEIGHT=35;                           // METEOR_SPAWN_HEIGHT above the aimed block
const START_VY=-.2,GRAVITY=.04,DRAG=.98;         // setVelocity(0, -0.2, 0) + vanilla FallingBlockEntity physics
const SIZE=8,R=SIZE/2,SPIN=.19;                  // approved eight-block ball, centre 4 above the entity, spin rad/s
const RING_R=5.4,RING_CLOSE=.8;                  // spawnLandingRing: radius 5.4 - 0.8 × descent progress
const FULL_R=3,DAMAGE_R=10,MAX_DAMAGE=60,POWER=5;// impactDamage: 60 inside 3 blocks, none past 10; explosion power 5
const WARN_R=60;                                 // action-bar warning radius
const damageAt=d=>MAX_DAMAGE*clamp((DAMAGE_R-d)/(DAMAGE_R-FULL_R));
/** Feet height above the landing per tick (vanilla order: gravity, move, then drag). Constant table. */
const FALL=(()=>{const ys=[SPAWN_HEIGHT];let y=SPAWN_HEIGHT,v=START_VY;while(y>0){v-=GRAVITY;y=Math.max(0,y+v);v*=DRAG;ys.push(y)}return ys})();
const FALL_TICKS=FALL.length-1;                  // 44 ticks
const FLIGHT=FALL_TICKS*TICK;                    // 2.2 s
const LAND_SPEED=(FALL[FALL_TICKS-2]-FALL[FALL_TICKS-1])/TICK; // ~24 blocks a second on the last full step

// ---- Timeline and layout ----------------------------------------------------------------------------------
const T0=.4,TI=T0+FLIGHT,DURATION=7.2;
const TGT=[17,0,0];                    // the aimed point, 17 blocks away (outside the 10-block blast)
const ZA=[17.7,0,1.1];                 // the target zombie, 1.3 blocks from the centre: full 60 damage
const ZB=[23.6,0,-1.6];                // a zombie 6.8 blocks out, outside the warning ring: still ~28 damage
const DEAD=TI+.32;                     // both zombies are gone once the dome has swallowed them

function feet(t){
 const k=(t-T0)/TICK;if(k<=0)return SPAWN_HEIGHT;if(k>=FALL_TICKS)return 0;
 const i=Math.floor(k);return lerp(FALL[i],FALL[i+1],k-i);
}
/** Downward speed in blocks a second at time t. */
function fallSpeed(t){const i=Math.floor(clamp((t-T0)/TICK,0,FALL_TICKS-2));return(FALL[i]-FALL[i+1])/TICK} // last step is cut short by the ground
const center=t=>[TGT[0],feet(t)+R,TGT[2]];
/** 0 at spawn, 1 at landing: the ring's descent progress (by height, like the server). */
const descent=t=>1-feet(t)/SPAWN_HEIGHT;

// ---- Terrain: open field, a low mound inside the blast and a step behind it -------------------------------
function height(x,z){
 const cx=Math.floor(x),cz=Math.floor(z);
 if(cx>=19&&cx<=21&&cz>=-11&&cz<=-8)return 1;
 if(cx>=29)return 1;
 return 0;
}

// ---- The falling ball: a stepped shell of block fire around a white-hot core -------------------------------
const CELL=2/3;
/** Exposed cells of a sphere of radius 3.98 on a 2/3-block grid (12 cells across, same outline as approved). */
const SHELL=(()=>{
 const n=6,inside=(i,j,k)=>i>=-n&&i<n&&j>=-n&&j<n&&k>=-n&&k<n&&Math.hypot(i+.5,j+.5,k+.5)*CELL<3.98,out=[];
 for(let i=-n;i<n;i++)for(let j=-n;j<n;j++)for(let k=-n;k<n;k++){
  if(!inside(i,j,k))continue;
  if([[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]].every(([a,b,c])=>inside(i+a,j+b,k+c)))continue;
  const c=[(i+.5)*CELL,(j+.5)*CELL,(k+.5)*CELL];out.push({c,r:len(c),n:norm(c)});
 }
 return out;
})();
/** The hot layer under the shell: exposed cells of a radius-3.1 sphere on a one-block grid. */
const CORE=(()=>{
 const n=3,inside=(i,j,k)=>i>=-n&&i<n&&j>=-n&&j<n&&k>=-n&&k<n&&Math.hypot(i+.5,j+.5,k+.5)<3.1,out=[];
 for(let i=-n;i<n;i++)for(let j=-n;j<n;j++)for(let k=-n;k<n;k++){
  if(!inside(i,j,k)||[[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]].every(([a,b,c])=>inside(i+a,j+b,k+c)))continue;
  out.push({c:[i+.5,j+.5,k+.5]});
 }
 return out;
})();
/** Tongue roots: even directions over the top half of the ball; the topmost few are the long crown flames. */
const TONGUES=(()=>{const out=[];for(let i=0;i<46;i++){const d=sphereDir(i,46);if(d[1]>.05)out.push({d,crown:d[1]>.8})}return out})();

/** Grow-in when the ball appears (0 → 1 over a quarter second) instead of popping in at full size. */
const appear=t=>{const k=clamp((t-T0)/.25);return k*k*(3-2*k)};

function ball(m,t,ctx){
 if(t<T0||t>=TI)return;
 const C=center(t),k=appear(t),age=t-T0,sp=clamp(fallSpeed(t)/LAND_SPEED);
 const ax=yawAxes(SPIN*age),[f,,s]=ax,local=c=>add(add(mul(f,c[0]),mul(UP,c[1])),mul(s,c[2]));
 // Shell: hottest on the leading (bottom) face, cooling toward the top and outermost steps. Each cube boils
 // in size and band on its own; some pulse small for a moment and open vents onto the hot layer below.
 SHELL.forEach((cell,i)=>{
  const vent=smoothstep((noise(t*1.6+i*3.17)-.56)/.16);
  const sz=CELL*k*(.86+.2*noise(t*5.3+i*1.37))*(1-.72*vent);
  const heat=2.3-cell.n[1]*1.15+(noise(t*2.1+i*.71)-.5)*1.4-(cell.r>3.6?.3:0)+.35*sp;
  cube(m,add(C,local(mul(cell.c,k))),sz,{band:heatBand(heat),seed:1000+i,axes:ax});
 });
 // Hot layer: a one-block stepped sphere just under the shell, white and yellow, so every gap glows.
 CORE.forEach((cell,i)=>cube(m,add(C,local(mul(cell.c,k))),k,{band:noise(t*2.7+i*1.9)>.45?'core':'yellow',seed:1700+i,axes:ax}));
 // Tongues stream back (up) against the fall from the top half only, so the leading face stays a clean ball.
 // They lengthen and sweep back further as the ball speeds up; their bases sit hidden inside the shell.
 TONGUES.forEach(({d,crown},j)=>{
  const n=local(d),root=add(C,mul(n,(R-.8)*k));
  const dir=norm(add(mul(n,crown?.22:.62),[0,.9+.9*sp,0]));
  const L=(2+2.2*rnd(2000+j*1.9))*(crown?1.45:1)*(.8+.55*sp)*k,W=(.75+.32*rnd(2001+j*2.3))*(crown?1.25:1)*k;
  flame(m,root,dir,{width:W,length:L,seed:2000+j*13,heat:1.05,sway:.04,speed:1.4+sp});
 });
}

// ---- Burning chunks shed in flight: left behind as the ball falls away, so they stream above it -------------
const SHED=44;
function shower(m,t,ctx){
 const first=Math.ceil(Math.max(T0+.18,t-1.15)*SHED),last=Math.floor(Math.min(t,TI)*SHED);
 for(let k=first;k<=last;k++){
  const te=k/SHED,age=t-te,life=.65+.45*rnd(k*1.9),l=age/life;if(l<0||l>=1)continue;
  const a=rnd(k*2.3)*TAU,y=-.55+1.05*rnd(k*3.1),h=Math.sqrt(1-y*y),n=[Math.cos(a)*h,y,Math.sin(a)*h];
  // World velocity: half the ball's fall speed at release plus an outward kick.
  const out=1.8+1.6*rnd(k*4.7),p=add(add(center(te),mul(n,R*.96)),[n[0]*out*age,-fallSpeed(te)*.5*age,n[2]*out*age]);
  const sz=(.45+.4*rnd(k*5.3))*Math.min(1,l*6)*Math.pow(1-l,1.1);
  if(p[1]<sz)continue;
  const axes=tumble(k,age,5);
  cube(m,p,sz,{band:heatBand(3.9-l*3),seed:k,axes});
  if(k%2===0&&sz>.15)cube(m,add(p,mul(axes[1],sz*.5)),sz*.55,{band:heatBand(3.1-l*3.2),seed:k+.5,axes});
 }
 if(!ctx.embers)return;
 // Ember sparks off the shell and a dark smoke trail high above (accents only).
 for(let k=Math.ceil(Math.max(T0+.1,t-.5)*40);k<=Math.floor(Math.min(t,TI)*40);k++){
  const te=k/40,age=t-te,l=age/.5;if(l<0||l>=1)continue;
  const a=rnd(k*6.1)*TAU,y=-.3+1.1*rnd(k*6.7),h=Math.sqrt(Math.max(0,1-y*y)),n=[Math.cos(a)*h,y,Math.sin(a)*h];
  const p=add(add(center(te),mul(n,R+.2)),[n[0]*5*age,(-fallSpeed(te)*.35+3)*age,n[2]*5*age]);
  cube(m,p,.16*(1-l),{band:heatBand(3.6-l*2),seed:7000+k,axes:tumble(7000+k,age,6)});
 }
 // Smoke puffs come off the top half and are left behind, spreading into a loose trail.
 for(let k=Math.ceil(Math.max(T0+.3,t-1.5)*5);k<=Math.floor(Math.min(t,TI-.15)*5);k++){
  const te=k/5,a=rnd(k*8.3)*TAU,y=.35+.6*rnd(k*8.9),h=Math.sqrt(1-y*y),n=[Math.cos(a)*h,y,Math.sin(a)*h];
  const p=add(add(center(te),mul(n,R+.3)),mul([n[0],0,n[2]],2.2*(t-te)));
  smoke(m,p,t-te,{size:.9+.4*rnd(k*9.7),rise:2,duration:1.5,seed:7500+k});
 }
}

// ---- The warning ring: a ring of short block flames on the ground that closes and grows as the ball drops ---
const RING_N=28,BAND_N=40;
function warningRing(m,t,ctx){
 if(t<T0||t>TI+.12)return;
 const p=descent(Math.min(t,TI)),r=RING_R-RING_CLOSE*p;
 const grow=smoothstep((t-T0)/.3)*(1-smoothstep((t-TI)/.12));if(grow<=0)return;
 for(let i=0;i<RING_N;i++){
  const a=(i+.3*rnd(3000+i))/RING_N*TAU,c=Math.cos(a),s=Math.sin(a),x=TGT[0]+c*r,z=TGT[2]+s*r;
  flame(m,[x,ctx.ground(x,z),z],[-c*.12,1,-s*.12],{width:.55*grow,length:(.5+.85*p)*grow*(.8+.4*rnd(3001+i*1.7)),
   seed:3100+i*7,heat:1+.18*p,sway:.04,speed:1+.8*p});
 }
 // A low band of burning ground under the flames, so the ring reads as a solid circle from far away.
 const chord=TAU*r/BAND_N;
 for(let i=0;i<BAND_N;i++){
  const a=(i+.5)/BAND_N*TAU,x=TGT[0]+Math.cos(a)*r,z=TGT[2]+Math.sin(a)*r,h=.14*grow*(.8+.4*noise(t*5+i*2.1));
  cube(m,[x,ctx.ground(x,z)+h/2,z],[chord*.94*grow,h,.55*grow],{band:heatBand(1.6+p+(noise(t*3.3+i*1.3)-.5)*1.4),seed:3500+i,axes:yawAxes(a+Math.PI/2)});
 }
}

// ---- Impact ------------------------------------------------------------------------------------------------
const DOME_N=110,INNER_N=64,SHOCK_N=44,SHOCK_REACH=.3,CHUNK_N=24,CHUNK_G=18;
/** Golden-spiral directions over the upper half of a sphere (y > 0). */
const upDir=(i,n)=>sphereDir(i,n*2);
/** Where a ray from the ground contact point leaves the ball (centre 4 up, radius 4): 8·dy. */
const ballEdge=d=>SIZE*d[1];

/** The ball's heart bursts: a cluster of white-hot cubes where the ball was, swelling a little and sinking away
 * inside the dome. Many medium cubes, so it reads as a blob of heat rather than one big box. */
const FLASH_N=14;
function flash(m,a){
 if(a<0||a>.34)return;
 const grow=a<.05?lerp(.85,1,a/.05):1-smoothstep((a-.05)/.29);
 for(let j=0;j<FLASH_N;j++){
  const d=sphereDir(j,FLASH_N),sd=4000+j*2.3,o=(1.6+.8*rnd(sd))*(1+2.2*a);
  const p=add(TGT,[d[0]*o,R*.8+d[1]*o*.8,d[2]*o]);
  cube(m,p,(2.6+1.1*rnd(sd+.1))*grow,{band:heatBand(4.3-a*6-rnd(sd+.2)*.8),seed:sd,axes:tumble(sd,a,1.6)});
 }
}

function dome(m,a,ctx){
 if(a<0||a>2)return;
 // Outer shell of the dome: pieces leave the ball's surface and balloon out to the 10-block damage edge,
 // then cool through the bands, drift up and shrink away.
 // Each piece is a burning clump pointing along its flight, so the dome reads as fire thrown outward rather
 // than a heap of loose blocks. The top thins and cools first; the skirt near the ground lasts longest. Pieces
 // are gone before they reach the dark ember band.
 for(let i=0;i<DOME_N;i++){
  const d=upDir(i,DOME_N),sd=4100+i*1.3,delay=.04*rnd(sd);
  const e=1-Math.pow(1-clamp((a-delay)/.4),3),R1=DAMAGE_R*(.9+.1*rnd(sd+.2));
  const r=lerp(ballEdge(d)*.95,R1,e)+.7*Math.max(0,a-.4);
  const die=1-smoothstep((a-.22-.25*rnd(sd+.4)-.2*(1-d[1]))/(.4+.25*rnd(sd+.5)));if(die<=0)continue;
  const sz=(.62+.45*rnd(sd+.6))*(.8+.2*e)*die;
  const p=add(TGT,add(mul(d,r),[0,1.1*Math.pow(Math.max(0,a-.3),2),0]));
  p[1]=Math.max(p[1],ctx.ground(p[0],p[2])+sz*.5);
  const heat=3.75-a*2.6-.4*d[1]*smoothstep(a/.3)+(rnd(sd+.7)-.5)*1.1;
  clump(m,p,sz,heat,sd,basis(d,rnd(sd+.8)*TAU+a*(rnd(sd+.9)<.5?-1.4:1.4)));
 }
 // Inner layer: hotter, a little behind, gone sooner; it fills the gaps of the outer shell with bright fire.
 for(let i=0;i<INNER_N;i++){
  const d=upDir(i,INNER_N),sd=4700+i*1.7;
  const e=1-Math.pow(1-clamp(a/.46),3),r=lerp(ballEdge(d)*.75,DAMAGE_R*.64*(.88+.12*rnd(sd)),e);
  const die=1-smoothstep((a-.18-.2*rnd(sd+.4))/(.35+.25*rnd(sd+.5)));if(die<=0)continue;
  const sz=(.8+.5*rnd(sd+.6))*die;
  const p=add(TGT,add(mul(d,r),[0,1.4*Math.pow(Math.max(0,a-.25),2),0]));
  p[1]=Math.max(p[1],ctx.ground(p[0],p[2])+sz*.5);
  cube(m,p,[sz,sz*1.3,sz],{band:heatBand(3.95-a*2.6+(rnd(sd+.7)-.5)*.8),seed:sd,axes:basis(d,rnd(sd+.8)*TAU+a*1.2)});
 }
}
/** A burning clump: a main cube with a cooler tip along axes[1] and two smaller cubes on its sides. */
function clump(m,p,size,heat,seed,axes){
 cube(m,p,[size,size*1.25,size],{band:heatBand(heat),seed,axes});
 cube(m,add(p,mul(axes[1],size*.7)),size*.58,{band:heatBand(heat-.8),seed:seed+1,axes});
 for(let k=0;k<2;k++){
  const side=mul(axes[k*2],rnd(seed+k*2.9)<.5?-1:1),s=size*(.45+.12*rnd(seed+k*3.7));
  cube(m,add(add(p,mul(side,size*.5)),mul(axes[1],-size*.2)),s,{band:heatBand(heat-1.1),seed:seed+k+2,axes});
 }
}

/** A ring of flames races across the ground to the 10-block edge, holds there for a moment and dies down. */
function shock(m,a,ctx){
 if(a<0||a>1.3)return;
 const e=clamp(a/SHOCK_REACH),r=1+(DAMAGE_R-1)*(1-(1-e)*(1-e));
 const hs=smoothstep(a/.05)*(1-smoothstep((a-SHOCK_REACH-.12)/.85));if(hs<=0)return;
 const lean=lerp(.55,.15,smoothstep((a-SHOCK_REACH)/.25));
 for(let i=0;i<SHOCK_N;i++){
  const ang=(i+.4*rnd(4500+i))/SHOCK_N*TAU,c=Math.cos(ang),s=Math.sin(ang),ri=r*(1+.025*(rnd(4501+i*1.3)-.5));
  const x=TGT[0]+c*ri,z=TGT[2]+s*ri;
  flame(m,[x,ctx.ground(x,z),z],[c*lean,1,s*lean],{width:.95*(.8+.4*rnd(4502+i))*(.6+.4*hs),length:1.8*hs*(.75+.5*rnd(4503+i*2.1)),
   seed:4600+i*7,heat:1.02-.4*smoothstep((a-SHOCK_REACH)/.8),sway:.04,speed:1.6});
 }
}

/** Molten chunks: magma lumps thrown out in arcs that land, sit burning with a small flame, then shrink away. */
const CHUNKS=Array.from({length:CHUNK_N},(_,i)=>{
 const sd=5000+i*3.1,az=(i+.6*rnd(sd))/CHUNK_N*TAU,vh=3.5+5.5*rnd(sd+.1),vy=7+6*rnd(sd+.2),sz=.55+.35*rnd(sd+.3);
 const p0=add(TGT,[Math.cos(az)*1.3,1+.8*rnd(sd+.4),Math.sin(az)*1.3]),dir=[Math.cos(az),0,Math.sin(az)];
 const flightAt=yg=>(vy+Math.sqrt(vy*vy+2*CHUNK_G*(p0[1]-yg-sz/2)))/CHUNK_G;
 let fl=flightAt(0);const land0=add(p0,mul(dir,vh*fl)),yg=height(land0[0],land0[2]);fl=flightAt(yg);
 return{sd,dir,vh,vy,sz,p0,fl,yg,delay:.06*rnd(sd+.5),burn:1.5+1.1*rnd(sd+.6)};
});
function chunks(m,a,ctx){
 if(a<0)return;
 for(const ch of CHUNKS){
  const age=a-ch.delay;if(age<0)continue;
  const at=u=>add(ch.p0,[ch.dir[0]*ch.vh*u,ch.vy*u-CHUNK_G*u*u/2,ch.dir[2]*ch.vh*u]);
  if(age<ch.fl){
   const g=Math.min(1,age/.08),axes=tumble(ch.sd,age,6);
   cube(m,at(age),ch.sz*g,{band:'magma',seed:ch.sd,axes});
   // A short burning wake: two cooler pieces just behind, smaller each step back.
   for(let j=1;j<=2;j++){const u=age-j*.035;if(u<.02)continue;
    cube(m,at(u),ch.sz*g*(.75-j*.2),{band:heatBand(3.2-j*.9),seed:ch.sd+j,axes:tumble(ch.sd+j,u,6)})}
   continue;
  }
  const q=(age-ch.fl)/ch.burn;if(q>=1)continue;
  const live=1-smoothstep((q-.3)/.7),sz=ch.sz*live,land=at(ch.fl);
  const p=[land[0],ch.yg+sz/2,land[2]],axes=yawAxes(rnd(ch.sd+.8)*TAU);
  cube(m,p,sz,{band:q<.72?'magma':'char',seed:ch.sd,axes});
  if(live>.08)flame(m,[p[0],ch.yg+sz*.85,p[2]],UP,{width:.48*live,length:.85*live,seed:ch.sd+11,heat:.95,sway:.04});
  if(ctx.embers&&q<.12)sparkBurst(m,p,q*ch.burn,{count:5,speed:1.6,up:2.2,life:.4,seed:ch.sd+20,size:.12});
 }
}

// ---- Aftermath: scorched ground (the game blows a real crater) and ordinary fires -----------------------
const SCORCH=(()=>{const out=[];for(let x=-5;x<=5;x++)for(let z=-5;z<=5;z++){
 const cx=TGT[0]+x+.5,cz=TGT[2]+z+.5,d=Math.hypot(cx-TGT[0],cz-TGT[2]);
 if(d<4.6&&rnd(6000+x*31+z*7)>(d<3?.05:.45))out.push({x:cx,z:cz,d,sd:6000+x*31+z*7});}return out})();
const FIRES=[[-3,2],[2,3],[4,-1],[-1,-4],[6,4],[-6,-2],[1,7],[-5,5],[7,-5]].map(([x,z],i)=>({x:TGT[0]+x+.5,z:TGT[2]+z+.5,
 lit:.35+.55*rnd(6500+i),sd:6500+i*11}));
const endFade=t=>1-smoothstep((t-(DURATION-.45))/.38);

function aftermath(m,t,a,ctx){
 if(a<0)return;
 const fade=endFade(t);
 for(const s of SCORCH){
  const g=smoothstep((a-s.d/34)/.12)*fade;if(g<=0)continue;
  cube(m,[s.x,ctx.ground(s.x,s.z)+.03,s.z],[g,.06,g],{band:'char',seed:s.sd,axes:AXES});
 }
 // Vanilla fire blocks the explosion places: a pair of small flames per burning block.
 for(const f of FIRES){
  const g=smoothstep((a-f.lit)/.25)*fade;if(g<=0)continue;const y=ctx.ground(f.x,f.z);
  flame(m,[f.x-.17,y,f.z+.12],UP,{width:.55*g,length:1*g,seed:f.sd,heat:1.05,sway:.04});
  flame(m,[f.x+.2,y,f.z-.15],UP,{width:.45*g,length:.75*g,seed:f.sd+5,heat:1,sway:.04});
 }
 if(!ctx.embers)return;
 sparkBurst(m,add(TGT,[0,1,0]),a,{count:40,speed:11,up:10,life:1.2,seed:6800,size:.2,g:14});
 for(let i=0;i<12;i++){
  const sd=6900+i*2.3,ang=rnd(sd)*TAU,r=3+5*rnd(sd+.1);
  smoke(m,add(TGT,[Math.cos(ang)*r,1+2*rnd(sd+.2),Math.sin(ang)*r]),a-.45-.6*rnd(sd+.3),{size:1+.6*rnd(sd+.4),rise:5+2*rnd(sd+.5),duration:2.2,seed:sd});
 }
}

// ---- Caster: a small flare at the wand tip as the spell goes off -----------------------------------------
const AIM_PITCH=-Math.atan2(EYE,TGT[0]);
function castFlare(m,t,ctx){
 // Third person only: in first person the game draws the held wand separately, and a flare at the server's
 // tip estimate would sit right in front of your eye.
 const a=t-T0;if(a<0||a>.6||ctx.cam==='caster')return;
 const tip=wandTip([0,0,0],0,AIM_PITCH);
 if(a<.22){const k=a/.22,s=.34*Math.pow(Math.sin(k*Math.PI),.6);
  cube(m,tip,s,{band:heatBand(4-k*1.5),seed:8000,axes:tumble(8000,a,4)});cube(m,tip,s*.8,{band:'yellow',seed:8001,axes:tumble(8001,a,-4)})}
 if(ctx.embers)sparkBurst(m,tip,a,{count:8,speed:1.2,up:2.4,life:.5,seed:8010,size:.07,g:3});
}

function figures(m,t,ctx){
 if(ctx.cam!=='caster')figure(m,[0,0,0],0,{pose:t>T0-.35&&t<T0+.55?'cast':'idle',pitch:AIM_PITCH});
 if(t<DEAD){
  const hurt=t>=TI?1:0,yawTo=(p,q)=>Math.atan2(q[2]-p[2],q[0]-p[0]);
  figure(m,ZA,yawTo(ZA,[0,0,0]),{kind:'zombie',hurt});
  if(ctx.cam!=='target')figure(m,ZB,yawTo(ZB,[0,0,0]),{kind:'zombie',hurt});
 }
}

const blend=(p,q,w)=>add(mul(p,1-w),mul(q,w));

export default {
 id:'meteor',name:'Meteor',slot:'Ultimate',
 duration:DURATION,
 budget:1500,
 summary:`Aim at a spot up to ${RANGE} blocks away and an eight-block ball of block fire appears ${SPAWN_HEIGHT} blocks above it. While it falls, a ring of fire burns on the ground under it, flame tongues stream back off the ball and burning chunks shower behind it. It lands ${FLIGHT.toFixed(1)} seconds later: a dome of fire balloons out to the ${DAMAGE_R}-block damage edge, a ring of flames races across the ground to that edge, molten chunks fly out and burn down, and a few ordinary fires are left burning.`,
 notes:[
  `The fall is the real one: the ball starts slowly (4 blocks a second) and speeds up under normal falling-block gravity, landing after ${FALL_TICKS} ticks (${FLIGHT.toFixed(1)} s) at about ${Math.round(LAND_SPEED)} blocks a second. The tongues grow longer and sweep back further as it speeds up.`,
  `The ball keeps the approved outline: ${SIZE} blocks wide with stepped edges, turning slowly (one turn every ${Math.round(TAU/SPIN)} seconds). Its shell is ${SHELL.length} block-fire cubes over a white-hot inner layer. The shell is hottest on the bottom face that leads the fall and cools toward the top; its cubes keep pulsing small for a moment, opening vents onto the hot layer underneath.`,
  `The ground ring is the real warning ring: ${RING_R} blocks from the centre at first, closing to ${(RING_R-RING_CLOSE).toFixed(1)} as the ball comes down, with its flames growing taller as it gets close. Everyone within ${WARN_R} blocks also gets the "MAXIMUM METEOR DETECTED" warning and a wither roar.`,
  `Damage: ${MAX_DAMAGE} (${MAX_DAMAGE/2} hearts) within ${FULL_R} blocks of where it lands, fading to nothing at ${DAMAGE_R} blocks (walls in the way reduce it). The dome and the racing ring of flames stop exactly at that ${DAMAGE_R}-block edge, so the ground shows how far it reached.`,
  `Worth knowing: the warning ring only covers about half the danger zone. The far zombie stands outside the ring, ${Math.hypot(ZB[0]-TGT[0],ZB[2]-TGT[2]).toFixed(1)} blocks from the centre, and still takes about ${Math.round(damageAt(Math.hypot(ZB[0]-TGT[0],ZB[2]-TGT[2])))} damage, more than a zombie's 20 health. A zombie, or a player with no armour, dies anywhere within about 7.7 blocks.`,
  `You stand ${TGT[0]} blocks back here. The blast never hurts you or your party, wherever you aim.`,
  `The explosion has power ${POWER}: it breaks blocks and starts fires. This page's ground can't change, so the crater is drawn as scorched black ground; the game digs a real crater there. The small fires afterwards stand in for the ordinary Minecraft fires the explosion places.`,
  'The molten chunks are new: magma lumps thrown out in arcs that land, burn with a small flame and shrink away. They replace the lava particles.',
  'Also new and optional: the ball grows in over a quarter second instead of popping in, and other players see a small flare at your wand tip as you cast (the game shows nothing at the wand today; it is left out of your own first-person view).',
  'Turning embers off removes the sparks and smoke; the ball, ring, dome, flames and chunks stay. Camera 2 is your view as you watch it fall. Camera 3 is the far zombie: it sees the ring light up around it, then looks up.',
 ].join('\n'),
 phases:[[0,'Aiming'],[T0,`Cast: the ball appears ${SPAWN_HEIGHT} blocks up`],[T0+.3,'Falling: the ring marks the spot'],[TI,'Impact: the dome blows out to 10 blocks'],[TI+.6,'Molten chunks land and burn out'],[TI+2.4,'Ordinary fires keep burning']],
 area:{x0:-6,x1:34,z0:-14,z1:14},
 terrain:height,
 cameras:{
  orbit:{target:[14,16,0],yaw:.5,pitch:.12,distance:48},
  caster:t=>{
   const eye=[0,EYE,0],aim=[TGT[0],.3,TGT[2]];
   // Aim at the spot, look up to watch the ball come down, then settle on the blast.
   const C=center(clamp(t,T0,TI)),track=[TGT[0],lerp(.5,C[1],.72),TGT[2]],blast=[TGT[0],3,TGT[2]];
   const up=smoothstep((t-T0-.1)/.55),down=smoothstep((t-TI+.25)/.4);
   return{eye,look:blend(blend(aim,track,up),blast,down)};
  },
  target:t=>{
   const eye=add(ZB,[0,EYE,0]);
   // The far zombie looks toward you as the ring lights up around it, looks up at the ball, then at the blast.
   // While it falls the ball sits near the top of the view, so the ground stays in frame as soon as it can.
   const C=center(clamp(t,T0,TI)),flat=[TGT[0]-ZB[0],0,TGT[2]-ZB[2]],hd=len(flat),dir=norm(flat);
   const elev=Math.max(.12,Math.atan2(C[1]-EYE,hd)-.38),track=add(eye,[dir[0]*Math.cos(elev)*10,Math.sin(elev)*10,dir[2]*Math.cos(elev)*10]);
   const atYou=[0,-1.2,0],blast=[TGT[0],3,TGT[2]];
   const up=smoothstep((t-T0-.55)/.6),down=smoothstep((t-TI+.15)/.35);
   return{eye,look:blend(blend(atYou,track,up),blast,down)};
  },
 },
 draw(m,t,ctx){
  const a=t-TI;
  figures(m,t,ctx);
  castFlare(m,t,ctx);
  warningRing(m,t,ctx);
  ball(m,t,ctx);
  shower(m,t,ctx);
  flash(m,a);
  dome(m,a,ctx);
  shock(m,a,ctx);
  chunks(m,a,ctx);
  aftermath(m,t,a,ctx);
 },
};
