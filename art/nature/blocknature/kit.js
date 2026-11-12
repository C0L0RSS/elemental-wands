// Opaque, world-oriented Nature pieces. Deterministic in time and seed; disappearance is scale only.
import{ATLAS,add,mul,rnd,TAU,smoothstep}from './engine.js';
import{AXES,basis}from './shared.js';
export{figure,terrain,EYE}from './shared.js';
export const BAND={leaf:0,darkleaf:1,pink:2,violet:3,gold:4,bark:5,sap:6,cream:7};
export function cube(m,p,size,{band='leaf',seed=0,axes=AXES,frame=null}={}){
 const s=typeof size==='number'?[size,size,size]:size;
 m.box(p,mul(s,.5),axes,ATLAS.key,{cell:[frame??Math.floor(m.time*8+rnd(seed)*4)%4,BAND[band]]});
}
export function piece(m,p,size=.3,kind='petal',band='pink',seed=0,age=0){
 const axes=basis([Math.sin(seed+age*2),1,Math.cos(seed*.7+age)],seed+age*2);
 const box=(offset,extent)=>cube(m,add(p,add(mul(axes[0],offset[0]*size),add(mul(axes[1],offset[1]*size),mul(axes[2],offset[2]*size)))),extent.map(v=>v*size),{band,seed,axes});
 if(kind==='pollen')box([0,0,0],[.5,.5,.5]);
 else if(kind==='leaf'){box([0,0,0],[.65,.18,1]);box([0,0,.58],[.38,.18,.3]);box([0,.1,-.1],[.12,.12,.7]);}
 else if(kind==='ribbon')for(let i=0;i<4;i++)box([Math.sin(i*.8)*.2,i*.3,0],[.2,.36,.18]);
 else{box([0,0,0],[.8,.2,1]);box([0,.04,.57],[.48,.16,.22]);}
}
export function burst(m,p,age,{count=10,seed=0,scale=.3,radius=.8,duration=.7,kind='petal',band='pink',fall=false}={}){
 if(age<0||age>=duration)return;
 const k=age/duration,keep=1-smoothstep((k-.35)/.65);
 for(let i=0;i<count;i++){const a=i*2.39996+seed,r=radius*(.2+Math.sin(k*Math.PI/2))*(.65+.35*rnd(i+seed));
 piece(m,add(p,[Math.cos(a)*r,(fall?-k*.6:Math.sin(k*Math.PI)*.45)+rnd(i+seed)*.15,Math.sin(a)*r]),scale*keep,kind,band,seed+i,age);}
}
// Six sap leaves spiral up around the body with four gold kernels climbing faster between them; the face stays open.
export const HEAL_TIME=.9;
export function heal(m,p,age){
 if(age<0||age>=HEAL_TIME)return;
 const keep=(1-smoothstep((age-.45)/.45))*smoothstep(age/.1);
 for(let i=0;i<6;i++){const a=i*TAU/6+age*1.6,r=.62+age*.13;
 piece(m,add(p,[Math.cos(a)*r,.45+age*1.5+(i%2)*.2,Math.sin(a)*r]),.34*keep,'leaf','sap',i,age);}
 for(let i=0;i<4;i++){const a=(i+.5)*TAU/4-age*2.2,r=.5;
 piece(m,add(p,[Math.cos(a)*r,.8+age*2,Math.sin(a)*r]),.19*keep,'pollen','gold',i+11,age);}
}
