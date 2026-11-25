package com.whiteclient.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
//#if MC >= 1.21.11
import com.mojang.blaze3d.textures.GpuSampler;
//#endif
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphics.class)
public interface DrawContextInvoker {
	@Invoker("submitBlit")
	//#if MC >= 1.21.11
	void whiteclient$texturedQuad(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler, int x0, int y0, int x1, int y1,
								  float u0, float u1, float v0, float v1, int color);
	//#else
	//$ void whiteclient$texturedQuad(RenderPipeline pipeline, GpuTextureView texture, int x0, int y0, int x1, int y1,
	//$ 							  float u0, float u1, float v0, float v1, int color);
	//#endif
}
