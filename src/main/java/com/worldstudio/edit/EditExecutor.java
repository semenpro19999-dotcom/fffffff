package com.worldstudio.edit;

import com.worldstudio.WorldStudioMod;
import com.worldstudio.config.StudioConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/** Runs every editing operation on the server, records undo data and reports the result. */
public final class EditExecutor {
	/** Decides whether the block at a local region coordinate is affected by an operation. */
	private interface RegionFilter {
		boolean test(BlockState current, int x, int y, int z, int sizeX, int sizeY, int sizeZ);
	}

	private EditExecutor() {
	}

	public static void execute(ServerPlayerEntity player, EditSession session, EditOp op, boolean ignoreAir) {
		if (!Permissions.canEdit(player)) {
			session.message(player, Permissions.deniedMessage());
			return;
		}

		ServerWorld world = player.getServerWorld();

		switch (op) {
			case COPY -> copy(player, session, world);
			case PASTE -> paste(player, session, world, ignoreAir);
			case FILL -> applyRegion(player, session, world, session.selectedBlock(), EditOp.FILL,
					(current, x, y, z, sx, sy, sz) -> true);
			case REPLACE -> applyRegion(player, session, world, session.selectedBlock(), EditOp.REPLACE,
					(current, x, y, z, sx, sy, sz) -> current.getBlock() == session.replaceFrom());
			case WALLS -> applyRegion(player, session, world, session.selectedBlock(), EditOp.WALLS,
					(current, x, y, z, sx, sy, sz) -> x == 0 || x == sx - 1 || z == 0 || z == sz - 1);
			case OUTLINE -> applyRegion(player, session, world, session.selectedBlock(), EditOp.OUTLINE,
					(current, x, y, z, sx, sy, sz) -> x == 0 || x == sx - 1 || y == 0 || y == sy - 1 || z == 0 || z == sz - 1);
			case HOLLOW -> applyRegion(player, session, world, Blocks.AIR.getDefaultState(), EditOp.HOLLOW,
					(current, x, y, z, sx, sy, sz) -> x != 0 && x != sx - 1 && y != 0 && y != sy - 1 && z != 0 && z != sz - 1);
			case CLEAR -> applyRegion(player, session, world, Blocks.AIR.getDefaultState(), EditOp.CLEAR,
					(current, x, y, z, sx, sy, sz) -> true);
		}

		session.syncTo(player);
	}

