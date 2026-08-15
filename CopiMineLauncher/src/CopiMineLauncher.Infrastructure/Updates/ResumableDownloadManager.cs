using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;

namespace CopiMineLauncher.Infrastructure.Updates;

public interface IResumableDownloadManager
{
    Task<string> DownloadAsync(
        Uri source,
        string destination,
        long expectedSize,
        string expectedSha256,
        CancellationToken cancellationToken);
}

public sealed class ResumableDownloadManager : IResumableDownloadManager
{
    private const int MaximumAttempts = 3;
    private readonly HttpClient httpClient;

    public ResumableDownloadManager(HttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public async Task<string> DownloadAsync(
        Uri source,
        string destination,
        long expectedSize,
        string expectedSha256,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(source);
        var finalPath = Path.GetFullPath(destination);
        var partPath = finalPath + ".part";
        Directory.CreateDirectory(Path.GetDirectoryName(finalPath)!);
        Exception? lastFailure = null;

        for (var attempt = 1; attempt <= MaximumAttempts; attempt++)
        {
            try
            {
                await DownloadAttemptAsync(source, partPath, expectedSize, cancellationToken);
                Verify(partPath, expectedSize, expectedSha256);
                File.Move(partPath, finalPath, overwrite: true);
                return finalPath;
            }
            catch (InvalidDataException exception)
            {
                lastFailure = exception;
                DeleteIfExists(partPath);
                if (attempt == MaximumAttempts)
                {
                    break;
                }
            }
            catch (HttpRequestException exception)
            {
                lastFailure = exception;
                if (attempt == MaximumAttempts)
                {
                    break;
                }

                await Task.Delay(TimeSpan.FromMilliseconds(50 * attempt), cancellationToken);
            }
            catch (IOException exception)
            {
                lastFailure = exception;
                if (attempt == MaximumAttempts)
                {
                    break;
                }

                await Task.Delay(TimeSpan.FromMilliseconds(50 * attempt), cancellationToken);
            }
        }

        DeleteIfExists(finalPath);
        DeleteIfExists(partPath);
        throw new InvalidDataException($"Download failed verification after {MaximumAttempts} attempts: {source}", lastFailure);
    }

    private async Task DownloadAttemptAsync(Uri source, string partPath, long expectedSize, CancellationToken cancellationToken)
    {
        var existingLength = File.Exists(partPath) ? new FileInfo(partPath).Length : 0;
        using var request = new HttpRequestMessage(HttpMethod.Get, source);
        if (existingLength > 0)
        {
            request.Headers.Range = new RangeHeaderValue(existingLength, null);
        }

        using var response = await httpClient.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        if (response.StatusCode != HttpStatusCode.OK && response.StatusCode != HttpStatusCode.PartialContent)
        {
            throw new HttpRequestException($"Download returned {(int)response.StatusCode} {response.ReasonPhrase}");
        }

        var append = existingLength > 0 && response.StatusCode == HttpStatusCode.PartialContent;
        if (append && response.Content.Headers.ContentRange?.From is not null && response.Content.Headers.ContentRange.From != existingLength)
        {
            append = false;
        }

        await using var output = new FileStream(partPath, append ? FileMode.Append : FileMode.Create, FileAccess.Write, FileShare.None, 64 * 1024, useAsync: true);
        await using var input = await response.Content.ReadAsStreamAsync(cancellationToken);
        await input.CopyToAsync(output, cancellationToken);
        await output.FlushAsync(cancellationToken);

        if (new FileInfo(partPath).Length > expectedSize)
        {
            throw new InvalidDataException("Downloaded bytes exceed expected size");
        }
    }

    private static void Verify(string path, long expectedSize, string expectedSha256)
    {
        var info = new FileInfo(path);
        if (!info.Exists || info.Length != expectedSize)
        {
            throw new InvalidDataException($"Downloaded size {info.Length} does not match expected size {expectedSize}");
        }

        using var stream = File.OpenRead(path);
        var actual = Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
        if (!string.Equals(actual, expectedSha256, StringComparison.Ordinal))
        {
            throw new InvalidDataException("Downloaded SHA-256 does not match the manifest");
        }
    }

    private static void DeleteIfExists(string path)
    {
        if (File.Exists(path))
        {
            File.Delete(path);
        }
    }
}
