package com.worldstudio.client.gui;

import com.worldstudio.WorldStudioMod;
import com.worldstudio.client.ClientState;
import com.worldstudio.client.net.ClientNetworking;
import com.worldstudio.edit.EditOp;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The editor panel: a toolbox of region operations on the left, a searchable block palette on the
 * right, and a schematic library along the bottom.
 */
public class StudioScreen extends Screen {
	private static final Identifier LOGO = WorldStudioMod.id("textures/gui/studio_logo.png");

	private static final int PANEL_W = 396;
	private static final int PANEL_H = 232;

	private static final int TOOLS_X = 8;
	private static final int TOOLS_X2 = 104;
	private static final int TOOLS_Y = 41;
	private static final int TOOL_W = 92;
	private static final int TOOL_H = 16;
	private static final int TOOL_STEP = 19;

	private static final int PALETTE_X = 202;
	private static final int PALETTE_W = 186;
	private static final int GRID_Y = 74;
	private static final int GRID_H = 96;
	private static final int LABEL_Y = 173;

	private static final int SCHEM_Y = 180;
	private static final int SCHEM_FIELD_W = 96;
	private static final int LIB_Y = 198;
	private static final int STATUS_Y = 184;
	private static final int CLOSE_Y = 204;

	private final List<ButtonWidget> toolButtons = new ArrayList<>();
	private ButtonWidget pasteButton;
	private ButtonWidget rotateButton;
	private ButtonWidget undoButton;
	private ButtonWidget redoButton;
	private ButtonWidget pasteAirButton;
	private ButtonWidget outlineButton;
	private ButtonWidget saveButton;
	private ButtonWidget loadButton;
	private ButtonWidget deleteButton;
	private TextFieldWidget searchField;
	private TextFieldWidget nameField;
	private BlockPaletteWidget palette;

	private boolean pasteAir;
	private String searchText = "";
	private String schematicName = "";

	public StudioScreen() {
		super(Text.translatable("worldstudio.screen.title"));
	}

	private int panelX() {
		return (this.width - PANEL_W) / 2;
	}

	private int panelY() {
		return Math.max(8, (this.height - PANEL_H) / 2);
	}

