// Shared scene assembly for the page (app.js) and the headless checker (check.mjs).
import{Mesh,cameraEye}from'./engine.js';
import{terrain}from'./kit.js';

// The painted atlas; the generated placeholder only stands in until it exists.
const SPACE=['textures/blockspace.png','placeholder/blockspace.png'];
export const TEXTURES={blockspace:SPACE,blockspace_glass:SPACE,white:'scenery/white.png',grass:'scenery/grass.png',dirt:'scenery/dirt.png'};
export const CAMERAS=['orbit','caster','target'];
const CUBE_FLOATS=6*6*13;

export function cameraFor(scene,name,t){const c=scene.cameras[name];return typeof c==='function'?c(t):c}

/**
 * Builds one frame: terrain, then the scene's figures and void glass. The caster and target cameras are both first-person
 * views, so both get the near-eye clearance (SpellViewClearance shrinks spell meshes near any first-person eye in game).
 */
export function buildMesh(scene,t,camera,{cam='orbit',embers=true,dusk=false}={}){
 const m=new Mesh({time:t,eye:cameraEye(camera),clearance:cam==='caster'||cam==='target'});
 const height=scene.terrain||(()=>0);
 const ground=(x,z)=>height(Math.floor(x)+.5,Math.floor(z)+.5);
 terrain(m,scene.area||{x0:-8,x1:8,z0:-8,z1:8},height);
 scene.draw(m,t,{embers,dusk,ground,cam});

 return m;
}

/** Spell pieces only (both atlas batches), excluding terrain and figures. */
export const spaceCubes=m=>{
 // Glass pieces wait in m.sorted until the renderer sorts them into their batch.
 const waiting=(m.sorted.get('blockspace_glass')||[]).reduce((n,[,g])=>n+g.length,0);
 return((m.batches.get('blockspace')?.length||0)+(m.batches.get('blockspace_glass')?.length||0)+waiting)/CUBE_FLOATS;
};
