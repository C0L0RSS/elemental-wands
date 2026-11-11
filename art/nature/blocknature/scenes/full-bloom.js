// Runtime port: FullBloomManager: broadcast origin/start tick, caster ID and blessed IDs. 240 ticks active, 12 tick wave. Budget 260 cuboids; grass/flowers stay vanilla blocks.
import{common,cameras}from './common.js';
import{piece,burst,heal,cube,figure,BAND}from '../kit.js';
import{flower,model,pad}from '../context.js';
import{add,clamp,lerp,rnd,TAU}from '../engine.js';
import{basis}from '../shared.js';
export default{...common,id:'full-bloom',name:'Full Bloom',slot:'Ultimate',duration:12.9,budget:260,
 summary:'A low, open wreath of petals rolls to ten blocks in twelve ticks. Sap leaves lift around blessed allies immediately.',
 notes:'Cast at 0.3 s. Radius 10 at 0.9 s. Blessing is immediate, not granted on wave contact. Three caster petals every 0.4 s until 12.3 s. Vanilla grass growth omitted to isolate the effect.',
 phases:[[0,'Ready'],[.3,'Blessing + cast'],[.9,'Wave reaches radius 10'],[1.6,'Caster drift'],[12.3,'Blessing ends']],
 cameras:{...cameras,orbit:{target:[0,.8,0],yaw:-1.05,pitch:.55,distance:16},caster:{eye:[0,1.62,0],look:[9,.7,0]}},
 draw(m,t,{embers}){const age=t-.3;figure(m,[0,0,0],0,{pose:'cast'});figure(m,[5,0,1],Math.PI);figure(m,[-3,0,4],0);
 for(const p of [[0,0,0],[5,0,1],[-3,0,4]])heal(m,p,age);
 burst(m,[0,1,0],age,{count:14,radius:1.3,scale:.4});
 if(age>=0&&age<1.2){const r=10*clamp(age/.6),keep=1-clamp((age-.6)/.6);
 for(let i=0;i<48;i++){const a=i*TAU/48;
 piece(m,[Math.cos(a)*r,.35+rnd(i)*.9,Math.sin(a)*r],(.35+rnd(i+7)*.13)*keep,'petal',i%3?'pink':'violet',i,age);
 if(i%3===0)piece(m,[Math.cos(a)*r,.2,Math.sin(a)*r],.38*keep,'leaf','leaf',i,age);
 if(embers&&i%6===0)piece(m,[Math.cos(a)*r,.8,Math.sin(a)*r],.22*keep,'pollen','gold',i,age);}}
 for(let n=1;n<30;n++)burst(m,[0,1.08,0],age-n*.4,{count:3,scale:.23,radius:.48,duration:.75,seed:n,fall:true});
 }};