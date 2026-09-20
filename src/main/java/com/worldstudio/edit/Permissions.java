package com.worldstudio.edit;

import com.worldstudio.WorldStudioMod;
import com.worldstudio.config.StudioConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Decides who is allowed to edit the world. Single player is always allowed by default so that the
 * mod works out of the box; on dedicated servers an operator level is required.
 */
public final class Permissions {
	private Permissions() {
	}

	public static boolean canEdit(ServerPlayerEntity player) {
		StudioConfig config = WorldStudioMod.config();
		MinecraftServer server = player.getServer();

		if (server == null) {
			return false;
		}

		if (config.allowWhenNotDedicated && !server.isDedicated()) {
			return true;
		}

		if (player.hasPermissionLevel(config.requiredPermissionLevel)) {
			return true;
		}

		return config.allowCreativePlayers && player.isCreative();
	}

	public static Text deniedMessage() {
		return Text.translatable("worldstudio.error.no_permission");
	}
}
