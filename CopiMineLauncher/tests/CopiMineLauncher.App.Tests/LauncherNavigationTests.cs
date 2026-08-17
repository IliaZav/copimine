using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherNavigationTests
{
    [Fact]
    public void Settings_screen_replaces_home_and_back_returns_to_home()
    {
        var navigation = new LauncherScreenNavigation();

        navigation.NavigateTo(LauncherScreen.Settings);

        navigation.Current.Should().Be(LauncherScreen.Settings);
        navigation.CanGoBack.Should().BeTrue();

        navigation.NavigateBack();

        navigation.Current.Should().Be(LauncherScreen.Home);
        navigation.CanGoBack.Should().BeFalse();
    }

    [Fact]
    public void Repeated_activation_of_the_same_skin_is_a_new_selection_request()
    {
        var gate = new SkinSelectionActivationGate();

        var first = gate.Begin("2049021");
        var second = gate.Begin("2049021");

        first.Version.Should().NotBe(second.Version);
        gate.IsCurrent(first).Should().BeFalse();
        gate.IsCurrent(second).Should().BeTrue();
    }
}
