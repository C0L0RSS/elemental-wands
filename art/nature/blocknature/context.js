// Review-only rendering of unchanged approved vertices and block cuboids. No runtime writes.
import{meshes,bases}from './context-data.js';
import{AXES,basis}from './shared.js';
import{add,mul,faceTone}from './engine.js';
export function model(m,name,p,scale=1,axes=AXES){
 const data=meshes[name];if(!data)throw Error('Unknown context model '+name);
 if(!m.batches.has('white'))m.batches.set('white',[]);const out=m.batches.get('white');
 const transform=v=>add(mul(axes[0],v[0]),add(mul(axes[1],v[1]),mul(axes[2],v[2])));
 for(let i=0;i<data.length;i+=10){const pos=add(p,mul(transform(data.slice(i,i+3)),scale)),n=transform(data.slice(i+3,i+6)),shade=faceTone(n);out.push(...pos,...n,0,0,...data.slice(i+6,i+9).map(v=>v*shade),1,0);}
}
export function flower(m,p,{grown=false,stage=3,wilt=false,aim=true}={}){
 const base=bases[grown?'nature_seedling_grown':'nature_seedling_'+stage];
 for(const e of base.elements){const c=e.faces.up.tintindex,col=[(c>>16&255)/255,(c>>8&255)/255,(c&255)/255,1];
 m.box(add(p,e.from.map((v,i)=>(v+e.to[i])/32-(i===1?0:.5))),e.from.map((v,i)=>(e.to[i]-v)/32),AXES,'white',{color:col});}
 const head=(off,s)=>model(m,wilt?'sentry_head_wilt':'sentry_head',add(p,off),s,basis(aim?[1,wilt?-.23:.25,0]:[0,1,0]));
 if(grown){head([.3125,1.5,0],1);head([-.6875,1.125,.125],.75);}else head([0,.6875,0],Math.max(.12,(stage+1)/4));
}
// Pad stand-in: dimensions match SpringbloomRules. Approved pad itself is unchanged.
export function pad(m,p){for(let i=0;i<5;i++)m.box(add(p,[(i-2)*.3,.34375,0]),[.15,.34375,.75-Math.abs(i-2)*.13],AXES,'white',{color:[.28+i*.025,.48,.19,1]});}
