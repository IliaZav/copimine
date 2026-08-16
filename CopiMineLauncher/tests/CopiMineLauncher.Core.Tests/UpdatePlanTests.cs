using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Core.Updates;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Core.Tests;

public sealed class UpdatePlanTests
{
    [Fact]
    public void Clean_install_adds_every_official_file()
    {
        var manifest = FixtureManifest("a", "b", "c");

        var plan = OwnershipPolicy.BuildPlan(manifest, ManagedState.Empty, _ => LocalFileSnapshot.Missing);

        plan.Operations.Should().HaveCount(3);
        plan.Operations.Should().OnlyContain(operation => operation.Kind == UpdateOperationKind.Add);
        plan.NextState.Files.Select(file => file.ComponentId).Should().BeEquivalentTo(new[] { "a", "b", "c" });
    }

    [Fact]
    public void Unknown_user_files_are_not_part_of_the_plan()
    {
        var manifest = FixtureManifest("a", "b", "c");
        var previous = StateFor(manifest);

        var plan = OwnershipPolicy.BuildPlan(manifest, previous, path => path == "mods/a.jar"
            ? new LocalFileSnapshot(true, 1, Hash('a'))
            : new LocalFileSnapshot(true, 1, Hash(path[5])));

        plan.Operations.Should().BeEmpty();
        plan.NextState.Files.Should().HaveCount(3);
    }

    [Fact]
    public void Component_filename_change_removes_only_old_managed_path_and_adds_new_path()
    {
        var old = Entry("copimine-client", "mods/CopiMineClient-1.0.0.jar", 'a');
        var updated = Entry("copimine-client", "mods/CopiMineClient-1.1.0.jar", 'b');
        var manifest = FixtureManifest(updated);
        var previous = new ManagedState(1, new[] { new ManagedFileRecord("copimine-client", old.Path, old.Sha256, old.Version) });

        var plan = OwnershipPolicy.BuildPlan(manifest, previous, path => path == old.Path
            ? new LocalFileSnapshot(true, 1, old.Sha256)
            : LocalFileSnapshot.Missing);

        plan.Operations.Should().Contain(operation => operation.Kind == UpdateOperationKind.Remove && operation.RelativePath == old.Path);
        plan.Operations.Should().Contain(operation => operation.Kind == UpdateOperationKind.Add && operation.RelativePath == updated.Path);
    }

    [Fact]
    public void Removed_official_component_is_removed_only_when_previous_state_owns_it()
    {
        var manifest = FixtureManifest("a");
        var previous = StateFor(FixtureManifest("a", "obsolete"));

        var plan = OwnershipPolicy.BuildPlan(manifest, previous, _ => new LocalFileSnapshot(true, 1, Hash('a')));

        plan.Operations.Should().ContainSingle(operation => operation.Kind == UpdateOperationKind.Remove && operation.ComponentId == "obsolete");
    }

    [Fact]
    public void Unknown_file_at_official_path_creates_conflict_instead_of_silent_overwrite()
    {
        var manifest = FixtureManifest("a");
        var unknownBytes = new LocalFileSnapshot(true, 10, new string('f', 64));

        var plan = OwnershipPolicy.BuildPlan(manifest, ManagedState.Empty, _ => unknownBytes);

        plan.HasConflicts.Should().BeTrue();
        plan.Operations.Should().ContainSingle(operation => operation.Kind == UpdateOperationKind.Conflict);
    }

    [Fact]
    public void Unknown_file_with_matching_hash_is_still_not_adopted_or_deleted()
    {
        var manifest = FixtureManifest("a");
        var expectedHash = manifest.Files[0].Sha256;

        var plan = OwnershipPolicy.BuildPlan(
            manifest,
            ManagedState.Empty,
            _ => new LocalFileSnapshot(true, 1, expectedHash));

        plan.HasConflicts.Should().BeTrue();
        plan.Operations.Should().ContainSingle(operation => operation.Kind == UpdateOperationKind.Conflict);
        plan.NextState.Files.Should().BeEmpty();
    }

    [Fact]
    public void Merge_entries_fail_closed_instead_of_being_replaced_as_raw_files()
    {
        var entry = Entry("config", "config/copimine.json", 'a') with { Ownership = "merge" };
        var plan = OwnershipPolicy.BuildPlan(FixtureManifest(entry), ManagedState.Empty, _ => LocalFileSnapshot.Missing);

        plan.HasConflicts.Should().BeTrue();
        plan.Operations.Should().ContainSingle(operation =>
            operation.Kind == UpdateOperationKind.Conflict
            && operation.Reason == "merge-policy-unsupported");
        plan.NextState.Files.Should().BeEmpty();
    }

    private static ManagedState StateFor(LauncherManifest manifest) => new(
        manifest.Sequence - 1,
        manifest.Files.Select(entry => new ManagedFileRecord(entry.ComponentId, entry.Path, entry.Sha256, entry.Version)).ToArray());

    private static LauncherManifest FixtureManifest(params string[] componentIds) => FixtureManifest(componentIds.Select(id => Entry(id, $"mods/{id}.jar", id[0])).ToArray());

    private static LauncherManifest FixtureManifest(params ManifestFileEntry[] entries) => new(
        1, "CopiMineLauncher", "stable", 2, "1.0.0", "1.21.1", "0.19.3",
        DateTimeOffset.UtcNow, null,
        new JavaRuntimeMetadata("21", "https://copimine.ru/java.zip", 1, new string('j', 64)),
        entries, new ManifestServer("mc.copimine.ru", 25565, "CopiMine"), "test");

    private static ManifestFileEntry Entry(string id, string path, char hashMarker) => new(
        id, path, "mod", "1.0.0", $"https://copimine.ru/{id}.jar", 1, Hash(hashMarker), true, "managed");

    private static string Hash(char marker) => new(marker, 64);
}
