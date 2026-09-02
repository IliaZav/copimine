using CmlLib.Core.ProcessBuilder;

namespace CopiMineLauncher.Infrastructure.Launch;

public static class MinecraftLaunchServerConfiguration
{
    public static void Apply(MLaunchOption options, string? serverAddress, int serverPort)
    {
        ArgumentNullException.ThrowIfNull(options);

        if (string.IsNullOrWhiteSpace(serverAddress))
        {
            return;
        }

        if (serverPort is < 1 or > 65535)
        {
            throw new ArgumentOutOfRangeException(nameof(serverPort), "Minecraft server port must be between 1 and 65535");
        }

        options.ServerIp = serverAddress.Trim();
        options.ServerPort = serverPort;
    }
}
