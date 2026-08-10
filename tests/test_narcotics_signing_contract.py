from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NARCOTICS_FACTORY = (
    ROOT
    / "copimine-narcotics/src/me/copimine/narcotics/item/NarcoticItemFactory.java"
)
RELEASE_INSTALLER = ROOT / "deploy/ubuntu/copimine_unpack_and_verify.sh"


def test_narcotics_verifies_hmac_with_bounded_legacy_keyring():
    source = NARCOTICS_FACTORY.read_text(encoding="utf-8")
    assert "COPIMINE_NARCOTICS_SIGNING_LEGACY_SECRETS_FILE" in source
    assert "verificationSecrets" in source
    assert "for (byte[] candidate : verificationSecrets)" in source
    assert "MessageDigest.isEqual" in source


def test_release_installer_preserves_narcotics_signing_keys():
    source = RELEASE_INSTALLER.read_text(encoding="utf-8")
    assert "NARCOTICS_SIGNING_SECRET_FILE" in source
    assert "NARCOTICS_SIGNING_LEGACY_SECRETS_FILE" in source
    assert "preserveNarcoticsSigningSecret" in source
    assert "prepareNarcoticsSigningState" in source
