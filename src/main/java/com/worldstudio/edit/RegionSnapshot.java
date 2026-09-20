package com.worldstudio.edit;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable, palette compressed snapshot of a box of blocks.
 *
 * <p>The same structure powers three features: the undo/redo history (absolute position, restored
 * in place), the clipboard (relative position, applied at an arbitrary origin) and saved
 * schematics (serialised to NBT on disk).
 */
public final class RegionSnapshot {
	/**
	 * Flags used for every edit: notify clients and redraw, but skip neighbour updates so that a
	 * bulk fill does not trigger a cascade of physics updates.
	 */
	public static final int EDIT_FLAGS = Block.NOTIFY_LISTENERS | Block.REDRAW_ON_MAIN_THREAD;

	private static final int MAX_PALETTE = Short.MAX_VALUE;

	private final RegistryKey<World> dimension;
	private final BlockPos min;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final List<BlockState> palette;
	private final short[] data;

	private RegionSnapshot(RegistryKey<World> dimension, BlockPos min, int sizeX, int sizeY, int sizeZ,
			List<BlockState> palette, short[] data) {
		this.dimension = dimension;
		this.min = min;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.palette = palette;
		this.data = data;
	}

	public static BlockPos min(BlockPos a, BlockPos b) {
		return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
	}

	public static BlockPos max(BlockPos a, BlockPos b) {
		return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
	}

	/** Captures every block in the inclusive box {@code a}..{@code b}. */
	public static RegionSnapshot capture(ServerWorld world, BlockPos a, BlockPos b) {
		BlockPos min = min(a, b);
		BlockPos max = max(a, b);
		int sx = max.getX() - min.getX() + 1;
		int sy = max.getY() - min.getY() + 1;
		int sz = max.getZ() - min.getZ() + 1;

		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Short> indices = new HashMap<>();
		short[] data = new short[sx * sy * sz];
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int y = 0; y < sy; y++) {
			for (int z = 0; z < sz; z++) {
				for (int x = 0; x < sx; x++) {
					cursor.set(min.getX() + x, min.getY() + y, min.getZ() + z);
					BlockState state = world.getBlockState(cursor);
					Short index = indices.get(state);
					short value;

					if (index == null) {
						if (palette.size() >= MAX_PALETTE) {
							throw new IllegalStateException("Region palette overflow");
						}

						value = (short) palette.size();
						palette.add(state);
						indices.put(state, value);
					} else {
						value = index;
					}

					data[index(x, y, z, sx, sz)] = value;
				}
			}
		}

