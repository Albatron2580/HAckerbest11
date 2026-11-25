//#file MC >= 26.2
package com.whiteclient.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.whiteclient.WhiteClient;
import com.whiteclient.mixin.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;

public final class GlowLayer {
	private GlowLayer() {
	}

	public static RenderType create() {
		try {
			Field f = RenderPipelines.class.getDeclaredField("MATRICES_FOG_SNIPPET");
			f.setAccessible(true);
			RenderPipeline.Snippet snippet = (RenderPipeline.Snippet) f.get(null);
			RenderPipeline pipeline = RenderPipeline.builder(snippet)
					.withLocation(Identifier.fromNamespaceAndPath(WhiteClient.MOD_ID, "pipeline/glow"))
					.withVertexShader("core/rendertype_lightning")
					.withFragmentShader("core/rendertype_lightning")
					.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
					.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
					.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.build();
			return RenderTypeInvoker.whiteclient$create("whiteclient_glow", RenderSetup.builder(pipeline).createRenderSetup());
		} catch (Throwable t) {
			WhiteClient.LOGGER.warn("[WHITECLIENT] own glow layer unavailable, using dragon rays", t);
			return null;
		}
	}
}
