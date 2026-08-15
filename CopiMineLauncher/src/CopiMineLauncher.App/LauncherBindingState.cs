using System.Text.Json;

namespace CopiMineLauncher.App;

public sealed record LauncherBindingState(
    bool Linked = false,
    string SiteAccountId = "",
    string SiteUsername = "",
    string MinecraftName = "");

public sealed class LauncherDeviceIdentityStore
{
    private readonly string path;

    public LauncherDeviceIdentityStore(string stateRoot)
    {
        path = Path.Combine(Path.GetFullPath(stateRoot), "launcher-device-id.txt");
    }

    public string LoadOrCreate()
    {
        try
        {
            if (File.Exists(path))
            {
                var existing = File.ReadAllText(path).Trim();
                if (IsValid(existing)) return existing;
            }

            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            var created = $"cm-{Guid.NewGuid():N}";
            var temporary = path + ".tmp";
            File.WriteAllText(temporary, created + Environment.NewLine);
            File.Move(temporary, path, overwrite: true);
            return created;
        }
        catch (IOException exception)
        {
            throw new InvalidOperationException("Не удалось сохранить идентификатор Launcher.", exception);
        }
    }

    private static bool IsValid(string value) =>
        value.Length is >= 16 and <= 128
        && value.All(character => char.IsAsciiLetterOrDigit(character) || character is '.' or '_' or ':' or '-');
}

public sealed class LauncherBindingStateStore
{
    private readonly string path;

    public LauncherBindingStateStore(string stateRoot)
    {
        path = Path.Combine(Path.GetFullPath(stateRoot), "launcher-binding.json");
    }

    public LauncherBindingState Load()
    {
        if (!File.Exists(path)) return new();
        try
        {
            var state = JsonSerializer.Deserialize<LauncherBindingState>(File.ReadAllText(path));
            return state is { Linked: true } && !string.IsNullOrWhiteSpace(state.SiteAccountId)
                ? state
                : new();
        }
        catch (JsonException)
        {
            return new();
        }
        catch (IOException)
        {
            return new();
        }
    }

    public void Save(LauncherBindingState state)
    {
        ArgumentNullException.ThrowIfNull(state);
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temporary = path + ".tmp";
        File.WriteAllText(temporary, JsonSerializer.Serialize(state, new JsonSerializerOptions { WriteIndented = true }) + Environment.NewLine);
        File.Move(temporary, path, overwrite: true);
    }
}
