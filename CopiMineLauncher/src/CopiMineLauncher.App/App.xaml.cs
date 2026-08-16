using System.Windows;
using System.Net.Http;
using System.IO;
using CopiMineLauncher.Core;
using CopiMineLauncher.Infrastructure.Binding;
using Velopack;
using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.Manifest;
using CopiMineLauncher.Infrastructure.News;
using CopiMineLauncher.Infrastructure.Provisioning;
using CopiMineLauncher.Infrastructure.Runtime;
using CopiMineLauncher.Infrastructure.SelfUpdate;
using CopiMineLauncher.Infrastructure.Servers;
using CopiMineLauncher.Infrastructure.Updates;

namespace CopiMineLauncher.App;

public partial class App : Application
{
    public App()
    {
        VelopackApp.Build().Run();
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        LauncherProtocolRegistration.EnsureRegistered();
        var httpClient = new HttpClient(CreateHttpHandler())
        {
            Timeout = TimeSpan.FromSeconds(30)
        };
        var launcherDataRoot = LauncherInstallPaths.ResolveLauncherDataRoot();
        var bindingStateStore = new LauncherBindingStateStore(launcherDataRoot);
        var deviceId = new LauncherDeviceIdentityStore(launcherDataRoot).LoadOrCreate();
        var productionBindingClient = new HttpLauncherBindingClient(httpClient, new Uri("https://copimine.ru/"), deviceId);
        var localBindingClient = new HttpLauncherBindingClient(
            httpClient,
            LauncherInstallPaths.ResolveLocalBindingBaseUrl(),
            deviceId);
        var bindingClient = new FallbackLauncherBindingClient(productionBindingClient, localBindingClient);
        var cachePath = Path.Combine(launcherDataRoot, "patch-feed.json");
        var feedClient = new PatchFeedClient(httpClient, cachePath);
        var manifestClient = new SignedInstanceManifestClient(
            httpClient,
            new Ed25519ManifestVerifier(),
            PinnedManifestKey.PublicKey,
            PinnedManifestKey.KeyId);
        var downloads = new ResumableDownloadManager(httpClient);
        var runtimeCoordinator = new LauncherRuntimeCoordinator(
            manifestClient,
            new MinecraftProvisioner(httpClient),
            new JavaProvisioner(downloads),
            new TransactionalReconcilerFactory(downloads),
            new ServersDatService(),
            new MinecraftLaunchService(httpClient));
        var selfUpdate = new VelopackSelfUpdateService(
            LauncherInstallPaths.ResolveSelfUpdateFeed(GetStagingBaseUrl()),
            new VelopackUpdateBackend(),
            launcherDataRoot,
            new SelfUpdatePolicy(),
            () => LauncherVersionInfo.Version);
        var window = new MainWindow(new LauncherViewModel(
            feedClient,
            runtimeCoordinator,
            selfUpdate,
            defaultInstancePath: LauncherInstallPaths.ResolveMinecraftRoot(),
            launcherBindingClient: bindingClient,
            launcherBindingStateStore: bindingStateStore));
        MainWindow = window;
        window.Show();

        var callback = e.Args.FirstOrDefault(argument => LauncherProtocolCallbackParser.TryParse(argument, out _));
        if (!string.IsNullOrWhiteSpace(callback))
        {
            _ = window.HandleLauncherProtocolCallbackAsync(callback);
        }
    }

    private static HttpMessageHandler CreateHttpHandler()
    {
        var stagingValue = Environment.GetEnvironmentVariable("COPIMINE_LAUNCHER_STAGING_BASE_URL");
        HttpMessageHandler innerHandler;
        if (Uri.TryCreate(stagingValue, UriKind.Absolute, out var stagingBase)
            && stagingBase.IsLoopback
            && string.Equals(stagingBase.Scheme, Uri.UriSchemeHttp, StringComparison.OrdinalIgnoreCase)
            && string.IsNullOrEmpty(stagingBase.UserInfo))
        {
            innerHandler = new StagingHttpMessageHandler(stagingBase);
        }
        else
        {
            innerHandler = new HttpClientHandler();
        }

        return new LauncherDistributionHttpMessageHandler(
            innerHandler,
            LauncherInstallPaths.ResolveLauncherBootstrapRoot());
    }

    private static Uri? GetStagingBaseUrl()
    {
        var value = Environment.GetEnvironmentVariable("COPIMINE_LAUNCHER_STAGING_BASE_URL");
        return Uri.TryCreate(value, UriKind.Absolute, out var stagingBase)
            && stagingBase.IsLoopback
            && string.Equals(stagingBase.Scheme, Uri.UriSchemeHttp, StringComparison.OrdinalIgnoreCase)
            && string.IsNullOrEmpty(stagingBase.UserInfo)
            ? stagingBase
            : null;
    }

    private sealed class StagingHttpMessageHandler(Uri stagingBase) : HttpClientHandler
    {
        private readonly Uri stagingBase = stagingBase.AbsoluteUri.EndsWith("/", StringComparison.Ordinal)
            ? stagingBase
            : new Uri(stagingBase.AbsoluteUri + "/", UriKind.Absolute);

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            if (request.RequestUri is { IsAbsoluteUri: true } requestUri
                && (requestUri.Host.Equals("copimine.ru", StringComparison.OrdinalIgnoreCase)
                    || requestUri.Host.Equals("www.copimine.ru", StringComparison.OrdinalIgnoreCase)
                    || requestUri.Host.Equals("cdn.copimine.ru", StringComparison.OrdinalIgnoreCase)))
            {
                request.RequestUri = new Uri(stagingBase, requestUri.AbsolutePath.TrimStart('/') + requestUri.Query);
            }

            return base.SendAsync(request, cancellationToken);
        }
    }
}
