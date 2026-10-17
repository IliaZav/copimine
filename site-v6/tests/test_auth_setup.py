"""Operator setup must not silently enable paid delivery or alter a secret."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("configure_auth", Path(__file__).resolve().parents[1]/"scripts/configure_auth.py")
wizard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wizard)


class AuthSetupTests(unittest.TestCase):
    def test_editing_vk_does_not_enable_existing_disabled_delivery(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/"site.json"
            values = {"adapterUrl": "http://127.0.0.1:9998/operations", "notificationEnv": {"CM_NOTIFY_MODE": "disabled", "CM_SMSRU_API_ID": "fixture-secret-not-live"}}
            path.write_text(json.dumps(values))
            with patch.object(wizard.sys, "argv", ["configure_auth.py", "--config", str(path), "--origin", "https://copimine.test"]), patch.object(wizard.sys.stdin, "isatty", return_value=True), patch("builtins.input", side_effect=["н", "н", "д", "123456", ""]), patch("builtins.print"):
                wizard.main()
            saved = json.loads(path.read_text())
            self.assertEqual(saved["notificationEnv"]["CM_NOTIFY_MODE"], "disabled")
            self.assertEqual(saved["vkClientId"], "123456")
            self.assertEqual(saved["adapterUrl"], values["adapterUrl"])
            self.assertEqual(saved["notificationEnv"]["CM_SMSRU_API_ID"], "fixture-secret-not-live")
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_secret_whitespace_is_preserved_and_is_never_prompted_back(self):
        with patch.object(wizard.getpass, "getpass", return_value="  actual password  ") as prompt:
            self.assertEqual(wizard.ask("SMTP", "old-secret", secret=True), "  actual password  ")
            self.assertNotIn("old-secret", prompt.call_args.args[0])

    def test_symlink_destination_cannot_replace_another_file(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)/"protected.json"
            target.write_text('{"unchanged":true}')
            link = Path(directory)/"settings.json"
            link.symlink_to(target)
            with self.assertRaises(ValueError):
                wizard.save_private(link, {"changed": True})
            self.assertEqual(json.loads(target.read_text()), {"unchanged": True})


if __name__ == "__main__":
    unittest.main()
