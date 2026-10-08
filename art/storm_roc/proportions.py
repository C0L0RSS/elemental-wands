"""Regional bind-space proportions for the tall, lean raptor study."""
def region(name):
    if name=='root':return 'root'
    if name in ('head','upper_beak','lower_beak','crest') or name.startswith(('crest_','spark_')) or any(k in name for k in ('cheek','cere_feather')):return 'head'
    if name.startswith('tail'):return 'tail'
    if any(k in name for k in ('neck','throat','ruff')):return 'neck'
    if any(k in name for k in ('thigh','shin','foot','toe','trouser')):return 'leg'
    if any(k in name for k in ('wing','primary','secondary','primaries','secondaries','covert','leading','alula')):return 'wing'
    return 'body'


def point(name,p):
    x,y,z=p;r=region(name)
    if r=='root':return list(p)
    if r=='head':return [x,y+20.8,z+1]
    if r=='neck':return [x*.88,66.75+(y-54)*1.5,-3+(z+5)*.95]
    if r=='leg':return [x,7+(y-7)*1.55 if y>7 else y,z]
    if r=='wing':return [x,y+12.75,z+2]
    if r=='tail':return [x*.90,y+12,z]
    # A keel, not a sphere: compress width and depth, leaving a taller sternum.
    waist=.66 if y<36 else .73
    return [x*waist,51+(y-39)*1.05,8+(z-8)*.72]


def dimensions(name,center,size):
    r=region(name)
    factors={'root':[1,1,1],'head':[1,1,1],'neck':[.88,1.5,.95],
             'leg':[1,1.55 if center[1]>7 else 1,1],'wing':[1,1,1],
             'tail':[.90,1,1],'body':[.66 if center[1]<36 else .73,1.05,.72]}[r]
    grid=4 if r=='head' else 2
    return [max(1/grid,round(v*s*grid)/grid) for v,s in zip(size,factors)]
