package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.V3;
import com.whiteclient.render.WorldContext;
import com.whiteclient.render.WorldText;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * A glowing pet made of light that moves like a real animal: dogs, cats, foxes and hamsters walk next to
 * you and sit when you stop, bunnies hop, birds fly and land on your shoulder, fish swim around you.
 * Only you can see it (it is drawn, not a real entity), so it can't be hit and never affects gameplay.
 */
public class Pets extends Module {
	public static final String[] TYPES = {
			"Dog", "Cat", "Fox", "Bunny", "Hamster", "Bird", "Bee", "Butterfly", "Fish", "Jellyfish", "Wisp"
	};

	public final ModeSetting pet = add(new ModeSetting("Pet", "Dog", TYPES));
	public final TextSetting name = add(new TextSetting("Name", "", 24));
	public final BoolSetting natural = add(new BoolSetting("Natural Colors", true));
	public final ColorSetting color = add(new ColorSetting("Glow Color", 0x7FB8FF).visibleWhen(() -> !natural.isOn()));
	public final ColorSetting color2 = add(new ColorSetting("Second Color", 0xE79BFF).visibleWhen(() -> !natural.isOn()));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.6, 1.8, 0.05, "x"));
	public final ModeSetting side = add(new ModeSetting("Side", "Right", "Right", "Left"));
	public final NumberSetting distance = add(new NumberSetting("Distance", 1.5, 0.8, 3.0, 0.05));
	public final BoolSetting sit = add(new BoolSetting("Sit When You Stop", true).visibleWhen(this::isGround));
	public final BoolSetting perch = add(new BoolSetting("Land On Your Shoulder", true).visibleWhen(() -> pet.is("Bird")));
	public final NumberSetting height = add(new NumberSetting("Fly Height", 1.55, 0.5, 2.8, 0.05).visibleWhen(() -> !isGround()));
	public final NumberSetting glow = add(new NumberSetting("Glow", 1.0, 0.3, 2.0, 0.05, "x"));

	// simulation state (20 ticks per second), interpolated when drawing
	private double x, y, z, px, py, pz, vx, vy, vz;
	private float yaw, pyaw, phase, pphase, move, pmove, sitAmt, psit, perchAmt, pperch;
	private int idle, still;
	private boolean init;
	private float fade;
	private ClientWorld lastWorld;
	private String lastType = "";
	private Vec3d lastPlayer;

	public Pets() {
		super("Pets", "A glowing pet that follows you", Category.COSMETICS, false);
	}

	/** Pets are drawn, not entities; kept for code that skips pets. */
	public static boolean isPet(Entity e) {
		return false;
	}

	public boolean isGround() {
		return isGround(pet.get());
	}

	public static boolean isGround(String type) {
		return switch (type) {
			case "Dog", "Cat", "Fox", "Bunny", "Hamster" -> true;
			default -> false;
		};
	}

	@Override
	protected void onDisable() {
		init = false;
	}

	// ------------------------------------------------------------------ colours

	private int c1() {
		if (!natural.isOn()) {
			return color.rgb();
		}
		return switch (pet.get()) {
			case "Dog" -> 0xFFCF8A;
			case "Cat" -> 0xC9B3FF;
			case "Fox" -> 0xFF9150;
			case "Bunny" -> 0xF2F4FF;
			case "Hamster" -> 0xFFC49A;
			case "Bird" -> 0x6FD3FF;
			case "Bee" -> 0xFFD43B;
			case "Butterfly" -> 0x8FC2FF;
			case "Fish" -> 0xFF8A5C;
			case "Jellyfish" -> 0x7FB8FF;
			default -> 0xBFD8FF;
		};
	}

	private int c2() {
		if (!natural.isOn()) {
			return color2.rgb();
		}
		return switch (pet.get()) {
			case "Dog" -> 0xFFF1D6;
			case "Cat" -> 0xF4ECFF;
			case "Fox" -> 0xFFF0E0;
			case "Bunny" -> 0xFFB7D5;
			case "Bird" -> 0xFFE27A;
			case "Butterfly" -> 0xFF9BE9;
			case "Jellyfish" -> 0xE79BFF;
			default -> 0xFFFFFF;
		};
	}

	// ------------------------------------------------------------------ simulation

	private static double groundAt(ClientWorld world, double x, double z, double nearY) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
		int top = MathHelper.floor(nearY + 1.2);
		for (int yy = top; yy >= top - 8; yy--) {
			m.set(bx, yy, bz);
			VoxelShape shape = world.getBlockState(m).getCollisionShape(world, m);
			if (!shape.isEmpty()) {
				return yy + shape.getMax(Direction.Axis.Y);
			}
		}
		return nearY;
	}

	@Override
	public void onTick(MinecraftClient mc) {
		ClientWorld world = mc.world;
		PlayerEntity player = mc.player;
		if (world == null || player == null) {
			init = false;
			lastPlayer = null;
			return;
		}
		if (world != lastWorld || !pet.get().equals(lastType)) {
			init = false;
			lastWorld = world;
			lastType = pet.get();
		}
		px = x;
		py = y;
		pz = z;
		pyaw = yaw;
		pphase = phase;
		pmove = move;
		psit = sitAmt;
		pperch = perchAmt;

		Vec3d pl = player.getEntityPos();
		double pSpeed = lastPlayer == null ? 0 : Math.hypot(pl.x - lastPlayer.x, pl.z - lastPlayer.z);
		boolean playerMoving = lastPlayer != null && pl.squaredDistanceTo(lastPlayer) > 0.0004;
		lastPlayer = pl;
		still = playerMoving ? 0 : still + 1;

		float body = player.getBodyYaw();
		V3 f = V3.forward(body), r = V3.right(body);
		float sideSign = side.is("Left") ? -1f : 1f;
		if (isGround()) {
			tickGround(world, player, pl, pSpeed, f, r, sideSign);
		} else {
			tickFlyer(player, pl, f, r, sideSign);
		}
		fade = Math.min(1f, fade + 0.06f);
	}

	private void teleport(double nx, double ny, double nz, float nyaw) {
		x = px = nx;
		y = py = ny;
		z = pz = nz;
		yaw = pyaw = nyaw;
		vx = vy = vz = 0;
		fade = 0;
		idle = 0;
		init = true;
	}

	private void tickGround(ClientWorld world, PlayerEntity player, Vec3d pl, double pSpeed, V3 f, V3 r, float sideSign) {
		float s = size.getFloat();
		double d = distance.get();
		double tx = pl.x + r.x() * sideSign * d - f.x() * 0.5;
		double tz = pl.z + r.z() * sideSign * d - f.z() * 0.5;
		if (!init || Math.hypot(x - pl.x, z - pl.z) > 18 || Math.abs(pl.y - y) > 5) {
			teleport(tx, groundAt(world, tx, tz, pl.y), tz, player.getBodyYaw());
		}
		double dx = tx - x, dz = tz - z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		boolean bunny = pet.is("Bunny");
		float stride = strideOf(pet.get()) * s;
		if (dist > 0.3) {
			double maxStep = Math.max(0.09, pSpeed * 1.15 + 0.04);
			double step = Math.min(dist, Math.min(maxStep, dist * 0.22 + 0.015));
			if (bunny) {
				// bunnies move in hops: fast while in the air, a short pause on landing
				float cyc = (float) ((phase / (Math.PI * 2)) % 1.0);
				step *= cyc < 0.7f ? 1.35 : 0.25;
			}
			x += dx / dist * step;
			z += dz / dist * step;
			float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
			yaw += MathHelper.wrapDegrees(want - yaw) * 0.3f;
			phase += (float) (Math.max(step, bunny ? 0.03 : 0) / stride * Math.PI * 2);
			move += (Math.min(1f, (float) step / 0.07f) - move) * 0.3f;
			idle = 0;
		} else {
			move += (0f - move) * 0.25f;
			idle++;
			if (idle > 10) {
				float want = (float) Math.toDegrees(Math.atan2(-(pl.x - x), pl.z - z));
				yaw += MathHelper.wrapDegrees(want - yaw) * 0.08f;
			}
			if (bunny) {
				// finish the current hop instead of freezing mid-air
				float cyc = (float) ((phase / (Math.PI * 2)) % 1.0);
				if (cyc > 0.03f) {
					phase += 0.25f;
				}
			}
		}
		double gy = groundAt(world, x, z, Math.max(pl.y, y));
		y += (gy - y) * 0.45;
		boolean wantSit = sit.isOn() && idle > 40;
		sitAmt += ((wantSit ? 1f : 0f) - sitAmt) * 0.1f;
	}

	private void tickFlyer(PlayerEntity player, Vec3d pl, V3 f, V3 r, float sideSign) {
		double h = height.get() * player.getScale();
		double d = distance.get();
		float t = com.whiteclient.WhiteClient.get().time();
		double tx = pl.x + r.x() * sideSign * d * 0.85 + f.x() * 0.1;
		double ty = pl.y + h;
		double tz = pl.z + r.z() * sideSign * d * 0.85 + f.z() * 0.1;
		double k = 0.06, damp = 0.22;
		float flapRate;
		String type = pet.get();
		switch (type) {
			case "Bird" -> {
				boolean land = perch.isOn() && still > 40;
				if (land) {
					tx = pl.x + r.x() * sideSign * 0.33 - f.x() * 0.03;
					ty = pl.y + 1.43 * player.getScale() + (player.isSneaking() ? -0.25 : 0);
					tz = pl.z + r.z() * sideSign * 0.33 - f.z() * 0.03;
					k = 0.16;
					damp = 0.42;
				} else {
					tx += Math.sin(t * 0.7) * 0.3;
					ty += Math.sin(t * 1.1) * 0.15;
					tz += Math.cos(t * 0.9) * 0.3;
				}
				double toTarget = Math.sqrt(Math.pow(tx - x, 2) + Math.pow(ty - y, 2) + Math.pow(tz - z, 2));
				perchAmt += ((land && toTarget < 0.12 ? 1f : 0f) - perchAmt) * 0.25f;
				flapRate = perchAmt > 0.5f ? 0.05f : 0.95f;
			}
			case "Bee" -> {
				// lazy figure-eight around the anchor
				V3 off = r.mul((float) Math.sin(t * 1.3) * 0.5f).add(f, (float) Math.sin(t * 2.6) * 0.25f);
				tx += off.x();
				ty += Math.sin(t * 2.6 + 1) * 0.12;
				tz += off.z();
				k = 0.1;
				damp = 0.25;
				flapRate = 2.8f;
			}
			case "Butterfly" -> {
				tx += Math.sin(t * 1.7) * 0.4 + Math.sin(t * 4.3) * 0.08;
				ty += Math.sin(t * 3.1) * 0.15 + Math.sin(t * 1.3) * 0.1;
				tz += Math.cos(t * 1.4) * 0.4 + Math.cos(t * 3.7) * 0.08;
				k = 0.05;
				flapRate = 0.9f;
			}
			case "Fish" -> {
				// swims in a slow circle around you
				double ang = t * 0.55 * sideSign;
				tx = pl.x + Math.cos(ang) * d;
				tz = pl.z + Math.sin(ang) * d;
				ty = pl.y + h * 0.75 + Math.sin(t * 1.3) * 0.12;
				k = 0.05;
				damp = 0.2;
				flapRate = 0.35f;
			}
			case "Jellyfish" -> {
				ty += Math.sin(t * 0.8) * 0.18;
				tx += Math.sin(t * 0.3) * 0.2;
				tz += Math.cos(t * 0.35) * 0.2;
				k = 0.035;
				damp = 0.18;
				flapRate = 0.13f;
			}
			default -> { // wisp
				tx += Math.cos(t * 1.5) * 0.25;
				tz += Math.sin(t * 1.5) * 0.25;
				ty += Math.sin(t * 2.2) * 0.1;
				flapRate = 0.2f;
			}
		}
		if (!init || Math.sqrt(Math.pow(tx - x, 2) + Math.pow(ty - y, 2) + Math.pow(tz - z, 2)) > 18) {
			teleport(tx, ty, tz, player.getBodyYaw());
		}
		vx += (tx - x) * k - vx * damp;
		vy += (ty - y) * k - vy * damp;
		vz += (tz - z) * k - vz * damp;
		x += vx;
		y += vy;
		z += vz;
		double hs = Math.sqrt(vx * vx + vz * vz);
		float want = hs > 0.008 && perchAmt < 0.5f ? (float) Math.toDegrees(Math.atan2(-vx, vz)) : player.getBodyYaw();
		yaw += MathHelper.wrapDegrees(want - yaw) * 0.18f;
		move += (Math.min(1f, (float) hs / 0.08f) - move) * 0.2f;
		phase += flapRate * (type.equals("Fish") ? 1f + (float) hs * 25f : 1f);
		if (type.equals("Bird") && vy > 0.01) {
			phase += 0.4f; // flap harder when climbing
		}
	}

	private static float strideOf(String type) {
		return switch (type) {
			case "Cat" -> 0.5f;
			case "Fox" -> 0.58f;
			case "Bunny" -> 0.55f;
			case "Hamster" -> 0.22f;
			default -> 0.62f;
		};
	}

	// ------------------------------------------------------------------ drawing

	private record Quad(float bodyLen, float legLen, float bodyR, float headR, float neck, float snout, int ears, int tail, boolean hop) {
	}

	private static Quad quadOf(String type) {
		return switch (type) {
			case "Cat" -> new Quad(0.4f, 0.21f, 0.095f, 0.095f, 0.07f, 0.035f, 1, 1, false);
			case "Fox" -> new Quad(0.44f, 0.22f, 0.1f, 0.095f, 0.09f, 0.11f, 2, 2, false);
			case "Bunny" -> new Quad(0.26f, 0.09f, 0.11f, 0.09f, 0.09f, 0.03f, 3, 3, true);
			case "Hamster" -> new Quad(0.17f, 0.045f, 0.085f, 0.075f, 0.02f, 0.02f, 4, 4, false);
			default -> new Quad(0.46f, 0.26f, 0.12f, 0.11f, 0.13f, 0.09f, 0, 0, false);
		};
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		if (!init || ctx.client.player == null) {
			return;
		}
		float td = ctx.tickDelta;
		double lx = MathHelper.lerp(td, px, x), ly = MathHelper.lerp(td, py, y), lz = MathHelper.lerp(td, pz, z);
		float lyaw = pyaw + MathHelper.wrapDegrees(yaw - pyaw) * td;
		float ph = MathHelper.lerp(td, pphase, phase);
		float mv = MathHelper.lerp(td, pmove, move);
		float st = MathHelper.lerp(td, psit, sitAmt);
		float pr = MathHelper.lerp(td, pperch, perchAmt);
		V3 base = new V3(ctx.rx(lx), ctx.ry(ly), ctx.rz(lz));
		// in first person, hide the pet when it is right in front of the camera (e.g. on your shoulder)
		float camDist = base.length();
		float a = glow.getFloat() * fade;
		if (!ctx.thirdPerson() && camDist < 0.9f) {
			a *= Math.max(0f, (camDist - 0.5f) / 0.4f);
		}
		if (a <= 0.01f) {
			return;
		}
		int c1 = c1(), c2 = c2();
		float s = size.getFloat();
		float t = ctx.time;
		GlowBatch g = ctx.glow;
		float tagHeight;
		switch (pet.get()) {
			case "Bird" -> {
				bird(g, base, lyaw, ph, pr, s, c1, c2, a);
				tagHeight = 0.28f * s;
			}
			case "Bee" -> {
				bee(g, base, lyaw, ph, s, c1, c2, a);
				tagHeight = 0.25f * s;
			}
			case "Butterfly" -> {
				butterfly(g, base, lyaw, t, s, c1, c2, a);
				tagHeight = 0.35f * s;
			}
			case "Fish" -> {
				fish(g, base, lyaw, ph, t, s, c1, c2, a);
				tagHeight = 0.25f * s;
			}
			case "Jellyfish" -> {
				jellyfish(g, base, t, s, c1, c2, a);
				tagHeight = 0.5f * s;
			}
			case "Wisp" -> {
				wisp(g, base, t, s, c1, c2, a);
				tagHeight = 0.45f * s;
			}
			default -> {
				Quad q = quadOf(pet.get());
				float headTurn = 0;
				if (mv < 0.6f) {
					Vec3d player = ctx.client.player.getLerpedPos(td);
					double wx = player.x - lx, wz = player.z - lz;
					float toPlayer = (float) Math.toDegrees(Math.atan2(-wx, wz));
					headTurn = (float) Math.toRadians(MathHelper.clamp(MathHelper.wrapDegrees(toPlayer - lyaw), -55f, 55f)) * (1f - mv / 0.6f);
				}
				quadruped(g, base, lyaw, ph, mv, st, headTurn, t, s, q, c1, c2, a);
				tagHeight = (q.legLen + q.bodyR * 2 + q.neck + q.headR * 2.2f) * s + 0.12f;
			}
		}
		String n = name.get().trim();
		if (!n.isEmpty()) {
			WorldText.draw(ctx, base.x(), base.y() + tagHeight + 0.1f, base.z(), n, 0.9f, Colors.argb(0xFFFFFF, Math.min(1f, fade)), true);
		}
	}

	private static V3 lerp(V3 a, V3 b, float t) {
		return new V3(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
	}

	private static void blob(GlowBatch g, V3 p, float r, int col, float a) {
		g.disc(p.x(), p.y(), p.z(), r * 2.1f, col, 0.16f * a, 14);
		g.disc(p.x(), p.y(), p.z(), r, col, 0.3f * a, 12);
	}

	private static void dot(GlowBatch g, V3 p, float r, int col, float a) {
		g.disc(p.x(), p.y(), p.z(), r, col, Math.min(1f, a), 8);
	}

	/** Dogs, cats, foxes, bunnies and hamsters: body, head, ears, legs that walk, a tail that wags. */
	private void quadruped(GlowBatch g, V3 base, float yawDeg, float ph, float mv, float st, float headTurn, float t, float s, Quad q0, int c1, int c2, float a) {
		Quad q = new Quad(q0.bodyLen * s, q0.legLen * s, q0.bodyR * s, q0.headR * s, q0.neck * s, q0.snout * s, q0.ears, q0.tail, q0.hop);
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		float bob = mv * Math.abs((float) Math.sin(ph)) * 0.018f * s;
		float hop = q.hop ? Math.abs((float) Math.sin(ph * 0.5f)) * 0.22f * s : 0f;
		float hipH = q.legLen * (1f - 0.7f * st) + q.bodyR * 0.55f + hop + bob;
		float shH = q.legLen * (1f + 0.25f * st) + q.bodyR * 0.65f + hop + bob;
		V3 hip = base.add(f, -q.bodyLen / 2f + q.bodyLen * 0.12f * st).add(u, hipH);
		V3 sh = base.add(f, q.bodyLen / 2f).add(u, shH);

		// soft light on the ground
		g.flatDisc(base.x(), base.y() + 0.01f, base.z(), q.bodyLen * 0.7f + 0.1f, c1, 0.14f * a);

		// legs (diagonal pairs move together, like a trot)
		for (int leg = 0; leg < 4; leg++) {
			boolean front = leg < 2;
			float sideS = leg % 2 == 0 ? -1f : 1f;
			V3 attach = (front ? sh : hip).add(r, sideS * q.bodyR * 0.55f).add(u, -q.bodyR * 0.35f);
			float legPhase = ph + (((front ? 0 : 1) + (sideS > 0 ? 1 : 0)) % 2) * (float) Math.PI;
			float swing = (float) Math.sin(legPhase) * strideOf(pet.get()) * s * 0.28f * mv;
			float lift = Math.max(0f, (float) Math.cos(legPhase)) * 0.05f * s * mv;
			if (q.hop) {
				swing = front ? -0.02f * s : 0.03f * s;
				lift = hop * 0.35f;
			}
			V3 foot = base.add(f, (front ? q.bodyLen / 2f : -q.bodyLen / 2f) + swing).add(r, sideS * q.bodyR * 0.6f).add(u, lift + 0.01f);
			if (!front) {
				foot = foot.add(f, q.bodyLen * 0.32f * st);
			}
			V3 knee = lerp(attach, foot, 0.5f).add(f, front ? 0.012f * s : -0.04f * s * (1f + st));
			float w = 0.055f * s;
			attach.line(g, knee, w, c1, 0.8f * a);
			knee.line(g, foot, w * 0.9f, c1, 0.8f * a);
			dot(g, foot, 0.03f * s, c2, 0.55f * a);
		}

		// body: a chain of soft glows from hip to shoulders + a bright line along the back
		int n = 6;
		for (int i = 0; i < n; i++) {
			float k = i / (float) (n - 1);
			blob(g, lerp(hip, sh, k), q.bodyR * (0.9f + 0.15f * (float) Math.sin(Math.PI * k)), c1, a);
		}
		hip.add(u, q.bodyR * 0.8f).line(g, sh.add(u, q.bodyR * 0.85f), 0.04f * s, c2, 0.45f * a);

		// head
		V3 fh = f.rotate(u, headTurn), rh = r.rotate(u, headTurn);
		float lookUp = 0.04f * s * st;
		V3 neckBase = sh.add(u, q.bodyR * 0.3f);
		V3 head = neckBase.add(u, q.neck + q.headR * 0.4f + lookUp + (float) Math.sin(ph * 2) * 0.01f * mv).add(fh, q.headR * 0.6f);
		neckBase.line(g, head, q.headR * 0.9f, c1, 0.35f * a);
		blob(g, head, q.headR, c1, a * 1.15f);
		if (q.snout > 0.03f * s) {
			V3 sn = head.add(fh, q.headR * 0.85f + q.snout * 0.5f).add(u, -q.headR * 0.25f);
			blob(g, sn, q.snout * 0.6f + 0.015f * s, c1, a);
			dot(g, sn.add(fh, q.snout * 0.55f + 0.01f), 0.022f * s, c2, 0.9f * a);
		} else {
			dot(g, head.add(fh, q.headR * 0.95f).add(u, -q.headR * 0.2f), 0.018f * s, c2, 0.9f * a);
		}
		// eyes (with a blink now and then)
		boolean blink = (t % 4.3f) < 0.13f;
		if (!blink) {
			for (int e = -1; e <= 1; e += 2) {
				V3 eye = head.add(fh, q.headR * 0.78f).add(u, q.headR * 0.22f).add(rh, e * q.headR * 0.42f);
				dot(g, eye, 0.026f * s, 0xFFFFFF, a);
			}
		}
		// ears
		for (int e = -1; e <= 1; e += 2) {
			switch (q.ears) {
				case 0 -> { // floppy (dog)
					float flop = (float) Math.sin(ph) * 0.03f * s * mv;
					V3 e1 = head.add(u, q.headR * 0.7f).add(rh, e * q.headR * 0.55f);
					V3 e2 = e1.add(rh, e * q.headR * 0.32f).add(u, -q.headR * 0.8f + flop).add(fh, -0.02f * s);
					e1.line(g, e2, 0.075f * s, c1, 0.85f * a);
					dot(g, e2, 0.03f * s, c1, 0.5f * a);
				}
				case 1, 2 -> { // pointy (cat / fox)
					float tall = q.ears == 2 ? 1.85f : 1.45f;
					float twitch = Math.sin(t * 0.9 + e) > 0.97 ? 0.04f : 0f;
					V3 b1 = head.add(u, q.headR * 0.62f).add(rh, e * q.headR * 0.12f);
					V3 b2 = head.add(u, q.headR * 0.5f).add(rh, e * q.headR * 0.78f);
					V3 tip = head.add(u, q.headR * tall).add(rh, e * q.headR * (0.55f + twitch * 2)).add(fh, -0.01f * s);
					int fill = Colors.argb(c1, 0.28f * a);
					g.quad(b1.x(), b1.y(), b1.z(), fill, tip.x(), tip.y(), tip.z(), fill, tip.x(), tip.y(), tip.z(), fill, b2.x(), b2.y(), b2.z(), fill);
					b1.line(g, tip, 0.03f * s, c1, 0.9f * a);
					tip.line(g, b2, 0.03f * s, c1, 0.9f * a);
					dot(g, lerp(b1, tip, 0.45f).add(rh, e * 0.012f * s), 0.016f * s, c2, 0.6f * a);
				}
				case 3 -> { // long bunny ears
					float twitch = (float) Math.sin(t * 3 + e * 1.3) * 0.02f;
					V3 b = head.add(u, q.headR * 0.7f).add(rh, e * q.headR * 0.3f);
					V3 tip = b.add(u, q.headR * 2.3f).add(fh, -q.headR * 0.35f + twitch).add(rh, e * q.headR * 0.25f);
					b.line(g, tip, 0.08f * s, c1, 0.85f * a);
					b.line(g, lerp(b, tip, 0.85f), 0.03f * s, c2, 0.7f * a);
				}
				default -> dot(g, head.add(u, q.headR * 0.7f).add(rh, e * q.headR * 0.62f), 0.032f * s, c1, 0.6f * a);
			}
		}
		// tail
		V3 tb = hip.add(u, q.bodyR * 0.45f).add(f, -q.bodyR * 0.35f);
		switch (q.tail) {
			case 0 -> { // dog: wags, faster when happy (standing near you)
				float wag = (float) Math.sin(t * (mv > 0.3f ? 10 : 15)) * (0.35f + 0.4f * (1f - mv)) * (1f - 0.4f * st);
				V3 dir = f.mul(-0.55f).add(u, 0.85f).normalize().rotate(u, wag);
				V3 p0 = tb;
				for (int i = 0; i < 4; i++) {
					V3 p1 = p0.add(dir, 0.06f * s);
					p0.line(g, p1, 0.05f * s, c1, 0.85f * a);
					p0 = p1;
					dir = dir.add(f, -0.18f).normalize();
				}
				dot(g, p0, 0.03f * s, c2, 0.6f * a);
			}
			case 1 -> { // cat: long, curls slowly
				V3 dir = f.mul(-0.85f).add(u, 0.35f).normalize();
				V3 p0 = tb;
				for (int i = 0; i < 7; i++) {
					float sway = (float) Math.sin(t * 1.6 - i * 0.5) * 0.25f;
					V3 p1 = p0.add(dir.rotate(u, sway), 0.05f * s);
					p0.line(g, p1, 0.035f * s, c1, 0.85f * a);
					p0 = p1;
					dir = dir.add(u, 0.32f).normalize();
				}
				dot(g, p0, 0.022f * s, c2, 0.6f * a);
			}
			case 2 -> { // fox: big bushy tail with a white tip
				V3 dir = f.mul(-0.9f).add(u, -0.05f).normalize().rotate(u, (float) Math.sin(t * 2) * 0.18f);
				V3 p0 = tb;
				for (int i = 0; i < 5; i++) {
					V3 p1 = p0.add(dir, 0.055f * s);
					blob(g, p1, (0.035f + 0.012f * i) * s, i == 4 ? c2 : c1, a);
					p0 = p1;
					dir = dir.add(u, -0.08f).normalize();
				}
			}
			case 3 -> blob(g, hip.add(f, -q.bodyR * 0.95f).add(u, q.bodyR * 0.25f), 0.045f * s, c2, a * 1.3f);
			default -> {
			}
		}
	}

	private void bird(GlowBatch g, V3 p, float yawDeg, float ph, float perched, float s, int c1, int c2, float a) {
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		blob(g, p.add(f, -0.04f * s), 0.065f * s, c1, a);
		blob(g, p.add(f, 0.03f * s), 0.06f * s, c1, a);
		V3 hd = p.add(f, 0.1f * s).add(u, 0.06f * s);
		blob(g, hd, 0.05f * s, c1, a * 1.15f);
		hd.add(f, 0.04f * s).line(g, hd.add(f, 0.095f * s).add(u, -0.012f * s), 0.03f * s, c2, a);
		for (int e = -1; e <= 1; e += 2) {
			dot(g, hd.add(f, 0.03f * s).add(r, e * 0.034f * s).add(u, 0.014f * s), 0.013f * s, 0xFFFFFF, a);
		}
		// tail feathers
		V3 tb = p.add(f, -0.08f * s);
		for (int e = -1; e <= 1; e++) {
			tb.line(g, tb.add(f, -0.13f * s).add(r, e * 0.03f * s).add(u, -0.02f * s), 0.025f * s, e == 0 ? c2 : c1, 0.8f * a);
		}
		// wings: flapping in flight, folded when sitting on your shoulder
		float flap = (float) Math.sin(ph) * 0.95f + 0.25f;
		for (int e = -1; e <= 1; e += 2) {
			V3 root = p.add(r, e * 0.03f * s).add(u, 0.025f * s);
			V3 wd = r.mul(e).rotate(f, -e * flap);
			V3 tipFly = root.add(wd, 0.21f * s).add(f, -0.05f * s);
			V3 tipFold = root.add(f, -0.15f * s).add(r, e * 0.035f * s).add(u, -0.01f * s);
			V3 tip = lerp(tipFly, tipFold, perched);
			V3 mid = lerp(root.add(wd, 0.11f * s).add(f, 0.045f * s), root.add(f, -0.02f * s).add(r, e * 0.05f * s), perched);
			V3 back = lerp(root.add(wd, 0.12f * s).add(f, -0.09f * s), root.add(f, -0.1f * s).add(r, e * 0.045f * s), perched);
			int fill = Colors.argb(c1, 0.32f * a);
			g.quad(root.x(), root.y(), root.z(), fill, mid.x(), mid.y(), mid.z(), fill, tip.x(), tip.y(), tip.z(), fill, back.x(), back.y(), back.z(), fill);
			root.line(g, mid, 0.025f * s, c1, 0.85f * a);
			mid.line(g, tip, 0.025f * s, c1, 0.85f * a);
			tip.line(g, back, 0.02f * s, c2, 0.6f * a);
		}
	}

	private void bee(GlowBatch g, V3 p, float yawDeg, float ph, float s, int c1, int c2, float a) {
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		blob(g, p.add(f, -0.045f * s), 0.06f * s, c1, a);
		blob(g, p.add(f, 0.03f * s), 0.045f * s, c1, a);
		V3 hd = p.add(f, 0.075f * s).add(u, 0.01f * s);
		blob(g, hd, 0.035f * s, c1, a);
		// stripes over the back
		for (int i = 0; i < 2; i++) {
			V3 c = p.add(f, (-0.03f - 0.035f * i) * s).add(u, 0.05f * s);
			c.add(r, -0.045f * s).line(g, c.add(r, 0.045f * s), 0.016f * s, c2, 0.35f * a);
		}
		for (int e = -1; e <= 1; e += 2) {
			dot(g, hd.add(f, 0.025f * s).add(r, e * 0.022f * s).add(u, 0.01f * s), 0.01f * s, 0xFFFFFF, a);
			hd.add(r, e * 0.015f * s).line(g, hd.add(r, e * 0.03f * s).add(u, 0.06f * s).add(f, 0.03f * s), 0.012f * s, c1, 0.7f * a);
			// blurred fast wings: a few ghost positions
			for (int k = 0; k < 3; k++) {
				float flap = (float) Math.sin(ph + k * 0.7f) * 0.7f;
				V3 root = p.add(u, 0.04f * s).add(r, e * 0.02f * s);
				V3 tip = root.add(r.mul(e).rotate(f, -e * flap), 0.09f * s).add(u, 0.05f * s).add(f, -0.03f * s);
				V3 m1 = lerp(root, tip, 0.5f).add(f, 0.03f * s), m2 = lerp(root, tip, 0.5f).add(f, -0.03f * s);
				int fill = Colors.argb(c2, 0.14f * a);
				g.quad(root.x(), root.y(), root.z(), fill, m1.x(), m1.y(), m1.z(), fill, tip.x(), tip.y(), tip.z(), fill, m2.x(), m2.y(), m2.z(), fill);
			}
		}
		dot(g, p.add(f, -0.11f * s), 0.014f * s, c2, 0.8f * a);
	}

	private void fish(GlowBatch g, V3 p, float yawDeg, float ph, float t, float s, int c1, int c2, float a) {
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		int n = 7;
		V3 tail = p;
		for (int i = 0; i < n; i++) {
			float k = i / (float) (n - 1);
			float wig = (float) Math.sin(ph * 2.2f - i * 0.9f) * 0.025f * s * k;
			V3 c = p.add(f, (0.13f - 0.26f * k) * s).add(r, wig);
			float rad = (0.06f - 0.035f * k) * s;
			blob(g, c, rad, (i == 1 || i == 3) ? Colors.mix(c1, c2, 0.6f) : c1, a);
			tail = c;
		}
		float fin = (float) Math.sin(ph * 2.2f - 6) * 0.05f * s;
		V3 t1 = tail.add(f, -0.1f * s).add(r, 0.06f * s + fin).add(u, 0.04f * s);
		V3 t2 = tail.add(f, -0.1f * s).add(r, -0.06f * s + fin).add(u, -0.04f * s);
		int fill = Colors.argb(c1, 0.3f * a);
		g.quad(tail.x(), tail.y(), tail.z(), fill, t1.x(), t1.y(), t1.z(), fill, t2.x(), t2.y(), t2.z(), fill, tail.x(), tail.y(), tail.z(), fill);
		tail.line(g, t1, 0.022f * s, c1, 0.8f * a);
		tail.line(g, t2, 0.022f * s, c1, 0.8f * a);
		// pectoral fins + eyes
		for (int e = -1; e <= 1; e += 2) {
			V3 root = p.add(f, 0.05f * s).add(r, e * 0.04f * s).add(u, -0.02f * s);
			root.line(g, root.add(r, e * 0.05f * s).add(f, -0.04f * s).add(u, (float) Math.sin(t * 6 + e) * 0.015f * s), 0.02f * s, c2, 0.6f * a);
			dot(g, p.add(f, 0.12f * s).add(r, e * 0.03f * s).add(u, 0.015f * s), 0.012f * s, 0xFFFFFF, a);
		}
		// a bubble now and then
		float bu = (t * 0.6f) % 1f;
		dot(g, p.add(f, 0.18f * s).add(u, bu * 0.4f * s), 0.015f * s, c2, 0.5f * a * (1 - bu));
	}

	/** A glowing jellyfish ("kwal"): pulsing bell + flowing tentacles. */
	private void jellyfish(GlowBatch g, V3 c, float t, float s, int c1, int c2, float a) {
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.6);
		float bellR = (0.27f + 0.035f * pulse) * s;
		float bellH = (0.24f - 0.035f * pulse) * s;
		V3 top = c.add(V3.UP, 0.12f * s);
		int rings = 5, seg = 18;
		V3[] rim = new V3[seg + 1];
		for (int ring = 1; ring <= rings; ring++) {
			float u = ring / (float) rings;
			float rr = bellR * (float) Math.sin(u * Math.PI * 0.5);
			float yy = -bellH * u * u;
			V3 prev = null;
			for (int i = 0; i <= seg; i++) {
				double ang = Math.PI * 2 * i / seg + t * 0.3;
				float wav = ring == rings ? 0.015f * s * (float) Math.sin(ang * 6 + t * 4) : 0;
				V3 pt = new V3(top.x() + (float) Math.cos(ang) * (rr + wav), top.y() + yy, top.z() + (float) Math.sin(ang) * (rr + wav));
				if (prev != null) {
					g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), (ring == rings ? 0.05f : 0.03f) * s, Colors.mix(c1, c2, u), a * (ring == rings ? 0.9f : 0.45f));
				}
				prev = pt;
				if (ring == rings) {
					rim[i] = pt;
				}
			}
		}
		g.disc(top.x(), top.y() - bellH * 0.5f, top.z(), bellR * 2.4f, c1, 0.28f * a * (0.7f + 0.3f * pulse), 16);
		g.disc(top.x(), top.y() - bellH * 0.4f, top.z(), bellR * 0.9f, 0xFFFFFF, 0.35f * a, 12);
		int tentacles = 7;
		for (int k = 0; k < tentacles; k++) {
			V3 start = rim[(k * seg / tentacles) % seg];
			V3 prev = start;
			int steps = 8;
			float len = (0.6f + 0.14f * (k % 3)) * s;
			for (int st = 1; st <= steps; st++) {
				float u = st / (float) steps;
				float sway = (float) Math.sin(t * 2.2 - u * 4 + k) * 0.06f * u * s;
				float sway2 = (float) Math.cos(t * 1.8 - u * 3 + k * 1.7) * 0.06f * u * s;
				float inward = 1f - 0.35f * u;
				V3 pt = new V3(c.x() + (start.x() - c.x()) * inward + sway, start.y() - len * u, c.z() + (start.z() - c.z()) * inward + sway2);
				g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), 0.028f * s, Colors.mix(c1, c2, 0.4f + 0.6f * u), a * (1f - u * 0.75f) * 0.8f);
				prev = pt;
			}
		}
	}

	private void wisp(GlowBatch g, V3 c, float t, float s, int c1, int c2, float a) {
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3);
		g.disc(c.x(), c.y(), c.z(), 0.7f * s, c1, 0.3f * a, 16);
		g.disc(c.x(), c.y(), c.z(), (0.3f + 0.05f * pulse) * s, c2, 0.7f * a, 14);
		g.disc(c.x(), c.y(), c.z(), 0.11f * s, 0xFFFFFF, Math.min(1f, a), 10);
		for (int i = 0; i < 4; i++) {
			double ang = t * 2.4 + i * Math.PI / 2;
			V3 p = new V3(c.x() + (float) Math.cos(ang) * 0.36f * s, c.y() + (float) Math.sin(t * 3 + i) * 0.1f * s, c.z() + (float) Math.sin(ang) * 0.36f * s);
			g.disc(p.x(), p.y(), p.z(), 0.09f * s, i % 2 == 0 ? c1 : c2, 0.85f * a, 8);
		}
	}

	private void butterfly(GlowBatch g, V3 c, float yawDeg, float t, float s, int c1, int c2, float a) {
		V3 fwd = V3.forward(yawDeg);
		V3 right = V3.right(yawDeg);
		float flap = (float) Math.sin(t * 14);
		for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
			float lift = 0.6f + 0.5f * flap;
			V3 wingDir = right.mul(sideSign).rotate(fwd, -sideSign * lift);
			for (int part = 0; part < 2; part++) {
				float len = (part == 0 ? 0.44f : 0.3f) * s;
				float along = (part == 0 ? 0.1f : -0.1f) * s;
				V3 root = c.add(fwd, along * 0.3f);
				V3 tip = root.add(wingDir, len).add(fwd, along);
				V3 mid1 = root.add(wingDir, len * 0.55f).add(fwd, along + 0.13f * s);
				V3 mid2 = root.add(wingDir, len * 0.55f).add(fwd, along - 0.13f * s);
				int col = part == 0 ? c1 : c2;
				int fill = Colors.argb(col, 0.55f * a);
				int fillRoot = Colors.argb(0xFFFFFF, 0.6f * a);
				g.quad(root.x(), root.y(), root.z(), fillRoot, mid1.x(), mid1.y(), mid1.z(), fill, tip.x(), tip.y(), tip.z(), fill, mid2.x(), mid2.y(), mid2.z(), fill);
				root.line(g, mid1, 0.05f * s, col, a * 0.8f);
				mid1.line(g, tip, 0.05f * s, col, a * 0.8f);
				tip.line(g, mid2, 0.05f * s, col, a * 0.8f);
				mid2.line(g, root, 0.05f * s, col, a * 0.8f);
			}
		}
		c.add(fwd, 0.12f * s).line(g, c.add(fwd, -0.12f * s), 0.04f * s, 0xFFFFFF, a);
		g.disc(c.x(), c.y(), c.z(), 0.4f * s, c1, 0.12f * a, 12);
	}
}
