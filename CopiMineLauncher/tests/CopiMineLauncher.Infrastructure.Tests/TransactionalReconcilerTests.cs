using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Core.Updates;
using CopiMineLauncher.Infrastructure.Updates;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class TransactionalReconcilerTests
{
    [Fact]
    public async Task Untrusted_manifest_is_rejected_before_any_mutation()
    {
        using var temp = new TemporaryDirectory();
        var downloader = new FixtureDownloader();
        var reconciler = CreateReconciler(temp.Path, trusted: false, downloader);

        var result = await reconciler.ReconcileAsync(Manifest(Entry("a", "mods/a.jar", "a")), CancellationToken.None);

        result.Status.Should().Be(ReconciliationStatus.Failed);
        result.ErrorCode.Should().Be("MANIFEST_NOT_TRUSTED");
        File.Exists(Path.Combine(temp.Path, "mods", "a.jar")).Should().BeFalse();
        downloader.Calls.Should().BeEmpty();
    }

    [Fact]
    public async Task Clean_install_update_and_user_extra_preservation_are_transactional()
    {
        using var temp = new TemporaryDirectory();
        var firstEntries = new[] { Entry("a", "mods/a.jar", "A"), Entry("b", "mods/b.jar", "B") };
        var secondEntries = new[] { Entry("a", "mods/a.jar", "A2"), Entry("c", "mods/c.jar", "C") };
        var downloader = new FixtureDownloader(firstEntries.Concat(secondEntries));
        var reconciler = CreateReconciler(temp.Path, trusted: true, downloader);

        (await reconciler.ReconcileAsync(Manifest(firstEntries, sequence: 1), CancellationToken.None)).Status
            .Should().Be(ReconciliationStatus.Updated);
        var userPath = Path.Combine(temp.Path, "mods", "sodium.jar");
        Directory.CreateDirectory(Path.GetDirectoryName(userPath)!);
        var userBytes = Encoding.UTF8.GetBytes("user-owned-mod");
        await File.WriteAllBytesAsync(userPath, userBytes);

        var update = await reconciler.ReconcileAsync(Manifest(secondEntries, sequence: 2), CancellationToken.None);

        update.Status.Should().Be(ReconciliationStatus.Updated);
        File.ReadAllText(Path.Combine(temp.Path, "mods", "a.jar")).Should().Be("A2");
        File.Exists(Path.Combine(temp.Path, "mods", "b.jar")).Should().BeFalse();
        File.ReadAllText(Path.Combine(temp.Path, "mods", "c.jar")).Should().Be("C");
        File.ReadAllBytes(userPath).Should().Equal(userBytes);

        var state = await new AtomicFileStore(temp.Path).LoadStateAsync(CancellationToken.None);
        state.ManifestSequence.Should().Be(2);
        state.Files.Select(file => file.ComponentId).Should().BeEquivalentTo(new[] { "a", "c" });
    }

    [Fact]
    public async Task Corrupt_or_failed_download_keeps_previous_version_and_state()
    {
        using var temp = new TemporaryDirectory();
        var oldEntry = Entry("a", "mods/a.jar", "old");
        var newEntry = Entry("a", "mods/a.jar", "new");
        var downloader = new FixtureDownloader(new[] { oldEntry, newEntry });
        var reconciler = CreateReconciler(temp.Path, trusted: true, downloader);
        await reconciler.ReconcileAsync(Manifest(new[] { oldEntry }, 1), CancellationToken.None);
        downloader.FailNext = true;

        var result = await reconciler.ReconcileAsync(Manifest(new[] { newEntry }, 2), CancellationToken.None);

        result.Status.Should().Be(ReconciliationStatus.Failed);
        File.ReadAllText(Path.Combine(temp.Path, "mods", "a.jar")).Should().Be("old");
        (await new AtomicFileStore(temp.Path).LoadStateAsync(CancellationToken.None)).ManifestSequence.Should().Be(1);
        Directory.GetFiles(Path.Combine(temp.Path, ".copimine"), "*.part", SearchOption.AllDirectories).Should().BeEmpty();
    }

    [Fact]
    public async Task Older_signed_sequence_is_rejected_before_download_or_mutation()
    {
        using var temp = new TemporaryDirectory();
        var entry = Entry("a", "mods/a.jar", "same");
        var downloader = new FixtureDownloader(new[] { entry });
        var reconciler = CreateReconciler(temp.Path, trusted: true, downloader);

        (await reconciler.ReconcileAsync(Manifest(new[] { entry }, sequence: 2), CancellationToken.None)).IsSuccess.Should().BeTrue();
        var result = await reconciler.ReconcileAsync(Manifest(new[] { entry }, sequence: 1), CancellationToken.None);

        result.ErrorCode.Should().Be("MANIFEST_SEQUENCE_ROLLBACK");
        downloader.Calls.Should().ContainSingle();
        File.ReadAllText(Path.Combine(temp.Path, "mods", "a.jar")).Should().Be("same");
    }

    [Fact]
    public async Task Injected_commit_failure_rolls_back_the_file_and_removes_journal()
    {
        using var temp = new TemporaryDirectory();
        var staged = Path.Combine(temp.Path, ".copimine", "staging", "tx", "mods", "a.jar");
        Directory.CreateDirectory(Path.GetDirectoryName(staged)!);
        await File.WriteAllTextAsync(staged, "new");
        var entry = Entry("a", "mods/a.jar", "new");
        var operation = new UpdateOperation(UpdateOperationKind.Add, "a", entry.Path, null, entry.Sha256, entry, null, staged);
        var journal = new TransactionJournal("tx", 1, TransactionPhase.Prepared, DateTimeOffset.UtcNow, ManagedState.Empty,
            new[] { new TransactionJournalEntry(UpdateOperationKind.Add, "a", entry.Path, staged, null, null, entry.Sha256) });
        var store = new AtomicFileStore(temp.Path, _ => true);

        var action = () => store.CommitAsync(new[] { operation }, journal, new ManagedState(1, new[] { new ManagedFileRecord("a", entry.Path, entry.Sha256, entry.Version) }), CancellationToken.None);

        await action.Should().ThrowAsync<IOException>();
        File.Exists(Path.Combine(temp.Path, "mods", "a.jar")).Should().BeFalse();
        File.Exists(Path.Combine(temp.Path, ".copimine", "update-journal.json")).Should().BeFalse();
    }

    [Fact]
    public async Task Recovery_restores_backup_and_previous_state_from_interrupted_journal()
    {
        using var temp = new TemporaryDirectory();
        var target = Path.Combine(temp.Path, "mods", "a.jar");
        var backup = Path.Combine(temp.Path, ".copimine", "backups", "tx", "mods", "a.jar");
        Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        Directory.CreateDirectory(Path.GetDirectoryName(backup)!);
        await File.WriteAllTextAsync(target, "new");
        await File.WriteAllTextAsync(backup, "old");
        var oldEntry = Entry("a", "mods/a.jar", "old");
        var previousState = new ManagedState(1, new[] { new ManagedFileRecord("a", oldEntry.Path, oldEntry.Sha256, oldEntry.Version) });
        var journal = new TransactionJournal("tx", 2, TransactionPhase.Committing, DateTimeOffset.UtcNow, previousState,
            new[] { new TransactionJournalEntry(UpdateOperationKind.Replace, "a", oldEntry.Path, null, backup, oldEntry.Sha256, Hash("new")) });
        Directory.CreateDirectory(Path.Combine(temp.Path, ".copimine"));
        await File.WriteAllTextAsync(Path.Combine(temp.Path, ".copimine", "update-journal.json"), JsonSerializer.Serialize(journal));

        var recovered = await new AtomicFileStore(temp.Path).RecoverAsync(CancellationToken.None);

        recovered.Should().BeTrue();
        File.ReadAllText(target).Should().Be("old");
        (await new AtomicFileStore(temp.Path).LoadStateAsync(CancellationToken.None)).ManifestSequence.Should().Be(1);
        File.Exists(Path.Combine(temp.Path, ".copimine", "update-journal.json")).Should().BeFalse();
    }

    [Fact]
    public async Task Recovery_rejects_a_backup_path_outside_the_transaction_directory()
    {
        using var temp = new TemporaryDirectory();
        var outsidePath = Path.Combine(Path.GetTempPath(), $"copimine-journal-outside-{Guid.NewGuid():N}.bin");
        await File.WriteAllTextAsync(outsidePath, "must remain untouched");
        try
        {
            var entry = Entry("a", "mods/a.jar", "old");
            var journal = new TransactionJournal(
                "tx",
                2,
                TransactionPhase.Committing,
                DateTimeOffset.UtcNow,
                new ManagedState(1, Array.Empty<ManagedFileRecord>()),
                new[]
                {
                    new TransactionJournalEntry(
                        UpdateOperationKind.Replace,
                        "a",
                        entry.Path,
                        null,
                        outsidePath,
                        entry.Sha256,
                        Hash("new"))
                });
            var metadataRoot = Path.Combine(temp.Path, ".copimine");
            Directory.CreateDirectory(metadataRoot);
            await File.WriteAllTextAsync(
                Path.Combine(metadataRoot, "update-journal.json"),
                JsonSerializer.Serialize(journal));

            var action = () => new AtomicFileStore(temp.Path).RecoverAsync(CancellationToken.None);

            await action.Should().ThrowAsync<InvalidDataException>().WithMessage("*journal*path*");
            File.ReadAllText(outsidePath).Should().Be("must remain untouched");
            File.Exists(Path.Combine(metadataRoot, "update-journal.json")).Should().BeTrue();
        }
        finally
        {
            File.Delete(outsidePath);
        }
    }

    [Fact]
    public async Task Commit_rejects_a_staged_path_outside_the_transaction_directory()
    {
        using var temp = new TemporaryDirectory();
        var outsidePath = Path.Combine(Path.GetTempPath(), $"copimine-staged-outside-{Guid.NewGuid():N}.bin");
        await File.WriteAllTextAsync(outsidePath, "must remain staged");
        try
        {
            var entry = Entry("a", "mods/a.jar", "new");
            var operation = new UpdateOperation(
                UpdateOperationKind.Add,
                "a",
                entry.Path,
                null,
                entry.Sha256,
                entry,
                null,
                outsidePath);
            var journal = new TransactionJournal(
                "tx",
                1,
                TransactionPhase.Prepared,
                DateTimeOffset.UtcNow,
                ManagedState.Empty,
                new[]
                {
                    new TransactionJournalEntry(
                        UpdateOperationKind.Add,
                        "a",
                        entry.Path,
                        outsidePath,
                        null,
                        null,
                        entry.Sha256)
                });

            var action = () => new AtomicFileStore(temp.Path).CommitAsync(
                new[] { operation },
                journal,
                new ManagedState(1, new[] { new ManagedFileRecord("a", entry.Path, entry.Sha256, entry.Version) }),
                CancellationToken.None);

            await action.Should().ThrowAsync<InvalidDataException>().WithMessage("*journal*path*");
            File.ReadAllText(outsidePath).Should().Be("must remain staged");
            File.Exists(Path.Combine(temp.Path, "mods", "a.jar")).Should().BeFalse();
        }
        finally
        {
            File.Delete(outsidePath);
        }
    }

    [Fact]
    public async Task Download_manager_resumes_a_partial_range_and_verifies_hash()
    {
        using var temp = new TemporaryDirectory();
        var destination = Path.Combine(temp.Path, "staging", "file.bin");
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        await File.WriteAllTextAsync(destination + ".part", "hello ");
        using var http = new HttpClient(new RangeHandler());
        var manager = new ResumableDownloadManager(http);
        var expected = Encoding.UTF8.GetBytes("hello world");

        var result = await manager.DownloadAsync(new Uri("https://copimine.ru/file.bin"), destination, expected.Length, Hash(expected), CancellationToken.None);

        result.Should().Be(destination);
        File.ReadAllText(destination).Should().Be("hello world");
        File.Exists(destination + ".part").Should().BeFalse();
    }

    [Fact]
    public async Task Durable_prepared_journal_exists_before_the_first_download()
    {
        using var temp = new TemporaryDirectory();
        var entry = Entry("a", "mods/a.jar", "A");
        var downloader = new FixtureDownloader(new[] { entry });
        var journalObserved = false;
        downloader.BeforeDownload = _ =>
        {
            journalObserved = true;
            File.Exists(Path.Combine(temp.Path, ".copimine", "update-journal.json")).Should().BeTrue();
        };

        var result = await CreateReconciler(temp.Path, trusted: true, downloader)
            .ReconcileAsync(Manifest(entry), CancellationToken.None);

        result.IsSuccess.Should().BeTrue();
        journalObserved.Should().BeTrue();
        File.Exists(Path.Combine(temp.Path, ".copimine", "update-journal.json")).Should().BeFalse();
    }

    [Fact]
    public async Task Managed_state_with_a_different_instance_identity_is_rejected()
    {
        using var temp = new TemporaryDirectory();
        var store = new AtomicFileStore(temp.Path);
        await store.LoadStateAsync(CancellationToken.None);
        var statePath = Path.Combine(temp.Path, ".copimine", "managed-state.json");
        await File.WriteAllTextAsync(
            statePath,
            "{\"manifestSequence\":1,\"files\":[],\"schemaVersion\":1,\"instanceId\":\"4d6f9f9f-5fd9-4e14-bdf4-9195279c7f27\"}");

        var action = () => store.LoadStateAsync(CancellationToken.None);

        await action.Should().ThrowAsync<InvalidDataException>()
            .WithMessage("*not trusted*");
    }

    [Fact]
    public async Task Network_disconnect_keeps_partial_staging_and_resumes_without_touching_final_file()
    {
        using var temp = new TemporaryDirectory();
        var destination = Path.Combine(temp.Path, "staging", "file.bin");
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        await File.WriteAllTextAsync(destination, "previous");
        using var http = new HttpClient(new DisconnectingHandler());
        var manager = new ResumableDownloadManager(http);
        var expected = Encoding.UTF8.GetBytes("hello world");

        var result = await manager.DownloadAsync(new Uri("https://copimine.ru/file.bin"), destination, expected.Length, Hash(expected), CancellationToken.None);

        result.Should().Be(destination);
        File.ReadAllText(destination).Should().Be("hello world");
        File.Exists(destination + ".part").Should().BeFalse();
    }

    [Fact]
    public async Task Download_manager_never_promotes_corrupt_bytes()
    {
        using var temp = new TemporaryDirectory();
        var destination = Path.Combine(temp.Path, "staging", "file.bin");
        using var http = new HttpClient(new CorruptHandler());
        var manager = new ResumableDownloadManager(http);

        var action = () => manager.DownloadAsync(new Uri("https://copimine.ru/file.bin"), destination, 5, Hash(Encoding.UTF8.GetBytes("right")), CancellationToken.None);

        await action.Should().ThrowAsync<InvalidDataException>();
        File.Exists(destination).Should().BeFalse();
        File.Exists(destination + ".part").Should().BeFalse();
    }

    [Fact]
    public async Task Download_manager_discards_an_oversized_partial_before_resuming()
    {
        using var temp = new TemporaryDirectory();
        var destination = Path.Combine(temp.Path, "staging", "file.bin");
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        await File.WriteAllTextAsync(destination + ".part", "stale bytes that are too long");
        using var handler = new FreshFileHandler("right");
        using var http = new HttpClient(handler);
        var manager = new ResumableDownloadManager(http);
        var expected = Encoding.UTF8.GetBytes("right");

        var result = await manager.DownloadAsync(new Uri("https://copimine.ru/file.bin"), destination, expected.Length, Hash(expected), CancellationToken.None);

        result.Should().Be(destination);
        File.ReadAllText(destination).Should().Be("right");
        handler.Requests.Should().ContainSingle().Which.Headers.Range.Should().BeNull();
    }

    [Fact]
    public async Task Download_manager_restarts_when_a_full_partial_has_the_wrong_hash()
    {
        using var temp = new TemporaryDirectory();
        var destination = Path.Combine(temp.Path, "staging", "file.bin");
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        await File.WriteAllTextAsync(destination + ".part", "wrong");
        using var handler = new FreshFileHandler("right");
        using var http = new HttpClient(handler);
        var manager = new ResumableDownloadManager(http);
        var expected = Encoding.UTF8.GetBytes("right");

        var result = await manager.DownloadAsync(new Uri("https://copimine.ru/file.bin"), destination, expected.Length, Hash(expected), CancellationToken.None);

        result.Should().Be(destination);
        File.ReadAllText(destination).Should().Be("right");
        handler.Requests.Should().ContainSingle().Which.Headers.Range.Should().BeNull();
    }

    private static TransactionalReconciler CreateReconciler(string root, bool trusted, FixtureDownloader downloader) =>
        new(root, new FixtureTrustGate(trusted), downloader, new AtomicFileStore(root));

    private static LauncherManifest Manifest(IEnumerable<ManifestFileEntry> entries, long sequence = 1) => new(
        1, "CopiMineLauncher", "stable", sequence, "1.0.0", "1.21.1", "0.19.3", DateTimeOffset.UtcNow, null,
        new JavaRuntimeMetadata("21", "https://copimine.ru/java.zip", 1, new string('j', 64)), entries.ToArray(),
        new ManifestServer("mc.copimine.ru", 25565, "CopiMine"), "test");

    private static LauncherManifest Manifest(ManifestFileEntry entry) => Manifest(new[] { entry });

    private static ManifestFileEntry Entry(string id, string path, string content) => new(
        id, path, "mod", content, $"https://copimine.ru/{id}.jar", Encoding.UTF8.GetByteCount(content), Hash(Encoding.UTF8.GetBytes(content)), true, "managed");

    private static string Hash(string content) => Hash(Encoding.UTF8.GetBytes(content));

    private static string Hash(byte[] bytes) => Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();

    private static string Hash(char marker) => new(marker, 64);

    private sealed class FixtureTrustGate(bool trusted) : IManifestTrustGate
    {
        public ValueTask<bool> IsTrustedAsync(LauncherManifest manifest, CancellationToken cancellationToken) => ValueTask.FromResult(trusted);
    }

    private sealed class FixtureDownloader : IResumableDownloadManager
    {
        private readonly Dictionary<string, Queue<byte[]>> contentByUrl;

        public FixtureDownloader(IEnumerable<ManifestFileEntry>? entries = null)
        {
            contentByUrl = new Dictionary<string, Queue<byte[]>>(StringComparer.Ordinal);
            foreach (var entry in entries ?? Array.Empty<ManifestFileEntry>())
            {
                if (!contentByUrl.TryGetValue(entry.Url, out var queue))
                {
                    queue = new Queue<byte[]>();
                    contentByUrl[entry.Url] = queue;
                }

                queue.Enqueue(Encoding.UTF8.GetBytes(entry.Version));
            }
        }

        public List<Uri> Calls { get; } = new();

        public Action<string>? BeforeDownload { get; set; }

        public bool FailNext { get; set; }

        public async Task<string> DownloadAsync(Uri source, string destination, long expectedSize, string expectedSha256, CancellationToken cancellationToken)
        {
            Calls.Add(source);
            BeforeDownload?.Invoke(destination);
            if (FailNext)
            {
                FailNext = false;
                throw new InvalidDataException("fixture download failure");
            }

            var bytes = contentByUrl[source.ToString()].Dequeue();
            Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            await File.WriteAllBytesAsync(destination, bytes, cancellationToken);
            return destination;
        }
    }

    private sealed class RangeHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            request.Headers.Range.Should().NotBeNull();
            request.Headers.Range!.Ranges.Single().From.Should().Be(6);
            var response = new HttpResponseMessage(HttpStatusCode.PartialContent)
            {
                Content = new ByteArrayContent(Encoding.UTF8.GetBytes("world"))
            };
            response.Content.Headers.ContentRange = new ContentRangeHeaderValue(6, 10, 11);
            return Task.FromResult(response);
        }
    }

    private sealed class CorruptHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(Encoding.UTF8.GetBytes("wrong")) });
    }

    private sealed class FreshFileHandler(string content) : HttpMessageHandler
    {
        public List<HttpRequestMessage> Requests { get; } = new();

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Requests.Add(request);
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new ByteArrayContent(Encoding.UTF8.GetBytes(content))
            });
        }
    }

    private sealed class DisconnectingHandler : HttpMessageHandler
    {
        private int calls;

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            calls++;
            if (calls == 1)
            {
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
                {
                    Content = new StreamContent(new ThrowingStream(Encoding.UTF8.GetBytes("hello ")))
                });
            }

            request.Headers.Range.Should().NotBeNull();
            request.Headers.Range!.Ranges.Single().From.Should().Be(6);
            var response = new HttpResponseMessage(HttpStatusCode.PartialContent)
            {
                Content = new ByteArrayContent(Encoding.UTF8.GetBytes("world"))
            };
            response.Content.Headers.ContentRange = new ContentRangeHeaderValue(6, 10, 11);
            return Task.FromResult(response);
        }
    }

    private sealed class ThrowingStream(byte[] bytes) : Stream
    {
        private int position;

        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => bytes.Length;
        public override long Position { get => position; set => throw new NotSupportedException(); }

        public override int Read(byte[] buffer, int offset, int count)
        {
            if (position > 0)
            {
                throw new IOException("simulated network disconnect");
            }

            var copied = Math.Min(count, bytes.Length);
            bytes.AsSpan(0, copied).CopyTo(buffer.AsSpan(offset, copied));
            position = copied;
            return copied;
        }

        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            if (position > 0)
            {
                return ValueTask.FromException<int>(new IOException("simulated network disconnect"));
            }

            var copied = Math.Min(buffer.Length, bytes.Length);
            bytes.AsSpan(0, copied).CopyTo(buffer.Span);
            position = copied;
            return ValueTask.FromResult(copied);
        }

        public override void Flush() => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-launcher-tests-").FullName;

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
