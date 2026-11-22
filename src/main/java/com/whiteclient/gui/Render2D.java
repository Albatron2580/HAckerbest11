package com.whiteclient.gui;

import com.whiteclient.render.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

/**
 * 2D helpers for the white/black UI. Rounded shapes are drawn row by row in
 * physical pixels with anti-aliased edges, so they stay smooth at any GUI scale.
 */
public final class Render2D {
	private Render2D() {
	}

	private static int scale() {
		return Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor());
	}

	public static void roundRect(DrawContext ctx, float x, float y, float w, float h, float radius, int argb) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) {
			return;
		}
		int s = scale();
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.scale(1f / s, 1f / s);

		float X = x * s, Y = y * s, W = w * s, H = h * s;
		float R = Math.min(radius * s, Math.min(W, H) / 2f);
		int top = Math.round(Y);
		int bottom = Math.round(Y + H);
		int cornerTop = (int) Math.ceil(Y + R);
		int cornerBottom = (int) Math.floor(Y + H - R);
		int left = Math.round(X);
		int right = Math.round(X + W);

		if (cornerBottom > cornerTop) {
			ctx.fill(left, cornerTop, right, cornerBottom, argb);
		} else {
			cornerTop = cornerBottom = (top + bottom) / 2;
		}
		for (int py = top; py < bottom; py++) {
			if (py >= cornerTop && py < cornerBottom) {
				continue;
			}
			float cy = py + 0.5f;
			float dy;
			if (cy < Y + R) {
				dy = (Y + R) - cy;
			} else if (cy > Y + H - R) {
				dy = cy - (Y + H - R);
			} else {
				dy = 0;
			}
			float inset = R - (float) Math.sqrt(Math.max(0f, R * R - dy * dy));
			float l = X + inset;
			float r = X + W - inset;
			int li = (int) Math.ceil(l);
			int ri = (int) Math.floor(r);
			if (ri > li) {
				ctx.fill(li, py, ri, py + 1, argb);
			}
			float fl = li - l;
			if (fl > 0.03f) {
				ctx.fill(li - 1, py, li, py + 1, Colors.mulAlpha(argb, fl));
			}
			float fr = r - ri;
			if (fr > 0.03f && ri >= li) {
				ctx.fill(ri, py, ri + 1, py + 1, Colors.mulAlpha(argb, fr));
			}
		}
		m.popMatrix();
	}

	/** Rounded rectangle with a 1px border (border drawn behind the fill). */
	public static void roundRectBordered(DrawContext ctx, float x, float y, float w, float h, float radius, int fill, int border) {
		roundRect(ctx, x - 1, y - 1, w + 2, h + 2, radius + 1, border);
		roundRect(ctx, x, y, w, h, radius, fill);
	}

	public static void circle(DrawContext ctx, float cx, float cy, float r, int argb) {
		roundRect(ctx, cx - r, cy - r, r * 2, r * 2, r, argb);
	}

	/** Soft drop shadow made from stacked translucent rounded rects. */
	public static void shadow(DrawContext ctx, float x, float y, float w, float h, float radius, int layers, int alpha) {
		for (int i = layers; i >= 1; i--) {
			float a = alpha / (float) layers;
			roundRect(ctx, x - i, y - i + 1, w + i * 2, h + i * 2, radius + i, Colors.withAlpha(0, (int) a));
		}
	}

	/** iOS-like toggle switch. {@code t} is the animation progress 0 (off) .. 1 (on). */
	public static void toggle(DrawContext ctx, float x, float y, float t) {
		float w = 20, h = 11;
		int track = Colors.mixArgb(0xFF2A2A33, 0xFFF2F2F5, t);
		roundRect(ctx, x, y, w, h, h / 2f, track);
		float knobX = x + 5.5f + (w - 11f) * t;
		int knob = Colors.mixArgb(0xFF8C8C98, 0xFF0E0E12, t);
		circle(ctx, knobX, y + h / 2f, 3.6f, knob);
	}

	public static void text(DrawContext ctx, TextRenderer tr, String s, float x, float y, int color) {
		ctx.drawText(tr, s, Math.round(x), Math.round(y), color, false);
	}

	public static void textCentered(DrawContext ctx, TextRenderer tr, String s, float cx, float y, int color) {
		ctx.drawText(tr, s, Math.round(cx - tr.getWidth(s) / 2f), Math.round(y), color, false);
	}

	/** Text drawn at a custom scale (e.g. 0.75 for small captions). */
	public static void textScaled(DrawContext ctx, TextRenderer tr, String s, float x, float y, float scale, int color) {
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(scale, scale);
		ctx.drawText(tr, s, 0, 0, color, false);
		m.popMatrix();
	}

	/** Small white planet-with-ring logo. */
	public static void logo(DrawContext ctx, float cx, float cy, float size, float time) {
		float r = size * 0.42f;
		// ring behind
		roundRect(ctx, cx - size * 0.95f, cy - size * 0.16f, size * 1.9f, size * 0.32f, size * 0.16f, 0x55FFFFFF);
		circle(ctx, cx, cy, r + 0.8f, 0x33FFFFFF);
		circle(ctx, cx, cy, r, 0xFFFFFFFF);
		// ring front highlight
		roundRect(ctx, cx - size * 0.95f, cy + size * 0.02f, size * 1.9f, size * 0.12f, size * 0.06f, 0xCCFFFFFF);
		float a = time * 1.7f;
		circle(ctx, cx + (float) Math.cos(a) * size * 0.85f, cy + (float) Math.sin(a) * size * 0.12f, size * 0.09f, 0xFFFFFFFF);
	}
}
