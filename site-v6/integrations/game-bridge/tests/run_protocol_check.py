#!/usr/bin/env python3
"""Cross-language signed spool test; needs JDK, no Minecraft runtime/network."""
import argparse
import base64
import hashlib
import hmac
import json
from pathlib import Path
import subprocess

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--javac',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=True)
    fixture={'operationId':'8e1a597b-f861-4c73-b8d5-37c47ac60fe8','capturedAt':1792238400123,
             'text':'Проверка 🐉\nточной подписи','values':[True,False,None,2.5,{'x':-512}]}
    key=b'\x01'*32 # Public, deliberately fixed test vector; never an example deployment secret.
    raw=json.dumps(fixture,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()
    signed=json.dumps({'payload':base64.b64encode(raw).decode(),'signature':hmac.new(key,raw,hashlib.sha256).hexdigest()}).encode()
    incoming=output/'python-envelope.json';incoming.write_bytes(signed)
    outgoing=output/'java-envelope.json'
    classes=output/'classes';classes.mkdir(exist_ok=True)
    subprocess.run([str(args.javac),'-J-Xmx128m','-proc:none','-encoding','UTF-8','-d',str(classes),
        str(root/'java/me/copimine/ultimateplus/BridgeProtocol.java'),str(root/'tests/BridgeProtocolCheck.java')],check=True,capture_output=True)
    java=args.javac.with_name('java.exe' if args.javac.suffix=='.exe' else 'java')
    result=subprocess.run([str(java),'-Xmx128m','-cp',str(classes),'BridgeProtocolCheck',str(incoming),str(outgoing),hashlib.sha256(raw).hexdigest()],check=True,capture_output=True,text=True,encoding='utf-8')
    report=json.loads(result.stdout.strip())
    envelope=json.loads(outgoing.read_bytes());decoded=base64.b64decode(envelope['payload'],validate=True)
    assert hmac.compare_digest(envelope['signature'],hmac.new(key,decoded,hashlib.sha256).hexdigest())
    assert json.loads(decoded)==fixture
    report.update({'crossLanguageHmac':True,'unicodeRoundTrip':True,'pythonToJava':True,'javaToPython':True,
                   'javaVersion':subprocess.check_output([str(args.javac),'-version'],text=True).strip()})
    (output/'protocol-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report))

if __name__=='__main__':main()
