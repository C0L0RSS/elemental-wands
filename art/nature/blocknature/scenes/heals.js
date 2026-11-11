// Runtime port: ThornLashEntity healed flag and NatureCombat.flowerDamageDealt successful heal event: healed player ID, event tick and amount. Budget 30 cuboids. Pollen contact remains at bite, not on caster.
import{common,cameras}from './common.js';
import{piece,burst,heal,cube,figure,BAND}from '../kit.js';
import{flower,model,pad}from '../context.js';
import{add,clamp,lerp,rnd,TAU}from '../engine.js';
import{basis}from '../shared.js';
export default{...common,id:'heals',name:'Shared heal',duration:3,budget:30,
 summary:'Six sap leaves spiral up around the healed player while four gold kernels climb between them. A warm pollen accent marks the bite contact separately.',
 notes:'Same 0.9 s cue for Thornbite lifesteal and successful flower heal; suppress when health did not rise. Timing is a proposed visual envelope, not a gameplay delay. The approved flytrap is not altered.',
 phases:[[.3,'Thornbite heal'],[1.7,'Flower heal']],draw(m,t){figure(m,[0,0,0],0);heal(m,[0,0,0],t-.3);heal(m,[0,0,0],t-1.7);burst(m,[2,1,0],t-.3,{count:5,kind:'pollen',band:'gold',radius:.2,scale:.17,duration:.35});}};