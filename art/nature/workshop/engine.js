export const add=(a,b)=>a.map((v,i)=>v+b[i]), sub=(a,b)=>a.map((v,i)=>v-b[i]), mul=(a,k)=>a.map(v=>v*k), dot=(a,b)=>a.reduce((v,n,i)=>v+n*b[i],0), cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]], norm=a=>mul(a,1/(Math.hypot(...a)||1)), mix=(a,b,t)=>add(mul(a,1-t),mul(b,t));
export const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x)), smooth=x=>{x=clamp(x);return x*x*(3-2*x)}, rnd=x=>{let a=Math.sin(x*127.1+311.7)*43758.5453;return a-Math.floor(a)}, TAU=Math.PI*2;
export const C={bark:[.39,.25,.13],wood:[.54,.37,.16],root:[.18,.52,.10],leaf:[.19,.72,.13],lightleaf:[.47,.89,.19],darkleaf:[.10,.43,.14],petal:[.94,.20,.56],petallight:[1.,.45,.69],violet:[.60,.27,.86],gold:[1.,.80,.15],sap:[.66,1.,.19],stone:[.45,.49,.45],shadow:[.17,.21,.17]};
export class Mesh{
 constructor(){this.data=[];this.triangles=0;this.offset=[0,0,0]}
 at(offset,draw){let prior=this.offset;this.offset=add(prior,offset);try{draw()}finally{this.offset=prior}}
 tri(a,b,c,color,mat=0){let n=norm(cross(sub(b,a),sub(c,a)));for(let p of [a,b,c])this.data.push(...add(p,this.offset),...n,...color,mat);this.triangles++}
 quad(a,b,c,d,color,mat=0){this.tri(a,b,c,color,mat);this.tri(a,c,d,color,mat)}
 box(p,s,col,mat=4,rotation=0){let ps=[];for(let x of [-1,1])for(let y of [-1,1])for(let z of [-1,1]){let xx=x*s[0]/2,zz=z*s[2]/2;ps.push(add(p,[xx*Math.cos(rotation)+zz*Math.sin(rotation),y*s[1]/2,-xx*Math.sin(rotation)+zz*Math.cos(rotation)]))}for(let f of [[0,1,3,2],[4,6,7,5],[0,4,5,1],[2,3,7,6],[0,2,6,4],[1,5,7,3]])this.quad(...f.map(i=>ps[i]),col,mat)}
 // Square beams, stepped leaf plates, and tiered buds share a voxel-like silhouette.
 tube(points,radius,col=C.bark,mat=0,sides=4,endRadius=radius*.5){
  sides=4;
  for(let i=0;i<points.length-1;i++){
   let a=points[i],b=points[i+1];if(Math.hypot(...sub(b,a))<.00001)continue;
   let axis=norm(sub(b,a)),u=norm(cross(axis,Math.abs(axis[1])>.95?[1,0,0]:[0,1,0])),v=cross(axis,u),ra=radius+(endRadius-radius)*i/(points.length-1),rb=radius+(endRadius-radius)*(i+1)/(points.length-1);
   for(let k=0;k<4;k++){let q=Math.PI/4+k*TAU/4,qq=Math.PI/4+(k+1)*TAU/4,off=(r,t)=>add(mul(u,Math.cos(t)*r*1.4142),mul(v,Math.sin(t)*r*1.4142));this.quad(add(a,off(ra,q)),add(a,off(ra,qq)),add(b,off(rb,qq)),add(b,off(rb,q)),col,mat);if(i===0)this.tri(a,add(a,off(ra,qq)),add(a,off(ra,q)),col,mat);if(i===points.length-2)this.tri(b,add(b,off(rb,q)),add(b,off(rb,qq)),col,mat)}
  }
 }
 leaf(a,b,width,col=C.leaf,mat=1,fold=.08){
  let dir=norm(sub(b,a)),side=norm(cross(dir,[0,1,.001])),up=norm(cross(side,dir)),len=Math.hypot(...sub(b,a));
  if(len<.005||width<.003)return;
  const widths=[.28,.62,.94,1,.72,.32],thickness=Math.max(.008,Math.min(.055,width*.14));
  for(let i=0;i<6;i++){
   let lo=i/6,hi=(i+1)/6,w=width*widths[i];
   const pt=(t,x,y)=>add(mix(a,b,t),add(mul(side,x),mul(up,y)));
   let corners=[pt(lo,-w,-thickness),pt(lo,w,-thickness),pt(hi,w,-thickness),pt(hi,-w,-thickness),pt(lo,-w,thickness),pt(lo,w,thickness),pt(hi,w,thickness),pt(hi,-w,thickness)];
   for(let f of [[0,3,2,1],[4,5,6,7],[0,1,5,4],[1,2,6,5],[2,3,7,6],[3,0,4,7]])this.quad(...f.map(k=>corners[k]),mul(col,f[0]===4?1.02:.91),mat);
   if(mat===1){let veinWidth=Math.max(.005,width*.085),h=thickness+.002;this.quad(pt(lo,-veinWidth,h),pt(lo,veinWidth,h),pt(hi,veinWidth,h),pt(hi,-veinWidth,h),mul(col,1.15),mat)}
  }
 }
 gem(p,r,col=C.gold,mat=3){
  this.box(p,[r*1.5,r*1.5,r*1.5],col,mat);
  this.box(add(p,[0,r*.82,0]),[r*.88,r*.30,r*.88],mul(col,1.04),mat);
  this.box(add(p,[0,-r*.82,0]),[r*.88,r*.30,r*.88],mul(col,.90),mat);
 }

}
export function createRenderer(canvas){let gl=canvas.getContext('webgl2',{antialias:true,alpha:false,preserveDrawingBuffer:true});if(!gl)throw Error('This preview needs WebGL 2. Open it in a browser with hardware acceleration enabled.');let vs=`#version 300 es
precision highp float;in vec3 p;in vec3 n;in vec3 c;in float m;uniform mat4 vp;out vec3 N;out vec3 P;out vec3 C;out float M;void main(){P=p;N=n;C=c;M=m;gl_Position=vp*vec4(p,1.);}`;
let fs=`#version 300 es
precision highp float;in vec3 N;in vec3 P;in vec3 C;in float M;uniform vec3 eye;uniform vec3 fog;uniform float night;uniform sampler2D oakLog;uniform sampler2D oakTop;uniform sampler2D oakLeaves;out vec4 outColor;float hash(vec3 p){return fract(sin(dot(p,vec3(12.9898,78.233,37.719)))*43758.5453);}void main(){vec3 nn=normalize(N)*(gl_FrontFacing?1.:-1.);float light=.52+.38*max(0.,dot(nn,normalize(vec3(-.6,1.,.7))))+.10*max(0.,nn.y);light=mix(light,light*.65,night);vec3 cells=floor(P*(M<.5?16.:M<1.5?16.:M<2.5?20.:18.));float grain=hash(cells);float noise=.91+grain*.15;if(M<.5){float groove=hash(vec3(floor(P.x*24.),floor(P.y*4.),floor(P.z*24.)));noise*=groove>.85?.78:1.;}if(M>2.5&&M<3.5){light=1.12;noise=.94+grain*.10;}vec3 col=C*light*noise;if(M>5.5&&M<6.5){col=mix(vec3(.11,.28,.25),vec3(.25,.47,.40),.5+.5*sin(P.x*3.+P.z*4.));col*=light;}if(M>7.5){vec3 an=abs(N);vec2 uv=an.y>.5?P.xz+vec2(.5):an.x>.5?vec2(P.z+.5,P.y-.02):vec2(P.x+.5,P.y-.02);bool leaf=M>9.5&&M<10.5;bool endFace=(M<8.5&&an.y>.5)||(M>8.5&&M<9.5&&an.x>.5)||(M>10.5&&an.z>.5);if(!leaf&&M>8.5&&!endFace)uv=uv.yx;vec4 texel=leaf?texture(oakLeaves,fract(uv)):endFace?texture(oakTop,fract(uv)):texture(oakLog,fract(uv));if(texel.a<.5)discard;col=texel.rgb*C*light;}float dist=length(eye-P);float haze=clamp((dist-16.)/70.,0.,.62);col=mix(col,fog,haze);outColor=vec4(pow(max(col,vec3(0.)),vec3(.87)),1.);}`;
function shader(type,src){let s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s}let prog=gl.createProgram();gl.attachShader(prog,shader(gl.VERTEX_SHADER,vs));gl.attachShader(prog,shader(gl.FRAGMENT_SHADER,fs));gl.linkProgram(prog);if(!gl.getProgramParameter(prog,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(prog));gl.useProgram(prog);
for(let [unit,name,file]of [[0,'oakLog','oak_log'],[1,'oakTop','oak_log_top'],[2,'oakLeaves','oak_leaves']]){
 let texture=gl.createTexture();gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([180,180,180,255]));gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.REPEAT);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.REPEAT);gl.uniform1i(gl.getUniformLocation(prog,name),unit);
 let img=new Image();img.onload=()=>{gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img)};img.onerror=()=>console.error('Could not load Minecraft reference texture: '+file);img.src='textures/'+file+'.png';
}
let buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);for(let [name,size,off]of [['p',3,0],['n',3,3],['c',3,6],['m',1,9]]){let loc=gl.getAttribLocation(prog,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,40,off*4)}gl.enable(gl.DEPTH_TEST);let uniforms=Object.fromEntries(['vp','eye','fog','night'].map(k=>[k,gl.getUniformLocation(prog,k)]));return{gl,draw(mesh,camera,night=false){let ratio=Math.min(devicePixelRatio,2),w=Math.round(canvas.clientWidth*ratio),h=Math.round(canvas.clientHeight*ratio);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h}gl.viewport(0,0,w,h);let fog=night?[.045,.085,.065]:[.12,.185,.135];gl.clearColor(...fog,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.useProgram(prog);gl.uniform3fv(uniforms.fog,fog);gl.uniform1f(uniforms.night,night?1:0);let {yaw,pitch,distance,target}=camera,eye=add(target,[Math.sin(yaw)*Math.cos(pitch)*distance,Math.sin(pitch)*distance,Math.cos(yaw)*Math.cos(pitch)*distance]);gl.uniform3fv(uniforms.eye,eye);gl.uniformMatrix4fv(uniforms.vp,false,matmul(perspective(Math.PI/4.5,w/h,.05,160),lookAt(eye,target)));gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(mesh.data),gl.DYNAMIC_DRAW);gl.drawArrays(gl.TRIANGLES,0,mesh.data.length/10);}}}
function perspective(fovy,aspect,near,far){let f=1/Math.tan(fovy/2),nf=1/(near-far);return new Float32Array([f/aspect,0,0,0,0,f,0,0,0,0,(far+near)*nf,-1,0,0,2*far*near*nf,0])}
function lookAt(eye,target){let z=norm(sub(eye,target)),x=norm(cross([0,1,0],z)),y=cross(z,x);return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1])}
function matmul(a,b){let o=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)o[c*4+r]+=a[k*4+r]*b[c*4+k];return o}
