export const add=(a,b)=>a.map((v,i)=>v+b[i]);
export const sub=(a,b)=>a.map((v,i)=>v-b[i]);
export const mul=(a,s)=>a.map(v=>v*s);
export const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);
export const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
export const norm=a=>mul(a,1/(Math.hypot(...a)||1));
export const clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
export const rnd=n=>{let a=Math.sin(n*127.1+43.7)*43758.5453;return a-Math.floor(a)};
export const TAU=Math.PI*2;

export async function loadReferences(){
 const r=await fetch('references.json');if(!r.ok)throw Error('The texture reference list could not load.');
 const manifest=await r.json(),images={},loaded=new Map();
 const custom=await fetch('custom-art.json');if(!custom.ok)throw Error('The custom Fire sprite atlas could not load.');
 Object.assign(manifest.textures,(await custom.json()).textures);
 await Promise.all(Object.entries(manifest.textures).map(async([key,value])=>{
  if(!loaded.has(value.url)){const img=new Image();img.src=value.url;loaded.set(value.url,img.decode().then(()=>img).catch(()=>{throw Error('Could not decode texture: '+value.url)}))}images[key]=await loaded.get(value.url);
 }));
 return {manifest,images};
}

export function frameRect(refs,key,time,phase=0){
 const atlas=refs.manifest.textures[key].atlas;
 if(atlas){const f=((Math.floor(time*atlas.fps)+phase)%atlas.frames+atlas.frames)%atlas.frames,crop=atlas.crop||[0,0,1,1];return[(f+crop[0])/atlas.columns,(atlas.row+crop[1])/atlas.rows,crop[2]/atlas.columns,crop[3]/atlas.rows]}
 const img=refs.images[key],meta=refs.manifest.textures[key].animation;
 if(!meta)return[0,0,1,1];
 const size=meta.height||img.width,frames=meta.frames||Array.from({length:img.height/size},(_,i)=>i);
 const duration=frames.reduce((n,f)=>n+(typeof f==='number'?(meta.frametime||1):(f.time||meta.frametime||1)),0);
 let tick=((Math.floor(time*20)+phase)%duration+duration)%duration,index=0;
 for(const f of frames){let length=typeof f==='number'?(meta.frametime||1):(f.time||meta.frametime||1);index=typeof f==='number'?f:f.index;if(tick<length)break;tick-=length;}
 return [0,index*size/img.height,1,size/img.height];
}

export class Mesh{
 constructor(refs,time,camera){this.refs=refs;this.time=time;this.camera=camera;this.opaque=new Map();this.transparent=[];this.quads=0}
 quad(points,key,options={}){
  const {color=[1,1,1,1],emissive=false,blend=false,phase=0,uv=null}=options;
  const rect=frameRect(this.refs,key,this.time,phase),coords=uv||[[0,1],[1,1],[1,0],[0,0]];
  const normal=norm(cross(sub(points[1],points[0]),sub(points[2],points[0]))),data=[];
  for(const i of [0,1,2,0,2,3])data.push(...points[i],...normal,rect[0]+coords[i][0]*rect[2],rect[1]+coords[i][1]*rect[3],...color,emissive?1:0);
  if(blend){this.transparent.push({key,data,center:mul(points.reduce((s,p)=>add(s,p),[0,0,0]),.25)})}
  else{if(!this.opaque.has(key))this.opaque.set(key,[]);this.opaque.get(key).push(...data)}
  this.quads++;
 }
 box(p,s,key,options={},angle=0){
  let pts=[];for(let x of[-1,1])for(let y of[-1,1])for(let z of[-1,1]){let xx=x*s[0]/2,zz=z*s[2]/2;pts.push(add(p,[xx*Math.cos(angle)+zz*Math.sin(angle),y*s[1]/2,-xx*Math.sin(angle)+zz*Math.cos(angle)]))}
  for(const f of [[0,4,6,2],[5,1,3,7],[1,0,2,3],[4,5,7,6],[2,6,7,3],[1,5,4,0]])this.quad(f.map(i=>pts[i]),key,options);
 }
 sprite(p,w,h,key,options={}){
  const yaw=this.camera.yaw,pitch=this.camera.pitch;
  const right=[Math.cos(yaw),0,-Math.sin(yaw)],up=[-Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),-Math.cos(yaw)*Math.sin(pitch)];
  this.plane(p,mul(right,w/2),mul(up,h/2),key,options);
 }
 plane(p,right,up,key,options={}){this.quad([sub(sub(p,right),up),sub(add(p,right),up),add(add(p,right),up),add(sub(p,right),up)],key,options)}
}

function perspective(fovy,aspect,near,far){let f=1/Math.tan(fovy/2),nf=1/(near-far);return new Float32Array([f/aspect,0,0,0,0,f,0,0,0,0,(far+near)*nf,-1,0,0,2*far*near*nf,0])}
function lookAt(eye,target){let z=norm(sub(eye,target)),x=norm(cross([0,1,0],z)),y=cross(z,x);return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1])}
function matmul(a,b){let out=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)out[c*4+r]+=a[k*4+r]*b[c*4+k];return out}

