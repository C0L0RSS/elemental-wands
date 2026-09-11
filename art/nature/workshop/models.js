import{add,sub,mul,mix,norm,clamp,smooth,rnd,TAU,C}from'./engine.js';
export function floor(m,r=5,water=false){m.box([0,-.20,0],[r*2,.34,r*2],[.22,.29,.19],7);m.box([0,-.022,0],[r*2-.04,.035,r*2-.04],water?[.25,.48,.42]:[.30,.38,.24],water?6:7);for(let n=-r;n<=r;n++){m.box([n,.001,0],[.012,.002,r*2],[.35,.43,.29],7);m.box([0,.001,n],[r*2,.002,.012],[.35,.43,.29],7)}for(let k=0;k<35;k++){let x=(rnd(k+3)-.5)*r*1.8,z=(rnd(k+90)-.5)*r*1.8;if(Math.abs(x)<1.2&&Math.abs(z)<1.2)continue;m.box([x,-.09,z],[.12+rnd(k)*.20,.13,.1+rnd(k+4)*.2],[.26,.31,.23],4,rnd(k)*3)}}
export function roots(m,p,r,g=1,seed=1,thorns=true){
 const directions=[[1,0],[1,1],[0,1],[-1,1],[-1,0],[-1,-1],[0,-1],[1,-1]];
 for(let k=0;k<directions.length;k++){
  let [dx,dz]=directions[k],len=r*(.70+rnd(k+seed)*.30)*g,turn=(k%2?1:-1)*.12;
  let points=[[0,.07,0],[dx*len*.28,.12,dz*len*.18],[dx*len*.28,.12,dz*len*.49],[dx*len*.67,.06,dz*len*.49],[dx*len*.67+turn,.06,dz*len*.83],[dx*len,.035,dz*len]].map(v=>add(p,v));
  m.tube(points,.068*g,C.root,0,4,.015*g);
  if(thorns)for(let j of [2,4]){let v=points[j];m.box(add(v,[0,.065*g,0]),[.055*g,.13*g,.055*g],C.wood,0);m.box(add(v,[dx*.023,.14*g,dz*.023]),[.033*g,.035*g,.033*g],C.lightleaf,1)}
  let v=points[3];m.leaf(v,add(v,[dx*.30*g,.12*g,(dz||1)*.26*g]),.13*g,C.leaf);
 }
}
export function flower(m,p,r,open=1,seed=0,time=0){
 // Broad stepped petals in two distinct colors, with a blocky golden pollen center.
 for(let layer=0;layer<2;layer++)for(let i=0;i<4;i++){
  let a=i*Math.PI/2+(layer?Math.PI/4:0),spread=r*(layer?.74:1)*(.28+.72*open),up=r*(.74-.57*open)+layer*.075;
  let base=add(p,[Math.cos(a)*r*.12,layer*.045,Math.sin(a)*r*.12]),tip=add(p,[Math.cos(a)*spread,up,Math.sin(a)*spread]);
  m.leaf(base,tip,r*(layer?.34:.39),layer?C.petallight:C.petal,2,.025);
 }
 for(let i=0;i<4;i++){let a=i*Math.PI/2;m.leaf(add(p,[0,-.035,0]),add(p,[Math.cos(a)*r*.70,r*.06,Math.sin(a)*r*.70]),r*.24,C.violet,2,.02)}
 m.box(add(p,[0,.075,0]),[r*.43,.12,r*.43],C.gold,3);
 for(let x of [-1,1])for(let z of [-1,1])m.box(add(p,[x*r*.12,.16,z*r*.12]),[r*.105,.10,r*.105],C.gold,3);
}
export function seedPod(m,p,size=1,spin=0){let bot=add(p,[0,-.24*size,0]),top=add(p,[0,.27*size,0]);m.tube([bot,p,top],.18*size,C.wood,0,7,.02);for(let i=0;i<6;i++){let a=spin+i*TAU/6;let start=add(p,[Math.cos(a)*.09*size,-.22*size,Math.sin(a)*.09*size]);m.leaf(start,add(p,[Math.cos(a+.23)*.19*size,.28*size,Math.sin(a+.23)*.19*size]),.14*size,i%2?C.leaf:C.darkleaf,1,.025)}for(let i=0;i<2;i++){let a=spin+i*Math.PI;m.leaf(add(p,[Math.cos(a)*.1*size,0,Math.sin(a)*.1*size]),add(p,[Math.cos(a)*.70*size,.25*size,Math.sin(a)*.70*size]),.19*size,C.lightleaf,1,.04)}m.gem(add(p,[0,.26*size,0]),.048*size,C.gold)}
export function plant(m,p,g=1,time=0,bloom=true,seed=0){let h=.35+g*.85,sway=Math.sin(time*1.2+seed)*.025*g;roots(m,p,.62,g,seed,false);let stem=[p,add(p,[.08,h*.3,.02]),add(p,[-.05+sway,h*.7,.04]),add(p,[sway,h,0])];m.tube(stem,.075,C.root,0,6,.025);for(let i=0;i<5;i++){let a=i*2.39+seed,len=(.48+i*.025)*(.45+g*.55),base=add(p,[0,h*(.12+i*.09),0]);m.leaf(base,add(base,[Math.cos(a)*len,.13+g*.1,Math.sin(a)*len]),len*.29,i%2?C.leaf:C.lightleaf,1,.04)}if(bloom)flower(m,stem[3],.42+.18*g,smooth((g-.30)/.7),seed,time);else seedPod(m,stem[3],.45,seed);if(g>.65)for(let i=0;i<3;i++){let a=i*TAU/3+1.2,base=add(p,[Math.cos(a)*.12,h*.4,Math.sin(a)*.12]),top=add(p,[Math.cos(a)*.38,h*.8,Math.sin(a)*.38]);m.tube([base,mix(base,top,.65),top],.026,C.root,0,5,.01);flower(m,top,.14,smooth((g-.65)/.35),a,time)}}
export function raft(m,p,g=1,time=0){
 for(let i=0;i<4;i++){let a=i*Math.PI/2,len=.47*g;m.leaf(add(p,[0,.055,0]),add(p,[Math.cos(a)*len,.08,Math.sin(a)*len]),.27*g,i%2?C.leaf:C.lightleaf,1,.01)}
 let ring=[[-.30,.09,-.35],[.22,.09,-.35],[.36,.09,-.20],[.36,.09,.24],[.21,.09,.36],[-.31,.09,.36],[-.37,.09,.18],[-.37,.09,-.20],[-.30,.09,-.35]].map(v=>add(p,mul(v,g)));
 m.tube(ring,.025,C.root,0,4,.025);flower(m,add(p,[0,.13,0]),.14,g,1,time);
}
export function rootTile(m,p,g=1,seed=0,water=false,time=0){if(water){raft(m,p,g,time);return}for(let axis=0;axis<2;axis++){let pts=[];for(let j=0;j<=6;j++){let t=j/6;pts.push(add(p,axis?[Math.sin(t*5+seed)*.12,.07+Math.sin(t*Math.PI)*.045,(t-.5)*g]:[(t-.5)*g,.055+Math.sin(t*Math.PI)*.08,Math.sin(t*6+seed)*.11]))}m.tube(pts,.052*g,C.root,0,5,.040*g)}for(let i=0;i<3;i++){let a=i*2.4+seed,v=add(p,[Math.cos(a)*.26*g,.075,Math.sin(a)*.26*g]);m.tube([v,add(v,[Math.cos(a)*.09,.22*g,Math.sin(a)*.09])],.035*g,C.wood,0,4,.001);m.leaf(v,add(v,[Math.cos(a+.5)*.27*g,.11*g,Math.sin(a+.5)*.27*g]),.075*g,C.leaf)}if(rnd(seed)>.64)flower(m,add(p,[.1,.12,-.1]),.14,g,seed,time)}
// A real block-grid build. Materials 8/9/11 use vanilla oak bark/end grain;
// material 10 uses vanilla cutout oak leaves with a vibrant biome-style tint.
export const TREE_BLOCKS=(()=>{
 let cells=new Map(),put=(x,y,z,kind,stage)=>cells.set(`${x},${y},${z}`,{x,y,z,kind,stage});
 for(let y=0;y<=6;y++)put(0,y,0,y===2?'heart':'log',.04+y*.045);
 put(-1,0,0,'log',.06);put(0,0,-1,'log',.07);
 // Long, staggered limbs spread horizontally before their small upward turns.
 for(let d=1;d<=4;d++){
  put(-d,4,0,'log-x',.28+d*.025);
  put(d,5,0,'log-x',.30+d*.025);
  put(0,5,d,'log-z',.32+d*.025);
  put(0,6,-d,'log-z',.34+d*.025);
 }
 for(let [x,y,z]of [[-3,5,0],[3,6,0],[0,6,3],[0,7,-3]])put(x,y,z,'log-y',.43);
 // Separate leafy crowns follow the outstretched limbs rather than hiding them
 // inside one compact ball. The central crown still tops out at y=8 (nine blocks).
 for(let [cx,cy,cz]of [[-3,5,0],[3,6,0],[0,6,3],[0,6,-3],[0,7,0]]){
  for(let [dy,r]of [[-1,1],[0,2],[1,1]])for(let dx=-r;dx<=r;dx++)for(let dz=-r;dz<=r;dz++){
   let x=cx+dx,y=cy+dy,z=cz+dz;
   if(cells.has(`${x},${y},${z}`))continue;
   if(Math.abs(dx)===r&&Math.abs(dz)===r&&(x+z+y)%3!==0)continue;
   put(x,y,z,'leaves',Math.min(.94,.44+y*.035+(Math.abs(x)+Math.abs(z))*.017));
  }
 }
 for(let pos of [[-5,5,0],[-3,5,2],[5,6,0],[3,6,2],[0,6,5],[1,7,3],[0,6,-5],[-2,6,-3],[0,8,1]]){
  let cell=cells.get(pos.join(','));if(cell?.kind==='leaves')cell.kind='bloom-leaves';
 }
 return [...cells.values()];
})();
function blockFacePixel(m,p,face,u,v,w,h,color,depth=.013,material=2){
 let q=[...p],size;
 if(face==='front'){q[0]+=u;q[1]+=v;q[2]+=.501;size=[w,h,depth]}
 else if(face==='right'){q[0]+=.501;q[1]+=v;q[2]+=u;size=[depth,h,w]}
 else if(face==='left'){q[0]-=.501;q[1]+=v;q[2]+=u;size=[depth,h,w]}
 else if(face==='back'){q[0]+=u;q[1]+=v;q[2]-=.501;size=[w,h,depth]}
 else{q[0]+=u;q[1]+=.501;q[2]+=v;size=[w,depth,h]}
 m.box(q,size,color,material);
}
function heartwood(m,p,time){
 for(let face of ['front','right','back','left']){
  blockFacePixel(m,p,face,0,0,.50,.625,[.17,.25,.09],.012,0);
  for(let [u,v,w,h]of [[0,0,.125,.375],[-.125,0,.125,.125],[.125,0,.125,.125],[0,.25,.125,.0625],[0,-.25,.125,.0625]])blockFacePixel(m,p,face,u,v,w,h,mul(C.sap,.90+Math.sin(time*2)*.08),.021,3);
  for(let [u,v]of [[-.25,.1875],[.25,-.1875]])blockFacePixel(m,p,face,u,v,.0625,.1875,C.root,.018,1);
 }
}
function floweringLeafBlock(m,p){
 for(let face of ['front','right','left','back','top'])for(let [u,v,scale]of [[-.1875,.125,1],[.1875,-.1875,.72]]){
  let px=.0625*scale;
  blockFacePixel(m,p,face,u,v,px*3,px*3,C.violet,.014,2);
  blockFacePixel(m,p,face,u,v,px*5,px,C.petal,.019,2);
  blockFacePixel(m,p,face,u,v,px,px*5,C.petallight,.021,2);
  blockFacePixel(m,p,face,u,v,px,px,C.gold,.025,3);
 }
}
export function elderTree(m,p,g,time){
 for(let block of TREE_BLOCKS){
  if(g<block.stage)continue;
  let center=add(p,[block.x,block.y+.5,block.z]);
  let leaves=block.kind.includes('leaves');
  let material=leaves?10:block.kind==='log-x'?9:block.kind==='log-z'?11:8;
  m.box(center,[1,1,1],leaves?[.42,.88,.21]:[1,1,1],material);
  if(block.kind==='heart')heartwood(m,center,time);
  if(block.kind==='bloom-leaves')floweringLeafBlock(m,center);
 }
}

