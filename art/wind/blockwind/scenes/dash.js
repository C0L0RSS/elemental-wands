// Runtime port: WaylayDashVfxManager already samples the dash path every tick for TRACE_TICKS; instead of spawning
// slipstream/feather particles it would send one small payload per dash (start position, direction, chained flag)
// and each client draws the launch rings and trail from the player's own interpolated positions (kept for 5 ticks,
// like the Fire flame history). WIND_BURST_RING/WIND_AIR_RIBBON at the cast become cubes; a few motes stay.
// Cube budget: about 120 for a single dash, 200 at the peak of a chained dash.
import{ring,puff,wisp,motes,figure,lookDir,yawAxes,EYE}from'../kit.js';
import{add,sub,mul,norm,len,clamp,lerp,smoothstep}from'../engine.js';

// ---- Real numbers (WindAbilityHandler.castSecondary, WaylayDashVfxManager) ----------------------------------
const TICK=.05,BASE=1.5,CHAINED=1.875;          // 75% impulses: base and second chained dash
const AIR_DRAG=.91,GROUND=.546,AIR_TICKS=6;     // a short slow-falling hop, then ground friction
const TRACE=5*TICK,BODY_Y=.48*1.8;              // the server traces 5 ticks from the body centre
const DASHES=[{t:.35,yaw:0,strength:BASE,chained:false},{t:.9,yaw:.62,strength:CHAINED,chained:true}];
const DURATION=2.8,LIFE=.4;

// Tick-by-tick horizontal motion: each dash adds its impulse to whatever velocity is left.
const PATH=(()=>{
 const out=[[0,0,0]];let p=[0,0,0],v=[0,0,0],air=0;
 for(let n=0;n<DURATION/TICK+2;n++){
  const t=n*TICK;
  for(const d of DASHES)if(Math.abs(d.t-t)<TICK/2){v=add(v,mul(lookDir(d.yaw),d.strength));air=AIR_TICKS}
  p=add(p,v);v=mul(v,air>0?AIR_DRAG:GROUND);air--;out.push(p);
 }
 return out;
})();
/** Feet position at time t (linear between ticks, like client interpolation). */
function feet(t){const f=t/TICK,i=Math.floor(f),k=f-i;return add(mul(PATH[i],1-k),mul(PATH[i+1],k))}
function heading(t){let yaw=0;for(const d of DASHES)if(t>=d.t)yaw=d.yaw;return yaw}

/** Trail sampling: one sample every STEP seconds of the traced path (about 0.3 blocks at full speed). */
const STEP=.01,PUFF_LIFE=.55;
const bodyAt=t=>add(feet(t),[0,BODY_Y,0]);
/** Horizontal right-hand side of a movement direction. */
const sideOf=f=>norm([-f[2],0,f[0]]);

