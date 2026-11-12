// Sentry Bloom: the Nature wand's planted seed-spitting flower.
// Authored on the Minecraft pixel grid (16 px = 1 block). The flower is cut in two:
//  - sentryBloom(): the base block model (root crown, braced leaves, stem), origin at
//    the bottom centre of the cell. Its stem top at stage 3 is the head's pivot.
//  - sentryHead(): the head render mesh (knuckle, seed sac, petals, throat, seed),
//    pivot at the origin and mouth along +Y. The game turns it to face its target.
// Same design as the approved radially symmetric bloom, re-cut slightly more compact
// so it can tip past horizontal without sinking into its own leaves.
import{C}from'./engine.js';

const PX=1/16;
// Stem top per growth stage, in pixels; stage 3 is the head pivot.
export const SENTRY_STEM_TOP_PX=[4.5,7,9,11];
// Head layout along +Y, in pixels from the pivot: a short neck lifts the sac clear
// of the stem so the head can tip a little past horizontal.
const SAC_BASE=3.5,SAC_TOP=6.5;
export const SENTRY_PIVOT_HEIGHT=SENTRY_STEM_TOP_PX[3]*PX;   // 0.6875 blocks
// Centre of the loaded seed along the head's +Y axis from the pivot.
export const SENTRY_MUZZLE_OFFSET=(SAC_TOP+2)*PX;
// Muzzle height when the head is upright (rest pose).
export const SENTRY_MUZZLE_HEIGHT=SENTRY_PIVOT_HEIGHT+SENTRY_MUZZLE_OFFSET;

const P={...C,throat:[.30,.10,.42],sac:[.25,.60,.14],rib:[.13,.40,.12],cap:[1,.92,.45]};
// Wilt: the same pieces, faded toward dry brown.
const fade=(c,k=.55,to=[.42,.33,.20])=>c.map((v,i)=>v*(1-k)+to[i]*k);
const W={...P,petal:fade(P.petal,.5),petallight:fade(P.petallight,.55),violet:fade(P.violet,.55),
 throat:fade(P.throat,.35),sac:fade(P.sac,.55),rib:fade(P.rib,.5),gold:fade(P.gold,.6),root:fade(P.root,.4)};

// Mirror a piece authored on the +X side around the axis to all four sides.
function fourWith(px){return (x0,y0,z0,x1,y1,z1,c)=>{for(let k=0;k<4;k++){let r=[[x0,z0,x1,z1],[-z1,x0,-z0,x1],[-x1,-z1,-x0,-z0],[z0,-x1,z1,-x0]][k];px(r[0],y0,r[1],r[2],y1,r[3],c)}}}
// A piece on each of the four diagonals, spanning radius a..b on both axes.
const CORNERS=[[1,1],[1,-1],[-1,1],[-1,-1]];
function cornerWith(px){return (a,b,y0,y1,c,only=CORNERS)=>{for(let [sx,sz]of only)px(Math.min(sx*a,sx*b),y0,Math.min(sz*a,sz*b),Math.max(sx*a,sx*b),y1,Math.max(sz*a,sz*b),c)}}

// Base block model. stage 0..3: quick unfurl (0-2) into the held pose (3).
export function sentryBloom(m,p=[0,0,0],stage=3){
 stage=Math.max(0,Math.min(3,stage));
 const px=(x0,y0,z0,x1,y1,z1,c)=>m.box([p[0]+(x0+x1)/32,p[1]+(y0+y1)/32,p[2]+(z0+z1)/32],[(x1-x0)/16,(y1-y0)/16,(z1-z0)/16],c);
 const four=fourWith(px);
 const stemTop=SENTRY_STEM_TOP_PX[stage];

 // Root crown and four braced leaves: stepped plates running down to the soil.
 const spread=[.45,.7,.9,1][stage];
 px(-3,0,-3,3,1.5,3,P.bark);
 four(3,1.5,-1,4,2.5,1,P.bark);
 const brace=[[2,4,2.5,4.5,2.5],[4,5.5,1.5,3.5,2.5],[5.5,7,.5,2.5,2],[7,8,0,1,1.5]];
 for(let [i,[a,b,y0,y1,w]]of brace.entries()){
  if(a*spread>7.5)continue;
  let A=Math.max(2,a*spread),B=Math.max(A+.5,b*spread),lift=(1-spread)*3*(i+1)/2;
  four(A,y0+lift,-w,B,y1+lift,w,i%2?P.leaf:P.darkleaf);
  if(i<3)four(A,y1+lift,-.5,B,y1+lift+.5,.5,P.lightleaf); // midrib
 }

 // Stem: square, thicker at the foot, with a bark node. It ends at the pivot,
 // where the head's own knuckle sits over it at every angle.
 const mid=Math.min(4,stemTop*.5);
 px(-2,1.5,-2,2,mid,2,P.root);
 px(-1.5,mid,-1.5,1.5,stemTop,1.5,P.root);
 if(stage>=1)px(-2.5,mid-.5,-2.5,2.5,mid+.5,2.5,P.bark);
}

