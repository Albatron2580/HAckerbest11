package com.whiteclient.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public final class Gfx {
	private Gfx() {
	}

	public static void blit(GuiGraphics ctx, Identifier id, int x, int y, float u, float v, int w, int h, int regionW, int regionH,
							int texW, int texH, int argb) {
		//#if MC >= 1.21.6
		ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, regionW, regionH, texW, texH, argb);
		//#elif MC >= 1.21.2
		//$ ctx.blit(net.minecraft.client.renderer.RenderType::guiTextured, id, x, y, u, v, w, h, regionW, regionH, texW, texH, argb);
		//#else
		//$ com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		//$ com.mojang.blaze3d.systems.RenderSystem.setShaderColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f,
		//$ 		((argb >>> 24) & 255) / 255f);
		//$ ctx.blit(id, x, y, w, h, u, v, regionW, regionH, texW, texH);
		//$ com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		//#endif
	}
}
