package com.whiteclient.modules;

import com.whiteclient.gui.Render2D;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class LowHealth extends Module {
	public final NumberSetting hearts = add(new NumberSetting("Below Hearts", 4, 1, 10, 0.5));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFF2840));
	public final NumberSetting strength = add(new NumberSetting("Strength", 0.55, 0.1, 1.0, 0.05));

	public static float debugHealth = -1;

	public LowHealth() {
		super("Low Health Glow", "Red glow around the screen at low health", Category.HUD, true);
	}

	@Override
	public void renderHud(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
			return;
		}
		float hp = debugHealth >= 0 ? debugHealth : mc.player.getHealth();
		float limit = hearts.getFloat() * 2f;
		if (hp > limit || hp <= 0) {
			return;
		}
		float k = 1f - hp / limit;
		float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / (k > 0.6f ? 140.0 : 260.0));
		float a = strength.getFloat() * (0.35f + 0.65f * k) * pulse;
		int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
		int c = color.rgb();
		int edge = Colors.argb(c, a), none = Colors.argb(c, 0f);
		int band = Math.max(20, (int) (Math.min(w, h) * 0.22f));
		ctx.fillGradient(0, 0, w, band, edge, none);
		ctx.fillGradient(0, h - band, w, h, none, edge);
		Render2D.hGradient(ctx, 0, 0, band, h, edge, none);
		Render2D.hGradient(ctx, w - band, 0, band, h, none, edge);
	}
}
