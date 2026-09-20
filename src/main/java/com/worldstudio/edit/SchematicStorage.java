package com.worldstudio.edit;

import com.worldstudio.WorldStudioMod;
import com.worldstudio.config.StudioConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The schematic library: saved clipboard structures stored as NBT files inside
 * {@code <world>/worldstudio/}. This is the "toolbox" analogue of Roblox Studio.
 */
public final class SchematicStorage {
	private static final String EXTENSION = ".nbt";

	private SchematicStorage() {
	}

	/** Strips anything that is not a safe file name character. */
	public static String sanitize(String name, int maxLength) {
		StringBuilder builder = new StringBuilder(name.length());

		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);

			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
					|| c == '_' || c == '-' || c == ' ') {
				builder.append(c);
			}
		}

		String trimmed = builder.toString().trim();

		while (trimmed.contains("  ")) {
			trimmed = trimmed.replace("  ", " ");
		}

		if (trimmed.length() > maxLength) {
			trimmed = trimmed.substring(0, maxLength).trim();
		}

		return trimmed;
	}

	public static boolean isNameValid(String name) {
		return !name.isEmpty();
	}

	public static Path directory(MinecraftServer server) throws IOException {
		Path dir = server.getSavePath(WorldSavePath.ROOT).resolve("worldstudio");
		Files.createDirectories(dir);
		return dir;
	}

	public static List<String> list(MinecraftServer server) {
		List<String> names = new ArrayList<>();

		try {
			Path dir = directory(server);

			try (Stream<Path> stream = Files.list(dir)) {
				stream.filter(Files::isRegularFile)
						.map(path -> path.getFileName().toString())
						.filter(name -> name.endsWith(EXTENSION))
						.map(name -> name.substring(0, name.length() - EXTENSION.length()))
						.sorted(Comparator.naturalOrder())
						.forEach(names::add);
			}
		} catch (IOException e) {
			WorldStudioMod.LOGGER.warn("[WorldStudio] could not list schematics", e);
		}

		return names;
	}

	public static boolean save(MinecraftServer server, String name, RegionSnapshot snapshot) {
		if (!isNameValid(name) || snapshot == null) {
			return false;
		}

		try {
			Path file = directory(server).resolve(name + EXTENSION);
			NbtCompound nbt = new NbtCompound();
			nbt.putString("id", "worldstudio:schematic");
			nbt.putInt("version", 1);
			nbt.putLong("savedAt", System.currentTimeMillis());
			nbt.put("region", snapshot.writeStructureNbt());
			NbtIo.writeCompressed(nbt, file.toFile());
			return true;
		} catch (IOException | RuntimeException e) {
			WorldStudioMod.LOGGER.warn("[WorldStudio] could not save schematic '{}'", name, e);
			return false;
		}
	}

	public static NbtCompound loadRaw(MinecraftServer server, String name) {
		if (!isNameValid(name)) {
			return null;
		}

		try {
			Path file = directory(server).resolve(name + EXTENSION);

			if (!Files.isRegularFile(file)) {
				return null;
			}

			return NbtIo.readCompressed(file.toFile());
		} catch (IOException e) {
			WorldStudioMod.LOGGER.warn("[WorldStudio] could not read schematic '{}'", name, e);
			return null;
		}
	}

	public static boolean delete(MinecraftServer server, String name) {
		if (!isNameValid(name)) {
			return false;
		}

		try {
			return Files.deleteIfExists(directory(server).resolve(name + EXTENSION));
		} catch (IOException e) {
			WorldStudioMod.LOGGER.warn("[WorldStudio] could not delete schematic '{}'", name, e);
			return false;
		}
	}

	public static int sizeLimit(StudioConfig config) {
		return config.maxSchematicNameLength;
	}
}
