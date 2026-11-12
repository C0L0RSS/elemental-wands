import{add,sub,mul,dot,cross,norm,len,clamp,lerp,smoothstep,rnd,TAU,UP}from './engine.js';
import{cube}from './kit.js';
export const AXES=[[1,0,0],[0,1,0],[0,0,1]];
export const easeOut=k=>1-(1-clamp(k))**2;

/** Right-handed basis whose second axis is dir; roll spins it around dir. */
export function basis(dir,roll=0){
 const y=norm(dir),ref=Math.abs(y[1])>.92?[1,0,0]:UP;
 let x=norm(cross(y,ref)),z=cross(x,y);
 if(roll){const c=Math.cos(roll),s=Math.sin(roll);[x,z]=[add(mul(x,c),mul(z,s)),sub(mul(z,c),mul(x,s))]}
 return[x,y,z];
}
/** Basis whose first axis runs along dir (texture u follows the flow on the long faces); second axis leans toward up. */
export function flowAxes(dir,up=UP,roll=0){
 const x=norm(dir);let ref=sub(up,mul(x,dot(up,x)));if(len(ref)<1e-3)ref=Math.abs(x[0])<.9?[1,0,0]:[0,0,1];
 let y=norm(sub(ref,mul(x,dot(ref,x)))),z=cross(x,y);
 if(roll){const c=Math.cos(roll),s=Math.sin(roll);[y,z]=[add(mul(y,c),mul(z,s)),sub(mul(z,c),mul(y,s))]}
 return[x,y,z];
}
/** Basis turned around the vertical axis by yaw (radians). Axis 0 points along [cos, 0, sin]; axis 2 is the right hand. */
export function yawAxes(yaw){const f=[Math.cos(yaw),0,Math.sin(yaw)];return[f,UP,cross(f,UP)]}
/** Rodrigues rotation of v around unit axis k by angle a. */
export function rotate(v,k,a){const c=Math.cos(a),s=Math.sin(a);return add(add(mul(v,c),mul(cross(k,v),s)),mul(k,dot(k,v)*(1-c)))}
/** Even directions on a sphere (golden spiral). */
export function sphereDir(i,count){const y=1-2*(i+.5)/count,r=Math.sqrt(Math.max(0,1-y*y)),a=i*2.399963;return[Math.cos(a)*r,y,Math.sin(a)*r]}

export const EYE=1.62;
export function lookDir(yaw,pitch=0){return[Math.cos(yaw)*Math.cos(pitch),Math.sin(pitch),Math.sin(yaw)*Math.cos(pitch)]}
/** The mod's held-wand tip estimate (SpellCastVisuals): ahead of the eye, toward the casting hand, a little low. */
export function wandTip(pos,yaw,pitch=0){
 const look=lookDir(yaw,pitch),right=[-Math.sin(yaw),0,Math.cos(yaw)],up=cross(right,look);
 return add(add(pos,[0,EYE,0]),add(mul(look,.75),add(mul(right,.38),mul(up,-.32))));
}

const SKIN={player:{head:[.78,.58,.44],body:[.18,.55,.62],legs:[.24,.22,.55],arm:[.78,.58,.44],sleeve:[.18,.55,.62]},
 zombie:{head:[.36,.6,.33],body:[.18,.55,.6],legs:[.26,.25,.56],arm:[.36,.6,.33],sleeve:[.36,.6,.33]}};

/**
 * A blocky humanoid standing at pos (feet) facing yaw. pose: 'idle' | 'cast' (right arm aimed) | 'air' (legs apart).
 * lean tips the whole body forward (radians) for dashes and dives. hurt 0..1 tints it red like a hit mob.
 * Seen from its own eyes the body vanishes in the near-eye clearance; the tip cube is left off there.
 */