// Head render mesh, in blocks, pivot at the origin, mouth along +Y.
// pose: 'loaded' (ready), 'fired' (just spat), 'wilt' (last seconds).
export function sentryHead(m,pose='loaded'){
 const wilt=pose==='wilt',fired=pose==='fired',c=wilt?W:P;
 const px=(x0,y0,z0,x1,y1,z1,col)=>m.box([(x0+x1)/32,(y0+y1)/32,(z0+z1)/32],[(x1-x0)/16,(y1-y0)/16,(z1-z0)/16],col);
 const four=fourWith(px),corner=cornerWith(px);

 // Knuckle: the bend joint, stem-coloured, so the stem top it swallows at any tilt
 // reads as one bending stalk.
 px(-1.75,-1.75,-1.75,1.75,1.75,1.75,c.root);
 px(-1.25,1.5,-1.25,1.25,4,1.25,c.root);     // neck
 // Tapered calyx: small heel, wider shoulder, no seed studs.
 px(-1.8,4,-1.8,1.8,4.5,1.8,c.rib);
 px(-2.5,4.5,-2.5,2.5,6,2.5,c.sac);
 px(-3,6,-3,3,7,3,c.sac);
 px(-1.8,7,-1.8,1.8,7.7,1.8,c.throat);
 four(1.65,7.5,-1.65,2.2,8.8,1.65,c.violet);
 // Eight overlapping stepped petals form a cup around the small open throat.
 const k=fired?.88:1;
 const r=a=>1.8+(a-1.8)*k;
 four(2.1,6,-1.7,r(3.5),8,1.7,c.petal);
 corner(1.8,r(3.2),6.5,8,c.petal);
 if(!wilt){
  four(r(3),7.5,-2,r(4.6),9.3,2,c.petal);
  four(r(4),9,-1.7,r(5.4),10,1.7,c.petallight);
  corner(r(2.8),r(4.2),8,9.5,c.petal);
  corner(r(3.4),r(4.7),9.3,10.3,c.petallight);
 }else{
  four(3,7,-2,4.7,8.5,2,c.petal);
  four(4.2,8,-1.7,5.3,9,1.7,c.petallight);
  corner(2.8,4.2,7,8.5,c.petal);
  corner(3.5,4.7,8,9,c.petallight);
 }
 // Flat pollen-gold seed, centred exactly at the gameplay muzzle.
 if(pose==='loaded'){
  const mz=SENTRY_MUZZLE_OFFSET*16;
  px(-1.25,mz-.4,-1.25,1.25,mz+.4,1.25,c.gold);
  px(-.85,mz+.4,-.85,.85,mz+.5,.85,c.cap);
 }
}

// Full Bloom: one tall leaning stem and a smaller lateral shoot. Mounts are in
// blocks from the bottom centre, in model axes (+X east, +Z south), main first.
export const SENTRY_GROWN_STEM_TOPS=[[5/16,24/16,0],[-11/16,18/16,2/16]];
export const SENTRY_GROWN_HEAD_SCALES=[1,.75];
export const SENTRY_GROWN_MAX_TILT=105;
export function sentryBloomGrown(m,p=[0,0,0]){
 const px=(a,b,c,d,e,f,col)=>m.box([p[0]+(a+d)/32,p[1]+(b+e)/32,p[2]+(c+f)/32],[(d-a)/16,(e-b)/16,(f-c)/16],col);
 const four=fourWith(px);
 px(-4,0,-4,4,2,4,P.bark);
 four(4,1,-1.5,5,3,1.5,P.bark);
 for(const [a,b,y0,y1,w] of [[2,6,3,5,4.5],[6,10,2,4,4.5],[10,13,1,3,4],[13,16,0,1.5,3]]){
  four(a,y0,-w,b,y1,w,P.darkleaf);
  four(a,y1,-.5,b,y1+.5,.5,P.lightleaf);
 }
 // Four additional diagonal leaves fill the gaps in the larger root rosette.
 const corner=cornerWith(px);
 corner(4,7,2,4,P.darkleaf);
 corner(6.5,10,1,3,P.leaf);
 corner(9.5,12,0,1.5,P.darkleaf);
 px(-2.5,2,-2.5,2.5,7,2.5,P.root);
 px(-3,5.5,-3,3,6.5,3,P.bark);
 // Continuous main stalk: broad at the node, then bending east, away from the side
 // shoot, so the two heads balance each other.
 px(-1.5,6,-1.5,1.5,12,1.5,P.root);
 px(-.5,11,-1.5,2.5,16,1.5,P.root);
 px(1,15,-1.25,3.5,19,1.25,P.root);
 px(2.5,18,-1,4.5,21.5,1,P.root);
 px(4,21,-.5,6,24,.5,P.root);
 // The side shoot splits a third of the way up the main stalk, well above
 // the rosette, then steps up and outward into a slim turning-head socket.
 px(-3,9,-1,0,11.5,1.5,P.root);
 px(-6,10.5,-.5,-2,12.5,2,P.root);
 px(-9,12,.5,-5,14,3,P.root);
 px(-11.75,13.5,1.25,-8.75,15.5,2.75,P.root);
 px(-11.5,15,1.5,-10.5,18,2.5,P.root);
}
