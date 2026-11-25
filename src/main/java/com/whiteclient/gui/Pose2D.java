package com.whiteclient.gui;

import net.minecraft.client.gui.GuiGraphics;

public final class Pose2D {
	//#if MC >= 1.21.6
	private final org.joml.Matrix3x2fStack s;
	//#else
	//$ private final com.mojang.blaze3d.vertex.PoseStack s;
	//#endif

	private Pose2D(GuiGraphics g) {
		s = g.pose();
	}

	public static Pose2D of(GuiGraphics g) {
		return new Pose2D(g);
	}

	public void pushMatrix() {
		//#if MC >= 1.21.6
		s.pushMatrix();
		//#else
		//$ s.pushPose();
		//#endif
	}

	public void popMatrix() {
		//#if MC >= 1.21.6
		s.popMatrix();
		//#else
		//$ s.popPose();
		//#endif
	}

	public void translate(float x, float y) {
		//#if MC >= 1.21.6
		s.translate(x, y);
		//#else
		//$ s.translate(x, y, 0f);
		//#endif
	}

	public void scale(float x, float y) {
		//#if MC >= 1.21.6
		s.scale(x, y);
		//#else
		//$ s.scale(x, y, 1f);
		//#endif
	}

	public void scale(float xy) {
		scale(xy, xy);
	}

	public void rotate(float radians) {
		//#if MC >= 1.21.6
		s.rotate(radians);
		//#else
		//$ s.mulPose(com.mojang.math.Axis.ZP.rotation(radians));
		//#endif
	}
}
