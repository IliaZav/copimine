#!/usr/bin/env python3
"""Create private initial bridge configuration. No network, RCON or game commands."""
import argparse
import json
import os
from pathlib import Path
import secrets
from urllib.parse import urlsplit

ROOT=Path(__file__).resolve().parent
LOCAL_ACTIONS={'money.adjust','purchase.limit','sessions.revoke'}
INTERNAL_ACTIONS={'inventory.refresh','purchase.deliver','sanction.revoke','effect.revert'}

def checked_url(value,adapter=False):
    parsed=urlsplit(value)
    if not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError('Use a fixed service origin without credentials, query or fragment')
    if parsed.scheme!='https' and not (parsed.scheme=='http' and parsed.hostname in ('127.0.0.1','::1','localhost')):
        raise ValueError('Non-loopback service URLs require HTTPS')
    if parsed.path not in (('/operations',) if adapter else ('','/')):
        raise ValueError('Use /operations for the adapter and the root origin for the site')
    return value.rstrip('/')

def private_write(path,value):
    raw=value if isinstance(value,str) else json.dumps(value,ensure_ascii=False,indent=2)+'\n'
    descriptor=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(descriptor,'w',encoding='utf-8',newline='\n') as stream:
        stream.write(raw);stream.flush();os.fsync(stream.fileno())

def generate(output,spool,site_base,rcon_password,adapter_url='http://127.0.0.1:8791/operations',rcon_port=25575):
    output=Path(output).resolve();spool=Path(spool).resolve();rcon_password=Path(rcon_password).resolve()
    site=ROOT.parents[1]
    if output==site or output.is_relative_to(site):
        raise ValueError('Private bridge configuration must be outside the site directory')
    site_base=checked_url(site_base);adapter_url=checked_url(adapter_url,adapter=True)
    if not rcon_password.is_file():raise ValueError('Provide a private file containing the existing RCON password')
    if isinstance(rcon_port,bool) or not isinstance(rcon_port,int) or not 1<=rcon_port<=65535:
        raise ValueError('Invalid RCON port')
    output.mkdir(parents=True,mode=0o700,exist_ok=False)
    (output/'state').mkdir(mode=0o700)
    site_key,spool_key=secrets.token_hex(32),secrets.token_hex(32)
    private_write(output/'site-signing.key',site_key+'\n')
    private_write(output/'spool-signing.key',spool_key+'\n')
    rows=json.loads((ROOT/'actions.json').read_text(encoding='utf-8'))['actions']
    capabilities=sorted(({row['id'] for row in rows}-LOCAL_ACTIONS)|INTERNAL_ACTIONS)
    adapter={'listenHost':'127.0.0.1','listenPort':8791,'siteBaseURL':site_base,
             'spoolPath':str(spool),'statePath':str(output/'state'),
             'siteSecretFile':str(output/'site-signing.key'),'spoolSecretFile':str(output/'spool-signing.key'),
             'rconHost':'127.0.0.1','rconPort':rcon_port,'rconPasswordFile':str(rcon_password)}
    private_write(output/'adapter.json',adapter)
    private_write(output/'site-adapter.json',{'adapterUrl':adapter_url,'adapterSecret':site_key,'adapterCapabilities':capabilities})
    plugin='site-bridge:\n  enabled: true\n  secret-file: '+json.dumps(str(output/'spool-signing.key'))+'\n  profile-interval-seconds: 30\n  snapshot-retention-count: 256\n'
    private_write(output/'plugin-config-fragment.yml',plugin)
    return {'created':str(output),'adapterConfig':str(output/'adapter.json'),
            'siteConfig':str(output/'site-adapter.json'),'pluginConfigFragment':str(output/'plugin-config-fragment.yml'),
            'capabilityCount':len(capabilities),'gameConnectionsMade':False}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--spool',type=Path,required=True)
    parser.add_argument('--site-url',required=True)
    parser.add_argument('--rcon-password-file',type=Path,required=True)
    parser.add_argument('--adapter-url',default='http://127.0.0.1:8791/operations')
    parser.add_argument('--rcon-port',type=int,default=25575)
    args=parser.parse_args()
    print(json.dumps(generate(args.output,args.spool,args.site_url,args.rcon_password_file,args.adapter_url,args.rcon_port),ensure_ascii=False))

if __name__=='__main__':main()
