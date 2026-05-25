package com.miguealguacil.butler.command;

import com.miguealguacil.butler.AIButler;
import com.miguealguacil.butler.action.ButlerAction;
import com.miguealguacil.butler.action.ButlerActionExecutor;
import com.miguealguacil.butler.http.ButlerHttpClient;
import com.miguealguacil.butler.state.ButlerState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ButlerCommand {
    private ButlerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("butler")
                .then(Commands.literal("ping")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("Alfred está operativo."), false);
                        return 1;
                    }))
                .then(Commands.literal("pos")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player = source.getPlayerOrException();
                        BlockPos pos = player.blockPosition();
                        source.sendSuccess(
                            () -> Component.literal("Tu posición es: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                            false);
                        return 1;
                    }))
                .then(Commands.literal("here")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player = source.getPlayerOrException();
                        BlockPos pos = player.blockPosition();
                        ButlerState.setSavedPosition(pos);
                        source.sendSuccess(
                            () -> Component.literal("Posición guardada para Alfred: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                            false);
                        return 1;
                    }))
                .then(Commands.literal("target")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        BlockPos pos = ButlerState.getSavedPosition();
                        if (pos != null) {
                            source.sendSuccess(
                                () -> Component.literal("Objetivo actual de Alfred: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                                false);
                        } else {
                            source.sendSuccess(
                                () -> Component.literal("No hay posición guardada. Usa /butler here primero."),
                                false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("mock")
                    .executes(ctx -> {
                        ButlerActionExecutor.execute(
                            new ButlerAction("speak", "He recibido una acción mock correctamente."),
                            ctx.getSource());
                        return 1;
                    }))
                .then(Commands.literal("ask")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            MinecraftServer server = source.getServer();
                            String message = StringArgumentType.getString(ctx, "message");

                            source.sendSuccess(
                                () -> Component.literal("[Alfred] Procesando..."), false);

                            ButlerHttpClient.sendAsync(message)
                                .thenAccept(actions ->
                                    server.execute(() ->
                                        actions.forEach(action ->
                                            ButlerActionExecutor.execute(action, source))
                                    )
                                )
                                .exceptionally(ex -> {
                                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                    String msg;
                                    if (cause instanceof ButlerHttpClient.AuthException) {
                                        msg = "[Alfred] No pude autenticarme con el servidor.";
                                    } else if (cause instanceof ButlerHttpClient.ServerException) {
                                        msg = "[Alfred] Error del servidor.";
                                    } else {
                                        msg = "[Alfred] No pude contactar con el servidor.";
                                    }
                                    AIButler.LOGGER.error("Butler ask error", cause);
                                    server.execute(() ->
                                        source.sendSuccess(() -> Component.literal(msg), false));
                                    return null;
                                });

                            return 1;
                        })))
        );
    }
}
