using System.IO.Compression;
using System.Text;
using CopiMineLauncher.Infrastructure.Servers;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class ServersDatServiceTests
{
    private static readonly ManagedServerRecord CopiMine = new("CopiMine", "mc.copimine.ru", 25565);

    [Fact]
    public async Task Missing_servers_dat_gets_one_copimine_server()
    {
        using var temp = new TemporaryDirectory();
        var path = Path.Combine(temp.Path, "servers.dat");

        var evidence = await new ServersDatService().EnsureCopiMineServerAsync(path, CopiMine);

        evidence.Changed.Should().BeTrue();
        CountText(Decompress(path), "mc.copimine.ru:25565").Should().Be(1);
        CountText(Decompress(path), "CopiMine").Should().Be(1);
        CountText(Decompress(path), "acceptTextures").Should().Be(1);
    }

    [Fact]
    public async Task Duplicate_copimine_entries_are_collapsed_and_third_party_server_is_preserved()
    {
        using var temp = new TemporaryDirectory();
        var path = Path.Combine(temp.Path, "servers.dat");
        await File.WriteAllBytesAsync(path, BuildServers(
            ("Other", "other.example:25565", null),
            ("CopiMine", "mc.copimine.ru:25565", 42),
            ("CopiMine", "mc.copimine.ru:25565", 99)));

        await new ServersDatService().EnsureCopiMineServerAsync(path, CopiMine);
        var output = Decompress(path);

        CountText(output, "mc.copimine.ru:25565").Should().Be(1);
        CountText(output, "other.example:25565").Should().Be(1);
        CountText(output, "custom").Should().Be(1);
    }

    [Fact]
    public async Task Existing_unknown_tags_are_preserved_and_operation_is_idempotent()
    {
        using var temp = new TemporaryDirectory();
        var path = Path.Combine(temp.Path, "servers.dat");
        await File.WriteAllBytesAsync(path, BuildServers(("Other", "other.example:25565", 42)));

        var service = new ServersDatService();
        await service.EnsureCopiMineServerAsync(path, CopiMine);
        var first = await File.ReadAllBytesAsync(path);
        var evidence = await service.EnsureCopiMineServerAsync(path, CopiMine);
        var second = await File.ReadAllBytesAsync(path);

        evidence.Changed.Should().BeFalse();
        second.Should().Equal(first);
        CountText(Decompress(second), "other.example:25565").Should().Be(1);
        CountText(Decompress(second), "custom").Should().Be(1);
    }

    [Fact]
    public async Task Corrupt_servers_dat_is_rejected_without_overwrite()
    {
        using var temp = new TemporaryDirectory();
        var path = Path.Combine(temp.Path, "servers.dat");
        var corrupt = new byte[] { 1, 2, 3, 4 };
        await File.WriteAllBytesAsync(path, corrupt);

        var action = () => new ServersDatService().EnsureCopiMineServerAsync(path, CopiMine);

        await action.Should().ThrowAsync<InvalidDataException>();
        (await File.ReadAllBytesAsync(path)).Should().Equal(corrupt);
    }

    [Fact]
    public async Task Uncompressed_minecraft_servers_dat_is_updated_in_place()
    {
        using var temp = new TemporaryDirectory();
        var path = Path.Combine(temp.Path, "servers.dat");
        await File.WriteAllBytesAsync(path, BuildRawServers(("Other", "other.example:25565", 42)));

        var evidence = await new ServersDatService().EnsureCopiMineServerAsync(path, CopiMine);

        evidence.Changed.Should().BeTrue();
        var output = await File.ReadAllBytesAsync(path);
        output[0].Should().Be(10);
        CountText(output, "mc.copimine.ru:25565").Should().Be(1);
        CountText(output, "other.example:25565").Should().Be(1);
        CountText(output, "acceptTextures").Should().Be(1);
    }

    [Fact]
    public async Task Same_display_name_on_another_address_is_preserved_as_a_user_server()
    {
        using var temp = new TemporaryDirectory();
        var path = Path.Combine(temp.Path, "servers.dat");
        await File.WriteAllBytesAsync(path, BuildServers(
            ("CopiMine", "community.example:25565", 7)));

        await new ServersDatService().EnsureCopiMineServerAsync(path, CopiMine);
        var output = Decompress(path);

        CountText(output, "community.example:25565").Should().Be(1);
        CountText(output, "mc.copimine.ru:25565").Should().Be(1);
    }

    [Fact]
    public async Task Trailing_nbt_bytes_are_rejected_without_overwrite()
    {
        using var temp = new TemporaryDirectory();
        var path = Path.Combine(temp.Path, "servers.dat");
        var original = BuildRawServers(("Other", "other.example:25565", 42)).Concat(new byte[] { 0x7F }).ToArray();
        await File.WriteAllBytesAsync(path, original);

        var action = () => new ServersDatService().EnsureCopiMineServerAsync(path, CopiMine);

        await action.Should().ThrowAsync<InvalidDataException>();
        (await File.ReadAllBytesAsync(path)).Should().Equal(original);
    }

    private static byte[] Decompress(string path) => Decompress(File.ReadAllBytes(path));

    private static byte[] Decompress(byte[] bytes)
    {
        using var input = new MemoryStream(bytes);
        using var gzip = new GZipStream(input, CompressionMode.Decompress);
        using var output = new MemoryStream();
        gzip.CopyTo(output);
        return output.ToArray();
    }

    private static int CountText(byte[] bytes, string value)
    {
        var text = Encoding.UTF8.GetString(bytes);
        return Enumerable.Range(0, Math.Max(0, text.Length - value.Length + 1))
            .Count(index => text.AsSpan(index, value.Length).SequenceEqual(value));
    }

    private static byte[] BuildServers(params (string Name, string Ip, int? Custom)[] servers)
    {
        using var raw = new MemoryStream();
        WriteServers(raw, servers);

        using var compressed = new MemoryStream();
        using (var gzip = new GZipStream(compressed, CompressionLevel.SmallestSize, leaveOpen: true))
        {
            raw.Position = 0;
            raw.CopyTo(gzip);
        }

        return compressed.ToArray();
    }

    private static byte[] BuildRawServers(params (string Name, string Ip, int? Custom)[] servers)
    {
        using var raw = new MemoryStream();
        WriteServers(raw, servers);
        return raw.ToArray();
    }

    private static void WriteServers(Stream output, params (string Name, string Ip, int? Custom)[] servers)
    {
        using var raw = new MemoryStream();
        using (var writer = new BinaryWriter(raw, Encoding.UTF8, leaveOpen: true))
        {
            writer.Write((byte)10);
            WriteString(writer, string.Empty);
            writer.Write((byte)9);
            WriteString(writer, "servers");
            writer.Write((byte)10);
            WriteInt(writer, servers.Length);
            foreach (var server in servers)
            {
                writer.Write((byte)8);
                WriteString(writer, "name");
                WriteString(writer, server.Name);
                writer.Write((byte)8);
                WriteString(writer, "ip");
                WriteString(writer, server.Ip);
                if (server.Custom is not null)
                {
                    writer.Write((byte)3);
                    WriteString(writer, "custom");
                    WriteInt(writer, server.Custom.Value);
                }

                writer.Write((byte)0);
            }

            writer.Write((byte)0);
        }
        raw.Position = 0;
        raw.CopyTo(output);
    }

    private static void WriteString(BinaryWriter writer, string value)
    {
        var bytes = Encoding.UTF8.GetBytes(value);
        WriteUShort(writer, bytes.Length);
        writer.Write(bytes);
    }

    private static void WriteUShort(BinaryWriter writer, int value)
    {
        writer.Write((byte)(value >> 8));
        writer.Write((byte)value);
    }

    private static void WriteInt(BinaryWriter writer, int value)
    {
        writer.Write((byte)(value >> 24));
        writer.Write((byte)(value >> 16));
        writer.Write((byte)(value >> 8));
        writer.Write((byte)value);
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-servers-tests-").FullName;

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
