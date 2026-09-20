package com.worldstudio.client.gui;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A searchable, scrollable grid of every placeable block. Clicking a cell picks the block for the
 * active mode (the block to place, or the block to replace).
 */
public final class BlockPaletteWidget {
	public enum Mode {
		TARGET,
		SOURCE
	}

	private static final int CELL = 18;
	private static List<Block> cachedBlocks;

	private final List<Block> visible = new ArrayList<>();
	private final MinecraftClient client;
	private int x;
	private int y;
	private int width;
	private int height;
	private int columns = 1;
	private int rows = 1;
	private int scroll;
	private String filter = "";
	private Mode mode = Mode.TARGET;
	private Block hovered;

	public BlockPaletteWidget(MinecraftClient client) {
		this.client = client;
		applyFilter();
	}

	/** Collects every block that has an item form, once per game session. */
	private static List<Block> allBlocks() {
		if (cachedBlocks != null) {
			return cachedBlocks;
		}

		List<Block> blocks = new ArrayList<>();

		for (Identifier id : Registries.BLOCK.getIds()) {
			Block block = Registries.BLOCK.get(id);

			if (block == null || block.asItem() == Items.AIR) {
				continue;
			}

			blocks.add(block);
		}

		blocks.sort(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()));
		cachedBlocks = blocks;
		return blocks;
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.columns = Math.max(1, width / CELL);
		this.rows = Math.max(1, height / CELL);
		clampScroll();
	}

	public void setFilter(String filter) {
		this.filter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		this.scroll = 0;
		applyFilter();
	}

	private void applyFilter() {
		this.visible.clear();
		boolean empty = this.filter.isEmpty();

		for (Block block : allBlocks()) {
			if (empty || matches(block)) {
				this.visible.add(block);
			}
		}

		clampScroll();
	}

	private boolean matches(Block block) {
		Identifier id = Registries.BLOCK.getId(block);

		if (id.getPath().contains(this.filter) || id.getNamespace().contains(this.filter)) {
			return true;
		}

		String name = block.getName().getString().toLowerCase(Locale.ROOT);
		return name.contains(this.filter);
	}

	private void clampScroll() {
		int maxScroll = Math.max(0, (this.visible.size() + this.columns - 1) / this.columns - this.rows);
		this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public Mode mode() {
		return this.mode;
	}

	public int visibleCount() {
		return this.visible.size();
	}

	public Block hovered() {
		return this.hovered;
	}

	/** The block id currently chosen for the active mode. */
	public Identifier currentSelection() {
		return this.mode == Mode.TARGET ? com.worldstudio.client.ClientState.selectedBlock
				: com.worldstudio.client.ClientState.replaceFrom;
	}

	public void render(DrawContext context, int mouseX, int mouseY) {
		context.fill(this.x, this.y, this.x + this.width, this.y + this.height, 0xB0_0D1017);
		context.drawBorder(this.x - 1, this.y - 1, this.width + 2, this.height + 2, 0xFF_3B4252);

		boolean inside = mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		this.hovered = null;

		if (this.visible.isEmpty()) {
			context.drawTextWithShadow(this.client.textRenderer, "…", this.x + 6, this.y + 6, 0xFF_9AA0A6);
			return;
		}

		Identifier selection = currentSelection();
		context.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height);

		int startIndex = this.scroll * this.columns;

		for (int row = 0; row < this.rows; row++) {
			for (int column = 0; column < this.columns; column++) {
				int index = startIndex + row * this.columns + column;

				if (index >= this.visible.size()) {
					break;
				}

				Block block = this.visible.get(index);
				int cellX = this.x + 1 + column * CELL;
				int cellY = this.y + 1 + row * CELL;
				Identifier id = Registries.BLOCK.getId(block);
				boolean hoveredCell = inside && mouseX >= cellX && mouseX < cellX + CELL && mouseY >= cellY
						&& mouseY < cellY + CELL;

				if (hoveredCell) {
					this.hovered = block;
				}

				if (id.equals(selection)) {
					context.fill(cellX - 1, cellY - 1, cellX + CELL - 1, cellY + CELL - 1, 0xFF_2F6F3E);
				} else if (hoveredCell) {
					context.fill(cellX - 1, cellY - 1, cellX + CELL - 1, cellY + CELL - 1, 0x60_FFFFFF);
				}

				context.drawItem(new ItemStack(block), cellX, cellY);
			}
		}

		context.disableScissor();

		// Scrollbar
		int totalRows = (this.visible.size() + this.columns - 1) / this.columns;

		if (totalRows > this.rows) {
			int trackX = this.x + this.width - 2;
			int trackHeight = this.height - 2;
			int thumbHeight = Math.max(8, trackHeight * this.rows / totalRows);
			int maxScroll = totalRows - this.rows;
			int thumbY = this.y + 1 + (maxScroll == 0 ? 0 : (trackHeight - thumbHeight) * this.scroll / maxScroll);
			context.fill(trackX, this.y + 1, trackX + 1, this.y + 1 + trackHeight, 0x60_FFFFFF);
			context.fill(trackX, thumbY, trackX + 1, thumbY + thumbHeight, 0xFF_8AB4F8);
		}
	}

	public Optional<Block> mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || this.visible.isEmpty()) {
			return Optional.empty();
		}

		if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.y || mouseY >= this.y + this.height) {
			return Optional.empty();
		}

		int column = (int) ((mouseX - this.x - 1) / CELL);
		int row = (int) ((mouseY - this.y - 1) / CELL);

		if (column < 0 || column >= this.columns || row < 0 || row >= this.rows) {
			return Optional.empty();
		}

		int index = (this.scroll + row) * this.columns + column;

		if (index < 0 || index >= this.visible.size()) {
			return Optional.empty();
		}

		return Optional.of(this.visible.get(index));
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.y || mouseY >= this.y + this.height) {
			return false;
		}

		int previous = this.scroll;
		this.scroll += amount > 0 ? -2 : 2;
		clampScroll();
		return this.scroll != previous || amount != 0;
	}
}
