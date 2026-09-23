import {C, mix, smooth} from '../workshop/engine.js';

// Approved source shared by the preview and runtime exporter. One unit is one Minecraft block.
// Palette comes directly from the approved Nature workshop.
export const DESIGN = {height: .6875, diameter: 2.25, lifetime: 4, cooldown: 10};

export function springbloom(m, {opening=1, wilt=0}={}) {
  const grow=smooth(opening), fade=1-smooth(wilt), scale=(.18+.82*grow)*(1-.60*smooth(wilt));
  const height=DESIGN.height*(.42+.58*grow);
  const tint=c=>mix(c,C.root,wilt*.45);
  const box=(p,s,c,mat=1,rotation=0)=>m.box([p[0]*scale,p[1]*fade,p[2]*scale],[s[0]*scale,s[1]*Math.max(.01,fade),s[2]*scale],tint(c),mat,rotation);
  if(fade<.01)return;
  // A leafy rosette rests directly on the ground; there is no stem.
  for(let k=0;k<8;k++) {
    const angle=k*Math.PI/4+Math.PI/8;
    for(let j=0;j<6;j++) {
      const r=.38+j*.145,w=[.28,.43,.48,.41,.30,.14][j];
      const x=Math.sin(angle)*r,z=Math.cos(angle)*r,y=.079+j*.006;
      box([x,y,z],[w,.125,.18],k%2?C.leaf:C.lightleaf,1,angle);
      if(j>1&&j<5)box([x,y+.065,z],[.037,.012,.13],C.root,1,angle);
    }
  }
  // Low petals frame the bubble, with their outer tips resting on the leaves.
  for(let k=0;k<8;k++) {
    const angle=k*Math.PI/4;
    for(let j=0;j<5;j++) {
      const r=.54+j*.13,w=[.33,.43,.46,.34,.16][j];
      const folded=(1-grow)*r*.36+wilt*r*.27;
      const y=.19-j*.014+folded;
      const x=Math.sin(angle)*r,z=Math.cos(angle)*r;
      box([x,y-.047,z],[w+.035,.052,.145],C.violet,2,angle);
      box([x,y,z],[w,.075,.14],k%2?C.petal:C.petallight,2,angle);
      if(j>0&&j<4)box([x+Math.cos(angle)*w*.19,y+.040,z-Math.sin(angle)*w*.19],[w*.28,.008,.084],k%2?C.petallight:C.petal,2,angle);
    }
  }
  // Solid golden bubble: stacked voxel slices make a broad, shallow dome.
  // Its crown sits at 11/16 block; the petals and leaves sit at floor level.
  const bottom=.125, domeHeight=height-bottom;
  const bands=12, pixel=.125;
  for(let band=0;band<bands;band++) {
    const f=(band+.45)/bands;
    const radius=.79*Math.sqrt(1-f*f);
    const layerHeight=domeHeight/bands;
    const y=bottom+(band+.5)*layerHeight;
    for(let row=-6;row<=6;row++) {
      const z=row*pixel;
      if(Math.abs(z)+pixel*.35>radius)continue;
      const half=Math.round(Math.sqrt(radius*radius-z*z)/pixel)*pixel;
      if(half<pixel)continue;
      const color=band<2?[.93,.61,.08]:band<5?[1,.74,.10]:C.gold;
      box([0,y,z],[half*2,layerHeight,pixel],color,2);
    }
  }
  // A few connected pale pollen clusters suggest a soft rounded surface.
  const capY=height-.002;
  box([-.125,capY,-.0625],[.25,.004,.125],[1,.93,.51],2);
  box([-.0625,capY,.0625],[.125,.004,.125],[1,.90,.38],2);

}

export function jumpPod(m,p=[0,0,0],turn=0) {
  m.at(p,()=>{
    m.box([0,0,0],[.28,.28,.28],C.root,1,turn);
    for(let i=0;i<4;i++) {
      const a=turn+i*Math.PI/2;
      m.box([Math.sin(a)*.12,.055,Math.cos(a)*.12],[.13,.28,.12],i%2?C.leaf:C.lightleaf,1,a);
    }
    m.box([0,.19,0],[.13,.09,.13],C.petal,2);
    m.box([0,.245,0],[.06,.035,.06],C.gold,2);
  });
}

export function bouncePollen(m,t) {
  if(t<0||t>.65)return;
  for(let i=0;i<16;i++) {
    const a=i*Math.PI/8,r=.32+t*(.7+(i%3)*.22);
    const p=[Math.sin(a)*r,DESIGN.height+t*(1.7+(i%4)*.24)-t*t*1.4,Math.cos(a)*r];
    m.box(p,[.045,.045,.045],i%3?C.gold:C.petallight,3);
  }
}
