// Reference gallery of the shared primitives in ../kit.js at a few sizes.
import{flame,burst,puff,sparks,smoke,flameRing,cube,figure,EYE}from'../kit.js';

export default {
 id:'kit',name:'Block-fire kit',slot:'Reference',
 duration:4,
 summary:'The shared pieces every fire spell is built from.',
 notes:'Left to right: small, medium and large flame tongues; an ember-heat flame; a looping burst; tumbling puffs at each heat; a ring of flames; rising sparks and smoke.',
 phases:[[0,'Burning']],
 area:{x0:-8,x1:9,z0:-5,z1:6},
 cameras:{orbit:{target:[0,1,0],yaw:0,pitch:.32,distance:12},caster:{eye:[0,EYE,7],look:[0,1,0]}},
 draw(m,t,ctx){
  figure(m,[-7,0,2],-Math.PI/2,{pose:'idle'});
  flame(m,[-6,0,0],[0,1,0],{width:.35,length:.8,seed:11});
  flame(m,[-4.5,0,0],[0,1,0],{width:.6,length:1.5,seed:23});
  flame(m,[-2.5,0,0],[0,1,0],{width:1,length:2.6,seed:37});
  flame(m,[-.5,0,0],[0,1,0],{width:.6,length:1.2,seed:41,heat:.45});
  burst(m,[2,1,0],t%2,{radius:1.6,count:22,seed:5,duration:.9});
  for(let i=0;i<5;i++)puff(m,[4+i*.8,1.2,-2],t,{size:.45,seed:60+i,heat:4-i});
  flameRing(m,[5,0,2],1.5,t,{height:.6,seed:9});
  if(ctx.embers){sparks(m,[-2.5,.3,0],t,{count:12,spread:.6,rise:3,seed:2});for(let i=0;i<3;i++)smoke(m,[-2.5,2.6,0],(t+i*.4)%1.2,{size:.5,seed:i+90})}
 },
};
