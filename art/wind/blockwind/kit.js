// Shared block-wind primitives. Every function is a pure function of (mesh time, arguments, seed):
// no hidden state, no Math.random, so a Java renderer can reproduce the same cubes every frame
// (the same contract as the Fire block-fire kit this study was copied from).
//
// Look rules:
// - Air is see-through. Most wind cubes are translucent; only blade edges and dagger metal are solid.
// - Speed reads through stretch: fast air is long thin streak boxes along the flow, slow air is round puffy clusters.
// - Pieces fade by shrinking and thinning; anything that loops restarts while it is invisible.
// - No dark outlines or glow halos. Contrast comes from the atlas bands and the baked face shade.
import{ATLAS,add,sub,mul,dot,cross,norm,len,dist,clamp,lerp,smoothstep,rnd,noise,TAU,UP}from'./engine.js';

/** Atlas rows in textures/blockwind.png. */
export const BAND={gale:0,cloud:1,mist:2,streak:3,swirl:4,feather:5,pearl:6,silver:7};
// How much each band ignores scene light (1 = full-bright). Gale edges glow a little at dusk; clouds mostly take the light.
const GLOW=[1,.55,.45,.8,.7,.5,.35,.25];
/** Atlas frame rate; each cube offsets its frame by its seed so neighbours don't animate in sync. */
export const FPS=8;
/** Default opacity of see-through air cubes. */
export const AIR_ALPHA=.62;

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

/**
 * One wind cube. size is an edge length or [x, y, z] extents along axes. alpha below 1 makes it a see-through air cube
 * (sorted, blended); 1 makes it solid. glow overrides the band's light response.
 */
export function cube(m,p,size,{band='cloud',seed=0,axes=AXES,alpha=AIR_ALPHA,glow=null,color=[1,1,1],fps=FPS}={}){
 const s=typeof size==='number'?[size,size,size]:size;
 const row=BAND[band];if(row===undefined)throw Error('Unknown band: '+band);
 if(alpha<=.02)return;
 const frame=Math.floor(m.time*fps+rnd(seed+.77)*4)%4;
 m.box(p,mul(s,.5),axes,alpha<1?ATLAS.airKey:ATLAS.key,{cell:[frame,row],emissive:glow??GLOW[row],color:[color[0],color[1],color[2],Math.min(1,alpha)],tone:alpha<1?'soft':true});
}

/** A speed line: one long thin box from a to b. width is its cross-section; up orients the flat side. */
export function streak(m,a,b,{width=.12,height=null,band='streak',alpha=.7,seed=0,up=UP,roll=0}={}){
 const d=sub(b,a),l=len(d);if(l<1e-3||width<=1e-3)return;
 cube(m,mul(add(a,b),.5),[l,height??width,width],{band,alpha,seed,axes:flowAxes(d,up,roll)});
}

/**
 * A puffy cloud cluster that blooms, drifts and thins away over duration: a core cube with lobes that spread as it
 * ages. Lower lobes use the shadowed mist band, like the underside of a real cloud.
 */
export function puff(m,center,age,{size=.5,seed=0,duration=.8,drift=[0,0,0],spread=.6,lobes=4,alpha=AIR_ALPHA,band='cloud'}={}){
 const k=age/duration;if(k<0||k>=1)return;
 const grow=.45+.55*easeOut(k/.3),fade=1-smoothstep((k-.5)/.5),s=size*grow*(.35+.65*fade);
 const c=add(center,mul(drift,easeOut(k))),axes=yawAxes(rnd(seed)*TAU+k*.8);
 const a=alpha*(.55+.45*fade);
 cube(m,c,s,{band,seed,axes,alpha:a});
 for(let i=0;i<lobes;i++){
  const d=sphereDir(i+Math.floor(rnd(seed+i)*3),lobes+3),off=mul(d,s*(.42+spread*k));
  cube(m,add(c,off),s*(.5+.25*rnd(seed+i*1.7)),{band:d[1]<-.15?'mist':band,seed:seed+i+1,axes,alpha:a});
 }
}

/**
 * A stepped crescent air blade flying along fwd. It faces the caster and the target (so it never turns edge-on in
 * first person): the arc spans side=cross(fwd, up), bulges toward up, and its tips sweep back along -fwd, so from the
 * side it reads as a swept bow. The convex rim is a thin solid strip of the bright gale band; inside it sits a wider
 * see-through body. k (0..1) dissolves it: segments peel from the tips inward, drift back and shrink. echo drops the
 * solid rim, for the fainter copies that trail a fast blade.
 */
