"""Single persistent systemd worker for map jobs and the transactional mail outbox."""
import json
import logging
import os
import shutil
import time

from fastapi import HTTPException
from .config import Settings
from .db import Database
from .game import GameAdapter
from .mail import pump_outbox
from .maps import MapJobs, MapStorage, PublicationError
from .ops import SAMPLE_SECONDS, sample, sample_interval

LOG = logging.getLogger('copimine.worker')


class MapWorker:
    def __init__(self, database, settings, game):
        self.database,self.settings,self.game=database,settings,game
        self.jobs=MapJobs(database,settings)
        self.storage=MapStorage(settings.map_root,settings.map_minimum_free_bytes)

    def rpc(self, operation, params=None):
        return self.game.rpc(operation,{'id':'map-worker','name':'Публикация карты'},params or {})['data']

    def tick(self, now=None):
        now=int(time.time()) if now is None else now
        self.jobs.schedule(now)
        with self.database.transaction() as db:
            row=db.execute("SELECT * FROM cm_site_map_jobs WHERE status IN ('queued','waiting_for_resources','running','validating','publishing') ORDER BY created_s LIMIT 1").fetchone()
        if not row:return
        job=dict(row);result=json.loads(job['result'])
        actor={'id':job['actor_id'],'name':job['actor_name']}
        try:
            # A crash after atomic promotion is completed from the same immutable manifest.
            published=self.storage.current()
            if published.get('version')==job['id']:
                self.complete(job,published);return
            health=self.rpc('map.health')
            if not health.get('available'):
                raise PublicationError('bluemap_unavailable')
            game=self.game.health(detailed=True)
            count=game.get('players',0)
            if isinstance(count,list):count=len(count)
            loaded=os.getloadavg()[0] if hasattr(os,'getloadavg') else 0
            enough_disk=self.settings.map_root.exists() and shutil.disk_usage(self.settings.map_root).free>=self.settings.map_minimum_free_bytes
            if count>=1 or loaded>max(2,(os.cpu_count() or 1)*0.65) or not enough_disk:
                if health.get('rendering'):self.rpc('map.control',{'mode':'stop'})
                if now-job['created_s']>1800:raise PublicationError('resource_wait_timeout')
                self.jobs.phase(job,'waiting_for_resources','Сервер занят игроками или не хватает свободных ресурсов.',result);return
            if job['status'] in ('queued','waiting_for_resources'):
                self.rpc('map.control',{'mode':'start'})
                if not result.get('started'):
                    self.rpc('map.update')
                    result.update(started=True,startedAt=now)
                self.jobs.phase(job,'running',result=result);return
            if now-result.get('startedAt',now)>7200:
                raise PublicationError('render_timeout')
            if job['status']=='running':
                if health.get('busy') or health.get('queued',0)>0:
                    result.pop('idleAt',None)
                    result['progress']=health.get('progress')
                    self.jobs.phase(job,'running',result=result);return
                if not result.get('idleAt'):
                    result['idleAt']=now;self.jobs.phase(job,'running',result=result);return
                if now-result['idleAt']<5:return
                self.rpc('map.control',{'mode':'stop'})
                self.jobs.phase(job,'validating',result=result);return
            if health.get('rendering') or health.get('flushing'):return
            if health.get('flushError'):raise PublicationError('renderer_flush_failed')
            if not result.get('flushRequestedAt'):
                result['flushRequestedAt']=int(time.time()*1000)
                self.rpc('map.flush');self.jobs.phase(job,'validating',result=result);return
            if health.get('flushedAt',0)<result['flushRequestedAt']:
                # A Minecraft restart resets the flush proof. Request another save.
                if now-job['updated_s']>30:
                    result.pop('flushRequestedAt',None);self.jobs.phase(job,'validating',result=result)
                return
            delay=self.jobs.config()['minAgeMinutes']*60
            if now-result['idleAt']<delay:return
            publication=self.storage.publish(self.game.server/'bluemap/web',job['id'],lambda phase:self.jobs.phase(job,phase,result=result))
            self.complete(job,publication)
            self.storage.cleanup()
        except (HTTPException,PublicationError,OSError,ValueError) as error:
            code=error.detail.get('code','game_unavailable') if isinstance(error,HTTPException) and isinstance(error.detail,dict) else str(error) if isinstance(error,PublicationError) else 'publication_failed'
            self.jobs.phase(job,'failed',code)
            self.database.audit(actor,'map.failed',job['id'],job['reason'],{'jobId':job['id'],'error':code})
            LOG.warning('Map publication failed job=%s code=%s',job['id'],code)
            try:self.rpc('map.control',{'mode':'stop'})
            except Exception:pass

    def complete(self,job,publication):
        self.jobs.phase(job,'completed',result=publication)
        self.database.audit({'id':job['actor_id'],'name':job['actor_name']},'map.published',job['id'],job['reason'],publication)


def main():
    import fcntl
    settings=Settings.from_env()
    settings.map_root.mkdir(parents=True,exist_ok=True)
    lock=(settings.map_root/'worker.lock').open('a')
    fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    database=Database(settings.database_url);database.initialize()
    worker=MapWorker(database,settings,GameAdapter(settings,database))
    logging.basicConfig(level=logging.INFO)
    last_sample=0
    interval=SAMPLE_SECONDS
    while True:
        try:
            worker.tick()
            if settings.smtp_host and settings.mail_domain_ready:pump_outbox(database,settings)
            if time.time()-last_sample>=interval:
                try:
                    sample(database,settings,worker.game)
                except Exception as error:
                    LOG.warning('Metrics sample failed type=%s',type(error).__name__)
                last_sample=time.time()
                interval=sample_interval(database)
        except Exception as error:
            LOG.error('Worker iteration failed type=%s',type(error).__name__)
        time.sleep(1)


if __name__=='__main__':main()
