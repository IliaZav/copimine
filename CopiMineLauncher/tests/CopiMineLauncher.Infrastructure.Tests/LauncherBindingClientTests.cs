using System.Net;
using System.Text;
using CopiMineLauncher.Infrastructure.Binding;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class LauncherBindingClientTests
{
    [Fact]
    public async Task Challenge_response_is_parsed_without_sending_a_password()
    {
        using var http = new HttpClient(new RecordingHandler(request =>
        {
            request.Method.Should().Be(HttpMethod.Post);
            request.Content!.ReadAsStringAsync().GetAwaiter().GetResult().ToLowerInvariant().Should().NotContain("password");
            return Json("""
            {
              "challengeId": "challenge-1234567890",
              "pollToken": "poll-token-abcdefghijklmnopqrstuvwxyz-123456",
              "authorizationUrl": "https://copimine.ru/cabinet/link.html?launcher_challenge=challenge-1234567890",
              "expiresAt": "2026-08-15T18:00:00Z",
              "minecraftName": "Player"
            }
            """);
        }));
        var client = new HttpLauncherBindingClient(http, new Uri("https://copimine.ru/"), "cm-device-1234567890");

        var result = await client.CreateChallengeAsync("Player", "1.0.0", CancellationToken.None);

        result.ChallengeId.Should().Be("challenge-1234567890");
        result.AuthorizationUrl.Host.Should().Be("copimine.ru");
        result.MinecraftName.Should().Be("Player");
    }

    [Fact]
    public async Task Link_status_sends_only_device_and_poll_credentials_and_parses_linked_account()
    {
        using var http = new HttpClient(new RecordingHandler(request =>
        {
            request.Method.Should().Be(HttpMethod.Get);
            request.RequestUri!.Query.Should().Contain("device_id=cm-device-1234567890");
            request.RequestUri.Query.Should().Contain("poll_token=poll-token");
            return Json("""
            {
              "linked": true,
              "status": "LINKED",
              "siteAccountId": "account-1",
              "siteUsername": "PlayerOne",
              "minecraftName": "Player",
              "launcherAccessToken": "poll-token"
            }
            """);
        }));
        var client = new HttpLauncherBindingClient(http, new Uri("https://copimine.ru/"), "cm-device-1234567890");
        var challenge = new LauncherLinkChallenge(
            "challenge-1234567890",
            "poll-token",
            new Uri("https://copimine.ru/cabinet/link.html"),
            DateTimeOffset.UtcNow.AddMinutes(5),
            "Player");

        var result = await client.GetStatusAsync(challenge, CancellationToken.None);

        result.Linked.Should().BeTrue();
        result.SiteUsername.Should().Be("PlayerOne");
    }

    [Fact]
    public async Task Challenge_rejects_an_authorization_url_outside_the_copimine_site()
    {
        using var http = new HttpClient(new RecordingHandler(_ => Json("""
        {
          "challengeId": "challenge-1234567890",
          "pollToken": "poll-token-abcdefghijklmnopqrstuvwxyz-123456",
          "authorizationUrl": "https://evil.example/cabinet/link.html",
          "expiresAt": "2026-08-15T18:00:00Z",
          "minecraftName": "Player"
        }
        """)));
        var client = new HttpLauncherBindingClient(http, new Uri("https://copimine.ru/"), "cm-device-1234567890");

        var action = () => client.CreateChallengeAsync("Player", "1.0.0", CancellationToken.None);

        await action.Should().ThrowAsync<LauncherBindingException>()
            .Where(exception => exception.Code == "LAUNCHER_LINK_AUTH_URL_INVALID");
    }

    private static HttpResponseMessage Json(string payload) =>
        new(HttpStatusCode.OK) { Content = new StringContent(payload, Encoding.UTF8, "application/json") };

    private sealed class RecordingHandler(Func<HttpRequestMessage, HttpResponseMessage> callback) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(callback(request));
    }
}
