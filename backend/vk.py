"""Server-side VK ID authorization-code flow with PKCE; inactive without app ID.

Protocol reference: VKCOM/vkid-web-sdk/src/auth/auth.ts (official VK source).
Tokens are used only for one server-side identity lookup and are never retained.
"""
import base64
import hashlib
import hmac
import re
import secrets
import time
from urllib.parse import urlencode

import httpx
from fastapi import APIRouter,Request
from fastapi.responses import RedirectResponse
from .auth import create_user,issue_session
from .security import COOKIE,current_user,digest,fail,throttle

router=APIRouter()
PROVIDER='https://id.vk.ru'


def binding(request):
    return digest(request.cookies.get(COOKIE,'')) if request.state.user else digest(request.state.anonymous)


def authorization(request,link=False):
    settings=request.app.state.settings;database=request.app.state.database
    if not settings.vk_client_id:fail(503,'provider_not_configured','Вход через VK ещё не настроен. Используйте пароль.')
    if not re.fullmatch('[0-9]{1,20}',settings.vk_client_id):fail(503,'provider_not_configured','Проверьте настройки приложения VK ID.')
    if link:current_user(request,fresh=True)
    throttle(database,'vk:'+request.client.host,15,600)
    state=secrets.token_urlsafe(32);verifier=secrets.token_urlsafe(48)
    challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    with database.transaction() as db:
        db.execute('INSERT INTO cm_site_oauth(state_hash,verifier,nonce,session_hash,user_id,created_s) VALUES(%s,%s,%s,%s,%s,%s)',(digest(state),verifier,secrets.token_urlsafe(24),binding(request),request.state.user['id'] if link else None,int(time.time())))
    return PROVIDER+'/authorize?'+urlencode(dict(response_type='code',client_id=settings.vk_client_id,redirect_uri=settings.public_url+'/api/v6/auth/vk/callback',state=state,code_challenge=challenge,code_challenge_method='S256',scope=''))


@router.get('/api/v6/auth/vk/start')
def start(request:Request):
    return RedirectResponse(authorization(request),status_code=302)


@router.post('/api/v6/account/vk/start')
def link(request:Request):
    return {'authorizeUrl':authorization(request,link=True)}


def exchange_identity(settings,code,device_id,state,verifier):
    try:
        with httpx.Client(timeout=12,follow_redirects=False,trust_env=False) as client:
            response=client.post(PROVIDER+'/oauth2/auth',data=dict(grant_type='authorization_code',code=code,device_id=device_id,state=state,code_verifier=verifier,client_id=settings.vk_client_id,redirect_uri=settings.public_url+'/api/v6/auth/vk/callback'))
            response.raise_for_status();token=response.json()
            if token.get('error') or not hmac.compare_digest(str(token.get('state','')),state):raise ValueError('Invalid token state')
            access=token.get('access_token')
            if not isinstance(access,str) or not 10<=len(access)<=8192:raise ValueError('Invalid access token')
            response=client.post(PROVIDER+'/oauth2/user_info',data={'client_id':settings.vk_client_id,'access_token':access})
            response.raise_for_status();user=response.json().get('user',{})
            identifier=str(user.get('user_id',''))
            if not re.fullmatch('[0-9]{1,20}',identifier) or str(token.get('user_id',''))!=identifier:raise ValueError('Invalid identity')
            return {'id':identifier,'name':str(user.get('first_name','VK'))[:32]}
    except (httpx.HTTPError,ValueError,TypeError):
        fail(502,'vk_unavailable','VK ID не подтвердил вход. Попробуйте ещё раз или войдите по паролю.')


@router.get('/api/v6/auth/vk/callback')
def callback(request:Request):
    settings=request.app.state.settings;database=request.app.state.database
    if not settings.vk_client_id:fail(503,'provider_not_configured','VK ID ещё не настроен.')
    params=request.query_params;state=params.get('state','');code=params.get('code','');device=params.get('device_id','')
    if not 30<=len(state)<=200 or not 1<=len(code)<=8192 or not 1<=len(device)<=1024:fail(400,'oauth_state','Вход VK истёк или был отменён. Начните снова.')
    with database.transaction() as db:
        row=db.execute('SELECT * FROM cm_site_oauth WHERE state_hash=%s FOR UPDATE',(digest(state),)).fetchone()
        if not row or row['consumed_s'] or row['created_s']<int(time.time())-600 or not hmac.compare_digest(row['session_hash'],binding(request)):
            fail(400,'oauth_state','Сеанс VK не совпадает с браузером. Начните вход снова.')
        saved=dict(row)
        db.execute("UPDATE cm_site_oauth SET consumed_s=%s,verifier='' WHERE state_hash=%s",(int(time.time()),digest(state)))
    identity=exchange_identity(settings,code,device,state,saved['verifier'])
    with database.transaction() as db:
        existing=db.execute('SELECT * FROM cm_site_users WHERE vk_id=%s',(identity['id'],)).fetchone()
    if saved['user_id']:
        user=current_user(request,fresh=True)
        if user['id']!=saved['user_id'] or existing and existing['id']!=user['id']:fail(409,'identity_conflict','Этот VK уже связан с другим аккаунтом.')
        with database.transaction() as db:db.execute('UPDATE cm_site_users SET vk_id=%s,updated_s=%s WHERE id=%s',(identity['id'],int(time.time()),user['id']))
        database.audit(user,'vk.link',user['id'])
        return RedirectResponse('/#settings/connections',status_code=303)
    if existing:
        user=dict(existing)
        if not user['enabled']:fail(403,'account_blocked','Доступ к аккаунту закрыт.')
        if user['totp_secret']:fail(403,'mfa_required','Для аккаунта включена 2FA. Используйте вход с паролем и кодом приложения.')
    else:
        user=create_user(database,'VK_'+identity['id'],secrets.token_urlsafe(40))
        with database.transaction() as db:db.execute('UPDATE cm_site_users SET vk_id=%s WHERE id=%s',(identity['id'],user['id']))
    database.audit(user,'vk.login',user['id'])
    response=RedirectResponse('/#account',status_code=303)
    issue_session(request,response,user)
    return response
