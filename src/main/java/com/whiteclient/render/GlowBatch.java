package com.whiteclient.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.whiteclient.WhiteClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.Arrays;

public final class GlowBatch {
	private static final int FLOATS_PER_QUAD = 12;

	private float[] pos = new float[FLOATS_PER_QUAD * 1024];
	private int[] col = new int[4 * 1024];
	private long[] order = new long[1024];
	private int count;

	private static RenderLayer cachedLayer;
	private static boolean cachedTriangles;
	private static boolean cachedNeedsSort;
	private static boolean layerResolved;

	private float viewX, viewY, viewZ;
	private boolean viewSet;

	public void setView(float fx, float fy, float fz) {
		viewX = fx;
		viewY = fy;
		viewZ = fz;
		viewSet = true;
	}

	public void clear() {
		count = 0;
	}

	public int size() {
		return count;
	}

	private void ensure() {
		if (count >= order.length) {
			int n = order.length * 2;
			pos = Arrays.copyOf(pos, n * FLOATS_PER_QUAD);
			col = Arrays.copyOf(col, n * 4);
			order = Arrays.copyOf(order, n);
		}
	}

	public void quad(float x1, float y1, float z1, int c1,
					 float x2, float y2, float z2, int c2,
					 float x3, float y3, float z3, int c3,
					 float x4, float y4, float z4, int c4) {
		if (((c1 | c2 | c3 | c4) >>> 24) == 0) {
			return;
		}
		ensure();
		int p = count * FLOATS_PER_QUAD;
		pos[p] = x1; pos[p + 1] = y1; pos[p + 2] = z1;
		pos[p + 3] = x2; pos[p + 4] = y2; pos[p + 5] = z2;
		pos[p + 6] = x3; pos[p + 7] = y3; pos[p + 8] = z3;
		pos[p + 9] = x4; pos[p + 10] = y4; pos[p + 11] = z4;
		int c = count * 4;
		col[c] = c1; col[c + 1] = c2; col[c + 2] = c3; col[c + 3] = c4;
		count++;
	}

	public interface QuadVisitor {
		void quad(float[] positions, int posOffset, int[] colors, int colorOffset);
	}

	public void visit(QuadVisitor visitor) {
		for (int i = 0; i < count; i++) {
			visitor.quad(pos, i * FLOATS_PER_QUAD, col, i * 4);
		}
	}

