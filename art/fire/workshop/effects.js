import{Mesh,model,add,mul,clamp,rnd,TAU,cross,norm}from'./engine.js';
import{drawMeteorBody}from'./meteor-model.js?v=4';

export const scenes={
 ground:{name:'Ground fire',title:'Regular Minecraft fire.',duration:4,distance:6.1,pitch:.30,target:[0,.65,0],description:'Primary and ultimate ground fire use the regular Minecraft fire texture, animation, height, and model arrangement.',detail:'Only Dragon’s Pyre leaves the custom short fire carpet. The primary stream and meteor keep their original spell artwork while the fires they leave on the floor look familiar.',timeline:'Vanilla flame cycle',phase:()=> 'Regular ground fire'},
 primary:{name:'Inferno Wave',title:'A flame with its own shape.',duration:3.6,distance:13,pitch:.25,target:[0,.7,0],description:'A newly drawn orange flame head pulls long, torn tails behind it. Short custom curls and embers peel away from the stream.',detail:'Dedicated four-frame stream artwork supplies the silhouette and moving detail. Vanilla fire is a color reference only. Preview travel is slowed for inspection.',timeline:'Cast → flight → impact',phase:t=>t<.3?'Forming':t<1.9?'In flight':t<2.7?'Impact':'Dissipating'},
 pyre:{name:'Dragon’s Pyre',title:'A wall that leaves a low burn.',duration:6,distance:18,pitch:.48,target:[0,.65,0],description:'A tall custom wall of fire shoots down the runway. As it passes, it leaves the thin orange fire carpet behind.',detail:'The moving wall rises about 2.8 blocks. Only the lingering floor fire stays within 0.075 block — roughly three inches. Try Player eye, then scrub from the moving wall to the settled trail.',timeline:'Wall travels → low fire remains',phase:t=>t<3.67?'Moving fire wall':'Low lingering fire'},
 meteor:{name:'Maximum Meteor',title:'A massive, blocky fireball.',duration:7,distance:33,pitch:.25,target:[0,10.5,0],description:'An eight-block-wide blazing mass with a rounded overall shape built from smaller Minecraft-style steps. Fire curls around the entire body, with a long blazing crown and a shower of drifting embers.',detail:'The reference guides its enormous fiery presence. The model is a custom voxel sphere with exposed stepped faces, a full flame shell, and a long fiery wake. It leaves regular Minecraft fire after impact. No visible rock or magma core.',timeline:'Warning → fireball descends → impact',phase:t=>t<1.3?'Landing warning':t<4.2?'Maximum Meteor':t<5.3?'Impact':'Regular ground fires'},
 particles:{name:'Particle study',title:'Original shapes, shared colors.',duration:4,distance:9.4,pitch:.20,target:[0,1,0],description:'New molten ember flecks, curled flame streams, and broken impact arcs. Each has its own four-frame texture sequence.',detail:'Orange does most of the work, with gold and small pale-yellow highlights. Vanilla fire and lava inform the colors; no vanilla flame or lava sprites form these effects. Neutral smoke remains vanilla.',timeline:'Particle life cycle',phase:t=>t<1?'Emitting':t<2.5?'Drifting':'Fading'}
};

function floor(m,radius=3){
 for(let x=-radius;x<=radius;x++)for(let z=-radius;z<=radius;z++){
  let c=.60+((x+z)%2===0?.045:0);m.quad([[x-.497,-.04,z+.497],[x+.497,-.04,z+.497],[x+.497,-.04,z-.497],[x-.497,-.04,z-.497]],'stone',{color:[c,c,c,1]});
 }
}

function groundFire(m,p,old=false,seed=0,scale=1){
 if(old){for(let angle of[Math.PI/4,Math.PI*3/4])m.plane(add(p,[0,scale*.5,0]),[Math.cos(angle)*scale*.64,0,Math.sin(angle)*scale*.64],[0,scale*.5,0],'old_ground'+(seed%2),{emissive:true});return}
 model(m,m.refs.manifest.models.template_fire_floor,p,seed%2?'fire1':'fire0',{emissive:true},0,scale);
 for(let i=0;i<4;i++)model(m,m.refs.manifest.models.template_fire_side,p,(seed+i)%2?'fire1':'fire0',{emissive:true},i*Math.PI/2,scale);
}

