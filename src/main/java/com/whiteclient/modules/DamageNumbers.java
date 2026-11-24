package com.whiteclient.modules;

import com.whiteclient.combat.CombatTracker;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.WorldContext;
import com.whiteclient.render.WorldText;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class DamageNumbers extends Module {
	public final ModeSetting format = add(new ModeSetting("Format", "Health", "Health", "Hearts"));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final BoolSetting heals = add(new BoolSetting("Show Heals", true));
	public final BoolSetting own = add(new BoolSetting("Show Your Damage", false));
	public final NumberSetting scale = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05, "x"));
	public final NumberSetting range = add(new NumberSetting("Range", 24, 6, 48, 1));

	private final Map<Integer, Float> health = new HashMap<>();
	private final List<Popup> popups = new ArrayList<>();
	private final Random random = new Random();

	private record Popup(double x, double y, double z, float start, String text, int rgb, float size, float dx, float dz) {
	}

	public DamageNumbers() {
		super("Damage Numbers", "Floating damage numbers", Category.PVP, true);
	}

	@Override
	protected void onDisable() {
		health.clear();
		popups.clear();
	}

	@Override
	public void onTick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) {
			health.clear();
			popups.clear();
			return;
		}
		float now = com.whiteclient.WhiteClient.get().time();
		double r2 = range.get() * range.get();
		Set<Integer> seen = new HashSet<>();
		for (Entity e : mc.world.getEntities()) {
			if (!(e instanceof LivingEntity living) || Pets.isPet(e) || e.squaredDistanceTo(mc.player) > r2) {
				continue;
			}
			if (e == mc.player && !own.isOn()) {
				continue;
			}
			int id = e.getId();
			seen.add(id);
			float hp = living.getHealth();
			Float before = health.put(id, hp);
			if (before == null) {
				continue;
			}
			float diff = hp - before;
			if (diff < -0.01f || (heals.isOn() && diff > 0.49f && hp < living.getMaxHealth() + 0.01f)) {
				boolean crit = diff < 0 && CombatTracker.recentCrit(e);
				float amount = Math.abs(diff);
				if (format.is("Hearts")) {
					amount /= 2f;
				}
				String num = amount >= 10 ? String.valueOf(Math.round(amount)) : String.format(Locale.ROOT, "%.1f", amount);
				if (num.endsWith(".0")) {
					num = num.substring(0, num.length() - 2);
				}
				String text = (diff < 0 ? "" : "+") + num + (format.is("Hearts") ? " ❤" : "") + (crit ? "!" : "");
				int rgb = diff > 0 ? 0x7CFF9A : crit ? 0xFFD36B : color.rgb();
				float size = (crit ? 1.35f : 1f) * (0.9f + Math.min(0.6f, amount / 20f));
				popups.add(new Popup(e.getX(), e.getY() + e.getHeight() + 0.1, e.getZ(), now, text, rgb, size,
						(random.nextFloat() - 0.5f) * 0.6f, (random.nextFloat() - 0.5f) * 0.6f));
			}
		}
		health.keySet().removeIf(id -> !seen.contains(id));
		if (popups.size() > 40) {
			popups.subList(0, popups.size() - 40).clear();
		}
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		float life = 1.2f;
		popups.removeIf(p -> ctx.time - p.start() > life);
		for (Popup p : popups) {
			float u = (ctx.time - p.start()) / life;
			float pop = u < 0.12f ? 0.6f + 3.3f * u : 1f + 0.05f * (1 - u);
			float a = u < 0.7f ? 1f : (1f - u) / 0.3f;
			float rise = (1f - (1f - u) * (1f - u)) * 0.7f;
			float x = (float) (p.x() - ctx.cam.x) + p.dx() * u;
			float y = (float) (p.y() - ctx.cam.y) + rise;
			float z = (float) (p.z() - ctx.cam.z) + p.dz() * u;
			WorldText.draw(ctx, x, y, z, p.text(), 1.5f * scale.getFloat() * p.size() * pop, Colors.argb(p.rgb(), a), true);
		}
	}
}
