using System.IO;
using System.Net;
using System.Net.Http;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherDistributionHttpMessageHandlerTests
{
    [Fact]
    public async Task Bundled_instance_files_are_used_when_distribution_returns_not_found()
    {
        using var bootstrap = new TemporaryDirectory();
        Directory.CreateDirectory(Path.Combine(bootstrap.Path, "files"));
        var manifest = "{\"schemaVersion\":1}";
        var fileHash = new string('a', 64);
        await File.WriteAllTextAsync(Path.Combine(bootstrap.Path, "instance-manifest.json"), manifest);
        await File.WriteAllTextAsync(Path.Combine(bootstrap.Path, "files", fileHash), "managed-mod");

        using var handler = new LauncherDistributionHttpMessageHandler(new NotFoundHandler(), bootstrap.Path);
        using var client = new HttpClient(handler);

        using var manifestResponse = await client.GetAsync("https://copimine.ru/launcher/stable/instance-manifest.json");
        using var fileResponse = await client.GetAsync($"https://copimine.ru/launcher/files/{fileHash}");

        manifestResponse.StatusCode.Should().Be(HttpStatusCode.OK);
        (await manifestResponse.Content.ReadAsStringAsync()).Should().Be(manifest);
        fileResponse.StatusCode.Should().Be(HttpStatusCode.OK);
        (await fileResponse.Content.ReadAsStringAsync()).Should().Be("managed-mod");
        manifestResponse.Headers.GetValues("X-Copimine-Launcher-Source").Single().Should().Be("bundled-bootstrap");
    }

    [Fact]
    public async Task Unrelated_not_found_resources_are_not_hidden_by_the_bootstrap()
    {
        using var bootstrap = new TemporaryDirectory();
        using var handler = new LauncherDistributionHttpMessageHandler(new NotFoundHandler(), bootstrap.Path);
        using var client = new HttpClient(handler);

        using var response = await client.GetAsync("https://copimine.ru/news/missing.html");

        response.StatusCode.Should().Be(HttpStatusCode.NotFound);
        response.Headers.Contains("X-Copimine-Launcher-Source").Should().BeFalse();
    }

    [Fact]
    public async Task Bundled_instance_files_are_used_when_distribution_times_out()
    {
        using var bootstrap = new TemporaryDirectory();
        Directory.CreateDirectory(Path.Combine(bootstrap.Path, "files"));
        var fileHash = new string('b', 64);
        await File.WriteAllTextAsync(Path.Combine(bootstrap.Path, "files", fileHash), "managed-mod-after-timeout");

        using var handler = new LauncherDistributionHttpMessageHandler(new TimeoutHandler(), bootstrap.Path);
        using var client = new HttpClient(handler);

        using var response = await client.GetAsync($"https://copimine.ru/launcher/files/{fileHash}");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        (await response.Content.ReadAsStringAsync()).Should().Be("managed-mod-after-timeout");
        response.Headers.GetValues("X-Copimine-Launcher-Source").Single().Should().Be("bundled-bootstrap");
    }

    private sealed class NotFoundHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound) { RequestMessage = request });
    }

    private sealed class TimeoutHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromException<HttpResponseMessage>(new TaskCanceledException("simulated distribution timeout"));
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-launcher-bootstrap-tests-").FullName;

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
