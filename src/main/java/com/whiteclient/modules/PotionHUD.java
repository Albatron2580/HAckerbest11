package com.whiteclient.modules;

import com.whiteclient.gui.Render2D;
import com.whiteclient.hud.Hud;
import com.whiteclient.hud.HudLayout;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

public class PotionHUD extends Module {
	public final ModeSetting position = add(new ModeSetting("Position", "Top Right", HudLayout.POSITIONS));
	public final BoolSetting hideVanilla = add(new BoolSetting("Hide Vanilla Icons", true));

	private static final String[] ROMAN = {"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	public PotionHUD() {
		super("Potion HUD", "Active effects with time left", Category.HUD, true);
	}

	public boolean hidesVanilla() {
		return isEnabled() && hideVanilla.isOn();
	}

	@Override
	public void renderHud(GuiGraphics ctx, float tickDelta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
		effects.removeIf(e -> !e.showIcon());
		if (effects.isEmpty()) {
			return;
		}
		effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration));
		Font tr = mc.font;
		float tickRate = mc.level.tickRateManager().tickrate();
		List<String[]> rows = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		float nameW = 0, timeW = 0;
		for (MobEffectInstance e : effects) {
			String name = e.getEffect().value().getDisplayName().getString();
			int amp = e.getAmplifier();
			if (amp > 0) {
				name += " " + (amp < ROMAN.length ? ROMAN[amp] : String.valueOf(amp + 1));
			}
			String time = e.isInfiniteDuration() ? "∞" : MobEffectUtil.formatDuration(e, 1.0f, tickRate).getString();
			rows.add(new String[]{name, time});
			colors.add(0xFF000000 | e.getEffect().value().getColor());
			nameW = Math.max(nameW, tr.width(name));
			timeW = Math.max(timeW, tr.width(time));
		}
		float lh = 12;
		float w = 8 + 6 + nameW + 10 + timeW + 8, h = 5 + rows.size() * lh + 2;
		float[] p = HudLayout.place(position.get(), w, h);
		Hud.panel(ctx, p[0], p[1], w, h, 1f);
		float y = p[1] + 6;
		for (int i = 0; i < rows.size(); i++) {
			String[] r = rows.get(i);
			MobEffectInstance e = effects.get(i);
			boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
			float blink = ending ? 0.55f + 0.45f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 180.0)) : 1f;
			Render2D.circle(ctx, p[0] + 10, y + 3.5f, 2.6f, colors.get(i));
			Render2D.text(ctx, tr, r[0], p[0] + 17, y, Hud.TEXT);
			Render2D.text(ctx, tr, r[1], p[0] + w - 8 - tr.width(r[1]), y, Hud.alpha(Hud.SUB, blink));
			y += lh;
		}
	}
}
