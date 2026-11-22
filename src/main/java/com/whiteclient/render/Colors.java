package com.whiteclient.render;

public final class Colors {
	private Colors() {
	}

	public static int argb(int rgb, float alpha) {
		int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f + 0.5f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	public static int withAlpha(int argb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
	}

	public static int mulAlpha(int argb, float factor) {
		int a = (argb >>> 24) & 0xFF;
		return withAlpha(argb, (int) (a * factor));
	}

	/** Linear mix of two RGB colours, t=0 gives a, t=1 gives b. Alpha of the result is 0. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	public static int mixArgb(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF;
		int ba = (b >>> 24) & 0xFF;
		int alpha = (int) (aa + (ba - aa) * t);
		return (alpha << 24) | mix(a, b, t);
	}

	/** Multiplies the RGB channels (darken when factor < 1). */
	public static int shade(int rgb, float factor) {
		int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * factor));
		int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * factor));
		int b = Math.min(255, (int) ((rgb & 0xFF) * factor));
		return (r << 16) | (g << 8) | b;
	}

	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		int i = (int) (h * 6f);
		float f = h * 6f - i;
		float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		float r, g, b;
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}
}
