package me.copimine.client;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public record ClientReadyAckPayload(
        boolean accepted,
        int requiredProtocol,
        int receivedProtocol,
        String reason
) implements CustomPayload {
    public static final CustomPayload.Id<ClientReadyAckPayload> ID =
            new CustomPayload.Id<>(Identifier.of("copimine", "client_ready_ack"));
    public static final PacketCodec<RegistryByteBuf, ClientReadyAckPayload> CODEC =
            CustomPayload.codecOf(ClientReadyAckPayload::write, ClientReadyAckPayload::read);
    private static final int MAX_REASON_LENGTH = 64;

    public ClientReadyAckPayload {
        reason = normalize(reason);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        try (DataOutputStream out = new DataOutputStream(new ByteBufOutputStream(buf))) {
            out.writeBoolean(accepted);
            out.writeInt(requiredProtocol);
            out.writeInt(receivedProtocol);
            out.writeUTF(reason);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to write CopiMineClient READY ACK payload", error);
        }
    }

    private static ClientReadyAckPayload read(RegistryByteBuf buf) {
        try (DataInputStream in = new DataInputStream(new ByteBufInputStream(buf))) {
            boolean accepted = in.readBoolean();
            int requiredProtocol = in.readInt();
            int receivedProtocol = in.readInt();
            String reason = normalize(in.readUTF());
            if (in.available() != 0) {
                throw new IllegalArgumentException("Unexpected trailing bytes in CLIENT_READY_ACK");
            }
            return new ClientReadyAckPayload(accepted, requiredProtocol, receivedProtocol, reason);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to read CopiMineClient READY ACK payload", error);
        }
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            return normalized.substring(0, MAX_REASON_LENGTH);
        }
        return normalized;
    }
}
