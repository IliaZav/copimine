#!/usr/bin/env python3
"""Build the reviewed plugin extensions in an isolated directory; never deploy.

Inputs are exact Git source blobs or a verified source tree. Existing plugin JARs
are read only as a compilation classpath. No stock build script is executed
because those scripts copy their result into the running server.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parent
REF="a16dc50002f3cde94a4f0e350a18fccd4e1c8bf5"
ADMIN="copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java"
ADMIN_SHA256="a3eb258941603ebc787973d662101e8b8dbb68fe8420d8428c75dd44003034fc"
MODULES=("copimine-admin-plugin","copimine-artifacts","copimine-economy-core")

def sha(value):
    return hashlib.sha256(value).hexdigest()

def replace_once(source,old,new):
    if source.count(old)!=1:
        raise ValueError("Expected exactly one source anchor: "+old[:100])
    return source.replace(old,new,1)

def patch_admin(raw,fragment):
    if sha(raw)!=ADMIN_SHA256:
        raise ValueError("Admin source hash mismatch; refusing to patch an unreviewed plugin")
    source=raw.decode("utf-8")
    if "class SiteBridge" in source:
        raise ValueError("Bridge already exists")
    source=replace_once(source,"Bukkit.getPluginManager().registerEvents(this, this);",
        "Bukkit.getPluginManager().registerEvents(this, this);\n        siteBridge.start();")
    source=replace_once(source,"@Override public void onDisable() {",
        "@Override public void onDisable() {\n        siteBridge.close();")
    source=replace_once(source,"String root=command.getName().toLowerCase(Locale.ROOT);",
        'String root=command.getName().toLowerCase(Locale.ROOT);\n            if(root.equals("cmultra")&&args.length>0&&args[0].equals("sitebridge"))return siteBridge.command(sender,args);')
    source=replace_once(source,"private void handleExtraPrank(Player admin,Player t,String act)",
        "private void handleExtraPrank(CommandSender admin,Player t,String act)")
    source=replace_once(source,"private void repairHand(Player a,Player t)",
        "private void repairHand(CommandSender a,Player t)")
    source=replace_once(source,"private long voiceMute(Player target, int minutes) {",
        'private long voiceMute(Player target, int minutes) {\n        siteBridge.externalChange(target.getUniqueId(),"mute:voice");')
    marker='if(frozen.remove(t.getUniqueId())){staffNotify("&bFreeze снят:'
    source=replace_once(source,marker,'siteBridge.externalChange(t.getUniqueId(),"freeze"); '+marker)
    source=source.rstrip()
    if not source.endswith("}"):
        raise ValueError("Unexpected Java source ending")
    return (source[:-1]+fragment+"\n}\n").encode("utf-8")

def patch_locked(raw,lock,fragment):
    if sha(raw)!=lock["sourceSha256"]:
        raise ValueError("Source hash mismatch: "+lock["path"])
    text=raw.decode("utf-8")
    if lock["requiredAbsent"] in text:
        raise ValueError("Extension already exists")
    return replace_once(text,lock["insertBefore"],fragment+"\n"+lock["insertBefore"]).encode("utf-8")

def source_files(git_repo,source_root):
    if git_repo:
        paths=subprocess.check_output(["git","-C",str(git_repo),"ls-tree","-r","--name-only",REF,"--",*MODULES],text=True).splitlines()
        for path in paths:
            if "/src/" in path and path.endswith(".java") or path.endswith(("/plugin.yml","/config.yml","/messages_ru.yml")):
                yield path,subprocess.check_output(["git","-C",str(git_repo),"show",REF+":"+path])
    else:
        for module in MODULES:
            folder=source_root/module
            for path in sorted(folder.rglob("*")):
                if path.is_file() and (path.suffix==".java" and "src" in path.parts or path.name in ("plugin.yml","config.yml","messages_ru.yml")):
                    yield path.relative_to(source_root).as_posix(),path.read_bytes()

def build(args):
    output=args.output.resolve()
    if output==ROOT or output.is_relative_to(ROOT/"java"):
        raise ValueError("Build output must be an isolated directory")
    output.mkdir(parents=True,exist_ok=True)
    staged=output/"source"
    staged.mkdir(exist_ok=True)
    admin_fragment=(ROOT/"SiteBridge.java.inc").read_text(encoding="utf-8")
    locks=[]
    for name in ("artifact","economy"):
        path=ROOT/(name+"-source-lock.json")
        if path.exists():
            lock=json.loads(path.read_text(encoding="utf-8"))
            lock["fragmentBytes"]=(ROOT/lock["fragment"]).read_bytes()
            if lock.get("fragmentSha256") and sha(lock["fragmentBytes"])!=lock["fragmentSha256"]:
                raise ValueError("Fragment checksum changed after review: "+name)
            locks.append(lock)
    records=[]
    for path,raw in source_files(args.git_repo,args.source_root):
        original=raw
        if path==ADMIN:
            raw=patch_admin(raw,admin_fragment)
        for lock in locks:
            if path==lock["path"]:
                raw=patch_locked(raw,lock,lock["fragmentBytes"].decode("utf-8"))
        if path=="copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java":
            # Exact source has six old enum names removed in Paper26.3.
            # These are the same registry attributes, verified with javap on the target API.
            text=raw.decode("utf-8")
            for old,new,count in (("Attribute.GENERIC_MAX_HEALTH","Attribute.MAX_HEALTH",3),
                                  ("Attribute.GENERIC_ATTACK_DAMAGE","Attribute.ATTACK_DAMAGE",3)):
                if text.count(old)!=count:
                    raise ValueError("Unexpected Paper compatibility anchor count: "+old)
                text=text.replace(old,new)
            raw=text.encode("utf-8")
        if path.endswith("/plugin.yml"):
            text=raw.decode("utf-8")
            text=re.sub(r"(?m)^version:\s*['\"]?([^'\"\n]+)['\"]?\s*$",lambda m:"version: '"+m.group(1)+"-site-v6.1.0'",text,count=1)
            raw=text.encode("utf-8")
        destination=staged/path
        destination.parent.mkdir(parents=True,exist_ok=True)
        destination.write_bytes(raw)
        records.append({"path":path,"sourceSha256":sha(original),"patchedSha256":sha(raw)})
    if not (staged/ADMIN).is_file():
        raise ValueError("Verified admin source is missing")
    protocol=ROOT/"java/me/copimine/ultimateplus/BridgeProtocol.java"
    protocol_target=staged/"copimine-admin-plugin/src/me/copimine/ultimateplus/BridgeProtocol.java"
    protocol_target.write_bytes(protocol.read_bytes())
    for required in ("copimine-admin-plugin","copimine-artifacts"):
        if not (staged/required/"plugin.yml").exists():
            raise ValueError("Complete verified source resources required: "+required)
    report={"schemaVersion":1,"sourceCommit":REF,"compileOnly":True,"deployed":False,"sourceFiles":records}
    (output/"source-provenance.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    if args.prepare_only:
        print(json.dumps({"prepared":True,"files":len(records)}))
        return
    if not args.javac or not args.classpath:
        raise ValueError("--javac and --classpath JSON file are required for compilation")
    classpath=[Path(p).resolve() for p in json.loads(args.classpath.read_text(encoding="utf-8"))]
    if any(not p.is_file() for p in classpath):
        raise ValueError("A compilation dependency is missing")
    classes=output/"classes"
    classes.mkdir(exist_ok=True)
    sources=sorted(staged.rglob("*.java"))
    def quote(value):
        return '"'+str(value).replace("\\","/").replace('"','\\"')+'"'
    arguments=["-proc:none","-encoding","UTF-8","-cp",quote(os.pathsep.join(str(p) for p in classpath)),
               "-d",quote(classes),*[quote(p) for p in sources]]
    argfile=output/"javac.args"
    argfile.write_text("\n".join(arguments)+"\n",encoding="utf-8")
    result=subprocess.run([str(args.javac),"-J-Xmx512m","@"+str(argfile)],capture_output=True,text=True,encoding="utf-8",errors="replace")
    (output/"compile.log").write_text(result.stdout+result.stderr,encoding="utf-8")
    if result.returncode:
        print(result.stdout+result.stderr)
        raise RuntimeError("javac failed; see isolated compile.log")
    dist=output/"dist"
    dist.mkdir(exist_ok=True)
    package_roots={"copimine-admin-plugin":("me/copimine/ultimateplus/","CopiMineUltimateAdminPlus.jar"),
                   "copimine-artifacts":("me/copimine/artifacts/","CopiMineArtifacts.jar"),
                   "copimine-economy-core":("me/copimine/economycore/","CopiMineEconomyCore.jar")}
    jars=[]
    for module,(prefix,name) in package_roots.items():
        base=staged/module
        if not (base/"plugin.yml").exists():
            continue
        jar=dist/name
        with zipfile.ZipFile(jar,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for path in sorted((classes/prefix).rglob("*.class")):
                archive.write(path,path.relative_to(classes).as_posix())
            for resource in ("plugin.yml","config.yml","messages_ru.yml"):
                if (base/resource).exists():
                    archive.write(base/resource,resource)
            if module=="copimine-admin-plugin":
                archive.write(ROOT/"actions.json","site-bridge-actions.json")
        jars.append({"name":name,"bytes":jar.stat().st_size,"sha256":sha(jar.read_bytes())})
    safe_warnings=result.stderr.replace(str(output),"build").replace(str(output).replace("\\","/"),"build")
    report.update({"compiled":True,"javaVersion":subprocess.check_output([str(args.javac),"-version"],text=True).strip(),
                   "dependencies":[{"name":p.name,"sha256":sha(p.read_bytes())} for p in classpath],
                   "jars":jars,"warnings":safe_warnings.splitlines()})
    (dist/"build-verification.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"compiled":True,"deployed":False,"sourceFiles":len(records),"jars":jars}))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--git-repo",type=Path)
    group.add_argument("--source-root",type=Path)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--javac",type=Path)
    parser.add_argument("--classpath",type=Path)
    parser.add_argument("--prepare-only",action="store_true")
    build(parser.parse_args())

if __name__=="__main__":
    main()

