package com.worldstudio.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.worldstudio.StudioItems;
import com.worldstudio.WorldStudioMod;
import com.worldstudio.edit.EditExecutor;
import com.worldstudio.edit.EditOp;
import com.worldstudio.edit.EditSession;
import com.worldstudio.edit.Permissions;
import com.worldstudio.edit.SchematicStorage;
import com.worldstudio.edit.SessionManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** {@code /worldstudio} (alias {@code /ws}) mirrors everything the graphical panel can do. */
public final class StudioCommand {
	private StudioCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(build("worldstudio"));
			dispatcher.register(build("ws"));
		});
	}

	private static LiteralArgumentBuilder<ServerCommandSource> build(String name) {
		return CommandManager.literal(name)
				.requires(source -> source.getEntity() instanceof ServerPlayerEntity)
				.then(CommandManager.literal("help").executes(StudioCommand::help))
				.then(CommandManager.literal("wand").executes(StudioCommand::giveWand))
				.then(CommandManager.literal("pos1").executes(context -> setPoint(context, 0)))
				.then(CommandManager.literal("pos2").executes(context -> setPoint(context, 1)))
				.then(CommandManager.literal("sel").executes(StudioCommand::showSelection))
				.then(CommandManager.literal("clearsel").executes(StudioCommand::clearSelection))
				.then(CommandManager.literal("block").then(CommandManager.argument("id", StringArgumentType.string())
						.executes(StudioCommand::setBlock)))
				.then(CommandManager.literal("from").then(CommandManager.argument("id", StringArgumentType.string())
						.executes(StudioCommand::setReplaceFrom)))
				.then(CommandManager.literal("fill").executes(context -> run(context, EditOp.FILL)))
				.then(CommandManager.literal("replace").executes(context -> run(context, EditOp.REPLACE)))
				.then(CommandManager.literal("walls").executes(context -> run(context, EditOp.WALLS)))
				.then(CommandManager.literal("outline").executes(context -> run(context, EditOp.OUTLINE)))
				.then(CommandManager.literal("hollow").executes(context -> run(context, EditOp.HOLLOW)))
				.then(CommandManager.literal("clear").executes(context -> run(context, EditOp.CLEAR)))
				.then(CommandManager.literal("copy").executes(context -> run(context, EditOp.COPY)))
				.then(CommandManager.literal("paste").then(CommandManager
						.argument("keepAir", IntegerArgumentType.integer(0, 1))
						.executes(StudioCommand::pasteExplicit))
						.executes(context -> run(context, EditOp.PASTE)))
				.then(CommandManager.literal("rotate").then(CommandManager.argument("steps",
						IntegerArgumentType.integer(1, 3)).executes(StudioCommand::rotate)))
				.then(CommandManager.literal("undo").executes(StudioCommand::undo))
				.then(CommandManager.literal("redo").executes(StudioCommand::redo))
				.then(CommandManager.literal("save").then(CommandManager.argument("name", StringArgumentType.greedyString())
						.executes(StudioCommand::save)))
				.then(CommandManager.literal("load").then(CommandManager.argument("name", StringArgumentType.greedyString())
						.executes(StudioCommand::load)))
				.then(CommandManager.literal("delete").then(CommandManager.argument("name", StringArgumentType.greedyString())
						.executes(StudioCommand::delete)))
				.then(CommandManager.literal("list").executes(StudioCommand::listSchematics))
				.executes(StudioCommand::help);
	}

	private static ServerPlayerEntity player(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		return context.getSource().getPlayerOrThrow();
	}

	private static EditSession session(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		return SessionManager.get(player(context));
	}

	private static boolean allowed(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
		if (Permissions.canEdit(player)) {
			return true;
		}

		context.getSource().sendFeedback(() -> Permissions.deniedMessage(), false);
		return false;
	}

	private static int help(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		source.sendFeedback(() -> Text.translatable("worldstudio.command.help.title").formatted(Formatting.GOLD), false);

		for (String key : new String[] {
				"worldstudio.command.help.wand",
				"worldstudio.command.help.select",
				"worldstudio.command.help.block",
				"worldstudio.command.help.operations",
				"worldstudio.command.help.clipboard",
				"worldstudio.command.help.history",
				"worldstudio.command.help.schematics",
				"worldstudio.command.help.gui"
		}) {
			source.sendFeedback(() -> Text.translatable(key).formatted(Formatting.GRAY), false);
		}

		return 1;
	}

	private static int giveWand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);
		ItemStack stack = new ItemStack(StudioItems.studioWand);
		player.getInventory().offerOrDrop(stack);
		context.getSource().sendFeedback(() -> Text.translatable("worldstudio.command.gave_wand").formatted(Formatting.GREEN),
				false);
		return 1;
	}

	private static int setPoint(CommandContext<ServerCommandSource> context, int index) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		EditSession session = session(context);
		session.setPoint(index, player.getBlockPos());
		session.syncTo(player);
		context.getSource().sendFeedback(() -> Text.translatable("worldstudio.message.point_set",
				Text.literal(String.valueOf(index + 1)).formatted(Formatting.YELLOW),
				player.getBlockPos().getX(), player.getBlockPos().getY(), player.getBlockPos().getZ())
				.formatted(Formatting.AQUA), true);
		return 1;
	}

	private static int showSelection(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		EditSession session = session(context);
		ServerCommandSource source = context.getSource();

		if (!session.hasSelection()) {
			source.sendFeedback(() -> Text.translatable("worldstudio.status.no_selection").formatted(Formatting.RED), false);
			return 0;
		}

		source.sendFeedback(() -> Text.translatable("worldstudio.status.selection",
				session.selectionMax().getX() - session.selectionMin().getX() + 1,
				session.selectionMax().getY() - session.selectionMin().getY() + 1,
				session.selectionMax().getZ() - session.selectionMin().getZ() + 1,
				session.selectionVolume()).formatted(Formatting.AQUA), false);
		return 1;
	}

	private static int clearSelection(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);
		EditSession session = session(context);
		session.clearSelection();
		session.syncTo(player);
		context.getSource().sendFeedback(() -> Text.translatable("worldstudio.command.selection_cleared")
				.formatted(Formatting.YELLOW), true);
		return 1;
	}

	private static int setBlock(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		String id = StringArgumentType.getString(context, "id");
		BlockState state = EditExecutor.resolveState(id);

		if (state == null) {
			context.getSource().sendFeedback(() -> Text.translatable("worldstudio.error.unknown_block", id)
					.formatted(Formatting.RED), false);
			return 0;
		}

		EditSession session = session(context);
		session.setSelectedBlock(state);
		session.syncTo(player);
		context.getSource().sendFeedback(() -> Text.translatable("worldstudio.command.block_set",
				state.getBlock().getName()).formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int setReplaceFrom(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		String id = StringArgumentType.getString(context, "id");
		BlockState state = EditExecutor.resolveState(id);

		if (state == null) {
			context.getSource().sendFeedback(() -> Text.translatable("worldstudio.error.unknown_block", id)
					.formatted(Formatting.RED), false);
			return 0;
		}

		EditSession session = session(context);
		session.setReplaceFrom(state.getBlock());
		session.syncTo(player);
		context.getSource().sendFeedback(() -> Text.translatable("worldstudio.command.replace_from_set",
				state.getBlock().getName()).formatted(Formatting.GREEN), true);
		return 1;
	}

	private static int run(CommandContext<ServerCommandSource> context, EditOp op) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		EditExecutor.execute(player, session(context), op, WorldStudioMod.config().pasteIgnoresAir);
		return 1;
	}

	private static int pasteExplicit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		int keepAir = IntegerArgumentType.getInteger(context, "keepAir");
		EditExecutor.execute(player, session(context), EditOp.PASTE, keepAir == 0);
		return 1;
	}

	private static int rotate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		EditExecutor.rotate(player, session(context), IntegerArgumentType.getInteger(context, "steps"));
		return 1;
	}

	private static int undo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		EditExecutor.undo(player, session(context));
		return 1;
	}

	private static int redo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		EditExecutor.redo(player, session(context));
		return 1;
	}

	private static int save(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		EditExecutor.saveSchematic(player, session(context), StringArgumentType.getString(context, "name"));
		return 1;
	}

	private static int load(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		EditExecutor.loadSchematic(player, session(context), StringArgumentType.getString(context, "name"));
		return 1;
	}

	private static int delete(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);

		if (!allowed(context, player)) {
			return 0;
		}

		EditExecutor.deleteSchematic(player, session(context), StringArgumentType.getString(context, "name"));
		return 1;
	}

	private static int listSchematics(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = player(context);
		List<String> names = SchematicStorage.list(player.getServer());
		ServerCommandSource source = context.getSource();

		if (names.isEmpty()) {
			source.sendFeedback(() -> Text.translatable("worldstudio.command.no_schematics").formatted(Formatting.GRAY),
					false);
			return 0;
		}

		source.sendFeedback(() -> Text.translatable("worldstudio.command.schematics", names.size())
				.formatted(Formatting.GOLD), false);

		for (String name : names) {
			source.sendFeedback(() -> Text.literal(" - " + name).formatted(Formatting.AQUA), false);
		}

		return names.size();
	}
}
