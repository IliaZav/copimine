from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NARCOTICS_FACTORY = (
    ROOT
    / "copimine-narcotics/src/me/copimine/narcotics/item/NarcoticItemFactory.java"
)
NARCOTICS_KEYRING = (
    ROOT
    / "copimine-narcotics/src/me/copimine/narcotics/item/NarcoticSigningKeyring.java"
)
RELEASE_INSTALLER = ROOT / "deploy/ubuntu/copimine_unpack_and_verify.sh"


def test_issued_items_accept_signatures_from_a_bounded_legacy_keyring() -> None:
    factory = NARCOTICS_FACTORY.read_text(encoding="utf-8")
    keyring = NARCOTICS_KEYRING.read_text(encoding="utf-8")

    assert "COPIMINE_NARCOTICS_SIGNING_LEGACY_SECRETS_FILE" in factory
    assert "private final NarcoticSigningKeyring signingKeyring;" in factory
    assert "NarcoticSigningKeyring.fromBase64(primaryEncoded, legacyKeys)" in factory
    assert "signingKeyring.verify(" in factory
    assert "private static final int MAX_LEGACY_KEYS = 8;" in keyring
    assert "containsKey(keys, candidate)" in keyring
    assert "MessageDigest.isEqual" in keyring


def test_new_narcotics_signatures_use_only_the_current_primary_key() -> None:
    factory = NARCOTICS_FACTORY.read_text(encoding="utf-8")
    keyring = NARCOTICS_KEYRING.read_text(encoding="utf-8")

    assert "this.signingKeyring = loadSigningKeyring(plugin);" in factory
    assert "return signingKeyring.sign(signaturePayload(" in factory
    signing = keyring.split("    String sign(String payload) {", 1)[1].split("    boolean verify(", 1)[0]
    assert "return hmac(primarySecret, payload);" in signing
    assert "verificationSecrets" not in signing


def test_release_installer_preserves_external_narcotics_signing_state() -> None:
    source = RELEASE_INSTALLER.read_text(encoding="utf-8")

    assert "COPIMINE_NARCOTICS_SIGNING_SECRET_FILE" in source
    assert "COPIMINE_NARCOTICS_SIGNING_LEGACY_SECRETS_FILE" in source
    assert "NARCOTICS_MAX_LEGACY_SIGNING_SECRETS=8" in source
    assert "preserveNarcoticsSigningSecret" in source
    assert "prepareNarcoticsSigningState" in source
    assert 'install -d -o "$APP_USER" -g "$APP_GROUP" -m 0700 "$legacy_directory"' in source
    assert 'chmod 600 "$NARCOTICS_SIGNING_LEGACY_SECRETS_FILE"' in source
