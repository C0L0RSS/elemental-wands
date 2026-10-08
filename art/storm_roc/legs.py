"""Fixed-length avian leg IK: short femur, trouser, backward hock and tarsus."""
import math
import numpy as np
from fold import rotation

FEMUR=14.
DRUM=28.
TARSUS=26.

def solve_leg(p,label,hip,foot,femur_angle,foot_rotation=None):
    hip=np.array(hip,dtype=float);foot=np.array(foot,dtype=float)
    femur_R=rotation([femur_angle,0,0])
    knee=hip+femur_R@np.array([0,-FEMUR,0])
    # Solve in the bird's sagittal plane, selecting the backward hock branch.
    v=foot[1:]-knee[1:];distance=float(np.linalg.norm(v))
    assert abs(DRUM-TARSUS)+.001<distance<DRUM+TARSUS-.001,(label,distance)
    u=v/distance;along=(DRUM**2-TARSUS**2+distance**2)/(2*distance)
    height=math.sqrt(max(0,DRUM**2-along**2))
    perpendicular=np.array([u[1],-u[0]])
    if perpendicular[1]<0:perpendicular=-perpendicular
    heel=np.r_[hip[0],knee[1:]+u*along+perpendicular*height]
    def segment(name,start,end):
        d=end-start;angle=math.degrees(math.atan2(d[2],-d[1]))
        p.put(label+'_'+name,rotation([angle,0,0]),start)
    p.put(label+'_thigh',femur_R,hip)
    segment('shin',knee,heel);segment('tarsus',heel,foot)
    p.put(label+'_foot',rotation(foot_rotation or [0,0,0]),foot)
    return knee,heel
