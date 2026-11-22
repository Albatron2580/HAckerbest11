package com.whiteclient.modules;

import com.whiteclient.combat.CombatTracker;
import com.whiteclient.gui.Render2D;
import com.whiteclient.hud.Hud;
import com.whiteclient.hud.HudLayout;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.Locale;

/** A card with the name, head and health of the player/mob you are fighting. */
public class TargetHUD extends Module {
	public final ModeSetting position = add(new ModeSetting("Position", "Near Crosshair", "Near Crosshair", "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Left", "Right"));
	public final BoolSetting crosshair = add(new BoolSetting("Show What You Look At", false));
	public final BoolSetting winLose = add(new BoolSetting("Winning / Losing", true));

	/** Test hook. */
	public static LivingEntity forced;

	private LivingEntity shown;
	private float alpha;
	private float shownHp = -1, ghostHp = -1;
	private long lastFrame = System.nanoTime();

	public TargetHUD() {
		super("Target HUD", "Name, head and health of your target", Category.PVP, true);
	}

	private LivingEntity pick(MinecraftClient mc) {
		if (forced != null && forced.isAlive()) {
			return forced;
		}
		LivingEntity t = CombatTracker.lastHit(6000);
		if (t != null && (!t.isAlive() || t.squaredDistanceTo(mc.player) > 32 * 32)) {
			t = null;
		}
		if (t == null && crosshair.isOn()) {
			Entity e = mc.targetedEntity;
			if (e instanceof LivingEntity l && !Pets.isPet(e)) {
				t = l;
			}
		}
		return t;
	}

	@Override
	public void renderHud(DrawContext ctx, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		LivingEntity t = pick(mc);
		if (t != null && t != shown) {
			shown = t;
			shownHp = ghostHp = t.getHealth();
		}
		alpha += ((t != null ? 1f : 0f) - alpha) * Math.min(1f, dt * 10f);
		if (shown == null || alpha < 0.02f) {
			if (t == null) {
				shown = null;
			}
			return;
		}
		LivingEntity e = shown;
		float hp = Math.max(0, e.getHealth());
		float max = Math.max(1, e.getMaxHealth());
		shownHp += (hp - shownHp) * Math.min(1f, dt * 12f);
		ghostHp += (shownHp - ghostHp) * Math.min(1f, dt * 2.5f);
		if (ghostHp < shownHp) {
			ghostHp = shownHp;
		}

		TextRenderer tr = mc.textRenderer;
		float w = 148, h = 46;
		float x, y;
		if (position.is("Near Crosshair")) {
			x = ctx.getScaledWindowWidth() / 2f + 16;
			y = ctx.getScaledWindowHeight() / 2f + 16;
		} else {
			float[] p = HudLayout.place(position.get(), w, h);
			x = p[0];
			y = p[1];
		}
		float a = alpha;
		var m = ctx.getMatrices();
		m.pushMatrix();
		float sc = 0.92f + 0.08f * a;
		m.translate(x + w / 2f, y + h / 2f);
		m.scale(sc, sc);
		m.translate(-(x + w / 2f), -(y + h / 2f));
		Hud.panel(ctx, x, y, w, h, a);

		// head / model
		float hs = 34;
		Render2D.roundRect(ctx, x + 6, y + 6, hs, hs, 6, Colors.withAlpha(0x1C1C24, (int) (255 * a)));
		try {
			if (e instanceof AbstractClientPlayerEntity p) {
				PlayerSkinDrawer.draw(ctx, p.getSkin(), (int) (x + 7), (int) (y + 7), (int) hs - 2);
			} else {
				int size = (int) Math.max(6, Math.min(26, 26 / Math.max(e.getHeight(), e.getWidth())));
				InventoryScreen.drawEntity(ctx, (int) (x + 6), (int) (y + 6), (int) (x + 6 + hs), (int) (y + 6 + hs), size, 0.0625f,
						x + 6 + hs / 2f, y + 10, e);
			}
		} catch (Throwable ignored) {
		}
		// hurt flash over the head
		if (e.hurtTime > 0) {
			Render2D.roundRect(ctx, x + 6, y + 6, hs, hs, 6, Colors.argb(0xFF3050, 0.35f * e.hurtTime / 10f * a));
		}

		float tx = x + 6 + hs + 7;
		float tw = w - (tx - x) - 7;
		String name = e.getName().getString();
		while (tr.getWidth(name) > tw && name.length() > 2) {
			name = name.substring(0, name.length() - 1);
		}
		Render2D.text(ctx, tr, name, tx, y + 7, Hud.alpha(Hud.TEXT, a));
		double dist = Math.sqrt(e.squaredDistanceTo(mc.player));
		String info = String.format(Locale.ROOT, "%.1f ❤   %.1fm", hp / 2f, dist);
		Render2D.textScaled(ctx, tr, info, tx, y + 18, 0.75f, Hud.alpha(Hud.SUB, a));
		if (winLose.isOn()) {
			float mine = mc.player.getHealth();
			String wl = mine > hp + 0.5f ? "Winning" : mine < hp - 0.5f ? "Losing" : "Even";
			int wc = mine > hp + 0.5f ? 0xFF7CFF9A : mine < hp - 0.5f ? 0xFFFF6B7A : 0xFFE0E0E8;
			float ww = tr.getWidth(wl) * 0.7f;
			Render2D.textScaled(ctx, tr, wl, x + w - 7 - ww, y + 8.5f, 0.7f, Hud.alpha(wc, a));
		}
		// health bar
		float by = y + h - 14;
		float frac = Math.min(1f, shownHp / max), ghost = Math.min(1f, ghostHp / max);
		Render2D.roundRect(ctx, tx, by, tw, 6, 3, Colors.withAlpha(0x2A2A33, (int) (255 * a)));
		Render2D.roundRect(ctx, tx, by, Math.max(6, tw * ghost), 6, 3, Colors.withAlpha(0xFF8095, (int) (160 * a)));
		int barColor = Colors.mix(0xFF5C6E, 0xFFFFFF, Math.min(1f, frac * 1.6f));
		Render2D.roundRect(ctx, tx, by, Math.max(6, tw * frac), 6, 3, Colors.withAlpha(barColor, (int) (255 * a)));
		m.popMatrix();
	}
}
