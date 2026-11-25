package com.whiteclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

public final class WorldText {
	private WorldText() {
	}

	public static void draw(WorldContext ctx, float x, float y, float z, String text, float scale, int argb, boolean shadow) {
		Font tr = ctx.client.font;
		PoseStack ms = ctx.matrices;
		ms.pushPose();
		ms.translate(x, y, z);
		ms.mulPose(ctx.camRotation);
		float s = 0.025f * scale;
		ms.scale(s, -s, s);
		Matrix4f m = ms.last().pose();
		float w = tr.width(text);
		int light = LightTexture.FULL_BRIGHT;
		if (shadow) {
			int a = (argb >>> 24) & 0xFF;
			int dark = (Math.max(0, a - 40) << 24) | 0x101014;
			ms.translate(0, 0, 0.01f);
			Matrix4f back = ms.last().pose();
			tr.drawInBatch(text, -w / 2f + 1, 1, dark, false, back, ctx.consumers, Font.DisplayMode.NORMAL, 0, light);
		}
		tr.drawInBatch(text, -w / 2f, 0, argb, false, m, ctx.consumers, Font.DisplayMode.NORMAL, 0, light);
		ms.popPose();
	}
}
