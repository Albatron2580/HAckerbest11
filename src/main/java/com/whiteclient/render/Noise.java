package com.whiteclient.render;

/** Compact 3D simplex noise (based on Stefan Gustavson's public-domain reference). Output ~[-1, 1]. */
public final class Noise {
	private static final int[][] GRAD3 = {
			{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
			{1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
			{0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};
	private static final short[] PERM = new short[512];
	private static final short[] PERM_MOD12 = new short[512];

	static {
		short[] p = new short[256];
		for (short i = 0; i < 256; i++) {
			p[i] = i;
		}
		long seed = 0x5EEDBEEFL;
		for (int i = 255; i > 0; i--) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int j = (int) ((seed >>> 33) % (i + 1));
			short t = p[i];
			p[i] = p[j];
			p[j] = t;
		}
		for (int i = 0; i < 512; i++) {
			PERM[i] = p[i & 255];
			PERM_MOD12[i] = (short) (PERM[i] % 12);
		}
	}

	private static final double F3 = 1.0 / 3.0;
	private static final double G3 = 1.0 / 6.0;

	private Noise() {
	}

	private static int fastfloor(double x) {
		int xi = (int) x;
		return x < xi ? xi - 1 : xi;
	}

	private static double dot(int[] g, double x, double y, double z) {
		return g[0] * x + g[1] * y + g[2] * z;
	}

	public static double noise(double xin, double yin, double zin) {
		double n0, n1, n2, n3;
		double s = (xin + yin + zin) * F3;
		int i = fastfloor(xin + s);
		int j = fastfloor(yin + s);
		int k = fastfloor(zin + s);
		double t = (i + j + k) * G3;
		double x0 = xin - (i - t);
		double y0 = yin - (j - t);
		double z0 = zin - (k - t);
		int i1, j1, k1, i2, j2, k2;
		if (x0 >= y0) {
			if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
			else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
			else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
		} else {
			if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
			else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
			else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
		}
		double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
		double x2 = x0 - i2 + 2.0 * G3, y2 = y0 - j2 + 2.0 * G3, z2 = z0 - k2 + 2.0 * G3;
		double x3 = x0 - 1.0 + 3.0 * G3, y3 = y0 - 1.0 + 3.0 * G3, z3 = z0 - 1.0 + 3.0 * G3;
		int ii = i & 255, jj = j & 255, kk = k & 255;
		int gi0 = PERM_MOD12[ii + PERM[jj + PERM[kk]]];
		int gi1 = PERM_MOD12[ii + i1 + PERM[jj + j1 + PERM[kk + k1]]];
		int gi2 = PERM_MOD12[ii + i2 + PERM[jj + j2 + PERM[kk + k2]]];
		int gi3 = PERM_MOD12[ii + 1 + PERM[jj + 1 + PERM[kk + 1]]];
		double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
		if (t0 < 0) n0 = 0.0; else { t0 *= t0; n0 = t0 * t0 * dot(GRAD3[gi0], x0, y0, z0); }
		double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
		if (t1 < 0) n1 = 0.0; else { t1 *= t1; n1 = t1 * t1 * dot(GRAD3[gi1], x1, y1, z1); }
		double t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
		if (t2 < 0) n2 = 0.0; else { t2 *= t2; n2 = t2 * t2 * dot(GRAD3[gi2], x2, y2, z2); }
		double t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
		if (t3 < 0) n3 = 0.0; else { t3 *= t3; n3 = t3 * t3 * dot(GRAD3[gi3], x3, y3, z3); }
		return 32.0 * (n0 + n1 + n2 + n3);
	}
}
