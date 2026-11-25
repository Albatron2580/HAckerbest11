package com.whiteclient.render;

import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public final class OrganicMesh {
	private final SolidBatch batch = new SolidBatch();
	private float light = 1f, glowAmt = 0f;
	private float gr = 1f, gg = 1f, gb = 1f;
	private static final float LX = 0.32f, LY = 0.86f, LZ = 0.4f;

	public void setup(float light, int glowRgb, float glowAmt) {
		this.light = light;
		this.glowAmt = glowAmt;
		gr = ((glowRgb >> 16) & 0xFF) / 255f;
		gg = ((glowRgb >> 8) & 0xFF) / 255f;
		gb = (glowRgb & 0xFF) / 255f;
	}

	private int shade(int rgb, int alpha, float nx, float ny, float nz, float px, float py, float pz, float gloss) {
		float vl = Math.max(1.0e-4f, (float) Math.sqrt(px * px + py * py + pz * pz));
		float vx = -px / vl, vy = -py / vl, vz = -pz / vl;
		float ndl = nx * LX + ny * LY + nz * LZ;
		float wrap = Math.max(0f, (ndl + 0.45f) / 1.45f);
		float diff = (0.4f + 0.6f * wrap) * (0.82f + 0.18f * ny);
		float ndv = Math.max(0f, nx * vx + ny * vy + nz * vz);
		float rim = (float) Math.pow(1f - ndv, 2.4);
		float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
		float sheen = rim * 0.14f;
		r = r * diff * light + (sheen * light + rim * glowAmt * gr * 0.6f);
		g = g * diff * light + (sheen * light + rim * glowAmt * gg * 0.6f);
		b = b * diff * light + (sheen * light + rim * glowAmt * gb * 0.6f);
		if (gloss > 0) {
			float rx = 2 * ndv * nx - vx, ry = 2 * ndv * ny - vy, rz = 2 * ndv * nz - vz;
			float rl = Math.max(0f, rx * LX + ry * LY + rz * LZ);
			float spec = gloss * (float) Math.pow(rl, 24) * Math.max(0.35f, light);
			r += spec;
			g += spec;
			b += spec;
		}
		int ir = (int) Math.min(255f, r * 255f), ig = (int) Math.min(255f, g * 255f), ib = (int) Math.min(255f, b * 255f);
		return (alpha << 24) | (ir << 16) | (ig << 8) | ib;
	}

	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
		int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
		int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
		return (r << 16) | (g << 8) | bl;
	}

	private static float smooth(float e0, float e1, float x) {
		float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
		return t * t * (3 - 2 * t);
	}

	public void ellipsoid(V3 c, V3 ax, V3 ay, V3 az, float rx, float ry, float rz, int rgb) {
		ellipsoid(c, ax, ay, az, rx, ry, rz, rgb, rgb, 0f, 0xFF);
	}

	public void ellipsoid(V3 c, V3 ax, V3 ay, V3 az, float rx, float ry, float rz, int top, int bottom, float gloss, int alpha) {
		float size = Math.max(rx, Math.max(ry, rz)) / distance(c);
		int lon = size > 0.12f ? 14 : size > 0.05f ? 12 : size > 0.022f ? 9 : 6;
		int lat = Math.max(3, lon / 2);
		V3[][] pos = new V3[lat + 1][lon];
		V3[][] nor = new V3[lat + 1][lon];
		int[][] col = new int[lat + 1][lon];
		for (int i = 0; i <= lat; i++) {
			double th = -Math.PI / 2 + Math.PI * i / lat;
			float ct = (float) Math.cos(th), st = (float) Math.sin(th);
			for (int j = 0; j < lon; j++) {
				double ph = Math.PI * 2 * j / lon;
				float cp = (float) Math.cos(ph), sp = (float) Math.sin(ph);
				float lx = ct * cp, ly = st, lz = ct * sp;
				V3 p = c.add(ax, lx * rx).add(ay, ly * ry).add(az, lz * rz);
				V3 n = ax.mul(lx / rx).add(ay, ly / ry).add(az, lz / rz).normalize();
				pos[i][j] = p;
				nor[i][j] = n;
				int base = top == bottom ? top : mix(bottom, top, smooth(-0.45f, 0.2f, st));
				col[i][j] = shade(base, alpha, n.x(), n.y(), n.z(), p.x(), p.y(), p.z(), gloss);
			}
		}
		for (int i = 0; i < lat; i++) {
			for (int j = 0; j < lon; j++) {
				int j2 = (j + 1) % lon;
				V3 a = pos[i][j], b = pos[i][j2], cc = pos[i + 1][j2], d = pos[i + 1][j];
				V3 n = nor[i][j].add(nor[i][j2]).add(nor[i + 1][j2]).add(nor[i + 1][j]);
				batch.quad(a.x(), a.y(), a.z(), col[i][j], b.x(), b.y(), b.z(), col[i][j2], cc.x(), cc.y(), cc.z(), col[i + 1][j2],
						d.x(), d.y(), d.z(), col[i + 1][j], n.x(), n.y(), n.z());
			}
		}
	}

	private static float distance(V3 p) {
		return (float) Math.sqrt(p.x() * p.x() + p.y() * p.y() + p.z() * p.z()) + 0.05f;
	}

	public void sphere(V3 c, float r, int rgb) {
		ellipsoid(c, new V3(1, 0, 0), V3.UP, new V3(0, 0, 1), r, r, r, rgb);
	}

	public void glossySphere(V3 c, float r, int rgb, float gloss) {
		ellipsoid(c, new V3(1, 0, 0), V3.UP, new V3(0, 0, 1), r, r, r, rgb, rgb, gloss, 0xFF);
	}

	public void tube(V3 a, V3 b, float ra, float rb, int rgb) {
		tube(a, b, ra, rb, rgb, rgb);
	}

	public void tube(V3 a, V3 b, float ra, float rb, int rgbA, int rgbB) {
		V3 d = b.sub(a);
		float len = d.length();
		if (len < 1.0e-5f) {
			return;
		}
		d = d.mul(1f / len);
		V3 ref = Math.abs(d.y()) < 0.9f ? V3.UP : new V3(1, 0, 0);
		V3 e1 = d.cross(ref).normalize();
		V3 e2 = d.cross(e1).normalize();
		int sides = Math.max(ra, rb) / distance(a) > 0.03f ? 10 : 6;
		V3[] pa = new V3[sides], pb = new V3[sides], na = new V3[sides];
		for (int i = 0; i < sides; i++) {
			double an = Math.PI * 2 * i / sides;
			V3 radial = e1.mul((float) Math.cos(an)).add(e2, (float) Math.sin(an));
			pa[i] = a.add(radial, ra);
			pb[i] = b.add(radial, rb);
			na[i] = radial.add(d, (ra - rb) / len).normalize();
		}
		for (int i = 0; i < sides; i++) {
			int j = (i + 1) % sides;
			V3 n = na[i].add(na[j]);
			batch.quad(pa[i].x(), pa[i].y(), pa[i].z(), shade(rgbA, 0xFF, na[i].x(), na[i].y(), na[i].z(), pa[i].x(), pa[i].y(), pa[i].z(), 0),
					pa[j].x(), pa[j].y(), pa[j].z(), shade(rgbA, 0xFF, na[j].x(), na[j].y(), na[j].z(), pa[j].x(), pa[j].y(), pa[j].z(), 0),
					pb[j].x(), pb[j].y(), pb[j].z(), shade(rgbB, 0xFF, na[j].x(), na[j].y(), na[j].z(), pb[j].x(), pb[j].y(), pb[j].z(), 0),
					pb[i].x(), pb[i].y(), pb[i].z(), shade(rgbB, 0xFF, na[i].x(), na[i].y(), na[i].z(), pb[i].x(), pb[i].y(), pb[i].z(), 0),
					n.x(), n.y(), n.z());
		}
	}

	public void cone(V3 base, V3 tip, V3 w, float rw, float rd, int rgb, int innerRgb, V3 inner) {
		V3 d = tip.sub(base).normalize();
		V3 e1 = w.sub(d.mul(w.dot(d))).normalize();
		V3 e2 = d.cross(e1).normalize();
		int sides = 8;
		V3[] p = new V3[sides];
		V3[] n = new V3[sides];
		for (int i = 0; i < sides; i++) {
			double an = Math.PI * 2 * i / sides;
			float c = (float) Math.cos(an), s = (float) Math.sin(an);
			p[i] = base.add(e1, c * rw).add(e2, s * rd);
			n[i] = e1.mul(c / rw).add(e2, s / rd).normalize();
		}
		for (int i = 0; i < sides; i++) {
			int j = (i + 1) % sides;
			V3 fn = n[i].add(n[j]).normalize();
			int base1 = inner != null && fn.dot(inner) > 0.35f ? innerRgb : rgb;
			int ci = shade(base1, 0xFF, n[i].x(), n[i].y(), n[i].z(), p[i].x(), p[i].y(), p[i].z(), 0);
			int cj = shade(base1, 0xFF, n[j].x(), n[j].y(), n[j].z(), p[j].x(), p[j].y(), p[j].z(), 0);
			int ct = shade(base1, 0xFF, fn.x(), fn.y(), fn.z(), tip.x(), tip.y(), tip.z(), 0);
			batch.quad(p[i].x(), p[i].y(), p[i].z(), ci, p[j].x(), p[j].y(), p[j].z(), cj, tip.x(), tip.y(), tip.z(), ct, tip.x(), tip.y(), tip.z(), ct,
					fn.x(), fn.y(), fn.z());
		}
	}

	public void membrane(V3[] pts, int rootRgb, int edgeRgb, float alphaRoot, float alphaEdge) {
		V3 c = pts[0];
		V3 n = pts[1].sub(c).cross(pts[pts.length - 1].sub(c)).normalize();
		for (int i = 1; i < pts.length - 1; i++) {
			V3 a = pts[i], b = pts[i + 1];
			int cr = flat(rootRgb, alphaRoot, n, c), ca = flat(edgeRgb, alphaEdge, n, a), cb = flat(edgeRgb, alphaEdge, n, b);
			batch.quad(c.x(), c.y(), c.z(), cr, a.x(), a.y(), a.z(), ca, b.x(), b.y(), b.z(), cb, b.x(), b.y(), b.z(), cb, 0, 0, 0);
		}
	}

	private int flat(int rgb, float alpha, V3 n, V3 p) {
		float vl = Math.max(1.0e-4f, p.length());
		float d = Math.abs(n.dot(p.mul(-1f / vl)));
		float ndl = Math.abs(n.x() * LX + n.y() * LY + n.z() * LZ);
		float k = (0.55f + 0.45f * ndl) * light;
		float glow = (1f - d) * glowAmt * 0.4f;
		int r = (int) Math.min(255f, ((rgb >> 16) & 0xFF) * k + glow * gr * 255f);
		int g = (int) Math.min(255f, ((rgb >> 8) & 0xFF) * k + glow * gg * 255f);
		int b = (int) Math.min(255f, (rgb & 0xFF) * k + glow * gb * 255f);
		int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public void shadow(V3 c, V3 fwd, float halfLen, float halfW, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		V3 r = fwd.cross(V3.UP).normalize();
		int seg = 18;
		int ci = ((int) (alpha * 150) << 24), oe = 0;
		V3 prev = null;
		for (int i = 0; i <= seg; i++) {
			double an = Math.PI * 2 * i / seg;
			V3 p = c.add(fwd, (float) Math.cos(an) * halfLen).add(r, (float) Math.sin(an) * halfW);
			if (prev != null) {
				batch.quad(c.x(), c.y(), c.z(), ci, prev.x(), prev.y(), prev.z(), oe, p.x(), p.y(), p.z(), oe, c.x(), c.y(), c.z(), ci, 0, 1, 0);
			}
			prev = p;
		}
	}

	public void draw(MultiBufferSource consumers, Matrix4f matrix) {
		batch.draw(consumers, matrix);
	}
}
