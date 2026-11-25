package com.whiteclient.modules;

import com.whiteclient.combat.CombatTracker;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.render.WorldText;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class KillEffects extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Soul", "Soul", "Lightning", "Shockwave", "Hearts", "Confetti"));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05, "x"));

	private final List<float[]> effects = new ArrayList<>();
	private final Random random = new Random();

	public KillEffects() {
		super("Kill Effects", "An effect when you kill something", Category.PVP, true);
		CombatTracker.onKill(this::kill);
	}

	private void kill(LivingEntity target, Vec3 pos) {
		if (!isEnabled()) {
			return;
		}
		spawn(pos, target.getBbHeight());
	}

	public void spawn(Vec3 pos, float height) {
		int idx = 0;
		for (int i = 0; i < style.getModes().size(); i++) {
			if (style.is(style.getModes().get(i))) {
				idx = i;
			}
		}
		effects.add(new float[]{(float) pos.x, (float) pos.y, (float) pos.z, com.whiteclient.WhiteClient.get().time(), height, random.nextFloat() * 100, idx});
		if (effects.size() > 12) {
			effects.remove(0);
		}
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		GlowBatch g = ctx.glow;
		float life = 1.8f;
		effects.removeIf(e -> ctx.time - e[3] > life);
		float s = size.getFloat();
		int rgb = color.rgb();
		for (float[] e : effects) {
			float t = ctx.time - e[3];
			float u = t / life;
			float a = Math.min(1f, (1f - u) * 2.5f);
			float x = e[0] - (float) ctx.cam.x, y = e[1] - (float) ctx.cam.y, z = e[2] - (float) ctx.cam.z;
			float h = e[4];
			Random r = new Random((long) e[5]);
			switch ((int) e[6]) {
				case 1 -> {
					float flash = u < 0.35f ? 1f : Math.max(0f, 1f - (u - 0.35f) * 2.5f);
					float top = y + 14;
					float px = x, py = top, pz = z;
					int segs = 14;
					for (int i = 1; i <= segs; i++) {
						float k = i / (float) segs;
						float nx = x + (i == segs ? 0 : (r.nextFloat() - 0.5f) * 1.4f * (1 - k * 0.6f));
						float nz = z + (i == segs ? 0 : (r.nextFloat() - 0.5f) * 1.4f * (1 - k * 0.6f));
						float ny = top + (y + h * 0.5f - top) * k;
						g.glowLine(px, py, pz, nx, ny, nz, 0.35f * s, 0xDCE6FF, flash * 1.3f);
						if (i % 4 == 0) {
							float bx = nx + (r.nextFloat() - 0.5f) * 2, bz = nz + (r.nextFloat() - 0.5f) * 2;
							g.glowLine(nx, ny, nz, bx, ny - 1.2f, bz, 0.18f * s, 0xDCE6FF, flash * 0.8f);
						}
						px = nx;
						py = ny;
						pz = nz;
					}
					g.flatRing(x, y + 0.03f, z, 0.3f + u * 2.5f * s, 0.12f, rgb, a);
					g.disc(x, y + h * 0.5f, z, 1.6f * s, 0xDCE6FF, 0.5f * flash, 18);
				}
				case 2 -> {
					for (int k = 0; k < 3; k++) {
						float uu = Math.max(0f, u * 1.4f - k * 0.15f);
						if (uu <= 0 || uu > 1) {
							continue;
						}
						float ease = 1f - (1f - uu) * (1f - uu);
						g.flatRing(x, y + 0.04f + k * 0.02f, z, ease * 3.2f * s, 0.12f, rgb, (1f - uu) * 1.1f);
					}
					float beam = Math.max(0f, 1f - u * 2f);
					g.glowLine(x, y, z, x, y + 6 * s, z, 0.5f * s, rgb, beam);
					g.flatDisc(x, y + 0.03f, z, 1.4f * s, rgb, 0.4f * beam);
				}
				case 3 -> {
					for (int i = 0; i < 9; i++) {
						float ang = r.nextFloat() * 6.283f;
						float d = 0.3f + r.nextFloat() * 0.6f;
						float rise = t * (0.9f + r.nextFloat());
						WorldText.draw(ctx, x + (float) Math.cos(ang) * d * (0.6f + u), y + h * 0.6f + rise, z + (float) Math.sin(ang) * d * (0.6f + u),
								"❤", (1.4f + r.nextFloat()) * s, Colors.argb(i % 3 == 0 ? 0xFFFFFF : 0xFF5C8A, a), false);
					}
				}
				case 4 -> {
					for (int i = 0; i < 40; i++) {
						float ang = r.nextFloat() * 6.283f;
						float sp = 1.2f + r.nextFloat() * 2.2f;
						float vy = 2.5f + r.nextFloat() * 3f;
						float px = x + (float) Math.cos(ang) * sp * t;
						float pz = z + (float) Math.sin(ang) * sp * t;
						float py = y + h * 0.7f + vy * t - 4.9f * t * t * 0.6f;
						int c = Colors.hsv(r.nextFloat(), 0.6f, 1f);
						g.disc(px, py, pz, 0.07f * s, c, 0.95f * a, 6);
					}
				}
				default -> {
					float rise = (1f - (1f - u) * (1f - u)) * 2.6f * s;
					float cy = y + h * 0.5f + rise;
					g.disc(x, cy, z, 0.55f * s, rgb, 0.45f * a, 16);
					g.disc(x, cy, z, 0.2f * s, 0xFFFFFF, 0.9f * a, 10);
					for (int i = 0; i < 10; i++) {
						float k = i / 10f;
						float wob = (float) Math.sin(t * 6 + i) * 0.12f * k;
						g.disc(x + wob, cy - k * 1.1f * s, z - wob, (0.25f - k * 0.18f) * s, rgb, 0.35f * a * (1 - k), 10);
					}
					if (u < 0.6f) {
						float ring = u / 0.6f;
						g.flatRing(x, y + 0.04f, z, (0.3f + ring * 1.4f) * s, 0.08f, rgb, (1 - ring) * 1.1f);
					}
				}
			}
		}
	}
}
