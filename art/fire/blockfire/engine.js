// Block-fire preview engine: pure geometry (Mesh) plus a small WebGL 2 renderer.
// Mesh output is deterministic in (time, seed) so the same functions port to Java.
export const add=(a,b)=>a.map((v,i)=>v+b[i]);
export const sub=(a,b)=>a.map((v,i)=>v-b[i]);
export const mul=(a,s)=>a.map(v=>v*s);
export const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);
export const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
export const len=a=>Math.hypot(...a);
export const norm=a=>mul(a,1/(Math.hypot(...a)||1));
export const dist=(a,b)=>len(sub(a,b));
export const clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
export const lerp=(a,b,t)=>a+(b-a)*t;
export const smoothstep=t=>{t=clamp(t);return t*t*(3-2*t)};
export const rnd=n=>{let a=Math.sin(n*127.1+43.7)*43758.5453;return a-Math.floor(a)};
// Smooth 1D value noise in [0,1]; same hash as rnd, so Java can mirror it exactly.
export const noise=x=>{const i=Math.floor(x),f=x-i,u=f*f*(3-2*f);return lerp(rnd(i),rnd(i+1),u)};
export const TAU=Math.PI*2;
export const UP=[0,1,0];

/** Minecraft-style directional shade baked into vertex colour: top 1.0, bottom 0.6, sides 0.73–0.87. */
export const faceTone=n=>.80+.20*n[1]+.07*n[2]+.03*n[0];

/** Atlas layout shared with textures/blockfire.png: 4 frames across, 8 bands down, 16 px cells.
 * Translucent cubes (smoke) batch under smokeKey, the same image drawn last with blending. */
export const ATLAS={key:'blockfire',smokeKey:'blockfire_smoke',columns:4,rows:8};
const TRANSLUCENT=new Set([ATLAS.smokeKey]);

export class Mesh{
 /** @param eye camera position; clearance>0 shrinks cubes near it like SpellViewClearance does in first person. */
 constructor({time=0,eye=[0,0,0],clearance=false}={}){this.time=time;this.eye=eye;this.clearance=clearance;this.batches=new Map();this.quads=0;this.cubes=0}
 quad(points,key,{color=[1,1,1,1],emissive=false,uv=null,tone=true}={}){
  const normal=norm(cross(sub(points[1],points[0]),sub(points[2],points[0]))),coords=uv||[[0,1],[1,1],[1,0],[0,0]];
  const shade=tone?faceTone(normal):1,c=[color[0]*shade,color[1]*shade,color[2]*shade,color[3]];
  if(!this.batches.has(key))this.batches.set(key,[]);
  const data=this.batches.get(key);
  for(const i of[0,1,2,0,2,3])data.push(...points[i],...normal,coords[i][0],coords[i][1],...c,emissive?1:0);
  this.quads++;
 }
 /**
  * An oriented cuboid. axes are three orthonormal vectors; half holds the half-extent along each.
  * cell=[column,row] picks an atlas cell for every face (texture "up" follows axes[1]).
  */
 box(center,half,axes,key,{cell=null,color=[1,1,1,1],emissive=false,tone=true}={}){
  if(this.clearance){
   const d=dist(center,this.eye),h=Math.max(...half),hidden=.35+h*.9,visible=hidden+Math.max(.75,h*.85);
   const s=smoothstep((d-hidden)/(visible-hidden));if(s<=0)return;half=mul(half,s);
  }
  if(!(half[0]>1e-4&&half[1]>1e-4&&half[2]>1e-4))return;
  const [a,b,c]=axes.map((v,i)=>mul(v,half[i]));
  let uv=null;
  if(cell){const u0=cell[0]/ATLAS.columns,v0=cell[1]/ATLAS.rows,du=1/ATLAS.columns,dv=1/ATLAS.rows;uv=[[u0,v0+dv],[u0+du,v0+dv],[u0+du,v0],[u0,v0]]}
  const p=(x,y,z)=>add(center,add(mul(a,x),add(mul(b,y),mul(c,z))));
  // Each face: outward axis sign, then the in-face "right" and "up" vectors (as local axis/sign pairs).
  const faces=[[p(1,-1,1),p(1,-1,-1),p(1,1,-1),p(1,1,1)],[p(-1,-1,-1),p(-1,-1,1),p(-1,1,1),p(-1,1,-1)],
   [p(-1,-1,1),p(1,-1,1),p(1,1,1),p(-1,1,1)],[p(1,-1,-1),p(-1,-1,-1),p(-1,1,-1),p(1,1,-1)],
   [p(-1,1,1),p(1,1,1),p(1,1,-1),p(-1,1,-1)],[p(-1,-1,-1),p(1,-1,-1),p(1,-1,1),p(-1,-1,1)]];
  for(const f of faces)this.quad(f,key,{color,emissive,uv,tone});
  this.cubes++;
 }
}