	private static void applyRegion(ServerPlayerEntity player, EditSession session, ServerWorld world,
			BlockState target, EditOp op, RegionFilter filter) {
		if (!session.hasSelection()) {
			session.message(player, Text.translatable("worldstudio.error.no_selection"));
			return;
		}

		StudioConfig config = WorldStudioMod.config();
		BlockPos min = session.selectionMin();
		BlockPos max = session.selectionMax();
		int sizeX = max.getX() - min.getX() + 1;
		int sizeY = max.getY() - min.getY() + 1;
		int sizeZ = max.getZ() - min.getZ() + 1;
		long volume = (long) sizeX * sizeY * sizeZ;

		if (volume > config.maxEditVolume) {
			session.message(player, Text.translatable("worldstudio.error.too_large", volume, config.maxEditVolume));
			return;
		}

		RegionSnapshot before;

		try {
			before = RegionSnapshot.capture(world, min, max);
		} catch (RuntimeException e) {
			WorldStudioMod.LOGGER.warn("[WorldStudio] capture failed", e);
			session.message(player, Text.translatable("worldstudio.error.capture_failed"));
			return;
		}

		int changed = 0;
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					cursor.set(min.getX() + x, min.getY() + y, min.getZ() + z);
					BlockState current = world.getBlockState(cursor);

					if (!filter.test(current, x, y, z, sizeX, sizeY, sizeZ)) {
						continue;
					}

					if (current == target) {
						continue;
					}

					world.setBlockState(cursor, target, RegionSnapshot.EDIT_FLAGS);
					changed++;
				}
			}
		}

		if (changed == 0) {
			session.message(player, Text.translatable("worldstudio.result.no_change"));
			return;
		}

		session.pushUndo(before);
		session.message(player, Text.translatable(resultKey(op), changed));
	}

	private static void copy(ServerPlayerEntity player, EditSession session, ServerWorld world) {
		if (!session.hasSelection()) {
			session.message(player, Text.translatable("worldstudio.error.no_selection"));
			return;
		}

		StudioConfig config = WorldStudioMod.config();
		long volume = session.selectionVolume();

		if (volume > config.maxClipboardVolume) {
			session.message(player, Text.translatable("worldstudio.error.clipboard_too_large", volume,
					config.maxClipboardVolume));
			return;
		}

		RegionSnapshot snapshot = RegionSnapshot.capture(world, session.selectionMin(), session.selectionMax());
		session.setClipboard(snapshot);
		session.message(player, Text.translatable("worldstudio.result.copy", snapshot.sizeX(), snapshot.sizeY(),
				snapshot.sizeZ()));
	}

	private static void paste(ServerPlayerEntity player, EditSession session, ServerWorld world, boolean ignoreAir) {
		RegionSnapshot clipboard = session.clipboard();

		if (clipboard == null) {
			session.message(player, Text.translatable("worldstudio.error.no_clipboard"));
			return;
		}

		StudioConfig config = WorldStudioMod.config();

		if (clipboard.volume() > config.maxEditVolume) {
			session.message(player, Text.translatable("worldstudio.error.too_large", clipboard.volume(),
					config.maxEditVolume));
			return;
		}

		BlockPos feet = player.getBlockPos();
		int lowest = Math.max(world.getBottomY(), feet.getY());
		int highest = Math.min(world.getTopY() - clipboard.sizeY() + 1, feet.getY());
		int originY = Math.max(lowest, highest);
		BlockPos origin = new BlockPos(feet.getX(), originY, feet.getZ());
		BlockPos max = origin.add(clipboard.sizeX() - 1, clipboard.sizeY() - 1, clipboard.sizeZ() - 1);

		RegionSnapshot before = RegionSnapshot.capture(world, origin, max);
		int changed = clipboard.apply(world, origin, ignoreAir);

		if (changed == 0) {
			session.message(player, Text.translatable("worldstudio.result.no_change"));
			return;
		}

		session.pushUndo(before);
		session.message(player, Text.translatable("worldstudio.result.paste", changed));
	}

	public static void rotate(ServerPlayerEntity player, EditSession session, int steps) {
		if (!session.hasClipboard()) {
			session.message(player, Text.translatable("worldstudio.error.no_clipboard"));
			session.syncTo(player);
			return;
		}

		BlockRotation rotation = switch (Math.floorMod(steps, 4)) {
			case 1 -> BlockRotation.CLOCKWISE_90;
			case 2 -> BlockRotation.CLOCKWISE_180;
			case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
			default -> BlockRotation.NONE;
		};

		if (rotation == BlockRotation.NONE) {
			session.syncTo(player);
			return;
		}

		session.rotateClipboard(rotation);
		session.message(player, Text.translatable("worldstudio.result.rotate",
				Text.translatable("worldstudio.rotation." + rotation.name().toLowerCase(Locale.ROOT))));
		session.syncTo(player);
	}

	public static void undo(ServerPlayerEntity player, EditSession session) {
		revert(player, session, true);
	}

	public static void redo(ServerPlayerEntity player, EditSession session) {
		revert(player, session, false);
	}

	private static void revert(ServerPlayerEntity player, EditSession session, boolean undo) {
		RegionSnapshot entry = undo ? session.popUndo() : session.popRedo();

		if (entry == null) {
			session.message(player, Text.translatable(undo ? "worldstudio.error.no_undo" : "worldstudio.error.no_redo"));
			session.syncTo(player);
			return;
		}

		MinecraftServer server = player.getServer();
		ServerWorld world = server.getWorld(entry.dimension());

		if (world == null) {
			session.message(player, Text.translatable("worldstudio.error.dimension_gone"));
			session.syncTo(player);
			return;
		}

		RegionSnapshot opposite;

		try {
			opposite = RegionSnapshot.capture(world, entry.min(), entry.max());
		} catch (RuntimeException e) {
			WorldStudioMod.LOGGER.warn("[WorldStudio] history capture failed", e);
			session.message(player, Text.translatable("worldstudio.error.capture_failed"));
			session.syncTo(player);
			return;
		}

		if (undo) {
			session.pushRedo(opposite);
		} else {
			session.pushUndo(opposite);
		}

		int restored = entry.restore(server);
		session.message(player, Text.translatable(undo ? "worldstudio.result.undo" : "worldstudio.result.redo", restored));
		session.syncTo(player);
	}

	public static void saveSchematic(ServerPlayerEntity player, EditSession session, String rawName) {
		StudioConfig config = WorldStudioMod.config();
		String name = SchematicStorage.sanitize(rawName, config.maxSchematicNameLength);

		if (!SchematicStorage.isNameValid(name)) {
			session.message(player, Text.translatable("worldstudio.error.invalid_name"));
			session.syncTo(player);
			return;
		}

		if (!session.hasClipboard()) {
			session.message(player, Text.translatable("worldstudio.error.no_clipboard"));
			session.syncTo(player);
			return;
		}

		boolean saved = SchematicStorage.save(player.getServer(), name, session.clipboard());
		session.message(player, Text.translatable(saved ? "worldstudio.result.saved" : "worldstudio.error.save_failed",
				name));
		session.syncTo(player);
	}

	public static void loadSchematic(ServerPlayerEntity player, EditSession session, String rawName) {
		StudioConfig config = WorldStudioMod.config();
		String name = SchematicStorage.sanitize(rawName, config.maxSchematicNameLength);
		NbtCompound raw = SchematicStorage.loadRaw(player.getServer(), name);

		if (raw == null || !raw.contains("region")) {
			session.message(player, Text.translatable("worldstudio.error.not_found", name));
			session.syncTo(player);
			return;
		}

		ServerWorld world = player.getServerWorld();
		RegistryEntryLookup<Block> lookup = world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK);
		RegionSnapshot snapshot;

		try {
			snapshot = RegionSnapshot.readStructureNbt(raw.getCompound("region"), lookup, world.getRegistryKey(),
					BlockPos.ORIGIN);
		} catch (RuntimeException e) {
			WorldStudioMod.LOGGER.warn("[WorldStudio] could not parse schematic '{}'", name, e);
			session.message(player, Text.translatable("worldstudio.error.corrupt", name));
			session.syncTo(player);
			return;
		}

		if (snapshot.volume() > config.maxClipboardVolume) {
			session.message(player, Text.translatable("worldstudio.error.clipboard_too_large", snapshot.volume(),
					config.maxClipboardVolume));
			session.syncTo(player);
			return;
		}

		session.setClipboard(snapshot);
		session.message(player, Text.translatable("worldstudio.result.loaded", name));
		session.syncTo(player);
	}

	public static void deleteSchematic(ServerPlayerEntity player, EditSession session, String rawName) {
		StudioConfig config = WorldStudioMod.config();
		String name = SchematicStorage.sanitize(rawName, config.maxSchematicNameLength);
		boolean deleted = SchematicStorage.delete(player.getServer(), name);
		session.message(player, Text.translatable(deleted ? "worldstudio.result.deleted" : "worldstudio.error.not_found",
				name));
		session.syncTo(player);
	}

	/** Turns a block id string into a block state, or null when it cannot be resolved. */
	public static BlockState resolveState(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}

		Identifier identifier = Identifier.tryParse(id);

		if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
			return null;
		}

		return Registries.BLOCK.get(identifier).getDefaultState();
	}

	public static Identifier idOf(Block block) {
		return Registries.BLOCK.getId(block);
	}

	private static String resultKey(EditOp op) {
		return "worldstudio.result." + op.name().toLowerCase(Locale.ROOT);
	}
}