	@Override
	protected void init() {
		super.init();
		this.toolButtons.clear();

		int px = panelX();
		int py = panelY();

		this.palette = new BlockPaletteWidget(this.client);
		this.palette.setBounds(px + PALETTE_X + 2, py + GRID_Y, PALETTE_W - 8, GRID_H);

		// Column A: operations that need a region selection.
		addTool(0, 0, Text.translatable("worldstudio.tool.fill"), button -> run(EditOp.FILL));
		addTool(0, 1, Text.translatable("worldstudio.tool.replace"), button -> run(EditOp.REPLACE));
		addTool(0, 2, Text.translatable("worldstudio.tool.walls"), button -> run(EditOp.WALLS));
		addTool(0, 3, Text.translatable("worldstudio.tool.outline"), button -> run(EditOp.OUTLINE));
		addTool(0, 4, Text.translatable("worldstudio.tool.hollow"), button -> run(EditOp.HOLLOW));
		addTool(0, 5, Text.translatable("worldstudio.tool.clear"), button -> run(EditOp.CLEAR));
		addTool(0, 6, Text.translatable("worldstudio.tool.copy"), button -> run(EditOp.COPY));

		// Column B: clipboard, history and view options.
		this.pasteButton = addTool(1, 0, Text.translatable("worldstudio.tool.paste"), button -> run(EditOp.PASTE));
		this.rotateButton = addTool(1, 1, Text.translatable("worldstudio.tool.rotate"),
				button -> ClientNetworking.rotate(1));
		this.undoButton = addTool(1, 2, Text.translatable("worldstudio.tool.undo"), button -> ClientNetworking.undo());
		this.redoButton = addTool(1, 3, Text.translatable("worldstudio.tool.redo"), button -> ClientNetworking.redo());
		this.pasteAirButton = addTool(1, 4, pasteAirLabel(), button -> {
			this.pasteAir = !this.pasteAir;
			refreshButtons();
		});
		this.outlineButton = addTool(1, 5, outlineLabel(), button -> {
			ClientState.showSelection = !ClientState.showSelection;
			refreshButtons();
		});
		addTool(1, 6, Text.translatable("worldstudio.tool.clear_selection"), button -> {
			ClientNetworking.clearSelection();
			ClientState.pos1 = null;
			ClientState.pos2 = null;
		});

		// Block palette: search box and the target/source mode switch.
		this.searchField = new TextFieldWidget(this.textRenderer, px + PALETTE_X, py + TOOLS_Y, PALETTE_W, 14,
				Text.translatable("worldstudio.palette.search"));
		this.searchField.setMaxLength(48);
		this.searchField.setPlaceholder(Text.translatable("worldstudio.palette.search"));
		this.searchField.setText(this.searchText);
		this.searchField.setChangedListener(text -> {
			this.searchText = text;

			if (this.palette != null) {
				this.palette.setFilter(text);
			}
		});
		this.addDrawableChild(this.searchField);

		ButtonWidget targetMode = ButtonWidget.builder(Text.translatable("worldstudio.palette.mode.target"),
				button -> setPaletteMode(BlockPaletteWidget.Mode.TARGET))
				.dimensions(px + PALETTE_X, py + 58, 91, 13)
				.build();
		ButtonWidget sourceMode = ButtonWidget.builder(Text.translatable("worldstudio.palette.mode.source"),
				button -> setPaletteMode(BlockPaletteWidget.Mode.SOURCE))
				.dimensions(px + PALETTE_X + 95, py + 58, 91, 13)
				.build();
		this.addDrawableChild(targetMode);
		this.addDrawableChild(sourceMode);
		this.toolButtons.add(targetMode);
		this.toolButtons.add(sourceMode);

		// Schematic library.
		this.nameField = new TextFieldWidget(this.textRenderer, px + TOOLS_X, py + SCHEM_Y, SCHEM_FIELD_W, 14,
				Text.translatable("worldstudio.schematic.name"));
		this.nameField.setMaxLength(48);
		this.nameField.setPlaceholder(Text.translatable("worldstudio.schematic.name"));
		this.nameField.setText(this.schematicName);
		this.nameField.setChangedListener(text -> this.schematicName = text);
		this.addDrawableChild(this.nameField);

		this.saveButton = addButton(px + TOOLS_X + SCHEM_FIELD_W + 4, py + SCHEM_Y, 28, 14,
				Text.translatable("worldstudio.schematic.save"), button -> {
					ClientNetworking.saveSchematic(currentName());
					refreshButtons();
				});
		this.loadButton = addButton(px + TOOLS_X + SCHEM_FIELD_W + 36, py + SCHEM_Y, 28, 14,
				Text.translatable("worldstudio.schematic.load"), button -> ClientNetworking.loadSchematic(currentName()));
		this.deleteButton = addButton(px + TOOLS_X + SCHEM_FIELD_W + 68, py + SCHEM_Y, 24, 14,
				Text.translatable("worldstudio.schematic.delete"),
				button -> ClientNetworking.deleteSchematic(currentName()));

		addButton(px + PALETTE_X, py + CLOSE_Y, 88, 16, Text.translatable("worldstudio.screen.close"),
				button -> this.close());

		refreshButtons();
	}

	private String currentName() {
		String name = this.schematicName == null ? "" : this.schematicName.trim();

		if (name.isEmpty()) {
			name = "build_" + (ClientState.schematics.size() + 1);
			this.schematicName = name;

			if (this.nameField != null) {
				this.nameField.setText(name);
			}
		}

		return name;
	}

