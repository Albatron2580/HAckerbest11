//#file MC >= 26.2
package com.whiteclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public final class SubmitDraw implements WorldDraw {
	private final SubmitNodeCollector out;

	public SubmitDraw(SubmitNodeCollector out) {
		this.out = out;
	}

	@Override
	public boolean immediate() {
		return false;
	}

	@Override
	public void geometry(RenderType layer, Consumer<VertexConsumer> emitter) {
		out.submitCustomGeometry(new PoseStack(), layer, (pose, buffer) -> emitter.accept(buffer));
	}

	@Override
	public void text(Font font, String text, float x, float y, int argb, Matrix4f matrix, int light) {
		PoseStack ps = new PoseStack();
		ps.last().pose().set(matrix);
		out.submitText(ps, x, y, Component.literal(text).getVisualOrderText(), false, Font.DisplayMode.NORMAL, light, argb, 0, 0);
	}
}
