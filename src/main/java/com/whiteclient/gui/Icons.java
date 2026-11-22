package com.whiteclient.gui;

import net.minecraft.client.gui.DrawContext;

/** Small vector-style icons built from rounded shapes. {@code s} is the icon size, colours should be opaque. */
public final class Icons {
	private Icons() {
	}

	public static void person(DrawContext ctx, float cx, float cy, float s, int c) {
		Render2D.circle(ctx, cx, cy - s * 0.2f, s * 0.21f, c);
		Render2D.roundRect(ctx, cx - s * 0.33f, cy + s * 0.08f, s * 0.66f, s * 0.36f, s * 0.18f, c);
	}

	public static void people(DrawContext ctx, float cx, float cy, float s, int c, int dim) {
		person(ctx, cx + s * 0.2f, cy - s * 0.02f, s * 0.82f, dim);
		person(ctx, cx - s * 0.14f, cy + s * 0.02f, s * 0.9f, c);
	}

	public static void gear(DrawContext ctx, float cx, float cy, float s, int c) {
		float rt = s * 0.38f;
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			float tx = cx + (float) Math.cos(a) * rt;
			float ty = cy + (float) Math.sin(a) * rt;
			Render2D.circle(ctx, tx, ty, s * 0.11f, c);
		}
		Render2D.roundRectOutline(ctx, cx - s * 0.3f, cy - s * 0.3f, s * 0.6f, s * 0.6f, s * 0.3f, s * 0.15f, c);
	}

	public static void globe(DrawContext ctx, float cx, float cy, float s, int c) {
		float r = s * 0.42f;
		float t = Math.max(0.6f, s * 0.08f);
		Render2D.roundRectOutline(ctx, cx - r, cy - r, r * 2, r * 2, r, t, c);
		Render2D.roundRectOutline(ctx, cx - r * 0.45f, cy - r, r * 0.9f, r * 2, r * 0.45f, t, c);
		Render2D.roundRect(ctx, cx - r, cy - t / 2f, r * 2, t, t / 2f, c);
	}

	public static void cloud(DrawContext ctx, float cx, float cy, float s, int c) {
		Render2D.circle(ctx, cx - s * 0.17f, cy + s * 0.02f, s * 0.2f, c);
		Render2D.circle(ctx, cx + s * 0.07f, cy - s * 0.08f, s * 0.26f, c);
		Render2D.circle(ctx, cx + s * 0.3f, cy + s * 0.06f, s * 0.16f, c);
		Render2D.roundRect(ctx, cx - s * 0.38f, cy + s * 0.02f, s * 0.84f, s * 0.22f, s * 0.11f, c);
	}

	public static void list(DrawContext ctx, float cx, float cy, float s, int c) {
		float h = Math.max(0.8f, s * 0.11f);
		for (int i = -1; i <= 1; i++) {
			float y = cy + i * s * 0.28f;
			Render2D.circle(ctx, cx - s * 0.34f, y, h * 0.75f, c);
			Render2D.roundRect(ctx, cx - s * 0.18f, y - h / 2f, s * 0.56f, h, h / 2f, c);
		}
	}

	public static void power(DrawContext ctx, float cx, float cy, float s, int c) {
		float r = s * 0.36f;
		float dot = Math.max(0.55f, s * 0.065f);
		for (int i = 0; i <= 28; i++) {
			double a = Math.toRadians(-90 + 38 + i * (360 - 76) / 28.0);
			Render2D.circle(ctx, cx + (float) Math.cos(a) * r, cy + s * 0.04f + (float) Math.sin(a) * r, dot, c);
		}
		Render2D.roundRect(ctx, cx - dot, cy - s * 0.44f, dot * 2, s * 0.42f, dot, c);
	}

	public static void spark(DrawContext ctx, float cx, float cy, float s, int c) {
		// four-point star from two thin diamonds made of stacked rects
		int steps = 6;
		for (int i = 0; i < steps; i++) {
			float k = 1f - i / (float) steps;
			float len = s * 0.5f * (i + 1) / steps;
			float th = s * 0.11f * k + 0.3f;
			Render2D.roundRect(ctx, cx - th, cy - (s * 0.5f - len + th), th * 2, (s * 0.5f - len + th) * 2, th, c);
			Render2D.roundRect(ctx, cx - (s * 0.5f - len + th), cy - th, (s * 0.5f - len + th) * 2, th * 2, th, c);
		}
		Render2D.circle(ctx, cx, cy, s * 0.13f, c);
	}

	public static void note(DrawContext ctx, float cx, float cy, float s, int c) {
		Render2D.circle(ctx, cx - s * 0.12f, cy + s * 0.25f, s * 0.17f, c);
		Render2D.roundRect(ctx, cx - s * 0.0f, cy - s * 0.38f, s * 0.08f, s * 0.64f, s * 0.04f, c);
		Render2D.roundRect(ctx, cx - s * 0.0f, cy - s * 0.38f, s * 0.3f, s * 0.12f, s * 0.06f, c);
	}
}
