// A rounded eight-block fireball built from smaller half-block voxel steps.
// Only boundary faces are emitted; this is reusable preview geometry, not a rock core.
export const METEOR_SIZE=8;
export const METEOR_VOXEL_SIZE=.5;
export const meteorVoxels=[];
const occupied=new Set(),key=(x,y,z)=>`${x},${y},${z}`;
for(let x=-4;x<4;x+=.5)for(let y=-4;y<4;y+=.5)for(let z=-4;z<4;z+=.5){
 const cx=x+.25,cy=y+.25,cz=z+.25;
 // A symmetric sphere and finer steps produce a circular outline from every angle.
 const radius=3.98;
 if(Math.hypot(cx,cy,cz)<radius){occupied.add(key(x,y,z));meteorVoxels.push([x,y,z]);}
}
export const meteorFaces=[];
for(const[x,y,z]of meteorVoxels){
 const X=x+.5,Y=y+.5,Z=z+.5;
 const faces=[
  {n:[0,0,1],p:[[x,y,Z],[X,y,Z],[X,Y,Z],[x,Y,Z]],u:x,v:y},
  {n:[0,0,-1],p:[[X,y,z],[x,y,z],[x,Y,z],[X,Y,z]],u:-x,v:y},
  {n:[1,0,0],p:[[X,y,Z],[X,y,z],[X,Y,z],[X,Y,Z]],u:-z,v:y},
  {n:[-1,0,0],p:[[x,y,z],[x,y,Z],[x,Y,Z],[x,Y,z]],u:z,v:y},
  {n:[0,1,0],p:[[x,Y,Z],[X,Y,Z],[X,Y,z],[x,Y,z]],u:x,v:z},
  {n:[0,-1,0],p:[[x,y,z],[X,y,z],[X,y,Z],[x,y,Z]],u:x,v:-z}
 ];
 for(const f of faces){if(occupied.has(key(x+f.n[0]*.5,y+f.n[1]*.5,z+f.n[2]*.5)))continue;
  const u=(f.u+4)/9,v=(f.v+4)/9,step=.5/9;
  meteorFaces.push({positions:f.p,normal:f.n,uv:[[u,v+step],[u+step,v+step],[u+step,v],[u,v]]});
 }
}
export function drawMeteorBody(m,center,time){
 const yaw=time*.19,co=Math.cos(yaw),si=Math.sin(yaw);
 for(const face of meteorFaces){
  const tone=.76+face.normal[1]*.15+face.normal[2]*.13+face.normal[0]*.06;
  const points=face.positions.map(([x,y,z])=>[center[0]+x*co+z*si,center[1]+y,center[2]-x*si+z*co]);
  m.quad(points,'custom_meteor_surface',{uv:face.uv,emissive:true,color:[tone,tone,tone,1]});
 }
}