function pyreFloorFire(m,p,old=false,seed=0,scale=1){
 if(old){for(let angle of[Math.PI/4,Math.PI*3/4])m.plane(add(p,[0,scale*.5,0]),[Math.cos(angle)*scale*.64,0,Math.sin(angle)*scale*.64],[0,scale*.5,0],'old_ground'+(seed%2),{emissive:true,phase:0});return}
 if(scale<=0)return;
 // A horizontal animated footprint plus three original, extremely low flame curls.
 const h=.075*scale,w=.98*scale,options={emissive:true,phase:seed},a=(seed%4)*Math.PI/2;
 m.plane(add(p,[0,.009+(seed%3)*.0003,0]),[Math.cos(a)*w*.63,0,Math.sin(a)*w*.63],[-Math.sin(a)*w*.63,0,Math.cos(a)*w*.63],'custom_floor',options);
 for(let i=0;i<3;i++){
  const angle=(seed%4)*Math.PI/2+i*Math.PI/3,offset=(i-1)*.22*scale;
  m.plane(add(p,[Math.sin(angle)*offset,h*.5,Math.cos(angle)*offset]),[Math.cos(angle)*w*.45,0,Math.sin(angle)*w*.45],[0,h*.5,0],'custom_low_wisp',{...options,phase:seed+i});
 }
}

function motes(m,p,old,seed=0,count=12,spread=.75,height=2){
 for(let i=0;i<count;i++){
  const s=seed+i*17,t=(m.time*.55+rnd(s))%1,angle=rnd(s+3)*TAU,r=spread*(.35+rnd(s+4)*.65),size=(.07+rnd(s+5)*.08)*(1-t*.5);
  m.sprite(add(p,[Math.cos(angle)*r+Math.sin(t*5+s)*.07,t*height,Math.sin(angle)*r]),size*(old?1:2),size*(old?1:2),old?'old_ember'+(Math.floor(t*4)%4):'custom_ember',{emissive:true,blend:true,phase:i,color:[1,1,1,Math.min(1,(1-t)*3)]});
 }
 for(let i=0;i<Math.ceil(count/5);i++){
  const s=seed+i*7,t=(m.time*.22+rnd(s+41))%1,scale=.17+t*.54;
  m.sprite(add(p,[(rnd(s+42)-.5)*spread+t*.3,.5+t*height,(rnd(s+43)-.5)*spread]),scale,scale,'smoke'+Math.floor((1-t)*7),{blend:true,color:[.38,.38,.38,Math.sin(t*Math.PI)*.52]});
 }
}

// Original upward plume sprite; no vanilla fire tile is used on spell geometry.
function tongue(m,p,direction,width,length,seed=0,alpha=1){
 let side=Math.abs(direction[1])>.8?[Math.cos(seed)*width/2,0,Math.sin(seed)*width/2]:[0,Math.cos(seed)*width/2,Math.sin(seed)*width/2];
 const tail=add(p,mul(direction,-length*.65)),head=add(p,mul(direction,length*.35));
 m.quad([add(tail,side),add(head,mul(side,.62)),add(head,mul(side,-.62)),add(tail,mul(side,-1))],'custom_plume',{
  emissive:true,blend:alpha<1,phase:Math.floor(seed*3),color:[1,1,1,alpha],uv:[[0,1],[0,0],[1,0],[1,1]]
 });
}

