package com.whiteclient.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/** Text floating in the world that always faces the camera. */
public final class WorldText {
	private WorldText() {
	}

	/** Draws centred text at camera-relative x/y/z. Depth tested (hidden behind walls). */
	public static void draw(WorldContext ctx, float x, float y, float z, String text, float scale, int argb, boolean shadow) {
		TextRenderer tr = ctx.client.textRenderer;
		MatrixStack ms = ctx.matrices;
		ms.push();
		ms.translate(x, y, z);
		ms.multiply(ctx.camRotation);
		float s = 0.025f * scale;
		ms.scale(s, -s, s);
		Matrix4f m = ms.peek().getPositionMatrix();
		float w = tr.getWidth(text);
		int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		if (shadow) {
			int a = (argb >>> 24) & 0xFF;
			int dark = (Math.max(0, a - 40) << 24) | 0x101014;
			ms.translate(0, 0, 0.01f);
			Matrix4f back = ms.peek().getPositionMatrix();
			tr.draw(text, -w / 2f + 1, 1, dark, false, back, ctx.consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
		}
		tr.draw(text, -w / 2f, 0, argb, false, m, ctx.consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
		ms.pop();
	}
}
