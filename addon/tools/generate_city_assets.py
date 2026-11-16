"""Adapt pinned Modern Structures templates to Lost Cities chunk/floor parts.

Geometry derives from Modern Structures (Academic Free License 3.0).
The original mod JAR is read unchanged; no Java classes or textures are copied.
Rebuild with Python 3.11 and nbtlib: python addon/tools/generate_city_assets.py ORIGINAL.jar
"""
from pathlib import Path
import gzip,hashlib,io,json,math,sys,zipfile
import nbtlib

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'addon/src/main/resources/data/gnfrontier/lostcities'
def plain(value):
    if isinstance(value,dict):return {str(k):plain(v) for k,v in value.items()}
    if isinstance(value,(list,tuple)) or hasattr(value,'tolist'):return [plain(x) for x in value]
    if isinstance(value,str):return str(value)
    if isinstance(value,(float,nbtlib.Float,nbtlib.Double)):return float(value)
    return int(value)
def write(kind,name,data):
    p=DATA/kind/(name+'.json');p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n','utf-8')
def convert(archive,source,name,theme):
    raw=archive.read(f'data/modern_structures/structures/{source}.nbt')
    nbt=nbtlib.File.parse(io.BytesIO(gzip.decompress(raw)))
    sx,sy,sz=map(int,nbt['size']);nx,nz,ny=math.ceil(sx/16),math.ceil(sz/16),math.ceil(sy/6)
    palette=[plain(x) for x in nbt['palette']];cells={};entries=[{'char':' ','block':'minecraft:air'},{'char':'#','block':'minecraft:stone'}];indices={};next_char=0x100
    for block in nbt['blocks']:
        state=palette[int(block['state'])];blockid=state['Name']
        if blockid in ('minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:structure_block','minecraft:structure_void','minecraft:jigsaw'):continue
        props=state.get('Properties',{});definition=blockid+('['+','.join(f'{k}={v}' for k,v in sorted(props.items()))+']' if props else '')
        tag=plain(block['nbt']) if 'nbt' in block else None
        if tag:
            for key in ('x','y','z','id'):tag.pop(key,None)
        entry={'block':definition}
        if blockid in ('minecraft:chest','minecraft:trapped_chest','minecraft:barrel'):
            tag=tag or {}
            for key in ('Items','LootTable','LootTableSeed'):tag.pop(key,None)
            entry['loot']='gnfrontier:district_'+theme
        if tag:entry['tag']=tag
        key=json.dumps(entry,sort_keys=True);char=indices.get(key)
        if char is None:
            char=chr(next_char);next_char+=1;indices[key]=char;entries.append({'char':char,**entry})
        cells[tuple(map(int,block['pos']))]=char
    for cx in range(nx):
        for cz in range(nz):
            building=f'{name}_{cx}_{cz}';parts=[]
            for floor in range(ny):
                part=f'{building}_{floor}';slices=[]
                for ly in range(6):slices.append([''.join(cells.get((cx*16+x,floor*6+ly,cz*16+z),' ') for x in range(16)) for z in range(16)])
                write('parts',part,{'xsize':16,'zsize':16,'palette':{'palette':entries},'slices':slices});parts.append({'part':'gnfrontier:'+part,'floor':floor})
            write('buildings',building,{'filler':'#','rubble':'}','minfloors':ny-1,'maxfloors':ny-1,'maxcellars':0,'allowDoors':False,'parts':parts})
    write('multibuildings',name,{'dimx':nx,'dimz':nz,'buildings':[[f'gnfrontier:{name}_{x}_{z}' for z in range(nz)] for x in range(nx)]})
    write('conditions','district_'+theme,{'values':[{'factor':1,'value':'gnfrontier:chests/'+theme}]})
    return {'source':source,'asset':name,'originalNbtSha256':hashlib.sha256(raw).hexdigest(),'size':[sx,sy,sz],'chunkSize':[nx,nz],'floors':ny,'preservedSolidBlocks':len(cells),'paletteEntries':len(entries)}
def main():
    source=Path(sys.argv[1]);lock=json.loads((ROOT/'pack/runtime.lock.json').read_text('utf-8'))
    expected=next(x['sha256'] for x in lock['files'] if 'modern_structures-' in x['path'])
    assert hashlib.sha256(source.read_bytes()).hexdigest()==expected,'Original mod hash differs from the lock'
    with zipfile.ZipFile(source) as archive:
        rows=[convert(archive,*args) for args in [('factory','industrial_factory','industrial'),('factory_2','industrial_green_factory','industrial'),('bunker','military_bunker','military'),('research_outpost_2','military_outpost','military')]]
    report={'upstream':'Modern Structures 1.6','license':'Academic Free License 3.0','modSha256':expected,'generator':'addon/tools/generate_city_assets.py','templates':rows,'changes':['Split into 16x16x6 Lost Cities parts, pad margins with air','Keep source block states and block entity configuration; remove absolute saved coordinates','Use existing GN industrial/military loot conditions for storage containers','Keep source geometry; no new textures']}
    p=ROOT/'addon/city-template-sources.json';p.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8');print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
