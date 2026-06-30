package me.copimine.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;

public final class CopiMineClient implements ClientModInitializer {
    private final ClientConfig config = ClientConfig.load();
    private final ClientVisualManager visualManager = new ClientVisualManager(config);
    private final ClientPostProcessController postProcessController = new ClientPostProcessController();
    private final ShaderpackRegistry shaderpackRegistry = new ShaderpackRegistry();
    private final ShaderpackExporter shaderpackExporter = new ShaderpackExporter(shaderpackRegistry);
    private final FallbackPostProcessRuntime fallbackRuntime = new FallbackPostProcessRuntime(postProcessController);
    private final IrisShaderpackRuntime irisShaderpackRuntime = new IrisShaderpackRuntime(config, shaderpackRegistry, shaderpackExporter);
    private final ShaderRuntimeManager shaderRuntimeManager = new ShaderRuntimeManager(
            shaderpackRegistry,
            shaderpackExporter,
            irisShaderpackRuntime,
            fallbackRuntime
    );

    @Override
    public void onInitializeClient() {
        shaderRuntimeManager.initialize();
        visualManager.setShaderRuntimeManager(shaderRuntimeManager);
        ClientBridgeProtocol.registerNetworking(visualManager);
        HudRenderCallback.EVENT.register((drawContext, ignoredTickCounter) -> visualManager.render(drawContext));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            visualManager.tick(ClientBridgeProtocol::sendVisualFinished);
            ClientBridgeProtocol.tickNetwork(client);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientBridgeProtocol.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientBridgeProtocol.onDisconnect();
            visualManager.clearAll("disconnect");
        });
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> visualManager.clearAll("world_change"));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("copimineclient")
                        .then(ClientCommandManager.literal("status")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Text.literal(visualManager.statusLine()));
                                    context.getSource().sendFeedback(Text.literal(ClientBridgeProtocol.handshakeStatusLine()));
                                    context.getSource().sendFeedback(Text.literal("active=" + visualManager.activeSummary()));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("protocol")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Text.literal(
                                            "protocol=" + ClientBridgeProtocol.PROTOCOL_VERSION
                                                    + ", channel=" + ClientBridgeProtocol.MOD_CHANNEL));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("debug")
                                .then(ClientCommandManager.literal("on").executes(context -> {
                                    config.setDebugOverlay(true);
                                    context.getSource().sendFeedback(Text.literal("CopiMineClient: debug enabled"));
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("off").executes(context -> {
                                    config.setDebugOverlay(false);
                                    context.getSource().sendFeedback(Text.literal("CopiMineClient: debug disabled"));
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("reload")
                                .executes(context -> {
                                    config.reload();
                                    shaderRuntimeManager.initialize();
                                    context.getSource().sendFeedback(Text.literal("CopiMineClient: config and shaderpacks reloaded"));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("shader")
                                .then(ClientCommandManager.literal("status")
                                        .executes(context -> {
                                            context.getSource().sendFeedback(Text.literal(shaderRuntimeManager.statusLine()));
                                            for (String line : shaderRuntimeManager.exporterSummaryLines()) {
                                                context.getSource().sendFeedback(Text.literal(" - " + line));
                                            }
                                            return 1;
                                        }))
                                .then(ClientCommandManager.literal("test")
                                        .then(ClientCommandManager.argument("shaderId", StringArgumentType.word())
                                                .executes(context -> {
                                                    String shaderId = StringArgumentType.getString(context, "shaderId");
                                                    boolean applied = visualManager.startLocalShaderTest(shaderId, 30);
                                                    context.getSource().sendFeedback(Text.literal(applied
                                                            ? "Local shader test started: " + shaderId + " / route=" + visualManager.activeRuntimeRouteName() + " / " + visualManager.runtimeStatus()
                                                            : "Local shader test failed: " + shaderId + " / " + visualManager.lastFailureReason()));
                                                    return applied ? 1 : 0;
                                                })
                                                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1, 600))
                                                        .executes(context -> {
                                                            String shaderId = StringArgumentType.getString(context, "shaderId");
                                                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                                            boolean applied = visualManager.startLocalShaderTest(shaderId, seconds);
                                                            context.getSource().sendFeedback(Text.literal(applied
                                                                    ? "Local shader test started: " + shaderId + " / " + seconds + "s / route=" + visualManager.activeRuntimeRouteName() + " / " + visualManager.runtimeStatus()
                                                                    : "Local shader test failed: " + shaderId + " / " + visualManager.lastFailureReason()));
                                                            return applied ? 1 : 0;
                                                        }))))
                                .then(ClientCommandManager.literal("restore")
                                        .executes(context -> {
                                            visualManager.clearAll("manual-restore");
                                            context.getSource().sendFeedback(Text.literal("CopiMineClient: shader runtime restored"));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("visual")
                                .then(ClientCommandManager.literal("test")
                                        .then(ClientCommandManager.argument("effectId", StringArgumentType.word())
                                                .executes(context -> {
                                                    String effectId = StringArgumentType.getString(context, "effectId");
                                                    visualManager.startLocalTest(effectId, 30, 1.0F);
                                                    context.getSource().sendFeedback(Text.literal("Local visual test started: " + effectId));
                                                    return 1;
                                                })
                                                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1, 600))
                                                        .executes(context -> {
                                                            String effectId = StringArgumentType.getString(context, "effectId");
                                                            int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                                            visualManager.startLocalTest(effectId, seconds, 1.0F);
                                                            context.getSource().sendFeedback(Text.literal("Local visual test started: " + effectId + " / " + seconds + "s"));
                                                            return 1;
                                                        })
                                                        .then(ClientCommandManager.argument("intensity", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                                .executes(context -> {
                                                                    String effectId = StringArgumentType.getString(context, "effectId");
                                                                    int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                                                    float intensity = FloatArgumentType.getFloat(context, "intensity");
                                                                    visualManager.startLocalTest(effectId, seconds, intensity);
                                                                    context.getSource().sendFeedback(Text.literal(
                                                                            "Local visual test started: " + effectId + " / " + seconds + "s / intensity=" + intensity));
                                                                    return 1;
                                                                })))))
                                .then(ClientCommandManager.literal("stop")
                                        .then(ClientCommandManager.argument("effectId", StringArgumentType.word())
                                                .executes(context -> {
                                                    String effectId = StringArgumentType.getString(context, "effectId");
                                                    visualManager.stop(effectId);
                                                    context.getSource().sendFeedback(Text.literal("Stopped: " + effectId));
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("clear")
                                        .executes(context -> {
                                            visualManager.clearAll("manual");
                                            context.getSource().sendFeedback(Text.literal("Visuals cleared"));
                                            return 1;
                                        })))
        );
    }
}