	private void setPaletteMode(BlockPaletteWidget.Mode mode) {
		if (this.palette != null) {
			this.palette.setMode(mode);
		}

		refreshButtons();
	}

	private ButtonWidget addTool(int column, int row, Text label, ButtonWidget.PressAction action) {
		int x = panelX() + (column == 0 ? TOOLS_X : TOOLS_X2);
		int y = panelY() + TOOLS_Y + row * TOOL_STEP;
		return addButton(x, y, TOOL_W, TOOL_H, label, action);
	}

	private ButtonWidget addButton(int x, int y, int width, int height, Text label, ButtonWidget.PressAction action) {
		ButtonWidget button = ButtonWidget.builder(label, action).dimensions(x, y, width, height).build();
		this.addDrawableChild(button);
		this.toolButtons.add(button);
		return button;
	}

	private Text pasteAirLabel() {
		return Text.translatable("worldstudio.tool.paste_air",
				Text.translatable(this.pasteAir ? "worldstudio.state.on" : "worldstudio.state.off")
						.formatted(this.pasteAir ? Formatting.GREEN : Formatting.GRAY));
	}

	private Text outlineLabel() {
		return Text.translatable("worldstudio.tool.outline_view",
				Text.translatable(ClientState.showSelection ? "worldstudio.state.on" : "worldstudio.state.off")
						.formatted(ClientState.showSelection ? Formatting.GREEN : Formatting.GRAY));
	}

	/** Keeps every button's enabled state and toggle label in sync with the server state. */
	private void refreshButtons() {
		if (this.pasteButton != null) {
			this.pasteButton.active = ClientState.hasClipboard;
		}

		if (this.rotateButton != null) {
			this.rotateButton.active = ClientState.hasClipboard;
		}

		if (this.undoButton != null) {
			this.undoButton.active = ClientState.undoCount > 0;
		}

		if (this.redoButton != null) {
			this.redoButton.active = ClientState.redoCount > 0;
		}

		if (this.pasteAirButton != null) {
			this.pasteAirButton.setMessage(pasteAirLabel());
		}

		if (this.outlineButton != null) {
			this.outlineButton.setMessage(outlineLabel());
		}

		boolean hasClipboard = ClientState.hasClipboard;

		if (this.saveButton != null) {
			this.saveButton.active = hasClipboard;
		}

		boolean hasName = this.schematicName != null && !this.schematicName.trim().isEmpty();

		if (this.loadButton != null) {
			this.loadButton.active = hasName;
		}

		if (this.deleteButton != null) {
			this.deleteButton.active = hasName;
		}

		for (ButtonWidget button : this.toolButtons) {
			if (button.getMessage().getString().equals(I18n.translate("worldstudio.palette.mode.target"))) {
				button.active = this.palette == null || this.palette.mode() != BlockPaletteWidget.Mode.TARGET;
			} else if (button.getMessage().getString().equals(I18n.translate("worldstudio.palette.mode.source"))) {
				button.active = this.palette == null || this.palette.mode() != BlockPaletteWidget.Mode.SOURCE;
			}
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (this.searchField != null) {
			this.searchField.tick();
		}

		if (this.nameField != null) {
			this.nameField.tick();
		}

		refreshButtons();
	}

	private void run(EditOp op) {
		ClientNetworking.execute(op, !this.pasteAir);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);

		int px = panelX();
		int py = panelY();

		context.fill(px, py, px + PANEL_W, py + PANEL_H, 0xF2_11141D);
		context.drawBorder(px, py, PANEL_W, PANEL_H, 0xFF_4C566A);
		context.fill(px + 1, py + 1, px + PANEL_W - 1, py + 36, 0xFF_1B2130);
		context.fill(px + 1, py + 36, px + PANEL_W - 1, py + 37, 0xFF_3B4252);
		context.drawTexture(LOGO, px + 5, py + 4, 0, 0, 28, 28, 28, 28);

		context.drawTextWithShadow(this.textRenderer, Text.translatable("worldstudio.screen.title"), px + 38, py + 6,
				0xFF_E5E9F0);
		context.drawTextWithShadow(this.textRenderer, selectionLine(), px + 38, py + 16, 0xFF_88C0D0);
		context.drawTextWithShadow(this.textRenderer, clipboardLine(), px + 38, py + 26, 0xFF_A3BE8C);

		super.render(context, mouseX, mouseY, delta);

		if (this.palette != null) {
			this.palette.render(context, mouseX, mouseY);
			context.drawTextWithShadow(this.textRenderer, paletteLine(), px + PALETTE_X, py + LABEL_Y, 0xFF_D8DEE9);
		}

		context.drawTextWithShadow(this.textRenderer, libraryLine(), px + TOOLS_X, py + LIB_Y, 0xFF_7B8496);
		context.drawTextWithShadow(this.textRenderer, statusLine(), px + PALETTE_X, py + STATUS_Y, 0xFF_EBCB8B);
	}

