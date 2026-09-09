namespace CopiMineLauncher.Infrastructure.Manifest;

/// <summary>
/// Public staging key used by local release fixtures. The corresponding private
/// seed is never stored in source or packaged with the Launcher. A production
/// release replaces this public value through the controlled signing release.
/// </summary>
public static class PinnedManifestKey
{
    public const string KeyId = "launcher-v1-staging";

    public static byte[] PublicKey => Convert.FromHexString(
        "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
}
