"""Render saved Anvil terrain from a private archive to a sparse web tile pyramid.

Only block surface colors and elevation are exported. Entity, player, inventory,
structure, POI and block-entity data are never included in the output. The input
archive remains local. Requires Python 3.10+ and Pillow for this build step.
"""
import os
os.environ.setdefault('OPENBLAS_NUM_THREADS', '1')
os.environ.setdefault('OMP_NUM_THREADS', '1')
import argparse, array, collections, gc, gzip, hashlib, io, json, math, multiprocessing, re, struct, sys, tarfile, time
from datetime import datetime, timezone
from pathlib import Path
from PIL import Image

TILE_SIZE = 512
MAX_ZOOM = 5
AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air',
       'minecraft:structure_void', 'minecraft:light'}
WORLD_IDS = {'CopiMine': ('overworld', 'Обычный мир'),
             'CopiMine_nether': ('nether', 'Нижний мир'),
             'CopiMine_the_end': ('end', 'Край')}


class NBT:
    def __init__(self, data): self.f = io.BytesIO(data)
    def read(self, n):
        if not 0 <= n <= 64 * 1024 * 1024: raise ValueError('Invalid NBT length')
        b = self.f.read(n)
        if len(b) != n: raise EOFError('Truncated NBT')
        return b
    def num(self, fmt): return struct.unpack('>' + fmt, self.read(struct.calcsize('>' + fmt)))[0]
    def string(self): return self.read(self.num('H')).decode('utf-8', 'replace')
    def payload(self, t, depth=0):
        if depth > 64: raise ValueError('NBT too deep')
        if t in range(1, 7): return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t == 8: return self.string()
        if t == 7: return self.read(self.num('i'))
        if t in (11, 12):
            n = self.num('i'); sz = 4 if t == 11 else 8
            values = array.array('i' if t == 11 else 'q')
            values.frombytes(self.read(n * sz))
            if sys.byteorder != 'big': values.byteswap()
            return values
        if t == 9:
            sub = self.num('B'); n = self.num('i')
            if not 0 <= n <= 2_000_000: raise ValueError('Invalid NBT list')
            return [self.payload(sub, depth + 1) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                sub = self.num('B')
                if sub == 0: return d
                key = self.string(); d[key] = self.payload(sub, depth + 1)
        raise ValueError('Unsupported tag ' + str(t))
    def root(self):
        if self.num('B') != 10: raise ValueError('Root is not compound')
        self.string(); return self.payload(10)


def packed(data, bits, indices):
    """Decode modern padded longs: entries never cross a 64-bit boundary."""
    per = 64 // bits; mask = (1 << bits) - 1
    if not data: raise ValueError('Packed data too short')
    try: return [((int(data[i // per]) & 0xffffffffffffffff) >> ((i % per) * bits)) & mask for i in indices]
    except IndexError: raise ValueError('Packed data too short') from None


def palette_indices(bs, indices):
    palette = bs['palette']
    if len(palette) == 1: return [0] * len(indices)
    result = packed(bs['data'], max(4, (len(palette) - 1).bit_length()), indices)
    if max(result) >= len(palette): raise ValueError('Palette index out of bounds')
    return result


def color_of(name, palette, missing):
    name = name.split(':')[-1]
    if name in ('water', 'bubble_column', 'kelp', 'kelp_plant', 'seagrass', 'tall_seagrass'):
        return [51,108,177]
    if name in ('grass_block', 'short_grass', 'tall_grass', 'fern', 'large_fern'):
        return [109,153,70]
    if 'leaves' in name:
        if 'cherry' in name: return [206,153,175]
        if 'spruce' in name: return [59,96,66]
        return [76,127,58]
    if name == 'lava': return [218,93,27]
    if name in ('air','cave_air','void_air','structure_void','light'): return [0,0,0]
    for candidate in (name, name + '_top', name + '_side'):
        if candidate in palette: return palette[candidate]
    aliases = {'grass_path':'dirt_path_top','magma_block':'magma','snow':'snow',
               'snow_block':'snow','hay_block':'hay_block_top','dried_kelp_block':'dried_kelp_top',
               'farmland':'farmland','powder_snow':'powder_snow','glow_lichen':'glow_lichen'}
    if name in aliases and aliases[name] in palette: return palette[aliases[name]]
    for suffix in ('_stairs','_slab','_wall','_fence','_fence_gate','_button','_pressure_plate'):
        if name.endswith(suffix):
            base = name[:-len(suffix)]
            for candidate in (base,base+'_planks',base+'_block',base+'s'):
                if candidate in palette: return palette[candidate]
    for suffix in ('_wall_sign','_sign','_hanging_sign','_wall_hanging_sign','_trapdoor','_door'):
        if name.endswith(suffix):
            candidate = name[:-len(suffix)] + '_planks'
            if candidate in palette: return palette[candidate]
    missing[name] += 1
    return [126,123,113]


def surface(obj, dimension, palette, missing):
    sections = {int(s['Y']):s['block_states'] for s in obj.get('sections', []) if 'block_states' in s}
    if not sections: return None
    # The extra biome-only section below the world is not the minimum block Y.
    min_y = int(obj.get('yPos', -4 if dimension == 'overworld' else 0)) * 16
    hm = obj.get('Heightmaps', {}).get('WORLD_SURFACE')
    if hm is None: hm = obj.get('Heightmaps', {}).get('MOTION_BLOCKING')
    if hm is None: return None  # Unfinished generation chunks have no public surface.
    top = array.array('h',(v + min_y - 1 for v in packed(hm,9,range(256))))
    colors = bytearray(768); visible = bytearray(256)
    prepared = {}
    for sy, bs in sections.items():
        names = [p['Name'] for p in bs['palette']]
        pc = [bytes(color_of(n,palette,missing)) for n in names]
        air = [n in AIR for n in names]
        bits=max(4,(len(names)-1).bit_length());per=64//bits
        prepared[sy]=(bs.get('data'),bits,per,(1<<bits)-1,pc,air,
                      0 if all(air) else 1 if not any(air) else 2)
    def cell(p,k):
        data,bits,per,mask,pc,air,kind=p
        n=0 if len(pc)==1 else ((int(data[k//per])&0xffffffffffffffff)>>((k%per)*bits))&mask
        if n>=len(pc):raise ValueError('Palette index out of bounds')
        return n
    if dimension == 'nether':
        # A useful Nether map needs the roof hidden. Find the first floor under
        # at least 4 consecutive air blocks while scanning from Y=120 downward.
        for i in range(256):
            top[i]=-1;run=0;opened=False
            for sy in range(7,-1,-1):
                p=prepared.get(sy);high=8 if sy==7 else 15
                if p is None or p[6]==0:
                    run+=high+1;opened=opened or run>=4;continue
                if p[6]==1 and not opened:
                    run=0;continue
                hit=False
                for ly in range(high,-1,-1):
                    n=cell(p,ly*256+i)
                    if p[5][n]:
                        run+=1;opened=opened or run>=4
                    elif opened:
                        top[i]=sy*16+ly;colors[i*3:i*3+3]=p[4][n];visible[i]=1;hit=True;break
                    else:run=0
                if hit:break
        # Columns without an exposed cavern remain transparent, not invented.
    else:
        for i in range(256):
            y=top[i]
            for _ in range(32):
                if y<min_y:break
                p=prepared.get(y//16)
                if p is not None:
                    n=cell(p,(y%16)*256+i)
                    if not p[5][n]:
                        top[i]=y;colors[i*3:i*3+3]=p[4][n];visible[i]=1;break
                y-=1
    return top,colors,visible


def read_chunk(raw, index, external=None):
    off = int.from_bytes(raw[index*4:index*4+3], 'big')
    sectors = raw[index*4+3]
    if off == 0: return None
    pos = off * 4096
    if off < 2 or pos + 5 > len(raw): raise ValueError('Invalid region offset')
    size = int.from_bytes(raw[pos:pos+4], 'big'); typ = raw[pos+4]
    if size < 1 or size + 4 > sectors * 4096 or pos + size + 4 > len(raw):
        raise ValueError('Invalid chunk payload size')
    if typ & 128:
        if external is None:raise ValueError('External chunk file is missing')
        data=external;typ &= 127
    else:data = raw[pos+5:pos+4+size]
    if typ == 2: data = zlib_decompress(data)
    elif typ == 1: data = gzip.decompress(data)
    elif typ != 3: raise ValueError('Unsupported compression type ' + str(typ))
    return NBT(data).root()


def zlib_decompress(data):
    import zlib
    dec = zlib.decompressobj()
    result = dec.decompress(data, 64*1024*1024)
    if dec.unconsumed_tail: raise ValueError('Chunk decompression limit exceeded')
    if not dec.eof: raise ValueError('Truncated compressed chunk')
    return result


def save_tile(path, im):
    path.parent.mkdir(parents=True, exist_ok=True)
    try:im.save(path, format='WEBP', quality=88, method=4, exact=True)
    finally:im.close()


def make_pyramid(root, dimension, coordinates):
    levels = []
    coords = set(coordinates)
    for zoom in range(MAX_ZOOM, -1, -1):
        levels.append({'zoom':zoom,'blocksPerPixel':2**(MAX_ZOOM-zoom),
                       'tileCount':len(coords),'coordinates':[list(c) for c in sorted(coords)]})
        if zoom == 0: break
        parents = {(x//2,z//2) for x,z in coords}
        for px,pz in sorted(parents):
            im = Image.new('RGBA', (TILE_SIZE,TILE_SIZE), (0,0,0,0))
            for dx in (0,1):
                for dz in (0,1):
                    child = (px*2+dx,pz*2+dz)
                    if child not in coords: continue
                    q = root/'assets/map'/dimension/str(zoom)/str(child[0])/(str(child[1])+'.webp')
                    with Image.open(q) as src:
                        small = src.convert('RGBA').resize((256,256), Image.Resampling.BOX)
                    im.paste(small, (dx*256,dz*256))
            save_tile(root/'assets/map'/dimension/str(zoom-1)/str(px)/(str(pz)+'.webp'), im)
        coords = parents
    return sorted(levels, key=lambda d:d['zoom'])


def make_overview(root, dimension, bounds, finest_coords):
    minx,minz,maxx,maxz = [bounds[k] for k in ('minX','minZ','maxX','maxZ')]
    scale = max(1, math.ceil(max(maxx-minx,maxz-minz)/1536))
    w,h = max(1,math.ceil((maxx-minx)/scale)),max(1,math.ceil((maxz-minz)/scale))
    im = Image.new('RGBA', (w,h), (0,0,0,0))
    for x,z in finest_coords:
        q=root/'assets/map'/dimension/str(MAX_ZOOM)/str(x)/(str(z)+'.webp')
        # Crop generated data to the exact chunk bounds before mapping pixels.
        ox,oz=x*TILE_SIZE,z*TILE_SIZE
        cx0,cz0=max(minx,ox),max(minz,oz)
        cx1,cz1=min(maxx,ox+TILE_SIZE),min(maxz,oz+TILE_SIZE)
        if cx1<=cx0 or cz1<=cz0:continue
        box=(round((cx0-minx)/scale),round((cz0-minz)/scale),round((cx1-minx)/scale),round((cz1-minz)/scale))
        if box[2]<=box[0] or box[3]<=box[1]:continue
        with Image.open(q) as src:
            crop=src.convert('RGBA').crop((cx0-ox,cz0-oz,cx1-ox,cz1-oz))
            crop=crop.resize((box[2]-box[0],box[3]-box[1]),Image.Resampling.BOX)
        im.paste(crop,(box[0],box[1]))
    target=root/'assets/map'/dimension/'overview.webp'
    save_tile(target,im)
    return {'width':w,'height':h,'blocksPerPixel':scale}


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def bounds_empty():
    return {'minX':2**31,'minZ':2**31,'maxX':-2**31,'maxZ':-2**31}


def extend_bounds(bounds,cx,cz):
    bounds['minX']=min(bounds['minX'],cx*16);bounds['minZ']=min(bounds['minZ'],cz*16)
    bounds['maxX']=max(bounds['maxX'],(cx+1)*16);bounds['maxZ']=max(bounds['maxZ'],(cz+1)*16)


def atomic_json(path,value):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    temporary=path.with_name(path.name+'.tmp')
    temporary.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
    temporary.replace(path)


def stable_read(path,max_bytes=64*1024*1024,attempts=3):
    """Bounded read-only snapshot; never locks or writes a live game region."""
    last=None
    for attempt in range(attempts):
        try:
            before=path.stat()
            if before.st_size>max_bytes:raise ValueError('Source file exceeds the renderer size limit')
            with path.open('rb') as stream:
                raw=stream.read(max_bytes+1)
                stream.seek(0);header=stream.read(min(8192,len(raw)))
            after=path.stat()
            if (before.st_size==after.st_size==len(raw) and before.st_mtime_ns==after.st_mtime_ns
                    and before.st_ino==after.st_ino and raw[:len(header)]==header):
                return raw,attempt
            last='Source file changed while being read'
        except (OSError,ValueError) as exc:last=str(exc)
        if attempt+1<attempts:time.sleep(0.1*(attempt+1))
    raise RuntimeError('Stable source snapshot failed after '+str(attempts)+' attempts: '+str(last))


def spawn_only(raw):
    data=NBT(gzip.decompress(raw)).root().get('Data',{})
    if not all(k in data for k in ('SpawnX','SpawnY','SpawnZ')):return None
    return {'x':int(data['SpawnX']),'y':int(data['SpawnY']),'z':int(data['SpawnZ'])}


def external_chunk_indices(raw):
    found=[]
    for i in range(1024):
        offset=int.from_bytes(raw[i*4:i*4+3],'big')*4096
        if offset and offset+4<len(raw) and raw[offset+4]&128:found.append(i)
    return found


class RegionSource:
    def __init__(self,args):
        self.args=args;self.path=Path(args.input).resolve();self.spawns={};self.fingerprints=[]
        self.started=utc_now();self.retries=0;self.region_bytes=0;self.external_files=0
        self.archive=self.path.is_file();self.seen=set();self.files_read=0
        self.meta={'kind':'archive' if self.archive else 'directory','label':self.path.name,
                   'snapshotStartedAt':self.started,'capturedAt':args.captured_at}
        if self.archive:
            with self.path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
            self.meta.update({'archive':self.path.name,'sha256':digest,'bytes':self.path.stat().st_size,
                'archiveModifiedAt':datetime.fromtimestamp(self.path.stat().st_mtime,timezone.utc).isoformat()})
            if not self.meta['capturedAt']:
                match=re.search(r'(\d{8})-(\d{6})',self.path.name)
                if match:
                    self.meta['capturedAt']=datetime.strptime(''.join(match.groups()),'%Y%m%d%H%M%S').isoformat()
                    self.meta['captureTimeSource']='archive-filename; timezone is unspecified'
                else:self.meta['captureTimeSource']='unknown; supply --captured-at'
            else:self.meta['captureTimeSource']='explicit --captured-at'
        else:
            if not self.path.is_dir():raise ValueError('Input is not a readable archive or world directory')
            self.meta['capturedAt']=args.captured_at or self.started
            self.meta['captureTimeSource']='explicit --captured-at' if args.captured_at else 'live snapshot start UTC'
            self.meta['consistency']='Each region read is checked for stable size, timestamp and location header; capture across regions is not atomic.'
            self.directory_worlds=self.discover_directory_worlds()

    def discover_directory_worlds(self):
        root=self.path;world=root
        direct=any((root/p).is_dir() for p in ('region','DIM-1/region','DIM1/region'))
        if not direct:
            wanted=self.args.world_name
            props=root/'server.properties'
            if not wanted and props.is_file():
                # Read only the public level-name setting; never export this file.
                for line in props.read_text(encoding='utf-8',errors='replace').splitlines():
                    if line.startswith('level-name='):wanted=line.partition('=')[2].strip();break
                if wanted and (Path(wanted).name!=wanted or wanted in ('.','..')):
                    raise ValueError('level-name must name a direct world subdirectory')
            if wanted:
                world=root/wanted
                if not (world/'region').is_dir():raise ValueError('Configured overworld region directory is missing')
            else:
                candidates=[p for p in root.iterdir() if p.is_dir() and not p.name.startswith('.') and (p/'region').is_dir()]
                if len(candidates)!=1:
                    raise ValueError('Expected one overworld directory; use --world-name to select it')
                world=candidates[0]
        found={}
        def add(dim,parent,subdir):
            region=parent/subdir
            if not region.is_dir():return
            if dim in found and found[dim][0]!=region:raise ValueError('Multiple directories for '+dim)
            found[dim]=(region,parent/'level.dat')
        add('overworld',world,'region');add('nether',world,'DIM-1/region');add('end',world,'DIM1/region')
        if (world/'region').is_dir():
            add('nether',world.parent/(world.name+'_nether'),'DIM-1/region')
            add('end',world.parent/(world.name+'_the_end'),'DIM1/region')
        if not found:raise ValueError('No region directories found in the selected world')
        return found

    def read_directory_file(self,path,logical,max_bytes=64*1024*1024):
        raw,retries=stable_read(path,max_bytes,self.args.snapshot_attempts)
        self.retries+=retries;self.files_read+=1
        self.fingerprints.append((logical,hashlib.sha256(raw).hexdigest(),len(raw)))
        return raw

    def __iter__(self):
        if self.archive:
            mapping=dict(WORLD_IDS)
            mapping.update({'world':('overworld','Обычный мир'),'world_nether':('nether','Нижний мир'),
                            'world_the_end':('end','Край')})
            if self.args.world_name:
                prefix=self.args.world_name
                mapping.update({prefix:('overworld','Обычный мир'),prefix+'_nether':('nether','Нижний мир'),prefix+'_the_end':('end','Край')})
            pattern=re.compile(r'^([^/]+)/(?:DIM-1/|DIM1/)?region/r\.(-?\d+)\.(-?\d+)\.mca$')
            with tarfile.open(self.path,'r|gz') as archive:
                for member in archive:
                    components=member.name.split('/')
                    if len(components)==2 and components[1]=='level.dat' and components[0] in mapping:
                        if not member.isfile() or member.size>1024*1024:raise ValueError('Invalid level.dat')
                        with archive.extractfile(member) as stream:self.spawns[mapping[components[0]][0]]=spawn_only(stream.read())
                        continue
                    match=pattern.match(member.name)
                    if not match or match[1] not in mapping:continue
                    if not member.isfile() or not 8192<=member.size<=64*1024*1024:raise ValueError('Invalid region member')
                    dim=mapping[match[1]][0];rx,rz=int(match[2]),int(match[3])
                    with archive.extractfile(member) as stream:raw=stream.read()
                    external={}
                    for index in external_chunk_indices(raw):
                        logical=member.name.rsplit('/',1)[0]+'/c.'+str(rx*32+index%32)+'.'+str(rz*32+index//32)+'.mcc'
                        with tarfile.open(self.path,'r:gz') as extra:
                            entry=extra.getmember(logical)
                            if not entry.isfile() or entry.size>64*1024*1024:raise ValueError('Invalid external chunk')
                            with extra.extractfile(entry) as stream:external[index]=stream.read()
                        self.external_files+=1
                    yield self.checked(dim,rx,rz,raw,external)
        else:
            for dim,(directory,level) in self.directory_worlds.items():
                if level.is_file():self.spawns[dim]=spawn_only(self.read_directory_file(level,dim+'/level.dat',1024*1024))
                entries=[]
                for path in directory.glob('r.*.*.mca'):
                    match=re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca',path.name)
                    if match:entries.append((int(match[1]),int(match[2]),path))
                for rx,rz,path in sorted(entries):
                    raw=self.read_directory_file(path,dim+'/region/'+path.name)
                    if len(raw)<8192:raise ValueError('Region header is incomplete')
                    external={}
                    for index in external_chunk_indices(raw):
                        path=directory/('c.'+str(rx*32+index%32)+'.'+str(rz*32+index//32)+'.mcc')
                        external[index]=self.read_directory_file(path,dim+'/region/'+path.name)
                        self.external_files+=1
                    yield self.checked(dim,rx,rz,raw,external)

    def checked(self,dim,rx,rz,raw,external):
        key=(dim,rx,rz)
        if key in self.seen:raise ValueError('Duplicate region coordinates for one dimension')
        self.seen.add(key);self.region_bytes+=len(raw)
        return dim,rx,rz,raw,external

    def finish(self):
        self.meta.update({'snapshotCompletedAt':utc_now(),'regionBytes':self.region_bytes,
                          'stableReadRetries':self.retries,'externalChunkFiles':self.external_files})
        if not self.archive:
            digest=hashlib.sha256()
            for name,sha,size in sorted(self.fingerprints):digest.update((name+'\0'+sha+'\n').encode('utf-8'))
            self.meta.update({'archive':None,'sha256':digest.hexdigest(),'bytes':sum(x[2] for x in self.fingerprints),
                'filesRead':self.files_read,'fingerprintAlgorithm':'SHA256 of sorted logical file names and content SHA256 digests'})
        return self.meta


def render_region_job(job):
    dim,rx,rz,raw,external,palette,root,checkpoints,reuse=job
    root=Path(root);checkpoints=Path(checkpoints)
    token=hashlib.sha256(raw)
    for index,data in sorted(external.items()):token.update(str(index).encode('ascii'));token.update(data)
    token.update(json.dumps(palette,sort_keys=True).encode('utf-8'));token.update(b'renderer-1.1')
    token=token.hexdigest();cached_path=checkpoints/dim/(str(rx)+'_'+str(rz)+'.json')
    target=root/'assets/map'/dim/str(MAX_ZOOM)/str(rx)/(str(rz)+'.webp')
    if cached_path.is_file():
        cached=json.loads(cached_path.read_text(encoding='utf-8'))
        if cached.get('token')==token and (not cached['stats']['surfacePixels'] or target.is_file()):
            return cached['stats'],cached['missing'],True
    stat={'regionFiles':1,'savedChunks':0,'chunks':0,'skippedUnfinishedChunks':0,'emptySurfaceChunks':0,
          'dataVersions':collections.Counter(),'chunkStatuses':collections.Counter(),'bounds':bounds_empty(),
          'savedBounds':bounds_empty(),'surfacePixels':0,'surfaceMinY':32767,'surfaceMaxY':-32768,'coords':[],
          'errors':[],'externalChunks':len(external)}
    height=array.array('h',[0])*(TILE_SIZE*TILE_SIZE);colors=bytearray(TILE_SIZE*TILE_SIZE*3)
    alpha=bytearray(TILE_SIZE*TILE_SIZE);missing=collections.Counter()
    for index in range(1024):
        if not any(raw[index*4:index*4+3]):continue
        stat['savedChunks']+=1
        try:
            obj=read_chunk(raw,index,external.get(index));cx,cz=int(obj['xPos']),int(obj['zPos'])
            if cx!=rx*32+index%32 or cz!=rz*32+index//32:raise ValueError('Chunk coordinate mismatch')
            extend_bounds(stat['savedBounds'],cx,cz)
            stat['chunkStatuses'][str(obj.get('Status','unknown'))]+=1
            stat['dataVersions'][int(obj.get('DataVersion',0))]+=1
            result=surface(obj,dim,palette,missing)
            if result is None:stat['skippedUnfinishedChunks']+=1;continue
            h,c,a=result
            if not any(a):stat['emptySurfaceChunks']+=1;continue
            gx,gz=(cx%32)*16,(cz%32)*16
            for row in range(16):
                dest=(gz+row)*512+gx;src=row*16
                height[dest:dest+16]=h[src:src+16]
                colors[dest*3:(dest+16)*3]=c[src*3:(src+16)*3];alpha[dest:dest+16]=a[src:src+16]
            stat['chunks']+=1;stat['surfacePixels']+=sum(a)
            visible_heights=[h[i] for i in range(256) if a[i]]
            stat['surfaceMinY']=min(stat['surfaceMinY'],min(visible_heights));stat['surfaceMaxY']=max(stat['surfaceMaxY'],max(visible_heights))
            extend_bounds(stat['bounds'],cx,cz)
        except Exception as exc:stat['errors'].append({'dimension':dim,'region':[rx,rz],'chunkIndex':index,'error':str(exc)})
    if stat['errors']:return stat,dict(missing),False
    if stat['surfacePixels']:
        stat['coords']=[[rx,rz]]
        if not (reuse and target.is_file()):
            rgba=bytearray(TILE_SIZE*TILE_SIZE*4)
            for i,a in enumerate(alpha):
                if not a:continue
                dx=height[i]-height[i-1] if i%512 and alpha[i-1] else 0
                dz=height[i]-height[i-512] if i>=512 and alpha[i-512] else 0
                shade=max(.58,min(1.22,1+dx*.018-dz*.029));s=i*3;d=i*4
                rgba[d]=min(255,int(colors[s]*shade));rgba[d+1]=min(255,int(colors[s+1]*shade));rgba[d+2]=min(255,int(colors[s+2]*shade));rgba[d+3]=255
            save_tile(target,Image.frombytes('RGBA',(512,512),bytes(rgba)))
    atomic_json(cached_path,{'token':token,'stats':stat,'missing':dict(missing)})
    gc.collect()
    return stat,dict(missing),False


def merge_stats(total,current):
    for key in ('regionFiles','savedChunks','chunks','skippedUnfinishedChunks','emptySurfaceChunks','surfacePixels','externalChunks'):
        total[key]=total.get(key,0)+current[key]
    for key in ('dataVersions','chunkStatuses'):
        for name,count in current[key].items():total.setdefault(key,{})[str(name)]=total.setdefault(key,{}).get(str(name),0)+count
    for key in ('bounds','savedBounds'):
        if not current['surfacePixels' if key=='bounds' else 'savedChunks']:continue
        dest=total.setdefault(key,bounds_empty())
        for name,value in current[key].items():dest[name]=(min if name.startswith('min') else max)(dest[name],value)
    if current['surfacePixels']:
        total['surfaceMinY']=min(total.get('surfaceMinY',32767),current['surfaceMinY'])
        total['surfaceMaxY']=max(total.get('surfaceMaxY',-32768),current['surfaceMaxY'])
    total.setdefault('coords',[]).extend(current['coords'])


def render(args):
    start=time.time();root=Path(args.output).resolve();root.mkdir(parents=True,exist_ok=True)
    palette=json.loads(Path(args.palette).read_text(encoding='utf-8'))
    source=RegionSource(args);dimensions={};missing=collections.Counter();errors=[]
    checkpoints=Path(args.checkpoint_dir) if args.checkpoint_dir else root.parent/(root.name+'.render-checkpoints')
    labels={'overworld':'Обычный мир','nether':'Нижний мир','end':'Край'}
    try:
        # One small worker at a time limits memory; worker recycling releases
        # codec allocations. Raw region bytes stay in private process memory.
        with multiprocessing.get_context('spawn').Pool(processes=1,maxtasksperchild=8) as pool:
            for dim,rx,rz,raw,external in source:
                stat,miss,cached=pool.apply(render_region_job,((dim,rx,rz,raw,external,palette,str(root),str(checkpoints),args.reuse_existing_tiles),))
                total=dimensions.setdefault(dim,{'id':dim,'name':labels[dim]});merge_stats(total,stat);missing.update(miss)
                errors.extend(stat['errors'])
                print(json.dumps({'region':dim,'x':rx,'z':rz,'chunks':total['chunks'],'saved':total['savedChunks'],
                    'errors':len(errors),'cached':cached,'seconds':round(time.time()-start,1)}),flush=True)
                if errors:raise RuntimeError(str(len(errors))+' malformed chunks; publication cancelled')
        if not dimensions:raise RuntimeError('No region files were found in the selected source')
        source_metadata=source.finish()
        for dim in dimensions.values():
            dim['sourceSpawn']=source.spawns.get(dim['id']);coords=[tuple(c) for c in dim.pop('coords')]
            if not dim['surfacePixels']:dim['bounds']={'minX':0,'minZ':0,'maxX':0,'maxZ':0}
            if not dim['savedChunks']:dim['savedBounds']={'minX':0,'minZ':0,'maxX':0,'maxZ':0}
            levels=make_pyramid(root,dim['id'],coords)
            dim['overview']='assets/map/'+dim['id']+'/overview.webp'
            dim['overviewMetadata']=make_overview(root,dim['id'],dim['bounds'],coords)
            dim['bounds']['width']=dim['bounds']['maxX']-dim['bounds']['minX'];dim['bounds']['height']=dim['bounds']['maxZ']-dim['bounds']['minZ']
            dim['tiles']={'template':'assets/map/'+dim['id']+'/{zoom}/{x}/{z}.webp','tileSize':512,'minZoom':0,'maxZoom':MAX_ZOOM,
                'levels':levels,'coordinateFormula':'worldX = tileX * 512 * blocksPerPixel; worldZ = tileZ * 512 * blocksPerPixel'}
            dim['renderMode']='nether-floor-below-roof' if dim['id']=='nether' else 'saved-world-surface'
            dim['renderDescription']=('Roof hidden; first saved floor below 4 consecutive air blocks, scanned from Y=120 down.' if dim['id']=='nether'
                else 'Highest non-air block from WORLD_SURFACE heightmap; no underground scan.')
        manifest={'schemaVersion':1,'source':source_metadata,'generatedAt':utc_now(),'tileSize':512,'dimensions':list(dimensions.values()),
            'renderSeconds':round(time.time()-start,2),'renderer':'CopiMine sparse Anvil surface renderer 1.1','errors':0,
            'coverage':'All selected saved region files. Transparent pixels are absent, empty or unfinished terrain; no generated estimates.',
            'privacy':'No player identities, locations, entities, inventory, block entities, POI, seed or raw region data exported.',
            'colorNotes':'Average block texture colors, generic foliage/water colors, directional elevation shading; biome tints are approximate.',
            'unmatchedBlockColors':dict(missing.most_common())}
        atomic_json(root/'data/map-manifest.json',manifest)
        print(json.dumps({'complete':True,'seconds':manifest['renderSeconds'],'dimensions':[{'id':d['id'],'chunks':d['chunks'],
            'savedChunks':d['savedChunks'],'bounds':d['bounds']} for d in dimensions.values()],'errors':0,
            'outputBytes':sum(p.stat().st_size for p in (root/'assets/map').rglob('*.webp'))},ensure_ascii=True),flush=True)
    except Exception as exc:
        atomic_json(checkpoints/'render-errors.json',{'error':str(exc),'chunkErrors':errors,'regionFilesProcessed':len(source.seen)})
        raise


if __name__=='__main__':
    multiprocessing.freeze_support()
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',help='Private tar.gz archive, a world directory, or server root containing the worlds')
    parser.add_argument('--output',required=True,help='Private staging directory; publish only after successful exit')
    parser.add_argument('--palette',default=str(Path(__file__).with_name('map-palette.json')))
    parser.add_argument('--captured-at',default=None,help='ISO8601 source capture time; inferred from archive filename or live snapshot start')
    parser.add_argument('--world-name',default=None,help='Overworld directory name when the server root contains multiple worlds')
    parser.add_argument('--snapshot-attempts',type=int,default=3)
    parser.add_argument('--checkpoint-dir',default=None,help='Private progress directory; never include it in the public site')
    parser.add_argument('--reuse-existing-tiles',action='store_true',help='Recovery only: retain tiles already verified to belong to this exact source')
    args=parser.parse_args()
    if not 1<=args.snapshot_attempts<=10:parser.error('--snapshot-attempts must be between 1 and 10')
    render(args)
