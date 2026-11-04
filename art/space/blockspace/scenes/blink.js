// Runtime port: SpaceAbilityHandler.castSecondary teleports on the server and today spawns SPACE_RIFT,
// SPACE_IMPLOSION_RING and SPACE_CONSUMPTION at both ends (spawnBlinkFold) and a smaller rift for a failed blink.
// Each becomes one BlockSpaceEvents message (BLINK_FOLD at the departure feet, BLINK_UNFOLD at the arrival feet, with the
// caster's id so their own client can skip the ghost; BLINK_FAIL) drawn by a short client effect. The caster's ghost
// and coalescing sheen are drawn from the message alone; nothing follows the player afterwards. Stars stay with the
// particle setting. Cube budget: about 90 at each end while the slits are open.
import{slit,rift,openness,figure,blocks,lookDir,EYE}from'../kit.js';
import{add,clamp}from'../engine.js';

// ---- Real numbers (SpaceAbilityHandler) --------------------------------------------------------------------------
const RANGE=10;                       // BLINK_RANGE: the farthest safe spot along the aim
const T=.5,FAIL=1.55,DURATION=2.4;
const FROM=[0,0,0],TO=[RANGE,0,0];
const WALL_X=11;                      // the failed blink aims straight into this wall

/** Departure: the caster folds into the slit top-down as void glass while the space around them falls in after. */
function drawFold(m,t,embers){
 const age=t-T;if(age<0)return;
 rift(m,FROM,age,{seed:3,embers});
 const k=age/.2;if(k<1)figure(m,FROM,0,{glass:.95,build:1-k,seed:5});
}

/** Failed blink (no safe spot): a hairline slit flickers half open and shuts; nothing moves. */
function drawFail(m,t){
 const age=t-FAIL;if(age<0||age>.4)return;
 const p=add(TO,[.55,0,0]);
 slit(m,p,{height:1.6,width:.5,open:openness(age,{open:.05,hold:.03,close:.18,peak:.4}),seed:19,halo:false});
}

export default{
 id:'blink',name:'Blink Rift',slot:'Secondary',duration:DURATION,
 summary:'A standing eclipse slit opens at both ends. The caster folds into the first one as void glass while the space around them falls in, then coalesces out of the second as shards spill back out.',
 notes:'Teleports up to 10 blocks along the aim to the farthest safe spot; it leaves no return point, so both slits pinch shut within half a second.\nThe slit always turns to face you around its vertical axis, so it never goes edge-on.\nAt 1.55 s the caster tries again into the wall: with no safe spot the slit only flickers half open (no cooldown is spent).',
 phases:[[0,'Ready'],[T,'Blink · 10 blocks'],[T+.55,'Slits closed'],[FAIL,'No safe spot · fizzle']],
 area:{x0:-4,x1:15,z0:-6,z1:6},budget:240,
 cameras:{
  orbit:{target:[5.5,1.1,0],yaw:.12,pitch:.3,distance:13},
  caster:t=>{const at=t<T?FROM:TO;const eye=add(at,[0,EYE,0]);return{eye,look:add(eye,lookDir(0))}},
  target:{eye:[5,1.7,6.5],look:[5,1.1,0]},
 },
 draw(m,t,{embers}){
  blocks(m,[WALL_X,0,-2],[WALL_X+1,3,3],{tone:.5});
  if(t<T)figure(m,FROM,0,{pose:'cast'});
  else{
   const age=t-T,sheen=clamp(1-age/.35);
   figure(m,TO,0,{pose:t>=FAIL&&t<FAIL+.4?'cast':'idle',glass:sheen*.9,build:clamp(age/.12),seed:21});
  }
  drawFold(m,t,embers);
  rift(m,TO,t-T,{seed:13,embers,out:true,hold:.16});
  drawFail(m,t);
 },
};
