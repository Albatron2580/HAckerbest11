package com.whiteclient.gui;

import com.whiteclient.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.player.SkinTextures;

import java.util.function.Supplier;

/** A 3D player model for menus (uses the vanilla skin widget) that can walk and idle. */
public final class SkinPreview {
	private final PlayerSkinWidget widget;

	public SkinPreview(int width, int height, Supplier<SkinTextures> skin) {
		MinecraftClient mc = MinecraftClient.getInstance();
		widget = new PlayerSkinWidget(width, height, mc.getLoadedEntityModels(), skin);
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

	/**
	 * Poses arms and legs. {@code walk} 0..1 blends from idle to walking,
	 * {@code phase} advances the walk cycle, {@code time} drives the idle sway.
	 */
	public void pose(float phase, float walk, float time) {
		PlayerEntityModel[] models;
		try {
			models = new PlayerEntityModel[]{acc().whiteclient$wideModel(), acc().whiteclient$slimModel()};
		} catch (Throwable t) {
			return;
		}
		float swing = (float) Math.sin(phase) * walk;
		float idle = (float) Math.sin(time * 1.6f) * (1f - walk);
		for (PlayerEntityModel m : models) {
			if (m == null) {
				continue;
			}
			m.rightLeg.pitch = swing * 1.0f;
			m.leftLeg.pitch = -swing * 1.0f;
			m.rightArm.pitch = -swing * 0.85f + idle * 0.03f;
			m.leftArm.pitch = swing * 0.85f - idle * 0.03f;
			m.rightArm.roll = 0.06f + idle * 0.035f + 0.03f * (1f - walk);
			m.leftArm.roll = -0.06f - idle * 0.035f - 0.03f * (1f - walk);
			m.head.pitch = 0.05f * walk + 0.03f * idle;
			m.head.yaw = 0.12f * (float) Math.sin(time * 0.55f) * (1f - walk);
		}
	}

	public void render(DrawContext ctx, int x, int y, int mouseX, int mouseY, float delta) {
		widget.setPosition(x, y);
		widget.render(ctx, mouseX, mouseY, delta);
	}
}
