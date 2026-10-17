#!/usr/bin/env python3
"""Rebuild the vanilla 26.3 item catalog from a hash-verified official client.
Python 3.11+. Run with --client path/to/minecraft-26.3-client.jar --site path/to/site-v6.
Only official item definitions and language data are read. Existing matching artwork is reused.
"""
from pathlib import Path
import argparse,datetime,hashlib,json,os,re,urllib.request,zipfile
CLIENT_SHA1="e877b6a07acd633fb3bb475002175cec036e7b87"
RU_SHA1="8a57cfb9eba6a0030253e1065e74f7c63da43a8b"
RU_URL="https://resources.download.minecraft.net/8a/"+RU_SHA1
def digest(path,algorithm):
 with path.open("rb") as f:return hashlib.file_digest(f,algorithm).hexdigest()
def write_json(path,value):
 raw=(json.dumps(value,ensure_ascii=False,indent=2)+"\n").encode("utf-8")
 path.parent.mkdir(parents=True,exist_ok=True)
 temp=path.with_name(path.name+".tmp-"+str(os.getpid()))
 temp.write_bytes(raw);temp.replace(path)
 return hashlib.sha256(raw).hexdigest()
def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument("--client",type=Path,required=True)
 parser.add_argument("--site",type=Path,default=Path(__file__).resolve().parents[1])
 parser.add_argument("--ru",type=Path,help="Optional already-downloaded official ru_ru.json")
 args=parser.parse_args();site=args.site.resolve();client=args.client.resolve()
 assert digest(client,"sha1")==CLIENT_SHA1,"Client SHA1 does not match the pinned 26.3 authority"
 client_sha256=digest(client,"sha256")
 if args.ru:ru_bytes=args.ru.read_bytes()
 else:
  req=urllib.request.Request(RU_URL,headers={"User-Agent":"CopiMine-catalog-authority/1.0"})
  with urllib.request.urlopen(req,timeout=30) as res:ru_bytes=res.read(5_000_001)
 assert len(ru_bytes)<=5_000_000,"Unexpected language asset size"
 assert hashlib.sha1(ru_bytes).hexdigest()==RU_SHA1,"Official Russian language asset hash mismatch"
 ru=json.loads(ru_bytes)
 old=json.loads((site/"data/materials.json").read_text(encoding="utf-8-sig"))
 old_by_id={r["id"].split(":")[-1]:r for r in old}
 rows=[];missing=[];renamed=[];definitions_hash=hashlib.sha256()
 with zipfile.ZipFile(client) as z:
  version=json.loads(z.read("version.json"))
  assert version["id"]=="26.3" and version["stable"] is True
  en=json.loads(z.read("assets/minecraft/lang/en_us.json"))
  entries=sorted(n for n in z.namelist() if n.startswith("assets/minecraft/items/") and n.endswith(".json"))
  assert len(entries)==1658,"Unexpected official item definition count"
  for entry in entries:
   item=Path(entry).stem;assert re.fullmatch("[a-z0-9_]+",item)
   raw=z.read(entry);definition=json.loads(raw)
   definitions_hash.update(entry.encode()+b"\0"+raw+b"\0")
   key=next((k for k in ("item.minecraft."+item,"block.minecraft."+item) if k in ru and k in en),None)
   if key is None:raise ValueError("No official Russian/English translation: "+item)
   old_row=old_by_id.get(item,{})
   old_image=old_row.get("image","")
   image=old_image if old_image and (site/old_image).is_file() and (site/old_image).resolve().is_relative_to(site) else ""
   row={"id":item,"key":"minecraft:"+item,"name":ru[key],"ru":ru[key],"en":en[key],
        "image":image,"giveable":item!="air","translationKey":key,
        "definitionSha256":hashlib.sha256(raw).hexdigest()}
   if image:
    row["imageMethod"]=old_row.get("imageMethod","existing-prototype-icon")
    row["imageSource"]="prototype-v5"
   else:
    row["imageMethod"]="non-giveable-air" if item=="air" else "special-model-no-flat-sprite"
    missing.append(item)
   if old_row.get("ru") and old_row["ru"]!=ru[key]:
    renamed.append({"id":item,"previous":old_row["ru"],"official":ru[key]})
   rows.append(row)
 assert len({r["id"] for r in rows})==1658
 assert sum(bool(r["image"]) for r in rows)==1617
 public_hash=write_json(site/"data/materials-v6.json",rows)
 private_hash=write_json(site/"server/private/materials.json",rows)
 assert public_hash==private_hash
 authority={"schemaVersion":1,"version":"26.3","worldVersion":version["world_version"],
  "protocolVersion":version["protocol_version"],"stable":version["stable"],
  "clientSHA1":CLIENT_SHA1,"clientSHA256":client_sha256,"clientBytes":client.stat().st_size,
  "ruSHA1":RU_SHA1,"ruSHA256":hashlib.sha256(ru_bytes).hexdigest(),"ruBytes":len(ru_bytes),
  "itemDefinitionsSHA256":definitions_hash.hexdigest(),"materialsSHA256":public_hash,
  "urls":{"client":"https://piston-data.mojang.com/v1/objects/"+CLIENT_SHA1+"/client.jar","ru":RU_URL},
  "materialCount":len(rows),"translatedCount":len(rows),"giveableCount":sum(r["giveable"] for r in rows),
  "iconCount":sum(bool(r["image"]) for r in rows),"missingIcons":missing,
  "iconProvenance":"1617 existing name-matched prototype icons; special entity models have no fabricated flat replacement.",
  "coverage":"All assets/minecraft/items/*.json definitions in the pinned stable client, not a hand-maintained list.",
  "translationRule":"Official item.minecraft.<id>, otherwise block.minecraft.<id>; both ru_ru and en_us must contain the key.",
  "renamedFromPrototype":renamed,
  "generatedAt":datetime.datetime.now(datetime.timezone.utc).isoformat()}
 write_json(site/"sources/materials-v6-authority.json",authority)
 print(json.dumps({"materials":len(rows),"translated":len(rows),"giveable":authority["giveableCount"],
  "icons":authority["iconCount"],"missingIcons":missing,"renamed":renamed,"clientSHA256":client_sha256,
  "catalogSHA256":public_hash,"written":["data/materials-v6.json","server/private/materials.json","sources/materials-v6-authority.json"]},ensure_ascii=True),flush=True)
if __name__=="__main__":main()
