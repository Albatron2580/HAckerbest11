package com.whiteclient.modules;

import com.whiteclient.combat.CombatTracker;
import com.whiteclient.gui.Render2D;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

public class Hitmarker extends Module {
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final ColorSetting killColor = add(new ColorSetting("Kill Color", 0xFF4D6A));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05, "x"));

	public static long debugHitAt;

	public Hitmarker() {
		super("Hitmarker", "An X on your crosshair when you hit", Category.PVP, true);
	}

	@Override
	public void renderHud(GuiGraphics ctx, float tickDelta) {
		long now = System.currentTimeMillis();
		long hitAt = Math.max(CombatTracker.lastHitAt(), debugHitAt);
		float u = (now - hitAt) / 320f;
		if (u >= 1f || u < 0f) {
			return;
		}
		boolean kill = now - CombatTracker.lastKillAt() < 600;
		float a = 1f - u * u;
		float pop = 1f + 0.4f * (1f - u) * (1f - u);
		float cx = ctx.guiWidth() / 2f, cy = ctx.guiHeight() / 2f;
		float s = size.getFloat() * pop;
		float r1 = 3.5f * s, r2 = 8f * s;
		int col = Colors.argb(kill ? killColor.rgb() : color.rgb(), a);
		int shadow = Colors.argb(0x000000, 0.45f * a);
		for (int i = 0; i < 4; i++) {
			float dx = (i == 0 || i == 3) ? 1 : -1, dy = (i < 2) ? 1 : -1;
			float k = 0.7071f;
			Render2D.line(ctx, cx + dx * r1 * k + 0.5f, cy + dy * r1 * k + 0.5f, cx + dx * r2 * k + 0.5f, cy + dy * r2 * k + 0.5f, 2.2f, shadow);
			Render2D.line(ctx, cx + dx * r1 * k, cy + dy * r1 * k, cx + dx * r2 * k, cy + dy * r2 * k, 1.4f, col);
		}
	}
}
