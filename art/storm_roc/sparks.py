"""Crisp growing, branching and fading 32px lightning flipbooks."""
import math
from PIL import Image, ImageDraw


def partial(path,amount):
    """Reveal a bolt outward from its fixed strike point."""
    lengths=[math.dist(a,b) for a,b in zip(path,path[1:])]
    remaining=sum(lengths)*amount;out=[path[0]]
    for a,b,length in zip(path,path[1:],lengths):
        if remaining>=length:out.append(b);remaining-=length
        else:
            out.append(tuple(round(a[j]+(b[j]-a[j])*remaining/length) for j in range(2)))
            break
    return out


def build_sparks(roc):
    sheet=Image.new('RGBA',(256,64))
    for kind in range(2):
        for frame in range(8):
            tile=Image.new('RGBA',(32,32));draw=ImageDraw.Draw(tile)
            growth=[.18,.40,.72,1,1,1,.85,.55][frame]
            opacity=[.65,.9,1,1,.85,.60,.34,.12][frame]
            if kind==0:
                trunk=[(4,27),(10,22),(8,17),(17,19),(15,12),(24,9),(28,3)]
                paths=[partial(trunk,growth)]
                if frame>=2:paths.append(partial([(17,19),(23,22),(26,16),(29,17)],min(1,(frame-1)/3)))
                if frame>=3:paths.append(partial([(15,12),(9,9),(11,4)],min(1,(frame-2)/2)))
                if frame>=5:paths=paths[1:]+[trunk[3:]]
            else:
                # Expanding short forked rays replace the single long arc.
                paths=[]
                for ray in range(5):
                    angle=ray*math.tau/5+.22
                    def at(radius,a):return (round(16+math.cos(a)*radius),round(16+math.sin(a)*radius))
                    p=[(16,16),at(4,angle),at(7,angle+.3),at(12,angle)]
                    paths.append(partial(p,growth))
                    if frame in (3,4,5):paths.append([p[2],at(11,angle+.65)])
                if frame>=6:paths=[p[-2:] for p in paths]
            # Hard stepped rings: pale gold outside, warm edge, thin white core.
            for width,color in [(5,(247,214,137,round(48*opacity))),
                                (3,(255,229,164,round(155*opacity))),
                                (1,(255,254,242,round(255*opacity)))]:
                for path in paths:draw.line(path,fill=color,width=width)
            if frame in (1,2,3):draw.rectangle((15,15,17,17),fill=(255,254,242,round(255*opacity)))
            if frame>=4:
                for j in range(3):
                    x=(frame*7+j*11)%28+2;y=(frame*11+j*7)%28+2
                    draw.point((x,y),fill=(255,233,175,round(180*opacity)))
            sheet.paste(tile,(frame*32,kind*32))
    sequences={name:{'fps':18 if row==0 else 23,
        'frames':[{'uv':[i*32,row*32],'size':[32,32]} for i in range(8)]}
        for row,name in enumerate(('arc','spark'))}
    anchors=[]
    phases=[0,.061,.149,.237,.313,.381]
    by_name={b['name']:b for b in roc.bones}
    for name in ('spark_beak_tip','spark_beak_ridge','spark_eye_left','spark_eye_right','spark_brow_left','spark_brow_right'):
        b=by_name[name]
        anchors.append({'bone':b['name'],'pivot_model_pixels':b['pivot'],
                        'sequence':'spark' if 'eye' in b['name'] else 'arc',
                        'size_blocks':.95 if 'beak' in b['name'] else .4 if 'eye' in b['name'] else .62,
                        'phase':phases[len(anchors)]})
    metadata={'format_version':1,'texture':'storm_roc_sparks.png','sheet_size':[256,64],'frame_size':[32,32],
              'filter':'nearest','emissive':True,'billboard':'camera_facing','sequences':sequences,'anchors':anchors,
              'normal':{'copies_per_anchor':1,'brightness':1.0,'size_multiplier':1,'alpha_multiplier':.85},
              'charged':{'copies_per_anchor':3,'brightness':1.9,'size_multiplier':1.2,'alpha_multiplier':1.15},
              'jitter_blocks':.055,'alpha_range':[.65,1],
              'coordinates':'Anchor pivots are absolute bind coordinates, 16 model pixels per block; transform by the named bone matrix.'}
    return sheet,metadata
