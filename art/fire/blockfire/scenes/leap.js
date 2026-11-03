// Runtime port: a new client FireLeapFxRenderer draws everything here from FireLeapPayload (from, to) and the tick it arrives,
// plus the world clock; nothing else is needed for the take-off ring and the comet tail. Flight position is
// FireLeapRules.position(from, to, progress) over FireLeapRules.duration ticks, so every viewer draws the same arc.
// The landing wall needs one more bit of state: today the end packet (active=false) is sent both on a real landing
// and on a cancelled leap, so the server should say which (a "landed" flag or a small wave packet). The wall then
// runs on WAVE_SPEED / WAVE_RANGE / WAVE_HEIGHT with GuardianWaveSurface.ground and .visible per column, exactly
// like the damage. Particles that stay: a few FIRE_EMBER sparks at take-off, in flight and on landing, and a little
// smoke as the wall burns down. FIRE_FLAME_RIBBON at take-off, landing and on the crest is replaced by cubes.
// FireLeapPreview's aim arc and landing mark stay as they are (drawn here as thin amber lines for context).
// Cube budget: about 360 at the landing peak (up to 78 wall columns x 4 cubes, the wake and the landing blast),
// about 70 in flight, 130 at take-off.
import{cube,flame,burst,smoke,sparkBurst,figure,tumble,heatBand,yawAxes,rotate,EYE}from'../kit.js';
import{add,sub,mul,cross,norm,len,clamp,lerp,smoothstep,rnd,noise,TAU,UP}from'../engine.js';

// ---- Real numbers (FireLeapRules, FireLeapManager, FireLeapClient) -----------------------------------------
const TICK=.05;
const RANGE=22,WAVE_RANGE=5,WAVE_SPEED=.65,WAVE_HEIGHT=.6,GRAVITY=.09,CARRY=.35;
const FROM=[0,0,0],TO=[RANGE,1,0];                  // a full-range leap onto ground one block higher
const apex=(a,b)=>clamp(2.2+.2*Math.hypot(b[0]-a[0],b[2]-a[2]),2.5,6.5);
const ARC=(()=>{const peak=Math.max(FROM[1],TO[1])+apex(FROM,TO),up=Math.sqrt(peak-FROM[1]),down=Math.sqrt(peak-TO[1]);
 return[2*up*(up+down),2*(up+down)*(up+down)]})();
const FLIGHT_TICKS=clamp(Math.round(Math.sqrt(ARC[1]/GRAVITY)),12,30); // 25 ticks for this leap
const FLIGHT=FLIGHT_TICKS*TICK;                                         // 1.25 s
const PEAK=Math.max(FROM[1],TO[1])+apex(FROM,TO);                       // 7.5 (6.5 above the landing)
const leapPos=p=>{p=clamp(p);return[lerp(FROM[0],TO[0],p),FROM[1]+ARC[0]*p-ARC[1]*p*p/2,lerp(FROM[2],TO[2],p)]};
const leapDir=p=>norm([TO[0]-FROM[0],ARC[0]-ARC[1]*clamp(p),TO[2]-FROM[2]]);
/** Outer edge of the wave's damage band: (age+1)·0.65 blocks, capped at 5 (FireLeapManager.wave). */
const crest=a=>Math.min(WAVE_RANGE,(a/TICK+1)*WAVE_SPEED);
const STOP=(WAVE_RANGE/WAVE_SPEED-1)*TICK;                // 0.33 s: the crest reaches 5 blocks
const WAVE_END=Math.ceil(WAVE_RANGE/WAVE_SPEED)*TICK;    // 0.40 s: the server stops checking hits
// Landing keeps 35% of the last step as a skid; ground friction (0.6 × 0.91) eats it.
const SKID_STEP=Math.hypot(TO[0]-FROM[0],TO[2]-FROM[2])/FLIGHT_TICKS*CARRY,FRICTION=.546;
const skid=a=>a<=0?0:SKID_STEP*(1-Math.pow(FRICTION,a/TICK))/(1-FRICTION);

