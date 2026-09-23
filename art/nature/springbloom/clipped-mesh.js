import {Mesh,add,sub,cross} from '../workshop/engine.js';

// Clip each authored solid cuboid, including cap faces, to a world-block column.
// The original flower is unchanged; each piece can be omitted beside an obstacle.
export class ClippedMesh extends Mesh {
  constructor(x,z){super();this.bounds=[x-.5,x+.5,z-.5,z+.5];}
  box(p,s,col,mat=4,rotation=0){
    const vertices=[];
    for(const x of [-1,1])for(const y of [-1,1])for(const z of [-1,1]) {
      const xx=x*s[0]/2,zz=z*s[2]/2;
      vertices.push(add(p,[xx*Math.cos(rotation)+zz*Math.sin(rotation),y*s[1]/2,-xx*Math.sin(rotation)+zz*Math.cos(rotation)]));
    }
    let faces=[[0,1,3,2],[4,6,7,5],[0,4,5,1],[2,3,7,6],[0,2,6,4],[1,5,7,3]].map(f=>f.map(i=>vertices[i]));
    for(const [axis,limit,sign] of [[0,this.bounds[0],1],[0,this.bounds[1],-1],[2,this.bounds[2],1],[2,this.bounds[3],-1]]) {
      const cap=[],next=[];
      const distance=v=>sign*(v[axis]-limit);
      for(const face of faces){
        const polygon=[];
        for(let i=0;i<face.length;i++){
          const a=face[i],b=face[(i+1)%face.length],da=distance(a),db=distance(b),insideA=da>=-1e-9,insideB=db>=-1e-9;
          if(insideA)polygon.push(a);
          if(insideA!==insideB){
            const t=da/(da-db),v=a.map((value,j)=>value+(b[j]-value)*t);v[axis]=limit;polygon.push(v);
            if(!cap.some(q=>Math.hypot(...sub(v,q))<1e-8))cap.push(v);
          }
        }
        if(polygon.length>=3)next.push(polygon);
      }
      if(cap.length>=3){
        const center=[0,1,2].map(i=>cap.reduce((sum,v)=>sum+v[i],0)/cap.length);
        const u=axis===0?1:0,v=axis===0?2:1;
        cap.sort((a,b)=>Math.atan2(a[v]-center[v],a[u]-center[u])-Math.atan2(b[v]-center[v],b[u]-center[u]));
        if(sign>0)cap.reverse(); // Outward normal on the minimum face points negative.
        next.push(cap);
      }
      faces=next;
    }
    for(const face of faces)for(let i=1;i<face.length-1;i++)
      if(Math.hypot(...cross(sub(face[i],face[0]),sub(face[i+1],face[0])))>1e-10)
        this.tri(face[0],face[i],face[i+1],col,mat);
  }
}
