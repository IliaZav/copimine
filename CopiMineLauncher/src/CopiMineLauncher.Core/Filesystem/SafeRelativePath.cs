namespace CopiMineLauncher.Core.Filesystem;

public readonly record struct SafeRelativePath
{
    private static readonly HashSet<string> ReservedDeviceNames = new(StringComparer.OrdinalIgnoreCase)
    {
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    private SafeRelativePath(string value) => Value = value;

    public string Value { get; }

    public static SafeRelativePath Parse(string value)
    {
        if (string.IsNullOrWhiteSpace(value) || value.Contains('\0') || value.Contains('\\'))
        {
            throw new ArgumentException("Path must be a non-empty forward-slash relative path", nameof(value));
        }

        if (value.StartsWith("/", StringComparison.Ordinal) || value[0] == '~' || HasDrivePrefix(value))
        {
            throw new ArgumentException("Path must not be absolute or drive-qualified", nameof(value));
        }

        var segments = value.Split('/');
        if (segments.Length == 0 || segments.Any(segment => segment.Length == 0 || segment is "." or ".."))
        {
            throw new ArgumentException("Path contains an empty or traversal segment", nameof(value));
        }

        foreach (var segment in segments)
        {
            if (segment.EndsWith(' ') || segment.EndsWith('.') || ReservedDeviceNames.Contains(segment.Split('.')[0]))
            {
                throw new ArgumentException("Path contains a reserved or ambiguous Windows name", nameof(value));
            }
        }

        return new SafeRelativePath(string.Join('/', segments));
    }

    public override string ToString() => Value;

    private static bool HasDrivePrefix(string value) => value.Length >= 2 && char.IsLetter(value[0]) && value[1] == ':';
}
