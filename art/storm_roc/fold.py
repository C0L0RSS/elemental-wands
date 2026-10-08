"""Author a compact folded pose and export ordinary GeckoLib local bone channels.

Solve local transforms from desired body-space joints and feather frames. This
avoids accumulating Euler guesses down the shoulder/elbow/wrist hierarchy.
"""
import math
import numpy as np


def rotation(angles):
    x,y,z=np.radians(angles)*[-1,-1,1]
    cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])


def euler(m):
    y=math.asin(float(np.clip(-m[2,0],-1,1)))
    if abs(math.cos(y))>.00001:x=math.atan2(m[2,1],m[2,2]);z=math.atan2(m[1,0],m[0,0])
    else:x=0;z=math.atan2(-m[0,1],m[1,1])
    return [-math.degrees(x),-math.degrees(y),math.degrees(z)]


def frame(direction,normal):
    d=np.array(direction,dtype=float);d/=np.linalg.norm(d)
    w=np.cross(normal,d);w/=np.linalg.norm(w)
    return np.column_stack((w,np.cross(d,w),d))