	private String selectionLine() {
		if (!ClientState.hasSelection()) {
			return trim(I18n.translate("worldstudio.status.no_selection"), PANEL_W - 46);
		}

		BlockPos min = ClientState.selectionMin();
		BlockPos max = ClientState.selectionMax();
		return trim(I18n.translate("worldstudio.status.selection", max.getX() - min.getX() + 1,
				max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1, ClientState.selectionVolume()), PANEL_W - 46);
	}

	private String clipboardLine() {
		StringBuilder builder = new StringBuilder();

		if (ClientState.hasClipboard) {
			builder.append(trim(I18n.translate("worldstudio.status.clipboard", ClientState.clipboardX,
					ClientState.clipboardY, ClientState.clipboardZ), 150));
		} else {
			builder.append(trim(I18n.translate("worldstudio.status.no_clipboard"), 150));
		}

		builder.append("  ").append(trim(I18n.translate("worldstudio.status.history", ClientState.undoCount,
				ClientState.redoCount), 110));
		return builder.toString();
	}

	private String paletteLine() {
		if (this.palette == null) {
			return "";
		}

		Block hovered = this.palette.hovered();

		if (hovered != null) {
			Identifier id = Registries.BLOCK.getId(hovered);
			return trim(hovered.getName().getString() + " (" + id + ")", PALETTE_W);
		}

		Identifier selection = this.palette.currentSelection();
		return trim(I18n.translate("worldstudio.palette.selected", selection.toString(),
				this.palette.visibleCount()), PALETTE_W);
	}

	private String libraryLine() {
		String names = String.join(", ", ClientState.schematics);

		if (!names.isEmpty()) {
			names = " - " + trim(names, 96);
		}

		return trim(I18n.translate("worldstudio.schematic.library", ClientState.schematics.size()) + names, 190);
	}

	private String statusLine() {
		return trim(ClientState.statusMessage.getString(), PALETTE_W);
	}

	private String trim(String text, int maxWidth) {
		if (this.textRenderer == null || text == null) {
			return "";
		}

		return this.textRenderer.trimToWidth(text, maxWidth);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.palette != null) {
			Optional<Block> clicked = this.palette.mouseClicked(mouseX, mouseY, button);

			if (clicked.isPresent()) {
				Identifier id = Registries.BLOCK.getId(clicked.get());

				if (this.palette.mode() == BlockPaletteWidget.Mode.TARGET) {
					ClientState.selectedBlock = id;
					ClientNetworking.setSelectedBlock(id);
				} else {
					ClientState.replaceFrom = id;
					ClientNetworking.setReplaceFrom(id);
				}

				return true;
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.palette != null && this.palette.mouseScrolled(mouseX, mouseY, amount)) {
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean shouldPause() {
		// The world keeps running so edits are visible immediately, like a real editor.
		return false;
	}

	@Override
	public void close() {
		super.close();
	}
}
