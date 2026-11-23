package com.whiteclient.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.whiteclient.WhiteClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

/**
 * Fast versions of the rounded UI shapes. Instead of filling every pixel row separately (thousands of draw
 * calls for one big circle), shapes are drawn with a few textured quads: pre-rendered anti-aliased discs,
 * rings and corner pieces, plus a pre-rendered planet. This keeps the main menu smooth.
 */
public final class Shapes {
	private Shapes() {
	}

	private static boolean failed;
	private static final int[] DISC_SIZES = {16, 32, 64, 128, 256, 512};
	private static final Identifier[] DISCS = new Identifier[DISC_SIZES.length];
	private static boolean discsReady;
	/** Exact-size corner textures (filled: thickness 0, outline: thickness > 0), keyed by radius / thickness. */
	private static final Map<Long, Identifier> CORNERS = new LinkedHashMap<>(64, 0.75f, true);
	private static int cornerId;
	private static final int MAX_CORNERS = 160;

	public static boolean ok() {
		return !failed;
	}

	private static void fail(Throwable t) {
		if (!failed) {
			failed = true;
			WhiteClient.LOGGER.warn("[WHITECLIENT] fast shapes unavailable, using the slow path", t);
		}
	}

	// ------------------------------------------------------------------ textures

	/** Texture with selectable filtering (linear for scaled sprites). */
	private static final class ShapeTexture extends NativeImageBackedTexture {
		private final boolean linear;
		private GpuSampler sampler;
		private boolean samplerFailed;

		ShapeTexture(String name, int w, int h, boolean linear) {
			super(() -> name, w, h, false);
			this.linear = linear;
		}

		@Override
		public GpuSampler getSampler() {
			if (!linear) {
				return super.getSampler();
			}
			if (sampler == null && !samplerFailed) {
				try {
					sampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
							FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
				} catch (Throwable t) {
					samplerFailed = true;
				}
			}
			return sampler != null ? sampler : super.getSampler();
		}
	}

