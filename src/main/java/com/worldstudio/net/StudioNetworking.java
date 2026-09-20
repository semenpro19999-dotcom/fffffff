package com.worldstudio.net;

import com.worldstudio.WorldStudioMod;
import com.worldstudio.edit.EditExecutor;
import com.worldstudio.edit.EditOp;
import com.worldstudio.edit.EditSession;
import com.worldstudio.edit.Permissions;
import com.worldstudio.edit.RegionSnapshot;
import com.worldstudio.edit.SchematicStorage;
import com.worldstudio.edit.SessionManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Server side of the editor protocol. */
public final class StudioNetworking {
	private StudioNetworking() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_REQUEST_STATE,
				(server, player, handler, buf, responseSender) -> server.execute(() -> {
					if (!Permissions.canEdit(player)) {
						deny(player);
						return;
					}

					sendOpenEditor(player, SessionManager.get(player));
				}));

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_SELECT_POINT,
				(server, player, handler, buf, responseSender) -> {
					int index = buf.readInt();
					BlockPos pos = buf.readBlockPos();
					server.execute(() -> {
						if (!Permissions.canEdit(player)) {
							deny(player);
							return;
						}

						EditSession session = SessionManager.get(player);
						session.setPoint(index, pos);
						session.syncTo(player);
					});
				});

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_CLEAR_SELECTION,
				(server, player, handler, buf, responseSender) -> server.execute(() -> {
					if (!Permissions.canEdit(player)) {
						deny(player);
						return;
					}

					EditSession session = SessionManager.get(player);
					session.clearSelection();
					session.syncTo(player);
				}));

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_SET_BLOCK,
				(server, player, handler, buf, responseSender) -> {
					String id = buf.readString(32767);
					server.execute(() -> {
						if (!Permissions.canEdit(player)) {
							deny(player);
							return;
						}

						BlockState state = EditExecutor.resolveState(id);

						if (state == null) {
							SessionManager.get(player).message(player,
									Text.translatable("worldstudio.error.unknown_block", id));
							return;
						}

						EditSession session = SessionManager.get(player);
						session.setSelectedBlock(state);
						session.syncTo(player);
					});
				});

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_SET_REPLACE_FROM,
				(server, player, handler, buf, responseSender) -> {
					String id = buf.readString(32767);
					server.execute(() -> {
						if (!Permissions.canEdit(player)) {
							deny(player);
							return;
						}

						BlockState state = EditExecutor.resolveState(id);

						if (state == null) {
							SessionManager.get(player).message(player,
									Text.translatable("worldstudio.error.unknown_block", id));
							return;
						}

						EditSession session = SessionManager.get(player);
						session.setReplaceFrom(state.getBlock());
						session.syncTo(player);
					});
				});

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_EXECUTE,
				(server, player, handler, buf, responseSender) -> {
					int opId = buf.readInt();
					boolean ignoreAir = buf.readBoolean();
					server.execute(() -> {
						EditOp op = EditOp.byId(opId);

						if (op == null) {
							return;
						}

						if (!Permissions.canEdit(player)) {
							deny(player);
							return;
						}

						EditExecutor.execute(player, SessionManager.get(player), op, ignoreAir);
					});
				});

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_ROTATE,
				(server, player, handler, buf, responseSender) -> {
					int steps = buf.readInt();
					server.execute(() -> {
						if (!Permissions.canEdit(player)) {
							deny(player);
							return;
						}

						EditExecutor.rotate(player, SessionManager.get(player), steps);
					});
				});

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_UNDO,
				(server, player, handler, buf, responseSender) -> server.execute(() -> {
					if (!Permissions.canEdit(player)) {
						deny(player);
						return;
					}

					EditExecutor.undo(player, SessionManager.get(player));
				}));

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_REDO,
				(server, player, handler, buf, responseSender) -> server.execute(() -> {
					if (!Permissions.canEdit(player)) {
						deny(player);
						return;
					}

					EditExecutor.redo(player, SessionManager.get(player));
				}));

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_SAVE_SCHEMATIC,
				(server, player, handler, buf, responseSender) -> {
					String name = buf.readString(32767);
					server.execute(() -> {
						if (!Permissions.canEdit(player)) {
							deny(player);
							return;
						}

						EditExecutor.saveSchematic(player, SessionManager.get(player), name);
					});
				});

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_LOAD_SCHEMATIC,
				(server, player, handler, buf, responseSender) -> {
					String name = buf.readString(32767);
					server.execute(() -> {
						if (!Permissions.canEdit(player)) {
							deny(player);
							return;
						}

						EditExecutor.loadSchematic(player, SessionManager.get(player), name);
					});
				});

		ServerPlayNetworking.registerGlobalReceiver(StudioChannels.C2S_DELETE_SCHEMATIC,
				(server, player, handler, buf, responseSender) -> {
					String name = buf.readString(32767);
					server.execute(() -> {
						if (!Permissions.canEdit(player)) {
							deny(player);
							return;
						}

						EditExecutor.deleteSchematic(player, SessionManager.get(player), name);
					});
				});

		WorldStudioMod.LOGGER.info("[WorldStudio] registered {} server channels", 12);
	}

	private static void deny(ServerPlayerEntity player) {
		sendMessage(player, Permissions.deniedMessage());
	}

	public static void sendState(ServerPlayerEntity player, EditSession session) {
		PacketByteBuf buf = PacketByteBufs.create();
		writeState(buf, player.getServer(), session);
		ServerPlayNetworking.send(player, StudioChannels.S2C_STATE, buf);
	}

	/** Sends the state first, then tells the client to open the panel. */
	public static void sendOpenEditor(ServerPlayerEntity player, EditSession session) {
		sendState(player, session);
		ServerPlayNetworking.send(player, StudioChannels.S2C_OPEN_EDITOR, PacketByteBufs.empty());
	}

	public static void sendMessage(ServerPlayerEntity player, Text text) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(Text.Serializer.toJson(text));
		ServerPlayNetworking.send(player, StudioChannels.S2C_MESSAGE, buf);
	}

	/**
	 * Wire format of the state packet. The client reader in
	 * {@code com.worldstudio.client.net.ClientNetworking} must mirror this exactly.
	 */
	public static void writeState(PacketByteBuf buf, MinecraftServer server, EditSession session) {
		BlockPos pos1 = session.pos1();
		BlockPos pos2 = session.pos2();
		buf.writeBoolean(pos1 != null);

		if (pos1 != null) {
			buf.writeBlockPos(pos1);
		}

		buf.writeBoolean(pos2 != null);

		if (pos2 != null) {
			buf.writeBlockPos(pos2);
		}

		RegionSnapshot clipboard = session.clipboard();
		buf.writeBoolean(clipboard != null);

		if (clipboard != null) {
			buf.writeInt(clipboard.sizeX());
			buf.writeInt(clipboard.sizeY());
			buf.writeInt(clipboard.sizeZ());
		}

		buf.writeInt(session.undoCount());
		buf.writeInt(session.redoCount());
		buf.writeIdentifier(EditExecutor.idOf(session.selectedBlock().getBlock()));
		buf.writeIdentifier(EditExecutor.idOf(session.replaceFrom()));

		List<String> schematics = server == null ? List.of() : SchematicStorage.list(server);
		buf.writeInt(schematics.size());

		for (String name : schematics) {
			buf.writeString(name);
		}
	}

	/** Convenience used by the command so that both entry points stay in sync. */
	public static boolean isKnownBlock(String id) {
		return EditExecutor.resolveState(id) != null;
	}

	public static String blockIdOf(BlockState state) {
		return Registries.BLOCK.getId(state.getBlock()).toString();
	}
}
