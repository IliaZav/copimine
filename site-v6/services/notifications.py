"""Opt-in provider adapters; no public HTTP endpoint and no background sender.
Default: save a LOCAL preview. A production queue must authenticate the actor,
verify contact ownership, issue/hash/expire codes, rate-limit and audit separately.
Never call this directly from public user input or put secrets in the frontend.

Provider: https://sms.ru/docs/api/api_group_sms/send (POST, per-number status).
SMTP accepts a message, not proof of delivery. Network ambiguity is NOT retried.
"""
from __future__ import annotations
from email.message import EmailMessage
from email.utils import parseaddr
from pathlib import Path
from typing import Mapping, Any
import argparse,json,os,re,secrets,smtplib,ssl,urllib.parse,urllib.request

class DeliveryError(RuntimeError):pass

def parse_sms_reply(reply: dict[str, Any], number: str) -> dict[str,str]:
    entry=reply.get('sms',{}).get(number,{})
    if reply.get('status')!='OK' or entry.get('status')!='OK' or not entry.get('sms_id'):
        # Do not echo raw provider bodies: some providers include request secrets.
        raise DeliveryError('SMS provider rejected the recipient or response is incomplete')
    return {'status':'accepted','provider_id':str(entry['sms_id'])}

class NotificationGateway:
    def __init__(self,env:Mapping[str,str]|None=None,outbox:Path|None=None):
        self.env=dict(os.environ if env is None else env)
        self.outbox=outbox or Path(__file__).resolve().parent/'local-outbox'
    def send_code(self,channel:str,target:str,code:str,*,verified:bool,purpose:str='recovery',contact_challenge:bool=False)->dict[str,str]:
        if purpose not in {'login','recovery','verify_contact'}:raise DeliveryError('Unknown code purpose')
        # Only the trusted, authenticated contact-verification queue can use an
        # unverified destination. Login and recovery still require ownership.
        if not verified and not (purpose=='verify_contact' and contact_challenge is True):
            raise DeliveryError('A verified account contact is required')
        if not re.fullmatch(r'[0-9]{6}',code):raise DeliveryError('Six digit one-time code required')
        if any(c in target for c in '\r\n') or len(target)>254:raise DeliveryError('Invalid recipient')
        if channel=='email':
            if parseaddr(target)[1]!=target or not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+',target):raise DeliveryError('Invalid email')
        elif channel=='sms':
            if not re.fullmatch(r'\+[1-9][0-9]{8,14}',target):raise DeliveryError('E.164 phone required')
        else:raise DeliveryError('Unknown channel')
        mode=self.env.get('CM_NOTIFY_MODE','preview')
        if mode not in {'preview','live'}:raise DeliveryError('Invalid delivery mode')
        purpose_text={'login':'sign-in','recovery':'password recovery','verify_contact':'contact verification'}[purpose]
        text=f'CopiMine: {code}. Your {purpose_text} code expires in 10 minutes. Do not share it.'
        message=EmailMessage();message['To']=target;message['Subject']='CopiMine: '+purpose_text+' code'
        message['From']=self.env.get('CM_SMTP_FROM','no-reply@copimine.example');message.set_content(text)
        ref=secrets.token_hex(12)
        if mode=='preview':
            self.outbox.mkdir(parents=True,exist_ok=True,mode=0o700)
            path=self.outbox/(ref+('.eml' if channel=='email' else '.txt'))
            # File contains the test code for explicit local inspection, never in a web folder.
            path.write_bytes(message.as_bytes() if channel=='email' else text.encode())
            try:path.chmod(0o600)
            except OSError:pass
            return {'status':'local-preview','reference':ref,'channel':channel}
        if channel=='email':return self._smtp(message,ref)
        return self._sms(target,text)
    def _smtp(self,message:EmailMessage,ref:str)->dict[str,str]:
        e=self.env
        if not all(e.get(k) for k in ['CM_SMTP_HOST','CM_SMTP_USER','CM_SMTP_PASSWORD','CM_SMTP_FROM']):raise DeliveryError('SMTP is not configured')
        host=e['CM_SMTP_HOST']
        if not re.fullmatch(r'[A-Za-z0-9.-]+',host):raise DeliveryError('Invalid SMTP host')
        mode=e.get('CM_SMTP_TLS','starttls')
        if mode not in {'starttls','tls'}:raise DeliveryError('Encrypted SMTP is required')
        try:
            port=int(e.get('CM_SMTP_PORT','465' if mode=='tls' else '587'))
            context=ssl.create_default_context()
            with (smtplib.SMTP_SSL(host,port,timeout=15,context=context) if mode=='tls' else smtplib.SMTP(host,port,timeout=15)) as smtp:
                if mode=='starttls':smtp.ehlo();smtp.starttls(context=context);smtp.ehlo()
                smtp.login(e['CM_SMTP_USER'],e['CM_SMTP_PASSWORD'])
                rejected=smtp.send_message(message)
                if rejected:raise DeliveryError('SMTP recipient was rejected')
        except DeliveryError:raise
        except (OSError,smtplib.SMTPException,ValueError) as exc:
            raise DeliveryError('SMTP failed or delivery outcome unknown; reconcile before retry') from None
        return {'status':'accepted','reference':ref,'channel':'email'}
    def send_notice(self,target:str,subject:str,text:str,*,verified:bool)->dict[str,str]:
        if verified is not True:raise DeliveryError('A verified account email is required')
        if not isinstance(target,str) or len(target)>254 or any(c in target for c in '\r\n') or parseaddr(target)[1]!=target or not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+',target):
            raise DeliveryError('Invalid email')
        if not isinstance(subject,str) or not 1<=len(subject)<=160 or any(c in subject for c in '\r\n'):
            raise DeliveryError('Invalid notice subject')
        if not isinstance(text,str) or not 1<=len(text)<=8000 or '\x00' in text:
            raise DeliveryError('Invalid notice text')
        mode=self.env.get('CM_NOTIFY_MODE','preview')
        if mode not in {'preview','live'}:raise DeliveryError('Invalid delivery mode')
        message=EmailMessage();message['To']=target;message['Subject']=subject
        message['From']=self.env.get('CM_SMTP_FROM','no-reply@copimine.example');message.set_content(text)
        ref=secrets.token_hex(12)
        if mode=='preview':
            self.outbox.mkdir(parents=True,exist_ok=True,mode=0o700)
            path=self.outbox/(ref+'.eml');path.write_bytes(message.as_bytes())
            try:path.chmod(0o600)
            except OSError:pass
            return {'status':'local-preview','reference':ref,'channel':'email'}
        return self._smtp(message,ref)
    def send_notice(self,target:str,subject:str,text:str,*,verified:bool)->dict[str,str]:
        if verified is not True:raise DeliveryError('A verified account email is required')
        if not isinstance(target,str) or len(target)>254 or any(c in target for c in '\r\n') or parseaddr(target)[1]!=target or not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+',target):
            raise DeliveryError('Invalid email')
        if not isinstance(subject,str) or not 1<=len(subject)<=160 or any(c in subject for c in '\r\n'):
            raise DeliveryError('Invalid notice subject')
        if not isinstance(text,str) or not 1<=len(text)<=8000 or '\x00' in text:
            raise DeliveryError('Invalid notice text')
        mode=self.env.get('CM_NOTIFY_MODE','preview')
        if mode not in {'preview','live'}:raise DeliveryError('Invalid delivery mode')
        message=EmailMessage();message['To']=target;message['Subject']=subject
        message['From']=self.env.get('CM_SMTP_FROM','no-reply@copimine.example');message.set_content(text)
        ref=secrets.token_hex(12)
        if mode=='preview':
            self.outbox.mkdir(parents=True,exist_ok=True,mode=0o700)
            path=self.outbox/(ref+'.eml');path.write_bytes(message.as_bytes())
            try:path.chmod(0o600)
            except OSError:pass
            return {'status':'local-preview','reference':ref,'channel':'email'}
        return self._smtp(message,ref)
    def _sms(self,target:str,text:str)->dict[str,str]:
        key=self.env.get('CM_SMSRU_API_ID')
        if not key:raise DeliveryError('SMS provider is not configured')
        number=target.lstrip('+')
        fields={'api_id':key,'to':number,'msg':text,'json':'1'}
        if self.env.get('CM_SMSRU_SENDER'):fields['from']=self.env['CM_SMSRU_SENDER']
        request=urllib.request.Request('https://sms.ru/sms/send',data=urllib.parse.urlencode(fields).encode(),method='POST',headers={'Content-Type':'application/x-www-form-urlencoded'})
        # No redirects to untrusted destinations, and no credentials in query strings.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self,*args,**kwargs):return None
        opener=urllib.request.build_opener(NoRedirect(),urllib.request.HTTPSHandler(context=ssl.create_default_context()))
        try:
            with opener.open(request,timeout=15) as response:
                raw=response.read(65537)
                if len(raw)>65536:raise DeliveryError('SMS response too large')
                result=parse_sms_reply(json.loads(raw),number)
        except DeliveryError:raise
        except Exception:raise DeliveryError('SMS failed or outcome unknown; reconcile before retry') from None
        return {**result,'channel':'sms'}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description='Write a harmless local recovery preview; never sends externally.')
    parser.add_argument('--preview',action='store_true',required=True)
    args=parser.parse_args()
    # The CLI deliberately cannot activate real delivery; integration code calls the adapter.
    print(json.dumps(NotificationGateway({}).send_code('email','demo@copimine.example','123456',verified=True)))
