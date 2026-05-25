package com.miguealguacil.butler.action;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class ButlerActionExecutor {
    private ButlerActionExecutor() {}

    public static void execute(ButlerAction action, CommandSourceStack source) {
        switch (action.type()) {
            case "speak" ->
                source.sendSuccess(() -> Component.literal("[Alfred] " + action.message()), false);
            default ->
                source.sendSuccess(() -> Component.literal("[Alfred] Acción desconocida: " + action.type()), false);
        }
    }
}