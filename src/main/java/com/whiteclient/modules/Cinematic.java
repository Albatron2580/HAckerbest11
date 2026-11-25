package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

public class Cinematic extends Module {
	public final NumberSetting bars = add(new NumberSetting("Bars", 0.11, 0.0, 0.25, 0.01));
	public final ModeSetting grade = add(new ModeSetting("Color", "Warm", "None", "Warm", "Cool", "Dream", "Night"));
	public final NumberSetting strength = add(new NumberSetting("Color Strength", 0.5, 0.0, 1.0, 0.05));
	public final NumberSetting vignette = add(new NumberSetting("Vignette", 0.45, 0.0, 1.0, 0.05));

	private float anim;
	private long last;

	public Cinematic() {
		super("Cinematic", "Movie bars and a soft colour grade (great for videos)", Category.HUD, false);
	}

	@Override
	protected void onEnable() {
		anim = 0;
		last = System.nanoTime();
	}

	@Override
	public void renderHud(GuiGraphics ctx, float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1.0e9f);
		last = now;
		anim = Math.min(1f, anim + dt * 2.2f);
		float k = 1f - (1f - anim) * (1f - anim) * (1f - anim);
		int w = ctx.guiWidth(), h = ctx.guiHeight();
		float st = strength.getFloat() * k;
		int tint = switch (grade.get()) {
			case "Warm" -> 0xFF9A4D;
			case "Cool" -> 0x4DA3FF;
			case "Dream" -> 0xD28CFF;
			case "Night" -> 0x1B2A55;
			default -> -1;
		};
		if (tint >= 0 && st > 0.01f) {
			ctx.fillGradient(0, 0, w, h, Colors.argb(tint, 0.16f * st), Colors.argb(tint, 0.06f * st));
		}
		float v = vignette.getFloat() * k;
		if (v > 0.01f) {
			int edge = Math.max(8, w / 5);
			for (int i = 0; i < 8; i++) {
				float a = v * 0.07f * (8 - i) / 8f;
				int e = edge * (i + 1) / 8;
				int c = Colors.argb(0x000000, a);
				ctx.fill(0, 0, e, h, c);
				ctx.fill(w - e, 0, w, h, c);
			}
		}
		int bh = Math.round(h * bars.getFloat() * k);
		if (bh > 0) {
			ctx.fill(0, 0, w, bh, 0xFF000000);
			ctx.fill(0, h - bh, w, h, 0xFF000000);
		}
	}
}
