// Runtime port: SpringbloomManager and SpringbloomEntity: pad location, launch velocity/start tick, flight player position, removal and failed pod hit. Budget 70 cuboids. 8 s pad, 1.7 b/t launch, .08 gravity/.98 drag; leaf pair every four ticks.
import{common,cameras}from './common.js';
import{piece,burst,heal,cube,figure,BAND}from '../kit.js';
import{flower,model,pad}from '../context.js';
import{add,clamp,lerp,rnd,TAU}from '../engine.js';
import{basis}from '../shared.js';
const heights=[.6875];let y=.6875,v=1.7;for(let i=0;i<85;i++){y+=v;v=(v-.08)*.98;heights.push(Math.max(0,y));}
 const height=a=>{const f=clamp(a*20,0,84),i=Math.floor(f);return lerp(heights[i],heights[Math.min(i+1,85)],f-i);};
 export default{...common,id:'springbloom',name:'Springbloom',duration:9,budget:70,
 summary:'Broad leaves kick away from the pad; a small gold burst marks launch. The rider sheds a pair of leaves every four ticks.',
 notes:'Pad stand-in uses 1.5 block width and .6875 height. Standing drop launch uses zero horizontal velocity, 1.7 vertical. Real pad lifetime 160 ticks. Approved pad unchanged; failed pod landing leaf burst shown at 8.5 s.',
 phases:[[0,'Pad'],[.3,'Launch'],[8,'Pad expires'],[8.5,'Failed pod hit']],
 cameras:{...cameras,orbit:{target:[0,1.2,0],yaw:-1.1,pitch:.3,distance:7},target:{eye:[5,1.62,0],look:[0,4,0]}},
 draw(m,t){if(t<8)pad(m,[0,0,0]);const a=t-.3,h=a<0?.6875:height(a);figure(m,[0,h,0],0,{pose:a>0&&h>0?'air':'idle'});
 burst(m,[0,.8,0],a,{count:10,kind:'pollen',band:'gold',scale:.25,radius:.65});burst(m,[0,.7,0],a,{count:8,kind:'leaf',band:'leaf',scale:.34,radius:.95});
 for(let n=0;n<20;n++)if(height(n*.2)>0)burst(m,[0,height(n*.2)+.15,0],a-n*.2,{count:2,kind:'leaf',band:'leaf',scale:.24,radius:.2,fall:true,duration:.4});
 burst(m,[0,.15,0],t-8,{count:14,kind:'leaf',band:'darkleaf',radius:.65});burst(m,[2,.1,0],t-8.5,{count:6,kind:'leaf',band:'leaf',scale:.23,radius:.2});}};