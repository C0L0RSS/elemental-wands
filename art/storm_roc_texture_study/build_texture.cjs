// Pack generated artwork into existing UV islands. No model or UV changes.
const fs=require('fs'),path=require('path');
// Sharp from npm, or another copy named by SHARP_PATH.
const sharp=require(process.env.SHARP_PATH||'sharp');
const here=__dirname,root=path.resolve(here,'../..'),layout=JSON.parse(fs.readFileSync(path.join(here,'layout.json')));
const scale=1; // Original Minecraft pixel density and unchanged normalized UVs.
async function main(){
 const sheet=path.join(here,'material-sheet-pixel-v2.png'),meta=await sharp(sheet).metadata();
 const sw=Math.floor(meta.width/4),sh=Math.floor(meta.height/2),panels=[];
 // All repeating surfaces use one common art-pixel density. Cropping, rather
 // than squeezing a whole material onto each narrow side, avoids striped seams.
 for(let i=0;i<8;i++)panels.push(await sharp(sheet).extract({left:i%4*sw,top:Math.floor(i/4)*sh,width:sw,height:sh}).resize(32,64,{kernel:'nearest'}).ensureAlpha().raw().toBuffer());
 const original=path.join(root,'art/storm_roc_mod/candidate/storm_roc.png');
 const layers=[];
 for(const p of layout.parts){
  const wing=/wing_|primary_|secondary_|root_tertial|tail_/.test(p.bone);
  let panel=wing?(['feather','tip'].includes(p.kind)?3:2):p.mat==='crown'||p.mat==='crest'||p.bone.startsWith('crest')?4:p.mat==='mask'||p.mat==='talon'?5:p.mat.startsWith('horn')?6:p.mat==='skin'?7:p.kind==='hackle'||p.mat==='ruff'?1:0;
  for(const [face,uv] of Object.entries(p.cube.uv)){
   const [x,y]=uv.uv,[w,h]=uv.uv_size,W=w*scale,H=h*scale;
   let image;
   if(p.kind==='eye'||p.mat==='mouth')image=sharp(original).extract({left:x,top:y,width:w,height:h}).resize(W,H,{kernel:'nearest'});
   else {
    const raw=Buffer.alloc(W*H*4),src=panels[panel];
    for(let v=0;v<H;v++)for(let u=0;u<W;u++){
     let sx=u,sy=v;
     if(panel===3){
      if(['up','down'].includes(face)){sx=Math.floor(u/W*32);sy=Math.floor(v/H*64);if(face==='up')sy=63-sy;}
      else if(['east','west'].includes(face)){sx=4+v;sy=Math.floor(u/W*64);if(face==='west')sy=63-sy;}
     }else if(wing&&['arm','covert'].includes(p.kind)&&['up','down'].includes(face)){
      sx=v;sy=p.trail>0?W-1-u:u;
     }else if(face==='up')sy=H-1-v;
     sx=((sx%32)+32)%32;sy=((sy%64)+64)%64;
     src.copy(raw,(v*W+u)*4,(sy*32+sx)*4,(sy*32+sx)*4+4);
    }
    image=sharp(raw,{raw:{width:W,height:H,channels:4}});
   }
   const input=await image.extend({top:scale,bottom:scale,left:scale,right:scale,extendWith:'copy'}).png().toBuffer();
   layers.push({input,left:(x-1)*scale,top:(y-1)*scale});
  }
 }
 await sharp({create:{width:layout.atlas[0]*scale,height:layout.atlas[1]*scale,channels:4,background:'#00000000'}}).composite(layers).png().toFile(path.join(here,'storm-roc-vulture-v1.png'));
 // Preserve existing lightning accents exactly; they sit over the new feather albedo.
 console.log(`Packed ${layers.length} existing face islands into ${layout.atlas[0]*scale}px atlas`);
}
main().catch(e=>{console.error(e);process.exit(1)});
