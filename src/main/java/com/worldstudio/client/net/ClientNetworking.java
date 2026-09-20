package com.worldstudio.client.net;

import com.worldstudio.client.ClientState;
import com.worldstudio.client.gui.StudioScreen;
import com.worldstudio.edit.EditOp;
import com.worldstudio.net.StudioChannels;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Client side of the editor protocol: sends requests, applies server state. */
public final class ClientNetworking {
	private ClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(StudioChannels.S2C_STATE, (client, handler, buf, responseSender) -> {
			StatePayload payload = StatePayload.read(buf);
			client.execute(payload::apply);
		});

		ClientPlayNetworking.registerGlobalReceiver(StudioChannels.S2C_OPEN_EDITOR,
				(client, handler, buf, responseSender) -> client.execute(() -> client.setScreen(new StudioScreen())));

		ClientPlayNetworking.registerGlobalReceiver(StudioChannels.S2C_MESSAGE, (client, handler, buf, responseSender) -> {
			String json = buf.readString(32767);
			Text text = Text.Serializer.fromJson(json);
			client.execute(() -> {
				if (text == null) {
					return;
				}

				ClientState.statusMessage = text;

				if (client.currentScreen == null && client.inGameHud != null) {
					client.inGameHud.setOverlayMessage(text, false);
				}
			});
		});
	}

	/** Snapshot of the state packet, read on the netty thread and applied on the render thread. */
	private record StatePayload(BlockPos pos1, BlockPos pos2, boolean hasClipboard, int clipboardX, int clipboardY,
			int clipboardZ, int undoCount, int redoCount, Identifier selectedBlock, Identifier replaceFrom,
			List<String> schematics) {
		static StatePayload read(PacketByteBuf buf) {
			BlockPos pos1 = buf.readBoolean() ? buf.readBlockPos() : null;
			BlockPos pos2 = buf.readBoolean() ? buf.readBlockPos() : null;
			boolean hasClipboard = buf.readBoolean();
			int cx = 0;
			int cy = 0;
			int cz = 0;

			if (hasClipboard) {
				cx = buf.readInt();
				cy = buf.readInt();
				cz = buf.readInt();
			}

			int undo = buf.readInt();
			int redo = buf.readInt();
			Identifier selected = buf.readIdentifier();
			Identifier replace = buf.readIdentifier();
			int count = Math.min(buf.readInt(), 4096);
			List<String> names = new ArrayList<>(count);

			for (int i = 0; i < count; i++) {
				names.add(buf.readString(32767));
			}

			return new StatePayload(pos1, pos2, hasClipboard, cx, cy, cz, undo, redo, selected, replace, names);
		}

		void apply() {
			ClientState.pos1 = this.pos1;
			ClientState.pos2 = this.pos2;
			ClientState.hasClipboard = this.hasClipboard;
			ClientState.clipboardX = this.clipboardX;
			ClientState.clipboardY = this.clipboardY;
			ClientState.clipboardZ = this.clipboardZ;
			ClientState.undoCount = this.undoCount;
			ClientState.redoCount = this.redoCount;

			if (this.selectedBlock != null) {
				ClientState.selectedBlock = this.selectedBlock;
			}

			if (this.replaceFrom != null) {
				ClientState.replaceFrom = this.replaceFrom;
			}

			ClientState.schematics.clear();
			ClientState.schematics.addAll(this.schematics);
		}
	}

	public static void requestEditor() {
		ClientPlayNetworking.send(StudioChannels.C2S_REQUEST_STATE, PacketByteBufs.empty());
	}

	public static void selectPoint(int index, BlockPos pos) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeInt(index);
		buf.writeBlockPos(pos);
		ClientPlayNetworking.send(StudioChannels.C2S_SELECT_POINT, buf);
	}

	public static void clearSelection() {
		ClientPlayNetworking.send(StudioChannels.C2S_CLEAR_SELECTION, PacketByteBufs.empty());
	}

	public static void setSelectedBlock(Identifier id) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(id.toString());
		ClientPlayNetworking.send(StudioChannels.C2S_SET_BLOCK, buf);
	}

	public static void setReplaceFrom(Identifier id) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(id.toString());
		ClientPlayNetworking.send(StudioChannels.C2S_SET_REPLACE_FROM, buf);
	}

	public static void execute(EditOp op, boolean ignoreAir) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeInt(op.ordinal());
		buf.writeBoolean(ignoreAir);
		ClientPlayNetworking.send(StudioChannels.C2S_EXECUTE, buf);
	}

	public static void rotate(int steps) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeInt(steps);
		ClientPlayNetworking.send(StudioChannels.C2S_ROTATE, buf);
	}

	public static void undo() {
		ClientPlayNetworking.send(StudioChannels.C2S_UNDO, PacketByteBufs.empty());
	}

	public static void redo() {
		ClientPlayNetworking.send(StudioChannels.C2S_REDO, PacketByteBufs.empty());
	}

	public static void saveSchematic(String name) {
		ClientPlayNetworking.send(StudioChannels.C2S_SAVE_SCHEMATIC, namedBuf(name));
	}

	public static void loadSchematic(String name) {
		ClientPlayNetworking.send(StudioChannels.C2S_LOAD_SCHEMATIC, namedBuf(name));
	}

	public static void deleteSchematic(String name) {
		ClientPlayNetworking.send(StudioChannels.C2S_DELETE_SCHEMATIC, namedBuf(name));
	}

	private static PacketByteBuf namedBuf(String name) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(name);
		return buf;
	}
}
