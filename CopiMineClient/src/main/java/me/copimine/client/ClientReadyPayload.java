package me.copimine.client;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public record ClientReadyPayload(int protocolVersion, String clientVersion) implements CustomPayload {
    public static final CustomPayload.Id<ClientReadyPayload> ID =
            new CustomPayload.Id<>(Identifier.of("copimine", "client_ready"));
    public static final PacketCodec<RegistryByteBuf, ClientReadyPayload> CODEC =
            CustomPayload.codecOf(ClientReadyPayload::write, ClientReadyPayload::read);
    public static final int MAX_CLIENT_VERSION_LENGTH = 64;

    public ClientReadyPayload {
        clientVersion = normalize(clientVersion);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        try (DataOutputStream out = new DataOutputStream(new ByteBufOutputStream(buf))) {
            out.writeInt(protocolVersion);
            out.writeUTF(clientVersion);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to write CopiMineClient READY payload", error);
        }
    }

    private static ClientReadyPayload read(RegistryByteBuf buf) {
        try (DataInputStream in = new DataInputStream(new ByteBufInputStream(buf))) {
            int protocol = in.readInt();
            String version = normalize(in.readUTF());
            if (in.available() != 0) {
                throw new IllegalArgumentException("Unexpected trailing bytes in CLIENT_READY");
            }
            return new ClientReadyPayload(protocol, version);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to read CopiMineClient READY payload", error);
        }
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_CLIENT_VERSION_LENGTH) {
            return normalized.substring(0, MAX_CLIENT_VERSION_LENGTH);
        }
        return normalized;
    }
}
