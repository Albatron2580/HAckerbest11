package com.whiteclient.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public interface WorldDraw {
	boolean immediate();

	void geometry(RenderType layer, Consumer<VertexConsumer> emitter);

	void text(Font font, String text, float x, float y, int argb, Matrix4f matrix, int light);
}
