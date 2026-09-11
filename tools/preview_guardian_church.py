"""Offline textured WebGL review and stills of the packaged church block layouts."""
import base64,io,json,math,zipfile
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'art/guardian_church'
layouts=json.loads((OUT/'blocks.json').read_text())
jar=Path.home()/'.gradle/caches/fabric-loom/1.21.10/minecraft-client.jar'
textures={}
alias={'grass_block':'grass_block_top','quartz_pillar':'quartz_pillar','chiseled_quartz_block':'chiseled_quartz_block','quartz_block':'quartz_block_side','smooth_stone':'smooth_stone','smooth_quartz':'quartz_block_bottom','quartz_bricks':'quartz_bricks','end_rod':'end_rod','allium':'allium','azure_bluet':'azure_bluet','blue_orchid':'blue_orchid','stone_brick_stairs':'stone_bricks','stone_brick_slab':'stone_bricks','smooth_stone_slab':'smooth_stone','stone_brick_wall':'stone_bricks','spruce_trapdoor':'spruce_trapdoor','bell':'gold_block','chest':'oak_planks','guardian_socket':'chiseled_deepslate','oak_leaves':'oak_leaves'}
# Effigy uses the authored vanilla blocks, without a living entity mesh.
custom_textures={}
materials=sorted(set(v[3] for b in layouts.values() for v in b)|set(custom_textures));n=len(materials);cols=32;rows=math.ceil(n/cols)
atlas=Image.new('RGB',(cols*16,rows*16))
with zipfile.ZipFile(jar) as archive:
 for idx,b in enumerate(materials):
  name=b.split(':')[1];name=alias.get(name,name)
  try:img=custom_textures[b] if b in custom_textures else Image.open(io.BytesIO(archive.read('assets/minecraft/textures/block/'+name+'.png'))).convert('RGBA').crop((0,0,16,16))
  except KeyError:img=Image.new('RGBA',(16,16),(150,155,155,255))
  bg=Image.new('RGBA',(16,16),(120,170,177,255) if 'glass' in name else (60,92,46,255));bg.alpha_composite(img);img=bg.convert('RGB')
  if name=='grass_block_top':img=Image.fromarray((np.asarray(img)*np.array([.48,.76,.29])).astype('uint8'))
  if name=='oak_leaves':img=Image.fromarray((np.asarray(img)*np.array([.5,.85,.38])).astype('uint8'))
  textures[b]=np.asarray(img);atlas.paste(img,(idx%cols*16,idx//cols*16))
buf=io.BytesIO();atlas.save(buf,format='PNG');atlas64=base64.b64encode(buf.getvalue()).decode()
faces=[((1,0,0),[(1,0,0),(1,1,0),(1,1,1),(1,0,1)]),((-1,0,0),[(0,0,1),(0,1,1),(0,1,0),(0,0,0)]),((0,1,0),[(0,1,1),(1,1,1),(1,1,0),(0,1,0)]),((0,-1,0),[(0,0,0),(1,0,0),(1,0,1),(0,0,1)]),((0,0,1),[(1,0,1),(1,1,1),(0,1,1),(0,0,1)]),((0,0,-1),[(0,0,0),(0,1,0),(1,1,0),(1,0,0)])]
def geometry(data,cut=False):
 blocks={(x,y,z):b for x,y,z,b in data if (not cut or y<8) and b not in ['minecraft:allium','minecraft:azure_bluet','minecraft:blue_orchid']}
 for (x,y,z),b in blocks.items():
  for normal,corners in faces:
   if (x+normal[0],y+normal[1],z+normal[2]) in blocks:continue
   yield np.array(corners)+[x,y,z],np.array(normal),b

def render(data,yaw=-32,pitch=27,w=1100,h=1020,cut=False,center=(0,10,16),scale=15):
 a,b=map(math.radians,(yaw,pitch));cam=np.array([[math.cos(a),0,math.sin(a)],[-math.sin(a)*math.sin(b),math.cos(b),math.cos(a)*math.sin(b)],[math.sin(a)*math.cos(b),math.sin(b),-math.cos(a)*math.cos(b)]])
 rgb=np.full((h,w,3),[225,225,215],dtype='uint8');depth=np.full((h,w),-1e9)
 for quad,normal,block in geometry(data,cut):
  if np.dot(normal,cam[2])<=0:continue
  transformed=(quad-center)@cam.T;transformed[:,0]=transformed[:,0]*scale+w/2;transformed[:,1]=-transformed[:,1]*scale+h/2
  uv=np.array([[0,1],[0,0],[1,0],[1,1]])
  for ids in ([0,1,2],[0,2,3]):
   v=transformed[ids];tex=uv[ids];xmin=max(0,int(np.floor(v[:,0].min())));xmax=min(w-1,int(np.ceil(v[:,0].max())));ymin=max(0,int(np.floor(v[:,1].min())));ymax=min(h-1,int(np.ceil(v[:,1].max())))
   if xmin>xmax or ymin>ymax:continue
   xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5);p,q,r=v;den=(q[1]-r[1])*(p[0]-r[0])+(r[0]-q[0])*(p[1]-r[1])
   if abs(den)<1e-8:continue
   aa=((q[1]-r[1])*(xx-r[0])+(r[0]-q[0])*(yy-r[1]))/den;bb=((r[1]-p[1])*(xx-r[0])+(p[0]-r[0])*(yy-r[1]))/den;cc=1-aa-bb;zz=aa*p[2]+bb*q[2]+cc*r[2];region=depth[ymin:ymax+1,xmin:xmax+1];mask=(aa>=0)&(bb>=0)&(cc>=0)&(zz>region)
   coords=aa[...,None]*tex[0]+bb[...,None]*tex[1]+cc[...,None]*tex[2];tx=np.clip((coords[...,0]*16).astype(int),0,15);ty=np.clip((coords[...,1]*16).astype(int),0,15)
   shade=.64+.32*max(0,normal[1])+.18*max(0,-normal[2]);color=np.clip(textures[block][ty,tx]*shade,0,255).astype('uint8');region[mask]=zz[mask];rgb[ymin:ymax+1,xmin:xmax+1][mask]=color[mask]
 return Image.fromarray(rgb)
font='/System/Library/Fonts/Supplemental/Arial.ttf';bold='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
sheet=Image.new('RGB',(2200,1180),(225,225,215));draw=ImageDraw.Draw(sheet)
draw.text((55,35),'THE KEEPER’S SANCTUARY',font=ImageFont.truetype(bold,38),fill=(36,48,52));draw.text((55,90),'Actual block layout · ruined discovery / restored after victory',font=ImageFont.truetype(font,24),fill=(78,88,86))
for i,(name,data) in enumerate(layouts.items()):
 img=render(data);img.save(OUT/(name+'.png'));sheet.paste(img,(i*1100,145));draw.text((i*1100+55,150),name.upper(),font=ImageFont.truetype(bold,23),fill=(49,67,71))
sheet.save(OUT/'comparison.png')
statue=[v for v in layouts['ruined'] if -5<=v[0]<=5 and -1<=v[1]<=7 and -6<=v[2]<=0]
render(statue,yaw=-20,pitch=14,w=900,h=850,center=(0,3,-3),scale=68).save(OUT/'statue.png')
render(layouts['restored'],yaw=-25,pitch=52,cut=True).save(OUT/'interior.png')
# Compact exposed-face triangle meshes; vanilla texture tiles retain 1x1 scale.
meshes={}
for name,data in layouts.items():
 arr=[]
 for q,normal,block in geometry(data):
  i=materials.index(block);uv=[(.03,.97),(.03,.03),(.97,.03),(.97,.97)]
  for k in [0,1,2,0,2,3]:arr.extend([*q[k],*normal,(i%cols+uv[k][0])/cols,(i//cols+uv[k][1])/rows])
 meshes[name]=base64.b64encode(np.array(arr,dtype=np.float32).tobytes()).decode()
html='''<!doctype html><html><head><meta charset="utf-8"><title>The Keeper's Sanctuary — Build Preview</title><style>
*{box-sizing:border-box}body{margin:0;background:#e1e1d7;color:#243034;font:16px system-ui}header{padding:26px 34px}h1{margin:0 0 8px;font-family:Georgia;font-size:34px}p{margin:8px 0;color:#52615e}.views{display:flex;gap:2px}.view{flex:1;min-width:0;width:50%;position:relative;overflow:hidden}canvas{display:block;width:100%;height:70vh;touch-action:none}.label{z-index:1;position:absolute;left:32px;top:15px;letter-spacing:3px;font-size:13px;pointer-events:none}button{padding:10px 18px;background:#243c40;color:white;border:0;border-radius:4px;margin-right:8px;cursor:pointer}footer{padding:16px 34px;font-size:13px;color:#52615e}@media(max-width:800px){.views{display:block}.view{width:100%}canvas{height:55vh}}
</style></head><body><header><h1>The Keeper's Sanctuary</h1><p>Two moments of the same place. Drag to orbit · scroll to zoom · both views move together.</p><button id="reset">Front courtyard</button><button id="rear">Rear sanctuary</button><button id="top">Overhead</button><p>33 × 53 block footprint · carved arches & broken-ring glyphs · open summoning courtyard</p></header><div class="views"><div class="view"><span class="label">RUINED — DISCOVERY</span><canvas id="ruined"></canvas></div><div class="view"><span class="label">RESTORED — VICTORY</span><canvas id="restored"></canvas></div></div><footer>Preview uses the authored block coordinates and vanilla texture tiles. Slabs, wall blocks, chests, and end rods use simplified block envelopes; flowers are omitted. The guardian effigy is made from the same ordinary blocks as the in-game build. The flat ground here is illustrative: in Minecraft the side courtyard preserves natural terrain and the entrance receives local steps/soil blending. Minecraft supplies animation, final block models and lighting. <a href="statue.png">Guardian statue</a> · <a href="interior.png">Interior cutaway</a> · <a href="comparison.png">Save comparison image</a></footer><script>
const meshes=MESHES, atlasSource='data:image/png;base64,ATLAS';
let yaw=-.56,pitch=.47,zoom=1;
const views=[];const image=new Image();
function setup(id){const canvas=document.getElementById(id),g=canvas.getContext('webgl');if(!g){canvas.outerHTML='<p>WebGL unavailable. Open comparison.png.</p>';return}
const vs=`attribute vec3 pos;attribute vec3 normal;attribute vec2 uv;uniform mat3 camera;uniform vec2 scale;varying vec2 tex;varying float light;void main(){vec3 p=camera*(pos-vec3(0.,10.,16.));gl_Position=vec4(p.x*scale.x,p.y*scale.y,-p.z/180.,1.);tex=uv;light=.64+.32*max(0.,normal.y)+.18*max(0.,-normal.z);}`;
const fs=`precision mediump float;varying vec2 tex;varying float light;uniform sampler2D atlas;void main(){gl_FragColor=vec4(texture2D(atlas,tex).rgb*light,1.);}`;
function shader(type,src){let s=g.createShader(type);g.shaderSource(s,src);g.compileShader(s);if(!g.getShaderParameter(s,g.COMPILE_STATUS))throw Error(g.getShaderInfoLog(s));return s}let p=g.createProgram();g.attachShader(p,shader(g.VERTEX_SHADER,vs));g.attachShader(p,shader(g.FRAGMENT_SHADER,fs));g.linkProgram(p);if(!g.getProgramParameter(p,g.LINK_STATUS))throw Error(g.getProgramInfoLog(p));g.useProgram(p);
let bytes=Uint8Array.from(atob(meshes[id]),c=>c.charCodeAt(0)),data=new Float32Array(bytes.buffer);let buf=g.createBuffer();g.bindBuffer(g.ARRAY_BUFFER,buf);g.bufferData(g.ARRAY_BUFFER,data,g.STATIC_DRAW);for(let [name,size,offset] of [['pos',3,0],['normal',3,12],['uv',2,24]]){let a=g.getAttribLocation(p,name);g.enableVertexAttribArray(a);g.vertexAttribPointer(a,size,g.FLOAT,false,32,offset)}let t=g.createTexture();g.bindTexture(g.TEXTURE_2D,t);g.texImage2D(g.TEXTURE_2D,0,g.RGB,g.RGB,g.UNSIGNED_BYTE,image);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MIN_FILTER,g.NEAREST);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_MAG_FILTER,g.NEAREST);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_S,g.CLAMP_TO_EDGE);g.texParameteri(g.TEXTURE_2D,g.TEXTURE_WRAP_T,g.CLAMP_TO_EDGE);g.enable(g.DEPTH_TEST);
views.push(()=>{let w=canvas.clientWidth*devicePixelRatio,h=canvas.clientHeight*devicePixelRatio;canvas.width=w;canvas.height=h;g.viewport(0,0,w,h);g.clearColor(.882,.882,.843,1);g.clear(g.COLOR_BUFFER_BIT|g.DEPTH_BUFFER_BIT);let a=Math.cos(yaw),b=Math.sin(yaw),c=Math.cos(pitch),d=Math.sin(pitch);g.uniformMatrix3fv(g.getUniformLocation(p,'camera'),false,new Float32Array([a,-b*d,b*c,0,c,d,b,a*d,-a*c]));g.uniform2f(g.getUniformLocation(p,'scale'),zoom*1.8/70*Math.min(w,h)/w,zoom*1.8/70*Math.min(w,h)/h);g.drawArrays(g.TRIANGLES,0,data.length/8)});
let last;canvas.onpointerdown=e=>{last=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId)};canvas.onpointermove=e=>{if(!last)return;yaw+=(e.clientX-last[0])*.007;pitch=Math.max(-.1,Math.min(1.45,pitch+(e.clientY-last[1])*.007));last=[e.clientX,e.clientY];draw()};canvas.onpointerup=()=>last=null;canvas.onpointercancel=()=>last=null;canvas.onwheel=e=>{e.preventDefault();zoom=Math.max(.5,Math.min(3,zoom*Math.exp(-e.deltaY*.001)));draw()};}
function draw(){views.forEach(v=>v())}image.onload=()=>{setup('ruined');setup('restored');draw()};image.src=atlasSource;window.onresize=draw;document.getElementById('reset').onclick=()=>{yaw=-.56;pitch=.47;zoom=1;draw()};document.getElementById('rear').onclick=()=>{yaw=2.6;pitch=.47;zoom=1;draw()};document.getElementById('top').onclick=()=>{pitch=1.4;zoom=1;draw()};
</script></body></html>'''.replace('MESHES',json.dumps(meshes)).replace('ATLAS',atlas64)
(OUT/'preview.html').write_text(html)
print('Wrote comparison.png, interior.png, and offline textured preview.html')
