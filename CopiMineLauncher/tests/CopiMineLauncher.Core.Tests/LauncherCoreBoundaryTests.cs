using CopiMineLauncher.Core.Launch;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Core.News;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Core.Tests;

public sealed class LauncherCoreBoundaryTests
{
    private static readonly DateTimeOffset Now = DateTimeOffset.Parse("2026-08-15T12:00:00Z");

    [Fact]
    public void Manifest_rejects_expired_and_far_future_issue_times()
    {
        var manifest = ValidManifest() with
        {
            IssuedAtUtc = Now.AddMinutes(5).AddSeconds(1),
            ExpiresAtUtc = Now.AddSeconds(-1)
        };

        var result = new ManifestValidator().Validate(manifest, Now);

        result.ErrorCodes.Should().Contain(new[] { "MANIFEST_ISSUED_IN_FUTURE", "MANIFEST_EXPIRED" });
    }

    [Fact]
    public void Manifest_rejects_a_file_that_exceeds_the_download_bound()
    {
        var manifest = ValidManifest() with
        {
            Files = new[] { ValidManifest().Files[0] with { SizeBytes = 4L * 1024 * 1024 * 1024 + 1 } }
        };

        var result = new ManifestValidator().Validate(manifest, Now);

        result.ErrorCodes.Should().Contain("FILE_SIZE_INVALID");
    }

    [Fact]
    public void Instance_manifest_rejects_news_links_outside_the_public_news_section()
    {
        var document = ValidInstanceDocument() with
        {
            NewsUrl = "https://copimine.ru/cabinet/index.html"
        };

        var result = new InstanceManifestValidator().Validate(document, Now, signaturePublicKeyId: "launcher-v1");

        result.ErrorCodes.Should().Contain("NEWS_URL_INVALID");
    }

    [Fact]
    public void Patch_feed_orders_newest_items_and_caps_launcher_cards_at_three()
    {
        var result = PatchFeedParser.Parse("""
        {
          "schemaVersion": 1,
          "patches": [
            {"id":"old","version":"1.0.0","title":"Old","publishedAt":"2026-08-11T12:00:00Z","summary":["Old"],"detailUrl":"/news/old.html"},
            {"id":"new-1","version":"1.0.0","title":"New 1","publishedAt":"2026-08-15T12:00:00Z","summary":["One"],"detailUrl":"/news/new-1.html"},
            {"id":"new-3","version":"1.0.0","title":"New 3","publishedAt":"2026-08-14T12:00:00Z","summary":["Three"],"detailUrl":"/news/new-3.html"},
            {"id":"new-2","version":"1.0.0","title":"New 2","publishedAt":"2026-08-14T12:00:00Z","summary":["Two"],"detailUrl":"/news/new-2.html"},
            {"id":"newest","version":"1.0.0","title":"Newest","publishedAt":"2026-08-16T12:00:00Z","summary":["Newest"],"detailUrl":"/news/newest.html"}
          ]
        }
        """);

        result.IsDocumentValid.Should().BeTrue(result.ToString());
        result.Items.Select(item => item.Id).Should().Equal("newest", "new-1", "new-3");
    }

