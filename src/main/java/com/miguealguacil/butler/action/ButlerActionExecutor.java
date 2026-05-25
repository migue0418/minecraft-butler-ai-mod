package com.miguealguacil.butler.action;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class ButlerActionExecutor {
    private ButlerActionExecutor() {}

    public static void execute(ButlerAction action, CommandSourceStack source) {
        switch (action.type()) {
            case "speak" ->
                source.sendSuccess(() -> Component.literal("[Alfred] " + action.message()), false);
            case "move_to_position" -> {
                if (action.x() != null && action.y() != null && action.z() != null) {
                    source.sendSuccess(() -> Component.literal(
                        "[Alfred] Me dirijo a " + action.x() + " " + action.y() + " " + action.z() + "."), false);
                } else {
                    source.sendSuccess(() -> Component.literal("[Alfred] Destino no especificado."), false);
                }
            }
            default ->
                source.sendSuccess(() -> Component.literal("[Alfred] Acción desconocida: " + action.type()), false);
        }
    }
}