export function blade(m,center,fwd,up,{radius=.95,sweep=2.3,width=.34,depth=.1,bend=.55,back=.7,segments=13,seed=0,k=0,alpha=.72,echo=false}={}){
 const f=norm(fwd),u=norm(sub(up,mul(f,dot(up,f)))),side=cross(f,u);
 const at=a=>add(center,add(mul(side,Math.sin(a)*radius),add(mul(u,(Math.cos(a)-1)*radius*bend),mul(f,(Math.cos(a)-1)*radius*back))));
 const step=sweep/segments;
 for(let i=0;i<segments;i++){
  const v=(i+.5)/segments*2-1,a=v*sweep/2;
  // Tips dissolve first: each segment's own clock runs ahead the farther it is from the middle.
  const own=clamp(k*1.6-(1-Math.abs(v))*.6),keep=1-smoothstep(own);if(keep<=.02)continue;
  const profile=Math.pow(Math.cos(v*Math.PI/2),.7),w=width*(.25+.75*profile)*keep;
  const p=at(a),tangent=norm(sub(at(a+step/2),at(a-step/2)));
  // Right-handed (tangent × thin = radial), like every other cube; a mirrored basis turns the faces inside out.
  const outward=norm(add(mul(side,Math.sin(a)),mul(u,Math.cos(a)*bend))),radial=norm(sub(outward,mul(tangent,dot(outward,tangent)))),thin=cross(radial,tangent);
  const seg=len(sub(at(a+step/2),at(a-step/2)))*1.1,axes=[tangent,thin,radial];
  const drift=add(mul(f,-own*.6),mul(radial,own*.15));
  // An echo has no solid rim: it is all see-through body.
  if(!echo)cube(m,add(add(p,mul(radial,-w*.12)),drift),[seg,depth*keep,w*.26],{band:'gale',seed:seed+i,axes,alpha:1});
  cube(m,add(add(p,mul(radial,-w*.62)),drift),[seg*.96,depth*.75*keep,w*.74],{band:i%3===1?'streak':'cloud',seed:seed+i+40,axes,alpha:alpha*keep});
 }
}
/** Where the blade's two tips are (for tip trails). */
export function bladeTips(center,fwd,up,{radius=.95,sweep=2.3,bend=.55,back=.7}={}){
 const f=norm(fwd),u=norm(sub(up,mul(f,dot(up,f)))),side=cross(f,u);
 return[-1,1].map(s=>{const a=s*sweep/2;return add(center,add(mul(side,Math.sin(a)*radius),add(mul(u,(Math.cos(a)-1)*radius*bend),mul(f,(Math.cos(a)-1)*radius*back))))});
}

/** count cubes on a circle around normal, each stretched along the tangent. phase turns the ring. */
export function ring(m,center,normal,radius,{count=16,size=.12,stretch=1.4,band='cloud',alpha=AIR_ALPHA,phase=0,seed=0,gaps=0}={}){
 if(radius<=1e-3||size<=1e-3)return;
 const n=norm(normal),a0=norm(cross(n,Math.abs(n[1])>.9?[1,0,0]:UP)),b0=cross(n,a0);
 const arc=TAU*radius/count;
 for(let i=0;i<count;i++){
  if(gaps&&rnd(seed+i*3.1)<gaps)continue;
  const a=phase+TAU*i/count,radial=add(mul(a0,Math.cos(a)),mul(b0,Math.sin(a))),tangent=cross(n,radial);
  cube(m,add(center,mul(radial,radius)),[Math.min(arc*.85,size*stretch*2.2),size,size],{band,seed:seed+i,axes:[tangent,n,radial],alpha});
 }
}

/** A small curled wisp: a few cubes stepping along a tightening curl, shrinking toward the tail. */
export function wisp(m,p,dir,age,{size=.12,seed=0,duration=.5,curl=2.2,length=.6,alpha=.7,up=UP}={}){
 const k=age/duration;if(k<0||k>=1)return;
 const x=norm(dir),y0=norm(sub(up,mul(x,dot(up,x)))),side=rnd(seed)<.5?-1:1,y=mul(y0,side);
 const fade=1-smoothstep((k-.35)/.65),head=add(p,mul(x,length*easeOut(k)));
 for(let i=0;i<4;i++){
  const a=i/3*curl,r=length*.35*(1-i*.18);
  const q=add(head,add(mul(x,-Math.sin(a)*r),mul(y,(1-Math.cos(a))*r)));
  const s=size*(1-i*.2)*fade;if(s<=.01)continue;
  cube(m,q,[s*1.6,s,s],{band:i===0?'gale':'cloud',seed:seed+i,axes:flowAxes(add(mul(x,Math.cos(a)),mul(y,Math.sin(a))),up),alpha:alpha*(1-i*.15)});
 }
}

/** Tiny drifting motes (the reduced-particle setting removes them). */
export function motes(m,origin,age,{count=8,spread=.8,speed=1.2,duration=.7,size=.05,seed=0,dir=null}={}){
 const k=age/duration;if(k<0||k>=1)return;
 for(let i=0;i<count;i++){
  const d=dir?norm(add(dir,mul(sphereDir(i,count),.6))):sphereDir(i,count);
  const p=add(origin,add(mul(sphereDir(i+3,count+5),spread*.3),mul(d,speed*duration*easeOut(k)*(.6+.4*rnd(seed+i)))));
  cube(m,p,size*(1-k),{band:'gale',seed:seed+i,alpha:1});
 }
}

