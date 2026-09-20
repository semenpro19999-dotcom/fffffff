package com.worldstudio.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.worldstudio.client.ClientState;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Draws the region selection (or the single marked corner) directly in the world. */
public final class SelectionRenderer {
	private SelectionRenderer() {
	}

	public static void register() {
		WorldRenderEvents.LAST.register(SelectionRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		if (!ClientState.showSelection) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		BlockPos a = ClientState.pos1;
		BlockPos b = ClientState.pos2;

		if (client.player == null || client.world == null || (a == null && b == null)) {
			return;
		}

		Camera camera = context.camera();
		Vec3d cameraPos = camera.getPos();
		MatrixStack matrices = context.matrixStack();
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();

		matrices.push();
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.lineWidth(2.0F);

		buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

		if (a != null && b != null) {
			int minX = Math.min(a.getX(), b.getX());
			int minY = Math.min(a.getY(), b.getY());
			int minZ = Math.min(a.getZ(), b.getZ());
			int maxX = Math.max(a.getX(), b.getX()) + 1;
			int maxY = Math.max(a.getY(), b.getY()) + 1;
			int maxZ = Math.max(a.getZ(), b.getZ()) + 1;
			WorldRenderer.drawBox(matrices, buffer, minX, minY, minZ, maxX, maxY, maxZ, 0.24F, 0.82F, 1.0F, 0.95F);
		} else {
			BlockPos only = a != null ? a : b;
			WorldRenderer.drawBox(matrices, buffer, only.getX(), only.getY(), only.getZ(),
					only.getX() + 1, only.getY() + 1, only.getZ() + 1, 1.0F, 0.78F, 0.22F, 0.95F);
		}

		tessellator.draw();

		RenderSystem.lineWidth(1.0F);
		RenderSystem.disableBlend();
		RenderSystem.enableCull();
		matrices.pop();
	}
}
