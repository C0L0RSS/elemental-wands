"""Extend the established WebGL viewer with a preview-only full encounter sequence."""
def extend(template):
    template=template.replace('<option selected>leap_launch</option>','<option>leap_launch</option>')
    template=template.replace('<select id="clip">','<select id="clip"><option value="full_leap" selected>Full jump + both waves</option>')
    template=template.replace("let clipName='leap_launch'", "let clipName='full_leap'")
    template=template.replace('Animation poses · Flight and both shockwaves run in Minecraft.', 'Full jump on flat ground · In-game collision and damage require playtesting.')
    template=template.replace('<span id="time">0.00 s</span>', '<span id="time">0.00 s</span><strong id="phase" style="min-width:290px;color:#344b3b"></strong>')
    template=template.replace("function selectedClip(){return clips['animation.fractured_guardian.'+clipName]}", """
function selectedClip(){return clipName==='full_leap'?{loop:true,animation_length:5.6,bones:{}}:clips['animation.fractured_guardian.'+clipName]}
function poseClip(){if(clipName!=='full_leap')return {clip:selectedClip(),time:motionTime};const t=motionTime;return t<.7?{clip:clips['animation.fractured_guardian.leap_launch'],time:t}:t<2.5?{clip:clips['animation.fractured_guardian.leap_air'],time:t-.7}:{clip:clips['animation.fractured_guardian.leap_land'],time:Math.min(2.2,t-2.5)}}
function leapOffset(){if(clipName!=='full_leap')return [0,0,0];const u=Math.max(0,Math.min(1,(motionTime-.7)/1.8));let travel=Math.max(0,Math.min(1,(u-.1)/.8));travel=travel*travel*(3-2*travel);return [0,16*4*11*u*(1-u),-256*travel]}
""")
    template=template.replace('function buildPoses(){posedBones={};', 'function buildPoses(){const activePose=poseClip();posedBones={};')
    template=template.replace('selectedClip().bones[name]', 'activePose.clip.bones[name]').replace('sample(ch.rotation,motionTime)', 'sample(ch.rotation,activePose.time)').replace('sample(ch.position,motionTime)', 'sample(ch.position,activePose.time)')
    template=template.replace('return mul(m.r,v).map((x,i)=>x+m.p[i])', 'const offset=leapOffset();return mul(m.r,v).map((x,i)=>x+m.p[i]+offset[i])')
    template=template.replace('uniform vec2 extent;', 'uniform vec2 extent;uniform vec3 center;').replace('pos-vec3(0.,60.,0.)', 'pos-center').replace('-p.z/250.', '-p.z/1100.')
    template=template.replace('gl_FragColor=vec4', 'if(lightFace>1.5){color=lightFace<2.5?vec3(.83,.82,.78):lightFace<3.5?vec3(.0,.85,.94):lightFace<4.5?vec3(.92,.52,.13):lightFace<5.5?vec3(.67,.69,.64):vec3(.45,.48,.43);lit=lightFace>2.5&&lightFace<4.5?1.:0.;}gl_FragColor=vec4')
    template=template.replace("const triangleCount=vertices.length/9;", "")
    template=template.replace('gl.drawArrays(gl.TRIANGLES,0,triangleCount)', 'gl.drawArrays(gl.TRIANGLES,0,vertices.length/9)')
    template=template.replace('const extent=Math.max(90,90*h/w)/zoom;', "const baseExtent=clipName==='full_leap'?205:90;const extent=Math.max(baseExtent,baseExtent*h/w)/zoom;gl.uniform3f(gl.getUniformLocation(program,'center'),0,clipName==='full_leap'?140:60,clipName==='full_leap'?-128:0);")
    template=template.replace('let yaw=-32*Math.PI/180,pitch=14*Math.PI/180', 'let yaw=-48*Math.PI/180,pitch=18*Math.PI/180')
    template=template.replace('}}}updateVertices();', "}}if(clipName==='full_leap')addSequenceScene();}updateVertices();")
    template=template.replace('function updateVertices(){', """
function sceneQuad(q,code){const n=cross(sub(q[1],q[0]),sub(q[2],q[0])),size=Math.hypot(...n)||1;for(const i of [0,1,2,0,2,3])vertices.push(...q[i],...n.map(v=>v/size),0,0,code)}
function sceneBox(x,y,z,sx,sy,sz,code){const v=[[-sx/2,0,-sz/2],[sx/2,0,-sz/2],[sx/2,sy,-sz/2],[-sx/2,sy,-sz/2],[-sx/2,0,sz/2],[sx/2,0,sz/2],[sx/2,sy,sz/2],[-sx/2,sy,sz/2]].map(p=>[p[0]+x,p[1]+y,p[2]+z]);for(const face of [[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[0,1,5,4],[3,7,6,2]])sceneQuad(face.map(i=>v[i]),code)}
function sceneRing(z,radius,y,width,code){for(let i=0;i<80;i++){const a=i*Math.PI/40,b=(i+1)*Math.PI/40;sceneQuad([[radius*Math.cos(a),y,z+radius*Math.sin(a)],[radius*Math.cos(b),y,z+radius*Math.sin(b)],[(radius-width)*Math.cos(b),y,z+(radius-width)*Math.sin(b)],[(radius-width)*Math.cos(a),y,z+(radius-width)*Math.sin(a)]],code)}}
function sceneWave(z,begin){const age=(motionTime-begin)*20;if(age<0||age>=28)return;const radius=Math.min(18,(age+1)*.65)*16;const count=Math.max(12,Math.ceil(radius*Math.PI*2/16));for(let i=0;i<count;i++){const a=i*Math.PI*2/count;sceneBox(radius*Math.cos(a),0,z+radius*Math.sin(a),16,12,14,6)}sceneRing(z,radius,12.5,2.5,3)}
function addSequenceScene(){sceneQuad([[-350,-1,350],[350,-1,350],[350,-1,-620],[-350,-1,-620]],2);for(let x=-336;x<=336;x+=32)sceneQuad([[x,-.7,350],[x+.4,-.7,350],[x+.4,-.7,-620],[x,-.7,-620]],5);for(let z=-608;z<=352;z+=32)sceneQuad([[-350,-.6,z],[350,-.6,z],[350,-.6,z+.4],[-350,-.6,z+.4]],5);if(motionTime<2.5){sceneRing(-256,96,.3,3,4);sceneRing(-256,48,.3,2,4)}sceneWave(0,.7);sceneWave(-256,2.8);const phase=document.querySelector('#phase');if(phase)phase.textContent=motionTime<.7?'Fists down → launch':motionTime<2.5?'Airborne':motionTime<2.8?'Landing smash':motionTime<4.2?'Second shockwave':'Recovery';}
function updateVertices(){""")
    template=template.replace('timeline.max=selectedClip().animation_length;', "timeline.max=selectedClip().animation_length;if(clipName==='full_leap'){yaw=-48*Math.PI/180;pitch=18*Math.PI/180}else{yaw=-32*Math.PI/180;pitch=14*Math.PI/180}zoom=1;document.querySelector('#phase').textContent='';")
    template=template.replace('value="0" aria-label="Animation time"', 'value="0" aria-label="Animation time"').replace('max="6"', 'max="5.6"')
    return template