// ---- Timeline ---------------------------------------------------------------------------------------------
const T0=.45,TL=T0+FLIGHT,DURATION=3.6;
const progress=t=>clamp((t-T0)/FLIGHT);
/** The caster's feet at time t. */
function caster(t){
 if(t<T0)return FROM;
 if(t<TL)return leapPos(progress(t));
 return add(TO,[skid(t-TL),0,0]);
}

// ---- Terrain: a step up to the landing shelf, plus a raised ledge inside the wave's reach -----------------
function height(x,z){
 const cx=Math.floor(x),cz=Math.floor(z);
 if(cx>=19&&cx<=27&&cz<=-5)return 2;   // one block above the landing, 4 blocks to its left
 return cx>=18?1:0;
}
/** GuardianWaveSurface.ground: the floor under (x, z) within 2 up / 3 down of the origin, else none. */
function waveGround(o,x,z){const h=height(x,z);return h<=o[1]+2&&h>=o[1]-3?[x,h,z]:null}
/** GuardianWaveSurface.visible: a clear line 0.7 above the origin to 0.7 above the surface (cover stops the wave). */
function waveVisible(o,s){
 const a=add(o,[0,.7,0]),b=add(s,[0,.7,0]),d=len(sub(b,a)),n=Math.max(1,Math.ceil(d/.12));
 for(let i=1;i<n;i++){const p=add(a,mul(sub(b,a),i/n));if(p[1]<height(p[0],p[2]))return false}
 return true;
}

// ---- Enemies ---------------------------------------------------------------------------------------------
const ZA=[23.1,1,-.7];   // 1.3 blocks from the landing: inside 1.5, so it takes 8 damage (4 hearts)
const ZB=[25.3,1,1.7];   // 3.8 blocks out: jumps as the caster lands and the wall passes under its feet
/** Vanilla jump (0.42 up, gravity 0.08, drag 0.98) sampled per tick; feet height above the floor. */
function hop(a,v0=.42){
 if(a<=0)return 0;const ticks=a/TICK;let y=0,v=v0,prev=0;
 for(let i=0;i<Math.ceil(ticks);i++){prev=y;y+=v;v=(v-.08)*.98;if(y<=0)return 0}
 return Math.max(0,lerp(prev,y,ticks-Math.floor(ticks)||1));
}
/** Vanilla knockback 0.4 away from the caster: hops 0.4 up, slides with air drag 0.91, ground friction after. */
function knock(a){
 if(a<=0)return[0,0];const ticks=a/TICK;let h=0,v=.4,y=0,vy=.4,prevH=0,prevY=0;
 for(let i=0;i<Math.ceil(ticks);i++){prevH=h;prevY=y;h+=v;y=Math.max(0,y+vy);vy=(vy-.08)*.98;v*=y>0?.91:FRICTION}
 const f=ticks-Math.floor(ticks)||1;return[lerp(prevH,h,f),lerp(prevY,y,f)];
}
const HIT_A=TL+TICK;     // the crest band reaches ZA on the wave's second tick
const faceYaw=(from,to)=>Math.atan2(to[2]-from[2],to[0]-from[0]);
function zombieA(t){
 const[h,y]=knock(t-HIT_A),d=norm([ZA[0]-TO[0],0,ZA[2]-TO[2]]);
 return add(ZA,[d[0]*h,y,d[2]*h]);
}
const HOP=TL+.1;        // the far zombie jumps two ticks after the landing
const zombieB=t=>add(ZB,[0,hop(t-HOP),0]);

