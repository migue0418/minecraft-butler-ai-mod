package com.miguealguacil.butler.client.voice;

import com.miguealguacil.butler.action.ButlerActionExecutor;
import com.miguealguacil.butler.context.WorldContext;
import com.miguealguacil.butler.context.WorldContextCollector;
import com.miguealguacil.butler.http.ButlerHttpClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

public final class VoiceKeyBinding {
    private VoiceKeyBinding() {}

    // 300ms minimum: 44100 * 2 bytes * 0.3s ≈ 26460 bytes PCM
    private static final int MIN_PCM_BYTES = 44100 * 2 / 3;

    private static KeyMapping keyBinding;
    private static final VoiceRecorder recorder = new VoiceRecorder();
    private static boolean wasDown = false;

    public static void register() {
        keyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.butler.push_to_talk",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KeyMapping.Category.GAMEPLAY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(VoiceKeyBinding::tick);
    }

    private static void tick(Minecraft mc) {
        boolean isDown = keyBinding.isDown();
        if (isDown && !wasDown) onPress(mc);
        else if (!isDown && wasDown) onRelease(mc);
        wasDown = isDown;
    }

    private static void onPress(Minecraft mc) {
        if (mc.screen != null) return;
        if (recorder.isRecording()) return;
        try {
            recorder.start(() -> {
                sendClientMessage(mc, "[Alfred] Grabacion maxima alcanzada, enviando...");
                doSend(mc);
            });
            sendClientMessage(mc, "[Alfred] Grabando...");
        } catch (Exception e) {
            sendClientMessage(mc, "[Alfred] Error al acceder al microfono.");
        }
    }

    private static void onRelease(Minecraft mc) {
        if (!recorder.isRecording()) return;
        if (recorder.pcmBytesRecorded() < MIN_PCM_BYTES) {
            recorder.stop();
            sendClientMessage(mc, "[Alfred] Audio demasiado corto.");
            return;
        }
        doSend(mc);
    }

    private static void doSend(Minecraft mc) {
        byte[] wav = recorder.stop();
        if (wav == null || wav.length == 0) {
            sendClientMessage(mc, "[Alfred] Error al capturar el audio.");
            return;
        }
        sendClientMessage(mc, "[Alfred] Procesando...");
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            sendClientMessage(mc, "[Alfred] Solo funciona en partida local.");
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        WorldContext worldCtx = (player != null) ? WorldContextCollector.collect(player, server) : null;

        ButlerHttpClient.sendVoiceAsync(wav, worldCtx)
                .thenAccept(actions -> server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
                    if (p == null) return;
                    actions.forEach(a -> ButlerActionExecutor.execute(a, p.createCommandSourceStack()));
                }))
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg;
                    if (cause instanceof ButlerHttpClient.AuthException) {
                        msg = "[Alfred] No pude autenticarme con el servidor.";
                    } else if (cause instanceof ButlerHttpClient.ServerException sce && sce.status() == 422) {
                        msg = "[Alfred] No pude entenderte. Intenta de nuevo.";
                    } else {
                        msg = "[Alfred] No pude contactar con el servidor.";
                    }
                    server.execute(() -> sendClientMessage(mc, msg));
                    return null;
                });
    }

    private static void sendClientMessage(Minecraft mc, String text) {
        if (mc.gui != null) {
            mc.execute(() -> mc.gui.getChat().addClientSystemMessage(Component.literal(text)));
        }
    }
}
