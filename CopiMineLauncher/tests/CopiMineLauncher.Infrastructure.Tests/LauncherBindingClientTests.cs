using System.Net;
using System.Text;
using CopiMineLauncher.Infrastructure.Binding;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class LauncherBindingClientTests
{
    [Fact]
    public async Task Fallback_binding_client_keeps_the_real_site_when_primary_endpoint_is_available()
    {
        using var primaryHttp = new HttpClient(new RecordingHandler(request => Json("""
        {
          "challengeId": "challenge-real-123456",
          "pollToken": "poll-real-abcdefghijklmnopqrstuvwxyz-123456",
          "authorizationUrl": "https://copimine.ru/cabinet/link.html?launcher_challenge=challenge-real-123456",
          "expiresAt": "2026-08-15T18:00:00Z",
          "minecraftName": "Player"
        }
        """)));
        using var localHttp = new HttpClient(new RecordingHandler(_ => throw new InvalidOperationException("local endpoint must not be used")));
        var primary = new HttpLauncherBindingClient(primaryHttp, new Uri("https://copimine.ru/"), "cm-device-1234567890");
        var local = new HttpLauncherBindingClient(localHttp, new Uri("http://127.0.0.1:8090/"), "cm-device-1234567890");
        var client = new FallbackLauncherBindingClient(primary, local);

        var result = await client.CreateChallengeAsync("Player", "1.0.0", CancellationToken.None);

        result.AuthorizationUrl.Should().Be(new Uri("https://copimine.ru/cabinet/link.html?launcher_challenge=challenge-real-123456"));
    }

    [Fact]
    public async Task Fallback_binding_client_uses_loopback_when_real_site_is_unavailable_and_keeps_that_endpoint_for_polling()
    {
        var primaryCalls = 0;
        var localCalls = 0;
        using var primaryHttp = new HttpClient(new RecordingHandler(_ =>
        {
            primaryCalls++;
            throw new HttpRequestException("real site is offline");
        }));
        using var localHttp = new HttpClient(new RecordingHandler(request =>
        {
            localCalls++;
            if (request.Method == HttpMethod.Post)
            {
                return Json("""
                {
                  "challengeId": "challenge-local-123456",
                  "pollToken": "poll-local-abcdefghijklmnopqrstuvwxyz-123456",
                  "authorizationUrl": "http://127.0.0.1:8090/cabinet/link.html?launcher_challenge=challenge-local-123456",
                  "expiresAt": "2026-08-15T18:00:00Z",
                  "minecraftName": "Player"
                }
                """);
            }

            return Json("""
            {
              "linked": true,
              "status": "LINKED",
              "siteAccountId": "local-account",
              "siteUsername": "local-player",
              "minecraftName": "Player",
              "launcherAccessToken": "poll-local-abcdefghijklmnopqrstuvwxyz-123456"
            }
            """);
        }));
        var primary = new HttpLauncherBindingClient(primaryHttp, new Uri("https://copimine.ru/"), "cm-device-1234567890");
        var local = new HttpLauncherBindingClient(localHttp, new Uri("http://127.0.0.1:8090/"), "cm-device-1234567890");
        var client = new FallbackLauncherBindingClient(primary, local);

        var challenge = await client.CreateChallengeAsync("Player", "1.0.0", CancellationToken.None);
        var result = await client.GetStatusAsync(challenge, CancellationToken.None);

        primaryCalls.Should().Be(1);
        localCalls.Should().Be(2);
        challenge.AuthorizationUrl.Should().Be(new Uri("http://127.0.0.1:8090/cabinet/link.html?launcher_challenge=challenge-local-123456"));
        result.Linked.Should().BeTrue();
        result.SiteUsername.Should().Be("local-player");
    }

    [LocalBindingFact]
    public async Task Live_loopback_backend_accepts_a_launcher_challenge_after_primary_endpoint_fails()
    {
        var localBaseUrl = Environment.GetEnvironmentVariable("COPIMINE_LOCAL_BINDING_URL")!;
        using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(5) };
        var primary = new HttpLauncherBindingClient(http, new Uri("http://127.0.0.1:1/"), "cm-live-device-1234567890");
        var local = new HttpLauncherBindingClient(http, new Uri(localBaseUrl), "cm-live-device-1234567890");
        var client = new FallbackLauncherBindingClient(primary, local);

        var challenge = await client.CreateChallengeAsync("LauncherLocal", "1.0.0", CancellationToken.None);
        var status = await client.GetStatusAsync(challenge, CancellationToken.None);

        challenge.AuthorizationUrl.IsLoopback.Should().BeTrue();
        challenge.AuthorizationUrl.AbsolutePath.Should().Be("/cabinet/link.html");
        status.Linked.Should().BeFalse();
        status.Status.Should().Be("PENDING");
    }

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

    [Fact]
    public async Task Nickname_change_sends_device_bound_access_token_without_password()
    {
        using var http = new HttpClient(new RecordingHandler(request =>
        {
            request.Method.Should().Be(HttpMethod.Post);
            request.RequestUri!.AbsolutePath.Should().Be("/api/launcher/profile/nickname");
            var payload = request.Content!.ReadAsStringAsync().GetAwaiter().GetResult();
            payload.Should().Contain("old_minecraft_name");
            payload.Should().Contain("new_minecraft_name");
            payload.Should().Contain("access_token");
            payload.ToLowerInvariant().Should().NotContain("password");
            return Json("""
            {
              "ok": true,
              "changed": true,
              "minecraftName": "NewPlayer",
              "minecraftUuid": "00000000-0000-0000-0000-000000000002",
              "preserve_player_state": true,
              "authmePasswordPreserved": true
            }
            """);
        }));
        var client = new HttpLauncherBindingClient(http, new Uri("https://copimine.ru/"), "cm-device-1234567890");

        var result = await client.ChangeNicknameAsync("poll-token-abcdefghijklmnopqrstuvwxyz-123456", "Player", "NewPlayer", CancellationToken.None);

        result.Changed.Should().BeTrue();
        result.MinecraftName.Should().Be("NewPlayer");
        result.AuthMePasswordPreserved.Should().BeTrue();
    }

    private static HttpResponseMessage Json(string payload) =>
        new(HttpStatusCode.OK) { Content = new StringContent(payload, Encoding.UTF8, "application/json") };

    private sealed class RecordingHandler(Func<HttpRequestMessage, HttpResponseMessage> callback) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(callback(request));
    }

    private sealed class LocalBindingFactAttribute : FactAttribute
    {
        public LocalBindingFactAttribute()
        {
            if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("COPIMINE_LOCAL_BINDING_URL")))
            {
                Skip = "Set COPIMINE_LOCAL_BINDING_URL to run the disposable local binding backend flow.";
            }
        }
    }
}