		return new RegionSnapshot(world.getRegistryKey(), min.toImmutable(), sx, sy, sz, palette, data);
	}

	/** Rebuilds a snapshot that was written with {@link #writeStructureNbt()}. */
	public static RegionSnapshot readStructureNbt(NbtCompound nbt, RegistryEntryLookup<Block> lookup,
			RegistryKey<World> dimension, BlockPos origin) {
		int sx = nbt.getInt("sizeX");
		int sy = nbt.getInt("sizeY");
		int sz = nbt.getInt("sizeZ");

		if (sx <= 0 || sy <= 0 || sz <= 0) {
			throw new IllegalArgumentException("Invalid schematic size");
		}

		if ((long) sx * sy * sz > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Schematic too large");
		}

		NbtList paletteNbt = nbt.getList("palette", NbtElement.COMPOUND_TYPE);
		List<BlockState> palette = new ArrayList<>(paletteNbt.size());

		for (int i = 0; i < paletteNbt.size(); i++) {
			palette.add(NbtHelper.toBlockState(lookup, paletteNbt.getCompound(i)));
		}

		byte[] raw = nbt.getByteArray("data");
		short[] data = new short[sx * sy * sz];

		if (raw.length == data.length * 2) {
			ByteBuffer.wrap(raw).asShortBuffer().get(data);
		} else {
			// Malformed or truncated payload: treat the whole region as palette entry 0.
			data = new short[data.length];
		}

		return new RegionSnapshot(dimension, origin.toImmutable(), sx, sy, sz, palette, data);
	}

	/** Serialises the region as a relative structure (no absolute position, no dimension). */
	public NbtCompound writeStructureNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("sizeX", this.sizeX);
		nbt.putInt("sizeY", this.sizeY);
		nbt.putInt("sizeZ", this.sizeZ);

		NbtList paletteNbt = new NbtList();

		for (BlockState state : this.palette) {
			paletteNbt.add(NbtHelper.fromBlockState(state));
		}

		nbt.put("palette", paletteNbt);

		ByteBuffer buffer = ByteBuffer.allocate(this.data.length * 2);

		for (short value : this.data) {
			buffer.putShort(value);
		}

		nbt.putByteArray("data", buffer.array());
		nbt.putInt("stored", this.storedCount());
		return nbt;
	}

	private static int index(int x, int y, int z, int sx, int sz) {
		return (y * sz + z) * sx + x;
	}

	/** Writes every stored block into {@code world} with {@code origin} as the minimum corner. */
	public int apply(ServerWorld world, BlockPos origin, boolean skipAir) {
		int changed = 0;
		BlockPos.Mutable cursor = new BlockPos.Mutable();

		for (int y = 0; y < this.sizeY; y++) {
			for (int z = 0; z < this.sizeZ; z++) {
				for (int x = 0; x < this.sizeX; x++) {
					BlockState state = this.palette.get(this.data[index(x, y, z, this.sizeX, this.sizeZ)]);

					if (skipAir && state.isAir()) {
						continue;
					}

					cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);

					if (!world.isOutOfHeightLimit(cursor)) {
						world.setBlockState(cursor, state, EDIT_FLAGS);
						changed++;
					}
				}
			}
		}

		return changed;
	}

	/** Restores this snapshot at its original absolute position. Used for undo/redo. */
	public int restore(MinecraftServer server) {
		ServerWorld world = server.getWorld(this.dimension);

		if (world == null) {
			return 0;
		}

		return apply(world, this.min, false);
	}

	/**
	 * Returns a copy rotated around the region's own centre, using the same coordinate transform
	 * vanilla applies to structures.
	 */
	public RegionSnapshot rotated(BlockRotation rotation) {
		if (rotation == BlockRotation.NONE) {
			return this;
		}

		boolean swap = rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90;
		int newSizeX = swap ? this.sizeZ : this.sizeX;
		int newSizeZ = swap ? this.sizeX : this.sizeZ;

		List<BlockState> newPalette = new ArrayList<>();
		Map<BlockState, Short> newIndices = new HashMap<>();
		short[] newData = new short[newSizeX * this.sizeY * newSizeZ];

		for (int y = 0; y < this.sizeY; y++) {
			for (int z = 0; z < this.sizeZ; z++) {
				for (int x = 0; x < this.sizeX; x++) {
					BlockState state = this.palette.get(this.data[index(x, y, z, this.sizeX, this.sizeZ)]);
					BlockState rotated = state.rotate(rotation);
					Short existing = newIndices.get(rotated);
					short value;

					if (existing == null) {
						if (newPalette.size() >= MAX_PALETTE) {
							throw new IllegalStateException("Region palette overflow");
						}

						value = (short) newPalette.size();
						newPalette.add(rotated);
						newIndices.put(rotated, value);
					} else {
						value = existing;
					}

					newData[index(targetX(x, z, rotation, this.sizeX, this.sizeZ), y,
							targetZ(x, z, rotation, this.sizeX, this.sizeZ), newSizeX, newSizeZ)] = value;
				}
			}
		}

		return new RegionSnapshot(this.dimension, this.min, newSizeX, this.sizeY, newSizeZ, newPalette, newData);
	}

	private static int targetX(int x, int z, BlockRotation rotation, int sx, int sz) {
		switch (rotation) {
			case CLOCKWISE_90:
				return sz - 1 - z;
			case CLOCKWISE_180:
				return sx - 1 - x;
			case COUNTERCLOCKWISE_90:
				return z;
			default:
				return x;
		}
	}

	private static int targetZ(int x, int z, BlockRotation rotation, int sx, int sz) {
		switch (rotation) {
			case CLOCKWISE_90:
				return x;
			case CLOCKWISE_180:
				return sz - 1 - z;
			case COUNTERCLOCKWISE_90:
				return sx - 1 - x;
			default:
				return z;
		}
	}

	public RegistryKey<World> dimension() {
		return this.dimension;
	}

	public BlockPos min() {
		return this.min;
	}

	public BlockPos max() {
		return this.min.add(this.sizeX - 1, this.sizeY - 1, this.sizeZ - 1);
	}

	public int sizeX() {
		return this.sizeX;
	}

	public int sizeY() {
		return this.sizeY;
	}

	public int sizeZ() {
		return this.sizeZ;
	}

	public long volume() {
		return (long) this.sizeX * this.sizeY * this.sizeZ;
	}

	public int storedCount() {
		return this.data.length;
	}
}
