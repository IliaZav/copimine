using System.Net;
using System.Text;
using CopiMineLauncher.Infrastructure.Binding;
using CopiMineLauncher.Infrastructure.Skins;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class LauncherSecurityBoundaryTests
{
    [Fact]
    public void Cosmetic_texture_source_rejects_credentials_even_when_host_is_allowlisted()
    {
        var accepted = CosmeticTextureSources.TryNormalize(
            new Uri("https://attacker:text@textures.minecraft.net/texture/skin"),
            out _);

        accepted.Should().BeFalse();
    }

    [Fact]
    public async Task Binding_authorization_url_rejects_credentials_even_when_host_is_copimine()
    {
        using var http = new HttpClient(new StaticResponseHandler("""
        {
          "challengeId": "challenge-credentials-123456",
          "pollToken": "poll-credentials-abcdefghijklmnopqrstuvwxyz-123456",
          "authorizationUrl": "https://attacker:secret@copimine.ru/cabinet/link.html",
          "expiresAt": "2099-08-15T18:00:00Z",
          "minecraftName": "Player"
        }
        """));
        var client = new HttpLauncherBindingClient(http, new Uri("https://copimine.ru/"), "cm-device-1234567890");

        Func<Task> action = () => client.CreateChallengeAsync("Player", "1.0.0", CancellationToken.None);

        var exception = await action.Should().ThrowAsync<LauncherBindingException>();
        exception.Which.Code.Should().Be("LAUNCHER_LINK_AUTH_URL_INVALID");
    }

    private sealed class StaticResponseHandler(string payload) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(payload, Encoding.UTF8, "application/json")
            });
    }

}
