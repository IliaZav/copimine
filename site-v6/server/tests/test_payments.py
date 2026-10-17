"""Payment regressions use a fake provider transport; they never create real payments."""
from concurrent.futures import ThreadPoolExecutor
import copy
import unittest
from unittest.mock import patch
from server.common import APIError
from server.storage import now_ms
from server.tests import test_backend as fixture

class PaymentTests(unittest.TestCase):
    setUp=fixture.BackendTests.setUp
    tearDown=fixture.BackendTests.tearDown
    account=fixture.BackendTests.account
    http=fixture.BackendTests.http

    def configured(self,test=False):
        self.svc.config.payment_shop_id="12345"
        self.svc.config.payment_secret="fixture-secret-not-for-real-payments"
        self.svc.config.payment_test=test

    def provider(self,method,path="",payload=None,key=None):
        return {"id":"provider-"+key,"status":"pending","paid":False,
                "amount":copy.deepcopy(payload["amount"]),"metadata":copy.deepcopy(payload["metadata"]),
                "recipient":{"account_id":"12345"},"test":self.svc.config.payment_test,
                "confirmation":{"confirmation_url":"https://yoomoney.ru/checkout/test"}}

    def create(self,amount=100,key="payment-fixture-001"):
        with patch.object(self.svc,"_provider_request",side_effect=self.provider) as transport:
            result=self.svc.create_payment({"amount":amount,"method":"sbp","idempotencyKey":key},
                                           self.player["user"],self.player["session"])
            provider=self.provider("POST",payload=transport.call_args.kwargs["payload"],key=result["id"])
        return result,provider

    def balance(self):
        with self.svc.store.connect() as conn:
            row=conn.execute("SELECT ar,cm FROM users WHERE id=?",(self.player["user"]["id"],)).fetchone()
            count=conn.execute("SELECT COUNT(*) FROM ledger WHERE kind='donation'").fetchone()[0]
            return row["ar"],row["cm"],count

    def test_unconfigured_provider_rejects_without_creating_payment(self):
        with self.assertRaises(APIError) as error:
            self.svc.create_payment({"amount":100,"method":"sbp","idempotencyKey":"payment-fixture-001"},
                                    self.player["user"],self.player["session"])
        self.assertEqual(error.exception.code,"payments_unconfigured")
        with self.svc.store.connect() as conn:
            self.assertEqual(conn.execute("SELECT COUNT(*) FROM payments").fetchone()[0],0)

    def test_same_payment_key_reuses_creation_and_rejects_changed_amount(self):
        self.configured()
        with patch.object(self.svc,"_provider_request",side_effect=self.provider) as transport:
            body={"amount":100,"method":"card","idempotencyKey":"payment-fixture-001"}
            first=self.svc.create_payment(body,self.player["user"],self.player["session"])
            second=self.svc.create_payment(body,self.player["user"],self.player["session"])
            self.assertEqual(first["id"],second["id"])
            self.assertEqual(transport.call_count,1)
            with self.assertRaises(APIError):
                self.svc.create_payment(dict(body,amount=200),self.player["user"],self.player["session"])
        self.assertEqual(self.balance(),(1000,1000,0))

    def test_parallel_success_callbacks_credit_once_and_old_pending_cannot_revert(self):
        self.configured()
        payment,provider=self.create()
        provider.update(status="succeeded",paid=True)
        with ThreadPoolExecutor(max_workers=6) as pool:
            results=list(pool.map(lambda _:self.svc._apply_provider(payment["id"],provider),range(6)))
        self.assertTrue(all(row["status"]=="completed" for row in results))
        self.assertEqual(self.balance(),(1000,1100,1))
        self.svc._apply_provider(payment["id"],dict(provider,status="pending",paid=False))
        self.assertEqual(self.balance(),(1000,1100,1))

    def test_forged_success_webhook_is_only_a_hint(self):
        self.configured()
        payment,provider=self.create()
        body={"type":"notification","event":"payment.succeeded",
              "object":dict(provider,status="succeeded",paid=True)}
        with patch.object(self.svc,"_provider_request",return_value=provider) as transport:
            self.svc.payment_webhook(body,"127.0.0.1")
        transport.assert_called_once_with("GET",provider["id"])
        self.assertEqual(self.balance(),(1000,1000,0))

    def test_amount_merchant_metadata_and_test_mode_must_all_match(self):
        self.configured()
        payment,provider=self.create()
        provider.update(status="succeeded",paid=True)
        cases=[dict(provider,amount={"value":"101.00","currency":"RUB"}),
               dict(provider,recipient={"account_id":"99999"}),
               dict(provider,metadata={"copimine_payment_id":payment["id"],"copimine_account_id":"another-user"}),
               dict(provider,test=True)]
        for altered in cases:
            with self.subTest(altered=altered):
                with self.assertRaises(APIError):
                    self.svc._apply_provider(payment["id"],altered)
        self.assertEqual(self.balance(),(1000,1000,0))

    def test_success_hook_recovers_create_whose_response_was_lost(self):
        self.configured()
        captured={}
        def lost(method,path="",payload=None,key=None):
            captured["provider"]=self.provider(method,path,payload,key)
            raise APIError(502,"Fixture timeout")
        with patch.object(self.svc,"_provider_request",side_effect=lost):
            with self.assertRaises(APIError):
                self.svc.create_payment({"amount":100,"method":"sbp","idempotencyKey":"unknown-create-001"},
                                        self.player["user"],self.player["session"])
        provider=captured["provider"];provider.update(status="succeeded",paid=True)
        with patch.object(self.svc,"_provider_request",return_value=provider):
            self.svc.payment_webhook({"type":"notification","event":"payment.succeeded","object":provider},"127.0.0.1")
        self.assertEqual(self.balance(),(1000,1100,1))

    def test_payment_visibility_and_test_payments_never_credit_live_balance(self):
        self.configured(test=True)
        payment,provider=self.create()
        with self.assertRaises(APIError) as error:
            self.svc.refresh_payment(payment["id"],self.other["user"],self.other["session"])
        self.assertEqual(error.exception.status,404)
        result=self.svc._apply_provider(payment["id"],dict(provider,status="succeeded",paid=True))
        self.assertEqual(result["status"],"test_completed")
        self.assertEqual(self.balance(),(1000,1000,0))

    def test_unknown_create_is_not_retried_after_provider_key_window(self):
        self.configured()
        with patch.object(self.svc,"_provider_request",side_effect=APIError(502,"Fixture timeout")):
            with self.assertRaises(APIError):
                self.svc.create_payment({"amount":100,"method":"sbp","idempotencyKey":"unknown-create-002"},
                                        self.player["user"],self.player["session"])
        with self.svc.store.transaction() as conn:
            conn.execute("UPDATE payments SET created_at=?",(now_ms()-24*3600000,))
        with patch.object(self.svc,"_provider_request") as transport:
            with self.assertRaises(APIError) as error:
                self.svc.create_payment({"amount":100,"method":"sbp","idempotencyKey":"unknown-create-002"},
                                        self.player["user"],self.player["session"])
            self.assertEqual(error.exception.code,"payment_unknown")
            transport.assert_not_called()

if __name__=="__main__":
    unittest.main()

