using CmlLib.Core.ProcessBuilder;
using CopiMineLauncher.Infrastructure.Launch;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class MinecraftLaunchServerConfigurationTests
{
    [Fact]
    public void Applies_optional_server_connection_to_cmlib_option()
    {
        var options = new MLaunchOption();

        MinecraftLaunchServerConfiguration.Apply(options, "127.0.0.1", 25566);

        options.ServerIp.Should().Be("127.0.0.1");
        options.ServerPort.Should().Be(25566);
    }

    [Fact]
    public void Leaves_server_connection_unset_when_no_server_was_requested()
    {
        var options = new MLaunchOption();

        MinecraftLaunchServerConfiguration.Apply(options, null, 25565);

        options.ServerIp.Should().BeNullOrEmpty();
        options.ServerPort.Should().Be(25565);
    }

    [Theory]
    [InlineData(0)]
    [InlineData(65536)]
    public void Rejects_invalid_server_ports(int port)
    {
        var options = new MLaunchOption();

        var action = () => MinecraftLaunchServerConfiguration.Apply(options, "127.0.0.1", port);

        action.Should().Throw<ArgumentOutOfRangeException>();
    }
}
