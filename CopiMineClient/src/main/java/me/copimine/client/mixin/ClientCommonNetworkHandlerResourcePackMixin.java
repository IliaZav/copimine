package me.copimine.client.mixin;

import me.copimine.client.CopiMineClientLogger;
import me.copimine.client.CopiMineResourcePackPolicy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.MalformedURLException;
import java.net.URL;

/** Auto-accepts only the first-party CopiMine pack used by the managed server. */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerResourcePackMixin {
    @Shadow
    @Final
    protected MinecraftClient client;

    @Inject(
            method = "onResourcePackSend",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void copimine$autoAcceptOfficialPack(ResourcePackSendS2CPacket packet, CallbackInfo callback) {
        URL url;
        try {
            url = new URL(packet.url());
        } catch (MalformedURLException error) {
            return;
        }
        if (!CopiMineResourcePackPolicy.accepts(url, packet.hash())) {
            return;
        }
        try {
            client.getServerResourcePackProvider().addResourcePack(packet.id(), url, packet.hash());
            client.getServerResourcePackProvider().acceptAll();
            CopiMineClientLogger.info("[CopiMineClient/ResourcePack] Accepted official pack url=" + url + " sha1=" + packet.hash());
            callback.cancel();
        } catch (RuntimeException error) {
            CopiMineClientLogger.warn("[CopiMineClient/ResourcePack] Auto-accept failed; vanilla prompt remains", error);
        }
    }
}
