using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;

namespace CopiMineLauncher.App;

/// <summary>
/// Keeps a freshly installed Launcher usable before the public distribution
/// directory has been published. The remote distribution remains authoritative;
/// the bundled bootstrap is used only for the exact signed instance resources
/// when the remote endpoint is missing or temporarily unreachable.
/// </summary>
public sealed class LauncherDistributionHttpMessageHandler : HttpMessageHandler
{
    private readonly HttpMessageInvoker innerInvoker;
    private readonly string bootstrapRoot;
    private static readonly TimeSpan BootstrapRemoteTimeout = TimeSpan.FromSeconds(8);

    public LauncherDistributionHttpMessageHandler(HttpMessageHandler innerHandler, string bootstrapRoot)
    {
        ArgumentNullException.ThrowIfNull(innerHandler);
        innerInvoker = new HttpMessageInvoker(innerHandler, disposeHandler: true);
        this.bootstrapRoot = Path.GetFullPath(bootstrapRoot ?? throw new ArgumentNullException(nameof(bootstrapRoot)));
    }

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        var fallbackPath = ResolveBootstrapPath(request.RequestUri);
        using var fallbackTimeout = fallbackPath is not null && File.Exists(fallbackPath)
            ? CancellationTokenSource.CreateLinkedTokenSource(cancellationToken)
            : null;
        fallbackTimeout?.CancelAfter(BootstrapRemoteTimeout);
        try
        {
            var response = await innerInvoker.SendAsync(request, fallbackTimeout?.Token ?? cancellationToken).ConfigureAwait(false);
            if (response.StatusCode != HttpStatusCode.NotFound
                || fallbackPath is null
                || !File.Exists(fallbackPath))
            {
                return response;
            }

            response.Dispose();
            return CreateBootstrapResponse(request, fallbackPath!);
        }
        catch (HttpRequestException) when (fallbackPath is not null && File.Exists(fallbackPath))
        {
            return CreateBootstrapResponse(request, fallbackPath!);
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested
            && fallbackPath is not null
            && File.Exists(fallbackPath))
        {
            return CreateBootstrapResponse(request, fallbackPath!);
        }
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            innerInvoker.Dispose();
        }

        base.Dispose(disposing);
    }

    private HttpResponseMessage CreateBootstrapResponse(HttpRequestMessage request, string path)
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            RequestMessage = request,
            Content = new StreamContent(new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read))
        };
        response.Content.Headers.ContentType = new MediaTypeHeaderValue(
            path.EndsWith(".json", StringComparison.OrdinalIgnoreCase) || path.EndsWith(".sig", StringComparison.OrdinalIgnoreCase)
                ? "application/json"
                : "application/octet-stream");
        response.Headers.TryAddWithoutValidation("X-Copimine-Launcher-Source", "bundled-bootstrap");
        return response;
    }

    private string? ResolveBootstrapPath(Uri? uri)
    {
        if (uri is null
            || !uri.IsAbsoluteUri
            || !string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            || (uri.Port != 443 && uri.Port != -1)
            || !string.IsNullOrEmpty(uri.UserInfo)
            || !IsAllowedHost(uri.Host))
        {
            return null;
        }

        string? relativePath = uri.AbsolutePath switch
        {
            "/launcher/stable/instance-manifest.json" => "instance-manifest.json",
            "/launcher/stable/instance-manifest.sig" => "instance-manifest.sig",
            _ => ResolveManagedFilePath(uri.AbsolutePath)
        };
        if (relativePath is null)
        {
            return null;
        }

        var root = bootstrapRoot.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)
            + Path.DirectorySeparatorChar;
        var candidate = Path.GetFullPath(Path.Combine(bootstrapRoot, relativePath.Replace('/', Path.DirectorySeparatorChar)));
        return candidate.StartsWith(root, StringComparison.OrdinalIgnoreCase) ? candidate : null;
    }

    private static string? ResolveManagedFilePath(string absolutePath)
    {
        const string prefix = "/launcher/files/";
        if (!absolutePath.StartsWith(prefix, StringComparison.Ordinal))
        {
            return null;
        }

        var hash = absolutePath[prefix.Length..];
        if (hash.Length != 64 || hash.Any(character => !Uri.IsHexDigit(character)))
        {
            return null;
        }

        return $"files/{hash.ToLowerInvariant()}";
    }

    private static bool IsAllowedHost(string host) =>
        host.Equals("copimine.ru", StringComparison.OrdinalIgnoreCase)
        || host.Equals("www.copimine.ru", StringComparison.OrdinalIgnoreCase)
        || host.Equals("cdn.copimine.ru", StringComparison.OrdinalIgnoreCase)
        || host.EndsWith(".copimine.ru", StringComparison.OrdinalIgnoreCase);
}
