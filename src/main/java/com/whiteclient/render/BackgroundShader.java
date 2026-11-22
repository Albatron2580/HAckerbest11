package com.whiteclient.render;

/**
 * CPU "shader" for the animated menu backgrounds. Pure Java (no Minecraft classes),
 * runs on a worker thread and fills an ARGB pixel buffer.
 */
public final class BackgroundShader {
	public static final int ENERGY = 0;
	public static final int AURORA = 1;
	public static final int MONO = 2;
	/** Soft grey silk curtains folding on the right side (WHITECLIENT showcase menu). */
	public static final int SILK = 3;

	private BackgroundShader() {
	}

	private static float clamp01(double v) {
		return (float) (v < 0 ? 0 : (v > 1 ? 1 : v));
	}

	private static float smooth(double e0, double e1, double x) {
		float t = clamp01((x - e0) / (e1 - e0));
		return t * t * (3 - 2 * t);
	}

	/** Fills {@code out} (w*h ARGB) for time {@code t} seconds. */
	public static void render(int[] out, int w, int h, double t, int mode, int rgb, float speed, float intensity) {
		double time = t * speed;
		float cr = ((rgb >> 16) & 0xFF) / 255f;
		float cg = ((rgb >> 8) & 0xFF) / 255f;
		float cb = (rgb & 0xFF) / 255f;
		double inv = 1.0 / h;
		for (int y = 0; y < h; y++) {
			double v = y * inv;
			for (int x = 0; x < w; x++) {
				double u = x * inv;
				float r, g, b;
				if (mode == SILK) {
					double aspect = (double) w / h;
					double mask = smooth(0.42 * aspect, 1.02 * aspect, u);
					double warp = 0.16 * Math.sin(v * 2.4 + time * 0.22 + u * 0.6)
							+ 0.08 * Math.sin(v * 5.3 - time * 0.31 + u * 2.1)
							+ 0.10 * Noise.noise(u * 0.7, v * 0.7, time * 0.05);
					double fx = (u + warp) * (6.5 + 1.5 * mask);
					float fold = (float) (0.5 + 0.5 * Math.sin(fx * Math.PI + time * 0.12));
					float shade = (float) Math.pow(fold, 1.7);
					float crest = (float) Math.pow(fold, 9);
					float base = 0.13f + 0.07f * (float) (1 - v) + 0.03f * (float) (u / aspect);
					float silk = 0.17f + 0.46f * shade + 0.27f * crest;
					float far = smooth(0.75 * aspect, 1.35 * aspect, u);
					silk *= 0.78f + 0.3f * far;
					float l = lerp(base, silk, (float) mask * 0.9f);
					float vig = 1f - 0.5f * (float) ((v - 0.45) * (v - 0.45));
					l *= vig * (0.85f + 0.15f * intensity);
					r = l * (0.93f + 0.07f * cr);
					g = l * (0.93f + 0.07f * cg);
					b = l * (0.93f + 0.07f * cb);
				} else if (mode == AURORA) {
					double n = Noise.noise(u * 1.1, v * 1.1 - time * 0.04, time * 0.06);
					double m = Noise.noise(u * 2.1 + 7.3, v * 1.6, time * 0.09);
					double k = Noise.noise(u * 0.6 - time * 0.02, v * 0.9 + 3.3, time * 0.03);
					// deep purple -> violet -> pink
					float a = clamp01(0.45 + 0.45 * n + 0.25 * (1 - v));
					r = lerp(0.07f, 0.42f, a);
					g = lerp(0.03f, 0.12f, a);
					b = lerp(0.17f, 0.55f, a);
					float pink = smooth(-0.1, 0.8, m) * 0.75f * clamp01(0.6 + k);
					r = lerp(r, 0.95f, pink * 0.8f);
					g = lerp(g, 0.38f, pink * 0.6f);
					b = lerp(b, 0.70f, pink * 0.7f);
					// soft light streaks
					float streak = (float) Math.pow(clamp01(1 - Math.abs(Noise.noise(u * 0.9, v * 3.0 + time * 0.05, 4.2)) * 3.0), 3.0) * 0.18f;
					r += streak;
					g += streak * 0.8f;
					b += streak;
				} else if (mode == MONO) {
					double n1 = Noise.noise(u * 1.3, v * 1.3, time * 0.10);
					float ridge = (float) Math.pow(clamp01(1 - Math.abs(n1) * 6.0), 4.0);
					float base = 0.035f + 0.03f * (float) (0.5 + 0.5 * Noise.noise(u * 0.5, v * 0.5, time * 0.03));
					float l = base + ridge * 0.55f + (float) Math.pow(ridge, 14) * 0.6f;
					r = g = b = l;
				} else {
					// ENERGY: domain-warped ridged noise -> glowing veins
					double wx = Noise.noise(u * 0.55, v * 0.55, time * 0.06) * 0.85;
					double wy = Noise.noise(u * 0.55 + 5.2, v * 0.55 + 1.3, time * 0.06) * 0.85;
					double n1 = Noise.noise(u * 1.25 + wx, v * 1.25 + wy, time * 0.11);
					double n2 = Noise.noise(u * 2.6 + wx * 1.6 + 3.1, v * 2.6 + wy * 1.6, time * 0.17 + 9.0);
					float r1 = clamp01(1 - Math.abs(n1) * 5.5);
					float r2 = clamp01(1 - Math.abs(n2) * 7.0);
					float glow = (float) (Math.pow(r1, 3.2) + Math.pow(r2, 4.0) * 0.45);
					float core = (float) (Math.pow(r1, 22) + Math.pow(r2, 30) * 0.6);
					float amb = 0.05f + 0.07f * (float) (0.5 + 0.5 * Noise.noise(u * 0.35, v * 0.35, time * 0.025));
					float vig = 1f - 0.45f * (float) ((u * h / w - 0.5) * (u * h / w - 0.5) + (v - 0.5) * (v - 0.5)) * 2f;
					float lum = (amb + glow * intensity) * vig;
					float ci = core * intensity * 0.9f;
					r = cr * lum + ci * (0.55f + 0.45f * cr);
					g = cg * lum + ci * (0.55f + 0.45f * cg);
					b = cb * lum + ci * (0.55f + 0.45f * cb);
				}
				int ir = (int) (clamp01(r) * 255f);
				int ig = (int) (clamp01(g) * 255f);
				int ib = (int) (clamp01(b) * 255f);
				out[y * w + x] = 0xFF000000 | (ir << 16) | (ig << 8) | ib;
			}
		}
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
