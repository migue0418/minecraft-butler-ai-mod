package com.miguealguacil.butler.action;

import com.miguealguacil.butler.state.ButlerState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class ButlerActionExecutor {
    private ButlerActionExecutor() {}

    public static void execute(ButlerAction action, CommandSourceStack source) {
        switch (action.type()) {
            case "speak" ->
                source.sendSuccess(() -> Component.literal("[Alfred] " + action.message()), false);
            case "move_to_position" -> {
                if (action.x() != null && action.y() != null && action.z() != null) {
                    ServerLevel level = (ServerLevel) source.getLevel();
                    ButlerState.findAlfred(level).ifPresent(alfred ->
                        alfred.getNavigation().moveTo(action.x(), action.y(), action.z(), 0.6)
                    );
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
