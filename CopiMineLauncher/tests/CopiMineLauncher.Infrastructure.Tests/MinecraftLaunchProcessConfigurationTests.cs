using CopiMineLauncher.Infrastructure.Launch;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class MinecraftLaunchProcessConfigurationTests
{
    [Fact]
    public void Process_uses_the_managed_instance_as_working_directory_and_hides_console()
    {
        var temp = Directory.CreateTempSubdirectory("copimine-launch-process-");
        try
        {
            using var process = new System.Diagnostics.Process();

            MinecraftLaunchProcessConfiguration.Apply(process, temp.FullName);

            process.StartInfo.WorkingDirectory.Should().Be(Path.GetFullPath(temp.FullName));
            process.StartInfo.UseShellExecute.Should().BeFalse();
            process.StartInfo.CreateNoWindow.Should().BeTrue();
            process.StartInfo.WindowStyle.Should().Be(System.Diagnostics.ProcessWindowStyle.Hidden);
            process.StartInfo.RedirectStandardOutput.Should().BeTrue();
            process.StartInfo.RedirectStandardError.Should().BeTrue();
        }
        finally
        {
            temp.Delete(true);
        }
    }
}
