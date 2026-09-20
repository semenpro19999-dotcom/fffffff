package com.worldstudio.edit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps one {@link EditSession} per player for the lifetime of their connection. */
public final class SessionManager {
	private static final Map<UUID, EditSession> SESSIONS = new HashMap<>();

	private SessionManager() {
	}

	public static EditSession get(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		UUID id = player.getUuid();
		EditSession existing = SESSIONS.get(id);

		if (existing != null && existing.server() == server) {
			return existing;
		}

		EditSession session = new EditSession(server, id);
		SESSIONS.put(id, session);
		return session;
	}

	public static EditSession peek(UUID id) {
		return SESSIONS.get(id);
	}

	public static void remove(UUID id) {
		SESSIONS.remove(id);
	}

	public static void clear() {
		SESSIONS.clear();
	}
}
