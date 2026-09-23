// Export the approved preview geometry verbatim. No PNGs are created or replaced.
import fs from 'node:fs';
import {Mesh} from '../art/nature/workshop/engine.js';
import {springbloom,jumpPod,DESIGN} from '../art/nature/springbloom/model.js';
import {ClippedMesh} from '../art/nature/springbloom/clipped-mesh.js';
const meshes={};
for(const [name,draw] of [['springbloom_pad',m=>springbloom(m)],['springbloom_wilt',m=>springbloom(m,{wilt:.18})],['springbloom_pod',m=>jumpPod(m)]]){
const m=new Mesh();draw(m);if(!m.data.every(Number.isFinite))throw Error('Invalid mesh '+name);
meshes[name]=m.data.map(v=>Math.round(v*100000)/100000);
if(name==='springbloom_pad'){let top=0;for(let i=1;i<m.data.length;i+=10)top=Math.max(top,m.data[i]);if(Math.abs(top-DESIGN.height)>1e-6)throw Error('Crown height drift');}
}
for(const [name,options] of [['springbloom_pad',{}],['springbloom_wilt',{wilt:.18}]]) {
  for(let x=-1;x<=1;x++)for(let z=-1;z<=1;z++) {
    const cell=(x+1)*3+z+1,m=new ClippedMesh(x,z);springbloom(m,options);
    if(!m.data.every(Number.isFinite))throw Error('Invalid clipped mesh');
    for(let i=0;i<m.data.length;i+=10)if(m.data[i]<x-.500001 || m.data[i]>x+.500001 || m.data[i+2]<z-.500001 || m.data[i+2]>z+.500001)
      throw Error('Geometry escapes its block column');
    meshes[name+'_cell_'+cell]=m.data.map(v=>Math.round(v*100000)/100000);
  }
}
const path=new URL('../src/main/resources/assets/elementalwands/nature/springbloom.json',import.meta.url),text=JSON.stringify(meshes)+'\n';
if(process.argv.includes('--check')){if(fs.readFileSync(path,'utf8')!==text)throw Error('Springbloom export drift');}
else fs.writeFileSync(path,text);
console.log('Springbloom: 3 full meshes and 18 capped column pieces '+(process.argv.includes('--check')?'verified':'exported')+'; no bitmap changes.');
