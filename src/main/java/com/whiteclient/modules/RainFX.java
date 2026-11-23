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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.Random;

/** Prettier rain: glowing streaks, ripples on the ground and little splash droplets. */
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

	// streaks
	private final double[] sx = new double[MAX_STREAKS], sy = new double[MAX_STREAKS], sz = new double[MAX_STREAKS];
	private final double[] sGround = new double[MAX_STREAKS];
	private final float[] sSpeed = new float[MAX_STREAKS];
	private int streakCount;
	// ripples
	private final double[] rx = new double[MAX_RIPPLES], ry = new double[MAX_RIPPLES], rz = new double[MAX_RIPPLES];
	private final float[] rAge = new float[MAX_RIPPLES], rLife = new float[MAX_RIPPLES], rSize = new float[MAX_RIPPLES];
	private int rippleCount;
	// droplets
	private final double[] dx = new double[MAX_DROPS], dy = new double[MAX_DROPS], dz = new double[MAX_DROPS];
	private final float[] dvx = new float[MAX_DROPS], dvy = new float[MAX_DROPS], dvz = new float[MAX_DROPS];
	private final double[] dFloor = new double[MAX_DROPS];
	private final float[] dAge = new float[MAX_DROPS];
	private int dropCount;
	private float spawnAcc;
	private float rainLevel;

	// ---- wet ground: a cached grid of the rain-exposed top surfaces around the player
	private static final int WR = 12, WN = WR * 2 + 1, SUB = 4, PN = WN * SUB + 1;
	private final float[] wTop = new float[WN * WN];
	private final float[] wLight = new float[WN * WN];
	private final boolean[] wFlat = new boolean[WN * WN];
	private final float[] wPeak = new float[WN * WN];
	/** Streak noise on whole-block corners and puddle noise on a finer grid (both world-anchored). */
	private final float[] wNoise = new float[(WN + 1) * (WN + 1)];
	private final float[] pNoise = new float[PN * PN];
	private int wcx = Integer.MIN_VALUE, wcz, wcy;
	private float wRefresh;
	/** Builds up while it rains and slowly dries afterwards, so puddles grow over time. */
	private float wetness;
	private ClientWorld wetWorld;
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

	private float rainStrength(MinecraftClient mc, float td) {
		if (mc.world == null) {
			return 0;
		}
		float g = mc.world.getRainGradient(td);
		return always.isOn() ? Math.max(g, 1f) : g;
	}

	/** Surface height at x/z or NaN when it isn't raining there. */
	private double surface(ClientWorld world, double x, double z, double playerY) {
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
		if (Math.abs(top - playerY) > 24) {
			return Double.NaN;
		}
		BlockPos pos = new BlockPos(bx, top, bz);
		if (!always.isOn() && !world.hasRain(pos)) {
			return Double.NaN;
		}
		double y = top;
		if (!world.getFluidState(pos.down()).isEmpty()) {
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

	private void respawnStreak(int i, Vec3d p, ClientWorld world, float r, boolean anywhere) {
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
		MinecraftClient mc = ctx.client;
		ClientWorld world = mc.world;
		if (world == null || mc.player == null) {
			return;
		}
		float strength = rainStrength(mc, ctx.tickDelta);
		rainLevel = strength;
		float dt = Math.min(0.05f, ctx.frameDelta);
		Vec3d p = mc.player.getLerpedPos(ctx.tickDelta);
		float r = radius.getFloat();
		GlowBatch g = ctx.glow;
		int rgb = color.rgb();
		float bright = brightness.getFloat();

		// ---- streaks
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

		// ---- extra ripples (rain hitting the ground everywhere, not only under the streaks)
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

		// ---- draw ripples: flat rings lying on the ground
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

		// ---- droplets
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

	// =====================================================================================
	// Wet ground: rain-exposed surfaces get a view-angle sheen (strong at grazing angles,
	// like real wet stone) and flat spots slowly fill with dark, sky-reflecting puddles.
	// =====================================================================================

	private void wetGround(WorldContext ctx, ClientWorld world, Vec3d p, float strength, float dt) {
		if (world != wetWorld) {
			wetWorld = world;
			wetness = strength; // joining a world where it already rains: already wet
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
		int base = Colors.mix(color.rgb(), 0xFFFFFF, 0.35f);
		int sky = Colors.mix(base, 0x6F86C8, 0.55f * (1f - night));
		float groundA = 0.26f * sh * wetness * (0.3f + 0.7f * night);
		float puddleA = 0.62f * sh * wetness * (0.35f + 0.65f * night);
		float reach = Math.min(WR, radius.getFloat() + 2f);
		if (FpsBoost.lighter()) {
			reach = Math.min(reach, 9f);
		}
		boolean doPuddles = puddles.isOn();
		// puddles grow the longer it rains
		float th = 0.80f - 0.20f * wetness;

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
				float light = wLight[i];
				int tint = light > 0.02f ? Colors.mix(sky, 0xFFB466, Math.min(1f, light * 0.85f)) : sky;
				float lightBoost = 1f + light * 1.2f;
				float y = top + 0.006f;
				float ry = ctx.ry(y);
				float ax = ctx.rx(x0), az = ctx.rz(z0), bxx = ax + 1, bzz = az + 1;
				int c00 = wetCol(ax, ry, az, p, x0, z0, reach, wNoise[gz * (WN + 1) + gx], groundA * lightBoost, tint);
				int c10 = wetCol(bxx, ry, az, p, x0 + 1, z0, reach, wNoise[gz * (WN + 1) + gx + 1], groundA * lightBoost, tint);
				int c11 = wetCol(bxx, ry, bzz, p, x0 + 1, z0 + 1, reach, wNoise[(gz + 1) * (WN + 1) + gx + 1], groundA * lightBoost, tint);
				int c01 = wetCol(ax, ry, bzz, p, x0, z0 + 1, reach, wNoise[(gz + 1) * (WN + 1) + gx], groundA * lightBoost, tint);
				g.quad(ax, ry, az, c00, bxx, ry, az, c10, bxx, ry, bzz, c11, ax, ry, bzz, c01);

				if (doPuddles && wFlat[i] && wPeak[i] > th) {
					puddle(ctx, g, gx, gz, x0, z0, top, p, reach, th, puddleA * lightBoost, tint);
				}
			}
		}
		puddleBase.draw(ctx.consumers, ctx.matrices.peek().getPositionMatrix());
	}

	/** A whole block of puddle, split into small cells so the puddle edge is a soft, round blob. */
	private void puddle(WorldContext ctx, GlowBatch g, int gx, int gz, int x0, int z0, float top, Vec3d p, float reach,
						float th, float alpha, int tint) {
		float yb = ctx.ry(top + 0.003f), ys = ctx.ry(top + 0.008f);
		float step = 1f / SUB;
		for (int sz = 0; sz < SUB; sz++) {
			for (int sx = 0; sx < SUB; sx++) {
				int px = gx * SUB + sx, pz = gz * SUB + sz;
				float m00 = mask(pNoise[pz * PN + px], th), m10 = mask(pNoise[pz * PN + px + 1], th);
				float m11 = mask(pNoise[(pz + 1) * PN + px + 1], th), m01 = mask(pNoise[(pz + 1) * PN + px], th);
				if (m00 + m10 + m11 + m01 <= 0.001f) {
					continue;
				}
				double wx = x0 + sx * step, wz = z0 + sz * step;
				float ax = ctx.rx(wx), az = ctx.rz(wz), bx = ax + step, bz = az + step;
				// darker water body (wet ground soaks up light) ...
				puddleBase.quad(ax, yb, az, Colors.argb(0x05070C, 0.42f * m00 * wetness),
						ax, yb, bz, Colors.argb(0x05070C, 0.42f * m01 * wetness),
						bx, yb, bz, Colors.argb(0x05070C, 0.42f * m11 * wetness),
						bx, yb, az, Colors.argb(0x05070C, 0.42f * m10 * wetness), 0f, 1f, 0f);
				// ... with the sky mirrored on top of it
				int c00 = puddleCol(ax, ys, az, p, wx, wz, reach, m00, alpha, tint);
				int c10 = puddleCol(bx, ys, az, p, wx + step, wz, reach, m10, alpha, tint);
				int c11 = puddleCol(bx, ys, bz, p, wx + step, wz + step, reach, m11, alpha, tint);
				int c01 = puddleCol(ax, ys, bz, p, wx, wz + step, reach, m01, alpha, tint);
				g.quad(ax, ys, az, c00, bx, ys, az, c10, bx, ys, bz, c11, ax, ys, bz, c01);
			}
		}
	}

	private static float mask(float n, float th) {
		float t = (n - th) / 0.07f;
		if (t <= 0) {
			return 0;
		}
		if (t >= 1) {
			return 1;
		}
		return t * t * (3 - 2 * t);
	}

	/** Fresnel: almost nothing straight down, a strong sheen when you look along the ground. */
	private static float fresnel(float vx, float vy, float vz) {
		float d = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4f;
		float cos = Math.min(1f, Math.abs(vy) / d);
		float k = 1f - cos;
		return 0.05f + 0.95f * k * k * k;
	}

	private static float edgeFade(Vec3d p, double wx, double wz, float reach) {
		double hx = wx - p.x, hz = wz - p.z;
		double hd = Math.sqrt(hx * hx + hz * hz);
		return (float) Math.max(0, Math.min(1, (reach - hd) / 3.5));
	}

	private static int wetCol(float vx, float vy, float vz, Vec3d p, double wx, double wz, float reach, float noise, float alpha, int tint) {
		if (vy > 0) {
			return 0;
		}
		float a = alpha * fresnel(vx, vy, vz) * edgeFade(p, wx, wz, reach) * (0.35f + 0.65f * noise);
		return Colors.argb(tint, Math.min(1f, a));
	}

	private static int puddleCol(float vx, float vy, float vz, Vec3d p, double wx, double wz, float reach, float m, float alpha, int tint) {
		if (vy > 0 || m <= 0) {
			return tint & 0xFFFFFF;
		}
		float a = alpha * m * (0.16f + 0.84f * fresnel(vx, vy, vz)) * edgeFade(p, wx, wz, reach);
		return Colors.argb(tint, Math.min(1f, a));
	}

	/** 1 in daylight, 0 at midnight (moonlit puddles still shine a little). */
	private static float dayLight(ClientWorld world) {
		long tod = Math.floorMod(world.getTimeOfDay(), 24000L);
		double ang = (tod - 6000) / 24000.0 * Math.PI * 2;
		return (float) Math.max(0, Math.min(1, 0.5 + Math.cos(ang) * 0.9));
	}

	private void refreshWet(ClientWorld world, int bx, int by, int bz) {
		wcx = bx;
		wcz = bz;
		wcy = by;
		BlockPos.Mutable m = new BlockPos.Mutable();
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
				wLight[i] = Math.max(0, world.getLightLevel(LightType.BLOCK, m) - 6) / 9f;
				wFlat[i] = gx > 0 && gz > 0 && gx < WN - 1 && gz < WN - 1
						&& same(t, wTop[i - 1]) && same(t, wTop[i + 1]) && same(t, wTop[i - WN]) && same(t, wTop[i + WN]);
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
		// the highest puddle value in each block, so dry blocks are skipped quickly
		for (int gz = 0; gz < WN; gz++) {
			for (int gx = 0; gx < WN; gx++) {
				float peak = 0;
				for (int sz = 0; sz <= SUB; sz++) {
					for (int sx = 0; sx <= SUB; sx++) {
						peak = Math.max(peak, pNoise[(gz * SUB + sz) * PN + gx * SUB + sx]);
					}
				}
				// puddles only sit where every corner of the block is flat with its neighbours
				wPeak[gz * WN + gx] = peak;
			}
		}
		// fade puddle noise to zero on the edges of a flat block that borders a step, so puddles don't hang over edges
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

	/** Y of the rain-exposed top face at x/z, or NaN (water, leaves, fences, snow biomes, far away). */
	private static float wetTop(ClientWorld world, BlockPos.Mutable m, int x, int z, int playerY, boolean any) {
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
		if (top - playerY > 14 || playerY - top > 18) {
			return Float.NaN;
		}
		m.set(x, top - 1, z);
		BlockState st = world.getBlockState(m);
		if (st.isAir() || !st.getFluidState().isEmpty() || st.isIn(BlockTags.LEAVES)) {
			return Float.NaN;
		}
		VoxelShape shape = st.getCollisionShape(world, m);
		if (shape.isEmpty()) {
			return Float.NaN;
		}
		double h = shape.getMax(Direction.Axis.Y);
		if (h > 1.0 || h < 0.05) {
			return Float.NaN;
		}
		if (!any) {
			m.set(x, top, z);
			if (world.getPrecipitation(m) != Biome.Precipitation.RAIN) {
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

	/** A soft horizontal ring (not camera facing) so ripples lie flat on the ground. */
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
	public void renderHud(DrawContext ctx, float tickDelta) {
		if (!mood.isOn() || rainLevel < 0.01f) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
		float a = moodStrength.getFloat() * rainLevel;
		int c = moodColor.rgb();
		ctx.fill(0, 0, w, h, Colors.argb(c, a * 0.6f));
		ctx.fillGradient(0, 0, w, h / 3, Colors.argb(c, a), Colors.argb(c, 0));
		ctx.fillGradient(0, h * 2 / 3, w, h, Colors.argb(c, 0), Colors.argb(c, a * 0.9f));
	}
}
