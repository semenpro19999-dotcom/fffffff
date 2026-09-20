package com.worldstudio.item;

import com.worldstudio.edit.EditSession;
import com.worldstudio.edit.Permissions;
import com.worldstudio.edit.SessionManager;
import com.worldstudio.net.StudioNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Studio Wand. Right click a block to mark the two corners of a region, sneak right click to
 * open the editor panel.
 */
public class StudioWandItem extends Item {
	public StudioWandItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();

		if (player == null) {
			return ActionResult.PASS;
		}

		if (player.isSneaking()) {
			openEditor(world, player);
			return ActionResult.success(world.isClient);
		}

		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
			EditSession session = SessionManager.get(serverPlayer);

			if (!Permissions.canEdit(serverPlayer)) {
				session.message(serverPlayer, Permissions.deniedMessage());
				return ActionResult.FAIL;
			}

			BlockPos pos = context.getBlockPos().toImmutable();
			int point = session.setNextSelection(pos);
			session.message(serverPlayer, Text.translatable("worldstudio.message.point_set",
					Text.literal(String.valueOf(point + 1)).formatted(Formatting.YELLOW),
					pos.getX(), pos.getY(), pos.getZ()).formatted(Formatting.AQUA));
			session.syncTo(serverPlayer);
		}

		return ActionResult.success(world.isClient);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (user.isSneaking()) {
			openEditor(world, user);
			return TypedActionResult.success(stack);
		}

		return TypedActionResult.pass(stack);
	}

	private static void openEditor(World world, PlayerEntity player) {
		if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return;
		}

		EditSession session = SessionManager.get(serverPlayer);

		if (!Permissions.canEdit(serverPlayer)) {
			session.message(serverPlayer, Permissions.deniedMessage());
			return;
		}

		StudioNetworking.sendOpenEditor(serverPlayer, session);
	}
}
