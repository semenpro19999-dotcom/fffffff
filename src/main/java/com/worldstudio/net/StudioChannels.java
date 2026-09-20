package com.worldstudio.net;

import com.worldstudio.WorldStudioMod;
import net.minecraft.util.Identifier;

/** Custom payload channel names. */
public final class StudioChannels {
	// client -> server
	public static final Identifier C2S_REQUEST_STATE = WorldStudioMod.id("request_state");
	public static final Identifier C2S_SELECT_POINT = WorldStudioMod.id("select_point");
	public static final Identifier C2S_CLEAR_SELECTION = WorldStudioMod.id("clear_selection");
	public static final Identifier C2S_SET_BLOCK = WorldStudioMod.id("set_block");
	public static final Identifier C2S_SET_REPLACE_FROM = WorldStudioMod.id("set_replace_from");
	public static final Identifier C2S_EXECUTE = WorldStudioMod.id("execute");
	public static final Identifier C2S_ROTATE = WorldStudioMod.id("rotate");
	public static final Identifier C2S_UNDO = WorldStudioMod.id("undo");
	public static final Identifier C2S_REDO = WorldStudioMod.id("redo");
	public static final Identifier C2S_SAVE_SCHEMATIC = WorldStudioMod.id("save_schematic");
	public static final Identifier C2S_LOAD_SCHEMATIC = WorldStudioMod.id("load_schematic");
	public static final Identifier C2S_DELETE_SCHEMATIC = WorldStudioMod.id("delete_schematic");

	// server -> client
	public static final Identifier S2C_STATE = WorldStudioMod.id("state");
	public static final Identifier S2C_OPEN_EDITOR = WorldStudioMod.id("open_editor");
	public static final Identifier S2C_MESSAGE = WorldStudioMod.id("message");

	private StudioChannels() {
	}
}
