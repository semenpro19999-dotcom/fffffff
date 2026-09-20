package com.worldstudio.client;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side mirror of the server editing session. Everything here is written from the state
 * packet and read by the panel and the in world selection renderer.
 */
public final class ClientState {
	public static final Identifier DEFAULT_BLOCK = new Identifier("minecraft", "stone");
	public static final Identifier AIR = new Identifier("minecraft", "air");

	public static BlockPos pos1;
	public static BlockPos pos2;
	public static boolean hasClipboard;
	public static int clipboardX;
	public static int clipboardY;
	public static int clipboardZ;
	public static int undoCount;
	public static int redoCount;
	public static Identifier selectedBlock = DEFAULT_BLOCK;
	public static Identifier replaceFrom = AIR;
	public static final List<String> schematics = new ArrayList<>();
	public static Text statusMessage = Text.empty();
	public static boolean showSelection = true;

	private ClientState() {
	}

	public static boolean hasSelection() {
		return pos1 != null && pos2 != null;
	}

	public static BlockPos selectionMin() {
		return new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()),
				Math.min(pos1.getZ(), pos2.getZ()));
	}

	public static BlockPos selectionMax() {
		return new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()),
				Math.max(pos1.getZ(), pos2.getZ()));
	}

	public static long selectionVolume() {
		if (!hasSelection()) {
			return 0L;
		}

		BlockPos min = selectionMin();
		BlockPos max = selectionMax();
		return (long) (max.getX() - min.getX() + 1)
				* (max.getY() - min.getY() + 1)
				* (max.getZ() - min.getZ() + 1);
	}

	/** Called when the client leaves a world so nothing leaks into the next session. */
	public static void reset() {
		pos1 = null;
		pos2 = null;
		hasClipboard = false;
		clipboardX = clipboardY = clipboardZ = 0;
		undoCount = 0;
		redoCount = 0;
		selectedBlock = DEFAULT_BLOCK;
		replaceFrom = AIR;
		schematics.clear();
		statusMessage = Text.empty();
	}
}
