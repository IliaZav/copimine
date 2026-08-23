using System.Buffers.Binary;
using System.IO.Compression;
using System.Text;

namespace CopiMineLauncher.Infrastructure.Servers;

public sealed record ManagedServerRecord(string DisplayName, string Address, int Port, bool AcceptTextures = true);

public sealed record ServersDatEvidence(bool Changed, int ExistingServerCount, int CopiMineServerCount, string Path);

public interface IServersDatService
{
    Task<ServersDatEvidence> EnsureCopiMineServerAsync(string serversDatPath, ManagedServerRecord record, CancellationToken cancellationToken = default);
}

public sealed class ServersDatService : IServersDatService
{
    private const long MaxDecompressedBytes = 32L * 1024 * 1024;
    private const string ServersTag = "servers";
    private const string NameTag = "name";
    private const string IpTag = "ip";
    private const string AcceptTexturesTag = "acceptTextures";
    // Older launcher builds wrote this private tag. Minecraft 1.21.1 does
    // not tolerate it in servers.dat, so it is only used for migration and
    // is never emitted again.
    private const string CopiMineManagedTag = "copimineManaged";

    public async Task<ServersDatEvidence> EnsureCopiMineServerAsync(string serversDatPath, ManagedServerRecord record, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(record);
        var path = Path.GetFullPath(serversDatPath);
        var document = File.Exists(path)
            ? ReadDocument(await File.ReadAllBytesAsync(path, cancellationToken))
            : new ServersDocument(NewRoot(), Compressed: true);
        var root = document.Root;
        var servers = GetOrCreateServers(root);
        var existingCount = servers.Items.Count;
        var canonicalIp = CanonicalIp(record);
        var exactAddressMatches = servers.Items
            .OfType<CompoundTag>()
            .Where(server => string.Equals(GetString(server, IpTag), canonicalIp, StringComparison.OrdinalIgnoreCase))
            .ToList();
        var legacyManagedMatches = servers.Items
            .OfType<CompoundTag>()
            .Where(server => GetByte(server, CopiMineManagedTag) == 1)
            .ToList();
        var matches = legacyManagedMatches.Count > 0 ? legacyManagedMatches : exactAddressMatches;

        var changed = false;
        foreach (var legacyServer in legacyManagedMatches)
        {
            changed |= legacyServer.Values.Remove(CopiMineManagedTag);
        }

        CompoundTag managed;
        if (matches.Count == 0)
        {
            managed = new CompoundTag();
            servers.Items.Add(managed);
            changed = true;
        }
        else
        {
            managed = matches[0];
            foreach (var duplicate in matches.Skip(1))
            {
                servers.Items.Remove(duplicate);
                changed = true;
            }
        }

        changed |= SetString(managed, NameTag, record.DisplayName);
        changed |= SetString(managed, IpTag, canonicalIp);
        changed |= SetByte(managed, AcceptTexturesTag, record.AcceptTextures ? (byte)1 : (byte)0);

        // Minecraft 1.21.1 reads servers.dat as raw NBT. Older launcher
        // builds and some third-party launchers leave gzip-NBT here, so a
        // valid compressed file must be normalized even when entries match.
        changed |= document.Compressed;
        if (!changed)
        {
            return new(false, existingCount, matches.Count > 0 ? 1 : 0, path);
        }

        var bytes = WriteRoot(root, compressed: false, rootName: document.RootName);
        var tempPath = path + ".tmp";
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        await File.WriteAllBytesAsync(tempPath, bytes, cancellationToken);
        File.Move(tempPath, path, overwrite: true);
        return new(true, existingCount, 1, path);
    }

    private static string CanonicalIp(ManagedServerRecord record) => $"{record.Address}:{record.Port}";

    private static CompoundTag NewRoot()
    {
        var root = new CompoundTag();
        root.Values[ServersTag] = new ListTag(TagType.Compound, new List<Tag>());
        return root;
    }

    private static ListTag GetOrCreateServers(CompoundTag root)
    {
        if (!root.Values.TryGetValue(ServersTag, out var tag))
        {
            var created = new ListTag(TagType.Compound, new List<Tag>());
            root.Values[ServersTag] = created;
            return created;
        }

        if (tag is not ListTag { ElementType: TagType.Compound } servers)
        {
            throw new InvalidDataException("servers.dat contains an invalid servers list");
        }

        return servers;
    }