    [Fact]
    public void Patch_feed_reports_duplicate_ids_and_rejects_thumbnail_traversal()
    {
        var result = PatchFeedParser.Parse("""
        {
          "schemaVersion": 1,
          "patches": [
            {"id":"same","version":"1.0.0","title":"One","publishedAt":"2026-08-15T12:00:00Z","summary":["One"],"detailUrl":"/news/one.html","thumbnailUrl":"/assets/../secret.png"},
            {"id":"same","version":"1.0.0","title":"Two","publishedAt":"2026-08-15T11:00:00Z","summary":["Two"],"detailUrl":"/news/two.html"}
          ]
        }
        """);

        result.IsDocumentValid.Should().BeTrue();
        result.Items.Should().BeEmpty();
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Contains("THUMBNAIL_URL_NOT_ALLOWED", StringComparison.Ordinal));
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Contains("DUPLICATE_ID", StringComparison.Ordinal));
    }

    [Fact]
    public void Failure_parser_classifies_java_memory_failures_without_blame_on_a_mod()
    {
        var report = MinecraftLaunchFailureParser.Parse(
            "[main/ERROR] java.lang.OutOfMemoryError: Could not reserve enough space for object heap",
            "C:\\CopiMine\\logs\\launcher-process.log",
            new[] { "ExtraVisuals-1.0.jar" });

        report.Kind.Should().Be(MinecraftLaunchFailureKind.JavaRuntime);
        report.IsModRelated.Should().BeFalse();
        report.IsLikelyUserMod.Should().BeFalse();
        report.Explanation.Should().Contain("Java");
    }

    [Fact]
    public void Failure_parser_finds_a_user_mixin_mod_and_keeps_evidence_bounded()
    {
        var longLine = new string('x', 500);
        var report = MinecraftLaunchFailureParser.Parse(
            $"[main/ERROR] MixinApplyError: mod 'extra-render' failed\n[main/ERROR] {longLine}",
            "logs/launcher-process.log",
            new[] { "ExtraRender-1.0.jar" });

        report.Kind.Should().Be(MinecraftLaunchFailureKind.Mixin);
        report.SuspectedModId.Should().Be("extra-render");
        report.SuspectedModFileName.Should().Be("ExtraRender-1.0.jar");
        report.IsLikelyUserMod.Should().BeTrue();
        report.Evidence.Should().OnlyContain(line => line.Length <= 360);
    }

    [Fact]
    public void Failure_parser_returns_a_clear_fallback_when_the_log_has_no_diagnostic_line()
    {
        var report = MinecraftLaunchFailureParser.Parse("Minecraft stopped", "logs/launcher-process.log");

        report.Kind.Should().Be(MinecraftLaunchFailureKind.Unknown);
        report.Evidence.Should().ContainSingle().Which.Should().Contain("нет строки");
        report.Summary.Should().NotBeNullOrWhiteSpace();
    }

    private static LauncherManifest ValidManifest() => new(
        1,
        "CopiMineLauncher",
        "stable",
        9,
        "1.0.0",
        "1.21.1",
        "0.19.3",
        Now.AddMinutes(-1),
        null,
        new JavaRuntimeMetadata("21", "https://copimine.ru/downloads/java.zip", 10, new string('a', 64)),
        new[]
        {
            new ManifestFileEntry(
                "copimine-client",
                "mods/CopiMineClient.jar",
                "mod",
                "1.0.0",
                "https://copimine.ru/downloads/CopiMineClient.jar",
                123,
                new string('b', 64),
                true,
                "managed")
        },
        new ManifestServer("mc.copimine.ru", 25565, "CopiMine"),
        "launcher-v1",
        new MinecraftRuntimeMetadata("https://copimine.ru/launcher/files/runtime", 789, new string('d', 64)));

    private static InstanceManifestDocument ValidInstanceDocument() => new(
        2,
        "stable",
        "2026.08.15.1",
        Now.AddMinutes(-1),
        "1.0.0",
        new InstanceMinecraft("1.21.1", "0.19.3", 21),
        new InstanceManifestServer("CopiMine", "mc.copimine.ru", true),
        new[]
        {
            new InstanceManifestFile(
                "copimine-client",
                "mods/CopiMineClient.jar",
                "https://copimine.ru/launcher/files/client",
                new string('b', 64),
                123,
                "MANAGED",
                true,
                "mod",
                "1.4.0",
                "REPLACE")
        },
        Array.Empty<InstanceConfigPolicy>(),
        "https://copimine.ru/news.html",
        17,
        new InstanceJavaRuntime("Adoptium", "temurin-21", "windows-x64", "21.0.10", "https://copimine.ru/launcher/files/java", 456, new string('c', 64)),
        "launcher-v1",
        new InstanceMinecraftRuntime("https://copimine.ru/launcher/files/runtime", 789, new string('d', 64)));
}
