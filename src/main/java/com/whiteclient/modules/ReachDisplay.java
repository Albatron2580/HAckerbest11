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

import java.util.Locale;

public class ReachDisplay extends Module {
	public final ModeSetting position = add(new ModeSetting("Position", "Right", HudLayout.POSITIONS));
	public final BoolSetting always = add(new BoolSetting("Always Show", false));

	public ReachDisplay() {
		super("Reach Display", "Distance of your last hit", Category.PVP, true);
	}

	@Override
	public void renderHud(DrawContext ctx, float tickDelta) {
		long age = System.currentTimeMillis() - CombatTracker.lastReachAt();
		boolean has = CombatTracker.lastReachAt() > 0;
		float a = always.isOn() ? 1f : (has ? Math.min(1f, Math.max(0f, (3000 - age) / 400f)) : 0f);
		if (a <= 0.02f) {
			return;
		}
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		String value = has ? String.format(Locale.ROOT, "%.2f", CombatTracker.lastReach()) : "-";
		String label = " blocks";
		float w = tr.getWidth(value) + tr.getWidth(label) + 16, h = 20;
		float[] p = HudLayout.place(position.get(), w, h);
		Hud.panel(ctx, p[0], p[1], w, h, a);
		Render2D.text(ctx, tr, value, p[0] + 8, p[1] + 6, Hud.alpha(Hud.TEXT, a));
		Render2D.text(ctx, tr, label, p[0] + 8 + tr.getWidth(value), p[1] + 6, Hud.alpha(Hud.SUB, a));
	}
}