export function player(m,p=[0,0,0],time=0){let clothes=[.29,.38,.34],skin=[.63,.50,.36],metal=[.51,.58,.53];m.box(add(p,[0,1.55,0]),[.45,.45,.45],skin,5);m.box(add(p,[0,1.76,-.015]),[.47,.14,.46],[.21,.24,.18],5);m.box(add(p,[0,1.05,0]),[.50,.62,.29],clothes,5);m.box(add(p,[0,1.12,.16]),[.42,.39,.06],metal,4);for(let s of [-1,1]){m.box(add(p,[s*.135,.41,0]),[.22,.72,.26],clothes,5);m.box(add(p,[s*.135,.09,.055]),[.24,.17,.35],[.26,.24,.19],0);m.box(add(p,[s*.36,1.01,.012]),[.20,.61,.25],clothes,5);m.box(add(p,[s*.36,.68,.025]),[.19,.15,.20],skin,5)}for(let s of [-1,1])m.box(add(p,[s*.093,1.57,.230]),[.043,.046,.010],[.1,.17,.13],5)}
export function spider(m,p=[0,0,0],time=0){m.box(add(p,[0,.43,-.32]),[.83,.54,.98],[.29,.28,.23],4);m.box(add(p,[0,.37,.36]),[.58,.43,.51],[.24,.24,.20],4);for(let side of [-1,1])for(let i=0;i<4;i++){let z=(i-1.5)*.27,base=add(p,[side*.32,.45,z]),knee=add(p,[side*.79,.65,z+(i-1.5)*.22]),toe=add(p,[side*1.1,.035,z+(i-1.5)*.4]);m.tube([base,knee,toe],.065,[.32,.31,.24],4,4,.035)}for(let i=0;i<4;i++)m.box(add(p,[(i-1.5)*.12,.42,.625]),[.06,.065,.015],[.67,.27,.19],3)}
function rot(v,angles){let [x,y,z]=v;for(let [axis,d]of angles.entries()){let a=d*Math.PI/180,c=Math.cos(a),s=Math.sin(a);if(axis===0)[y,z]=[y*c-z*s,y*s+z*c];if(axis===1)[x,z]=[x*c+z*s,-x*s+z*c];if(axis===2)[x,y]=[x*c-y*s,x*s+y*c]}return[x,y,z]}
export function guardian(m,data,time=0,opening=false){if(!data)return;let bones=data['minecraft:geometry'][0].bones,byName=Object.fromEntries(bones.map(b=>[b.name,b]));let transform=(point,bone)=>{let q=point,b=bone;while(b){if(b.rotation)q=add(b.pivot,rot(sub(q,b.pivot),b.rotation));b=byName[b.parent]}return mul(q,1/16)};for(let bone of bones)for(let [i,cube]of(bone.cubes||[]).entries()){let origin=cube.origin,size=cube.size,inf=cube.inflate||0,ps=[];for(let x of[0,1])for(let y of[0,1])for(let z of[0,1]){let q=[origin[0]+x*size[0]+(x?inf:-inf),origin[1]+y*size[1]+(y?inf:-inf),origin[2]+z*size[2]+(z?inf:-inf)];if(cube.rotation)q=add(cube.pivot||bone.pivot,rot(sub(q,cube.pivot||bone.pivot),cube.rotation));q=transform(q,bone);q[2]*=-1;if(opening){q[2]-=smooth((q[1]-1)/4)*.20;q[0]+=Math.sin(time*28)*.012}ps.push(q)}let core=bone.name==='core',col=core?[.33,.73,.60]:mul(C.stone,.79+rnd(i+bone.name.length)*.28);for(let f of[[0,1,3,2],[4,6,7,5],[0,4,5,1],[2,3,7,6],[0,2,6,4],[1,5,7,3]])m.quad(...f.map(i=>ps[i]),col,core?3:4)}}
export function wrap(m,width,height,stacks,mode,time,boss=false,growth=1){
 if(stacks===0||growth<=0)return;
 let rx=width*.55+.08,rz=rx*(boss?.78:.82),top=height*(.18+stacks*.115);
 // Uneven chamfered corners and deliberate vertical jogs: no circular helix.
 const perimeter=[[-1,-.72],[-1,.45],[-.68,1],[.52,1],[1,.60],[1,-.64],[.62,-1],[-.54,-1]];
 for(let k=0;k<stacks;k++){
  let points=[],start=(k*3)%8,progress=clamp((growth-k*.09)/(1-k*.09));
  for(let j=0;j<=9;j++){
   let q=perimeter[(start+j)%8],rise=.085+j/9*top,previousRise=.085+Math.max(0,j-1)/9*top;
   let flex=mode==='resisting'&&j%3===0?Math.sin(time*4+k)*.025:0;
   let x=q[0]*rx+flex,z=q[1]*rz-flex;
   if(j)points.push([x,previousRise,z]);
   points.push([x,rise,z]);
  }
  let visible=Math.max(1,Math.floor(progress*(points.length-1))),pts=points.slice(0,visible+1);
  if(visible<points.length-1)pts.push(mix(points[visible],points[visible+1],progress*(points.length-1)-visible));
  m.tube(pts,.024+stacks*.006+(boss?.025:0),k%2?C.root:C.leaf,1,4,.021+stacks*.005);
  for(let i=4;i<pts.length;i+=6){let v=pts[i],sx=Math.sign(v[0])||1,sz=Math.sign(v[2])||1;m.box(add(v,[sx*.025,.045,sz*.025]),[boss?.065:.04,boss?.14:.09,boss?.065:.04],C.lightleaf,1);if(k%2===0)m.leaf(v,add(v,[sx*(boss?.33:.18),.13,sz*.06]),boss?.13:.08,C.lightleaf)}
 }
 if(stacks===5&&mode!=='lingering'){
  let anchors=[[-1,-1],[1,-1],[-1,1],[1,1]];
  for(let [sx,sz]of anchors){let base=[sx*(rx+.32),.035,sz*(rz+.24)],tip=[sx*rx,height*.20*growth,sz*rz];let pts=[base,[tip[0],.035,base[2]],[tip[0],.13,base[2]],[tip[0],.13,tip[2]],tip];m.tube(pts,boss?.085:.045,C.root,1,4,.024);if(mode==='rooted'||mode==='opening')m.gem(tip,boss?.065:.034,C.sap)}
  if(mode==='opening')for(let [sx,sz]of anchors)m.gem([sx*rx,height*.58,sz*rz],boss?.075:.035,C.sap);
 }
}