	public void ribbon(float ax, float ay, float az, float bx, float by, float bz, float width, int core) {
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		float vx = (ax + bx) * 0.5f, vy = (ay + by) * 0.5f, vz = (az + bz) * 0.5f;
		float sx = dy * vz - dz * vy;
		float sy = dz * vx - dx * vz;
		float sz = dx * vy - dy * vx;
		float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1.0e-6f) {
			return;
		}
		float k = width / len;
		sx *= k; sy *= k; sz *= k;
		int edge = core & 0x00FFFFFF;
		quad(ax + sx, ay + sy, az + sz, edge, ax, ay, az, core, bx, by, bz, core, bx + sx, by + sy, bz + sz, edge);
		quad(ax, ay, az, core, ax - sx, ay - sy, az - sz, edge, bx - sx, by - sy, bz - sz, edge, bx, by, bz, core);
	}

	public void glowLine(float ax, float ay, float az, float bx, float by, float bz, float width, int rgb, float intensity) {
		if (intensity <= 0.003f) {
			return;
		}
		ribbon(ax, ay, az, bx, by, bz, width, Colors.argb(rgb, 0.22f * intensity));
		ribbon(ax, ay, az, bx, by, bz, width * 0.42f, Colors.argb(rgb, 0.55f * intensity));
		ribbon(ax, ay, az, bx, by, bz, width * 0.14f, Colors.argb(Colors.mix(rgb, 0xFFFFFF, 0.65f), Math.min(1f, 1.0f * intensity)));
	}

	public void disc(float cx, float cy, float cz, float radius, int rgb, float alpha, int segments) {
		if (alpha <= 0.003f) {
			return;
		}
		if (viewSet && cx * viewX + cy * viewY + cz * viewZ < -radius * 1.5f) {
			return;
		}
		float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
		float vx, vy, vz;
		if (len < 1.0e-4f) {
			vx = 0; vy = 0; vz = 1;
		} else {
			vx = cx / len; vy = cy / len; vz = cz / len;
		}
		float rx = -vz, ry = 0, rz = vx;
		float rl = (float) Math.sqrt(rx * rx + rz * rz);
		if (rl < 1.0e-4f) {
			rx = 1; rz = 0; rl = 1;
		}
		rx /= rl; rz /= rl;
		float ux = ry * vz - rz * vy;
		float uy = rz * vx - rx * vz;
		float uz = rx * vy - ry * vx;

		int center = Colors.argb(rgb, alpha);
		int mid = Colors.argb(rgb, alpha * 0.33f);
		int edge = rgb & 0xFFFFFF;
		float inner = radius * 0.38f;
		double step = Math.PI * 2 / segments;
		for (int i = 0; i < segments; i++) {
			float c0 = (float) Math.cos(i * step), s0 = (float) Math.sin(i * step);
			float c1 = (float) Math.cos((i + 1) * step), s1 = (float) Math.sin((i + 1) * step);
			float ax0 = (rx * c0 + ux * s0), ay0 = (ry * c0 + uy * s0), az0 = (rz * c0 + uz * s0);
			float ax1 = (rx * c1 + ux * s1), ay1 = (ry * c1 + uy * s1), az1 = (rz * c1 + uz * s1);
			quad(cx, cy, cz, center, cx, cy, cz, center,
					cx + ax0 * inner, cy + ay0 * inner, cz + az0 * inner, mid,
					cx + ax1 * inner, cy + ay1 * inner, cz + az1 * inner, mid);
			quad(cx + ax0 * inner, cy + ay0 * inner, cz + az0 * inner, mid,
					cx + ax0 * radius, cy + ay0 * radius, cz + az0 * radius, edge,
					cx + ax1 * radius, cy + ay1 * radius, cz + az1 * radius, edge,
					cx + ax1 * inner, cy + ay1 * inner, cz + az1 * inner, mid);
		}
	}

	public void flatRing(float cx, float cy, float cz, float radius, float width, int rgb, float alpha) {
		if (alpha <= 0.004f || radius <= 0) {
			return;
		}
		int seg = Math.max(16, Math.min(48, (int) (radius * 40)));
		int core = Colors.argb(rgb, Math.min(1f, alpha));
		int edge = rgb & 0xFFFFFF;
		float inner = Math.max(0, radius - width), outer = radius + width;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			quad(cx + c0 * outer, cy, cz + s0 * outer, edge, cx + c1 * outer, cy, cz + s1 * outer, edge,
					cx + c1 * radius, cy, cz + s1 * radius, core, cx + c0 * radius, cy, cz + s0 * radius, core);
			quad(cx + c0 * radius, cy, cz + s0 * radius, core, cx + c1 * radius, cy, cz + s1 * radius, core,
					cx + c1 * inner, cy, cz + s1 * inner, edge, cx + c0 * inner, cy, cz + s0 * inner, edge);
		}
	}

	public void flatDisc(float cx, float cy, float cz, float radius, int rgb, float alpha) {
		if (alpha <= 0.004f || radius <= 0) {
			return;
		}
		int seg = 24;
		int center = Colors.argb(rgb, Math.min(1f, alpha));
		int edge = rgb & 0xFFFFFF;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			quad(cx, cy, cz, center, cx, cy, cz, center,
					cx + (float) Math.cos(a0) * radius, cy, cz + (float) Math.sin(a0) * radius, edge,
					cx + (float) Math.cos(a1) * radius, cy, cz + (float) Math.sin(a1) * radius, edge);
		}
	}

	private static void resolveLayer() {
		if (layerResolved) {
			return;
		}
		layerResolved = true;
		RenderLayer[] candidates = {RenderLayers.dragonRays(), RenderLayers.lightning()};
		for (RenderLayer layer : candidates) {
			try {
				if (layer.getVertexFormat() != VertexFormats.POSITION_COLOR) {
					continue;
				}
				VertexFormat.DrawMode mode = layer.getDrawMode();
				if (mode == VertexFormat.DrawMode.TRIANGLES || mode == VertexFormat.DrawMode.QUADS) {
					cachedLayer = layer;
					cachedTriangles = mode == VertexFormat.DrawMode.TRIANGLES;
					cachedNeedsSort = layer != candidates[0];
					WhiteClient.LOGGER.info("[WHITECLIENT] glow layer: {} ({})", layer, mode);
					return;
				}
			} catch (Throwable t) {
				WhiteClient.LOGGER.warn("[WHITECLIENT] could not inspect render layer", t);
			}
		}
		WhiteClient.LOGGER.warn("[WHITECLIENT] no usable glow render layer found; glow effects disabled");
	}

	public void draw(VertexConsumerProvider consumers, Matrix4f matrix) {
		if (count == 0) {
			return;
		}
		resolveLayer();
		if (cachedLayer == null) {
			count = 0;
			return;
		}
		for (int i = 0; i < count; i++) {
			if (cachedNeedsSort) {
				int p = i * FLOATS_PER_QUAD;
				float cx = (pos[p] + pos[p + 3] + pos[p + 6] + pos[p + 9]) * 0.25f;
				float cy = (pos[p + 1] + pos[p + 4] + pos[p + 7] + pos[p + 10]) * 0.25f;
				float cz = (pos[p + 2] + pos[p + 5] + pos[p + 8] + pos[p + 11]) * 0.25f;
				float d = cx * cx + cy * cy + cz * cz;
				order[i] = ((long) Float.floatToIntBits(d) << 32) | (i & 0xFFFFFFFFL);
			} else {
				order[i] = count - 1 - i;
			}
		}
		if (cachedNeedsSort) {
			Arrays.sort(order, 0, count);
		}

		VertexConsumer vc = consumers.getBuffer(cachedLayer);
		for (int k = count - 1; k >= 0; k--) {
			int i = (int) order[k];
			int p = i * FLOATS_PER_QUAD;
			int c = i * 4;
			float x1 = pos[p], y1 = pos[p + 1], z1 = pos[p + 2];
			float x2 = pos[p + 3], y2 = pos[p + 4], z2 = pos[p + 5];
			float x3 = pos[p + 6], y3 = pos[p + 7], z3 = pos[p + 8];
			float x4 = pos[p + 9], y4 = pos[p + 10], z4 = pos[p + 11];
			float ax = x3 - x1, ay = y3 - y1, az = z3 - z1;
			float bx = x4 - x2, by = y4 - y2, bz = z4 - z2;
			float nx = ay * bz - az * by;
			float ny = az * bx - ax * bz;
			float nz = ax * by - ay * bx;
			boolean facing = (nx * -x1 + ny * -y1 + nz * -z1) >= 0;
			if (facing) {
				emit(vc, matrix, x1, y1, z1, col[c], x2, y2, z2, col[c + 1], x3, y3, z3, col[c + 2], x4, y4, z4, col[c + 3]);
			} else {
				emit(vc, matrix, x1, y1, z1, col[c], x4, y4, z4, col[c + 3], x3, y3, z3, col[c + 2], x2, y2, z2, col[c + 1]);
			}
		}
		count = 0;
	}

	private static void emit(VertexConsumer vc, Matrix4f m,
							 float x1, float y1, float z1, int c1,
							 float x2, float y2, float z2, int c2,
							 float x3, float y3, float z3, int c3,
							 float x4, float y4, float z4, int c4) {
		if (cachedTriangles) {
			vc.vertex(m, x1, y1, z1).color(c1);
			vc.vertex(m, x2, y2, z2).color(c2);
			vc.vertex(m, x3, y3, z3).color(c3);
			vc.vertex(m, x1, y1, z1).color(c1);
			vc.vertex(m, x3, y3, z3).color(c3);
			vc.vertex(m, x4, y4, z4).color(c4);
		} else {
			vc.vertex(m, x1, y1, z1).color(c1);
			vc.vertex(m, x2, y2, z2).color(c2);
			vc.vertex(m, x3, y3, z3).color(c3);
			vc.vertex(m, x4, y4, z4).color(c4);
		}
	}
}