// ---- Local helpers ----------------------------------------------------------------------------------------
/** Axes that face along the ground direction a and roll outward by angle (like a cube tumbling across the floor). */
function rolled(a,angle){const k=[Math.sin(a),0,-Math.cos(a)];return yawAxes(a).map(v=>rotate(v,k,angle))}
/** A thin glowing slab between two ground points (FireLeapPreview's line segments). */
function slab(m,p,q,width,color){
 const d=sub(q,p),l=len(d);if(l<1e-3)return;
 const x=norm(d),z=norm(cross(x,UP)),y=cross(z,x);
 m.box(mul(add(p,q),.5),[l/2,.012,width],[x,y,z],'white',{color,emissive:true,tone:false});
}
/** FireLeapPreview.sigil, simplified: broken seal at the 5-block wave range with eight inward flame teeth. */
function sigil(m,o,committed){
 const amber=committed?[.98,.5,.16,1]:[.7,.42,.2,1],gold=committed?[1,.8,.42,1]:[.72,.6,.36,1],w=committed?.04:.027;
 const polar=(a,r)=>[o[0]+Math.cos(a)*r,0,o[2]+Math.sin(a)*r];
 const line=(a,b,width,c)=>{
  const p=waveGround(o,a[0],a[2]),q=waveGround(o,b[0],b[2]);
  if(p&&q&&Math.abs(p[1]-q[1])<1.2&&waveVisible(o,p))slab(m,add(p,[0,.045,0]),add(q,[0,.045,0]),width,c);
 };
 for(let s=0;s<8;s++){
  const c=s*TAU/8;
  for(let i=0;i<5;i++){const a=c-.29+i*.116;line(polar(a,5),polar(a+.116,5),w,amber);if(i>0&&i<4)line(polar(a,4.8),polar(a+.116,4.8),w*.65,gold)}
  const tip=polar(c,4.12);line(polar(c-.06,4.6),tip,w,gold);line(tip,polar(c+.06,4.6),w,gold);
 }
}

/** FireLeapPreview's dashed aim arc: every other of 40 steps, skipping the leg within 2 blocks of the eye. */
function aimArc(m){
 const eye=add(FROM,[0,EYE,0]);let prev=leapPos(0);
 for(let i=1;i<=40;i++){const next=leapPos(i/40);
  if(i%2===0&&len(sub(prev,eye))>2&&len(sub(next,eye))>2)slab(m,prev,next,.012,[1,.76,.42,1]);prev=next}
}

// ---- Take-off: a ring of fire thrown out across the ground from the caster's feet -------------------------
function takeoff(m,age,ctx){
 if(age<0||age>1.6)return;const c=FROM;
 // A low hot flash at the feet that the ring pieces start hidden inside.
 if(age<.18){const k=age/.18,s=Math.pow(Math.sin(k*Math.PI),.6);
  cube(m,add(c,[0,.28,0]),[1.5*s,.55*s,1.5*s],{band:heatBand(4-k*1.2),seed:501,axes:yawAxes(.4+age*3)});
  cube(m,add(c,[0,.22,0]),[1.2*s,.42*s,1.2*s],{band:heatBand(3-k),seed:502,axes:yawAxes(1.2-age*3)});}
 // Short tongues flare around the feet and shrink to nothing as the caster leaves.
 const fl=1-smoothstep(age/.4);
 if(fl>0)for(let j=0;j<7;j++){const a=j/7*TAU+.3,r=.55+.12*rnd(510+j);
  flame(m,add(c,[Math.cos(a)*r,0,Math.sin(a)*r]),[Math.cos(a)*.7,1,Math.sin(a)*.7],{width:.5*fl,length:1.05*fl*(.8+.4*rnd(511+j)),seed:512+j*7,heat:1.15,sway:.04})}
 // The ring: chunky pieces roll outward along the floor, lift a little, cool and shrink away.
 const N=22;
 for(let i=0;i<N;i++){
  const li=.55*(.8+.4*rnd(520+i*1.7)),l=age/li;if(l<=0||l>=1)continue;
  const a=(i+.45*rnd(521+i*2.3))/N*TAU,R=2.4*(.75+.25*rnd(522+i*3.1)),r=.3+R*(1-Math.pow(1-l,2.4));
  const s=.46*(.8+.4*rnd(523+i))*Math.min(1,l*6)*Math.pow(1-l,1.15);
  const p=add(c,[Math.cos(a)*r,.16+s*.35+.5*l*l,Math.sin(a)*r]);
  cube(m,p,s,{band:heatBand(4.3-l*4),seed:524+i,axes:rolled(a,r*2.4)});
 }
 if(ctx.embers){
  sparkBurst(m,add(c,[0,.15,0]),age,{count:16,speed:2.6,up:3.2,life:.75,seed:540});
  for(let i=0;i<5;i++){const a=i/5*TAU+.7;smoke(m,[Math.cos(a)*1.5,.3,Math.sin(a)*1.5],age-.22-rnd(550+i)*.12,{size:.5,rise:1.3,duration:1.1,seed:551+i})}
 }
}