	private static Identifier upload(String name, int w, int h, int[] argb, boolean linear) {
		ShapeTexture tex = new ShapeTexture("whiteclient_" + name, w, h, linear);
		NativeImage img = tex.getImage();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setColorArgb(x, y, argb[y * w + x]);
			}
		}
		tex.upload();
		Identifier id = Identifier.of(WhiteClient.MOD_ID, "dynamic/shape_" + name);
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
		return id;
	}

	/** Anti-aliased coverage (4x4 samples per pixel) of a ring between radius ri and ro around (c, c). */
	private static int[] ringPixels(int size, float c, float ro, float ri) {
		int[] px = new int[size * size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int hit = 0;
				for (int sy = 0; sy < 4; sy++) {
					float dy = y + (sy + 0.5f) / 4f - c;
					for (int sx = 0; sx < 4; sx++) {
						float dx = x + (sx + 0.5f) / 4f - c;
						float d2 = dx * dx + dy * dy;
						if (d2 <= ro * ro && (ri <= 0 || d2 >= ri * ri)) {
							hit++;
						}
					}
				}
				int a = hit * 255 / 16;
				px[y * size + x] = (a << 24) | 0xFFFFFF;
			}
		}
		return px;
	}

	private static void ensureDiscs() {
		if (discsReady) {
			return;
		}
		for (int i = 0; i < DISC_SIZES.length; i++) {
			int n = DISC_SIZES[i];
			DISCS[i] = upload("disc_" + n, n, n, ringPixels(n, n / 2f, n / 2f - 1f, 0f), true);
		}
		discsReady = true;
	}

	private static Identifier corner(int r, int t) {
		long key = ((long) r << 16) | t;
		Identifier id = CORNERS.get(key);
		if (id != null) {
			return id;
		}
		if (CORNERS.size() >= MAX_CORNERS) {
			Map.Entry<Long, Identifier> eldest = CORNERS.entrySet().iterator().next();
			CORNERS.remove(eldest.getKey());
			try {
				MinecraftClient.getInstance().getTextureManager().destroyTexture(eldest.getValue());
			} catch (Throwable ignored) {
			}
		}
		int size = r * 2;
		id = upload("corner_" + (cornerId++), size, size, ringPixels(size, r, r, t > 0 ? r - t : 0f), false);
		CORNERS.put(key, id);
		return id;
	}

	// ------------------------------------------------------------------ shapes

	private static int scale() {
		return Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor());
	}

	/** Circle at sub-pixel position and size (one textured quad). */
	public static boolean circle(DrawContext ctx, float cx, float cy, float r, int argb) {
		if (failed) {
			return false;
		}
		if ((argb >>> 24) == 0 || r <= 0) {
			return true;
		}
		try {
			ensureDiscs();
			float d = r * 2 * scale();
			int idx = DISC_SIZES.length - 1;
			for (int i = 0; i < DISC_SIZES.length; i++) {
				if (DISC_SIZES[i] - 2 >= d) {
					idx = i;
					break;
				}
			}
			int n = DISC_SIZES[idx];
			sprite(ctx, DISCS[idx], n, cx, cy, r * n / (n - 2f), argb);
			return true;
		} catch (Throwable t) {
			fail(t);
			return false;
		}
	}

	/** Draws a square n x n texture centred at (cx, cy) with half size {@code half} (GUI units). */
	static void sprite(DrawContext ctx, Identifier id, int n, float cx, float cy, float half, int argb) {
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(cx - half, cy - half);
		m.scale(half * 2 / n, half * 2 / n);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, n, n, n, n, n, n, argb);
		m.popMatrix();
	}

	/** Filled rounded rectangle: a few fills + four corner pieces (all in physical pixels). */
	public static boolean roundRect(DrawContext ctx, float x, float y, float w, float h, float radius, int argb) {
		if (failed) {
			return false;
		}
		try {
			int s = scale();
			int x0 = Math.round(x * s), x1 = Math.round((x + w) * s), y0 = Math.round(y * s), y1 = Math.round((y + h) * s);
			if (x1 <= x0 || y1 <= y0) {
				return true;
			}
			int r = Math.min(Math.round(radius * s), Math.min(x1 - x0, y1 - y0) / 2);
			Matrix3x2fStack m = ctx.getMatrices();
			m.pushMatrix();
			m.scale(1f / s, 1f / s);
			if (r <= 0) {
				ctx.fill(x0, y0, x1, y1, argb);
			} else {
				Identifier c = corner(r, 0);
				if (x1 - r > x0 + r) {
					ctx.fill(x0 + r, y0, x1 - r, y1, argb);
				}
				if (y1 - r > y0 + r) {
					ctx.fill(x0, y0 + r, x0 + r, y1 - r, argb);
					ctx.fill(x1 - r, y0 + r, x1, y1 - r, argb);
				}
				corners(ctx, c, r, x0, y0, x1, y1, argb);
			}
			m.popMatrix();
			return true;
		} catch (Throwable t) {
			fail(t);
			return false;
		}
	}

	private static void corners(DrawContext ctx, Identifier c, int r, int x0, int y0, int x1, int y1, int argb) {
		int n = r * 2;
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, c, x0, y0, 0f, 0f, r, r, r, r, n, n, argb);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, c, x1 - r, y0, r, 0f, r, r, r, r, n, n, argb);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, c, x0, y1 - r, 0f, r, r, r, r, r, n, n, argb);
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, c, x1 - r, y1 - r, r, r, r, r, r, r, n, n, argb);
	}

	/** Rounded outline: four straight strips + four ring corners. */
	public static boolean roundRectOutline(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int argb) {
		if (failed) {
			return false;
		}
		try {
			int s = scale();
			int x0 = Math.round(x * s), x1 = Math.round((x + w) * s), y0 = Math.round(y * s), y1 = Math.round((y + h) * s);
			if (x1 <= x0 || y1 <= y0) {
				return true;
			}
			int t = Math.max(1, Math.round(thickness * s));
			int r = Math.min(Math.round(radius * s), Math.min(x1 - x0, y1 - y0) / 2);
			if (r < t || x1 - x0 < 2 * t || y1 - y0 < 2 * t) {
				return false; // tiny or very thick: let the exact slow path handle it
			}
			Matrix3x2fStack m = ctx.getMatrices();
			m.pushMatrix();
			m.scale(1f / s, 1f / s);
			if (x1 - r > x0 + r) {
				ctx.fill(x0 + r, y0, x1 - r, y0 + t, argb);
				ctx.fill(x0 + r, y1 - t, x1 - r, y1, argb);
			}
			if (y1 - r > y0 + r) {
				ctx.fill(x0, y0 + r, x0 + t, y1 - r, argb);
				ctx.fill(x1 - t, y0 + r, x1, y1 - r, argb);
			}
			corners(ctx, corner(r, t), r, x0, y0, x1, y1, argb);
			m.popMatrix();
			return true;
		} catch (Throwable t) {
			fail(t);
			return false;
		}
	}

	// ------------------------------------------------------------------ big planet (pre-rendered)

	private static final int PLANET_TEX = 768;
	private static final float GLOW_EXTENT = 1.48f;
	private static CompletableFuture<int[][]> planetJob;
	private static Identifier planetBody, planetGlow;

	/** Starts rendering the planet textures in the background (call early, e.g. at startup). */
	public static void prewarm() {
		if (planetJob == null) {
			planetJob = CompletableFuture.supplyAsync(() -> new int[][]{planetBodyPixels(PLANET_TEX), planetGlowPixels(256)});
		}
	}

	/** Planet body (shaded sphere + soft bands) and atmosphere glow textures, or null while they are being made. */
	static Identifier[] planet() {
		if (failed) {
			return null;
		}
		if (planetBody != null) {
			return new Identifier[]{planetBody, planetGlow};
		}
		prewarm();
		if (!planetJob.isDone()) {
			return null;
		}
		try {
			int[][] px = planetJob.join();
			planetBody = upload("planet_body", PLANET_TEX, PLANET_TEX, px[0], true);
			planetGlow = upload("planet_glow", 256, 256, px[1], true);
			return new Identifier[]{planetBody, planetGlow};
		} catch (Throwable t) {
			fail(t);
			return null;
		}
	}

	static int planetTexSize() {
		return PLANET_TEX;
	}

	static float glowExtent() {
		return GLOW_EXTENT;
	}

	/**
	 * Same look as the old layered planet (dark base, lit towards the top left, three faint bands),
	 * but as one smooth gradient. The planet radius is (size / 2 - 2) pixels.
	 */
	private static int[] planetBodyPixels(int size) {
		int[] out = new int[size * size];
		float rad = size / 2f - 2f, c = size / 2f;
		int dark = 0x1D1E23, light = 0xB7BAC4;
		float[][] bands = new float[3][];
		for (int b = 0; b < 3; b++) {
			float by = -0.35f + b * 0.32f;
			float half = (float) Math.sqrt(Math.max(0, 1 - by * by)) * 0.92f;
			bands[b] = new float[]{by, half};
		}
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float u = (x + 0.5f - c) / rad, v = (y + 0.5f - c) / rad;
				float dist = (float) Math.sqrt(u * u + v * v);
				float cover = Math.max(0f, Math.min(1f, (1f - dist) * rad + 0.5f));
				if (cover <= 0f) {
					continue;
				}
				// largest t where the pixel is still inside the shifted, shrinking light layer
				float lo = 0f, hi = 1f;
				for (int it = 0; it < 14; it++) {
					float t = (lo + hi) / 2f;
					float du = u + 0.36f * t, dv = v + 0.36f * t, rr = 1f - 0.86f * t;
					if (du * du + dv * dv <= rr * rr) {
						lo = t;
					} else {
						hi = t;
					}
				}
				float k = (float) Math.pow(lo, 1.25);
				float r = (((dark >> 16) & 0xFF) + ((((light >> 16) & 0xFF) - ((dark >> 16) & 0xFF)) * k));
				float g = (((dark >> 8) & 0xFF) + ((((light >> 8) & 0xFF) - ((dark >> 8) & 0xFF)) * k));
				float bl = ((dark & 0xFF) + (((light & 0xFF) - (dark & 0xFF)) * k));
				for (float[] band : bands) {
					float top = band[0], h = 0.07f, rr = 0.035f;
					if (v >= top && v <= top + h) {
						float half = band[1];
						float dy = Math.max(0f, Math.max(top + rr - v, v - (top + h - rr)));
						float inset = rr - (float) Math.sqrt(Math.max(0f, rr * rr - dy * dy));
						float edge = Math.min(u - (-half + inset), (half - inset) - u) * rad;
						float a = Math.max(0f, Math.min(1f, edge + 0.5f)) * (14f / 255f);
						r += (255 - r) * a;
						g += (255 - g) * a;
						bl += (255 - bl) * a;
					}
				}
				int ia = Math.round(cover * 255);
				out[y * size + x] = (ia << 24) | (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(bl);
			}
		}
		return out;
	}

	/** Soft white atmosphere around the planet: strongest at the surface, gone at GLOW_EXTENT radii. */
	private static int[] planetGlowPixels(int size) {
		int[] out = new int[size * size];
		float c = size / 2f, ext = size / 2f - 1f;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float dx = x + 0.5f - c, dy = y + 0.5f - c;
				float d = (float) Math.sqrt(dx * dx + dy * dy) / ext * GLOW_EXTENT; // in planet radii
				float layers = Math.max(0f, Math.min(10f, (GLOW_EXTENT - d) / 0.045f));
				float a = 1f - (float) Math.pow(1f - 6f / 255f, layers);
				out[y * size + x] = (Math.round(a * 255) << 24) | 0xFFFFFF;
			}
		}
		return out;
	}
}
