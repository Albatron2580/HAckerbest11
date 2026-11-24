package com.whiteclient.modules;

import com.whiteclient.combat.CombatTracker;
import com.whiteclient.gui.Render2D;
import com.whiteclient.hud.Hud;
import com.whiteclient.hud.HudLayout;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class ComboCounter extends Module {
	public final ModeSetting position = add(new ModeSetting("Position", "Right", HudLayout.POSITIONS));
	public final BoolSetting always = add(new BoolSetting("Always Show", false));

	private int lastCombo;
	private long popAt;

	public ComboCounter() {
		super("Combo Counter", "Hits in a row without getting hit", Category.PVP, false);
	}

	@Override
	public void renderHud(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int combo = CombatTracker.combo();
		long age = System.currentTimeMillis() - CombatTracker.comboAt();
		if (combo != lastCombo) {
			if (combo > lastCombo) {
				popAt = System.currentTimeMillis();
			}
			lastCombo = combo;
		}
		float a = always.isOn() ? 1f : (combo > 0 ? Math.min(1f, Math.max(0f, (3000 - age) / 400f)) : 0f);
		if (a <= 0.02f) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		String num = String.valueOf(combo);
		String label = combo == 1 ? " hit" : " combo";
		float w = tr.getWidth(num) * 1.5f + tr.getWidth(label) + 16, h = 20;
		float[] p = HudLayout.place(position.get(), w, h);
		Hud.panel(ctx, p[0], p[1], w, h, a);
		float pop = 1f + 0.35f * Math.max(0f, 1f - (System.currentTimeMillis() - popAt) / 250f);
		float s = 1.5f * pop;
		Render2D.textScaled(ctx, tr, num, p[0] + 8, p[1] + 10 - 4 * s, s, Hud.alpha(Hud.TEXT, a));
		Render2D.text(ctx, tr, label, p[0] + 8 + tr.getWidth(num) * 1.5f, p[1] + 7, Hud.alpha(Hud.SUB, a));
	}
}