function burst(m,p,age,old,strength=1){
 if(age<0||age>1.5)return;
 if(old){let f=Math.min(5,Math.floor(age*6));m.sprite(add(p,[0,.3,0]),strength*2.7,strength*2.7,'old_impact_ring'+f,{emissive:true,blend:true,color:[1,1,1,clamp(1-age)]});return}
 const fade=clamp((1.25-age)*1.8),r=(.3+age*2.6)*strength;
 const turn=age*.8;
 m.plane(add(p,[0,.045,0]),[Math.cos(turn)*r,0,Math.sin(turn)*r],[-Math.sin(turn)*r,.012,Math.cos(turn)*r],'custom_arc',{emissive:true,blend:true,color:[1,1,1,fade]});
 for(let i=0;i<16;i++){
  const angle=i/16*TAU,dir=[Math.cos(angle),.3+Math.sin(i*4)*.12,Math.sin(angle)];
  tongue(m,add(p,[dir[0]*r,.10+Math.sin(Math.min(1,age)*Math.PI)*(.35+rnd(i)*.6),dir[2]*r]),dir,.26*strength,.6*strength, i,fade);
  m.sprite(add(p,[dir[0]*r*1.2,.2+age*(1+rnd(i)),dir[2]*r*1.2]),.23,.23,'custom_ember',{emissive:true,blend:true,phase:i,color:[1,1,1,fade]});
 }
 for(let i=0;i<7;i++){let a=i/7*TAU,s=(.3+age*.8)*strength;m.sprite(add(p,[Math.cos(a)*age*strength,.3+age*.85,Math.sin(a)*age*strength]),s,s,'smoke'+Math.min(7,Math.floor((1-clamp(age/1.5))*7)),{blend:true,color:[.42,.42,.42,fade*.5]})}
}

function primary(m,t,old,particles){
 floor(m,6);
 const progress=clamp((t-.25)/1.65),x=-4.7+progress*9.4;
 if(t<1.9){
  let growth=clamp(t/.35),p=[x,1.05,0];
  if(old){let f=Math.min(9,Math.floor(progress*10)),len=[1.2,1.75,2.45,3.2,3.75,4,4,3.72,3.2,2.55][f],width=[.55,.72,.94,1.18,1.40,1.52,1.58,1.48,1.30,1.05][f],front=[.85,1.10,1.48,1.95,2.45,2.90,3.2,3.12,2.82,2.3][f];
   m.plane(add(p,[-len*.5+.6,0,0]),[len*.5,0,0],[0,width*.5,0],'old_inferno_stream'+f,{emissive:true});
   m.plane(add(p,[-len*.5+.6,0,0]),[len*.5,0,0],[0,0,width*.36],'old_inferno_stream'+f,{emissive:true,blend:true,color:[1,1,1,.85]});
   m.plane(add(p,[.68,0,0]),[0,0,front*.5],[0,front*.5,0],'old_inferno_front'+f,{emissive:true});
  }else{
   for(let i=0;i<3;i++){let a=i*Math.PI/3;m.plane(add(p,[-.8,0,0]),[1.8*growth,0,0],[0,Math.cos(a)*.85*growth,Math.sin(a)*.85*growth],'custom_stream',{emissive:true,phase:i})}
   // A broken front curl bridges the stream from oblique viewing angles.
   m.plane(add(p,[.32,0,0]),[0,0,.72*growth],[0,.72*growth,0],'custom_arc',{emissive:true});
  }
  if(particles){for(let i=0;i<15;i++){let u=(m.time*1.2+i*.13)%1;m.sprite(add(p,[-u*2.7,(rnd(i)-.5)*.9*u,(rnd(i+22)-.5)*.9*u]),.30*(1-u*.6),.23*(1-u*.6),old?'old_flame_ribbon'+Math.floor(u*7):'custom_ember',{emissive:true,blend:true,phase:i,color:[1,1,1,1-u]})}}
 }
 if(t>1.9)burst(m,[4.7,.08,0],t-1.9,old,.8);
 // Several sample patches show the visual treatment of the existing temporary ground fire.
 for(let i=0;i<5;i++){if(progress>(i+1)/6)groundFire(m,[-3+i*1.6,.005,0],old,i,1)}
}

