using CopiMineLauncher.Infrastructure.Provisioning;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.IntegrationTests;

public sealed class FabricProvisioningSpikeTests
{
    [Fact]
    public void CmlLib_resolves_the_pinned_Fabric_profile_name()
    {
        var versionName = FabricProvisioner.ResolveVersionName("1.21.1", "0.19.3");

        versionName.Should().Be("fabric-loader-0.19.3-1.21.1");
    }

    [Theory]
    [InlineData("1.21.2", "0.19.3")]
    [InlineData("1.21.1", "0.19.2")]
    public void Fabric_spike_rejects_unpinned_versions_before_network_use(string minecraftVersion, string loaderVersion)
    {
        var action = () => FabricProvisioner.ResolveVersionName(minecraftVersion, loaderVersion);

        action.Should().Throw<ArgumentException>();
    }
}