function drawDash(m,t,d,embers){
 const age=t-d.t;if(age<0||age>1.2)return;
 const dir=lookDir(d.yaw),side=yawAxes(d.yaw)[2],start=add(feet(d.t),[0,BODY_Y,0]);
 // Launch: a pressure ring standing behind the body, opening and thinning; a chained dash doubles it.
 const open=smoothstep(age/.35),thin=1-smoothstep(age/.42);
 ring(m,add(start,mul(dir,-.35)),dir,lerp(.45,1.3,open),{count:16,size:.14*thin,phase:age*1.2,seed:d.t*9});
 if(d.chained)ring(m,add(start,mul(dir,-.9)),dir,lerp(.3,.95,open),{count:12,size:.1*thin,band:'streak',alpha:.8,phase:-age*1.6,seed:d.t*9+5});
 // Kicked-up air at the feet, rolling back and out.
 for(let i=0;i<6;i++){const s=i<3?-1:1,j=i%3;
  puff(m,add(feet(d.t),add(mul(side,s*(.25+j*.15)),[0,.18,0])),age-j*.03,{size:.3,seed:d.t*13+i,duration:.6,drift:add(mul(dir,-.7-j*.2),mul(side,s*.6)),lobes:3});}
 // Trail: soft and curved, like a boat's wake (straight lanes joined at angles read as lightning). Samples are born
 // where the body passed and stay put in the world; with age they spread sideways, drift back and thin out.
 const samples=[];
 for(let tb=d.t;tb<=d.t+TRACE+1e-6;tb+=STEP){
  const f=sub(bodyAt(tb+STEP),bodyAt(tb));if(len(f)<.02)continue;
  samples.push({tb,a:t-tb,p:bodyAt(tb),f:norm(f),s:sideOf(f)});
 }
 // Wake puffs: a continuous cloud trail that widens into a V behind you. A chained dash's outer puffs are pale blue.
 samples.forEach((q,i)=>{
  if(q.a<0||q.a>PUFF_LIFE)return;
  const lane=[-1,1,0][i%3],spread=lane?(.15+.95*q.a):0;
  const at=add(add(q.p,mul(q.s,lane*spread)),add(mul(q.f,-1.1*q.a),[0,(lane?.02:.18)-.2*q.a,0]));
  puff(m,at,q.a,{size:(lane?.34:.26)*(d.chained?1.15:1),seed:q.tb*97+i,duration:PUFF_LIFE,drift:mul(q.s,lane*.25),lobes:3,alpha:lane?.55:.45,band:d.chained&&lane?'streak':'cloud'});
 });
 // Short curled swooshes peeling back off each shoulder: curves, never long straight lines.
 samples.forEach((q,i)=>{
  if(i%5!==2)return;
  for(const side of[-1,1])wisp(m,add(add(q.p,mul(q.s,side*.32)),[0,.3,0]),add(mul(q.f,-1),mul(q.s,side*.8)),q.a,
   {seed:q.tb*7+side,duration:LIFE,size:.11,length:.85,curl:2.6,up:mul(q.s,side)});
 });
 if(embers)motes(m,start,age,{count:6+(d.chained?4:0),dir:mul(dir,-1),seed:d.t*3,duration:.5,speed:1.6});
}

export default{
 id:'dash',name:'Waylay Dash',slot:'Secondary',duration:DURATION,
 summary:'A pressure ring snaps open behind you and a soft trail of cloud widens into a V along your path, like a boat\'s wake, with curled swooshes peeling off your shoulders. A chained second dash doubles the ring and tints the outer cloud pale blue.',
 notes:'Real numbers: 1.5 impulse, 1.875 for the chained dash, a short slow-falling hop then ground friction; the server traces the first 5 ticks of each dash.\nThe wake is born where your body passed and stays put in the world: it spreads sideways into a V, drifts back and thins out over about half a second. Everything is curved or puffy; no straight lines meet at angles, so it never reads as lightning.\nThe kicked-up air at your feet rolls back and outward.\nFirst person: the trail is behind you; only the ring edge and side wisps show at the corners.',
 phases:[[0,'Ready'],[.35,'Dash'],[.9,'Chained dash'],[1.5,'Clear']],
 area:{x0:-4,x1:23,z0:-5,z1:12},budget:360,
 cameras:{
  orbit:{target:[9,1,3],yaw:-1.95,pitch:.38,distance:13},
  caster:t=>{const p=add(feet(t),[0,EYE,0]);return{eye:p,look:add(p,add(mul(lookDir(heading(t)),5),[0,-.4,0]))}},
  target:{eye:[15,EYE,10],look:[9,1,2]},
 },
 draw(m,t,{embers}){
  const dashAge=Math.min(...DASHES.map(d=>t>=d.t?t-d.t:9));
  figure(m,feet(t),heading(t),{pose:dashAge<.5?'air':'idle',lean:dashAge<.45?.38*(1-smoothstep(dashAge/.45)):0});
  for(const d of DASHES)drawDash(m,t,d,embers);
 },
};