function pyre(m,t,old,particles){
 floor(m,7);let front=Math.min(11,t*3),center=-5;
 for(let row=0;row<11;row++)for(let side=-1;side<=1;side++){
  let age=(front-row-Math.abs(side)*.22)/3;if(age<0)continue;
  const p=[center+row,.005,side];m.box(add(p,[0,-.015,0]),[1,.045,1],'netherrack');
  let scale=clamp(age/.17);pyreFloorFire(m,p,old,row+side+4,scale);
  if(particles&&side===0){
   if(old)motes(m,add(p,[0,.7,0]),true,row*3+10,3,.65,1.7);
   else if(row%2===0){const u=(t*.55+row*.13)%1;m.sprite(add(p,[Math.sin(row)*.23,.025+u*.19,Math.cos(row)*.23]),.08,.08,'custom_ember',{emissive:true,blend:true,phase:row,color:[1,1,1,1-u]})}
  }
 }
 if(front<11){
  let p=[center+front,.65,0];if(old)m.plane(p,[0,0,1.7],[0,.85,0],'old_pyre_front'+(Math.floor(t*10)%8),{emissive:true});
  else for(let i=-1;i<=1;i++){
   const x=center+front+Math.abs(i)*.18,h=i===0?2.8:2.45;
   m.quad([[x,0,i-.64],[x,0,i+.64],[x+.24,h,i+.64],[x+.24,h,i-.64]],'custom_pyre_wall',{emissive:true,phase:i+1});
   // A second, narrower layer gives the traveling wall thickness from the side.
   m.plane([x-.22,h*.44,i],[.32,0,0],[0,h*.44,0],'custom_pyre_wall',{emissive:true,phase:i+2});
  }
 }
}

function meteorFlame(m,root,direction,width,length,phase,texture='custom_plume'){
 const axis=norm(direction),side=norm(cross(axis,Math.abs(axis[1])>.9?[1,0,0]:[0,1,0]));
 // Curved strips keep roots attached to the shell and carry the flame outward.
 for(let segment=0;segment<3;segment++){
  const a=segment/3,b=(segment+1)/3;
  const at=u=>add(root,add(mul(axis,u*length),mul(side,Math.sin(u*Math.PI)*Math.sin(m.time*5+phase)*length*.10)));
  const pa=at(a),pb=at(b),wa=width*(1-a*.42)*.5,wb=width*(1-b*.42)*.5;
  m.quad([add(pa,mul(side,-wa)),add(pa,mul(side,wa)),add(pb,mul(side,wb)),add(pb,mul(side,-wb))],texture,{emissive:true,phase,uv:[[0,1-a],[1,1-a],[1,1-b],[0,1-b]]});
 }
}

function meteorCorona(m,p,t,particles){
 // Full-sphere coverage: the earlier small equatorial flames were buried in the body.
 for(let i=0;i<46;i++){
  const y=1-2*(i+.5)/46,a=i*2.399963+t*.24,r=Math.sqrt(1-y*y),n=[Math.cos(a)*r,y,Math.sin(a)*r];
  const root=add(p,mul(n,4.22)),direction=add(mul(n,.72),[0,.82,0]);
  meteorFlame(m,root,direction,2.4+rnd(i+7)*1.5,2.0+rnd(i+11)*2.2,i,i%2===0?'custom_pyre_wall':'custom_plume');
 }
 // Long trailing crown, with a clear opening through which the round body reads.
 for(let i=0;i<11;i++){
  const a=i/11*TAU+t*.3,r=1.0+rnd(i+61)*1.4;
  const root=add(p,[Math.cos(a)*r,Math.sqrt(16-r*r),Math.sin(a)*r]);
  meteorFlame(m,root,[Math.cos(a)*.18,1,Math.sin(a)*.18],2.0+rnd(i+83)*1.2,4.2+rnd(i+90)*2.2,i+47,i%2===0?'custom_pyre_wall':'custom_plume');
 }
 if(!particles)return;
 for(let i=0;i<78;i++){
  const life=(t*.46+rnd(i+200))%1,a=i*2.4+t*.22,y=(rnd(i+201)-.5)*1.4,r=Math.sqrt(1-y*y);
  const n=[Math.cos(a)*r,y,Math.sin(a)*r],radius=4.35+life*(2.6+rnd(i+208)*2);
  const q=add(p,add(mul(n,radius),[Math.sin(life*4+i)*.2,life*3.8,Math.cos(life*4+i)*.2]));
  const size=(.24+rnd(i+202)*.27)*(1-life*.45);
  m.sprite(q,size,size,'custom_ember',{emissive:true,blend:true,phase:i,color:[1,1,1,Math.min(1,(1-life)*2.5)]});
 }
}

