package com.whiteclient.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the browser window draw the page texture (a GPU texture view) in the GUI. */
@Mixin(DrawContext.class)
public interface DrawContextInvoker {
	@Invoker("drawTexturedQuad")
	void whiteclient$texturedQuad(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler, int x0, int y0, int x1, int y1,
								  float u0, float u1, float v0, float v1, int color);
}
