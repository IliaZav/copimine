from test_auth import client,login
import base64
import dataclasses
import io
from PIL import Image


def test_content_editor_persists_and_rejects_stale_writes(client):
    s=login(client);h={'Origin':'http://testserver','X-CSRF-Token':s['csrf']}
    editor=client.get('/api/v6/admin/content').json()
    version=editor['versions']['faq']
    data={'item':{'id':'real-question','question':'A saved question','answer':'A saved answer','published':True},'expectedVersion':version,'reason':'Content integration test'}
    saved=client.patch('/api/v6/admin/content/faq',headers=h,json=data)
    assert saved.status_code==200,saved.text
    assert client.patch('/api/v6/admin/content/faq',headers=h,json=data).status_code==409
    assert any(x['id']=='real-question' for x in client.get('/api/v6/admin/content').json()['data']['faq'])


def test_drafts_and_active_html_never_reach_public_state(client):
    s=login(client);h={'Origin':'http://testserver','X-CSRF-Token':s['csrf']}
    version=client.get('/api/v6/admin/content').json()['versions']['pages']
    data={'item':{'id':'private-draft','slug':'private-draft','published':False,'draft':{'title':'Hidden draft','body':'CONFIDENTIAL_DRAFT_BODY','description':'','nav':False}},'expectedVersion':version,'reason':'Check draft privacy'}
    assert client.patch('/api/v6/admin/content/pages',headers=h,json=data).status_code==200
    version=client.get('/api/v6/admin/content').json()['versions']['pages']
    data['expectedVersion']=version;data['item']['draft']['body']='<img src=x onerror=alert(1)>'
    assert client.patch('/api/v6/admin/content/pages',headers=h,json=data).status_code==400
    client.post('/api/v6/auth/logout',headers=h,json={})
    assert 'CONFIDENTIAL_DRAFT_BODY' not in client.get('/api/v6/state').text


def test_published_page_keeps_live_copy_when_a_new_draft_is_saved(client):
    s=login(client);h={'Origin':'http://testserver','X-CSRF-Token':s['csrf']}
    editor=client.get('/api/v6/admin/content').json()
    item={'id':'release-notes','slug':'release-notes','published':False,'draft':{'title':'Заметки','body':'Первая версия','description':'','nav':False}}
    saved=client.patch('/api/v6/admin/content/pages',headers=h,json={'item':item,'expectedVersion':editor['versions']['pages'],'reason':'Publish flow'}).json()
    published=client.patch('/api/v6/admin/content/pages',headers=h,json={'item':{**saved['item'],'publish':True},'expectedVersion':saved['version'],'reason':'Publish flow'}).json()
    assert any(p['slug']=='release-notes' and p['published']['body']=='Первая версия' for p in client.get('/api/v6/state').json()['pages'])
    draft={**published['item'],'draft':{**published['item']['draft'],'body':'Новая версия в черновике'}}
    client.patch('/api/v6/admin/content/pages',headers=h,json={'item':draft,'expectedVersion':published['version'],'reason':'Save next draft'})
    page=next(p for p in client.get('/api/v6/state').json()['pages'] if p['slug']=='release-notes')
    assert page['published']['body']=='Первая версия'


def test_old_bulk_editor_cannot_overwrite_versioned_content(client):
    s=login(client);h={'Origin':'http://testserver','X-CSRF-Token':s['csrf']}
    assert client.post('/api/v6/admin/content',headers=h,json={'faq':[]}).status_code==409
    assert client.post('/api/v7/workspace',headers=h,json={'faq':[]}).status_code==409


def test_site_copy_is_versioned_and_only_public_fields_are_saved(client):
    s=login(client);h={'Origin':'http://testserver','X-CSRF-Token':s['csrf']}
    settings=client.get('/api/v6/admin/site-settings').json()
    body={'values':{'headline':'Собери свою историю','description':'Мир, в котором строят вместе.','serverIp':'copimine.ru','gameVersion':'26.3'},'expectedVersion':settings['version'],'reason':'Update home page'}
    assert client.patch('/api/v6/admin/site-settings',headers=h,json=body).status_code==200
    config=client.get('/api/v6/state').json()['config']
    assert config['headline']=='Собери свою историю'
    assert config['serverIp']=='copimine.ru'
    body['reason']='Stale copy update'
    assert client.patch('/api/v6/admin/site-settings',headers=h,json=body).status_code==409
    body['expectedVersion']=client.get('/api/v6/admin/site-settings').json()['version']
    body['values']['playersOnline']=9999
    assert client.patch('/api/v6/admin/site-settings',headers=h,json=body).status_code==400


def test_default_site_copy_uses_direct_copimine_text(client):
    config=client.get('/api/v6/state').json()['config']
    assert config['headline']=='CopiMine —\nMinecraft-сервер.'
    assert config['description']=='Строй и играй вместе с другими. Банк, выборы и магазин — на сайте.'


def test_gallery_upload_is_private_until_the_image_is_published(client,tmp_path):
    session=login(client);headers={'Origin':'http://testserver','X-CSRF-Token':session['csrf']}
    client.app.state.settings=dataclasses.replace(client.app.state.settings,private=tmp_path/'private')
    out=io.BytesIO()
    Image.new('RGB',(1,1),(20,80,42)).save(out,format='PNG')
    image=out.getvalue()
    editor=client.get('/api/v6/admin/content').json()
    uploaded=client.post('/api/v6/admin/content/gallery/image',headers=headers,json={
        'image':base64.b64encode(image).decode('ascii'),'title':'Дом у реки','caption':'Снимок игроков',
        'alt':'Деревянный дом у реки','position':1,'published':False,
        'expectedVersion':editor['versions']['gallery'],'reason':'Добавил фото в галерею',
    })
    assert uploaded.status_code==200,uploaded.text
    item=uploaded.json()['item'];public_url=item['src']
    assert client.get(public_url).status_code==404
    private=client.get('/api/v6/admin/content/gallery/image/'+item['id'])
    assert private.status_code==200 and private.headers['content-type']=='image/png'
    assert Image.open(io.BytesIO(private.content)).size==(1,1)
    published=client.patch('/api/v6/admin/content/gallery',headers=headers,json={
        'item':{**item,'published':True},'expectedVersion':uploaded.json()['version'],'reason':'Опубликовал фото',
    })
    assert published.status_code==200,published.text
    response=client.get(public_url)
    assert response.status_code==200 and response.content==private.content
    assert response.headers['cache-control'].startswith('public, max-age=60')


def test_gallery_upload_rejects_non_images_and_anonymous_writes(client):
    session=login(client);headers={'Origin':'http://testserver','X-CSRF-Token':session['csrf']}
    editor=client.get('/api/v6/admin/content').json()
    body={'image':base64.b64encode(b'<svg onload="alert(1)">').decode('ascii'),'title':'bad','caption':'','alt':'bad image','position':0,'published':False,'expectedVersion':editor['versions']['gallery'],'reason':'Reject non-image'}
    assert client.post('/api/v6/admin/content/gallery/image',headers=headers,json=body).status_code==400
    client.post('/api/v6/auth/logout',headers=headers,json={})
    anonymous_headers={'Origin':'http://testserver','X-CSRF-Token':client.get('/api/v6/session').json()['csrf']}
    assert client.post('/api/v6/admin/content/gallery/image',headers=anonymous_headers,json=body).status_code==401
