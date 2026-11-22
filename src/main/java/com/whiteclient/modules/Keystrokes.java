package com.whiteclient.modules;

import com.whiteclient.gui.Render2D;
import com.whiteclient.hud.ClickCounter;
import com.whiteclient.hud.Hud;
import com.whiteclient.hud.HudLayout;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

/** WASD, space and mouse buttons with clicks per second. */
public class Keystrokes extends Module {
	public final ModeSetting position = add(new ModeSetting("Position", "Left", HudLayout.POSITIONS));
	public final BoolSetting mouse = add(new BoolSetting("Mouse Buttons", true));
	public final BoolSetting cps = add(new BoolSetting("Show CPS", true));
	public final BoolSetting space = add(new BoolSetting("Space Bar", true));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.6, 1.6, 0.05, "x"));

	private final float[] press = new float[7];
	private long lastFrame = System.nanoTime();
	/** Test hook: keys shown as held (bit mask W A S D LMB RMB SPACE). */
	public static int debugHeld;

	public Keystrokes() {
		super("Keystrokes", "WASD + mouse with CPS", Category.HUD, false);
	}

	@Override
	public void renderHud(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options == null) {
			return;
		}
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		KeyBinding[] keys = {mc.options.forwardKey, mc.options.leftKey, mc.options.backKey, mc.options.rightKey,
				mc.options.attackKey, mc.options.useKey, mc.options.jumpKey};
		for (int i = 0; i < keys.length; i++) {
			boolean down = keys[i].isPressed() || (debugHeld & (1 << i)) != 0;
			press[i] += ((down ? 1f : 0f) - press[i]) * Math.min(1f, dt * 18f);
		}
		float sc = scale.getFloat();
		float k = 22, gp = 2;
		float w = k * 3 + gp * 2;
		float h = k * 2 + gp + (mouse.isOn() ? k + gp : 0) + (space.isOn() ? 12 + gp : 0);
		float[] p = HudLayout.place(position.get(), w * sc, h * sc);
		var m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(p[0], p[1]);
		m.scale(sc, sc);
		TextRenderer tr = mc.textRenderer;
		key(ctx, tr, k + gp, 0, k, k, label(mc.options.forwardKey, "W"), press[0], null);
		key(ctx, tr, 0, k + gp, k, k, label(mc.options.leftKey, "A"), press[1], null);
		key(ctx, tr, k + gp, k + gp, k, k, label(mc.options.backKey, "S"), press[2], null);
		key(ctx, tr, (k + gp) * 2, k + gp, k, k, label(mc.options.rightKey, "D"), press[3], null);
		float y = (k + gp) * 2;
		if (mouse.isOn()) {
			float mw = (w - gp) / 2f;
			key(ctx, tr, 0, y, mw, k, "LMB", press[4], cps.isOn() ? ClickCounter.left() + " CPS" : null);
			key(ctx, tr, mw + gp, y, mw, k, "RMB", press[5], cps.isOn() ? ClickCounter.right() + " CPS" : null);
			y += k + gp;
		}
		if (space.isOn()) {
			key(ctx, tr, 0, y, w, 12, null, press[6], null);
			float bar = w * 0.4f;
			Render2D.roundRect(ctx, w / 2f - bar / 2f, y + 5.5f, bar, 1.2f, 0.6f, Colors.mixArgb(0xCCFFFFFF, 0xFF15151B, press[6]));
		}
		m.popMatrix();
	}

	private static String label(KeyBinding key, String fallback) {
		String s = key.getBoundKeyLocalizedText().getString();
		return s.length() <= 3 ? s.toUpperCase() : fallback;
	}

	private void key(DrawContext ctx, TextRenderer tr, float x, float y, float w, float h, String text, float down, String sub) {
		int bg = Colors.mixArgb(Colors.withAlpha(0x0D0D11, 200), 0xF0FFFFFF, down);
		Render2D.roundRect(ctx, x, y, w, h, 5, bg);
		Render2D.roundRectOutline(ctx, x, y, w, h, 5, 1, Colors.withAlpha(0xFFFFFF, (int) (36 + 60 * down)));
		int fg = Colors.mixArgb(0xFFFFFFFF, 0xFF15151B, down);
		if (text != null) {
			float tw = tr.getWidth(text);
			if (sub != null) {
				Render2D.text(ctx, tr, text, x + w / 2f - tw / 2f, y + 3.5f, fg);
				float sw = tr.getWidth(sub) * 0.6f;
				Render2D.textScaled(ctx, tr, sub, x + w / 2f - sw / 2f, y + 13.5f, 0.6f, Colors.mixArgb(0xFFA8A8B4, 0xFF3A3A44, down));
			} else {
				Render2D.text(ctx, tr, text, x + w / 2f - tw / 2f, y + h / 2f - 4, fg);
			}
		}
	}
}
