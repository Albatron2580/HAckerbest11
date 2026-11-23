package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.V3;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;

import java.util.Random;

/** Twinkling stars and a milky-way band painted over the sky. */
public class GalaxySky extends Module {
	public final NumberSetting stars = add(new NumberSetting("Stars", 500, 100, 2000, 50));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.2, 0.2, 2.0, 0.05, "x"));
	public final NumberSetting starSize = add(new NumberSetting("Star Size", 1.5, 0.4, 3.0, 0.05, "x"));
	public final BoolSetting milkyWay = add(new BoolSetting("Milky Way", true));
	public final BoolSetting twinkle = add(new BoolSetting("Twinkle", true));
	public final ColorSetting tint = add(new ColorSetting("Nebula Tint", 0x9B5CFF));

	private float[][] starData = new float[0][];
	private float[][] bandData = new float[0][];
	private float[][] blobData = new float[0][];
	private int builtFor = -1;

	public GalaxySky() {
		super("Galaxy Sky", "Stars and a milky way in the sky", Category.WORLD, true);
	}

	private static V3 randomSkyDir(Random r, float minElevationDeg) {
		while (true) {
			float x = r.nextFloat() * 2 - 1, y = r.nextFloat(), z = r.nextFloat() * 2 - 1;
			V3 v = new V3(x, y, z);
			float l = v.length();
			if (l > 1 || l < 0.1f) {
				continue;
			}
			v = v.mul(1 / l);
			if (Math.toDegrees(Math.asin(v.y())) >= minElevationDeg) {
				return v;
			}
		}
	}

	private void build(int count) {
		Random r = new Random(1337);
		starData = new float[count][];
		for (int i = 0; i < count; i++) {
			V3 d = randomSkyDir(r, 7);
			float temp = r.nextFloat();
			int c = temp < 0.15f ? 0xBFD4FF : temp > 0.9f ? 0xFFE2B8 : 0xFFFFFF;
			float sz = 0.5f + (float) Math.pow(r.nextFloat(), 6) * 2.2f;
			starData[i] = new float[]{d.x(), d.y(), d.z(), sz, r.nextFloat() * 100f, 0.6f + r.nextFloat() * 2f, c};
		}
		// milky way: stars concentrated around a great circle that passes high overhead
		V3 n = new V3(0.35f, 0.25f, 0.9f).normalize();
		V3 a = n.cross(V3.UP).normalize();
		V3 b = n.cross(a).normalize();
		int bandCount = 450;
		bandData = new float[bandCount][];
		int made = 0;
		while (made < bandCount) {
			double th = r.nextDouble() * Math.PI * 2;
			float off = (float) (r.nextGaussian() * 0.11);
			V3 d = a.mul((float) Math.cos(th)).add(b, (float) Math.sin(th)).add(n, off).normalize();
			if (d.y() < 0.12f) {
				continue;
			}
			bandData[made++] = new float[]{d.x(), d.y(), d.z(), 0.35f + r.nextFloat() * 0.6f, r.nextFloat() * 100f};
		}
		int blobs = 46;
		blobData = new float[blobs][];
		made = 0;
		while (made < blobs) {
			double th = r.nextDouble() * Math.PI * 2;
			float off = (float) (r.nextGaussian() * 0.06);
			V3 d = a.mul((float) Math.cos(th)).add(b, (float) Math.sin(th)).add(n, off).normalize();
			if (d.y() < 0.15f) {
				continue;
			}
			blobData[made++] = new float[]{d.x(), d.y(), d.z(), 0.06f + r.nextFloat() * 0.08f, r.nextFloat()};
		}
		builtFor = count;
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		int count = FpsBoost.lighter() ? Math.max(100, stars.getInt() * 3 / 5) : stars.getInt();
		if (builtFor != count) {
			build(count);
		}
		int viewBlocks = ctx.client.options.getClampedViewDistance() * 16;
		float dist = Math.max(40f, Math.min(110f, viewBlocks * 0.55f));
		GlowBatch g = ctx.glow;
		float bright = brightness.getFloat();
		float sz = starSize.getFloat() * dist * 0.0032f;
		float t = ctx.time;
		boolean tw = twinkle.isOn();

		for (float[] s : starData) {
			float a = bright * (0.55f + 0.45f * Math.min(1f, s[3]));
			if (tw) {
				a *= 0.65f + 0.35f * (float) Math.sin(t * s[5] + s[4]);
			}
			g.disc(s[0] * dist, s[1] * dist, s[2] * dist, sz * s[3], (int) s[6], Math.min(1f, a), 4);
		}
		if (milkyWay.isOn()) {
			for (float[] s : bandData) {
				float a = bright * 0.55f * s[3];
				if (tw) {
					a *= 0.75f + 0.25f * (float) Math.sin(t * 1.7f + s[4]);
				}
				g.disc(s[0] * dist, s[1] * dist, s[2] * dist, sz * 0.75f, 0xEDEBFF, a, 4);
			}
			int nebula = tint.rgb();
			for (float[] s : blobData) {
				int c = Colors.mix(nebula, 0xFFFFFF, s[4] * 0.6f);
				g.disc(s[0] * dist, s[1] * dist, s[2] * dist, s[3] * dist, c, 0.06f * bright, 14);
			}
		}
	}
}
