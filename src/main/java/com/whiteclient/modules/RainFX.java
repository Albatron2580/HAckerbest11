package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
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

	public RainFX() {
		super("Rain FX", "Glowing rain with ripples and splashes", Category.WORLD, true);
	}

	@Override
	protected void onDisable() {
		streakCount = rippleCount = dropCount = 0;
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
			float a = 0.32f * bright * strength;
			g.ribbon(x, y + len, z, x, y, z, 0.022f, Colors.argb(rgb, a));
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
			g.ribbon(x, y, z, x - dvx[i] * 0.03f, y - dvy[i] * 0.03f, z - dvz[i] * 0.03f, 0.018f, Colors.argb(Colors.mix(rgb, 0xFFFFFF, 0.5f), a));
			g.disc(x, y, z, 0.03f, rgb, a * 0.45f, 6);
		}
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
