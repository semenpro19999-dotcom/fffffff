package com.worldstudio;

import com.worldstudio.command.StudioCommand;
import com.worldstudio.config.StudioConfig;
import com.worldstudio.edit.SessionManager;
import com.worldstudio.net.StudioNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldStudioMod implements ModInitializer {
	public static final String MOD_ID = "worldstudio";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static StudioConfig config = new StudioConfig();

	/** The loaded configuration. Never null after (and before) initialisation. */
	public static StudioConfig config() {
		return config;
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		config = StudioConfig.load();
		StudioItems.register();
		StudioNetworking.registerServerReceivers();
		StudioCommand.register();

		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> SessionManager.remove(handler.player.getUuid()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SessionManager.clear());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!server.isDedicated()) {
				LOGGER.info("[WorldStudio] integrated server detected, editing is allowed for the local player");
			}
		});

		LOGGER.info("[WorldStudio] ready: permissionLevel={}, maxEditVolume={}, maxClipboardVolume={}, undoLimit={}",
				config.requiredPermissionLevel, config.maxEditVolume, config.maxClipboardVolume, config.undoLimit);
	}
}
