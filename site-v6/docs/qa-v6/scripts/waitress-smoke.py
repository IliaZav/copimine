import http.cookies, json, os, pathlib, socket, subprocess, tempfile, time, urllib.request, urllib.error
site=pathlib.Path(__file__).resolve().parents[3]
checks=[]
with tempfile.TemporaryDirectory(prefix='copimine-waitress-') as private:
 with socket.socket() as s:
  s.bind(('127.0.0.1',0)); port=s.getsockname()[1]
 env=dict(os.environ)
 with open(pathlib.Path(private)/'http.log','w+') as log:
  process=subprocess.Popen(['python','server.py','--data-dir',str(pathlib.Path(private)/'state'),'--origin','https://copimine.test','--trust-loopback-proxy','serve','--host','127.0.0.1','--port',str(port),'--waitress'],cwd=site,env=env,stdout=log,stderr=log)
  def request(path,headers=None,body=None,secure=True):
   h={'X-CM-Client-IP':'198.51.100.43'}
   if secure:h['X-Forwarded-Proto']='https'
   h.update(headers or {})
   raw=None if body is None else json.dumps(body).encode()
   if raw is not None:h['Content-Type']='application/json'
   req=urllib.request.Request('http://127.0.0.1:'+str(port)+path,data=raw,headers=h)
   try:
    r=urllib.request.urlopen(req,timeout=4)
   except urllib.error.HTTPError as error:r=error
   with r:return r.status,dict(r.headers),r.read()
  def passed(name,value):
   checks.append({'name':name,'pass':bool(value)})
   assert value,name
  try:
   for attempt in range(60):
    try:
     status,headers,raw=request('/api/v6/session');break
    except (OSError,urllib.error.URLError):
     if process.poll() is not None:raise RuntimeError('Waitress exited before listening')
     time.sleep(.1)
   else:raise RuntimeError('Waitress did not start')
   bundle=json.loads(raw); cookie=http.cookies.SimpleCookie(headers['Set-Cookie'])
   passed('Production Waitress starts and issues a protected anonymous session',status==200 and all(x in headers['Set-Cookie'] for x in ('HttpOnly','Secure','SameSite=Lax')))
   passed('HTTPS is mandatory without trusted TLS forwarding',request('/api/v6/session',secure=False)[0]==400)
   passed('Private seed catalogue is not served',request('/server/private/catalog.json')[0]==404)
   st,_,body=request('/assets/js/auth-v6.js');passed('New authentication module is in the production static allowlist',st==200 and b'v6-contact-verify' in body)
   passed('Anonymous admin access is denied',request('/api/v6/admin/actions')[0]==401)
   st,_,body=request('/api/v6/state');state=json.loads(body);passed('Production public catalogue has 31 items and no edit reasons',st==200 and len(state['catalog'])==31 and all('reason' not in x and 'changeReason' not in x for x in state['catalog']))
   h={'Cookie':'cm_v6_session='+cookie['cm_v6_session'].value,'X-CSRF-Token':bundle['csrf'],'Origin':'https://copimine.test'}
   passed('Unconfigured VK returns an explicit error without provider access',request('/api/v6/auth/vk/start',h,{'mode':'login'})[0]==503)
   passed('Invalid client address from the configured proxy is rejected',request('/api/v6/session',{'X-CM-Client-IP':'198.51.100.43, 127.0.0.1'})[0]==400)
  finally:
   process.terminate()
   try:process.wait(timeout=8)
   except subprocess.TimeoutExpired:process.kill();process.wait(timeout=8)
report={'server':'Waitress 3.0.2','mode':'loopback with a simulated trusted TLS proxy','checks':checks,'externalProvidersContacted':False}
path=site/'docs/qa-v6/reports/waitress-smoke.json';path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'checks':len(checks),'passed':sum(x['pass'] for x in checks),'report':str(path)}))
