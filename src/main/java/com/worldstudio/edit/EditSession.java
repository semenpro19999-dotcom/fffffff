package com.worldstudio.edit;

import com.worldstudio.WorldStudioMod;
import com.worldstudio.net.StudioNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** Server side editing state for one player: selection, clipboard and undo/redo history. */
public final class EditSession {
	private final MinecraftServer server;
	private final UUID playerId;
	private final Deque<RegionSnapshot> undoStack = new ArrayDeque<>();
	private final Deque<RegionSnapshot> redoStack = new ArrayDeque<>();

	private BlockPos pos1;
	private BlockPos pos2;
	private int nextPoint;
	private RegionSnapshot clipboard;
	private BlockState selectedBlock = Blocks.STONE.getDefaultState();
	private Block replaceFrom = Blocks.AIR;

	public EditSession(MinecraftServer server, UUID playerId) {
		this.server = server;
		this.playerId = playerId;
	}

	public UUID playerId() {
		return this.playerId;
	}

	/** Alternates between the two selection corners. Returns the index (0 or 1) that was set. */
	public int setNextSelection(BlockPos pos) {
		int point = this.nextPoint;

		if (point == 0) {
			this.pos1 = pos.toImmutable();
			this.nextPoint = 1;
		} else {
			this.pos2 = pos.toImmutable();
			this.nextPoint = 0;
		}

		this.redoStack.clear();
		return point;
	}

	public void setPoint(int index, BlockPos pos) {
		if (index == 0) {
			this.pos1 = pos.toImmutable();
		} else {
			this.pos2 = pos.toImmutable();
		}

		this.redoStack.clear();
	}

	public void clearSelection() {
		this.pos1 = null;
		this.pos2 = null;
		this.nextPoint = 0;
	}

	public boolean hasSelection() {
		return this.pos1 != null && this.pos2 != null;
	}

	public BlockPos pos1() {
		return this.pos1;
	}

	public BlockPos pos2() {
		return this.pos2;
	}

	public BlockPos selectionMin() {
		return RegionSnapshot.min(this.pos1, this.pos2);
	}

	public BlockPos selectionMax() {
		return RegionSnapshot.max(this.pos1, this.pos2);
	}

	public long selectionVolume() {
		if (!hasSelection()) {
			return 0L;
		}

		BlockPos min = selectionMin();
		BlockPos max = selectionMax();
		return (long) (max.getX() - min.getX() + 1)
				* (max.getY() - min.getY() + 1)
				* (max.getZ() - min.getZ() + 1);
	}

	public void setSelectedBlock(BlockState state) {
		this.selectedBlock = state;
	}

	public BlockState selectedBlock() {
		return this.selectedBlock;
	}

	public void setReplaceFrom(Block block) {
		this.replaceFrom = block;
	}

	public Block replaceFrom() {
		return this.replaceFrom;
	}

	public void setClipboard(RegionSnapshot snapshot) {
		this.clipboard = snapshot;
	}

	public RegionSnapshot clipboard() {
		return this.clipboard;
	}

	public boolean hasClipboard() {
		return this.clipboard != null;
	}

	/** Rotates the clipboard in place by the given rotation. */
	public boolean rotateClipboard(BlockRotation rotation) {
		if (this.clipboard == null) {
			return false;
		}

		this.clipboard = this.clipboard.rotated(rotation);
		return true;
	}

	public void pushUndo(RegionSnapshot snapshot) {
		this.undoStack.addLast(snapshot);

		while (this.undoStack.size() > WorldStudioMod.config().undoLimit) {
			this.undoStack.removeFirst();
		}

		this.redoStack.clear();
	}

	public void pushRedo(RegionSnapshot snapshot) {
		this.redoStack.addLast(snapshot);

		while (this.redoStack.size() > WorldStudioMod.config().undoLimit) {
			this.redoStack.removeFirst();
		}
	}

	public RegionSnapshot popUndo() {
		return this.undoStack.pollLast();
	}

	public RegionSnapshot popRedo() {
		return this.redoStack.pollLast();
	}

	public int undoCount() {
		return this.undoStack.size();
	}

	public int redoCount() {
		return this.redoStack.size();
	}

	public MinecraftServer server() {
		return this.server;
	}

	/** Pushes the full session state to the client so the panel can redraw. */
	public void syncTo(ServerPlayerEntity player) {
		StudioNetworking.sendState(player, this);
	}

	public void message(ServerPlayerEntity player, Text text) {
		StudioNetworking.sendMessage(player, text);
	}
}
