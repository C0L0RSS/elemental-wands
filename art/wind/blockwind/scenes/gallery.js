// Primitive gallery: every atlas band on a solid and a see-through cube, then the shared shapes from kit.js looping.
import{cube,puff,blade,ring,wisp,spiral,streak,motes,BAND,yawAxes}from'../kit.js';
import{TAU}from'../engine.js';

const LOOP=2.4;
export default{
 id:'gallery',name:'Kit gallery',slot:'Reference',duration:LOOP,
 summary:'The eight atlas bands, each as a solid cube (front) and a see-through air cube (back), and the shared wind shapes.',
 notes:'Bands left to right: gale, cloud, mist, streak, swirl, feather, pearl, silver.\nShapes: puff, crescent blade, pressure ring, curled wisp, rising spiral, speed streaks, motes.',
 phases:[[0,'Loop']],area:{x0:-6,x1:7,z0:-4,z1:5},budget:900,
 cameras:{orbit:{target:[0,1.2,0],yaw:-.25,pitch:.3,distance:10}},
 draw(m,t,{embers}){
  Object.keys(BAND).forEach((band,i)=>{
   const x=-5+i*1.4;
   cube(m,[x,.9,2.5],.8,{band,seed:i,axes:yawAxes(.5),alpha:1});
   cube(m,[x,.9,.9],.8,{band,seed:i+9,axes:yawAxes(.5)});
  });
  const k=(t%LOOP)/LOOP;
  puff(m,[-4.6,1.2,-1.8],t%1.2,{size:.6,seed:3,duration:1.2,drift:[0,.4,0]});
  blade(m,[-2.2,1.4,-1.8],[0,0,1],[0,1,0],{k:Math.max(0,k*2-1),seed:4});
  ring(m,[0,1.3,-1.8],[0,0,1],.3+k*.8,{count:14,size:.1*(1-k)+.02,alpha:.8,phase:t});
  if(embers)wisp(m,[1.6,1,-1.8],[1,.4,0],t%.8,{duration:.8,size:.12,length:.8,seed:2});
  spiral(m,[3.6,0,-1.8],{height:2.6,radius:.55,phase:t*3,k:Math.max(0,k*2-1)});
  for(let i=0;i<4;i++){const y=.6+i*.4,x0=-5+((t*4+i*.7)%3);streak(m,[x0,y,-3.4],[x0+1.4,y,-3.4],{width:.07,seed:i})}
  if(embers)motes(m,[5.4,1.2,-1.8],t%.8,{duration:.8,seed:5});
 },
};