// ---- Flight: a comet tail of block fire behind the body ---------------------------------------------------
// The body moves 18-29 blocks a second, so pieces are shed often enough to touch; each one stays where it
// left the body, drifts up a touch, cools and shrinks to nothing, which keeps the tail about 3-4 blocks long.
const SHED_RATE=110,SHED_LIFE=.16;
function flight(m,t,ctx){
 const first=Math.ceil((Math.max(T0,t-SHED_LIFE))*SHED_RATE),last=Math.floor(Math.min(t,TL)*SHED_RATE);
 for(let k=first;k<=last;k++){
  const te=k/SHED_RATE;if(te<T0||te>TL)continue;
  const age=t-te,l=age/SHED_LIFE;if(l<0||l>=1)continue;
  const p=progress(te),d=leapDir(p),hip=add(leapPos(p),[0,.75,0]);
  const side=norm(cross(d,UP)),up=cross(side,d),j1=(rnd(k*1.31)-.5)*.45,j2=(rnd(k*2.17)-.5)*.45;
  const q=add(add(add(hip,mul(d,-.3)),add(mul(side,j1),mul(up,j2))),[0,.8*age,0]);
  const s=(.3+.16*rnd(k*3.7))*Math.min(1,l*10+.4)*Math.pow(1-l,1.15);
  cube(m,q,s,{band:heatBand(3.6-l*3.2+rnd(k*4.1)*.5),seed:k,axes:tumble(k,age,4)});
  if(k%3===0&&s>.12)cube(m,add(q,mul(side,j1>0?-s*.45:s*.45)),s*.55,{band:heatBand(3.1-l*3),seed:k+.5,axes:tumble(k+.5,age,4)});
 }
 if(t<T0||t>TL+.15)return;
 // The attached tongue: rooted at the hips, it streams back along the arc. It grows in a blink at take-off
 // and shrinks to nothing on touchdown.
 const p=progress(t),k=Math.min(1,(t-T0)/.07)*(1-smoothstep((t-TL+.04)/.14));
 if(k>0){
  const d=leapDir(p),hip=add(caster(t),[0,.8,0]);
  flame(m,add(hip,mul(d,.15)),mul(d,-1),{width:.66*k,length:1.7*k,seed:71,heat:1.2,sway:.05,speed:1.7,count:6});
  flame(m,add(hip,[0,-.45,0]),add(mul(d,-1),[0,-.3,0]),{width:.42*k,length:.9*k,seed:83,heat:1,sway:.05,speed:1.8});
 }
 // Two ember flecks a tick follow the body (FIRE_EMBER in FireLeapManager.tick).
 if(ctx.embers&&t<TL)for(let i=0;i<14;i++){
  const te=t-(i+((t*40)%1))/40,pe=progress(te);if(te<T0)continue;const age=t-te;
  const q=add(leapPos(pe),[(rnd(i*7.7+Math.floor(te*40))-.5)*.5,.4+.25*rnd(i*3.3+Math.floor(te*40))+age*.6-2*age*age,(rnd(i*5.1+Math.floor(te*40))-.5)*.5]);
  cube(m,q,.08*(1-age/.35),{band:heatBand(3.4-age*6),seed:600+i,axes:tumble(600+i,t,6)});
 }
}

