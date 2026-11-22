package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;

import java.util.Random;

/** Soft glowing snow / dust particles floating around you. */
public class Snow extends Module {
	public final NumberSetting amount = add(new NumberSetting("Amount", 180, 20, 600, 10));
	public final NumberSetting radius = add(new NumberSetting("Radius", 14, 4, 32, 1));
	public final NumberSetting size = add(new NumberSetting("Size", 0.05, 0.02, 0.25, 0.01));
	public final NumberSetting speed = add(new NumberSetting("Fall Speed", 1.0, 0.0, 3.0, 0.1, "x"));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 0.85, 0.1, 1.5, 0.05));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));

	private final Random random = new Random();
	private double[] px = new double[0];
	private double[] py = new double[0];
	private double[] pz = new double[0];
	private float[] phase = new float[0];
	private float[] scale = new float[0];
	private boolean needsSpread = true;

	public Snow() {
		super("Snow", "Glowing snow particles in the air", Category.WORLD, true);
	}

	@Override
	protected void onEnable() {
		needsSpread = true;
	}

	private void resize(int n) {
		px = new double[n];
		py = new double[n];
		pz = new double[n];
		phase = new float[n];
		scale = new float[n];
		needsSpread = true;
	}

	private void respawn(int i, double cx, double cy, double cz, float r, boolean anywhere) {
		double ang = random.nextDouble() * Math.PI * 2;
		double dist = Math.sqrt(random.nextDouble()) * r;
		px[i] = cx + Math.cos(ang) * dist;
		pz[i] = cz + Math.sin(ang) * dist;
		py[i] = anywhere ? cy + (random.nextDouble() * 1.3 - 0.4) * r * 0.6 : cy + r * 0.6 * (0.8 + random.nextDouble() * 0.2);
		phase[i] = random.nextFloat() * 100f;
		scale[i] = 0.5f + random.nextFloat();
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		int n = amount.getInt();
		if (px.length != n) {
			resize(n);
		}
		double cx = ctx.cam.x, cy = ctx.cam.y, cz = ctx.cam.z;
		float r = radius.getFloat();
		if (needsSpread) {
			for (int i = 0; i < n; i++) {
				respawn(i, cx, cy, cz, r, true);
			}
			needsSpread = false;
		}
		float dt = ctx.frameDelta;
		float t = ctx.time;
		float fall = 0.55f * speed.getFloat();
		GlowBatch g = ctx.glow;
		int rgb = color.rgb();
		float s = size.getFloat();
		float b = brightness.getFloat();
		for (int i = 0; i < n; i++) {
			py[i] -= fall * dt * (0.55f + 0.45f * scale[i]);
			px[i] += Math.sin(t * 0.7 + phase[i]) * 0.22 * dt;
			pz[i] += Math.cos(t * 0.55 + phase[i] * 1.3) * 0.22 * dt;
			double dx = px[i] - cx, dz = pz[i] - cz;
			if (py[i] < cy - r * 0.45 || py[i] > cy + r * 0.8 || dx * dx + dz * dz > r * r) {
				respawn(i, cx, cy, cz, r, py[i] >= cy - r * 0.45);
			}
			float x = (float) (px[i] - cx), y = (float) (py[i] - cy), z = (float) (pz[i] - cz);
			float d2 = x * x + y * y + z * z;
			if (d2 < 0.25f) {
				continue; // too close to the camera
			}
			float fade = 1f - (float) Math.min(1.0, Math.sqrt(d2) / r);
			float tw = 0.75f + 0.25f * (float) Math.sin(t * 2.3 + phase[i]);
			g.disc(x, y, z, s * scale[i] * 1.6f, rgb, b * tw * (0.35f + 0.65f * fade), 6);
		}
	}
}
