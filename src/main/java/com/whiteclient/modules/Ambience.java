package com.whiteclient.modules;

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
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class Ambience extends Module {
	public final ModeSetting mode = add(new ModeSetting("Particles", "Fireflies", "Fireflies", "Petals", "Embers", "Hearts", "Bubbles"));
	public final NumberSetting amount = add(new NumberSetting("Amount", 70, 10, 250, 5));
	public final NumberSetting radius = add(new NumberSetting("Radius", 12, 4, 28, 1));
	public final BoolSetting customColor = add(new BoolSetting("Custom Color", false));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF).visibleWhen(customColor::isOn));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.2, 2.0, 0.05, "x"));

	private final Random random = new Random();
	private double[] px = new double[0], py = new double[0], pz = new double[0];
	private float[] phase = new float[0], spin = new float[0], age = new float[0];
	private String lastMode = "";

	public Ambience() {
		super("Ambience", "Fireflies, petals, embers and more", Category.WORLD, false);
	}

	private void resize(int n) {
		px = new double[n];
		py = new double[n];
		pz = new double[n];
		phase = new float[n];
		spin = new float[n];
		age = new float[n];
		for (int i = 0; i < n; i++) {
			age[i] = -1;
		}
	}

	private void respawn(int i, Vec3d c, float r, boolean anywhere) {
		double ang = random.nextDouble() * Math.PI * 2;
		double d = Math.sqrt(random.nextDouble()) * r;
		px[i] = c.x + Math.cos(ang) * d;
		pz[i] = c.z + Math.sin(ang) * d;
		double h = switch (mode.get()) {
			case "Petals" -> anywhere ? random.nextDouble() * 9 - 1 : 8 + random.nextDouble() * 2;
			case "Embers", "Bubbles", "Hearts" -> anywhere ? random.nextDouble() * 6 - 1 : -1.5 + random.nextDouble();
			default -> random.nextDouble() * 4 - 0.5;
		};
		py[i] = c.y + h;
		phase[i] = random.nextFloat() * 100;
		spin[i] = random.nextFloat() * 6.28f;
		age[i] = 0;
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		if (ctx.client.player == null) {
			return;
		}
		int n = amount.getInt();
		if (px.length != n || !lastMode.equals(mode.get())) {
			resize(n);
			lastMode = mode.get();
		}
		Vec3d c = ctx.client.player.getLerpedPos(ctx.tickDelta);
		float r = radius.getFloat();
		float dt = Math.min(0.05f, ctx.frameDelta);
		float t = ctx.time;
		GlowBatch g = ctx.glow;
		float br = brightness.getFloat();
		String m = mode.get();
		for (int i = 0; i < n; i++) {
			if (age[i] < 0) {
				respawn(i, c, r, true);
			}
			age[i] += dt;
			float ph = phase[i];
			switch (m) {
				case "Petals" -> {
					py[i] -= dt * (0.55 + 0.25 * Math.sin(ph));
					px[i] += dt * (0.35 + 0.3 * Math.sin(t * 0.7 + ph));
					pz[i] += dt * 0.25 * Math.cos(t * 0.9 + ph);
				}
				case "Embers" -> {
					py[i] += dt * (0.7 + 0.4 * Math.sin(ph));
					px[i] += dt * 0.25 * Math.sin(t * 2 + ph);
					pz[i] += dt * 0.25 * Math.cos(t * 1.7 + ph);
				}
				case "Bubbles", "Hearts" -> {
					py[i] += dt * (0.35 + 0.15 * Math.sin(ph));
					px[i] += dt * 0.15 * Math.sin(t * 1.3 + ph);
				}
				default -> {
					px[i] += dt * 0.35 * Math.sin(t * 0.6 + ph * 1.3);
					py[i] += dt * 0.2 * Math.sin(t * 0.8 + ph);
					pz[i] += dt * 0.35 * Math.cos(t * 0.5 + ph * 0.7);
				}
			}
			double dx = px[i] - c.x, dz = pz[i] - c.z, dy = py[i] - c.y;
			if (dx * dx + dz * dz > r * r * 1.2 || dy < -3 || dy > 11 || age[i] > 25) {
				respawn(i, c, r, false);
				continue;
			}
			float x = ctx.rx(px[i]), y = ctx.ry(py[i]), z = ctx.rz(pz[i]);
			float fade = Math.min(1f, age[i] / 1.5f);
			switch (m) {
				case "Petals" -> {
					int col = customColor.isOn() ? color.rgb() : (i % 4 == 0 ? 0xFFE4F0 : 0xFF6FAE);
					float rot = t * 1.6f + spin[i];
					float s = 0.13f;
					float ax = (float) Math.cos(rot) * s, az = (float) Math.sin(rot) * s;
					float ay = (float) Math.sin(rot * 0.7f + ph) * s;
					int cc = Colors.argb(col, 0.95f * br * fade);
					int ce = Colors.argb(col, 0.45f * br * fade);
					g.quad(x - ax, y - ay, z - az, ce, x, y + s * 0.6f, z, cc, x + ax, y + ay, z + az, ce, x, y - s * 0.6f, z, cc);
				}
				case "Embers" -> {
					int col = customColor.isOn() ? color.rgb() : 0xFF8A3D;
					float fl = 0.6f + 0.4f * (float) Math.sin(t * 12 + ph);
					g.ribbon(x, y, z, x, y - 0.08f, z, 0.018f, Colors.argb(Colors.mix(col, 0xFFE0A0, 0.4f), 0.9f * br * fl * fade));
					g.disc(x, y, z, 0.07f, col, 0.45f * br * fl * fade, 6);
				}
				case "Hearts" -> {
					if (i % 3 == 0) {
						int col = customColor.isOn() ? color.rgb() : 0xFF6E9A;
						WorldText.draw(ctx, x, y, z, "❤", 1.3f, Colors.argb(col, 0.85f * fade), false);
					}
				}
				case "Bubbles" -> {
					int col = customColor.isOn() ? color.rgb() : 0xBFE6FF;
					com.whiteclient.modules.HitEffects.ringFacing(g, x, y, z, 0.06f + 0.03f * (float) Math.sin(ph), col, 0.35f * br * fade);
				}
				default -> {
					int col = customColor.isOn() ? color.rgb() : 0xE4FF8A;
					float blink = (float) Math.pow(Math.max(0, Math.sin(t * 1.4 + ph)), 3);
					if (blink > 0.02f) {
						g.disc(x, y, z, 0.36f, col, 0.55f * blink * br * fade, 10);
						g.disc(x, y, z, 0.07f, 0xFFFFF0, Math.min(1f, blink * br * fade), 6);
					}
				}
			}
		}
	}
}
