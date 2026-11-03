// Shared scene assembly for the page (app.js) and the headless checker (check.mjs).
import{Mesh,cameraEye}from'./engine.js';
import{terrain}from'./kit.js';

// The painted atlas; the flat placeholder only stands in if it is missing.
const WIND=['textures/blockwind.png','placeholder/blockwind.png'];
export const TEXTURES={blockwind:WIND,blockwind_air:WIND,white:'textures/white.png',grass:'textures/grass.png',dirt:'textures/dirt.png'};
export const CAMERAS=['orbit','caster','target'];
const CUBE_FLOATS=6*6*13;

export function cameraFor(scene,name,t){const c=scene.cameras[name];return typeof c==='function'?c(t):c}

/**
 * Builds one frame: terrain, then the scene's figures and wind. The caster and target cameras are both first-person
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

/** Spell cubes only (both atlas batches), excluding terrain and figures. */
export const windCubes=m=>{
 // Air cubes wait in m.sorted until the renderer sorts them into their batch.
 const waiting=(m.sorted.get('blockwind_air')||[]).reduce((n,[,g])=>n+g.length,0);
 return((m.batches.get('blockwind')?.length||0)+(m.batches.get('blockwind_air')?.length||0)+waiting)/CUBE_FLOATS;
};