// ---- Landing: the knee-high wall of fire rolls out to 5 blocks ---------------------------------------------
const COL_GAP=.4,COL_MIN=14,COL_MAX=78,WAKE_DT=.07,WAKE_LIFE=.18;
/** Golden-angle column order: any first n columns spread evenly round the ring, so new columns grow in
 *  between the old ones as the ring widens and nothing jumps. */
const colAngle=i=>i*2.399963+(rnd(i*1.3+7)-.5)*.08;
/** How many columns the ring has at radius r (one every 0.4 blocks of circumference). */
const colCount=r=>clamp(TAU*r/COL_GAP,COL_MIN,COL_MAX);
/** When column i starts to die: shortly after the crest stops, staggered so the ring gutters out unevenly. */
const dieAt=i=>STOP+.05+.32*rnd(i*3.9);
/** Upright axes facing outward at angle a, turned by jitter and leaning outward by lean radians. */
function leaning(a,jitter,lean){const k=[Math.sin(a),0,-Math.cos(a)];return yawAxes(a+jitter).map(v=>rotate(v,k,lean))}
const crestR=a=>Math.min(WAVE_RANGE,crest(Math.min(a,STOP)))-.21;
function landing(m,t,ctx){
 const a=t-TL;if(a<0||a>2)return;
 // The hot blast at the feet sized to the 1.5-block zone where the wave does double damage.
 burst(m,add(TO,[0,.35,0]),a,{radius:1.5,count:16,seed:400,duration:.42,size:.42,up:.12,heat:1,smoke:ctx.embers});
 if(ctx.embers)sparkBurst(m,add(TO,[0,.2,0]),a,{count:18,speed:3.5,up:3,life:.7,seed:430});
 const rc=crestR(a),cols=colCount(rc),spawn=smoothstep(a/.05),moving=a<STOP;
 // Inside 1.5 blocks the wave deals double damage: the wall burns hotter there.
 const inner=1-smoothstep((rc-1.3)/.4);
 for(let i=0;i<Math.ceil(cols);i++){
  const ang=colAngle(i),dir=[Math.cos(ang),0,Math.sin(ang)];
  // Each column is a short three-tier flame: a hot wide base, a narrower middle and a tip that flickers in
  // length, together the 0.6 hit height, plus one loose piece at a time rising off the tip and shrinking away.
  // Dying columns burn down from the top: tip, then middle, then base shrink to nothing.
  const g=clamp(cols-i)*spawn,burn=3*clamp((a-dieAt(i))/.5),die=burn/3;
  const kt=g*(1-smoothstep(burn)),km=g*(1-smoothstep(burn-1)),kb=g*(1-smoothstep(burn-2));
  if(kb>.01){
   const ri=rc+(rnd(i*2.1+3)-.5)*.16*Math.min(1,rc/2);
   const s=waveGround(TO,TO[0]+dir[0]*ri,TO[2]+dir[2]*ri);
   if(s&&waveVisible(TO,s)){
    const hot=lerp(3.5,4.2,inner)-die*2.4,jit=(rnd(i*4.3)-.5)*.7,lean=(moving?.12:0)+(rnd(i*4.9)-.5)*.16;
    // Near-cubes that overlap like the kit flame (not flat slabs stacked flush), each tier turned a little.
    const ax=j=>leaning(ang,jit+j*.5,lean),up=ax(0)[1],at=h=>add(s,mul(up,h));
    const tall=.9+.2*rnd(i*6.7),w=(.38+.1*rnd(i*3.7+1))*(.92+.16*noise(t*6.3+i*1.7));
    const bh=w*.92*tall*(.92+.16*noise(t*5.1+i*2.9))*kb,mw=w*.72*km,mh=w*.82*tall*(.9+.2*noise(t*6.1+i*3.7))*km;
    const tw=w*.46*kt,th=w*.62*tall*(.55+.9*noise(t*7.7+i*4.3))*kt;  // the tip flickers by length
    const bTop=bh-.08,mTop=bTop+mh-.1;  // base sunk 0.08 into the floor; solid body tops out near 0.6
    cube(m,at(bTop-bh/2),[w*kb,bh,w*.94*kb],{band:heatBand(hot-.8),seed:700+i,axes:ax(0)});
    if(km>.01)cube(m,at(mTop-mh/2),[mw,mh,mw*.94],{band:heatBand(hot-1.3),seed:740+i,axes:ax(1)});
    if(kt>.01){
     const tip=at(mTop-.07+th/2);
     cube(m,tip,[tw,th,tw*.94],{band:heatBand(hot-2.1),seed:780+i,axes:ax(2)});
     const u=((t/.28+rnd(i*5.3))%1+1)%1,rs=w*.4*Math.pow(1-u,.9)*kt;
     if(rs>.02)cube(m,add(tip,add(mul(up,th*.2+.36*u),mul(dir,.06*u))),rs,{band:heatBand(hot-1.9-u*1.6),seed:820+i,axes:leaning(ang,u*1.4+rnd(i*6.1)*3,u*.8)});
    }
   }
  }
  // Smoke curls off a few columns as they gutter out (an accent; lit by the scene, not glowing).
  if(ctx.embers&&i%6===0){
   const r=crestR(STOP),s=waveGround(TO,TO[0]+dir[0]*r,TO[2]+dir[2]*r);
   if(s&&waveVisible(TO,s))smoke(m,add(s,[0,.35,0]),a-dieAt(i)-.12,{size:.3,rise:1.1,duration:.9,seed:880+i});
  }
  // The wake: pieces the crest leaves on the ground behind it, rolling to a stop as they cool and shrink.
  if(i%2)continue;
  for(let n=0;n*WAKE_DT<=STOP;n++){
   const te=(n+rnd(i*8.9+n*.37))*WAKE_DT;if(te>STOP||te>a||i>=colCount(crestR(te)))continue;
   const age=a-te,l=age/WAKE_LIFE;if(l>=1)continue;
   const r=crestR(te)-.25,s=waveGround(TO,TO[0]+dir[0]*r,TO[2]+dir[2]*r);
   if(!s||!waveVisible(TO,s))continue;
   const sz=(.24+.08*rnd(i*9.7+n))*Math.pow(1-l,1.2);
   cube(m,add(s,[dir[0]*.4*l,.12+.28*l,dir[2]*.4*l]),sz,{band:heatBand(3.3-l*3.2),seed:900+i*7+n,axes:rolled(ang,l*2.5+n)});
  }
 }
}

