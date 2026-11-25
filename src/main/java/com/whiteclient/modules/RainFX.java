package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.SolidBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RainFX extends Module {
	public final BoolSetting streaks = add(new BoolSetting("Glow Streaks", true));
	public final BoolSetting ripples = add(new BoolSetting("Ripples", true));
	public final BoolSetting splashes = add(new BoolSetting("Splashes", true));
	public final NumberSetting amount = add(new NumberSetting("Amount", 1.0, 0.2, 3.0, 0.1, "x"));
	public final NumberSetting radius = add(new NumberSetting("Radius", 10, 4, 20, 1));
	public final ColorSetting color = add(new ColorSetting("Color", 0xCFE0FF));
	public final NumberSetting brightness = add(new NumberSetting("Brightness", 0.8, 0.1, 1.5, 0.05));
	public final BoolSetting mood = add(new BoolSetting("Rain Mood", true));
	public final ColorSetting moodColor = add(new ColorSetting("Mood Color", 0x1C2A44));
	public final NumberSetting moodStrength = add(new NumberSetting("Mood Strength", 0.22, 0.0, 0.6, 0.01));
	public final BoolSetting wetGround = add(new BoolSetting("Wet Ground", true));
	public final BoolSetting puddles = add(new BoolSetting("Puddles", true));
	public final NumberSetting shine = add(new NumberSetting("Shine", 1.0, 0.2, 2.0, 0.05, "x"));
	public final BoolSetting always = add(new BoolSetting("Preview Without Rain", false));

	private static final int MAX_STREAKS = 260, MAX_RIPPLES = 220, MAX_DROPS = 500;
	private final Random random = new Random();

	private final double[] sx = new double[MAX_STREAKS], sy = new double[MAX_STREAKS], sz = new double[MAX_STREAKS];
	private final double[] sGround = new double[MAX_STREAKS];
	private final float[] sSpeed = new float[MAX_STREAKS];
	private int streakCount;
	private final double[] rx = new double[MAX_RIPPLES], ry = new double[MAX_RIPPLES], rz = new double[MAX_RIPPLES];
	private final float[] rAge = new float[MAX_RIPPLES], rLife = new float[MAX_RIPPLES], rSize = new float[MAX_RIPPLES];
	private int rippleCount;
	private final double[] dx = new double[MAX_DROPS], dy = new double[MAX_DROPS], dz = new double[MAX_DROPS];
	private final float[] dvx = new float[MAX_DROPS], dvy = new float[MAX_DROPS], dvz = new float[MAX_DROPS];
	private final double[] dFloor = new double[MAX_DROPS];
	private final float[] dAge = new float[MAX_DROPS];
	private int dropCount;
	private float spawnAcc;
	private float rainLevel;

	private static final int WR = 12, WN = WR * 2 + 1, SUB = 4, PN = WN * SUB + 1;
	private final float[] wTop = new float[WN * WN];
	private final float[] wLight = new float[WN * WN];
	private final float[] wMat = new float[WN * WN];
	private final float[] cLight = new float[(WN + 1) * (WN + 1)];
	private final boolean[] wFlat = new boolean[WN * WN];
	private final float[] wPeak = new float[WN * WN];
	private final float[] wNoise = new float[(WN + 1) * (WN + 1)];
	private final float[] pNoise = new float[PN * PN];
	private int wcx = Integer.MIN_VALUE, wcz, wcy;
	private float wRefresh;
	private float wetness;
	private ClientLevel wetWorld;
	private final SolidBatch puddleBase = new SolidBatch();

	public RainFX() {
		super("Rain FX", "Glowing rain with ripples and splashes", Category.WORLD, true);
	}

	@Override
	protected void onDisable() {
		streakCount = rippleCount = dropCount = 0;
		wcx = Integer.MIN_VALUE;
		wetness = 0;
	}

	private float rainStrength(Minecraft mc, float td) {
		if (mc.level == null) {
			return 0;
		}
		float g = mc.level.getRainLevel(td);
		return always.isOn() ? Math.max(g, 1f) : g;
	}

	private double surface(ClientLevel world, double x, double z, double playerY) {
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		if (Math.abs(top - playerY) > 24) {
			return Double.NaN;
		}
		BlockPos pos = new BlockPos(bx, top, bz);
		if (!always.isOn() && !world.isRainingAt(pos)) {
			return Double.NaN;
		}
		double y = top;
		if (!world.getFluidState(pos.below()).isEmpty()) {
			y -= 0.11;
		}
		return y + 0.02;
	}

	private void ripple(double x, double y, double z, float sizeMul) {
		if (!ripples.isOn() && !splashes.isOn()) {
			return;
		}
		if (ripples.isOn()) {
			int i = rippleCount < MAX_RIPPLES ? rippleCount++ : random.nextInt(MAX_RIPPLES);
			rx[i] = x;
			ry[i] = y;
			rz[i] = z;
			rAge[i] = 0;
			rLife[i] = 0.55f + random.nextFloat() * 0.35f;
			rSize[i] = (0.22f + random.nextFloat() * 0.18f) * sizeMul;
		}
		if (splashes.isOn()) {
			int n = 2 + random.nextInt(3);
			for (int k = 0; k < n; k++) {
				int i = dropCount < MAX_DROPS ? dropCount++ : random.nextInt(MAX_DROPS);
				double ang = random.nextDouble() * Math.PI * 2;
				float hs = 0.4f + random.nextFloat() * 0.7f;
				dx[i] = x;
				dy[i] = y;
				dz[i] = z;
				dvx[i] = (float) Math.cos(ang) * hs;
				dvz[i] = (float) Math.sin(ang) * hs;
				dvy[i] = 1.3f + random.nextFloat() * 1.3f;
				dFloor[i] = y;
				dAge[i] = 0;
			}
		}
	}

	private void respawnStreak(int i, Vec3 p, ClientLevel world, float r, boolean anywhere) {
		double ang = random.nextDouble() * Math.PI * 2;
		double dist = Math.sqrt(random.nextDouble()) * r;
		sx[i] = p.x + Math.cos(ang) * dist;
		sz[i] = p.z + Math.sin(ang) * dist;
		double ground = surface(world, sx[i], sz[i], p.y);
		sGround[i] = ground;
		double topY = p.y + 9 + random.nextDouble() * 4;
		sy[i] = anywhere && !Double.isNaN(ground) ? ground + random.nextDouble() * (topY - ground) : topY;
		sSpeed[i] = 13f + random.nextFloat() * 5f;
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		Minecraft mc = ctx.client;
		ClientLevel world = mc.level;
		if (world == null || mc.player == null) {
			return;
		}
		float strength = rainStrength(mc, ctx.tickDelta);
		rainLevel = strength;
		float dt = Math.min(0.05f, ctx.frameDelta);
		Vec3 p = mc.player.getPosition(ctx.tickDelta);
		float r = radius.getFloat();
		GlowBatch g = ctx.glow;
		int rgb = color.rgb();
		float bright = brightness.getFloat();

		int wantStreaks = streaks.isOn() ? (int) Math.min(MAX_STREAKS, 110 * amount.get() * strength) : 0;
		while (streakCount < wantStreaks) {
			respawnStreak(streakCount++, p, world, r, true);
		}
		if (streakCount > wantStreaks) {
			streakCount = wantStreaks;
		}
		for (int i = 0; i < streakCount; i++) {
			sy[i] -= sSpeed[i] * dt;
			double ddx = sx[i] - p.x, ddz = sz[i] - p.z;
			boolean tooFar = ddx * ddx + ddz * ddz > r * r * 1.3;
			if (Double.isNaN(sGround[i]) || tooFar) {
				if (tooFar || sy[i] < p.y - 12) {
					respawnStreak(i, p, world, r, false);
				}
				if (Double.isNaN(sGround[i])) {
					continue;
				}
			}
			if (sy[i] <= sGround[i]) {
				ripple(sx[i], sGround[i], sz[i], 1f);
				respawnStreak(i, p, world, r, false);
				continue;
			}
			float len = 0.55f;
			float x = ctx.rx(sx[i]), y = ctx.ry(sy[i]), z = ctx.rz(sz[i]);
			float a = 0.45f * bright * strength;
			g.ribbon(x, y + len, z, x, y, z, 0.026f, Colors.argb(rgb, a));
		}

		spawnAcc += dt * 55 * amount.getFloat() * strength;
		while (spawnAcc >= 1f) {
			spawnAcc -= 1f;
			double ang = random.nextDouble() * Math.PI * 2;
			double dist = Math.sqrt(random.nextDouble()) * r;
			double x = p.x + Math.cos(ang) * dist, z = p.z + Math.sin(ang) * dist;
			double y = surface(world, x, z, p.y);
			if (!Double.isNaN(y)) {
				ripple(x, y, z, 0.85f);
			}
		}

		for (int i = 0; i < rippleCount; i++) {
			rAge[i] += dt;
			if (rAge[i] >= rLife[i]) {
				rippleCount--;
				copyRipple(rippleCount, i);
				i--;
				continue;
			}
			float u = rAge[i] / rLife[i];
			float ease = 1f - (1f - u) * (1f - u);
			float rad = rSize[i] * (0.15f + ease);
			float a = (1f - u) * 0.55f * bright;
			flatRing(g, ctx.rx(rx[i]), ctx.ry(ry[i]), ctx.rz(rz[i]), rad, 0.035f + 0.02f * u, rgb, a);
			if (u < 0.5f) {
				flatRing(g, ctx.rx(rx[i]), ctx.ry(ry[i]), ctx.rz(rz[i]), rad * 0.5f, 0.025f, rgb, a * (1f - u * 2f) * 0.7f);
			}
		}

		for (int i = 0; i < dropCount; i++) {
			dAge[i] += dt;
			dvy[i] -= 9.5f * dt;
			dx[i] += dvx[i] * dt;
			dy[i] += dvy[i] * dt;
			dz[i] += dvz[i] * dt;
			if (dy[i] < dFloor[i] || dAge[i] > 1.2f) {
				dropCount--;
				copyDrop(dropCount, i);
				i--;
				continue;
			}
			float x = ctx.rx(dx[i]), y = ctx.ry(dy[i]), z = ctx.rz(dz[i]);
			float a = 0.75f * bright * Math.max(0f, 1f - dAge[i] / 1.0f);
			g.ribbon(x, y, z, x - dvx[i] * 0.04f, y - dvy[i] * 0.04f, z - dvz[i] * 0.04f, 0.024f, Colors.argb(Colors.mix(rgb, 0xFFFFFF, 0.5f), a));
			g.disc(x, y, z, 0.045f, rgb, a * 0.55f, 6);
		}

		wetGround(ctx, world, p, strength, dt);
	}

	private void wetGround(WorldContext ctx, ClientLevel world, Vec3 p, float strength, float dt) {
		if (world != wetWorld) {
			wetWorld = world;
			wetness = strength;
			wcx = Integer.MIN_VALUE;
		}
		float target = always.isOn() ? 1f : strength;
		if (always.isOn()) {
			wetness = 1f;
		} else if (target > wetness) {
			wetness = Math.min(target, wetness + dt / 8f);
		} else {
			wetness = Math.max(target, wetness - dt / 45f);
		}
		if (!wetGround.isOn() || wetness < 0.01f) {
			return;
		}
		int bx = (int) Math.floor(p.x), by = (int) Math.floor(p.y), bz = (int) Math.floor(p.z);
		wRefresh -= dt;
		if (bx != wcx || bz != wcz || Math.abs(by - wcy) > 2 || wRefresh <= 0) {
			refreshWet(world, bx, by, bz);
			wRefresh = 0.6f;
		}

		GlowBatch g = ctx.glow;
		float sh = shine.getFloat();
		float night = dayLight(world);
		int base = Colors.mix(color.rgb(), 0x7D8CA8, 0.45f);
		int sky = Colors.mix(base, 0x46557F, 0.6f * (1f - night));
		float groundA = 0.36f * sh * wetness * (0.35f + 0.65f * night);
		float puddleA = 0.5f * sh * wetness * (0.45f + 0.55f * night);
		float warmW = 1f - 0.7f * night;
		float reach = Math.min(WR, radius.getFloat() + 2f);
		if (FpsBoost.lighter()) {
			reach = Math.min(reach, 9f);
		}
		boolean doPuddles = puddles.isOn();
		float th = 0.86f - 0.20f * wetness;
		float time = ctx.time;
		int quads = 0, cells = 0;

		for (int gz = 0; gz < WN; gz++) {
			for (int gx = 0; gx < WN; gx++) {
				int i = gz * WN + gx;
				float top = wTop[i];
				if (Float.isNaN(top)) {
					continue;
				}
				int x0 = wcx - WR + gx, z0 = wcz - WR + gz;
				double hx = x0 + 0.5 - p.x, hz = z0 + 0.5 - p.z;
				double hd = Math.sqrt(hx * hx + hz * hz);
				if (hd > reach + 0.8) {
					continue;
				}
				float mat = wMat[i];
				float ry = ctx.ry(top + 0.01f);
				float ax = ctx.rx(x0), az = ctx.rz(z0), bxx = ax + 1, bzz = az + 1;
				int k00 = gz * (WN + 1) + gx, k10 = k00 + 1, k01 = k00 + WN + 1, k11 = k01 + 1;
				float ga = groundA * mat;
				int c00 = wetCol(ax, ry, az, p, x0, z0, reach, wNoise[k00], ga, sky, cLight[k00] * warmW);
				int c10 = wetCol(bxx, ry, az, p, x0 + 1, z0, reach, wNoise[k10], ga, sky, cLight[k10] * warmW);
				int c11 = wetCol(bxx, ry, bzz, p, x0 + 1, z0 + 1, reach, wNoise[k11], ga, sky, cLight[k11] * warmW);
				int c01 = wetCol(ax, ry, bzz, p, x0, z0 + 1, reach, wNoise[k01], ga, sky, cLight[k01] * warmW);
				g.quad(ax, ry, az, c00, bxx, ry, az, c10, bxx, ry, bzz, c11, ax, ry, bzz, c01);
				quads++;

				float hsh = hash(x0, z0, 77);
				if (hsh < 0.45f) {
					float tw = (float) Math.sin(time * (1.5f + 3f * hash(x0, z0, 78)) + hash(x0, z0, 79) * 6.283f);
					tw = tw <= 0 ? 0 : tw * tw * tw * tw * tw * tw;
					if (tw > 0.03f) {
						float gx0 = x0 + 0.15f + 0.7f * hash(x0, z0, 80), gz0 = z0 + 0.15f + 0.7f * hash(x0, z0, 81);
						float vx = ctx.rx(gx0), vz = ctx.rz(gz0);
						float a = 0.9f * tw * fresnel(vx, ry, vz) * edgeFade(p, gx0, gz0, reach) * wetness * sh;
						if (a > 0.02f) {
							g.disc(vx, ry + 0.01f, vz, 0.035f + 0.02f * tw, 0xFFFFFF, a, 6);
						}
					}
				}

				if (doPuddles && wFlat[i] && wPeak[i] > th) {
					cells += puddle(ctx, g, gx, gz, x0, z0, top, p, reach, th, puddleA, sky, warmW);
				}
			}
		}
		debugWetQuads = quads;
		debugPuddleCells = cells;
		puddleBase.draw(ctx.consumers, ctx.matrices.last().pose());
	}

	public int debugWetQuads, debugPuddleCells;

	private int puddle(WorldContext ctx, GlowBatch g, int gx, int gz, int x0, int z0, float top, Vec3 p, float reach,
					   float th, float alpha, int sky, float warmW) {
		float yb = ctx.ry(top + 0.006f), ys = ctx.ry(top + 0.014f);
		float step = 1f / SUB;
		int k00 = gz * (WN + 1) + gx;
		float l00 = cLight[k00], l10 = cLight[k00 + 1], l01 = cLight[k00 + WN + 1], l11 = cLight[k00 + WN + 2];
		int cells = 0;
		boolean full = true;
		for (int sz = 0; sz <= SUB && full; sz++) {
			for (int sx = 0; sx <= SUB; sx++) {
				if (mask(pNoise[(gz * SUB + sz) * PN + gx * SUB + sx], th) < 0.999f) {
					full = false;
					break;
				}
			}
		}
		if (full) {
			float ax = ctx.rx(x0), az = ctx.rz(z0), bx = ax + 1, bz = az + 1;
			int dark = Colors.argb(0x04060A, 0.58f * wetness);
			puddleBase.quad(ax, yb, az, dark, ax, yb, bz, dark, bx, yb, bz, dark, bx, yb, az, dark, 0f, 1f, 0f);
			int c00 = puddleCol(ax, ys, az, p, x0, z0, reach, 1f, alpha, sky, l00 * warmW);
			int c10 = puddleCol(bx, ys, az, p, x0 + 1, z0, reach, 1f, alpha, sky, l10 * warmW);
			int c11 = puddleCol(bx, ys, bz, p, x0 + 1, z0 + 1, reach, 1f, alpha, sky, l11 * warmW);
			int c01 = puddleCol(ax, ys, bz, p, x0, z0 + 1, reach, 1f, alpha, sky, l01 * warmW);
			g.quad(ax, ys, az, c00, bx, ys, az, c10, bx, ys, bz, c11, ax, ys, bz, c01);
			return 1;
		}
		for (int sz = 0; sz < SUB; sz++) {
			for (int sx = 0; sx < SUB; sx++) {
				int px = gx * SUB + sx, pz = gz * SUB + sz;
				float m00 = mask(pNoise[pz * PN + px], th), m10 = mask(pNoise[pz * PN + px + 1], th);
				float m11 = mask(pNoise[(pz + 1) * PN + px + 1], th), m01 = mask(pNoise[(pz + 1) * PN + px], th);
				if (m00 + m10 + m11 + m01 <= 0.001f) {
					continue;
				}
				cells++;
				double wx = x0 + sx * step, wz = z0 + sz * step;
				float ax = ctx.rx(wx), az = ctx.rz(wz), bx = ax + step, bz = az + step;
				puddleBase.quad(ax, yb, az, Colors.argb(0x04060A, 0.58f * m00 * wetness),
						ax, yb, bz, Colors.argb(0x04060A, 0.58f * m01 * wetness),
						bx, yb, bz, Colors.argb(0x04060A, 0.58f * m11 * wetness),
						bx, yb, az, Colors.argb(0x04060A, 0.58f * m10 * wetness), 0f, 1f, 0f);
				float u0 = (float) sx / SUB, u1 = (float) (sx + 1) / SUB, v0 = (float) sz / SUB, v1 = (float) (sz + 1) / SUB;
				int c00 = puddleCol(ax, ys, az, p, wx, wz, reach, m00, alpha, sky, bilerp(l00, l10, l01, l11, u0, v0) * warmW);
				int c10 = puddleCol(bx, ys, az, p, wx + step, wz, reach, m10, alpha, sky, bilerp(l00, l10, l01, l11, u1, v0) * warmW);
				int c11 = puddleCol(bx, ys, bz, p, wx + step, wz + step, reach, m11, alpha, sky, bilerp(l00, l10, l01, l11, u1, v1) * warmW);
				int c01 = puddleCol(ax, ys, bz, p, wx, wz + step, reach, m01, alpha, sky, bilerp(l00, l10, l01, l11, u0, v1) * warmW);
				g.quad(ax, ys, az, c00, bx, ys, az, c10, bx, ys, bz, c11, ax, ys, bz, c01);
			}
		}
		return cells;
	}

	private static float bilerp(float a, float b, float c, float d, float u, float v) {
		float top = a + (b - a) * u, bot = c + (d - c) * u;
		return top + (bot - top) * v;
	}

	private static float mask(float n, float th) {
		float t = (n - th) / 0.03f;
		if (t <= 0) {
			return 0;
		}
		if (t >= 1) {
			return 1;
		}
		return t * t * (3 - 2 * t);
	}

	private static float fresnel(float vx, float vy, float vz) {
		float d = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4f;
		float cos = Math.min(1f, Math.abs(vy) / d);
		float k = 1f - cos;
		return 0.05f + 0.95f * k * k * (float) Math.sqrt(k);
	}

	private static float edgeFade(Vec3 p, double wx, double wz, float reach) {
		double hx = wx - p.x, hz = wz - p.z;
		double hd = Math.sqrt(hx * hx + hz * hz);
		return (float) Math.max(0, Math.min(1, (reach - hd) / 3.5));
	}

	private static int tint(int sky, float warm) {
		return warm > 0.02f ? Colors.mix(sky, 0xFFB466, Math.min(1f, warm * 0.9f)) : sky;
	}

	private static int wetCol(float vx, float vy, float vz, Vec3 p, double wx, double wz, float reach, float noise, float alpha,
							  int sky, float warm) {
		if (vy > 0) {
			return 0;
		}
		float a = alpha * (fresnel(vx, vy, vz) + warm * 0.35f) * edgeFade(p, wx, wz, reach) * (0.3f + 0.7f * noise);
		return Colors.argb(tint(sky, warm), Math.min(1f, a));
	}

	private static int puddleCol(float vx, float vy, float vz, Vec3 p, double wx, double wz, float reach, float m, float alpha,
								 int sky, float warm) {
		if (vy > 0 || m <= 0) {
			return 0;
		}
		float f = fresnel(vx, vy, vz);
		float rim = 4f * m * (1f - m);
		float a = alpha * (m * (0.25f + 0.6f * f + warm * 0.5f) + rim * 0.4f) * edgeFade(p, wx, wz, reach);
		return Colors.argb(tint(sky, warm), Math.min(1f, a));
	}

	private static float dayLight(ClientLevel world) {
		long tod = Math.floorMod(world.getDayTime(), 24000L);
		double ang = (tod - 6000) / 24000.0 * Math.PI * 2;
		return (float) Math.max(0, Math.min(1, 0.5 + Math.cos(ang) * 0.9));
	}

	private void refreshWet(ClientLevel world, int bx, int by, int bz) {
		wcx = bx;
		wcz = bz;
		wcy = by;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		boolean any = always.isOn();
		for (int gz = 0; gz < WN; gz++) {
			for (int gx = 0; gx < WN; gx++) {
				int x = bx - WR + gx, z = bz - WR + gz;
				wTop[gz * WN + gx] = wetTop(world, m, x, z, by, any);
			}
		}
		for (int gz = 0; gz < WN; gz++) {
			for (int gx = 0; gx < WN; gx++) {
				int i = gz * WN + gx;
				float t = wTop[i];
				wLight[i] = 0;
				wFlat[i] = false;
				if (Float.isNaN(t)) {
					continue;
				}
				m.set(bx - WR + gx, (int) Math.ceil(t - 0.01f), bz - WR + gz);
				wLight[i] = Math.max(0, world.getBrightness(LightLayer.BLOCK, m) - 5) / 10f;
				m.set(bx - WR + gx, (int) Math.ceil(t - 0.01f) - 1, bz - WR + gz);
				BlockState st = world.getBlockState(m);
				wMat[i] = st.is(BlockTags.DIRT) ? 0.7f : st.is(BlockTags.SAND) ? 0.85f : 1.15f;
				wFlat[i] = gx > 0 && gz > 0 && gx < WN - 1 && gz < WN - 1
						&& same(t, wTop[i - 1]) && same(t, wTop[i + 1]) && same(t, wTop[i - WN]) && same(t, wTop[i + WN]);
			}
		}
		for (int gz = 0; gz <= WN; gz++) {
			for (int gx = 0; gx <= WN; gx++) {
				float sum = 0;
				int cnt = 0;
				for (int dz = -1; dz <= 0; dz++) {
					for (int dx = -1; dx <= 0; dx++) {
						int cx = gx + dx, cz = gz + dz;
						if (cx >= 0 && cz >= 0 && cx < WN && cz < WN && !Float.isNaN(wTop[cz * WN + cx])) {
							sum += wLight[cz * WN + cx];
							cnt++;
						}
					}
				}
				cLight[gz * (WN + 1) + gx] = cnt == 0 ? 0 : sum / cnt;
			}
		}
		for (int gz = 0; gz <= WN; gz++) {
			for (int gx = 0; gx <= WN; gx++) {
				int x = bx - WR + gx, z = bz - WR + gz;
				wNoise[gz * (WN + 1) + gx] = 0.6f * valueNoise(x / 4.0, z / 4.0, 11) + 0.4f * valueNoise(x / 1.7, z / 1.7, 23);
			}
		}
		for (int pz = 0; pz < PN; pz++) {
			for (int px = 0; px < PN; px++) {
				double x = bx - WR + px / (double) SUB, z = bz - WR + pz / (double) SUB;
				pNoise[pz * PN + px] = 0.72f * valueNoise(x / 5.5, z / 5.5, 41) + 0.28f * valueNoise(x / 1.9, z / 1.9, 57);
			}
		}
		for (int gz = 0; gz < WN; gz++) {
			for (int gx = 0; gx < WN; gx++) {
				float peak = 0;
				for (int sz = 0; sz <= SUB; sz++) {
					for (int sx = 0; sx <= SUB; sx++) {
						peak = Math.max(peak, pNoise[(gz * SUB + sz) * PN + gx * SUB + sx]);
					}
				}
				wPeak[gz * WN + gx] = peak;
			}
		}
		for (int gz = 0; gz < WN; gz++) {
			for (int gx = 0; gx < WN; gx++) {
				int i = gz * WN + gx;
				if (wFlat[i]) {
					continue;
				}
				for (int sz = 0; sz <= SUB; sz++) {
					for (int sx = 0; sx <= SUB; sx++) {
						pNoise[(gz * SUB + sz) * PN + gx * SUB + sx] = 0;
					}
				}
			}
		}
	}

	private static boolean same(float a, float b) {
		return !Float.isNaN(b) && Math.abs(a - b) < 0.01f;
	}

	private static float wetTop(ClientLevel world, BlockPos.MutableBlockPos m, int x, int z, int playerY, boolean any) {
		int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		if (top - playerY > 14 || playerY - top > 18) {
			return Float.NaN;
		}
		m.set(x, top - 1, z);
		BlockState st = world.getBlockState(m);
		if (st.isAir() || !st.getFluidState().isEmpty() || st.is(BlockTags.LEAVES)) {
			return Float.NaN;
		}
		VoxelShape shape = st.getCollisionShape(world, m);
		if (shape.isEmpty()) {
			return Float.NaN;
		}
		double h = shape.max(Direction.Axis.Y);
		if (h > 1.0 || h < 0.05) {
			return Float.NaN;
		}
		if (!any) {
			m.set(x, top, z);
			if (world.precipitationAt(m) != Biome.Precipitation.RAIN) {
				return Float.NaN;
			}
		}
		return (float) (top - 1 + h);
	}

	private static float hash(int x, int z, int seed) {
		int h = x * 374761393 + z * 668265263 + seed * 982451653;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	private static float valueNoise(double x, double z, int seed) {
		int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
		float fx = (float) (x - x0), fz = (float) (z - z0);
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		float a = hash(x0, z0, seed), b = hash(x0 + 1, z0, seed), c = hash(x0, z0 + 1, seed), d = hash(x0 + 1, z0 + 1, seed);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
	}

	private void copyRipple(int from, int to) {
		rx[to] = rx[from];
		ry[to] = ry[from];
		rz[to] = rz[from];
		rAge[to] = rAge[from];
		rLife[to] = rLife[from];
		rSize[to] = rSize[from];
	}

	private void copyDrop(int from, int to) {
		dx[to] = dx[from];
		dy[to] = dy[from];
		dz[to] = dz[from];
		dvx[to] = dvx[from];
		dvy[to] = dvy[from];
		dvz[to] = dvz[from];
		dFloor[to] = dFloor[from];
		dAge[to] = dAge[from];
	}

	private static void flatRing(GlowBatch g, float cx, float cy, float cz, float radius, float width, int rgb, float alpha) {
		if (alpha <= 0.004f) {
			return;
		}
		int seg = 20;
		int core = Colors.argb(rgb, alpha);
		int edge = rgb & 0xFFFFFF;
		float inner = Math.max(0, radius - width), outer = radius + width;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			g.quad(cx + c0 * outer, cy, cz + s0 * outer, edge, cx + c1 * outer, cy, cz + s1 * outer, edge,
					cx + c1 * radius, cy, cz + s1 * radius, core, cx + c0 * radius, cy, cz + s0 * radius, core);
			g.quad(cx + c0 * radius, cy, cz + s0 * radius, core, cx + c1 * radius, cy, cz + s1 * radius, core,
					cx + c1 * inner, cy, cz + s1 * inner, edge, cx + c0 * inner, cy, cz + s0 * inner, edge);
		}
	}

	@Override
	public void renderHud(GuiGraphics ctx, float tickDelta) {
		if (!mood.isOn() || rainLevel < 0.01f) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		int w = ctx.guiWidth(), h = ctx.guiHeight();
		float a = moodStrength.getFloat() * rainLevel;
		int c = moodColor.rgb();
		ctx.fill(0, 0, w, h, Colors.argb(c, a * 0.6f));
		ctx.fillGradient(0, 0, w, h / 3, Colors.argb(c, a), Colors.argb(c, 0));
		ctx.fillGradient(0, h * 2 / 3, w, h, Colors.argb(c, 0), Colors.argb(c, a * 0.9f));
	}
}
