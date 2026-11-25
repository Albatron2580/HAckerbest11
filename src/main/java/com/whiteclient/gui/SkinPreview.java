package com.whiteclient.gui;

import com.whiteclient.mixin.PlayerSkinWidgetAccessor;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.player.PlayerSkin;

public final class SkinPreview {
	private final PlayerSkinWidget widget;

	public SkinPreview(int width, int height, Supplier<PlayerSkin> skin) {
		Minecraft mc = Minecraft.getInstance();
		widget = new PlayerSkinWidget(width, height, mc.getEntityModels(), skin);
	}

	public PlayerSkinWidget widget() {
		return widget;
	}

	private PlayerSkinWidgetAccessor acc() {
		return (PlayerSkinWidgetAccessor) (Object) widget;
	}

	public void setRotation(float x, float y) {
		try {
			acc().whiteclient$setXRotation(x);
			acc().whiteclient$setYRotation(y);
		} catch (Throwable ignored) {
		}
	}

	public float yRotation() {
		try {
			return acc().whiteclient$getYRotation();
		} catch (Throwable t) {
			return 0;
		}
	}

	public void pose(float phase, float walk, float time) {
		PlayerModel[] models;
		try {
			models = new PlayerModel[]{acc().whiteclient$wideModel(), acc().whiteclient$slimModel()};
		} catch (Throwable t) {
			return;
		}
		float swing = (float) Math.sin(phase) * walk;
		float idle = (float) Math.sin(time * 1.6f) * (1f - walk);
		for (PlayerModel m : models) {
			if (m == null) {
				continue;
			}
			m.rightLeg.xRot = swing * 1.0f;
			m.leftLeg.xRot = -swing * 1.0f;
			m.rightArm.xRot = -swing * 0.85f + idle * 0.03f;
			m.leftArm.xRot = swing * 0.85f - idle * 0.03f;
			m.rightArm.zRot = 0.06f + idle * 0.035f + 0.03f * (1f - walk);
			m.leftArm.zRot = -0.06f - idle * 0.035f - 0.03f * (1f - walk);
			m.head.xRot = 0.05f * walk + 0.03f * idle;
			m.head.yRot = 0.12f * (float) Math.sin(time * 0.55f) * (1f - walk);
		}
	}

	public void render(GuiGraphics ctx, int x, int y, int mouseX, int mouseY, float delta) {
		widget.setPosition(x, y);
		widget.render(ctx, mouseX, mouseY, delta);
	}
}