export function createRenderer(canvas,refs){
 const gl=canvas.getContext('webgl2',{antialias:true,alpha:false,preserveDrawingBuffer:true});
 if(!gl)throw Error('This preview needs WebGL 2. Try a browser with hardware acceleration enabled.');
 const vert=`#version 300 es
 precision highp float;in vec3 position;in vec3 normal;in vec2 uv;in vec4 color;in float emissive;uniform mat4 vp;out vec3 N;out vec2 UV;out vec4 C;out float E;
 void main(){gl_Position=vp*vec4(position,1.);N=normal;UV=uv;C=color;E=emissive;}`;
 const frag=`#version 300 es
 precision highp float;uniform sampler2D tex;uniform float night;uniform float cutoff;in vec3 N;in vec2 UV;in vec4 C;in float E;out vec4 outputColor;
 void main(){vec4 sampleColor=texture(tex,UV);if(sampleColor.a<cutoff)discard;vec4 pixel=sampleColor*C;if(pixel.a<.025)discard;float light=.57+.43*max(0.,dot(normalize(N),normalize(vec3(-.4,1.,.5))));light=mix(light,light*.32,night);light=mix(light,1.,E);outputColor=vec4(pixel.rgb*light,pixel.a);}`;
 function shader(type,src){const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s}
 const program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vert));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,frag));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);
 const textures={},uploaded=new Map();for(const[key,img]of Object.entries(refs.images)){if(uploaded.has(img)){textures[key]=uploaded.get(img);continue}let tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);textures[key]=tex;uploaded.set(img,tex)}
 const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);let offset=0;for(const[name,size]of[['position',3],['normal',3],['uv',2],['color',4],['emissive',1]]){const loc=gl.getAttribLocation(program,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,52,offset*4);offset+=size}
 const vp=gl.getUniformLocation(program,'vp'),night=gl.getUniformLocation(program,'night'),cutoff=gl.getUniformLocation(program,'cutoff');gl.uniform1i(gl.getUniformLocation(program,'tex'),0);gl.enable(gl.DEPTH_TEST);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);
 let draws=0;
 function batch(key,data){gl.bindTexture(gl.TEXTURE_2D,textures[key]);gl.uniform1f(cutoff,refs.manifest.textures[key].alphaCutoff||.025);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.DYNAMIC_DRAW);gl.drawArrays(gl.TRIANGLES,0,data.length/13);draws++}
 return {gl,draw(mesh,camera,dusk){
  const ratio=Math.min(devicePixelRatio||1,2),width=Math.max(1,Math.round(canvas.clientWidth*ratio)),height=Math.max(1,Math.round(canvas.clientHeight*ratio));
  if(canvas.width!==width||canvas.height!==height){canvas.width=width;canvas.height=height}
  gl.viewport(0,0,width,height);gl.clearColor(...(dusk?[.047,.052,.069]:[.21,.26,.29]),1);gl.depthMask(true);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.useProgram(program);
  const {yaw,pitch,distance,target}=camera,eye=add(target,[Math.sin(yaw)*Math.cos(pitch)*distance,Math.sin(pitch)*distance,Math.cos(yaw)*Math.cos(pitch)*distance]);
  gl.uniformMatrix4fv(vp,false,matmul(perspective(Math.PI/4,width/height,.05,200),lookAt(eye,target)));gl.uniform1f(night,dusk?1:0);
  gl.disable(gl.BLEND);draws=0;for(const[key,data]of mesh.opaque)batch(key,data);
  mesh.transparent.sort((a,b)=>dot(sub(b.center,eye),sub(b.center,eye))-dot(sub(a.center,eye),sub(a.center,eye)));
  gl.enable(gl.BLEND);gl.depthMask(false);let key=null,data=[];
  for(const item of mesh.transparent){if(item.key!==key&&data.length){batch(key,data);data=[]}key=item.key;data.push(...item.data)}if(data.length)batch(key,data);
  gl.depthMask(true);gl.disable(gl.BLEND);return {quads:mesh.quads,draws,error:gl.getError()};
 }};
}

// Read vanilla model planes directly, including their element rotations/rescale.
export function model(mesh,definition,p,key,options={},yaw=0,scale=1){
 for(const e of definition.elements||[]){
  const [x,y,z]=e.from.map(v=>v/16),[X,Y,Z]=e.to.map(v=>v/16);
  const faces={south:[[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]],north:[[X,y,z],[x,y,z],[x,Y,z],[X,Y,z]],east:[[X,y,Z],[X,y,z],[X,Y,z],[X,Y,Z]],west:[[x,y,z],[x,y,Z],[x,Y,Z],[x,Y,z]],up:[[x,Y,Z],[X,Y,Z],[X,Y,z],[x,Y,z]],down:[[x,y,z],[X,y,z],[X,y,Z],[x,y,Z]]};
  // Identical opposite faces need only one double-sided browser plane.
  const seen=new Set();
  for(const[face,entry]of Object.entries(e.faces)){
   const signature=faces[face].map(v=>v.join(',')).sort().join(';');if(seen.has(signature))continue;seen.add(signature);
   const pts=faces[face].map(v=>{
    if(e.rotation){let axis={x:0,y:1,z:2}[e.rotation.axis],o=e.rotation.origin.map(v=>v/16),a=e.rotation.angle*Math.PI/180,local=sub(v,o),i=(axis+1)%3,j=(axis+2)%3;
     if(e.rotation.rescale){local[i]/=Math.cos(a);local[j]/=Math.cos(a)}
     const u=local[i],w=local[j];local[i]=u*Math.cos(a)-w*Math.sin(a);local[j]=u*Math.sin(a)+w*Math.cos(a);v=add(o,local);
    }
    let q=mul(sub(v,[.5,0,.5]),scale);return add(p,[q[0]*Math.cos(yaw)+q[2]*Math.sin(yaw),q[1],-q[0]*Math.sin(yaw)+q[2]*Math.cos(yaw)]);
   });
   let r=(entry.uv||[0,0,16,16]).map(v=>v/16),uv=[[r[0],r[3]],[r[2],r[3]],[r[2],r[1]],[r[0],r[1]]];
   mesh.quad(pts,key,{...options,uv});
  }
 }
}