/** A rising helix of streak boxes: radius, height and turns describe the coil; k (0..1) unwinds it from the bottom. */
export function spiral(m,base,{radius=.7,height=3,turns=1.5,count=18,width=.08,phase=0,k=0,seed=0,alpha=.7}={}){
 for(let i=0;i<count;i++){
  const v=i/count,own=clamp(k*1.5-(1-v)*.5),keep=1-smoothstep(own);if(keep<=.02)continue;
  const a=phase+v*turns*TAU,a2=phase+(v+1/count)*turns*TAU,r=radius*(1+own*.6);
  const p=add(base,[Math.cos(a)*r,v*height,Math.sin(a)*r]),q=add(base,[Math.cos(a2)*r,(v+1/count)*height,Math.sin(a2)*r]);
  streak(m,p,q,{width:width*keep,band:i%4===0?'gale':'streak',alpha:i%4===0?1:alpha*keep,seed:seed+i});
 }
}

// ---- Models ----------------------------------------------------------------------------------------------

/**
 * The approved stepped Gale Dagger, box for box and colour for colour as GaleDaggerRenderer draws it: a pearl blade
 * with a raised ridge, compact guard and wrapped handle in flat colours (the user kept this look over an atlas skin).
 * fwd is where the tip points. grow (0..1) builds it from the grip out to the tip, for the wind-forging intro.
 */
const DAGGER_COLORS=[0xF8FAF7,0xE8EEED,0xD8E2E4,0xBCCBD0,0x98AAB4].map(c=>[(c>>16&255)/255,(c>>8&255)/255,(c&255)/255,1]);
export function dagger(m,pos,fwd,up=UP,{roll=0,grow=1,scale=1}={}){
 const z=norm(fwd);let y=norm(sub(up,mul(z,dot(up,z))));const x0=cross(y,z);
 const c=Math.cos(roll),s=Math.sin(roll),x=add(mul(x0,c),mul(y,s));y=sub(mul(y,c),mul(x0,s));
 const axes=[x,y,z];
 // Renderer space has the tip at -Z; here +fwd is the tip, so the renderer's z is negated.
 const box=(bx,by,bz,w,h,d,color,order)=>{
  const g=smoothstep(grow*1.6-order*.6);if(g<=.01)return;
  const p=add(pos,add(mul(x,bx*scale),add(mul(y,by*scale),mul(z,-bz*scale))));
  m.box(p,[w*scale*g/2,h*scale*g/2,d*scale*g/2],axes,'white',{color:DAGGER_COLORS[color]});
 };
 const rows=[0,1,1,2,2,3,3,3,3,3,3,3,2];
 for(let r=0;r<rows.length;r++)for(let k=-rows[r];k<=rows[r];k++)
  box(k*.038,k===0?.018:0,(r-9)*.063,.040,.036,.065,k===0?0:Math.abs(k)===rows[r]?1:k<0?2:3,1-r/rows.length);
 for(let k=-4;k<=4;k++)box(k*.04,0,.27,.043,.065,.055,2,0);
 for(let r=0;r<5;r++)box(0,0,.325+r*.045,.061,.055,.045,r%2?3:4,0);
 box(0,0,.56,.088,.073,.07,1,0);
}

/**
 * 3D feathered wings on a figure's back: a short gale-white leading bone and layered feather slabs that fan out
 * toward the tip, with smaller cloud coverts on top. spread 0 folds them back along the body, 1 opens them wide.
 * A proposal: today Zephyr Strike wears a texture on the vanilla Elytra geometry.
 */
export function wings(m,pos,yaw,{lean=0,spread=1,flap=0,seed=0}={}){
 const{back,axes}=torso(pos,yaw,lean),[f,u,r]=axes;
 for(const side of[-1,1]){
  const root=add(back,mul(r,side*.16));
  const lift=.35+flap*.25,boneDir=norm(add(add(mul(r,side*(.35+.65*spread)),mul(u,lift)),mul(f,-(.75-.55*spread))));
  const BONE=1.35,FEATHERS=8;
  for(let i=0;i<4;i++)cube(m,add(root,mul(boneDir,BONE*(i+.5)/4)),[BONE/4*1.05,.09,.1],{band:'gale',seed:seed+side*9+i,axes:flowAxes(boneDir,u),alpha:1});
  for(let i=0;i<FEATHERS;i++){
   const v=(i+.5)/FEATHERS,base=add(root,mul(boneDir,BONE*v));
   const fd=norm(add(add(mul(f,-.9),mul(u,-.55+.25*spread)),mul(boneDir,v*v*.9))),L=.45+.75*v*v;
   const axesF=flowAxes(fd,cross(boneDir,fd));
   cube(m,add(base,mul(fd,L/2)),[L,.04,.17],{band:'feather',seed:seed+side*31+i,axes:axesF,alpha:1});
   cube(m,add(add(base,mul(fd,L*.24)),mul(axesF[1],.035*side)),[L*.5,.035,.15],{band:'cloud',seed:seed+side*51+i,axes:axesF,alpha:1});
  }
 }
}

// ---- Figures and terrain (shared with the Fire study) ------------------------------------------------------

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
   if(!self)cube(m,tip,.11,{band:'gale',seed:3,axes:basis(aim,Math.PI/4),alpha:1});}
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
