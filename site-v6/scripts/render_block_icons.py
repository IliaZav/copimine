"""Offline orthographic GUI rendering from actual Minecraft JSON cuboids and UVs.
No invented block artwork. Original texture and model bytes come from the client
asset archive. Special entity meshes keep the extracted GUI sprite when available.
"""
from pathlib import Path
from functools import lru_cache
from PIL import Image, ImageDraw, ImageEnhance
import json, zipfile, io, math, numpy as np, argparse
ROOT=Path(__file__).resolve().parents[1]

def main(jar):
 with zipfile.ZipFile(jar) as z:
  names=set(z.namelist())
  @lru_cache(None)
  def model(key):
   if ':' not in key:key='minecraft:'+key
   path='assets/'+key.replace(':','/models/')+'.json'
   if path not in names:return {}
   cur=json.loads(z.read(path));base=model(cur['parent']) if cur.get('parent') else {}
   return {**base,**cur,'textures':{**base.get('textures',{}),**cur.get('textures',{})},'display':{**base.get('display',{}),**cur.get('display',{})}}
  def first(obj):
   if isinstance(obj,dict):
    if obj.get('type')=='minecraft:model':return obj.get('model')
    for k in ['fallback','model','base','models','entries','cases']:
     got=first(obj.get(k))
     if got:return got
   if isinstance(obj,list):
    for o in obj:
     got=first(o)
     if got:return got
  @lru_cache(None)
  def image(key):
   if ':' not in key:key='minecraft:'+key
   path='assets/'+key.replace(':','/textures/')+'.png'
   if path not in names:return None
   im=Image.open(io.BytesIO(z.read(path))).convert('RGBA');return im.crop((0,0,im.width,min(im.height,im.width)))
  def rotation(angles):
   x,y,zz=map(math.radians,angles);cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(zz),math.sin(zz)
   return np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])
  materials=json.loads((ROOT/'data/materials.json').read_text());count=0
  for row in materials:
   if row['kind']!='block':continue
   definition='assets/minecraft/items/'+row['key']+'.json'
   if definition not in names:continue
   key=first(json.loads(z.read(definition)))
   if not key:continue
   m=model(key)
   if not m.get('elements'):continue
   tex=m.get('textures',{});R=rotation(m.get('display',{}).get('gui',{}).get('rotation',[30,225,0]));faces=[]
   for e in m['elements']:
    x0,y0,z0=e['from'];x1,y1,z1=e['to'];er=e.get('rotation',{});axis=er.get('axis');ang=er.get('angle',0);R2=rotation([ang if axis==a else 0 for a in 'xyz']);origin=np.array(er.get('origin',[8,8,8]))
    geo={
     'up':([(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],[0,1,0]),
     'down':([(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)],[0,-1,0]),
     'north':([(x1,y1,z0),(x0,y1,z0),(x0,y0,z0),(x1,y0,z0)],[0,0,-1]),
     'south':([(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)],[0,0,1]),
     'east':([(x1,y1,z1),(x1,y1,z0),(x1,y0,z0),(x1,y0,z1)],[1,0,0]),
     'west':([(x0,y1,z0),(x0,y1,z1),(x0,y0,z1),(x0,y0,z0)],[-1,0,0])}
    for name,f in e.get('faces',{}).items():
     if name not in geo:continue
     pts,normal=geo[name];normal=R@R2@np.array(normal)
     if normal[2]<=0.005:continue
     t=f.get('texture','');seen=set()
     while isinstance(t,str) and t.startswith('#') and t not in seen:seen.add(t);t=tex.get(t[1:],'')
     if isinstance(t,dict):t=t.get('sprite','')
     im=image(t) if t else None
     if im is None:continue
     im=im.copy();uv=f.get('uv',[0,0,16,16]);u0,v0,u1,v1=[v*im.width/16 for v in uv];im=im.crop((min(u0,u1),min(v0,v1),max(u0,u1),max(v0,v1)))
     if im.width<1 or im.height<1:continue
     if u1<u0:im=im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
     if v1<v0:im=im.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
     if f.get('rotation'):im=im.rotate(-f['rotation'],expand=True)
     if f.get('tintindex') is not None:
      rr,gg,bb,aa=im.split();im=Image.merge('RGBA',(rr.point(lambda v:int(v*.52)),gg.point(lambda v:int(v*.83)),bb.point(lambda v:int(v*.32)),aa))
     im=ImageEnhance.Brightness(im).enhance({'up':1,'north':.78,'south':.82,'east':.68,'west':.7,'down':.6}[name])
     coords=(R@((R2@(np.array(pts)-origin).T).T+origin-8).T).T
     faces.append((coords,im))
   if not faces:continue
   allpts=np.concatenate([a[0] for a in faces]);xy=allpts[:,:2]*[1,-1];lo=xy.min(axis=0);hi=xy.max(axis=0);scale=56/max((hi-lo).max(),1);offset=(64-(hi-lo)*scale)/2-lo*scale
   canvas=Image.new('RGBA',(64,64));faces.sort(key=lambda a:a[0][:,2].mean())
   for pts,im in faces:
    xy=pts[:,:2]*[1,-1]*scale+offset
    # Inverse affine mapping uses vertices 0,1,3 of the projected parallelogram.
    A=np.array([[xy[i,0],xy[i,1],1] for i in [0,1,3]])
    if abs(np.linalg.det(A))<.01:continue
    coeffx=np.linalg.solve(A,[0,im.width,0]);coeffy=np.linalg.solve(A,[0,0,im.height]);coeff=tuple(coeffx)+tuple(coeffy)
    layer=im.transform((64,64),Image.Transform.AFFINE,coeff,Image.Resampling.NEAREST)
    mask=Image.new('L',(64,64));ImageDraw.Draw(mask).polygon([tuple(p) for p in xy],fill=255)
    alpha=np.minimum(np.array(layer.getchannel('A')),np.array(mask));layer.putalpha(Image.fromarray(alpha));canvas.alpha_composite(layer)
   # Actual 64px raster from model, all UVs retained, transparent margin.
   canvas.save(ROOT/row['image'],optimize=True);row['imageMethod']='orthographic-original-model';count+=1
  (ROOT/'data/materials.json').write_text(json.dumps(materials,ensure_ascii=False,separators=(',',':')))
  payload={'catalog':json.loads((ROOT/'data/catalog-current.json').read_text()),'materials':materials,'map':json.loads((ROOT/'data/map-source.json').read_text())}
  (ROOT/'assets/js/registry-v5.js').write_text('window.CM5_REGISTRY='+json.dumps(payload,ensure_ascii=False,separators=(',',':'))+';')
  print('Block icons rendered from original cuboid models:',count)
if __name__=='__main__':
 ap=argparse.ArgumentParser();ap.add_argument('client_jar');main(ap.parse_args().client_jar)
