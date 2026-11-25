package com.whiteclient.render;

import com.whiteclient.WhiteClient;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public final class SolidBatch {
	private static final int FLOATS = 12;
	private float[] pos = new float[FLOATS * 256];
	private int[] col = new int[4 * 256];
	private long[] order = new long[256];
	private int count;

	private static RenderType layer;
	private static boolean triangles;
	private static boolean resolved;
	private static boolean entityFormat;
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath(WhiteClient.MOD_ID, "dynamic/solid_white");

	private void ensure() {
		if (count >= order.length) {
			int n = order.length * 2;
			pos = Arrays.copyOf(pos, n * FLOATS);
			col = Arrays.copyOf(col, n * 4);
			order = Arrays.copyOf(order, n);
		}
	}

	public void quad(float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
					 float x3, float y3, float z3, int c3, float x4, float y4, float z4, int c4,
					 float nx, float ny, float nz) {
		if (nx != 0 || ny != 0 || nz != 0) {
			float cx = (x1 + x2 + x3 + x4) * 0.25f, cy = (y1 + y2 + y3 + y4) * 0.25f, cz = (z1 + z2 + z3 + z4) * 0.25f;
			if (nx * -cx + ny * -cy + nz * -cz <= 0) {
				return;
			}
		}
		ensure();
		int p = count * FLOATS;
		pos[p] = x1;
		pos[p + 1] = y1;
		pos[p + 2] = z1;
		pos[p + 3] = x2;
		pos[p + 4] = y2;
		pos[p + 5] = z2;
		pos[p + 6] = x3;
		pos[p + 7] = y3;
		pos[p + 8] = z3;
		pos[p + 9] = x4;
		pos[p + 10] = y4;
		pos[p + 11] = z4;
		int c = count * 4;
		col[c] = c1;
		col[c + 1] = c2;
		col[c + 2] = c3;
		col[c + 3] = c4;
		count++;
	}

	private static void resolve() {
		if (resolved) {
			return;
		}
		resolved = true;
		try {
			DynamicTexture white = new DynamicTexture(() -> "whiteclient_solid_white", 4, 4, false);
			NativeImage img = white.getPixels();
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					img.setPixel(x, y, 0xFFFFFFFF);
				}
			}
			white.upload();
			Minecraft.getInstance().getTextureManager().register(WHITE, white);
			RenderType l = RenderTypes.entityTranslucent(WHITE);
			if (l.format() == DefaultVertexFormat.NEW_ENTITY && Layers.topology(l).equals("QUADS")) {
				layer = l;
				entityFormat = true;
				WhiteClient.LOGGER.info("[WHITECLIENT] solid layer: {} (entity, depth)", l);
				return;
			}
		} catch (Throwable t) {
			WhiteClient.LOGGER.warn("[WHITECLIENT] entity solid layer unavailable, using debug quads", t);
		}
		try {
			RenderType l = RenderTypes.debugQuads();
			if (l.format() == DefaultVertexFormat.POSITION_COLOR) {
				String mode = Layers.topology(l);
				if (mode.equals("QUADS") || mode.equals("TRIANGLES")) {
					layer = l;
					triangles = mode.equals("TRIANGLES");
					WhiteClient.LOGGER.info("[WHITECLIENT] solid layer: {} ({})", l, mode);
					return;
				}
			}
		} catch (Throwable t) {
			WhiteClient.LOGGER.warn("[WHITECLIENT] solid layer unavailable", t);
		}
		WhiteClient.LOGGER.warn("[WHITECLIENT] no solid render layer; models disabled");
	}

	public void draw(WorldDraw out, Matrix4f m) {
		if (count == 0) {
			return;
		}
		resolve();
		if (layer == null) {
			count = 0;
			return;
		}
		for (int i = 0; i < count; i++) {
			int p = i * FLOATS;
			float cx = (pos[p] + pos[p + 3] + pos[p + 6] + pos[p + 9]) * 0.25f;
			float cy = (pos[p + 1] + pos[p + 4] + pos[p + 7] + pos[p + 10]) * 0.25f;
			float cz = (pos[p + 2] + pos[p + 5] + pos[p + 8] + pos[p + 11]) * 0.25f;
			float d = cx * cx + cy * cy + cz * cz;
			order[i] = ((long) Float.floatToIntBits(d) << 32) | (i & 0xFFFFFFFFL);
		}
		Arrays.sort(order, 0, count);
		final int n = count;
		final boolean now = out.immediate();
		final float[] p0 = now ? pos : Arrays.copyOf(pos, n * FLOATS);
		final int[] c0 = now ? col : Arrays.copyOf(col, n * 4);
		final long[] o0 = now ? order : Arrays.copyOf(order, n);
		final Matrix4f m0 = now ? m : new Matrix4f(m);
		out.geometry(layer, vc -> emitAll(vc, m0, p0, c0, o0, n));
		count = 0;
	}

	private static void emitAll(VertexConsumer vc, Matrix4f m, float[] pos, int[] col, long[] order, int count) {
		for (int k = count - 1; k >= 0; k--) {
			int i = (int) order[k];
			int p = i * FLOATS, c = i * 4;
			float x1 = pos[p], y1 = pos[p + 1], z1 = pos[p + 2];
			float x2 = pos[p + 3], y2 = pos[p + 4], z2 = pos[p + 5];
			float x3 = pos[p + 6], y3 = pos[p + 7], z3 = pos[p + 8];
			float x4 = pos[p + 9], y4 = pos[p + 10], z4 = pos[p + 11];
			float ax = x3 - x1, ay = y3 - y1, az = z3 - z1;
			float bx = x4 - x2, by = y4 - y2, bz = z4 - z2;
			float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
			if (nx * -x1 + ny * -y1 + nz * -z1 >= 0) {
				emit(vc, m, x1, y1, z1, col[c], x2, y2, z2, col[c + 1], x3, y3, z3, col[c + 2], x4, y4, z4, col[c + 3]);
			} else {
				emit(vc, m, x1, y1, z1, col[c], x4, y4, z4, col[c + 3], x3, y3, z3, col[c + 2], x2, y2, z2, col[c + 1]);
			}
		}
	}

	private static void emit(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
							 float x3, float y3, float z3, int c3, float x4, float y4, float z4, int c4) {
		if (entityFormat) {
			ent(vc, m, x1, y1, z1, c1);
			ent(vc, m, x2, y2, z2, c2);
			ent(vc, m, x3, y3, z3, c3);
			ent(vc, m, x4, y4, z4, c4);
			return;
		}
		if (triangles) {
			vc.addVertex(m, x1, y1, z1).setColor(c1);
			vc.addVertex(m, x2, y2, z2).setColor(c2);
			vc.addVertex(m, x3, y3, z3).setColor(c3);
			vc.addVertex(m, x1, y1, z1).setColor(c1);
			vc.addVertex(m, x3, y3, z3).setColor(c3);
			vc.addVertex(m, x4, y4, z4).setColor(c4);
		} else {
			vc.addVertex(m, x1, y1, z1).setColor(c1);
			vc.addVertex(m, x2, y2, z2).setColor(c2);
			vc.addVertex(m, x3, y3, z3).setColor(c3);
			vc.addVertex(m, x4, y4, z4).setColor(c4);
		}
	}

	private static void ent(VertexConsumer vc, Matrix4f m, float x, float y, float z, int c) {
		vc.addVertex(m, x, y, z).setColor(c).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
	}
}
