package com.miguealguacil.butler.client;

import com.miguealguacil.butler.client.entity.AlfredEntityRenderer;
import com.miguealguacil.butler.client.voice.VoiceKeyBinding;
import com.miguealguacil.butler.entity.AlfredEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AIButlerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(AlfredEntities.ALFRED, AlfredEntityRenderer::new);
		VoiceKeyBinding.register();
	}
}