    private static string? GetString(CompoundTag compound, string key) => compound.Values.TryGetValue(key, out var value) && value is StringTag text
        ? text.Value
        : null;

    private static bool SetString(CompoundTag compound, string key, string value)
    {
        if (compound.Values.TryGetValue(key, out var existing) && existing is StringTag text && string.Equals(text.Value, value, StringComparison.Ordinal))
        {
            return false;
        }

        compound.Values[key] = new StringTag(value);
        return true;
    }

    private static bool SetByte(CompoundTag compound, string key, byte value)
    {
        if (compound.Values.TryGetValue(key, out var existing) && existing is ByteTag byteTag && byteTag.Value == value)
        {
            return false;
        }

        compound.Values[key] = new ByteTag(value);
        return true;
    }

    private static ServersDocument ReadDocument(byte[] bytes)
    {
        var isCompressed = IsGzip(bytes);
        try
        {
            using var input = new MemoryStream(bytes, writable: false);
            using Stream payload = isCompressed
                ? new GZipStream(input, CompressionMode.Decompress)
                : input;
            using var raw = new MemoryStream();
            CopyWithLimit(payload, raw, MaxDecompressedBytes);
            raw.Position = 0;
            using var reader = new BigEndianReader(raw);
            var type = (TagType)reader.ReadByteChecked();
            if (type != TagType.Compound)
            {
                throw new InvalidDataException("servers.dat root is not a compound");
            }

            var rootName = reader.ReadString();
            var parsedRoot = (CompoundTag)ReadPayload(reader, type);
            if (!reader.AtEnd)
            {
                throw new InvalidDataException("servers.dat contains trailing NBT bytes");
            }

            return new ServersDocument(parsedRoot, isCompressed, rootName);
        }
        catch (InvalidDataException)
        {
            throw;
        }
        catch (Exception exception)
        {
            throw new InvalidDataException("servers.dat is not a valid NBT document", exception);
        }
    }

    private static bool IsGzip(byte[] bytes) => bytes.Length >= 2 && bytes[0] == 0x1F && bytes[1] == 0x8B;

    private static void CopyWithLimit(Stream source, Stream destination, long maximumBytes)
    {
        var buffer = new byte[81920];
        long total = 0;
        int read;
        while ((read = source.Read(buffer, 0, buffer.Length)) > 0)
        {
            total += read;
            if (total > maximumBytes)
            {
                throw new InvalidDataException("servers.dat decompressed payload is too large");
            }

            destination.Write(buffer, 0, read);
        }
    }

    private static byte[] WriteRoot(CompoundTag root, bool compressed, string rootName)
    {
        using var raw = new MemoryStream();
        using (var writer = new BigEndianWriter(raw))
        {
            writer.WriteByte((byte)TagType.Compound);
            writer.WriteString(rootName);
            WritePayload(writer, root);
        }

        if (!compressed)
        {
            return raw.ToArray();
        }

        using var compressedOutput = new MemoryStream();
        using (var gzip = new GZipStream(compressedOutput, CompressionLevel.SmallestSize, leaveOpen: true))
        {
            raw.Position = 0;
            raw.CopyTo(gzip);
        }

        return compressedOutput.ToArray();
    }

    private sealed record ServersDocument(CompoundTag Root, bool Compressed, string RootName = "");

    private static Tag ReadPayload(BigEndianReader reader, TagType type) => type switch
    {
        TagType.Byte => new ByteTag(reader.ReadByteChecked()),
        TagType.Short => new ShortTag(reader.ReadInt16()),
        TagType.Int => new IntTag(reader.ReadInt32()),
        TagType.Long => new LongTag(reader.ReadInt64()),
        TagType.Float => new FloatTag(reader.ReadSingle()),
        TagType.Double => new DoubleTag(reader.ReadDouble()),
        TagType.ByteArray => new ByteArrayTag(reader.ReadBytes(reader.ReadInt32())),
        TagType.String => new StringTag(reader.ReadString()),
        TagType.List => ReadList(reader),
        TagType.Compound => ReadCompound(reader),
        TagType.IntArray => new IntArrayTag(reader.ReadIntArray()),
        TagType.LongArray => new LongArrayTag(reader.ReadLongArray()),
        _ => throw new InvalidDataException($"Unsupported NBT tag type: {type}")
    };

