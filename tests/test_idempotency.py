from concurrent.futures import ThreadPoolExecutor
from fastapi import HTTPException
from test_auth import client


def test_simultaneous_request_key_is_reserved_before_external_side_effect(client):
    from backend.domain import receipt_lookup,save_receipt
    db=client.app.state.database;actor={'id':'transaction-actor','name':'Actor'}
    def reserve(_):
        try:return receipt_lookup(db,actor,'one-external-operation','transfer',{'amount':10})
        except HTTPException as e:return e.status_code
    with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(reserve,range(2)))
    assert results.count(409)==1
    reservation=next(x for x in results if isinstance(x,tuple))
    save_receipt(db,actor,'one-external-operation','transfer',reservation[0],{'executed':True})
    assert reserve(0)[1]=={'executed':True}