export function figure(m,pos,yaw,{kind='player',pose='idle',pitch=0,hurt=0,lean=0,wand=kind==='player'}={}){
 const c=SKIN[kind],tint=v=>[lerp(v[0],1,hurt*.6),lerp(v[1],.15,hurt*.7),lerp(v[2],.15,hurt*.7),1];
 let axes=yawAxes(yaw);const[f0,,r0]=axes;
 const hip=add(pos,[0,.75,0]);
 if(lean)axes=axes.map(v=>rotate(v,r0,-lean));
 const[f,u,r]=axes;
 const self=!!m.clearance&&len(sub(add(pos,[0,EYE,0]),m.eye))<.5;
 const at=(fw,y,rt)=>add(hip,add(mul(f,fw),add(mul(u,y-.75),mul(r,rt))));
 const air=pose==='air';
 const part=(p,size,col,ax=axes)=>m.box(p,mul(size,.5),ax,'white',{color:tint(col)});
 for(const s of[-1,1]){
  const swing=air?.5*s:0,legAx=[rotate(f,r,-swing),rotate(u,r,-swing),r];
  part(add(hip,mul(legAx[1],-.375)).map((v,i)=>v+r[i]*s*.125),[.25,.75,.25],c.legs,legAx);
 }
 const bodyY=1.125;
 part(at(0,bodyY,0),[.25,.75,.5],c.body);
 part(at(0,bodyY+.625,0),[.5,.5,.5],c.head);
 if(kind==='zombie'){for(const s of[-1,1])part(at(.375,bodyY+.25,s*.375),[.75,.25,.25],c.arm);return}
 part(at(0,bodyY,-.375),[.25,.75,.25],c.sleeve);
 const shoulder=at(0,bodyY+.3,.375);
 if(pose==='cast'||air){
  const tip=pose==='cast'?wandTip(pos,yaw,pitch):add(shoulder,mul(norm(add(f,mul(u,.6))),.9));
  const toTip=sub(tip,shoulder),aim=norm(toTip),reach=len(toTip),armLen=Math.min(.62,reach*.6);
  part(add(shoulder,mul(aim,armLen/2)),[.25,armLen,.25],c.sleeve,basis(aim));
  if(wand){const hand=add(shoulder,mul(aim,armLen)),wl=Math.max(.15,reach-armLen);
   part(add(hand,mul(aim,wl/2)),[.07,wl,.07],[.42,.28,.16],basis(aim));
   if(!self)cube(m,tip,.11,{band:'sap',seed:3,axes:basis(aim,Math.PI/4),alpha:1});}
 }else part(at(0,bodyY,.375),[.25,.75,.25],c.sleeve);
}
/** The body's local frame for attachments (wings): shoulder-blade centre and the leaned axes. */
export function torso(pos,yaw,lean=0){
 let axes=yawAxes(yaw);const r0=axes[2];if(lean)axes=axes.map(v=>rotate(v,r0,-lean));
 const hip=add(pos,[0,.75,0]);return{back:add(hip,add(mul(axes[1],.62),mul(axes[0],-.16))),axes};
}

/** Plain stone blocks filling the unit cells from min to max (exclusive). */
export function blocks(m,min,max,{tone=.5,seed=0}={}){
 for(let x=min[0];x<max[0];x++)for(let y=min[1];y<max[1];y++)for(let z=min[2];z<max[2];z++){
  const g=tone+.1*rnd(seed+x*7.1+y*3.3+z*1.9);
  m.box([x+.5,y+.5,z+.5],[.5,.5,.5],AXES,'white',{color:[g,g*.99,g*1.03,1]});
 }
}

/** Block terrain from a column-height function (integer heights; grass tops, dirt sides). */
export function terrain(m,area,height=()=>0){
 for(let x=area.x0;x<area.x1;x++)for(let z=area.z0;z<area.z1;z++){
  const h=height(x+.5,z+.5);
  const checker=((x+z)%2===0)?1:.94;
  m.quad([[x,h,z+1],[x+1,h,z+1],[x+1,h,z],[x,h,z]],'grass',{color:[checker,checker,checker,1]});
  for(const[dx,dz]of[[1,0],[-1,0],[0,1],[0,-1]]){
   const nh=height(x+.5+dx,z+.5+dz);if(nh>=h)continue;
   for(let y=nh;y<h;y++){
    const x0=dx===1?x+1:x,z0=dz===1?z+1:z;
    const pts=dx!==0?[[x0,y,dx>0?z+1:z],[x0,y,dx>0?z:z+1],[x0,y+1,dx>0?z:z+1],[x0,y+1,dx>0?z+1:z]]
     :[[dz>0?x:x+1,y,z0],[dz>0?x+1:x,y,z0],[dz>0?x+1:x,y+1,z0],[dz>0?x:x+1,y+1,z0]];
    m.quad(pts,'dirt',{color:[checker,checker,checker,1]});
   }
  }
 }
}
