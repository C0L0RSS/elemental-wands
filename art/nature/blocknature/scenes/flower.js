// Runtime port: FlowerTurretManager/SentryBloomEntity: plant/start tick, stage, mounts, shot counter, aim, wilt and growth events. Muzzle at pivot + direction*.53125*scale. Budget 72 cuboids.
import{common,cameras}from './common.js';
import{piece,burst,heal,cube,figure,BAND}from '../kit.js';
import{flower,model,pad}from '../context.js';
import{add,clamp,lerp,rnd,TAU}from '../engine.js';
import{basis}from '../shared.js';
export default{...common,id:'flower',name:'Sentry Bloom',duration:39,budget:72,
 summary:'Petals peel open at planting; a compact gold-and-pink puff leaves the mouth. Full Bloom raises the second head.',
 notes:'Plant 0.3; first shot 0.9, then every 2 s. Growth at 2.3 freezes flower age for 12 s; returns to single at 14.3. Natural wilt at 34.3; removal 36.3. Owner heal is shown on successful hits. The model vertices and block geometry are copied unchanged.',
 phases:[[.3,'Plant / 12 tick unfurl'],[.9,'Mouth shot'],[2.3,'Two heads / Full Bloom'],[14.3,'Return to single'],[34.3,'Wilt'],[36.3,'Removal']],
 cameras:{...cameras,orbit:{target:[.4,.9,0],yaw:.8,pitch:.28,distance:4},caster:{eye:[-1.8,1.62,1],look:[.6,.9,0]}},
 draw(m,t){const age=t-.3,grown=t>=2.3&&t<14.3,wilt=t>=34.3;
 if(age>=0&&t<36.3)flower(m,[0,0,0],{stage:Math.min(3,Math.floor(age/.2)),grown,wilt});
 burst(m,[0,.7,0],age,{count:8,kind:'leaf',band:'leaf',scale:.28,radius:.65});
 burst(m,[0,1,0],t-2.3,{count:12,scale:.32,radius:.9});
 burst(m,[0,.9,0],t-14.3,{count:8,fall:true});
 burst(m,[0,.5,0],t-36.3,{count:10,fall:true});
 figure(m,[-1.2,0,2],0);for(let at=.9;at<34.3;at+=2)heal(m,[-1.2,0,2],t-at-.25);
 if(wilt)burst(m,[0,.8,0],t-34.3,{count:6,band:'cream',fall:true,duration:2});
 const shot=(at,p,s=1)=>{const a=t-at,dir=[.9701425,.2425356,0],muzzle=add(p,dir.map(v=>v*.53125*s));
 burst(m,muzzle,a,{count:4,kind:'pollen',band:'gold',scale:.19,radius:.18,duration:.3});
 burst(m,add(muzzle,[0,.1,0]),a,{count:2,scale:.22,radius:.2,duration:.35});
 if(a>=0&&a<.25)model(m,'seed',add(muzzle,dir.map(v=>v*28*a)),.5);
 };
 for(let at=.9;at<34.3;at+=2){shot(at,grown?[.3125,1.5,0]:[0,.6875,0]);if(grown)shot(at+1,[-.6875,1.125,.125],.75);}
 }};