    private static CompoundTag ReadCompound(BigEndianReader reader)
    {
        var compound = new CompoundTag();
        while (true)
        {
            var type = (TagType)reader.ReadByteChecked();
            if (type == TagType.End)
            {
                return compound;
            }

            var name = reader.ReadString();
            if (compound.Values.ContainsKey(name))
            {
                throw new InvalidDataException($"servers.dat contains a duplicate NBT tag: {name}");
            }

            compound.Values[name] = ReadPayload(reader, type);
        }
    }

    private static ListTag ReadList(BigEndianReader reader)
    {
        var type = (TagType)reader.ReadByteChecked();
        var count = reader.ReadInt32();
        if (count < 0 || count > 100_000)
        {
            throw new InvalidDataException("NBT list length is outside the safe bound");
        }

        var items = new List<Tag>(count);
        for (var index = 0; index < count; index++)
        {
            items.Add(ReadPayload(reader, type));
        }

        return new ListTag(type, items);
    }

    private static void WritePayload(BigEndianWriter writer, Tag tag)
    {
        switch (tag)
        {
            case ByteTag value: writer.WriteByte(value.Value); break;
            case ShortTag value: writer.WriteInt16(value.Value); break;
            case IntTag value: writer.WriteInt32(value.Value); break;
            case LongTag value: writer.WriteInt64(value.Value); break;
            case FloatTag value: writer.WriteSingle(value.Value); break;
            case DoubleTag value: writer.WriteDouble(value.Value); break;
            case ByteArrayTag value: writer.WriteInt32(value.Value.Length); writer.WriteBytes(value.Value); break;
            case StringTag value: writer.WriteString(value.Value); break;
            case ListTag value:
                writer.WriteByte((byte)value.ElementType);
                writer.WriteInt32(value.Items.Count);
                foreach (var item in value.Items) WritePayload(writer, item);
                break;
            case CompoundTag value:
                foreach (var pair in value.Values)
                {
                    writer.WriteByte((byte)pair.Value.Type);
                    writer.WriteString(pair.Key);
                    WritePayload(writer, pair.Value);
                }
                writer.WriteByte((byte)TagType.End);
                break;
            case IntArrayTag value: writer.WriteInt32(value.Value.Length); foreach (var item in value.Value) writer.WriteInt32(item); break;
            case LongArrayTag value: writer.WriteInt32(value.Value.Length); foreach (var item in value.Value) writer.WriteInt64(item); break;
            default: throw new InvalidDataException($"Unsupported NBT tag implementation: {tag.GetType().Name}");
        }
    }

    private enum TagType : byte
    {
        End = 0,
        Byte = 1,
        Short = 2,
        Int = 3,
        Long = 4,
        Float = 5,
        Double = 6,
        ByteArray = 7,
        String = 8,
        List = 9,
        Compound = 10,
        IntArray = 11,
        LongArray = 12
    }

    private abstract class Tag(TagType type)
    {
        public TagType Type { get; } = type;
    }

    private sealed class ByteTag(byte value) : Tag(TagType.Byte) { public byte Value { get; } = value; }
    private sealed class ShortTag(short value) : Tag(TagType.Short) { public short Value { get; } = value; }
    private sealed class IntTag(int value) : Tag(TagType.Int) { public int Value { get; } = value; }
    private sealed class LongTag(long value) : Tag(TagType.Long) { public long Value { get; } = value; }
    private sealed class FloatTag(float value) : Tag(TagType.Float) { public float Value { get; } = value; }
    private sealed class DoubleTag(double value) : Tag(TagType.Double) { public double Value { get; } = value; }
    private sealed class ByteArrayTag(byte[] value) : Tag(TagType.ByteArray) { public byte[] Value { get; } = value; }
    private sealed class StringTag(string value) : Tag(TagType.String) { public string Value { get; } = value; }
    private sealed class ListTag(TagType elementType, List<Tag> items) : Tag(TagType.List) { public TagType ElementType { get; } = elementType; public List<Tag> Items { get; } = items; }
    private sealed class CompoundTag : Tag
    {
        public CompoundTag() : base(TagType.Compound) { }
        public Dictionary<string, Tag> Values { get; } = new(StringComparer.Ordinal);
    }

