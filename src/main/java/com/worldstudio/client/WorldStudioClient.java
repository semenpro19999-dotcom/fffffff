package com.worldstudio.client;

import com.worldstudio.client.net.ClientNetworking;
import com.worldstudio.client.render.SelectionRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public class WorldStudioClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientNetworking.register();
		StudioKeybindings.register();
		SelectionRenderer.register();

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientState::reset));

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			com.worldstudio.WorldStudioMod.LOGGER.info("[WorldStudio] client entrypoint ready (development environment)");
		}
	}
}
