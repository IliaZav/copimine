import sys,unittest,tempfile,json
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from services.notifications import NotificationGateway,DeliveryError,parse_sms_reply
class GatewayTests(unittest.TestCase):
 def test_dry_run_never_calls_network(self):
  with tempfile.TemporaryDirectory() as d,patch('urllib.request.urlopen',side_effect=AssertionError('network')):
   r=NotificationGateway({},Path(d)).send_code('email','demo@example.test','123456',verified=True)
   self.assertEqual(r['status'],'local-preview');self.assertNotIn('123456',json.dumps(r))
   self.assertEqual(len(list(Path(d).glob('*.eml'))),1)
 def test_unverified_refused(self):
  with tempfile.TemporaryDirectory() as d:
   with self.assertRaises(DeliveryError):NotificationGateway({},Path(d)).send_code('email','a@b.test','123456',verified=False)
 def test_live_needs_configuration(self):
  with tempfile.TemporaryDirectory() as d:
   with self.assertRaises(DeliveryError):NotificationGateway({'CM_NOTIFY_MODE':'live'},Path(d)).send_code('sms','+79990000000','123456',verified=True)
 def test_sms_top_level_success_is_not_enough(self):
  with self.assertRaises(DeliveryError):parse_sms_reply({'status':'OK','sms':{'79990000000':{'status':'ERROR','status_code':207}}},'79990000000')
 def test_sms_accepted_is_not_delivered(self):
  self.assertEqual(parse_sms_reply({'status':'OK','sms':{'79990000000':{'status':'OK','sms_id':'ref-1'}}},'79990000000'),{'status':'accepted','provider_id':'ref-1'})
 def test_bad_code_and_header_injection(self):
  with tempfile.TemporaryDirectory() as d:
   g=NotificationGateway({},Path(d))
   for target,code in [('x@x.test\r\nBcc: x@y.test','123456'),('x@x.test','not-a-code')]:
    with self.assertRaises(DeliveryError):g.send_code('email',target,code,verified=True)
if __name__=='__main__':unittest.main()
