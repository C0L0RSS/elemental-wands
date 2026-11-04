// Exact workshop geometry and world-up fire, shared by offline rendering/checks.
const fs=require('fs'),vm=require('vm'),path=require('path');
const ctx={};vm.createContext(ctx);
for(const file of ['core.js','effects.js'])vm.runInContext(fs.readFileSync(path.join(__dirname,'workshop',file),'utf8'),ctx);
const {bakeModel,poseBones,M,norm}=ctx.PyreCore;
const geo=JSON.parse(fs.readFileSync(process.argv[2]==='-'?0:process.argv[2],'utf8'));
const model=bakeModel(geo);
let result;
if(process.argv.includes('--effects')){
 const manifest=JSON.parse(fs.readFileSync(path.join(path.dirname(process.argv[2]),'manifest.json')));
 const time=process.argv.includes('--sweep')?1.8:.65;
 result=ctx.PyreEffects.scene(model,manifest,time,process.argv.includes('--sweep'),process.argv.includes('--planted'));
}else{
 const pose=poseBones(model,null,0);
 result={faces:model.faces.filter(f=>!f.empty).map(f=>{const m=pose.matrices[f.bone];return {bone:model.bones[f.bone].name,pts:f.pts.map(p=>M.apply(m,p)),n:norm(M.dir(m,f.n)),uvs:f.uvs};})};
}
process.stdout.write(JSON.stringify({...result,bones:model.bones.map(b=>b.name),cubes:model.cubes.length}));
