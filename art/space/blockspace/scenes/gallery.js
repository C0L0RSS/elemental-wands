// Primitive gallery: every atlas band on a solid and a see-through piece, then the shared void-glass shapes looping.
import{piece,core,ring,inflow,spill,stars,slit,shell,chase,astralOrb,gravityBomb,BAND,yawAxes}from'../kit.js';
import{TAU}from'../engine.js';

const LOOP=2.4;
export default{
 id:'gallery',name:'Kit gallery',slot:'Reference',duration:LOOP,
 summary:'The eight atlas bands, each as a solid piece (back row) and a see-through piece (front row), and the shared void-glass shapes.',
 notes:'Bands left to right: void, glass, nebula, rim, amethyst, blue, red, starlight. Glass and nebula are always see-through.\nShapes, middle row: eclipse core with its corona, broken orbit, inward stream, outward spill, stars, the Blink slit.\nFront row: a glass shell, a pulled wake, the Astral orb and the Gravity Bomb.',
 phases:[[0,'Loop']],area:{x0:-7,x1:8,z0:-6,z1:5},budget:700,
 cameras:{orbit:{target:[0,1.2,0],yaw:-.15,pitch:.34,distance:10.5}},
 draw(m,t,{embers}){
  Object.keys(BAND).forEach((band,i)=>{
   const x=-5+i*1.4;
   piece(m,[x,.9,-4.2],.8,{band,seed:i,axes:yawAxes(.5),alpha:1});
   piece(m,[x,.9,-2.8],.8,{band,seed:i+9,axes:yawAxes(.5),alpha:.7});
  });
  const k=(t%LOOP)/LOOP;
  core(m,[-5,1.3,0],.32,{seed:1});
  ring(m,[-3,1.3,0],[.3,1,.2],.55,{count:9,size:.07,gaps:.3,phase:t*3,seed:2});
  core(m,[-3,1.3,0],.12,{seed:3,corona:0});
  inflow(m,[-.8,1.3,0],t,{count:12,radius:1.1,period:1.2,seed:4,size:.2});
  spill(m,[1.4,1.3,0],t%1.2,{count:10,speed:3,duration:.8,seed:5});
  if(embers)stars(m,[3.2,1.3,0],t,{count:10,spread:.9,duration:1.2,seed:6});
  slit(m,[5.2,0,0],{height:2.2,width:.8,open:Math.sin(Math.PI*k)});
  shell(m,[-4.5,1.4,2.6],.9,{count:20,size:.42,seed:7});
  core(m,[-4.5,1.4,2.6],.35,{seed:8});
  const head=s=>[-1.6+((s*3)%4),1.3,2.6+.3*Math.sin(s*TAU)];
  chase(m,head,t,{from:0,every:.05,duration:.4,seed:9});
  core(m,head(t),.16,{seed:10});
  astralOrb(m,[2.6,1.3,2.6],t,{scale:1.6});
  gravityBomb(m,[4.8,1.3,2.6],t,{scale:1.6});
 },
};