// ---- Figures ----------------------------------------------------------------------------------------------
function figures(m,t,ctx){
 const p=caster(t);
 if(ctx.cam!=='caster'){
  if(t<T0)figure(m,p,0,{pose:'cast',pitch:-.05});
  else if(t<TL)figure(m,p,0,{pose:'air'});
  else figure(m,p,0,{pose:t-TL<.22?'crouch':'idle'});
 }
 const a=zombieA(t),hurtA=t>=HIT_A?1-smoothstep((t-HIT_A-.4)/.1):0;
 figure(m,a,faceYaw(a,p),{kind:'zombie',hurt:hurtA});
 if(ctx.cam!=='target'){const b=zombieB(t);figure(m,b,faceYaw(b,p),{kind:'zombie'})}
}

export default {
 id:'leap',name:'Fire Leap',slot:'Secondary',
 duration:DURATION,
 budget:450,
 summary:`Hold to aim, release to leap. Fire bursts out in a ring from your feet as you launch, a short comet tail of block fire streams behind you through the air, and when you land a knee-high wall of fire rolls ${WAVE_RANGE} blocks out over the ground and then burns down. Jump it and it misses you.`,
 notes:[
  `This is a full-range leap: ${RANGE} blocks forward onto ground one block higher. You peak ${PEAK} blocks up (${PEAK-TO[1]} above where you land) and the flight takes ${FLIGHT_TICKS} ticks, ${FLIGHT.toFixed(2)} seconds, on the same arc the game uses.`,
  'Take-off: a flash at your feet and a ring of fire cubes thrown about 2 1/2 blocks out across the ground. It is a send-off only; it does no damage.',
  'In the air, pieces peel off your body where you were, cool from yellow to dark red and shrink away, so the tail stays about 3 to 4 blocks long however fast you are moving.',
  `The landing wall is the real hit height, ${WAVE_HEIGHT} of a block (about knee-high); only the loose flickering tips go higher. Anything standing in it is hit once. If your feet are higher than that when it passes, it misses you, so it can be jumped.`,
  `It is fast: ${WAVE_SPEED} blocks a tick (13 blocks a second), so it reaches the full ${WAVE_RANGE} blocks in about a third of a second. The server stops checking hits after ${Math.ceil(WAVE_RANGE/WAVE_SPEED)} ticks (${WAVE_END.toFixed(1)} s). After that the flames only burn down, tips first, and are gone about a second after you land.`,
  'Closer hurts more: within 1.5 blocks of the landing it deals 8 damage (4 hearts), further out 4 (2 hearts); both grow with your Fire level. The wall burns brighter inside that zone and the landing blast covers it. The near zombie is 1.3 blocks away, so it takes the 8 and is knocked back.',
  `The far zombie is ${len(sub(ZB,TO)).toFixed(1)} blocks out. It jumps two ticks after you land and is about a block up when the wall reaches it, so it takes nothing. Press 3 to watch from its eyes.`,
  'The wall follows the ground with the same rules as the damage: it climbs the one-block ledge on your left, and it stops at the drop-off behind the landing because the edge blocks its line.',
  'The thin amber arc and ring are the existing aim preview and landing mark, unchanged and drawn here for context. Only you see the arc while aiming; everyone sees the ring from take-off until you touch down.',
  `Cooldown: 6 seconds. You skid about ${skid(5).toFixed(1)} of a block on landing (you keep 35% of your flight speed), as in game.`,
 ].join('\n'),
 phases:[[0,'Aiming'],[T0,'Take-off ring'],[T0+.2,'Comet tail in flight'],[TL,'Landing: the wall rolls out'],[TL+WAVE_END,'Wall dies down'],[TL+1.25,'Landed (6 s cooldown)']],
 area:{x0:-5,x1:31,z0:-9,z1:9},
 terrain:height,
 cameras:{
  orbit:{target:[15.5,1.8,0],yaw:.62,pitch:.3,distance:18},
  caster:t=>{
   const p=caster(t),eye=add(p,[0,EYE-(t>TL&&t-TL<.22?.3*Math.sin((t-TL)/.22*Math.PI):0),0]);
   // Aim at the landing, then look down at it on the way in and while the wall rolls out.
   const pitch=t<T0?-.08:t<TL?lerp(-.08,-.4,smoothstep(progress(t)*1.15-.15)):-.4;
   return{eye,look:add(eye,[Math.cos(pitch)*10,Math.sin(pitch)*10,.4])};
  },
  target:t=>{
   const eye=add(zombieB(t),[0,EYE,0]);
   // The far zombie watches the caster come in, then the landing spot as the wall rolls at it.
   const watch=add(caster(Math.min(t,TL)),[0,.9,0]),settle=add(TO,[.4,.2,.2]);
   return{eye,look:t<TL?watch:add(mul(watch,1-smoothstep((t-TL)/.2)),mul(settle,smoothstep((t-TL)/.2)))};
  },
 },
 draw(m,t,ctx){
  figures(m,t,ctx);
  // Aim preview (caster only) while aiming; the landing mark is everyone's from take-off until the end packet
  // at touchdown removes it (FireLeapClient).
  if(t<T0&&ctx.cam==='caster'){sigil(m,TO,false);aimArc(m,ctx)}
  else if(t>=T0&&t<TL)sigil(m,TO,true);
  takeoff(m,t-T0,ctx);
  flight(m,t,ctx);
  landing(m,t,ctx);
 },
};
