"""Storm Roc sculpt: original stepped volumes and layered feather anatomy."""
import math
from proportions import point, dimensions, region


def sculpt(Roc):
    r=Roc()
    def bone(name,p,parent=None):return r.bone(name,point(name,p),parent)
    bone('root',[0,0,0]);bone('body',[0,39,8],'root')
    def box(b,c,s,m,rot=None,scar=False,feather=False):r.cube(b,point(b,c),dimensions(b,c,s),m,rot,scar,feather)
    def plume(name,parent,a,b,w,m,tip=None):
        scale=.73 if region(name)=='body' else .88 if region(name)=='neck' else 1
        r.feather(name,parent,point(name,a),point(name,b),round(w*scale*2)/2,m,tip=tip)
    # Rounded keel courses: the narrow sternum projects ahead of the flanks.
    for y in range(22,62,3):
        f=math.sqrt(max(.04,1-((y-41)/22)**2))
        for lane in range(-2,3):
            x=lane*7*f;depth=46*f*math.sqrt(1-(lane*.36)**2)
            box('body',[x,y,5-1.5*(2-abs(lane))],[max(1,round(8*f*2)/2),3.5,round(depth*2)/2],'white')
    # Overlapping side breast feathers interrupt the exposed ellipsoid's seams.
    for side,label in ((-1,'right'),(1,'left')):
        for row in range(3):
            for col in range(3):
                y=51-row*7;z=-5+col*7
                def flank_x(y,z):return 19*math.sqrt(max(.1,1-((y-40.5)/21)**2-((z-6)/24)**2))+1
                plume(f'{label}_flank_{row}_{col}','body',[side*flank_x(y,z),y,z],[side*flank_x(y-9,z+5),y-9,z+5],6,'white')
    # Slate dorsal mantle follows the chest arc, never a single big back board.
    for i in range(7):
        z=-1+i*4;w=22-i*.5;y=round((41+21*math.sqrt(max(.1,1-((z-6)/24)**2)))*2)/2
        box('body',[0,y,z],[w,2,6],'slate')
        for side in (-1,1):box('body',[side*(w/2+1),y-3,z],[5,3,6],'slate')
    for side,label in ((-1,'right'),(1,'left')):
        for row in range(4):
            for col in range(4):
                x=side*(2+col*4);y=55-row*7
                def breast_z(x,y):return 6-24*math.sqrt(max(.05,1-(x/19)**2-((y-40.5)/21)**2))-1.5
                z=breast_z(x,y)
                plume(f'{label}_breast_{row}_{col}','body',[x,y,z],[x*.9,y-9,breast_z(x*.9,y-9)],5.5,'white')
        for row in range(2):
            for col in range(4):
                x=7+row*4;z=col*5
                def back_y(x,z):return 42+21*math.sqrt(max(.1,1-(x/19)**2-((z-6)/24)**2))
                plume(f'{label}_scapular_{row}_{col}','body',[side*x,back_y(x,z),z],[side*(x+2),back_y(x+2,z+14),z+14],7,'navy' if row==0 else 'slate')
    bone('neck_base',[0,54,-5],'body');bone('neck_mid',[0,61,-10],'neck_base');bone('neck_upper',[0,67,-13],'neck_mid')
    # Continuous S curve, with small overlapping courses instead of three boxes.
    neck_courses=[('neck_base',55,-3,17,16),('neck_base',57,-4,16,16),
                  ('neck_base',59,-6,15,15),('neck_mid',61,-9,14,14),
                  ('neck_mid',63,-12,13,13),('neck_mid',65,-14,13,13),
                  ('neck_upper',67,-15,13,13),('neck_upper',69,-15,14,14)]
    for owner,y,z,w,d in neck_courses:
        box(owner,[0,y,z],[w-3,3,d],'navy')
        for side in (-1,1):box(owner,[side*(w/2-1),y,z],[2,3,d-3],'navy')
    for side,label in ((-1,'right'),(1,'left')):
        for row in range(5):
            y=70-row*3;z=[-23,-22.5,-21,-18.5,-15][row]
            owner='neck_upper' if row<2 else 'neck_mid' if row<4 else 'neck_base'
            for i in range(3):
                plume(f'{label}_throat_{row}_{i}',owner,[side*(1+i*2),y,z],[side*(1.5+i*2.4),y-6,z+2],4,'white')
        for row in range(3):
            for i in range(4):
                y=69-row*4;z=-13+row*3+i*2
                plume(f'{label}_ruff_{row}_{i}','neck_upper' if row==0 else 'neck_mid',
                      [side*6,y,z],[side*(7+(i%2)*.5),y-6,z+6],3.5,'navy')
    # Enlarged bevelled skull; side orbits have a heavy brow and bare red orbital skin.
    bone('head',[0,70,-17],'neck_upper')
    for y,w,d,z in [(67,11,14,-16),(69,14,18,-16),(71,15,20,-16),(73,15,22,-16),(75,15,22,-16),(77,14,20,-16),(79,12,17,-15),(81,8,12,-14)]:
        box('head',[0,y,z],[w,2,d],'navy')
    for side,label in ((-1,'right'),(1,'left')):
        box('head',[side*7.25,76.75,-19.0],[1.5,7,10],'skin')
        box('head',[side*7.5,78.75,-20.0],[1,6,7],'skin')
        box('head',[side*8.1,78.25,-20.0],[.5,4,5],'mouth')
        box('head',[side*8.4,78.45,-20.5],[.5,3,3],'iris')
        box('head',[side*8.7,78.45,-21.0],[.5,2.5,1],'talon')
        box('head',[side*8.9,79.15,-21.5],[.5,.5,.5],'ivory')
        box('head',[side*8.2,80.15,-20.0],[2.5,2.5,10],'navy',[10,-side*6,side*9])
        box('head',[side*8,75.75,-19.5],[1,1,8],'skin')
        for i in range(6):plume(f'{label}_cheek_{i}','head',[side*(5+i*.45),69-i*.4,-24+i*1.5],[side*(7.5+i*.45),62-i*.4,-16+i*1.7],4.5,'white')
    for side,label in ((-1,'right'),(1,'left')):
        for row in range(2):
            for i in range(4):
                plume(f'{label}_cheek_blue_{row}_{i}','head',[side*7.5,79-row*5,-18+i*2],[side*8,76-row*5,-11+i*2.5],3.5,'navy')
    for side,label in ((-1,'right'),(1,'left')):
        for i in range(3):
            box('head',[side*(6-i*.4),66-i,-19+i*2],[3.5,4,7],'white')
            plume(f'{label}_cere_feather_{i}','head',[side*(3+i),76,-19-i],[side*(3+i*.5),74-i*.5,-28-i],4,'navy')
    # Contiguous horizontal slices trace the dorsal arch and returning hook.
    # Each slice narrows toward the ridge and hooked tip in frontal view.
    bone('upper_beak',[0,72,-26],'head')
    rows=[(78.5,1,-32,-25,3.5),(77.5,1,-36,-24.5,5.5),
          (76.5,1,-39,-24.5,7),(75.5,1,-41,-24.5,8),
          (74.5,1,-42.5,-24.5,9),(73.5,1,-43.5,-24.5,9.5),
          (72.5,1,-44.5,-24.5,9.5),(71.5,1,-45,-24.5,9.5),
          (70.5,1,-45.5,-24.5,9),(69.5,1,-45.75,-25,8.5),
          (68.5,1,-45.75,-26,8),(67.5,1,-45.75,-36,7.5),
          (66.5,1,-45.5,-38,6.5),(65.5,1,-45.25,-39,5.5),
          (64.5,1,-45,-39.75,4.5),(63.5,1,-44.5,-40,3.5),
          (62.5,1,-44,-40.5,2.75),(61.5,1,-43.5,-40.75,2),
          (60.5,1,-43,-41,1.25),(59.5,1,-42.25,-41.25,.5)]
    for y,h,front,back,w in rows:
        # Preserve every profile course exactly. Compress laterally and taper
        # along the bill as well as toward the culmen, rather than a wide slab.
        z=front
        while z<back:
            stop=min(back,z+1.5);mid=(z+stop)/2
            along=max(0,min(1,(mid+45.75)/21.25))
            width=max(.25,round((w/9.5)*4.75*(.28+.72*along)*4)/4)
            cere_edge=-28.5+(y-73)*.3
            mat='skin' if mid>cere_edge else 'beak_light' if mid>cere_edge-2 else 'bill_ivory'
            if y<68.5:mat='bill_ivory'
            box('upper_beak',[0,y,mid],[width,h,stop-z],mat)
            z=stop
    for side in (-1,1):
        box('upper_beak',[side*1.95,74,-27.5],[.25,1.5,1.5],'mouth',[14,0,0])
        box('head',[side*7.1,68.1,-23],[.5,.75,9],'mouth',[8,0,0])
    # The cutting edge follows the lateral taper too.
    for z,w in [(-26,4),(-29,3.5),(-32,3),(-35,2.5),(-37.5,2)]:
        box('upper_beak',[0,68.1,z],[w,.5,3],'bill_shadow')
    bone('lower_beak',[0,67,-23],'head')
    for z,w,y,d in [(-25,8,66.5,6),(-30,7,66.25,5),(-34,5.5,66,4),(-37,3.5,66,3)]:
        w=round(w*.5*4)/4
        box('lower_beak',[0,y,z],[w,3 if z>-29 else 1.5,d],'skin' if z>-29 else 'bill_shadow')
        box('lower_beak',[0,y+.8,z],[max(1,w-1),.25,d],'mouth')
    box('lower_beak',[0,67.4,-27.5],[1.5,.5,6],'tongue')
    box('head',[0,68,-23.5],[4,4,3],'mouth')
    box('upper_beak',[0,67.7,-31],[2,.5,10],'mouth')
    box('lower_beak',[0,66.75,-38.25],[1,1.5,1],'bill_ivory')
    bone('crest',[0,78,-13],'head')
    # Each swept tuft bends back first, then lifts at its lighter tapered end.
    for row in range(3):
        for i in range(3):
            x=(i-1)*4+(row-1)*.6;start=[x,80-row*3,-16+row*2]
            end=[((-12,0,12),(-9,-4,9),(-6,5,7))[row][i],
                 ((82,85,81),(81,83,82),(80,82,80))[row][i],
                 3+((i+row)%3)*2-row*1.5]
            name=f'crest_plume_{row}_{i}';bone(name,start,'crest')
            controls=[start,[(x+end[0])*.5,start[1]-1,start[2]+9],
                      [end[0],end[1]-4,end[2]-4],end]
            def curve(t):return [sum(controls[k][j]*v for k,v in enumerate(((1-t)**3,3*(1-t)**2*t,3*(1-t)*t*t,t**3))) for j in range(3)]
            for k in range(5):
                a=curve(k/5);b=curve((k+1)/5);v=[b[j]-a[j] for j in range(3)];length=math.hypot(*v);d=[v0/length for v0 in v]
                normal=[.55 if x>=0 else -.55,.15,-.82]
                w=[normal[1]*d[2]-normal[2]*d[1],normal[2]*d[0]-normal[0]*d[2],normal[0]*d[1]-normal[1]*d[0]];wl=math.hypot(*w);w=[v0/wl for v0 in w]
                n=[d[1]*w[2]-d[2]*w[1],d[2]*w[0]-d[0]*w[2],d[0]*w[1]-d[1]*w[0]]
                rot=[-math.degrees(math.atan2(n[2],d[2])),-math.degrees(math.asin(-w[2])),math.degrees(math.atan2(w[1],w[0]))]
                box(name,[(a[j]+b[j])/2 for j in range(3)],
                    [[4.5,4.5,3.5,2.25,.75][k],1.5,round((length+.5)*4)/4],
                    'crest_tip' if k>=3 else 'navy',rot,feather=True)
    # Broad continuous vanes. Inboard secondaries overlap at full width for most
    # of their lengths; only the distal primaries separate into fingers.
    for side,label in ((-1,'right'),(1,'left')):
        shoulder=bone(f'{label}_wing_shoulder',[side*12,54,0],'body')
        elbow=bone(f'{label}_wing_elbow',[side*43,56,0],shoulder)
        wrist=bone(f'{label}_wing_wrist',[side*70,58,-2],elbow)
        for owner,x,w,y in [(shoulder,27,30,54),(elbow,56,28,56),(wrist,78,18,58)]:
            box(owner,[side*x,y,4],[w,3,9],'red')
            box(owner,[side*x,y+2,-3],[w,2,5],'slate')
        bone(f'{label}_secondaries',[side*43,56,0],elbow)
        for i in range(12):
            x=18+i*4.3;y=54+i*.075;owner=shoulder if i<6 else f'{label}_secondaries'
            plume(f'{label}_secondary_{i:02}',owner,[side*x,y,6],[side*(x+5),y-1,39+6*math.sin(i/12*math.pi)],9,'flight','tip')
        bone(f'{label}_primaries',[side*70,58,0],wrist)
        for i in range(10):
            a=i/9
            plume(f'{label}_primary_{i:02}',f'{label}_primaries',[side*(67+i*1.6),56.5+i*.16,7-i*.45],[side*(76+37*a),55+6*a,47-32*a],11 if i<7 else 9,'flight','tip')
        for row in range(2):
            for i in range(16):
                x=15+i*4.6;owner=shoulder if x<43 else elbow if x<70 else wrist
                y=57+(x-15)*.055-row*.65;z=-1+row*10
                plume(f'{label}_covert_{row}_{i:02}',owner,[side*x,y,z],[side*(x+3),y-.8,z+17+(i%3-1)*2],8,'red' if row==0 else 'rose')
        for i in range(15):
            x=15+i*5;owner=shoulder if x<43 else elbow if x<70 else wrist
            plume(f'{label}_leading_{i:02}',owner,[side*x,60+(x-15)*.055,-5],[side*(x+5),59+(x-15)*.055,6],7,'slate')
        plume(f'{label}_alula',wrist,[side*69,60,-4],[side*82,61,-13],6,'navy')
    bone('tail_fan',[0,30,26],'body')
    for i in range(13):
        a=(i-6)/6
        plume(f'tail_{i:02}','tail_fan',[a*7,32+(i%3)*.9,23],[a*28,13+(i%3)*2+abs(a)*4,80-abs(a)*15+(i%2)*3],8,'tail_red')
    for i in range(7):
        a=(i-3)/3
        plume(f'tail_covert_{i}','tail_fan',[a*6,35,23],[a*16,25,51-abs(a)*4],8,'rose')
    for i in range(9):
        a=(i-4)/4
        plume(f'tail_middle_{i}','tail_fan',[a*6,34,25],[a*22,21+abs(a)*3,66-abs(a)*9],7,'tail_red')
    # Tapered thigh cores are concealed by staggered hanging feather skirts.
    for side,label in ((-1,'right'),(1,'left')):
        thigh=bone(f'{label}_thigh',[side*9,30,8],'body')
        for y,w,d in [(29,7,8),(25,7,8),(21,5,6)]:
            box(thigh,[side*10,y,9],[w,3.5,d],'white')
        for row in range(3):
            for i in range(8):
                a=(i/8+row*.065)*math.tau
                start=[side*10+math.cos(a)*(5.5-row*.65),30-row*4,9+math.sin(a)*(6-row*.65)]
                end=[side*10+math.cos(a)*(5-row*.6),22-row*4-(i%3),10+math.sin(a)*(5.5-row*.5)]
                plume(f'{label}_trouser_{row}_{i}',thigh,start,end,5.5,'white')
        shin=bone(f'{label}_shin',[side*10,21,9],thigh)
        box(shin,[side*10,14,8],[5,14,5],'scale')
        box(shin,[side*10,13,8],[6,11,3],'scale')
        for i in range(8):
            y=8.5+i*1.6
            box(shin,[side*10,y,5.1],[2.5,1.75,1],'scute')
            for edge in (-1,1):
                box(shin,[side*10+edge*1.6,y+.3,5.45],[1.5,1.25,1],'scute',[0,-edge*22,0])
        foot=bone(f'{label}_foot',[side*10,7,6],shin)
        box(foot,[side*10,6.5,5],[8,4,7],'scale')
        for i in range(4):
            back=i==3;x=side*10+(i-1)*3 if not back else side*10;z=7 if back else 2;sgn=1 if back else -1
            name=bone(f'{label}_toe_{i}_base',[x,6,z],foot)
            box(name,[x,5.5,z+sgn*2.5],[3,3,5],'scale')
            box(name,[x,6.6,z+sgn*4],[3.5,1.5,2],'scute')
            tip=bone(f'{label}_toe_{i}_tip',[x,5,z+sgn*5],name)
            box(tip,[x,4.5,z+sgn*6.5],[2.5,3,4],'scale')
            box(tip,[x,5.7,z+sgn*7],[3,1.5,2.5],'scute')
            for j in range(3):box(name,[x,7,z+sgn*(1+j)],[2.5,.5,.75],'scute')
            # Broad root arches beyond the crown; the fine tip returns toward it.
            for dz,y,w,h,d in [(9,5,3.5,3,3.5),(11,4.5,3,3,2.5),
                               (12.5,3,2.5,3.5,2),(13,1,2,3,1.5),
                               (12.5,-.75,1.25,2,1.5),(11.75,-1.75,.5,1,1)]:
                box(tip,[x,y,z+sgn*dz],[w,h,d],'talon')
    # Isolated scar overlays, in the atlas/glowmask, fitted to selected visible surfaces.
    for side,label in ((-1,'right'),(1,'left')):
        box('head',[side*9,74,-12.5],[.5,9,9],'navy',scar='face')
        box('neck_mid',[side*5.5,61,-20.1],[4,9,.5],'white',scar='neck')
        box('neck_mid',[side*10,62,-10],[.5,12,10],'navy',scar='face')
        box('body',[side*19,54,0],[.5,10,12],'navy',scar='face')
        box(f'{label}_wing_shoulder',[side*28,60.3,-1],[15,.5,8],'slate',scar='wing')
    box('body',[1.5,46,-14.7],[8,14,.5],'white',scar='chest')
    # Empty locator bones for camera-facing flipbook sprites; no crackle cubes.
    for name,p,parent in [('spark_beak_tip',[0,60,-42],'upper_beak'),('spark_beak_ridge',[0,79,-35],'upper_beak'),
                          ('spark_eye_left',[9.2,76.8,-22],'head'),('spark_eye_right',[-9.2,76.8,-22],'head'),
                          ('spark_brow_left',[8.8,78,-20],'head'),('spark_brow_right',[-8.8,78,-20],'head')]:bone(name,p,parent)
    family={'head'}
    for bone in r.bones:
        if bone.get('parent') in family:family.add(bone['name'])
        if bone['name'] not in family:continue
        pivot=point('head',[0,70,-17])
        def enlarge(p):return [round(pivot[j]+(p[j]-pivot[j])*1.40,4) for j in range(3)]
        bone['pivot']=enlarge(bone['pivot'])
        for c in bone['cubes']:
            center=enlarge([c['origin'][j]+c['size'][j]/2 for j in range(3)])
            c['size']=[max(.25,(math.ceil(v*1.40*4-1e-7) if bone['name']=='upper_beak' and j==2 else round(v*1.40*4))/4) for j,v in enumerate(c['size'])]
            c['origin']=[round(center[j]-c['size'][j]/2,4) for j in range(3)]
            if 'pivot' in c:c['pivot']=enlarge(c['pivot'])
    for b in r.bones:
        if b['name'] in ('left_leading_02','right_leading_02'):
            target=b['cubes'][0]
            r.cubes=[(c,m,'wing' if c is target else scar,f,d) for c,m,scar,f,d in r.cubes]
    head_cubes={id(c) for b in r.bones if b['name'] in family for c in b['cubes']}
    r.cubes=[(c,m,s,f,4 if id(c) in head_cubes else d) for c,m,s,f,d in r.cubes]
    from anatomy import redesign
    return redesign(r)
