using CopiMineLauncher.Infrastructure.Provisioning;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class MinecraftProvisionerTests
{
    [Fact]
    public async Task Provisioning_installs_vanilla_and_fabric_profiles_before_launch()
    {
        using var httpClient = new HttpClient();
        var profileInstaller = new FakeProfileInstaller();
        var fabricProvisioner = new FakeFabricProvisioner();
        var provisioner = new MinecraftProvisioner(httpClient, fabricProvisioner, profileInstaller);

        var result = await provisioner.EnsureMinecraftFabricAsync(
            "fixture-instance",
            "1.21.1",
            "0.19.3",
            CancellationToken.None);

        result.FabricVersionName.Should().Be("fabric-loader-0.19.3-1.21.1");
        profileInstaller.InstalledVersions.Should().Equal(
            "1.21.1",
            "fabric-loader-0.19.3-1.21.1");
    }

    [Fact]
    public async Task Production_provisioning_does_not_fall_back_to_external_Mojang_or_Fabric_services()
    {
        using var httpClient = new HttpClient();
        var provisioner = new MinecraftProvisioner(httpClient);

        var action = () => provisioner.EnsureMinecraftFabricAsync(
            Path.Combine(Path.GetTempPath(), "copimine-unseeded-" + Guid.NewGuid().ToString("N")),
            "1.21.1",
            "0.19.3",
            CancellationToken.None);

        await action.Should().ThrowAsync<MinecraftProvisioningException>()
            .Where(exception => exception.Code == "MINECRAFT_RUNTIME_NOT_READY");
    }

    private sealed class FakeProfileInstaller : IMinecraftProfileInstaller
    {
        public List<string> InstalledVersions { get; } = [];

        public Task InstallAsync(string versionName, CancellationToken cancellationToken)
        {
            InstalledVersions.Add(versionName);
            return Task.CompletedTask;
        }
    }

    private sealed class FakeFabricProvisioner : IFabricProvisioner
    {
        public Task<FabricProvisioningResult> EnsureFabricAsync(
            string instanceRoot,
            string minecraftVersion,
            string fabricLoaderVersion,
            CancellationToken cancellationToken) =>
            Task.FromResult(new FabricProvisioningResult(
                minecraftVersion,
                fabricLoaderVersion,
                "fabric-loader-0.19.3-1.21.1",
                instanceRoot));
    }
}