function meteor(m,t,old,particles){
 floor(m,old?10:12);
 if(t<4.2){
  const progress=clamp((t-1.2)/3),r=old?3.2-progress*1.55:5.4-progress*.8;
  for(let i=0;i<32;i++){
   const a=i/32*TAU+t*.17,p=[Math.cos(a)*r,.075,Math.sin(a)*r];
   if(old)m.sprite(p,.55,.55,'old_meteor_warning'+Math.floor(t*8)%8,{emissive:true});
   else m.plane(p,[Math.cos(a)*.39,0,Math.sin(a)*.39],[Math.sin(a)*.28,.012,-Math.cos(a)*.28],'custom_arc',{emissive:true,phase:i});
  }
  if(t>1.2){
   if(old){
    const p=[0,7*(1-progress*progress)+.1,0];
    model(m,m.refs.manifest.models.meteor_core,p,'magma',{emissive:true});
    m.sprite(add(p,[0,.65,0]),2.6,2.6,'old_meteor_shell'+Math.floor(t*10)%8,{emissive:true});
    if(particles)motes(m,add(p,[0,1.05,0]),true,67,18,.45,2.7);
   }else{
    const p=[0,10*(1-progress*progress)+4,0];
    drawMeteorBody(m,p,t);
    meteorCorona(m,p,t,particles);
   }
  }
 }else{
  const age=t-4.2;
  if(old&&age<1.8)m.sprite([0,1.3,0],6,6,'old_meteor_impact'+Math.min(9,Math.floor(age*6)),{emissive:true,blend:true,color:[1,1,1,clamp(1.8-age)]});
  else if(!old)burst(m,[0,.05,0],age,false,3.2);
  if(particles&&age<2.8)motes(m,[0,.3,0],old,22,old?24:40,old?2:4,old?2.6:4);
  if(age>.25)for(let i=0;i<9;i++){
   const a=i/9*TAU,r=1.4+rnd(i+15)*3;
   groundFire(m,[Math.cos(a)*r,.005,Math.sin(a)*r],old,i,1);
  }
 }
}

function particleStudy(m,t,old){
 floor(m,4);
 for(let x of[-2.5,0,2.5])m.box([x,.07,0],[1.4,.2,1.4],'netherrack');
 motes(m,[-2.5,.22,0],old,78,19,.42,2.6);
 for(let i=0;i<6;i++){
  const a=(t*.5+i/6)%1,p=[-.8+a*1.6,.7+Math.sin(a*Math.PI)*.65,0];
  if(old)m.sprite(p,.6,.6,'old_flame_ribbon'+Math.floor(a*8),{emissive:true,blend:true,color:[1,1,1,Math.sin(a*Math.PI)]});
  else m.sprite(p,.9,.55,'custom_stream',{emissive:true,blend:true,phase:i,color:[1,1,1,Math.sin(a*Math.PI)]});
 }
 burst(m,[2.5,.2,0],t%2.2,old,.45);
}

export function makeScene(refs,state,camera,old){
 const m=new Mesh(refs,state.time,camera),t=state.time;
 switch(state.scene){
  case'ground':floor(m,3);m.box([0,-.015,0],[1,.05,1],'netherrack');groundFire(m,[0,.015,0],old,0);if(state.particles)motes(m,[0,.55,0],old,5,10,.45,2.4);break;
  case'primary':primary(m,t,old,state.particles);break;
  case'pyre':pyre(m,t,old,state.particles);break;
  case'meteor':meteor(m,t,old,state.particles);break;
  case'particles':particleStudy(m,t,old);break;
 }
 return m;
}
