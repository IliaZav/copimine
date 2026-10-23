from dataclasses import replace
import time
from fastapi import HTTPException
from test_auth import client
from test_maps import source_tree


class Renderer:
    def __init__(self,server):
        self.server=server;self.flushed=0;self.calls=[];self.offline=False
    def health(self,detailed=False):return {'players':0,'ready':True}
    def rpc(self,op,actor,params=None):
        self.calls.append((op,params))
        if self.offline:raise HTTPException(503,detail={'code':'game_unavailable'})
        if op=='map.flush':self.flushed=int(time.time()*1000)
        if op=='map.health':return {'data':{'available':True,'rendering':False,'flushing':False,'flushedAt':self.flushed,'flushError':'','busy':False,'queued':0}}
        return {'data':{'accepted':True}}


def test_worker_finishes_real_snapshot_and_restart_recovers_promoted_job(client,tmp_path):
    from backend.maps import MapJobs,MapStorage
    from backend.worker import MapWorker
    settings=replace(client.app.state.settings,map_root=tmp_path/'published',map_minimum_free_bytes=0)
    settings.map_root.mkdir();server=tmp_path/'server';(server/'bluemap').mkdir(parents=True)
    source_tree(server/'bluemap/web')
    game=Renderer(server);db=client.app.state.database;jobs=MapJobs(db,settings)
    job,_=jobs.enqueue({'id':'test','name':'Test'},'real-publication','Runtime snapshot test')
    worker=MapWorker(db,settings,game);now=int(time.time())
    for offset in (0,5,11,16,21,26):worker.tick(now+offset)
    assert jobs.status()['job']['status']=='completed'
    assert MapStorage(settings.map_root).current()['version']==job['id']
    # Simulate the process dying after promotion, before its SQL completion receipt.
    jobs.phase(job,'publishing')
    MapWorker(db,settings,game).tick(now+30)
    assert jobs.status()['job']['status']=='completed'
    assert not any('force' in op for op,params in game.calls)


def test_server_outage_fails_job_and_preserves_last_good_map(client,tmp_path):
    from backend.maps import MapJobs,MapStorage
    from backend.worker import MapWorker
    settings=replace(client.app.state.settings,map_root=tmp_path/'published',map_minimum_free_bytes=0)
    source=source_tree(tmp_path/'source');store=MapStorage(settings.map_root,minimum_free=0)
    store.publish(source,'a'*32)
    jobs=MapJobs(client.app.state.database,settings)
    jobs.enqueue({'id':'test','name':'Test'},'offline-publication','Offline server integration test')
    renderer=Renderer(tmp_path);renderer.offline=True
    MapWorker(client.app.state.database,settings,renderer).tick()
    assert jobs.status()['job']['status']=='failed'
    assert store.current()['version']=='a'*32
