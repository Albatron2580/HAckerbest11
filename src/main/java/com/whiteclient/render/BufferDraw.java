//#file MC < 26.2
package com.whiteclient.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public final class BufferDraw implements WorldDraw {
	private final MultiBufferSource consumers;

	public BufferDraw(MultiBufferSource consumers) {
		this.consumers = consumers;
	}

	public MultiBufferSource consumers() {
		return consumers;
	}

	@Override
	public boolean immediate() {
		return true;
	}

	@Override
	public void geometry(RenderType layer, Consumer<VertexConsumer> emitter) {
		emitter.accept(consumers.getBuffer(layer));
	}

	@Override
	public void text(Font font, String text, float x, float y, int argb, Matrix4f matrix, int light) {
		font.drawInBatch(text, x, y, argb, false, matrix, consumers, Font.DisplayMode.NORMAL, 0, light);
	}
}
