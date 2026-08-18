namespace CopiMineLauncher.App;

public sealed record LauncherProtocolCallback(string ChallengeId);

public static class LauncherProtocolCallbackParser
{
    public static bool TryParse(string? value, out LauncherProtocolCallback callback)
    {
        callback = new LauncherProtocolCallback(string.Empty);
        if (string.IsNullOrWhiteSpace(value)
            || !Uri.TryCreate(value.Trim(), UriKind.Absolute, out var uri)
            || !string.Equals(uri.Scheme, "copimine", StringComparison.OrdinalIgnoreCase)
            || !string.Equals(uri.Host, "launcher", StringComparison.OrdinalIgnoreCase)
            || !string.IsNullOrEmpty(uri.UserInfo)
            || !string.Equals(uri.AbsolutePath, "/link", StringComparison.Ordinal)
            || !string.IsNullOrEmpty(uri.Fragment)
            || uri.Query.Length == 0)
        {
            return false;
        }

        var challenge = ReadQueryValue(uri.Query, "challenge");
        if (!IsSafeToken(challenge, 16, 96)) return false;

        callback = new LauncherProtocolCallback(challenge!);
        return true;
    }

    private static string? ReadQueryValue(string query, string key)
    {
        foreach (var pair in query.TrimStart('?').Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var parts = pair.Split('=', 2);
            if (parts.Length != 2 || !string.Equals(Uri.UnescapeDataString(parts[0]), key, StringComparison.Ordinal)) continue;

            try
            {
                return Uri.UnescapeDataString(parts[1]);
            }
            catch (UriFormatException)
            {
                return null;
            }
        }

        return null;
    }

    internal static bool IsSafeToken(string? value, int minimumLength, int maximumLength) =>
        value is not null
        && value.Length >= minimumLength
        && value.Length <= maximumLength
        && value.All(character => char.IsAsciiLetterOrDigit(character) || character is '_' or '-');
}
