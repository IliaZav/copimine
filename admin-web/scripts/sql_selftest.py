#!/usr/bin/env python3
"""SQLite smoke-test for CopiMine Admin SQL patterns.
Does not touch the real Minecraft DB. Creates temporary DBs only.
"""
from __future__ import annotations
import csv
import io
import os
import sqlite3
import tempfile


def qi(name: str) -> str:
    return '"' + name.replace('"', '""') + '"'


def tables(conn):
    return [r[0] for r in conn.execute("select name from sqlite_master where type='table' order by name")]


def cols(conn, table):
    return [r[1] for r in conn.execute(f"PRAGMA table_info({qi(table)})")]


def rows(conn, sql, params=()):
    conn.row_factory = sqlite3.Row
    return [dict(r) for r in conn.execute(sql, params).fetchall()]


def test_generic_crud(tmp: str):
    path = os.path.join(tmp, 'admin.db')
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute('create table election_candidates (id integer primary key autoincrement, player text not null, status text not null default "pending", enabled integer not null default 1, votes integer not null default 0)')
    conn.execute('create table votes (id integer primary key autoincrement, election_id integer, voter_uuid text, candidate text, created_at integer)')
    conn.executemany('insert into votes(election_id,voter_uuid,candidate,created_at) values(?,?,?,?)', [(1,'u1','A',100),(1,'u1','B',101),(1,'u2','A',102)])
    conn.commit()

    assert 'election_candidates' in tables(conn)
    assert {'id','player','status','enabled','votes'} <= set(cols(conn, 'election_candidates'))
    conn.execute(f"insert into {qi('election_candidates')} ({qi('player')},{qi('status')},{qi('enabled')},{qi('votes')}) values(?,?,?,?)", ['SudoKillDash9','approved',1,0])
    conn.commit()
    found = rows(conn, f"select * from {qi('election_candidates')} where cast({qi('player')} as text) like ?", ['%Sudo%'])
    assert len(found) == 1
    conn.execute(f"update {qi('election_candidates')} set {qi('votes')}=? where {qi('id')}=?", [5, found[0]['id']])
    conn.commit()
    assert rows(conn, f"select votes from {qi('election_candidates')} where id=?", [found[0]['id']])[0]['votes'] == 5
    dupes = rows(conn, f"select {qi('voter_uuid')}, {qi('election_id')}, count(*) as duplicates from {qi('votes')} group by {qi('voter_uuid')}, {qi('election_id')} having count(*) > 1 limit 100")
    assert dupes and dupes[0]['duplicates'] == 2
    conn.execute(f"delete from {qi('election_candidates')} where {qi('id')}=?", [found[0]['id']])
    conn.commit()
    assert rows(conn, f"select count(*) as c from {qi('election_candidates')}")[0]['c'] == 0
    conn.close()


def test_coreprotect_like(tmp: str):
    path = os.path.join(tmp, 'coreprotect.db')
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute('create table co_user (id integer primary key, user text)')
    conn.execute('create table co_world (id integer primary key, world text)')
    conn.execute('create table co_material_map (id integer primary key, material text)')
    conn.execute('create table co_block (time integer, user integer, wid integer, x integer, y integer, z integer, type integer, action integer, rolled_back integer)')
    conn.execute('insert into co_user values(1,"SudoKillDash9")')
    conn.execute('insert into co_world values(0,"world")')
    conn.execute('insert into co_material_map values(10,"DIAMOND_BLOCK")')
    conn.execute('insert into co_block values(100,1,0,10,64,10,10,1,0)')
    conn.commit()
    uid = rows(conn, f"select id as uid from co_user where lower({qi('user')}) = lower(?) limit 1", ['SudoKillDash9'])[0]['uid']
    result = rows(conn, f"select * from {qi('co_block')} where x between ? and ? and z between ? and ? and user = ? order by time desc limit ?", [5,15,5,15,uid,50])
    assert len(result) == 1 and result[0]['type'] == 10
    buf = io.StringIO()
    writer = csv.DictWriter(buf, fieldnames=sorted(result[0].keys()))
    writer.writeheader(); writer.writerow(result[0])
    assert 'DIAMOND' not in buf.getvalue()  # material mapping is done by backend after query
    conn.close()


def main():
    with tempfile.TemporaryDirectory() as tmp:
        test_generic_crud(tmp)
        test_coreprotect_like(tmp)
    print('SQL selftest OK')


if __name__ == '__main__':
    main()
