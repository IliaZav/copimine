"""Render only saved surface blocks from the supplied Anvil region archive.
No server connection, cave scan or player marker export. Padded long decoding is
required for modern Java block states; the obsolete contiguous format stripes maps.
Run: python scripts/render_world.py /path/to/copimine-map-preview.tar.gz
Requires Pillow and numpy for this build step, not for viewing the final HTML.
"""
import sys, io, json, tarfile, struct, zlib, gzip, math, hashlib, time
from pathlib import Path
from collections import Counter
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
class NBT:
 def __init__(self,data): self.f=io.BytesIO(data)
 def read(self,n):
  if not 0<=n<=32*1024*1024: raise ValueError('Invalid NBT length')
  b=self.f.read(n)
  if len(b)!=n: raise EOFError('Truncated NBT')
  return b
 def num(self,fmt): return struct.unpack('>'+fmt,self.read(struct.calcsize('>'+fmt)))[0]
 def string(self): return self.read(self.num('H')).decode('utf8','replace')
 def payload(self,t,depth=0):
  if depth>60: raise ValueError('NBT too deep')
  if t in range(1,7): return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==8:return self.string()
  if t==7:return self.read(self.num('i'))
  if t in (11,12):
   n=self.num('i'); dt='>i4' if t==11 else '>i8'; return np.frombuffer(self.read(n*(4 if t==11 else 8)),dtype=dt)
  if t==9:
   sub=self.num('B');n=self.num('i')
   if not 0<=n<=2000000:raise ValueError('Invalid NBT list')
   return [self.payload(sub,depth+1) for _ in range(n)]
  if t==10:
   d={}
   while True:
    sub=self.num('B')
    if sub==0:return d
    key=self.string();d[key]=self.payload(sub,depth+1)
  raise ValueError('Unsupported tag '+str(t))
 def root(self):
  if self.num('B')!=10:raise ValueError('Root is not compound')
  self.string();return self.payload(10)
def packed(data,bits,indices):
 # No entry straddles a 64-bit word in modern storage.
 per=64//bits; a=np.asarray(data,dtype=np.int64).view(np.uint64)
 idx=np.asarray(indices,dtype=np.uint64)
 return ((a[(idx//per).astype(int)] >> ((idx%per)*bits)) & np.uint64((1<<bits)-1)).astype(np.int32)
def sample_color(name):
 name=name.split(':')[-1]
 p=ROOT/'assets/minecraft/block'/(name+'.png')
 for suffix in ('','_top','_side'):
  q=ROOT/'assets/minecraft/block'/(name+suffix+'.png')
  if q.exists():p=q;break
 if p.exists():
  im=Image.open(p).convert('RGBA');im=im.crop((0,0,im.width,min(im.width,im.height)))
  a=np.array(im);visible=a[:,:,3]>40
  if visible.any():c=a[:,:,:3][visible].mean(axis=0)
  else:c=np.array([115,115,115])
 else:c=np.array([126,123,113])
 if name in ('water','bubble_column','kelp','seagrass','tall_seagrass'):return np.array([51,108,177])
 if name in ('grass_block','short_grass','tall_grass','fern','large_fern'):return np.array([109,153,70])
 if 'leaves' in name:
  if 'cherry' in name:return np.array([206,153,175])
  if 'spruce' in name:return np.array([59,96,66])
  return np.array([76,127,58])
 if name=='lava':return np.array([218,93,27])
 return c

def run(path):
 started=time.time(); height=np.full((1024,1024),-65,dtype=np.int16);col=np.zeros((1024,1024,3),dtype=float); col[:]=[10,16,12]
 cache={};count=0;errors=[];versions=Counter(); blocks=Counter()
 with tarfile.open(path,'r:gz') as tar:
  for rz in (-1,0):
   for rx in (-1,0):
    name=f'CopiMine/region/r.{rx}.{rz}.mca';m=tar.getmember(name)
    if not m.isfile() or m.size>32*1024*1024:raise ValueError('Invalid region member')
    raw=tar.extractfile(m).read();loc=raw[:4096]
    for idx in range(1024):
     off=int.from_bytes(loc[idx*4:idx*4+3],'big')
     if not off:continue
     try:
      pos=off*4096;size=int.from_bytes(raw[pos:pos+4],'big');typ=raw[pos+4]
      if typ&128:raise ValueError('External chunk not supplied')
      data=raw[pos+5:pos+4+size]
      data=zlib.decompress(data) if typ==2 else gzip.decompress(data) if typ==1 else data
      obj=NBT(data).root();versions[int(obj.get('DataVersion',0))]+=1
      cx,cz=int(obj['xPos']),int(obj['zPos'])
      if cx//32!=rx or cz//32!=rz:raise ValueError('Chunk coordinate mismatch')
      sections={int(s['Y']):s['block_states'] for s in obj.get('sections',[]) if 'block_states' in s}
      if not sections:raise ValueError('No block states')
      hm=obj.get('Heightmaps',{}).get('WORLD_SURFACE')
      if hm is None:hm=obj.get('Heightmaps',{}).get('MOTION_BLOCKING')
      if hm is None:raise ValueError('Missing heightmap')
      top=packed(hm,9,np.arange(256))-65
      names=[]
      for i in range(256):
       y=int(top[i]);x=i%16;z=i//16;name='minecraft:air'
       for tries in range(24):
        bs=sections.get(y//16)
        if bs:
         pal=bs['palette'];n=0
         if len(pal)>1:
          bits=max(4,(len(pal)-1).bit_length());k=((y%16)*256+z*16+x);per=64//bits
          arr=bs['data'];n=(int(arr[k//per]) & ((1<<64)-1))>>((k%per)*bits)&((1<<bits)-1)
         if n>=len(pal):raise ValueError('Palette out of bounds')
         name=pal[n]['Name']
        if name not in ('minecraft:air','minecraft:cave_air','minecraft:void_air'):break
        y-=1
       names.append(name);top[i]=y;blocks[name]+=1
      gy=(cz+32)*16;gx=(cx+32)*16;height[gy:gy+16,gx:gx+16]=top.reshape(16,16)
      for name in set(names):
       if name not in cache:cache[name]=sample_color(name)
      col[gy:gy+16,gx:gx+16]=np.array([cache[n] for n in names]).reshape(16,16,3)
      count+=1
     except Exception as e:errors.append({'region':name,'index':idx,'error':str(e)})
    print('region',rx,rz,'loaded',count,flush=True)
 if errors:raise RuntimeError(json.dumps(errors[:10]))
 dz,dx=np.gradient(height.astype(float)); shade=np.clip(1+(dx*.018-dz*.029),.58,1.22)
 col=np.clip(col*shade[:,:,None],0,255).astype('uint8')
 image=Image.fromarray(col);out=ROOT/'assets/img/map-world.webp';image.save(out,lossless=True,method=6)
 meta={'format':'anvil-region','sourceArchive':Path(path).name,'sourceSha256':hashlib.sha256(Path(path).read_bytes()).hexdigest(),'chunks':count,'bounds':{'x':-512,'z':-512,'width':1024,'height':1024},'dataVersions':dict(versions),'coverage':'Only four supplied region files, not the complete server world','render':'Surface block textures; padded long palette decoder; no live positions','image':'assets/img/map-world.webp','renderSeconds':round(time.time()-started,2)}
 (ROOT/'data/map-source.json').write_text(json.dumps(meta,indent=2),encoding='utf8')
 print(json.dumps(meta),flush=True)
if __name__=='__main__':run(sys.argv[1])
