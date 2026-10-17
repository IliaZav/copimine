import base64
import hashlib
import hmac
import importlib.util
import json
import pathlib
import sys
import tempfile
import time
import threading
import http.server
import urllib.request
import urllib.error
import unittest
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('game_adapter', ROOT / 'adapter.py')


class BridgeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = None
        if (ROOT / 'adapter.py').exists():
            cls.module = importlib.util.module_from_spec(SPEC)
            sys.modules[SPEC.name] = cls.module
            SPEC.loader.exec_module(cls.module)

    def fixture(self):
        self.assertIsNotNone(self.module, 'Executable adapter has not been implemented')
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        root = pathlib.Path(folder.name)
        return root, self.module

    def test_real_receipt_required_and_duplicate_does_not_execute_twice(self):
        root, m = self.fixture()
        operation = {'operationId': str(uuid.uuid4()), 'action': 'heal',
                     'target': {'accountId': 'account-test', 'uuid': str(uuid.uuid4()), 'name': 'TestPlayer'},
                     'params': {}, 'actorId': 'test-admin', 'reason': 'integration test', 'createdAt': int(time.time()*1000)}
        calls = []
        secret = b'b'*32
        def dispatch(command):
            calls.append(command)
            return 'Command accepted'
        bridge = m.SpoolBridge(root, secret, dispatch)
        self.assertIsNone(bridge.submit(operation))
        self.assertIsNone(bridge.submit(operation))
        self.assertEqual(calls, ['cmultra sitebridge execute ' + operation['operationId']])
        result = {'operationId': operation['operationId'], 'ackId': str(uuid.uuid4()), 'status': 'executed',
                  'executedAt': int(time.time()*1000), 'result': {'message': 'Healed', 'adapterVersion': m.VERSION}, 'snapshots': []}
        result['requestSha256']=hashlib.sha256(m.json_bytes(operation)).hexdigest()
        (root/'receipts'/f"{operation['operationId']}.json").write_bytes(m.envelope(secret, result))
        self.assertEqual(bridge.submit(operation)['status'], 'executed')
        self.assertEqual(len(calls), 1)
        altered = dict(operation, action='kick')
        with self.assertRaises(m.ProtocolError):
            bridge.submit(altered)

    def test_forged_and_mismatched_receipts_rejected(self):
        root, m = self.fixture()
        body = {'operationId': str(uuid.uuid4()), 'status':'executed'}
        good = m.envelope(b'a'*32, body)
        self.assertEqual(m.open_envelope(b'a'*32, good), body)
        with self.assertRaises(m.ProtocolError):
            m.open_envelope(b'z'*32, good)
        parsed = json.loads(good)
        parsed['payload'] = base64.b64encode(b'{"status":"failed"}').decode()
        with self.assertRaises(m.ProtocolError):
            m.open_envelope(b'a'*32, json.dumps(parsed).encode())

    def test_http_signature_replay_and_tamper_fail_closed(self):
        root, m = self.fixture()
        guard = m.AuthStore(root/'adapter.sqlite3', b's'*32)
        body = b'{"action":"heal"}'
        headers = m.sign_headers(b's'*32, body)
        guard.verify(headers, body)
        with self.assertRaises(m.ProtocolError):
            guard.verify(headers, body)
        with self.assertRaises(m.ProtocolError):
            guard.verify(m.sign_headers(b's'*32, body), body+b' ')

    def test_unverified_target_and_path_injection_rejected_before_dispatch(self):
        root, m = self.fixture()
        calls=[]
        bridge=m.SpoolBridge(root,b's'*32,lambda command:calls.append(command))
        for op in ({'operationId':'../../server.properties','action':'heal','target':{}},
                   {'operationId':str(uuid.uuid4()),'action':'heal','target':{'name':'OnlyName'}},
                   {'operationId':str(uuid.uuid4()),'action':'arbitrary.command','target':{'uuid':str(uuid.uuid4()),'accountId':'a'}}):
            with self.assertRaises(m.ProtocolError):
                bridge.submit(op)
        self.assertEqual(calls,[])

    def test_receipt_failure_requires_atomic_non_delivery_marker(self):
        root,m=self.fixture()
        op={'operationId':str(uuid.uuid4()),'action':'purchase.deliver','target':{'uuid':str(uuid.uuid4()),'accountId':'account'},'params':{'itemId':'minecraft:stone','quantity':1},'createdAt':int(time.time()*1000)}
        bridge=m.SpoolBridge(root,b's'*32,lambda cmd:None)
        bridge.submit(op)
        result={'operationId':op['operationId'],'ackId':str(uuid.uuid4()),'status':'failed','executedAt':op['createdAt'],'result':{'message':'unknown'},'snapshots':[]}
        result['requestSha256']=hashlib.sha256(m.json_bytes(op)).hexdigest()
        path=root/'receipts'/f"{op['operationId']}.json"
        path.write_bytes(m.envelope(b's'*32,result))
        with self.assertRaises(m.ProtocolError):
            bridge.submit(op)
        result['atomicNoDelivery']=True
        path.write_bytes(m.envelope(b's'*32,result))
        self.assertEqual(bridge.submit(op)['status'],'failed')

    def test_receipt_must_bind_exact_request_digest(self):
        root,m=self.fixture()
        op={'operationId':str(uuid.uuid4()),'action':'heal','target':{'uuid':str(uuid.uuid4()),'accountId':'account'},'params':{},'createdAt':int(time.time()*1000)}
        bridge=m.SpoolBridge(root,b's'*32,lambda cmd:None);bridge.submit(op)
        receipt={'operationId':op['operationId'],'ackId':str(uuid.uuid4()),'status':'executed','executedAt':op['createdAt'],'result':{'message':'Healed'},'snapshots':[]}
        path=root/'receipts'/f"{op['operationId']}.json"
        path.write_bytes(m.envelope(b's'*32,receipt))
        with self.assertRaises(m.ProtocolError):bridge.submit(op)
        receipt['requestSha256']='0'*64;path.write_bytes(m.envelope(b's'*32,receipt))
        with self.assertRaises(m.ProtocolError):bridge.submit(op)

    def profile_app(self):
        root,m=self.fixture()
        app=m.Application.__new__(m.Application)
        app.spool_secret=b'p'*32
        app.spool=m.SpoolBridge(root,app.spool_secret,lambda command:self.fail('profile publishing must not dispatch game commands'))
        app.publish_lock=threading.RLock()
        calls=[]
        def post(path,value):
            calls.append((path,value))
            return {'snapshotId':value['snapshotId'],'accepted':True}
        app.post_site=post
        return root,m,app,calls

    def test_periodic_profile_requires_signature_and_durable_publish_marker(self):
        root,m,app,calls=self.profile_app()
        game_id=str(uuid.uuid4());snapshot_id=str(uuid.uuid4())
        snapshot={'playerId':'verified-account','gameUUID':game_id,'snapshotId':snapshot_id,
                  'capturedAt':int(time.time()*1000),'inventory':[],'ender':[],
                  'profile':{'online':True,'mapVisible':False,'world':'overworld','x':10,'z':20}}
        path=root/'profiles'/f'{game_id}.json';path.parent.mkdir(exist_ok=True)
        path.write_bytes(m.envelope(b'x'*32,snapshot))
        with self.assertRaises(m.ProtocolError):app.publish_profile(path)
        self.assertEqual(calls,[])
        path.write_bytes(m.envelope(app.spool_secret,snapshot))
        app.publish_profile(path);app.publish_profile(path)
        self.assertEqual(calls,[('/api/v6/adapter/snapshots',snapshot)])
        marker=m.strict_json((root/'published-profiles'/f'{game_id}.json').read_bytes())
        self.assertEqual(marker['snapshotId'],snapshot_id)
        # A fresh application/process uses the persistent marker, not an in-memory cache.
        app2=m.Application.__new__(m.Application);app2.spool=app.spool;app2.spool_secret=app.spool_secret
        app2.publish_lock=threading.RLock();app2.post_site=app.post_site
        app2.publish_profile(path);self.assertEqual(len(calls),1)

    def test_profile_publication_retries_failed_post_and_rejects_wrong_filename(self):
        root,m,app,calls=self.profile_app()
        game_id=str(uuid.uuid4())
        snapshot={'playerId':'verified-account','gameUUID':game_id,'snapshotId':str(uuid.uuid4()),
                  'capturedAt':int(time.time()*1000),'inventory':[],'ender':[],'profile':{'online':False}}
        path=root/'profiles'/f'{game_id}.json';path.parent.mkdir(exist_ok=True)
        path.write_bytes(m.envelope(app.spool_secret,snapshot))
        success=app.post_site
        def fail(path,value):raise OSError('simulated site outage')
        app.post_site=fail
        with self.assertRaises(OSError):app.publish_profile(path)
        self.assertFalse((root/'published-profiles'/f'{game_id}.json').exists())
        app.post_site=success;app.publish_profile(path);self.assertEqual(len(calls),1)
        wrong=path.with_name(str(uuid.uuid4())+'.json');wrong.write_bytes(path.read_bytes())
        with self.assertRaises(m.ProtocolError):app.publish_profile(wrong)
        self.assertEqual(len(calls),1)

    def test_http_signed_ack_waits_for_actual_snapshot_ingestion(self):
        root,m,app,calls=self.profile_app()
        app.site_secret=b's'*64
        app.auth=m.AuthStore(root/'auth.sqlite3',app.site_secret)
        op={'operationId':str(uuid.uuid4()),'action':'inventory.refresh',
            'target':{'uuid':str(uuid.uuid4()),'accountId':'verified-account'},'params':{},'createdAt':int(time.time()*1000)}
        snapshot={'playerId':op['target']['accountId'],'gameUUID':op['target']['uuid'],'snapshotId':str(uuid.uuid4()),
                  'capturedAt':int(time.time()*1000),'inventory':[],'ender':[],'profile':{'online':True}}
        receipt={'operationId':op['operationId'],'ackId':str(uuid.uuid4()),'requestSha256':hashlib.sha256(m.json_bytes(op)).hexdigest(),
                 'status':'executed','executedAt':op['createdAt'],'snapshots':[snapshot],
                 'result':{'message':'Actual snapshot','snapshotId':snapshot['snapshotId'],'adapterVersion':m.VERSION}}
        (root/'incoming'/f"{op['operationId']}.json").write_bytes(m.envelope(app.spool_secret,op))
        (root/'receipts'/f"{op['operationId']}.json").write_bytes(m.envelope(app.spool_secret,receipt))
        server=http.server.ThreadingHTTPServer(('127.0.0.1',0),app.handler())
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        self.addCleanup(server.server_close);self.addCleanup(server.shutdown)
        body=m.json_bytes(op)
        def request():
            return urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:'+str(server.server_port)+'/operations',data=body,headers=m.sign_headers(app.site_secret,body)),timeout=3)
        success=app.post_site
        def fail(path,value):raise OSError('snapshot endpoint unavailable')
        app.post_site=fail
        with self.assertRaises(urllib.error.HTTPError) as failure:request()
        self.assertEqual(failure.exception.code,503)
        app.post_site=success
        with request() as response:
            raw=response.read();headers=response.headers
            self.assertEqual(response.status,200)
        self.assertEqual(calls,[('/api/v6/adapter/snapshots',snapshot)])
        ack=m.strict_json(raw);self.assertEqual(ack['ackId'],receipt['ackId'])
        self.assertEqual(ack['result']['snapshotId'],snapshot['snapshotId'])
        m.AuthStore(root/'response-auth.sqlite3',app.site_secret).verify(headers,raw)
        self.assertFalse((root/'published'/f"{op['operationId']}.json").exists())
        # Background delivery posts the same snapshot and ACK once; a retry is harmless.
        def both(path,value):
            calls.append((path,value));return {'accepted':True}
        app.post_site=both;app.publish(op,receipt);app.publish(op,receipt)
        self.assertEqual([p for p,_ in calls],['/api/v6/adapter/snapshots','/api/v6/adapter/snapshots','/api/v6/adapter/ack'])

    def test_signed_receipt_allows_bounded_clock_skew_but_not_wrong_request(self):
        root,m=self.fixture()
        op={'operationId':str(uuid.uuid4()),'action':'heal','target':{'uuid':str(uuid.uuid4()),'accountId':'account'},'params':{},'createdAt':int(time.time()*1000)}
        bridge=m.SpoolBridge(root,b's'*32,lambda cmd:None);bridge.submit(op)
        receipt={'operationId':op['operationId'],'ackId':str(uuid.uuid4()),'status':'executed','executedAt':op['createdAt']-25000,
                 'requestSha256':hashlib.sha256(m.json_bytes(op)).hexdigest(),'result':{'message':'Healed'},'snapshots':[]}
        path=root/'receipts'/f"{op['operationId']}.json";path.write_bytes(m.envelope(b's'*32,receipt))
        self.assertEqual(bridge.submit(op)['executedAt'],op['createdAt']-25000)
        for stamp in (op['createdAt']-60001,int(time.time()*1000)+61000,True):
            receipt['executedAt']=stamp;path.write_bytes(m.envelope(b's'*32,receipt))
            with self.assertRaises(m.ProtocolError):bridge.submit(op)

    def test_configuration_generator_keeps_independent_keys_outside_site(self):
        root,m=self.fixture()
        spec=importlib.util.spec_from_file_location('configure_bridge',ROOT/'configure.py')
        configure=importlib.util.module_from_spec(spec);spec.loader.exec_module(configure)
        password=root/'rcon-password.txt';password.write_text('public-test-password')
        folder=root/'private-config'
        result=configure.generate(folder,root/'game-spool','http://127.0.0.1:8765',password)
        site=m.strict_json((folder/'site-adapter.json').read_bytes())
        adapter=m.strict_json((folder/'adapter.json').read_bytes())
        self.assertEqual(len(site['adapterCapabilities']),119)
        self.assertEqual(site['adapterSecret'],(folder/'site-signing.key').read_text().strip())
        self.assertNotEqual((folder/'site-signing.key').read_bytes(),(folder/'spool-signing.key').read_bytes())
        self.assertNotIn(site['adapterSecret'],json.dumps(result))
        self.assertEqual(pathlib.Path(adapter['rconPasswordFile']),password)
        before=(folder/'site-signing.key').read_bytes()
        with self.assertRaises(FileExistsError):configure.generate(folder,root/'game-spool','http://127.0.0.1:8765',password)
        self.assertEqual((folder/'site-signing.key').read_bytes(),before)
        with self.assertRaises(ValueError):configure.generate(ROOT/'unsafe-generated',root/'game-spool','http://127.0.0.1:8765',password)


if __name__ == '__main__':
    unittest.main()