    private static byte GetByte(CompoundTag compound, string key) =>
        compound.Values.TryGetValue(key, out var value) && value is ByteTag byteTag ? byteTag.Value : (byte)0;
    private sealed class IntArrayTag(int[] value) : Tag(TagType.IntArray) { public int[] Value { get; } = value; }
    private sealed class LongArrayTag(long[] value) : Tag(TagType.LongArray) { public long[] Value { get; } = value; }

    private sealed class BigEndianReader(Stream stream) : IDisposable
    {
        private readonly BinaryReader reader = new(stream, Encoding.UTF8, leaveOpen: true);
        public bool AtEnd => reader.BaseStream.Position == reader.BaseStream.Length;
        public byte ReadByteChecked() => reader.ReadByte();
        public short ReadInt16() => BinaryPrimitives.ReadInt16BigEndian(reader.ReadBytes(sizeof(short)));
        public int ReadInt32() => BinaryPrimitives.ReadInt32BigEndian(reader.ReadBytes(sizeof(int)));
        public long ReadInt64() => BinaryPrimitives.ReadInt64BigEndian(reader.ReadBytes(sizeof(long)));
        public float ReadSingle() => BitConverter.Int32BitsToSingle(ReadInt32());
        public double ReadDouble() => BitConverter.Int64BitsToDouble(ReadInt64());
        public byte[] ReadBytes(int count)
        {
            if (count < 0 || count > 64 * 1024 * 1024) throw new InvalidDataException("NBT array length is outside the safe bound");
            var bytes = reader.ReadBytes(count);
            if (bytes.Length != count) throw new EndOfStreamException();
            return bytes;
        }
        public int[] ReadIntArray() { var count = ReadInt32(); if (count < 0 || count > 1_000_000) throw new InvalidDataException(); var values = new int[count]; for (var i = 0; i < count; i++) values[i] = ReadInt32(); return values; }
        public long[] ReadLongArray() { var count = ReadInt32(); if (count < 0 || count > 1_000_000) throw new InvalidDataException(); var values = new long[count]; for (var i = 0; i < count; i++) values[i] = ReadInt64(); return values; }
        public string ReadString()
        {
            var lengthBytes = reader.ReadBytes(sizeof(ushort));
            if (lengthBytes.Length != sizeof(ushort)) throw new EndOfStreamException();
            var length = BinaryPrimitives.ReadUInt16BigEndian(lengthBytes);
            return new UTF8Encoding(encoderShouldEmitUTF8Identifier: false, throwOnInvalidBytes: true).GetString(ReadBytes(length));
        }
        public void Dispose() => reader.Dispose();
    }

    private sealed class BigEndianWriter(Stream stream) : IDisposable
    {
        private readonly BinaryWriter writer = new(stream, Encoding.UTF8, leaveOpen: true);
        public void WriteByte(byte value) => writer.Write(value);
        public void WriteInt16(short value) => writer.Write(BinaryPrimitives.ReverseEndianness(value));
        public void WriteInt32(int value) => writer.Write(BinaryPrimitives.ReverseEndianness(value));
        public void WriteInt64(long value) => writer.Write(BinaryPrimitives.ReverseEndianness(value));
        public void WriteSingle(float value) => WriteInt32(BitConverter.SingleToInt32Bits(value));
        public void WriteDouble(double value) => WriteInt64(BitConverter.DoubleToInt64Bits(value));
        public void WriteBytes(byte[] value) => writer.Write(value);
        public void WriteString(string value) { var bytes = Encoding.UTF8.GetBytes(value); if (bytes.Length > ushort.MaxValue) throw new InvalidDataException(); writer.Write(BinaryPrimitives.ReverseEndianness((ushort)bytes.Length)); writer.Write(bytes); }
        public void Dispose() => writer.Dispose();
    }
}
