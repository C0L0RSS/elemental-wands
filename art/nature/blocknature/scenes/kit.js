// Runtime port: BlockNatureKit atlas/piece inspection only; fixed time and seed. Budget 100 cuboids.
import{common,cameras}from './common.js';
import{piece,burst,heal,cube,figure,BAND}from '../kit.js';
import{flower,model,pad}from '../context.js';
import{add,clamp,lerp,rnd,TAU}from '../engine.js';
import{basis}from '../shared.js';
export default{...common,id:'kit',name:'Nature material kit',duration:3,budget:100,
 summary:'Eight materials above; stepped petal, three-box leaf, pollen kernel and four-box sap ribbon below.',
 notes:'Left to right: leaf, dark leaf, pink, violet, gold, bark, sap, cream. Four columns per material are the exact atlas frames. No camera-facing geometry.',
 cameras:{...cameras,orbit:{target:[0,1,0],yaw:.12,pitch:.22,distance:5}},
 draw(m,t){Object.keys(BAND).forEach((band,i)=>{for(let f=0;f<4;f++)cube(m,[(i-3.5)*.65,1.3+f*.27,0],.22,{band,frame:f});});
 ['petal','leaf','pollen','ribbon'].forEach((kind,i)=>piece(m,[(i-1.5)*1.2,.55,0],.65,kind,['pink','leaf','gold','sap'][i],i,t));}};