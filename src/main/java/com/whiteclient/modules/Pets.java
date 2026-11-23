package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.OrganicMesh;
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
import net.minecraft.world.LightType;

/**
 * Pets that look and move like real animals, with a soft glow. Dogs, cats, foxes, bunnies and hamsters walk
 * next to you, jump when you jump, and when you stand still they leap up and lie on your shoulder or head.
 * Birds, bees and butterflies land on you, fish swim around you. "Light" style draws them as glowing light
 * sketches instead. Only you can see them (they are drawn, not real entities), so they never affect gameplay.
 */
public class Pets extends Module {
	public static final String[] TYPES = {
			"Dog", "Cat", "Fox", "Bunny", "Hamster", "Bird", "Bee", "Butterfly", "Fish", "Jellyfish", "Wisp"
	};

	public final ModeSetting pet = add(new ModeSetting("Pet", "Dog", TYPES));
	public final ModeSetting style = add(new ModeSetting("Style", "Real", "Real", "Light"));
	public final TextSetting name = add(new TextSetting("Name", "", 24));
	public final BoolSetting natural = add(new BoolSetting("Natural Colors", true));
	public final ColorSetting color = add(new ColorSetting("Glow Color", 0x7FB8FF).visibleWhen(() -> !natural.isOn()));
	public final ColorSetting color2 = add(new ColorSetting("Second Color", 0xE79BFF).visibleWhen(() -> !natural.isOn()));
	public final NumberSetting size = add(new NumberSetting("Size", 1.2, 0.6, 2.0, 0.05, "x"));
	public final ModeSetting side = add(new ModeSetting("Side", "Right", "Right", "Left"));
	public final NumberSetting distance = add(new NumberSetting("Distance", 1.5, 0.8, 3.0, 0.05));
	public final ModeSetting rest = add(new ModeSetting("Rest On You", "Shoulder", "Shoulder", "Head", "Off"));
	public final BoolSetting jumpWithYou = add(new BoolSetting("Jump With You", true).visibleWhen(this::isGround));
	public final BoolSetting sit = add(new BoolSetting("Sit When You Stop", true).visibleWhen(() -> isGround() && rest.is("Off")));
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
	// resting on you: 0 = on the ground, 1 = leaping up, 2 = lying on you, 3 = jumping down
	private int restState;
	private float restT, prestT;
	private double fromX, fromY, fromZ, landX, landY, landZ;
	// jumping with you
	private boolean airborne, wasOnGround = true;
	private double hopVy;
	private float air, pair, lie, plie;
	private final OrganicMesh mesh = new OrganicMesh();

