package com.whiteclient.modules;

import com.whiteclient.combat.CombatTracker;
import com.whiteclient.gui.Render2D;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class CustomCrosshair extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Cross + Dot", "Cross", "Cross + Dot", "Dot", "Circle", "Circle + Dot", "T-Shape", "Chevron", "Square"));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final BoolSetting outline = add(new BoolSetting("Outline", true));
	public final NumberSetting size = add(new NumberSetting("Size", 4.5, 2, 12, 0.5));
	public final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 8, 0.5));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", 1.2, 0.5, 3, 0.1));
	public final BoolSetting dynamic = add(new BoolSetting("Dynamic Gap", true));
	public final BoolSetting hitColor = add(new BoolSetting("Flash On Hit", true));
	public final BoolSetting cooldown = add(new BoolSetting("Attack Cooldown", true));

	private float spread;
	private long lastFrame = System.nanoTime();

	public CustomCrosshair() {
		super("Custom Crosshair", "A clean custom crosshair", Category.HUD, true);
	}

	public void draw(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options == null || !mc.options.getPerspective().isFirstPerson() || mc.player.isSpectator()) {
			return;
		}
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
		float target = 0;
		if (dynamic.isOn()) {
			double v = mc.player.getVelocity().horizontalLength();
			target = (float) Math.min(4, v * 14) + (mc.player.isOnGround() ? 0 : 1.5f);
		}
		spread += (target - spread) * Math.min(1f, dt * 10f);
		int rgb = color.rgb();
		if (hitColor.isOn()) {
			float u = (System.currentTimeMillis() - CombatTracker.lastHitAt()) / 250f;
			if (u >= 0 && u < 1) {
				rgb = Colors.mix(0xFF4D6A, rgb, u);
			}
		}
		int col = 0xFF000000 | rgb;
		float s = size.getFloat(), g = gap.getFloat() + spread, t = thickness.getFloat();
		if (outline.isOn()) {
			shape(ctx, cx, cy, s, g, t + 1.4f, 0x99000000, true);
		}
		shape(ctx, cx, cy, s, g, t, col, false);
		if (cooldown.isOn()) {
			float cd = mc.player.getAttackCooldownProgress(0f);
			if (cd < 0.999f) {
				float w = 16, by = cy + s + g + 5;
				Render2D.roundRect(ctx, cx - w / 2f, by, w, 2.5f, 1.25f, 0x88000000);
				Render2D.roundRect(ctx, cx - w / 2f, by, Math.max(2.5f, w * cd), 2.5f, 1.25f, col);
			}
		}
	}

	private void bar(DrawContext ctx, float x1, float y1, float x2, float y2, int col) {
		Render2D.roundRect(ctx, Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1), 0.4f, col);
	}

	private void shape(DrawContext ctx, float cx, float cy, float s, float g, float t, int col, boolean back) {
		float h = t / 2f;
		boolean cross = style.is("Cross") || style.is("Cross + Dot");
		if (cross || style.is("T-Shape")) {
			bar(ctx, cx - g - s, cy - h, cx - g, cy + h, col);
			bar(ctx, cx + g, cy - h, cx + g + s, cy + h, col);
			bar(ctx, cx - h, cy + g, cx + h, cy + g + s, col);
			if (cross) {
				bar(ctx, cx - h, cy - g - s, cx + h, cy - g, col);
			}
		}
		if (style.is("Circle") || style.is("Circle + Dot")) {
			float r = g + s * 0.6f;
			Render2D.roundRectOutline(ctx, cx - r, cy - r, r * 2, r * 2, r, t, col);
		}
		if (style.is("Square")) {
			float r = g + s * 0.5f;
			Render2D.roundRectOutline(ctx, cx - r, cy - r, r * 2, r * 2, 1, t, col);
		}
		if (style.is("Chevron")) {
			Render2D.line(ctx, cx - s, cy + s * 0.6f + g * 0.5f, cx, cy + g * 0.5f - s * 0.1f, t, col);
			Render2D.line(ctx, cx, cy + g * 0.5f - s * 0.1f, cx + s, cy + s * 0.6f + g * 0.5f, t, col);
		}
		if (style.is("Dot") || style.get().endsWith("+ Dot")) {
			float r = Math.max(1f, t * 0.9f) + (back ? 0.6f : 0f);
			Render2D.circle(ctx, cx, cy, r, col);
		}
	}
}