function perspective(fovy,aspect,near,far){let f=1/Math.tan(fovy/2),nf=1/(near-far);return new Float32Array([f/aspect,0,0,0,0,f,0,0,0,0,(far+near)*nf,-1,0,0,2*far*near*nf,0])}
function lookAt(eye,target){let z=norm(sub(eye,target)),x=norm(cross(UP,z)),y=cross(z,x);return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1])}
function matmul(a,b){let out=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)out[c*4+r]+=a[k*4+r]*b[c*4+k];return out}

/** Camera: {eye,look} for fixed views, or {target,yaw,pitch,distance} to orbit. */
export function cameraEye(camera){
 if(camera.eye)return camera.eye;
 const{yaw,pitch,distance,target}=camera;
 return add(target,[Math.sin(yaw)*Math.cos(pitch)*distance,Math.sin(pitch)*distance,Math.cos(yaw)*Math.cos(pitch)*distance]);
}

export async function loadTextures(manifest){
 const images={};
 await Promise.all(Object.entries(manifest).map(async([key,url])=>{
  const img=new Image();img.src=url;
  try{await img.decode()}catch{throw Error('Could not load texture: '+url)}
  images[key]=img;
 }));
 return images;
}

export function createRenderer(canvas,images){
 const gl=canvas.getContext('webgl2',{antialias:true,alpha:false,preserveDrawingBuffer:true});
 if(!gl)throw Error('This preview needs WebGL 2. Try a browser with hardware acceleration enabled.');
 const vert=`#version 300 es
 precision highp float;in vec3 position;in vec3 normal;in vec2 uv;in vec4 color;in float emissive;uniform mat4 vp;out vec3 N;out vec2 UV;out vec4 C;out float E;
 void main(){gl_Position=vp*vec4(position,1.);N=normal;UV=uv;C=color;E=emissive;}`;
 // Emissive faces keep their baked shade; lit faces dim with the scene light like ordinary blocks.
 const frag=`#version 300 es
 precision highp float;uniform sampler2D tex;uniform float night;in vec3 N;in vec2 UV;in vec4 C;in float E;out vec4 outputColor;
 void main(){vec4 s=texture(tex,UV);if(s.a<.5)discard;float light=mix(1.,.30,night);light=mix(light,1.,E);outputColor=vec4(s.rgb*C.rgb*light,C.a);}`;
 function shader(type,src){const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s}
 const program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vert));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,frag));gl.linkProgram(program);
 if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);
 const textures={};
 for(const[key,img]of Object.entries(images)){
  const tex=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,tex);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);textures[key]=tex;
 }
 const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);let offset=0;
 for(const[name,size]of[['position',3],['normal',3],['uv',2],['color',4],['emissive',1]]){const loc=gl.getAttribLocation(program,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,52,offset*4);offset+=size}
 const vp=gl.getUniformLocation(program,'vp'),night=gl.getUniformLocation(program,'night');gl.uniform1i(gl.getUniformLocation(program,'tex'),0);gl.enable(gl.DEPTH_TEST);
 return {draw(mesh,camera,dusk){
  const ratio=Math.min(devicePixelRatio||1,2),width=Math.max(1,Math.round(canvas.clientWidth*ratio)),height=Math.max(1,Math.round(canvas.clientHeight*ratio));
  if(canvas.width!==width||canvas.height!==height){canvas.width=width;canvas.height=height}
  gl.viewport(0,0,width,height);gl.clearColor(...(dusk?[.045,.05,.068]:[.53,.70,.92]),1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
  const eye=cameraEye(camera),look=camera.look||camera.target;
  gl.uniformMatrix4fv(vp,false,matmul(perspective((camera.fov||70)*Math.PI/180,width/height,.05,300),lookAt(eye,look)));gl.uniform1f(night,dusk?1:0);
  let draws=0;
  // Opaque batches first, then translucent ones blended without depth writes (like Minecraft's translucent layer).
  const batches=[...mesh.batches].sort((a,b)=>TRANSLUCENT.has(a[0])-TRANSLUCENT.has(b[0]));
  for(const[key,data]of batches){
   if(!textures[key])throw Error('Unknown texture key: '+key);
   const blend=TRANSLUCENT.has(key);if(blend){gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false)}
   gl.bindTexture(gl.TEXTURE_2D,textures[key]);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.DYNAMIC_DRAW);gl.drawArrays(gl.TRIANGLES,0,data.length/13);draws++;
   if(blend){gl.disable(gl.BLEND);gl.depthMask(true)}
  }
  return {quads:mesh.quads,cubes:mesh.cubes,draws,error:gl.getError()};
 }};
}