	public Pets() {
		super("Pets", "A pet that follows you, jumps with you and rests on your shoulder", Category.COSMETICS, false);
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

	/** Flyers that land on you when you stand still. */
	private static boolean lands(String type) {
		return switch (type) {
			case "Bird", "Bee", "Butterfly", "Wisp", "Jellyfish" -> true;
			default -> false;
		};
	}

	/** How big the pet is while it rests on you (a full size dog would not fit on a shoulder). */
	private static float restScale(String type) {
		return switch (type) {
			case "Dog" -> 0.7f;
			case "Fox" -> 0.72f;
			case "Cat" -> 0.78f;
			case "Bunny" -> 0.85f;
			case "Jellyfish", "Wisp" -> 0.55f;
			default -> 1f;
		};
	}

	@Override
	protected void onDisable() {
		init = false;
		restState = 0;
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

	/** Real-style colours: fur/feathers on top, belly, legs, nose, eyes, inner ears, accent. */
	private record Look(int top, int belly, int legs, int nose, int eye, int inner, int accent) {
	}

	private Look look() {
		if (!natural.isOn()) {
			int a = color.rgb(), b = color2.rgb();
			return new Look(a, b, a, 0x1A1616, 0x161214, b, b);
		}
		return switch (pet.get()) {
			case "Dog" -> new Look(0xD39A52, 0xF2D7A6, 0xDDAA66, 0x1B1716, 0x2B1B10, 0xB97A3E, 0xF2D7A6);
			case "Cat" -> new Look(0x8A8D96, 0xEDEDF0, 0xEDEDF0, 0xE79AA8, 0x9BE36B, 0xF0A9B6, 0x5E616A);
			case "Fox" -> new Look(0xE0742A, 0xF8F1E8, 0x2E2420, 0x1B1716, 0x2B1B10, 0xF8F1E8, 0x2E2420);
			case "Bunny" -> new Look(0xF1F1F4, 0xFFFFFF, 0xF1F1F4, 0xF09AB2, 0x1C1416, 0xF4B3C6, 0xFFFFFF);
			case "Hamster" -> new Look(0xE3A462, 0xFFF4E6, 0xF6CDB7, 0xE79AA8, 0x140E0E, 0xF2B9A6, 0xFFF4E6);
			case "Bird" -> new Look(0x4FBDF0, 0xA8E4FA, 0xB9A7A0, 0xE8B04A, 0x111111, 0x3E57D6, 0xFFE27A);
			case "Bee" -> new Look(0xF2C230, 0xC98E24, 0x1C1A16, 0x1C1A16, 0x111111, 0xFFFFFF, 0x1C1A16);
			case "Butterfly" -> new Look(0x2F86FF, 0x0C1838, 0x1A1A22, 0x1A1A22, 0x111111, 0xFFFFFF, 0x9BD3FF);
			case "Fish" -> new Look(0xFF7424, 0xFFF3EA, 0xFF7424, 0xF0A0A0, 0x111111, 0xFFB27A, 0xFFF6EE);
			default -> new Look(c1(), c2(), c1(), 0x1A1616, 0x111111, c2(), c2());
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

	/** Where the pet rests on you (world x, y, z) and the way it faces. */
	private double[] restSpot(PlayerEntity p, float td) {
		Vec3d pos = p.getLerpedPos(td);
		// the player model is drawn at 15/16 scale: shoulders at 1.41, top of the head at 1.875
		float sc = p.getScale() * 0.9375f;
		boolean sneak = p.isInSneakingPose();
		double neck = (sneak ? 1.18 : 1.5) * sc;
		if (rest.is("Head")) {
			float headYaw = MathHelper.lerpAngleDegrees(td, p.lastHeadYaw, p.headYaw);
			double pitch = Math.toRadians(MathHelper.clamp(p.getPitch(td), -45f, 45f));
			V3 f = V3.forward(headYaw);
			double fwd = 0.5 * sc * Math.sin(pitch) + (sneak ? 0.1 * sc : 0);
			return new double[]{pos.x + f.x() * fwd, pos.y + neck + 0.5 * sc * Math.cos(pitch) + 0.01, pos.z + f.z() * fwd, headYaw};
		}
		float bodyYaw = MathHelper.lerpAngleDegrees(td, p.lastBodyYaw, p.bodyYaw);
		V3 f = V3.forward(bodyYaw), r = V3.right(bodyYaw);
		float sideSign = side.is("Left") ? -1f : 1f;
		double out = 0.36 * sc, up = neck + 0.004, fwd = sneak ? 0.12 * sc : -0.01;
		return new double[]{pos.x + r.x() * sideSign * out + f.x() * fwd, pos.y + up, pos.z + r.z() * sideSign * out + f.z() * fwd, bodyYaw};
	}

	@Override
	public void onTick(MinecraftClient mc) {
		ClientWorld world = mc.world;
		PlayerEntity player = mc.player;
		if (world == null || player == null) {
			init = false;
			lastPlayer = null;
			restState = 0;
			return;
		}
		if (world != lastWorld || !pet.get().equals(lastType)) {
			init = false;
			restState = 0;
			airborne = false;
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
		prestT = restT;
		pair = air;
		plie = lie;

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
			restState = 0;
			tickFlyer(player, pl, f, r, sideSign);
		}
		wasOnGround = player.isOnGround();
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
		airborne = false;
		hopVy = 0;
		init = true;
	}

	private void tickGround(ClientWorld world, PlayerEntity player, Vec3d pl, double pSpeed, V3 f, V3 r, float sideSign) {
		float s = size.getFloat();
		double d = distance.get();
		double tx = pl.x + r.x() * sideSign * d - f.x() * 0.5;
		double tz = pl.z + r.z() * sideSign * d - f.z() * 0.5;
		boolean carried = player.getAbilities().flying || player.isGliding() || player.hasVehicle();
		boolean wantRest = !rest.is("Off") && (still > 40 || carried) && !player.isSpectator() && !player.isSleeping()
				&& !player.isTouchingWater() && !player.isInvisible();

		if (restState != 0 && init) {
			double[] spot = restSpot(player, 1f);
			if (restState == 1) {
				restT = Math.min(1f, restT + (carried ? 0.16f : 0.1f));
				if (restT >= 1f) {
					restState = 2;
				}
			} else if (restState == 2) {
				if (!wantRest) {
					restState = 3;
					restT = prestT = 0f;
				}
			} else {
				landX = tx;
				landZ = tz;
				landY = groundAt(world, tx, tz, pl.y);
				restT = Math.min(1f, restT + 0.1f);
				if (restT >= 1f) {
					restState = 0;
					x = px = landX;
					y = py = landY;
					z = pz = landZ;
					yaw = pyaw = (float) spot[3];
					idle = 0;
					airborne = false;
					hopVy = 0;
					return;
				}
			}
			x = spot[0];
			y = spot[1];
			z = spot[2];
			yaw = (float) spot[3];
			move += (0f - move) * 0.3f;
			air += ((restState == 2 ? 0f : 1f) - air) * 0.4f;
			lie += ((restState == 2 ? 1f : 0f) - lie) * 0.22f;
			sitAmt += (0f - sitAmt) * 0.2f;
			return;
		}

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
		// jump when you jump
		boolean onG = player.isOnGround();
		if (jumpWithYou.isOn() && !airborne && wasOnGround && !onG && player.getVelocity().y > 0.15 && !carried) {
			airborne = true;
			hopVy = 0.42;
		}
		double gy = groundAt(world, x, z, Math.max(pl.y, y));
		if (airborne) {
			y += hopVy;
			hopVy = (hopVy - 0.08) * 0.98;
			if (hopVy < 0 && y <= gy) {
				y = gy;
				airborne = false;
				hopVy = 0;
			}
		} else {
			y += (gy - y) * 0.45;
		}
		air += ((airborne ? 1f : 0f) - air) * 0.4f;
		lie += (0f - lie) * 0.25f;
		boolean wantSit = sit.isOn() && rest.is("Off") && idle > 40;
		sitAmt += ((wantSit ? 1f : 0f) - sitAmt) * 0.1f;
		// stood still long enough (or you are flying / riding): leap up onto you
		if (wantRest && !airborne && (carried || (idle > 6 && Math.hypot(pl.x - x, pl.z - z) < 4.5 && Math.abs(pl.y - y) < 2.5))) {
			restState = 1;
			restT = prestT = 0f;
			fromX = x;
			fromY = y;
			fromZ = z;
		}
	}

	private void tickFlyer(PlayerEntity player, Vec3d pl, V3 f, V3 r, float sideSign) {
		double h = height.get() * player.getScale();
		double d = distance.get();
		float t = WhiteClient.get().time();
		double tx = pl.x + r.x() * sideSign * d * 0.85 + f.x() * 0.1;
		double ty = pl.y + h;
		double tz = pl.z + r.z() * sideSign * d * 0.85 + f.z() * 0.1;
		double k = 0.06, damp = 0.22;
		float flapRate;
		String type = pet.get();
		boolean wantRest = !rest.is("Off") && still > 40 && !player.isTouchingWater() && !player.isSpectator();
		boolean land = wantRest && lands(type);
		if (land) {
			double[] spot = restSpot(player, 1f);
			tx = spot[0];
			ty = spot[1] + restLift(type);
			tz = spot[2];
			k = 0.16;
			damp = 0.42;
		}
		switch (type) {
			case "Bird" -> {
				if (!land) {
					tx += Math.sin(t * 0.7) * 0.3;
					ty += Math.sin(t * 1.1) * 0.15;
					tz += Math.cos(t * 0.9) * 0.3;
				}
				flapRate = perchAmt > 0.5f ? 0.05f : 0.95f;
			}
			case "Bee" -> {
				if (!land) {
					// lazy figure-eight around the anchor
					V3 off = r.mul((float) Math.sin(t * 1.3) * 0.5f).add(f, (float) Math.sin(t * 2.6) * 0.25f);
					tx += off.x();
					ty += Math.sin(t * 2.6 + 1) * 0.12;
					tz += off.z();
					k = 0.1;
					damp = 0.25;
				}
				flapRate = perchAmt > 0.5f ? 0.15f : 2.8f;
			}
			case "Butterfly" -> {
				if (!land) {
					tx += Math.sin(t * 1.7) * 0.4 + Math.sin(t * 4.3) * 0.08;
					ty += Math.sin(t * 3.1) * 0.15 + Math.sin(t * 1.3) * 0.1;
					tz += Math.cos(t * 1.4) * 0.4 + Math.cos(t * 3.7) * 0.08;
					k = 0.05;
				}
				flapRate = 0.9f;
			}
			case "Fish" -> {
				// swims in a slow circle around you (a small one around your head when you stand still)
				double ang = t * (wantRest ? 0.9 : 0.55) * sideSign;
				double rad = wantRest ? 0.62 : d;
				tx = pl.x + Math.cos(ang) * rad;
				tz = pl.z + Math.sin(ang) * rad;
				ty = pl.y + (wantRest ? 1.75 * player.getScale() : h * 0.75) + Math.sin(t * 1.3) * 0.12;
				k = 0.05;
				damp = 0.2;
				flapRate = 0.35f;
			}
			case "Jellyfish" -> {
				if (!land) {
					ty += Math.sin(t * 0.8) * 0.18;
					tx += Math.sin(t * 0.3) * 0.2;
					tz += Math.cos(t * 0.35) * 0.2;
					k = 0.035;
					damp = 0.18;
				}
				flapRate = 0.13f;
			}
			default -> { // wisp
				if (!land) {
					tx += Math.cos(t * 1.5) * 0.25;
					tz += Math.sin(t * 1.5) * 0.25;
					ty += Math.sin(t * 2.2) * 0.1;
				}
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
		double toTarget = Math.sqrt(Math.pow(tx - x, 2) + Math.pow(ty - y, 2) + Math.pow(tz - z, 2));
		perchAmt += ((land && toTarget < 0.15 ? 1f : 0f) - perchAmt) * 0.25f;
		double hs = Math.sqrt(vx * vx + vz * vz);
		float want = hs > 0.008 && perchAmt < 0.5f ? (float) Math.toDegrees(Math.atan2(-vx, vz)) : player.getBodyYaw();
		yaw += MathHelper.wrapDegrees(want - yaw) * 0.18f;
		move += (Math.min(1f, (float) hs / 0.08f) - move) * 0.2f;
		phase += flapRate * (type.equals("Fish") ? 1f + (float) hs * 25f : 1f);
		if (type.equals("Bird") && vy > 0.01) {
			phase += 0.4f; // flap harder when climbing
		}
	}

	private static double restLift(String type) {
		return switch (type) {
			case "Jellyfish" -> 0.55;
			case "Wisp" -> 0.16;
			default -> 0;
		};
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

	/** Proportions of the real-style animals (blocks, at size 1). */
	private record Rig(float bodyLen, float legLen, float bodyW, float bodyH, float headR, float neck, float snoutLen, float snoutR,
					   int ears, int tail, boolean hop, boolean round) {
	}

	private static Rig rigOf(String type) {
		return switch (type) {
			case "Cat" -> new Rig(0.25f, 0.17f, 0.066f, 0.074f, 0.072f, 0.06f, 0.026f, 0.034f, 1, 1, false, false);
			case "Fox" -> new Rig(0.28f, 0.17f, 0.068f, 0.078f, 0.074f, 0.07f, 0.085f, 0.03f, 2, 2, false, false);
			case "Bunny" -> new Rig(0.1f, 0.06f, 0.085f, 0.092f, 0.07f, 0.02f, 0.022f, 0.036f, 3, 3, true, true);
			case "Hamster" -> new Rig(0.05f, 0.03f, 0.08f, 0.076f, 0.066f, 0f, 0.018f, 0.03f, 4, 4, false, true);
			default -> new Rig(0.27f, 0.2f, 0.088f, 0.096f, 0.088f, 0.085f, 0.072f, 0.042f, 0, 0, false, false);
		};
	}

	private static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3 - 2 * t);
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		if (!init || ctx.client.player == null || ctx.client.world == null) {
			return;
		}
		float td = ctx.tickDelta;
		PlayerEntity player = ctx.client.player;
		String type = pet.get();
		boolean ground = isGround();
		double lx = MathHelper.lerp(td, px, x), ly = MathHelper.lerp(td, py, y), lz = MathHelper.lerp(td, pz, z);
		float lyaw = pyaw + MathHelper.wrapDegrees(yaw - pyaw) * td;
		float onYou = 0f; // 0 on the ground .. 1 resting on you
		float airUp = (float) MathHelper.clamp((y - py) / 0.18, -1.0, 1.0);
		float rt = MathHelper.lerp(td, prestT, restT);
		if (ground && restState != 0) {
			double[] spot = restSpot(player, td);
			float e = ease(rt);
			if (restState == 1) {
				lx = MathHelper.lerp(e, fromX, spot[0]);
				lz = MathHelper.lerp(e, fromZ, spot[2]);
				ly = fromY + (spot[1] - fromY) * rt + 0.5 * 4 * rt * (1 - rt);
				float travel = (float) Math.toDegrees(Math.atan2(-(spot[0] - fromX), spot[2] - fromZ));
				lyaw = travel + MathHelper.wrapDegrees((float) spot[3] - travel) * ease((rt - 0.6f) / 0.4f);
				onYou = e;
				airUp = rt < 0.55f ? 1f : -0.4f;
			} else if (restState == 2) {
				lx = spot[0];
				ly = spot[1];
				lz = spot[2];
				lyaw = (float) spot[3];
				onYou = 1f;
				airUp = 0f;
			} else {
				lx = MathHelper.lerp(e, spot[0], landX);
				lz = MathHelper.lerp(e, spot[2], landZ);
				ly = spot[1] + (landY - spot[1]) * rt + 0.35 * 4 * rt * (1 - rt);
				float travel = (float) Math.toDegrees(Math.atan2(-(landX - spot[0]), landZ - spot[2]));
				lyaw = (float) spot[3] + MathHelper.wrapDegrees(travel - (float) spot[3]) * ease(rt / 0.3f);
				onYou = 1f - e;
				airUp = rt < 0.3f ? 0.6f : -1f;
			}
		} else if (!ground && lands(type)) {
			float pr = MathHelper.lerp(td, pperch, perchAmt);
			if (pr > 0.001f) {
				double[] spot = restSpot(player, td);
				lx = MathHelper.lerp(pr, lx, spot[0]);
				ly = MathHelper.lerp(pr, ly, spot[1] + restLift(type));
				lz = MathHelper.lerp(pr, lz, spot[2]);
				lyaw = lyaw + MathHelper.wrapDegrees((float) spot[3] - lyaw) * pr;
				onYou = pr;
			}
		}
		float ph = MathHelper.lerp(td, pphase, phase);
		float mv = MathHelper.lerp(td, pmove, move);
		float st = MathHelper.lerp(td, psit, sitAmt);
		float pr = MathHelper.lerp(td, pperch, perchAmt);
		float ar = MathHelper.lerp(td, pair, air);
		float li = MathHelper.lerp(td, plie, lie);
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
		float s = size.getFloat() * MathHelper.lerp(onYou, 1f, restScale(type));
		float t = ctx.time;
		GlowBatch g = ctx.glow;
		float tagHeight;
		boolean real = style.is("Real") && !type.equals("Jellyfish") && !type.equals("Wisp");
		if (real) {
			float pop = 1f - (1f - fade) * (1f - fade) * (1f - fade);
			s *= 0.35f + 0.65f * pop;
			BlockPos bp = BlockPos.ofFloored(lx, ly + 0.3, lz);
			long time = ctx.client.world.getTimeOfDay() % 24000L;
			float day = 0.5f + 0.5f * (float) Math.cos((time - 6000L) / 24000.0 * Math.PI * 2);
			float sky = ctx.client.world.getLightLevel(LightType.SKY, bp) / 15f * (0.18f + 0.82f * day);
			float block = ctx.client.world.getLightLevel(LightType.BLOCK, bp) / 15f;
			float light = 0.3f + 0.7f * Math.max(sky, block);
			int glowRgb = Colors.mix(c1, 0xFFFFFF, 0.25f);
			mesh.setup(light, glowRgb, 0.55f * a);
			Look lk = look();
			boolean blink = (t % 4.3f) < 0.13f;
			V3 center;
			switch (type) {
				case "Bird" -> {
					birdReal(base, lyaw, ph, pr, s * 1.5f, lk, blink);
					center = base.add(V3.UP, 0.05f * s);
					tagHeight = 0.3f * s;
				}
				case "Bee" -> {
					beeReal(base, lyaw, ph, pr, s * 1.7f, lk);
					center = base;
					tagHeight = 0.3f * s;
				}
				case "Butterfly" -> {
					butterflyReal(g, base, lyaw, t, pr, s, lk, a);
					center = base;
					tagHeight = 0.32f * s;
				}
				case "Fish" -> {
					fishReal(base, lyaw, ph, s * 1.6f, lk);
					center = base;
					tagHeight = 0.32f * s;
				}
				default -> {
					Rig rg = rigOf(type);
					float headTurn = 0, headPitch = 0;
					if (onYou > 0.5f) {
						headTurn = (float) Math.sin(t * 0.55f) * 0.45f + (float) Math.sin(t * 1.7f) * 0.08f;
						headPitch = 0.08f;
					} else if (mv < 0.6f) {
						Vec3d pp = player.getLerpedPos(td);
						double wx = pp.x - lx, wz = pp.z - lz;
						float toPlayer = (float) Math.toDegrees(Math.atan2(-wx, wz));
						float k = 1f - mv / 0.6f;
						headTurn = (float) Math.toRadians(MathHelper.clamp(MathHelper.wrapDegrees(toPlayer - lyaw), -55f, 55f)) * k;
						headPitch = (0.12f + 0.25f * st) * k;
					}
					if (onYou < 0.05f) {
						double gy = groundAt(ctx.client.world, lx, lz, ly);
						float above = (float) (ly - gy);
						float sa = Math.max(0f, 1f - above * 1.4f);
						V3 sc = new V3(base.x(), (float) (ctx.ry(gy) + 0.012), base.z());
						mesh.shadow(sc, V3.forward(lyaw), (rg.bodyLen() * 0.5f + rg.bodyH() * 1.4f) * s, rg.bodyW() * 2.1f * s, 0.42f * sa);
					}
					quadrupedReal(base, lyaw, ph, mv, st, li, ar, airUp, headTurn, headPitch, t, s, rg, lk, blink);
					center = base.add(V3.UP, (rg.legLen() + rg.bodyH()) * s);
					tagHeight = (rg.legLen() + rg.bodyH() * 2 + rg.neck() + rg.headR() * 2.6f) * s + 0.08f;
				}
			}
			mesh.draw(ctx.consumers, ctx.matrices.peek().getPositionMatrix());
			g.disc(center.x(), center.y(), center.z(), 0.42f * s, glowRgb, 0.09f * a, 14);
		} else {
			switch (type) {
				case "Bird" -> {
					bird(g, base, lyaw, ph, pr, s * 1.5f, c1, c2, a);
					tagHeight = 0.4f * s;
				}
				case "Bee" -> {
					bee(g, base, lyaw, ph, s * 1.7f, c1, c2, a);
					tagHeight = 0.38f * s;
				}
				case "Butterfly" -> {
					butterfly(g, base, lyaw, t, s, c1, c2, a);
					tagHeight = 0.35f * s;
				}
				case "Fish" -> {
					fish(g, base, lyaw, ph, t, s * 1.6f, c1, c2, a);
					tagHeight = 0.38f * s;
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
					Quad q = quadOf(type);
					float headTurn = 0;
					if (mv < 0.6f && onYou < 0.5f) {
						Vec3d pp = player.getLerpedPos(td);
						double wx = pp.x - lx, wz = pp.z - lz;
						float toPlayer = (float) Math.toDegrees(Math.atan2(-wx, wz));
						headTurn = (float) Math.toRadians(MathHelper.clamp(MathHelper.wrapDegrees(toPlayer - lyaw), -55f, 55f)) * (1f - mv / 0.6f);
					}
					quadruped(g, base, lyaw, ph, mv, Math.max(st, li), headTurn, t, s, q, c1, c2, a);
					tagHeight = (q.legLen + q.bodyR * 2 + q.neck + q.headR * 2.2f) * s + 0.12f;
				}
			}
		}
		String n = name.get().trim();
		if (!n.isEmpty()) {
			WorldText.draw(ctx, base.x(), base.y() + tagHeight + 0.1f, base.z(), n, 0.9f, Colors.argb(0xFFFFFF, Math.min(1f, fade)), true);
		}
	}

	// ------------------------------------------------------------------ real style

	private static V3[] frame(V3 dir) {
		V3 az = dir.normalize();
		V3 ref = Math.abs(az.y()) < 0.95f ? V3.UP : new V3(1, 0, 0);
		V3 ax = ref.cross(az).normalize();
		V3 ay = az.cross(ax).normalize();
		return new V3[]{ax, ay, az};
	}

	/** Ellipsoid stretched from a to b (tails, segments). */
	private void blobAlong(V3 a, V3 b, float rad, float radV, int top, int bottom) {
		V3 d = b.sub(a);
		float len = d.length();
		if (len < 1.0e-5f) {
			return;
		}
		V3[] fr = frame(d);
		mesh.ellipsoid(lerp(a, b, 0.5f), fr[0], fr[1], fr[2], rad, radV, len * 0.5f + rad * 0.55f, top, bottom, 0f, 0xFF);
	}

	private void eye(V3 c, V3 rh, V3 uh, V3 fh, float er, int iris, boolean blink, boolean slit, int side) {
		if (blink) {
			mesh.ellipsoid(c, rh, uh, fh, er * 1.05f, er * 0.16f, er * 0.9f, 0x1A1414);
			return;
		}
		mesh.ellipsoid(c, rh, uh, fh, er, er, er * 0.92f, iris, iris, 1.3f, 0xFF);
		if (slit) {
			mesh.ellipsoid(c.add(fh, er * 0.72f), rh, uh, fh, er * 0.26f, er * 0.8f, er * 0.3f, 0x0C0C0C);
		}
		mesh.sphere(c.add(fh, er * 0.78f).add(uh, er * 0.42f).add(rh, -side * er * 0.2f), er * 0.27f, 0xFFFFFF);
	}

	/** Dogs, cats, foxes, bunnies and hamsters with soft shaded fur, real proportions and natural poses. */
	private void quadrupedReal(V3 base, float yawDeg, float ph, float mv, float st, float lie, float air, float airUp,
							   float headTurn, float headPitch, float t, float s, Rig rg, Look lk, boolean blink) {
		OrganicMesh m = mesh;
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		float L = rg.bodyLen() * s, Lg = rg.legLen() * s, W = rg.bodyW() * s, H = rg.bodyH() * s, HR = rg.headR() * s;
		float grounded = (1f - air);
		float bob = mv * Math.abs((float) Math.sin(ph)) * 0.014f * s * grounded;
		float hop = rg.hop() ? Math.abs((float) Math.sin(ph * 0.5f)) * 0.2f * s * (1f - lie) * grounded : 0f;
		float breathe = (float) Math.sin(t * 2.2f) * 0.004f * s;
		float hipH = MathHelper.lerp(st, Lg + H * 0.5f, H * 0.72f);
		float shH = MathHelper.lerp(st, Lg + H * 0.6f, Lg * 1.05f + H * 0.72f);
		hipH = MathHelper.lerp(lie, hipH, H * 0.8f);
		shH = MathHelper.lerp(lie, shH, H * 0.86f);
		hipH += hop + bob;
		shH += hop + bob;
		float pitch = air * MathHelper.clamp(airUp, -1f, 1f) * 0.38f;
		float hipShift = L * 0.14f * st;
		V3 mid = base.add(u, (hipH + shH) / 2f + breathe).add(f, hipShift / 2f);
		V3 sd0 = f.mul(L - hipShift).add(u, shH - hipH);
		float span = Math.max(1.0e-4f, sd0.length());
		V3 sd = sd0.mul(1f / span).rotate(r, pitch);
		V3 hip = mid.add(sd, -span / 2f), sh = mid.add(sd, span / 2f);
		V3 bu = r.cross(sd).normalize();
		int top = lk.top(), belly = lk.belly();

		// torso: barrel + chest + haunches (or one round ball for small animals)
		if (rg.round()) {
			m.ellipsoid(mid, r, bu, sd, W * 1.05f, H * 1.02f, span / 2f + H * 0.9f, top, belly, 0f, 0xFF);
		} else {
			m.ellipsoid(mid, r, bu, sd, W * 0.98f, H * 0.9f, span / 2f + H * 0.4f, top, belly, 0f, 0xFF);
			m.ellipsoid(sh.add(bu, H * 0.06f), r, bu, sd, W * 1.06f, H * 1.04f, H * 1.0f, top, belly, 0f, 0xFF);
			m.ellipsoid(hip.add(bu, H * 0.04f), r, bu, sd, W * 1.0f, H * 0.98f, H * 0.95f, top, belly, 0f, 0xFF);
		}

		// head
		V3 fh = f.rotate(u, headTurn), rh = r.rotate(u, headTurn);
		V3 fh2 = fh.rotate(rh, headPitch);
		V3 uh = rh.cross(fh2).normalize();
		float neckLen = rg.neck() * s * (1f - 0.25f * lie);
		V3 head;
		if (rg.round()) {
			head = sh.add(sd, H * 0.62f).add(u, H * 0.5f);
		} else {
			V3 neckBase = sh.add(bu, H * 0.35f).add(sd, H * 0.3f);
			head = neckBase.add(u, neckLen + HR * 0.3f).add(fh, HR * 0.45f + neckLen * 0.4f);
			m.tube(sh.add(bu, H * 0.15f), head.add(uh, -HR * 0.25f).add(fh2, -HR * 0.2f), W * 0.75f, HR * 0.62f, top);
		}
		m.ellipsoid(head, rh, uh, fh2, HR * 0.94f, HR * 0.9f, HR, top, belly, 0f, 0xFF);
		if (rg.ears() == 4) { // hamster cheeks
			for (int e = -1; e <= 1; e += 2) {
				m.ellipsoid(head.add(rh, e * HR * 0.5f).add(uh, -HR * 0.28f).add(fh2, HR * 0.28f), rh, uh, fh2, HR * 0.5f, HR * 0.45f, HR * 0.5f, belly);
			}
		}
		// muzzle + nose
		float sl = rg.snoutLen() * s, sr = rg.snoutR() * s;
		float sLen = Math.max(sl * 0.62f, sr * 0.8f);
		V3 snout = head.add(fh2, HR * 0.72f + sl * 0.4f).add(uh, -HR * 0.26f);
		m.ellipsoid(snout, rh, uh, fh2, sr, sr * 0.82f, sLen, top, belly, 0f, 0xFF);
		float noseR = sr * (rg.ears() == 0 || rg.ears() == 2 ? 0.42f : 0.3f);
		m.glossySphere(snout.add(fh2, sLen * 0.92f).add(uh, sr * 0.38f), noseR, lk.nose(), 1.1f);
		// eyes
		boolean round = rg.round();
		for (int e = -1; e <= 1; e += 2) {
			V3 ec = head.add(fh2, HR * 0.7f).add(uh, HR * 0.2f).add(rh, e * HR * (round ? 0.48f : 0.42f));
			eye(ec, rh, uh, fh2, HR * (round ? 0.19f : 0.16f), lk.eye(), blink, rg.ears() == 1, e);
		}
		// ears
		for (int e = -1; e <= 1; e += 2) {
			switch (rg.ears()) {
				case 0 -> { // floppy (dog)
					float flop = (float) Math.sin(ph) * 0.12f * mv + air * 0.35f;
					V3 ax = rh.rotate(fh2, e * (0.3f + flop)), ay = uh.rotate(fh2, e * (0.3f + flop));
					V3 c = head.add(rh, e * HR * 0.86f).add(uh, -HR * 0.08f).add(fh2, -HR * 0.12f);
					m.ellipsoid(c, ax, ay, fh2, HR * 0.14f, HR * 0.55f, HR * 0.34f, lk.inner());
				}
				case 1, 2 -> { // pointy (cat, fox)
					boolean fox = rg.ears() == 2;
					V3 b = head.add(uh, HR * 0.58f).add(rh, e * HR * 0.48f).add(fh2, -HR * 0.06f);
					V3 tip = b.add(uh, HR * (fox ? 1.05f : 0.8f)).add(rh, e * HR * 0.16f).add(fh2, -HR * 0.08f);
					m.cone(b, tip, rh, HR * (fox ? 0.36f : 0.32f), HR * 0.11f, top, lk.inner(), fh2);
					if (fox) {
						m.cone(lerp(b, tip, 0.55f), tip.add(uh, HR * 0.02f), rh, HR * 0.18f, HR * 0.125f, lk.accent(), lk.accent(), null);
					}
				}
				case 3 -> { // long bunny ears
					float twitch = (float) Math.sin(t * 3 + e * 1.3f) * 0.08f;
					V3 dir = uh.add(fh2, -0.22f + twitch - air * 0.3f).add(rh, e * 0.18f).normalize();
					V3 b = head.add(uh, HR * 0.6f).add(rh, e * HR * 0.32f);
					float len = HR * 1.2f;
					V3 c = b.add(dir, len * 0.85f);
					V3 wdir = rh.sub(dir.mul(rh.dot(dir))).normalize();
					V3 thin = dir.cross(wdir).normalize();
					if (thin.dot(fh2) < 0) {
						thin = thin.mul(-1f);
					}
					m.ellipsoid(c, wdir, dir, thin, HR * 0.27f, len, HR * 0.1f, top);
					m.ellipsoid(c.add(thin, HR * 0.05f), wdir, dir, thin, HR * 0.16f, len * 0.8f, HR * 0.06f, lk.inner());
				}
				default -> // round hamster ears
						m.ellipsoid(head.add(uh, HR * 0.72f).add(rh, e * HR * 0.55f).add(fh2, -HR * 0.05f), rh, uh, fh2, HR * 0.22f, HR * 0.24f, HR * 0.08f, lk.inner());
			}
		}

		// legs: walk (diagonal pairs), sit, lie (paws forward), stretch out in the air
		float legR = W * (round ? 0.34f : 0.36f);
		for (int leg = 0; leg < 4; leg++) {
			boolean front = leg < 2;
			float sideS = leg % 2 == 0 ? -1f : 1f;
			V3 attach = (front ? sh : hip).add(r, sideS * W * 0.55f).add(bu, -H * 0.42f);
			float legPhase = ph + (((front ? 0 : 1) + (sideS > 0 ? 1 : 0)) % 2) * (float) Math.PI;
			float swing = (float) Math.sin(legPhase) * strideOf(pet.get()) * s * 0.28f * mv;
			float lift = Math.max(0f, (float) Math.cos(legPhase)) * 0.05f * s * mv;
			if (rg.hop()) {
				swing = front ? -0.02f * s : 0.03f * s;
				lift = hop * 0.35f;
			}
			V3 foot = base.add(f, (front ? L / 2f : -L / 2f) + swing).add(r, sideS * W * 0.62f).add(u, lift);
			if (!front) {
				foot = foot.add(f, L * 0.3f * st);
			}
			V3 lieFoot = front ? base.add(f, L / 2f + H * 1.1f).add(r, sideS * W * 0.5f).add(u, legR * 0.4f)
					: base.add(f, -L / 2f + H * 0.5f).add(r, sideS * W * 1.05f).add(u, legR * 0.5f);
			foot = lerp(foot, lieFoot, lie);
			V3 airFoot = attach.add(f, front ? Lg * 0.7f : -Lg * 0.8f).add(u, -Lg * 0.6f);
			foot = lerp(foot, airFoot, air);
			V3 knee = lerp(attach, foot, 0.5f).add(f, front ? 0.012f * s : -0.035f * s * (1f + st)).add(u, lie * legR * 0.6f);
			int upper = top, lower = lk.legs();
			m.tube(attach, knee, legR * 1.15f, legR * 0.95f, upper, lower);
			m.sphere(knee, legR * 0.95f, lower);
			V3 pawC = foot.add(u, legR * 0.55f);
			m.tube(knee, pawC, legR * 0.95f, legR * 0.8f, lower);
			float pawLen = (rg.hop() && !front) ? 2.3f : 1.35f;
			m.ellipsoid(pawC.add(f, legR * 0.3f * pawLen), r, u, f, legR * 1.1f, legR * 0.65f, legR * pawLen, lower);
		}

		// tail
		V3 tb = hip.add(sd, -H * 0.75f).add(bu, H * 0.35f);
		switch (rg.tail()) {
			case 0 -> { // dog: feathery, wags faster when happy
				float wag = (float) Math.sin(t * (mv > 0.3f ? 10 : 14)) * (0.35f + 0.4f * (1f - mv)) * (1f - 0.5f * lie);
				V3 dir = sd.mul(-0.6f).add(u, 0.8f).normalize().rotate(u, wag);
				V3 p0 = tb;
				for (int i = 0; i < 5; i++) {
					V3 p1 = p0.add(dir, 0.05f * s);
					blobAlong(p0, p1, (0.03f - 0.003f * i) * s, (0.026f - 0.002f * i) * s, top, belly);
					p0 = p1;
					dir = dir.add(sd, -0.25f).add(u, -0.06f).normalize();
				}
			}
			case 1 -> { // cat: long, curls slowly at the tip
				V3 dir = sd.mul(-0.85f).add(u, 0.35f).normalize();
				V3 p0 = tb;
				for (int i = 0; i < 8; i++) {
					float sway = (float) Math.sin(t * 1.6 - i * 0.5) * 0.22f;
					V3 p1 = p0.add(dir.rotate(u, sway), 0.042f * s);
					blobAlong(p0, p1, 0.016f * s, 0.016f * s, i > 5 ? lk.accent() : top, top);
					p0 = p1;
					dir = dir.add(u, 0.3f - 0.4f * lie).normalize();
				}
			}
			case 2 -> { // fox: big bushy tail with a white tip
				V3 dir = sd.mul(-0.9f).add(u, -0.05f + air * 0.3f).normalize().rotate(u, (float) Math.sin(t * 2) * 0.18f);
				V3 p0 = tb;
				for (int i = 0; i < 5; i++) {
					V3 p1 = p0.add(dir, 0.05f * s);
					float rad = (0.03f + 0.011f * Math.min(i, 3)) * s;
					blobAlong(p0, p1, rad, rad, i == 4 ? belly : top, i == 4 ? belly : top);
					p0 = p1;
					dir = dir.add(u, -0.07f).normalize();
				}
			}
			case 3 -> m.sphere(hip.add(sd, -H * 0.95f).add(bu, H * 0.25f), 0.045f * s, belly); // cotton tail
			default -> {
			}
		}
	}

	/** Budgie: blue body, yellow face, wings that flap (folded when it sits on you). */
	private void birdReal(V3 p, float yawDeg, float ph, float perched, float s, Look lk, boolean blink) {
		OrganicMesh m = mesh;
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		float tilt = 0.55f * perched;
		V3 bf = f.rotate(r, tilt), bu = r.cross(bf).normalize();
		V3 body = p.add(u, 0.055f * s * perched);
		m.ellipsoid(body, r, bu, bf, 0.048f * s, 0.05f * s, 0.075f * s, lk.top(), lk.belly(), 0.3f, 0xFF);
		V3 head = body.add(bf, 0.062f * s).add(bu, 0.042f * s).add(u, 0.01f * s);
		m.ellipsoid(head, r, u, f, 0.042f * s, 0.041f * s, 0.044f * s, lk.accent(), lk.accent(), 0.2f, 0xFF);
		m.cone(head.add(f, 0.034f * s).add(u, -0.004f * s), head.add(f, 0.066f * s).add(u, -0.026f * s), r, 0.013f * s, 0.012f * s, lk.nose(), lk.nose(), null);
		for (int e = -1; e <= 1; e += 2) {
			m.sphere(head.add(f, 0.026f * s).add(u, -0.02f * s).add(r, e * 0.032f * s), 0.01f * s, lk.inner());
			eye(head.add(f, 0.024f * s).add(u, 0.012f * s).add(r, e * 0.031f * s), r, u, f, 0.0095f * s, lk.eye(), blink, false, e);
			// wings
			float flap = perched > 0.5f ? 0f : (float) Math.sin(ph) * 0.95f + 0.2f;
			V3 root = body.add(r, e * 0.034f * s).add(bu, 0.024f * s);
			V3 spread = r.mul(e).rotate(f, -e * flap).add(f, -0.15f);
			V3 folded = bf.mul(-1f).add(r, e * 0.22f).add(bu, 0.08f);
			V3 wdir = lerp(spread.normalize(), folded.normalize(), perched).normalize();
			V3 wn = lerp(u.rotate(f, -e * flap), r.mul(e), perched).normalize();
			V3 wide = wn.cross(wdir).normalize();
			float wl = MathHelper.lerp(perched, 0.12f, 0.09f) * s;
			m.ellipsoid(root.add(wdir, wl * 0.55f), wide, wn, wdir, 0.045f * s, 0.009f * s, wl * 0.62f,
					Colors.mix(lk.top(), 0x14204A, 0.3f), lk.top(), 0.2f, 0xFF);
		}
		V3 tdir = bf.mul(-1f).add(bu, -0.35f).normalize();
		V3[] tf = frame(tdir);
		m.ellipsoid(body.add(tdir, 0.1f * s), tf[0], tf[1], tf[2], 0.02f * s, 0.007f * s, 0.065f * s, Colors.mix(lk.top(), 0x1A2A6A, 0.5f));
		if (perched > 0.3f) {
			for (int e = -1; e <= 1; e += 2) {
				m.tube(body.add(r, e * 0.018f * s).add(bu, -0.04f * s), p.add(r, e * 0.02f * s).add(f, 0.012f * s), 0.006f * s, 0.005f * s, lk.legs());
			}
		}
	}

	/** Honey bee: fuzzy thorax, striped abdomen, see-through wings that buzz. */
	private void beeReal(V3 p, float yawDeg, float ph, float perched, float s, Look lk) {
		OrganicMesh m = mesh;
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		V3 thorax = p.add(u, 0.035f * s * perched);
		int dark = lk.accent();
		m.ellipsoid(thorax, r, u, f, 0.034f * s, 0.033f * s, 0.036f * s, lk.belly(), Colors.mix(lk.belly(), 0x3A2A10, 0.4f), 0f, 0xFF);
		V3 abd = thorax.add(f, -0.062f * s).add(u, -0.008f * s);
		m.ellipsoid(abd, r, u, f, 0.04f * s, 0.038f * s, 0.055f * s, lk.top(), lk.top(), 0.2f, 0xFF);
		for (int k = 0; k < 3; k++) {
			m.ellipsoid(abd.add(f, (0.016f - 0.024f * k) * s), r, u, f, (0.0412f - 0.004f * k) * s, (0.0392f - 0.004f * k) * s, 0.0075f * s, dark);
		}
		m.cone(abd.add(f, -0.05f * s), abd.add(f, -0.076f * s), r, 0.008f * s, 0.008f * s, dark, dark, null);
		V3 head = thorax.add(f, 0.042f * s).add(u, 0.004f * s);
		m.ellipsoid(head, r, u, f, 0.028f * s, 0.027f * s, 0.022f * s, dark, dark, 0.3f, 0xFF);
		for (int e = -1; e <= 1; e += 2) {
			m.ellipsoid(head.add(r, e * 0.019f * s).add(f, 0.008f * s).add(u, 0.004f * s), r, u, f, 0.01f * s, 0.017f * s, 0.012f * s, lk.eye(), lk.eye(), 1.3f, 0xFF);
			V3 an = head.add(u, 0.018f * s).add(r, e * 0.008f * s);
			V3 tip = an.add(u, 0.035f * s).add(f, 0.03f * s).add(r, e * 0.016f * s);
			m.tube(an, tip, 0.0035f * s, 0.003f * s, dark);
			// wings: a blur of fast beats while flying, folded flat when sitting
			V3 root = thorax.add(u, 0.03f * s).add(r, e * 0.012f * s);
			int ghosts = perched > 0.5f ? 1 : 2;
			for (int k = 0; k < ghosts; k++) {
				float flap = perched > 0.5f ? 0f : (float) Math.sin(ph + k * 1.3f) * 0.75f + 0.25f;
				V3 wd = lerp(r.mul(e).rotate(f, -e * flap).add(f, -0.25f).normalize(), f.mul(-1f).add(r, e * 0.25f).normalize(), perched).normalize();
				V3 across = lerp(f, r.mul(e), perched);
				V3[] fore = {root, root.add(wd, 0.035f * s).add(across, 0.02f * s), root.add(wd, 0.082f * s).add(across, 0.006f * s),
						root.add(wd, 0.066f * s).add(across, -0.02f * s), root.add(wd, 0.02f * s).add(across, -0.016f * s)};
				float al = perched > 0.5f ? 0.55f : 0.32f;
				m.membrane(fore, 0xFFFFFF, 0xCFE6FF, al, al * 0.5f);
				V3 root2 = root.add(f, -0.012f * s);
				V3[] hind = {root2, root2.add(wd, 0.025f * s).add(across, 0.012f * s), root2.add(wd, 0.055f * s),
						root2.add(wd, 0.04f * s).add(across, -0.016f * s)};
				m.membrane(hind, 0xFFFFFF, 0xCFE6FF, al * 0.8f, al * 0.4f);
			}
		}
	}

	/** Morpho-style butterfly: bright wings with dark edges, slow open/close when it sits on you. */
	private void butterflyReal(GlowBatch g, V3 c, float yawDeg, float t, float perched, float s, Look lk, float a) {
		OrganicMesh m = mesh;
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		V3 body = c.add(u, 0.02f * s * perched);
		m.ellipsoid(body, r, u, f, 0.014f * s, 0.014f * s, 0.07f * s, lk.legs());
		V3 head = body.add(f, 0.075f * s);
		m.sphere(head, 0.017f * s, lk.legs());
		for (int e = -1; e <= 1; e += 2) {
			V3 tip = head.add(f, 0.07f * s).add(u, 0.06f * s).add(r, e * 0.035f * s);
			m.tube(head.add(u, 0.01f * s), tip, 0.003f * s, 0.0025f * s, lk.legs());
			m.sphere(tip, 0.0065f * s, lk.legs());
		}
		float flap = perched > 0.5f ? 0.35f + 0.9f * (0.5f + 0.5f * (float) Math.sin(t * 1.6f)) : 0.6f + 0.55f * (float) Math.sin(t * 14);
		for (int e = -1; e <= 1; e += 2) {
			V3 wd = r.mul(e).rotate(f, -e * flap);
			V3 root = body.add(f, 0.02f * s);
			V3[] fore = {root, root.add(f, 0.1f * s).add(wd, 0.05f * s), root.add(f, 0.09f * s).add(wd, 0.19f * s),
					root.add(f, 0.01f * s).add(wd, 0.24f * s), root.add(f, -0.03f * s).add(wd, 0.12f * s)};
			m.membrane(fore, lk.top(), lk.belly(), 0.95f, 0.95f);
			V3 root2 = body.add(f, -0.01f * s);
			V3[] hind = {root2, root2.add(wd, 0.12f * s).add(f, -0.02f * s), root2.add(wd, 0.16f * s).add(f, -0.1f * s),
					root2.add(wd, 0.08f * s).add(f, -0.15f * s), root2.add(wd, 0.02f * s).add(f, -0.08f * s)};
			m.membrane(hind, lk.top(), lk.belly(), 0.95f, 0.95f);
			V3 a1 = fore[2], a2 = fore[3];
			g.glowLine(a1.x(), a1.y(), a1.z(), a2.x(), a2.y(), a2.z(), 0.02f * s, lk.accent(), 0.35f * a);
		}
	}

	/** Koi: orange and white, a body that bends as it swims, see-through fins. */
	private void fishReal(V3 p, float yawDeg, float ph, float s, Look lk) {
		OrganicMesh m = mesh;
		V3 f = V3.forward(yawDeg), r = V3.right(yawDeg), u = V3.UP;
		float w1 = (float) Math.sin(ph * 2.2f) * 0.18f, w2 = (float) Math.sin(ph * 2.2f - 1.2f) * 0.38f;
		m.ellipsoid(p.add(f, 0.02f * s), r, u, f, 0.04f * s, 0.048f * s, 0.085f * s, lk.top(), lk.belly(), 0.6f, 0xFF);
		m.ellipsoid(p.add(f, 0.04f * s).add(u, 0.028f * s), r, u, f, 0.03f * s, 0.016f * s, 0.035f * s, lk.accent());
		V3 bdir = f.mul(-1f).rotate(u, w1);
		V3[] bf = frame(bdir);
		V3 mid2 = p.add(bdir, 0.075f * s);
		m.ellipsoid(mid2, bf[0], bf[1], bf[2], 0.028f * s, 0.034f * s, 0.06f * s, lk.top(), lk.belly(), 0.6f, 0xFF);
		V3 tdir = bdir.rotate(u, w2 - w1);
		V3 tb = mid2.add(bdir, 0.05f * s);
		V3[] tail = {tb, tb.add(tdir, 0.06f * s).add(u, 0.05f * s), tb.add(tdir, 0.11f * s).add(u, 0.07f * s), tb.add(tdir, 0.08f * s),
				tb.add(tdir, 0.11f * s).add(u, -0.07f * s), tb.add(tdir, 0.06f * s).add(u, -0.05f * s)};
		m.membrane(tail, lk.top(), lk.inner(), 0.85f, 0.4f);
		V3[] dorsal = {p.add(u, 0.045f * s).add(f, 0.02f * s), p.add(u, 0.08f * s).add(f, -0.01f * s), p.add(u, 0.058f * s).add(f, -0.08f * s),
				p.add(u, 0.035f * s).add(f, -0.07f * s)};
		m.membrane(dorsal, lk.top(), lk.inner(), 0.8f, 0.4f);
		for (int e = -1; e <= 1; e += 2) {
			V3 fr = p.add(f, 0.035f * s).add(r, e * 0.035f * s).add(u, -0.025f * s);
			float fl = (float) Math.sin(ph * 3 + e) * 0.012f * s;
			V3[] fin = {fr, fr.add(r, e * 0.045f * s).add(f, -0.02f * s).add(u, fl), fr.add(r, e * 0.03f * s).add(f, -0.05f * s).add(u, -0.01f * s)};
			m.membrane(fin, lk.top(), lk.inner(), 0.8f, 0.35f);
			eye(p.add(f, 0.085f * s).add(u, 0.014f * s).add(r, e * 0.03f * s), r, u, f, 0.011f * s, lk.eye(), false, false, e);
		}
		m.sphere(p.add(f, 0.105f * s).add(u, -0.006f * s), 0.012f * s, lk.nose());
	}

	// ------------------------------------------------------------------ light style

	private static V3 lerp(V3 a, V3 b, float t) {
		return new V3(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
	}

	private static void blob(GlowBatch g, V3 p, float r, int col, float a) {
		g.disc(p.x(), p.y(), p.z(), r * 2.1f, col, 0.26f * a, 14);
		g.disc(p.x(), p.y(), p.z(), r * 1.05f, col, 0.5f * a, 12);
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
			float w = 0.07f * s;
			attach.line(g, knee, w, c1, 0.9f * a);
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
