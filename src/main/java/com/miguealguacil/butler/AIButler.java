package com.miguealguacil.butler;

import com.miguealguacil.butler.command.ButlerCommand;
import com.miguealguacil.butler.entity.AlfredEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIButler implements ModInitializer {
    public static final String MOD_ID = "ai-butler";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AlfredEntities.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            ButlerCommand.register(dispatcher)
        );
        LOGGER.info("AI Butler initialized.");
    }
}