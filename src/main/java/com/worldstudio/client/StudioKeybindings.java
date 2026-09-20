package com.worldstudio.client;

import com.worldstudio.client.net.ClientNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Hotkeys: open the panel, undo/redo with control, rotate the clipboard with brackets. */
public final class StudioKeybindings {
	public static final String CATEGORY = "worldstudio.keybind.category";

	public static KeyBinding openEditor;
	public static KeyBinding undo;
	public static KeyBinding redo;
	public static KeyBinding rotateClockwise;
	public static KeyBinding rotateCounterClockwise;
	public static KeyBinding toggleSelectionRendering;

	private StudioKeybindings() {
	}

	public static void register() {
		openEditor = KeyBindingHelper.registerKeyBinding(new KeyBinding("worldstudio.key.open_editor",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
		undo = KeyBindingHelper.registerKeyBinding(new KeyBinding("worldstudio.key.undo",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY));
		redo = KeyBindingHelper.registerKeyBinding(new KeyBinding("worldstudio.key.redo",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));
		rotateClockwise = KeyBindingHelper.registerKeyBinding(new KeyBinding("worldstudio.key.rotate_clockwise",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY));
		rotateCounterClockwise = KeyBindingHelper.registerKeyBinding(new KeyBinding("worldstudio.key.rotate_counterclockwise",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY));
		toggleSelectionRendering = KeyBindingHelper.registerKeyBinding(new KeyBinding("worldstudio.key.toggle_selection",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(StudioKeybindings::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}

		// Key bindings are only polled while no screen is open, so these never fire while typing.
		while (openEditor.wasPressed()) {
			ClientNetworking.requestEditor();
		}

		while (toggleSelectionRendering.wasPressed()) {
			ClientState.showSelection = !ClientState.showSelection;
		}

		while (undo.wasPressed()) {
			if (Screen.hasControlDown()) {
				ClientNetworking.undo();
			}
		}

		while (redo.wasPressed()) {
			if (Screen.hasControlDown()) {
				ClientNetworking.redo();
			}
		}

		while (rotateClockwise.wasPressed()) {
			ClientNetworking.rotate(1);
		}

		while (rotateCounterClockwise.wasPressed()) {
			ClientNetworking.rotate(3);
		}
	}
}
