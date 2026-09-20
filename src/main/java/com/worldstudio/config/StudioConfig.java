package com.worldstudio.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.worldstudio.WorldStudioMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Plain JSON config stored in {@code config/worldstudio.json}. */
public class StudioConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Operator level required on a dedicated server. */
	public int requiredPermissionLevel = 2;
	/** Always allow editing on an integrated (single player / LAN) server. */
	public boolean allowWhenNotDedicated = true;
	/** Also allow players in creative mode even without the operator level. */
	public boolean allowCreativePlayers = true;
	/** Hard cap on the number of blocks a single operation may touch. */
	public int maxEditVolume = 524288;
	/** Hard cap on the number of blocks the clipboard may hold. */
	public int maxClipboardVolume = 262144;
	/** How many undo steps are kept per player. */
	public int undoLimit = 16;
	/** When true, pasting skips air blocks instead of carving holes. */
	public boolean pasteIgnoresAir = true;
	/** Maximum length of a schematic name. */
	public int maxSchematicNameLength = 48;

	public static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("worldstudio.json");
	}

	public static StudioConfig load() {
		Path path = configFile();

		try {
			if (Files.exists(path)) {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				StudioConfig loaded = GSON.fromJson(json, StudioConfig.class);

				if (loaded != null) {
					loaded.clamp();
					return loaded;
				}
			}

			StudioConfig fresh = new StudioConfig();
			fresh.save();
			return fresh;
		} catch (Exception e) {
			WorldStudioMod.LOGGER.warn("[WorldStudio] could not read config, falling back to defaults", e);
			return new StudioConfig();
		}
	}

	public void save() {
		try {
			Path path = configFile();

			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}

			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (Exception e) {
			WorldStudioMod.LOGGER.warn("[WorldStudio] could not write config", e);
		}
	}

	private void clamp() {
		this.requiredPermissionLevel = Math.max(0, Math.min(4, this.requiredPermissionLevel));
		this.maxEditVolume = Math.max(64, Math.min(8_000_000, this.maxEditVolume));
		this.maxClipboardVolume = Math.max(64, Math.min(8_000_000, this.maxClipboardVolume));
		this.undoLimit = Math.max(0, Math.min(128, this.undoLimit));
		this.maxSchematicNameLength = Math.max(8, Math.min(64, this.maxSchematicNameLength));
	}
}
