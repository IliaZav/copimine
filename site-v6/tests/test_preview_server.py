"""Local file adapter tests. Fixture jars are created in temporary directories only."""
import unittest, tempfile, zipfile, io, json, os, time, sys, struct, threading
from pathlib import Path
from functools import partial
from urllib.request import urlopen, Request
from urllib.error import HTTPError
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from serve import inspect_materials, PreviewHandler, ThreadingHTTPServer

def jar(id, version='26.3', depends=None, nested=None):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:
        data={'id':id,'version':'test','depends':{'minecraft':version, **(depends or {})}}
        if nested:
            data['jars']=[{'file':'META-INF/jars/fabric-api.jar'}]
            z.writestr('META-INF/jars/fabric-api.jar', nested)
        z.writestr('fabric.mod.json',json.dumps(data))
    return out.getvalue()

class AdapterTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
        (self.root/'mods').mkdir()
    def tearDown(self):self.tmp.cleanup()
    def pack(self,compatible=True):
        (self.root/'mods'/'CopiMineClient-test.jar').write_bytes(jar('copimineclient',depends={'fabric-api':'*'},nested=jar('fabric-api')))
        (self.root/'mods'/'voicechat-fabric-test.jar').write_bytes(jar('voicechat','26.3.x' if compatible else '1.21.1'))
    def test_missing_profile_unavailable(self):
        m,b=inspect_materials(None);self.assertFalse(m['modpack']['available']);self.assertEqual(b,{})
    def test_two_matching_personal_jars_nested_dependency(self):
        self.pack();m,b=inspect_materials(self.root)
        self.assertTrue(m['modpack']['available']);self.assertEqual(len(b['mods']),2)
    def test_different_minecraft_versions_rejected(self):
        self.pack(False);m,b=inspect_materials(self.root);self.assertFalse(m['modpack']['available'])
    def test_fresh_map_hidden_old_tile_frozen(self):
        day=self.root/'journeymap/data/mp/~Minecraft/overworld/day';day.mkdir(parents=True)
        file=day/'-1,-1.png';payload=b'\x89PNG\r\n\x1a\n'+b'\x00\x00\x00\rIHDR'+struct.pack('>II',512,512)+b'fixture'
        file.write_bytes(payload)
        m,b=inspect_materials(self.root);self.assertFalse(m['map']['available'])
        past=time.time()-13*3600;os.utime(file,(past,past));m,b=inspect_materials(self.root);self.assertTrue(m['map']['available'])
        file.write_bytes(b'changed');self.assertEqual(b['tile-0'],payload)
    def test_http_zip_and_cross_site_protection(self):
        self.pack();m,b=inspect_materials(self.root)
        class H(PreviewHandler):
            def log_message(self,*args):pass
        H.materials=m;H.material_blobs=b
        server=ThreadingHTTPServer(('127.0.0.1',0),partial(H,directory=str(self.root)))
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        base='http://127.0.0.1:'+str(server.server_port)
        try:
            with urlopen(base+'/api/local-materials') as r:self.assertTrue(json.load(r)['modpack']['available'])
            with urlopen(base+'/downloads/copimine-mods.zip') as r:
                self.assertIn('attachment',r.headers['Content-Disposition'])
                with zipfile.ZipFile(io.BytesIO(r.read())) as z:
                    self.assertEqual(len([n for n in z.namelist() if n.endswith('.jar')]),2)
                    self.assertIn('SHA256SUMS.txt',z.namelist());self.assertIsNone(z.testzip())
            with self.assertRaises(HTTPError) as e:urlopen(Request(base+'/api/local-materials',headers={'Sec-Fetch-Site':'cross-site'}))
            self.assertEqual(e.exception.code,403)
            with self.assertRaises(HTTPError):urlopen(base+'/local-map/../../mods/CopiMineClient-test.jar')
            (self.root/'server').mkdir()
            (self.root/'server'/'private.json').write_text('{"secret":"private"}')
            for method in ('GET','HEAD'):
                with self.assertRaises(HTTPError) as blocked:
                    urlopen(Request(base+'/server/private.json',method=method))
                self.assertEqual(blocked.exception.code,404)
            (self.root/'index.html').write_text('<h1>CopiMine v6</h1>')
            with urlopen(base+'/') as response:self.assertIn(b'CopiMine v6',response.read())
        finally:server.shutdown();server.server_close()
if __name__=='__main__':unittest.main()
