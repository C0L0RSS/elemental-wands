// Runtime port: SeedProjectileEntity: interpolated position, velocity, fromFlower, spawn/hit tick and collision kind. Keep seed and mark models. Budget 50 cuboids. Player 1.5 b/t, .03 gravity/.99 drag; flower 1.4 b/t straight, half scale.
import{common,cameras}from './common.js';
import{piece,burst,heal,cube,figure,BAND}from '../kit.js';
import{flower,model,pad}from '../context.js';
import{add,clamp,lerp,rnd,TAU}from '../engine.js';
import{basis}from '../shared.js';
function position(a,flowerSeed){let p=[0,1.62,0],v=[1.5,0,0];if(flowerSeed)return[28*a,1.0,0];let ticks=a*20;
 for(let i=0;i<Math.floor(ticks);i++){v=[v[0]*.99,(v[1]-.03)*.99,0];p=add(p,v);}const v2=[v[0]*.99,(v[1]-.03)*.99,0];return add(p,v2.map(v=>v*(ticks%1)));}
 export default{...common,id:'seed',name:'Winged seed',duration:4,budget:50,
 summary:'A chunky trail of tumbling gold kernels and leaves follows the approved seed. The impact opens into a few thick petal flakes.',
 notes:'Player flight at 0.3 s; sample target contact at 0.55. Flower seed at 1.8, contact 2.05. Block impact variant at 3.0. Player max lifetime 5 s; flower 1.5 s. Sample contacts shorten flight without changing speed.',
 phases:[[.3,'Player seed'],[.55,'Player hit'],[1.8,'Flower seed'],[2.05,'Flower hit'],[3,'Block hit']],
 cameras:{...cameras,orbit:{target:[4,1,0],yaw:.15,pitch:.22,distance:5}},
 draw(m,t){figure(m,[7,0,0],Math.PI,{kind:'zombie'});
 for(const [start,small]of [[.3,false],[1.8,true]]){const age=t-start,hit=position(.25,small);
 if(age>=0&&age<.25)model(m,'seed',position(age,small),small?.5:1,basis([0,1,0],t*3.8));
 // Two kernels per tick of flight (a leaf each tick for the player's seed), each shrinking over .3 s.
 for(let j=0;j<10;j++){const born=j*.025,a=age-born;if(born<.25&&a>=0&&a<.3){const p=position(born,small),k=1-a/.3;piece(m,add(p,[0,-.1*a,0]),(small?.17:.25)*k,'pollen','gold',j,a);if(!small&&j%2===0)piece(m,add(p,[0,-.15-.2*a,0]),.3*k,'leaf','leaf',j,a);}}
 burst(m,hit,age-.25,{count:small?5:12,scale:small?.2:.32,radius:small?.3:.65});}
 burst(m,[4,.12,0],t-3,{count:6,kind:'pollen',band:'gold',radius:.4});burst(m,[4,.12,0],t-3,{count:4,kind:'leaf',band:'leaf',scale:.24,radius:.4});
 }};