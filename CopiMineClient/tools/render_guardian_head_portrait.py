"""Orthographic pixel portrait of the supplied Guardian head, from mesh + UV.

This renders the native model source; it does not paint a replacement monster.
Nearest texel sampling and a 128px canvas retain deliberate pixel-art edges.
"""
from pathlib import Path
import hashlib
import json
import math
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient"
GEOMETRY = ASSETS / "models/entity/end_rift_guardian/geometry.json"
TEXTURE = ASSETS / "textures/entity/end_rift_user_boss.png"
OUTPUT = ASSETS / "textures/gui/end_rift_guardian_head_portrait.png"


def rotation(degrees):
    x,y,z = map(math.radians,degrees)
    rx=np.array([[1,0,0],[0,math.cos(x),-math.sin(x)],[0,math.sin(x),math.cos(x)]])
    ry=np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]])
    rz=np.array([[math.cos(z),-math.sin(z),0],[math.sin(z),math.cos(z),0],[0,0,1]])
    return rz @ ry @ rx


def target(point):
    return np.array([-point[0],-point[1],point[2]],dtype=float)


def render():
    model=json.loads(GEOMETRY.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    head=next(b for b in model["bones"] if b["name"]=="head")
    atlas=np.array(Image.open(TEXTURE).convert("RGBA"))
    pivot=target(head["pivot"])
    angles=head.get("rotation",[0,0,0])
    bind=rotation([angles[0],-angles[1],-angles[2]])
    faces=[]
    for cube in head["cubes"]:
        x,y,z=cube["origin"];sx,sy,sz=cube["size"]
        a,b,c=-x-sx,y,z;d,e,f=-x,y+sy,z+sz
        vertices={
          "north":[[d,b,c],[a,b,c],[a,e,c],[d,e,c]],
          "east":[[d,b,f],[d,b,c],[d,e,c],[d,e,f]],
          "south":[[a,b,f],[d,b,f],[d,e,f],[a,e,f]],
          "west":[[a,b,c],[a,b,f],[a,e,f],[a,e,c]],
          "up":[[d,e,c],[a,e,c],[a,e,f],[d,e,f]],
          "down":[[a,b,c],[d,b,c],[d,b,f],[a,b,f]],
        }
        for name,raw in vertices.items():
            points=np.array([[v[0],-v[1],v[2]] for v in raw],dtype=float)
            cr=cube.get("rotation",[0,0,0]);cp=target(cube.get("pivot",[0,0,0]))
            matrix=rotation([cr[0],-cr[1],-cr[2]])
            points=(points-cp) @ matrix.T+cp
            points=(points-pivot) @ bind.T+pivot
            u,v=cube["uv"][name]["uv"];du,dv=cube["uv"][name]["uv_size"]
            uv=np.array([[u,v+dv],[u+du,v+dv],[u+du,v],[u,v]],dtype=float)*8
            if name=="down":uv=np.array([[u+du,v],[u,v],[u,v+dv],[u+du,v+dv]],dtype=float)*8
            faces.append((points,uv))
    all_points=np.concatenate([f[0] for f in faces])
    lower=all_points[:,:2].min(axis=0);upper=all_points[:,:2].max(axis=0)
    scale=116/max(upper-lower);centre=(lower+upper)/2
    pixels=np.zeros((128,128,4),dtype=np.uint8);depth=np.full((128,128),np.inf)
    for points,uv in faces:
        screen=(points[:,:2]-centre)*scale+64
        for ids in ((0,1,2),(0,2,3)):
            pts=screen[list(ids)];tex=uv[list(ids)];zs=points[list(ids),2]
            basis=np.array([pts[1]-pts[0],pts[2]-pts[0]]).T
            if abs(np.linalg.det(basis))<1e-10:continue
            inverse=np.linalg.inv(basis)
            lo=np.maximum(0,np.floor(pts.min(axis=0)).astype(int));hi=np.minimum(127,np.ceil(pts.max(axis=0)).astype(int))
            for py in range(lo[1],hi[1]+1):
                for px in range(lo[0],hi[0]+1):
                    w=inverse @ (np.array([px+.5,py+.5])-pts[0]);weights=np.array([1-w.sum(),w[0],w[1]])
                    if weights.min() < -1e-7:continue
                    z=float(weights @ zs)
                    if z>=depth[py,px]:continue
                    sample=weights @ tex
                    tx=min(atlas.shape[1]-1,max(0,int(sample[0])));ty=min(atlas.shape[0]-1,max(0,int(sample[1])))
                    colour=atlas[ty,tx]
                    if colour[3]==0:continue
                    pixels[py,px]=colour;depth[py,px]=z
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    Image.fromarray(pixels).save(OUTPUT)
    manifest={"geometrySha256":hashlib.sha256(GEOMETRY.read_bytes()).hexdigest(),
      "textureSha256":hashlib.sha256(TEXTURE.read_bytes()).hexdigest(),"bone":"head",
      "cubes":len(head["cubes"]),"width":128,"height":128,
      "portraitSha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}
    OUTPUT.with_suffix(".manifest.json").write_bytes((json.dumps(manifest,indent=2)+"\n").encode("utf-8"))
    print(json.dumps(manifest,indent=2))


if __name__=="__main__":render()
