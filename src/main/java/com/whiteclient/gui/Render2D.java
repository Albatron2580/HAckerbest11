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

	/** Horizontal span [left, right] of a rounded rect at physical row centre cy, or null if outside. */
	private static float[] span(float X, float Y, float W, float H, float R, float cy) {
		if (cy < Y || cy > Y + H || W <= 0 || H <= 0) {
			return null;
		}
		float dy;
		if (cy < Y + R) {
			dy = (Y + R) - cy;
		} else if (cy > Y + H - R) {
			dy = cy - (Y + H - R);
		} else {
			dy = 0;
		}
		float inset = R - (float) Math.sqrt(Math.max(0f, R * R - dy * dy));
		return new float[]{X + inset, X + W - inset};
	}

	/** Fills [l, r] on one physical row with anti-aliased ends. */
	private static void fillSpan(DrawContext ctx, float l, float r, int py, int argb) {
		if (r <= l) {
			return;
		}
		int li = (int) Math.ceil(l);
		int ri = (int) Math.floor(r);
		if (ri > li) {
			ctx.fill(li, py, ri, py + 1, argb);
		} else {
			ctx.fill((int) Math.floor(l), py, (int) Math.floor(l) + 1, py + 1, Colors.mulAlpha(argb, r - l));
			return;
		}
		float fl = li - l;
		if (fl > 0.03f) {
			ctx.fill(li - 1, py, li, py + 1, Colors.mulAlpha(argb, fl));
		}
		float fr = r - ri;
		if (fr > 0.03f) {
			ctx.fill(ri, py, ri + 1, py + 1, Colors.mulAlpha(argb, fr));
		}
	}

	/** Anti-aliased rounded outline (only the ring is drawn, so it works over translucent fills). */
	public static void roundRectOutline(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int argb) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) {
			return;
		}
		int s = scale();
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.scale(1f / s, 1f / s);
		float X = x * s, Y = y * s, W = w * s, H = h * s;
		float R = Math.min(radius * s, Math.min(W, H) / 2f);
		float T = Math.max(1f, thickness * s);
		float iX = X + T, iY = Y + T, iW = W - 2 * T, iH = H - 2 * T;
		float iR = Math.max(0f, R - T);
		int top = (int) Math.floor(Y);
		int bottom = (int) Math.ceil(Y + H);
		for (int py = top; py < bottom; py++) {
			float cy = py + 0.5f;
			float[] o = span(X, Y, W, H, R, cy);
			if (o == null) {
				continue;
			}
			float[] in = span(iX, iY, iW, iH, iR, cy);
			if (in == null) {
				fillSpan(ctx, o[0], o[1], py, argb);
			} else {
				fillSpan(ctx, o[0], in[0], py, argb);
				fillSpan(ctx, in[1], o[1], py, argb);
			}
		}
		m.popMatrix();
	}

	/** Vertical gradient inside a rounded rect (top colour -> bottom colour). */
	public static void roundRectGradient(DrawContext ctx, float x, float y, float w, float h, float radius, int topArgb, int bottomArgb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int s = scale();
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.scale(1f / s, 1f / s);
		float X = x * s, Y = y * s, W = w * s, H = h * s;
		float R = Math.min(radius * s, Math.min(W, H) / 2f);
		int top = (int) Math.floor(Y);
		int bottom = (int) Math.ceil(Y + H);
		for (int py = top; py < bottom; py++) {
			float cy = py + 0.5f;
			float[] o = span(X, Y, W, H, R, cy);
			if (o == null) {
				continue;
			}
			float t = (cy - Y) / H;
			fillSpan(ctx, o[0], o[1], py, Colors.mixArgb(topArgb, bottomArgb, t));
		}
		m.popMatrix();
	}

	/**
	 * Filled polygon (any shape, even-odd rule) with anti-aliased left/right edges.
	 * {@code pts} = x0,y0,x1,y1,... in GUI coordinates.
	 */
	public static void polygon(DrawContext ctx, float[] pts, int argb) {
		int n = pts.length / 2;
		if (n < 3 || (argb >>> 24) == 0) {
			return;
		}
		int s = scale();
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.scale(1f / s, 1f / s);
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		float[] p = new float[pts.length];
		for (int i = 0; i < pts.length; i++) {
			p[i] = pts[i] * s;
		}
		for (int i = 0; i < n; i++) {
			minY = Math.min(minY, p[i * 2 + 1]);
			maxY = Math.max(maxY, p[i * 2 + 1]);
		}
		float[] xs = new float[n];
		for (int py = (int) Math.floor(minY); py < (int) Math.ceil(maxY); py++) {
			float cy = py + 0.5f;
			int count = 0;
			for (int i = 0; i < n; i++) {
				float x1 = p[i * 2], y1 = p[i * 2 + 1];
				float x2 = p[((i + 1) % n) * 2], y2 = p[((i + 1) % n) * 2 + 1];
				if ((y1 <= cy && y2 > cy) || (y2 <= cy && y1 > cy)) {
					xs[count++] = x1 + (cy - y1) / (y2 - y1) * (x2 - x1);
				}
			}
			java.util.Arrays.sort(xs, 0, count);
			for (int k = 0; k + 1 < count; k += 2) {
				fillSpan(ctx, xs[k], xs[k + 1], py, argb);
			}
		}
		m.popMatrix();
	}

	/** Thick line segment as a polygon. */
	public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, float width, int argb) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1.0e-4f) {
			return;
		}
		float nx = -dy / len * width / 2f, ny = dx / len * width / 2f;
		polygon(ctx, new float[]{x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny}, argb);
	}

	/** Horizontal gradient (left colour -> right colour) made of thin vertical strips. */
	public static void hGradient(DrawContext ctx, float x, float y, float w, float h, int left, int right) {
		int steps = Math.max(2, Math.min(64, (int) w));
		for (int i = 0; i < steps; i++) {
			float x0 = x + w * i / steps;
			float x1 = x + w * (i + 1) / steps;
			int c = Colors.mixArgb(left, right, (i + 0.5f) / steps);
			ctx.fill(Math.round(x0), Math.round(y), Math.round(x1), Math.round(y + h), c);
		}
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

	/** Big planet logo: white planet with a tilted dotted ring and a small orbiting moon. */
	public static void planetLogo(DrawContext ctx, float cx, float cy, float r, float time, int alpha) {
		double tilt = Math.toRadians(-20);
		float ra = r * 1.95f, rb = r * 0.52f;
		float dot = Math.max(0.45f, r * 0.085f);
		int n = 70;
		float cos = (float) Math.cos(tilt), sin = (float) Math.sin(tilt);
		// back half of the ring
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			if (Math.sin(a) >= 0) {
				continue;
			}
			float ex = (float) Math.cos(a) * ra, ey = (float) Math.sin(a) * rb;
			circle(ctx, cx + ex * cos - ey * sin, cy + ex * sin + ey * cos, dot, Colors.withAlpha(0xFFFFFF, (int) (alpha * 0.45f)));
		}
		circle(ctx, cx, cy, r * 1.25f, Colors.withAlpha(0xFFFFFF, (int) (alpha * 0.06f)));
		circle(ctx, cx, cy, r, Colors.withAlpha(0xFFFFFF, alpha));
		// soft shadow on the planet's lower right
		circle(ctx, cx + r * 0.28f, cy + r * 0.3f, r * 0.62f, Colors.withAlpha(0xD8DCE6, (int) (alpha * 0.55f)));
		circle(ctx, cx - r * 0.12f, cy - r * 0.12f, r * 0.7f, Colors.withAlpha(0xFFFFFF, alpha));
		// front half of the ring
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			if (Math.sin(a) < 0) {
				continue;
			}
			float ex = (float) Math.cos(a) * ra, ey = (float) Math.sin(a) * rb;
			circle(ctx, cx + ex * cos - ey * sin, cy + ex * sin + ey * cos, dot, Colors.withAlpha(0xFFFFFF, alpha));
		}
		double m = time * 0.9;
		float mx = (float) Math.cos(m) * ra * 1.08f, my = (float) Math.sin(m) * rb * 1.08f;
		if (Math.sin(m) >= 0 || Math.abs(mx) > r) {
			circle(ctx, cx + mx * cos - my * sin, cy + mx * sin + my * cos, r * 0.16f, Colors.withAlpha(0xFFFFFF, alpha));
		}
	}

	/**
	 * A big shaded planet with the dotted logo ring, for backgrounds. The body is opaque (lit from the
	 * top left), the ring passes behind and in front of it, and a small moon orbits slowly.
	 */
	/** Ring half-width of {@link #bigPlanet} relative to the planet radius (moon orbits at 1.1x this). */
	public static final float BIG_RING = 1.55f;

	public static void bigPlanet(DrawContext ctx, float cx, float cy, float r, float time, float alpha) {
		if (alpha <= 0.01f || r < 4) {
			return;
		}
		double tilt = Math.toRadians(-18);
		float cos = (float) Math.cos(tilt), sin = (float) Math.sin(tilt);
		float ra = r * BIG_RING, rb = r * 0.4f;
		int n = Math.max(90, (int) (ra * 1.1f));
		float dot = Math.max(0.6f, r * 0.022f);
		float spinOffset = time * 0.05f;
		// atmosphere glow
		for (int g = 10; g >= 1; g--) {
			circle(ctx, cx, cy, r * (1f + g * 0.045f), Colors.withAlpha(0xFFFFFF, (int) (alpha * 6)));
		}
		// back half of the ring (inner faint band + dots)
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + spinOffset;
			if (Math.sin(a) >= 0) {
				continue;
			}
			for (int band = 0; band < 2; band++) {
				float k = band == 0 ? 1f : 0.86f;
				float ex = (float) Math.cos(a) * ra * k, ey = (float) Math.sin(a) * rb * k;
				circle(ctx, cx + ex * cos - ey * sin, cy + ex * sin + ey * cos, dot * (band == 0 ? 1f : 0.7f),
						Colors.withAlpha(0xFFFFFF, (int) (alpha * (band == 0 ? 0.35f : 0.2f) * 255)));
			}
		}
		// body: dark base with lighter layers shifted toward the light
		int dark = 0x1D1E23, light = 0xB7BAC4;
		int layers = 40;
		for (int i = 0; i <= layers; i++) {
			float t = i / (float) layers;
			float off = r * 0.36f * t;
			float rr = r * (1f - 0.86f * t);
			int col = Colors.mix(dark, light, (float) Math.pow(t, 1.25));
			circle(ctx, cx - off, cy - off, rr, Colors.withAlpha(col, (int) (alpha * 255)));
		}
		// soft bands across the surface (gas giant look)
		for (int b = 0; b < 3; b++) {
			float by = cy - r * 0.35f + b * r * 0.32f;
			float half = (float) Math.sqrt(Math.max(0, r * r - (by - cy) * (by - cy))) * 0.92f;
			roundRect(ctx, cx - half, by, half * 2, r * 0.07f, r * 0.035f, Colors.withAlpha(0xFFFFFF, (int) (alpha * 14)));
		}
		// front half of the ring
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n + spinOffset;
			if (Math.sin(a) < 0) {
				continue;
			}
			for (int band = 0; band < 2; band++) {
				float k = band == 0 ? 1f : 0.86f;
				float ex = (float) Math.cos(a) * ra * k, ey = (float) Math.sin(a) * rb * k;
				circle(ctx, cx + ex * cos - ey * sin, cy + ex * sin + ey * cos, dot * (band == 0 ? 1f : 0.7f),
						Colors.withAlpha(0xFFFFFF, (int) (alpha * (band == 0 ? 0.85f : 0.45f) * 255)));
			}
		}
		// moon
		double m = time * 0.25;
		float mx = (float) Math.cos(m) * ra * 1.1f, my = (float) Math.sin(m) * rb * 1.1f;
		boolean behind = Math.sin(m) < 0 && Math.abs(mx) < r;
		if (!behind) {
			float px = cx + mx * cos - my * sin, py = cy + mx * sin + my * cos;
			float mr = r * 0.12f;
			circle(ctx, px, py, mr * 1.6f, Colors.withAlpha(0xFFFFFF, (int) (alpha * 20)));
			circle(ctx, px, py, mr, Colors.withAlpha(0xD9DBE2, (int) (alpha * 255)));
			circle(ctx, px - mr * 0.25f, py - mr * 0.25f, mr * 0.65f, Colors.withAlpha(0xFFFFFF, (int) (alpha * 255)));
		}
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
