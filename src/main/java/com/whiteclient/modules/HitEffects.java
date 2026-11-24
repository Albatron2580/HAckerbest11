package com.whiteclient.modules;

import com.whiteclient.combat.CombatTracker;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.render.WorldText;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HitEffects extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Sparks", "Sparks", "Ring", "Stars", "Hearts", "Slash", "Crimson"));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final BoolSetting critColor = add(new BoolSetting("Gold On Crits", true));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05, "x"));
	public final NumberSetting extraCrits = add(new NumberSetting("Extra Crit Particles", 0, 0, 5, 1));

	private final List<Burst> bursts = new ArrayList<>();
	private final Random random = new Random();

	private static final class Burst {
		final Vec3d pos;
		final float start;
		final boolean crit;
		final float[] dirs = new float[3 * 12];
		final float seed;

		Burst(Vec3d pos, float start, boolean crit, Random r) {
			this.pos = pos;
			this.start = start;
			this.crit = crit;
			this.seed = r.nextFloat() * 10;
			for (int i = 0; i < 12; i++) {
				double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(r.nextDouble() * 2 - 1);
				dirs[i * 3] = (float) (Math.sin(ph) * Math.cos(th));
				dirs[i * 3 + 1] = (float) Math.abs(Math.cos(ph)) * 0.8f + 0.1f;
				dirs[i * 3 + 2] = (float) (Math.sin(ph) * Math.sin(th));
			}
		}
	}

	public HitEffects() {
		super("Hit Effects", "A burst where you hit (visual only)", Category.PVP, true);
		CombatTracker.onHit(this::hit);
	}

	private void hit(LivingEntity target, Vec3d pos, boolean crit) {
		if (!isEnabled()) {
			return;
		}
		bursts.add(new Burst(pos, com.whiteclient.WhiteClient.get().time(), crit, random));
		if (bursts.size() > 24) {
			bursts.remove(0);
		}
		int extra = extraCrits.getInt();
		MinecraftClient mc = MinecraftClient.getInstance();
		for (int i = 0; i < extra && mc.particleManager != null; i++) {
			mc.particleManager.addEmitter(target, ParticleTypes.CRIT);
		}
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		GlowBatch g = ctx.glow;
		float life = 0.5f;
		bursts.removeIf(b -> ctx.time - b.start > life);
		float s = size.getFloat();
		for (Burst b : bursts) {
			float u = (ctx.time - b.start) / life;
			float ease = 1f - (1f - u) * (1f - u);
			float a = 1f - u;
			int rgb = b.crit && critColor.isOn() ? 0xFFD36B : color.rgb();
			float x = ctx.rx(b.pos.x), y = ctx.ry(b.pos.y), z = ctx.rz(b.pos.z);
			switch (style.get()) {
				case "Ring" -> {
					g.disc(x, y, z, 0.25f * s * (0.5f + ease), rgb, 0.35f * a, 16);
					ringFacing(g, x, y, z, 0.5f * s * ease + 0.05f, rgb, a);
				}
				case "Stars" -> {
					for (int i = 0; i < 6; i++) {
						float d = ease * 0.7f * s;
						float px = x + b.dirs[i * 3] * d, py = y + b.dirs[i * 3 + 1] * d, pz = z + b.dirs[i * 3 + 2] * d;
						star(g, px, py, pz, 0.12f * s * (1 - u * 0.5f), rgb, a);
					}
				}
				case "Hearts" -> {
					for (int i = 0; i < 4; i++) {
						float d = ease * 0.5f * s;
						WorldText.draw(ctx, x + b.dirs[i * 3] * d, y + 0.1f + ease * 0.4f + b.dirs[i * 3 + 1] * d * 0.3f, z + b.dirs[i * 3 + 2] * d,
								"❤", 1.4f * s, Colors.argb(0xFF5C8A, a), false);
					}
				}
				case "Slash" -> {
					float ang = b.seed;
					float len = 0.9f * s;
					float dx = (float) Math.cos(ang) * len, dy = (float) Math.sin(ang) * len * 0.6f;
					float k = Math.min(1f, u * 3f);
					float x0 = x - dx * 0.5f, y0 = y - dy * 0.5f;
					g.glowLine(x0, y0, z, x0 + dx * k, y0 + dy * k, z, 0.12f * s, rgb, a * 1.3f);
				}
				case "Crimson" -> {
					for (int i = 0; i < 12; i++) {
						float d = ease * 0.6f * s;
						float px = x + b.dirs[i * 3] * d, py = y + b.dirs[i * 3 + 1] * d - u * u * 0.5f, pz = z + b.dirs[i * 3 + 2] * d;
						g.disc(px, py, pz, 0.06f * s, 0xC8102E, 0.9f * a, 8);
					}
				}
				default -> {
					for (int i = 0; i < 12; i++) {
						float d0 = ease * 0.55f * s, d1 = d0 + 0.18f * s * (1 - u);
						g.glowLine(x + b.dirs[i * 3] * d0, y + b.dirs[i * 3 + 1] * d0, z + b.dirs[i * 3 + 2] * d0,
								x + b.dirs[i * 3] * d1, y + b.dirs[i * 3 + 1] * d1, z + b.dirs[i * 3 + 2] * d1, 0.05f * s, rgb, a);
					}
					g.disc(x, y, z, 0.35f * s, rgb, 0.4f * a * (1 - u), 12);
				}
			}
		}
	}

	static void ringFacing(GlowBatch g, float x, float y, float z, float r, int rgb, float a) {
		float len = (float) Math.sqrt(x * x + y * y + z * z);
		if (len < 1.0e-3f) {
			return;
		}
		float vx = x / len, vy = y / len, vz = z / len;
		float rx = -vz, rz = vx;
		float rl = (float) Math.sqrt(rx * rx + rz * rz);
		if (rl < 1.0e-4f) {
			rx = 1;
			rz = 0;
			rl = 1;
		}
		rx /= rl;
		rz /= rl;
		float ux = -rz * vy, uy = rz * vx - rx * vz, uz = rx * vy;
		int seg = 28;
		float px = 0, py = 0, pz = 0;
		for (int i = 0; i <= seg; i++) {
			double t = Math.PI * 2 * i / seg;
			float c = (float) Math.cos(t) * r, s = (float) Math.sin(t) * r;
			float qx = x + rx * c + ux * s, qy = y + uy * s, qz = z + rz * c + uz * s;
			if (i > 0) {
				g.glowLine(px, py, pz, qx, qy, qz, 0.06f, rgb, a);
			}
			px = qx;
			py = qy;
			pz = qz;
		}
	}

	static void star(GlowBatch g, float x, float y, float z, float r, int rgb, float a) {
		g.glowLine(x - r, y, z, x + r, y, z, r * 0.5f, rgb, a);
		g.glowLine(x, y - r, z, x, y + r, z, r * 0.5f, rgb, a);
		g.disc(x, y, z, r * 1.2f, rgb, 0.4f * a, 8);
	}
}
