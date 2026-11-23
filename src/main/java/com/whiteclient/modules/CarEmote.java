package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.SolidBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.ActionSetting;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/**
 * Car emote (press N). "Show Off": a car parks right next to you so you can stand by it.
 * "Drive": you sit in it and it drives wherever you walk. Purely visual and client-side:
 * only you see it and your movement never changes. Press N again (or sneak while driving) to put it away.
 */
public class CarEmote extends Module {
	public static final String[] CARS = {"Sports", "Pickup", "Kart"};

	public final ModeSetting mode = add(new ModeSetting("Mode", "Show Off", "Show Off", "Drive"));
	public final ModeSetting car = add(new ModeSetting("Car", "Sports", CARS));
	public final ColorSetting color = add(new ColorSetting("Paint", 0x0F1014));
	public final BoolSetting lights = add(new BoolSetting("Headlights", true));
	public final BoolSetting underglow = add(new BoolSetting("Underglow", false));
	public final ColorSetting glowColor = add(new ColorSetting("Underglow Color", 0xFFFFFF).visibleWhen(underglow::isOn));
	public final BoolSetting thirdPerson = add(new BoolSetting("Third Person", true));
	public final NumberSetting cameraDistance = add(new NumberSetting("Camera Distance", 1.35, 1.0, 3.0, 0.05, "x"));
	public final BoolSetting sneakExit = add(new BoolSetting("Sneak To Get Out", true));
	public final ActionSetting toggleAction = add(new ActionSetting("Car", () -> active ? "Put Away" : "Spawn Car", this::toggleCar));

	private static boolean active;
	private float appear, pappear; // 0..1 spawn / despawn animation
	private boolean leaving;
	private boolean showOff;
	private Perspective savedPerspective;
	private double cx, cy, cz;
	private float yaw, pyaw, steer, psteer, wheel, pwheel;
	private boolean init;
	private final SolidBatch solid = new SolidBatch();

	public CarEmote() {
		super("Car Emote", "Press N for a car (only you see it)", Category.COSMETICS, true);
	}

	public static CarEmote get() {
		WhiteClient wc = WhiteClient.get();
		return wc == null ? null : wc.modules().get(CarEmote.class);
	}

	public static boolean active() {
		CarEmote c = get();
		return active && c != null && c.isEnabled();
	}

	/** Third-person camera distance multiplier, so the whole car fits on screen (used by a camera mixin). */
	public static float cameraScale() {
		CarEmote c = get();
		if (c == null || !active || !c.isEnabled()) {
			return 1f;
		}
		float k = Math.min(1f, c.appear * 1.5f);
		float target = c.cameraDistance.getFloat();
		if (c.car.is("Kart")) {
			target = 1f + (target - 1f) * 0.5f;
		}
		return 1f + (target - 1f) * k;
	}

	public void toggleCar() {
		if (active && !leaving) {
			stop();
		} else {
			start();
		}
	}

	public void start() {
		MinecraftClient mc = MinecraftClient.getInstance();
		active = true;
		leaving = false;
		init = false;
		showOff = mode.is("Show Off");
		appear = pappear = 0;
		if (thirdPerson.isOn() && mc.options != null) {
			Perspective cur = mc.options.getPerspective();
			Perspective want = showOff ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK;
			if (cur.isFirstPerson() || (showOff && cur != want)) {
				if (savedPerspective == null) {
					savedPerspective = cur;
				}
				mc.options.setPerspective(want);
			}
		}
	}

	public void stop() {
		leaving = true;
	}

	private void finish() {
		active = false;
		leaving = false;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (savedPerspective != null && mc.options != null) {
			mc.options.setPerspective(savedPerspective);
		}
		savedPerspective = null;
	}

	@Override
	protected void onDisable() {
		if (active) {
			finish();
		}
	}

	/** How far the seated player is moved up/down for the current car (Drive mode). */
	private float seatOffset() {
		return switch (car.get()) {
			case "Pickup" -> 0.3f;
			case "Kart" -> -0.48f;
			default -> -0.2f;
		};
	}

	/** Driver seat in car space {x, z}: the car is placed so this point is where you are. */
	private float[] seat() {
		return switch (car.get()) {
			case "Pickup" -> new float[]{-0.45f, 0.2f};
			case "Kart" -> new float[]{0f, -0.15f};
			default -> new float[]{-0.4f, -0.45f};
		};
	}

	/** Called by a mixin with the player's render state: sit pose + seat height (Drive mode only). */
	public static void applyPose(Entity entity, EntityRenderState state) {
		CarEmote c = get();
		if (c == null || !active || !c.isEnabled() || state == null || c.showOff) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (entity != mc.player) {
			return;
		}
		float k = c.appear;
		if (state instanceof BipedEntityRenderState b && k > 0.3f) {
			b.hasVehicle = true;
		}
		state.y += c.seatOffset() * Math.min(1f, k * 1.5f);
	}

	@Override
	public void onTick(MinecraftClient mc) {
		ClientPlayerEntity p = mc.player;
		if (!active) {
			return;
		}
		if (p == null || mc.world == null) {
			finish();
			return;
		}
		if (!leaving && mode.is("Show Off") != showOff) {
			start(); // mode changed in the menu
		}
		Vec3d pos = p.getEntityPos();
		if (!showOff && ((sneakExit.isOn() && p.isSneaking()) || p.hasVehicle() || p.isGliding() || p.isTouchingWater()
				|| p.getAbilities().flying || p.isSpectator())) {
			stop();
		}
		if (showOff && init && pos.squaredDistanceTo(cx, cy, cz) > 40 * 40) {
			stop();
		}
		pappear = appear;
		appear = leaving ? Math.max(0f, appear - 0.12f) : Math.min(1f, appear + 0.1f);
		if (leaving && appear <= 0f) {
			finish();
			return;
		}
		pyaw = yaw;
		psteer = steer;
		pwheel = wheel;
		float body = p.getBodyYaw();
		if (!init) {
			if (showOff) {
				// park beside you (on your right), angled a little like at a car meet
				float side = switch (car.get()) {
					case "Pickup" -> 2.2f;
					case "Kart" -> 1.25f;
					default -> 2.0f;
				};
				double r = Math.toRadians(body);
				double rx = -Math.cos(r), rz = -Math.sin(r), fx = -Math.sin(r), fz = Math.cos(r);
				cx = pos.x + rx * side - fx * 0.7;
				cz = pos.z + rz * side - fz * 0.7;
				yaw = body + 20f;
				cy = settle(mc, cx, cz, yaw, pos.y);
			} else {
				cx = pos.x;
				cy = pos.y;
				cz = pos.z;
				yaw = body;
			}
			pyaw = yaw;
			steer = psteer = 0;
			init = true;
		}
		if (showOff) {
			return;
		}
		float turn = MathHelper.wrapDegrees(body - yaw) * 0.35f;
		yaw += turn;
		steer += (MathHelper.clamp(turn * 2.5f, -30f, 30f) - steer) * 0.3f;
		// wheels roll forward / backward depending on the direction of travel
		double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
		double along = (pos.x - cx) * fx + (pos.z - cz) * fz;
		if (Math.abs(along) < 6) {
			wheel += (float) (along / wheelRadius());
		}
		cx = pos.x;
		cy = pos.y;
		cz = pos.z;
	}

	/** Ground height for a parked car: the highest ground under its four wheels (so it never sinks into a hill). */
	private double settle(MinecraftClient mc, double x, double z, float carYaw, double nearY) {
		float[] wheels = switch (car.get()) {
			case "Pickup" -> new float[]{0.9f, 1.85f};
			case "Kart" -> new float[]{0.66f, 0.7f};
			default -> new float[]{0.85f, 1.45f};
		};
		double r = Math.toRadians(carYaw);
		double c = Math.cos(r), sn = Math.sin(r);
		double best = Double.NEGATIVE_INFINITY;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				double lx = sx * wheels[0], lz = sz * wheels[1];
				double wx = x - c * lx - sn * lz, wz = z - sn * lx + c * lz;
				best = Math.max(best, groundAt(mc, wx, wz, nearY));
			}
		}
		if (!Double.isFinite(best)) {
			return nearY;
		}
		return Math.max(nearY - 3, Math.min(nearY + 2, best));
	}

	private static double groundAt(MinecraftClient mc, double x, double z, double nearY) {
		net.minecraft.util.math.BlockPos.Mutable m = new net.minecraft.util.math.BlockPos.Mutable();
		int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
		int top = MathHelper.floor(nearY + 2.2);
		for (int yy = top; yy >= top - 7; yy--) {
			m.set(bx, yy, bz);
			net.minecraft.util.shape.VoxelShape shape = mc.world.getBlockState(m).getCollisionShape(mc.world, m);
			if (!shape.isEmpty()) {
				return yy + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
			}
		}
		return nearY;
	}

	private float wheelRadius() {
		return switch (car.get()) {
			case "Pickup" -> 0.47f;
			case "Kart" -> 0.21f;
			default -> 0.35f;
		};
	}

	// ================================================================== materials + lighting

	private static final int PAINT = 0, GLASS = 1, TRIM = 2, CHROME = 3, RIM = 4, TYRE = 5, LEATHER = 6, BED = 7, LAMP = 8,
			RED_LAMP = 9, CALIPER = 10, ROTOR = 11, DARK_RIM = 12, TYRE2 = 13, AMBER = 14, PIANO = 15;
	private static final int[] MAT_RGB = {0, 0x0A0E15, 0x111215, 0xAEB3BC, 0x8A8F98, 0x121214, 0x232429, 0x18191C, 0xD9E0EA,
			0x9A121C, 0xC01C2A, 0x55585F, 0x2C2E34, 0x1B1B1E, 0xD98A1A, 0x08080A};
	private static final float[] MAT_R0 = {0.07f, 0.14f, 0.03f, 0.6f, 0.32f, 0f, 0.01f, 0.02f, 0.25f, 0.12f, 0.05f, 0.15f, 0.14f, 0f, 0.15f, 0.1f};
	private static final float[] MAT_F = {0.92f, 0.86f, 0.25f, 0.3f, 0.45f, 0.06f, 0.1f, 0.12f, 0.6f, 0.6f, 0.35f, 0.3f, 0.5f, 0.06f, 0.6f, 0.8f};
	private static final float[] MAT_S = {1.3f, 1.5f, 0.25f, 1.4f, 1.1f, 0.04f, 0.12f, 0.15f, 1.4f, 1.2f, 0.8f, 0.5f, 0.9f, 0.04f, 1.2f, 1.2f};
	private static final float[] MAT_P = {40, 70, 20, 80, 50, 8, 12, 12, 80, 60, 30, 20, 50, 8, 60, 60};
	/** Broad soft sheen (clear coat catching the sky light). */
	private static final float[] MAT_SHEEN = {0.16f, 0.1f, 0.03f, 0.12f, 0.1f, 0f, 0.02f, 0f, 0.05f, 0.05f, 0.05f, 0.03f, 0.06f, 0f, 0.05f, 0.08f};
	private static final float LX = 0.32f, LY = 0.86f, LZ = 0.4f;

	private int paintRgb;
	private int glassAlpha = 0xFF;
	private float light;
	private float ox, oy, oz, cosY, sinY, scale;
	private float wSpin, wSteer;
	private boolean roof;

	/**
	 * Glossy car lighting per vertex: diffuse sun + sun glint + the sky / ground reflected in the paint
	 * (stronger at grazing angles, like real clear coat). Everything is dimmed by the light level at night.
	 */
	private int lit(int m, float nx, float ny, float nz, float px, float py, float pz) {
		int rgb = m == PAINT ? paintRgb : MAT_RGB[m];
		float vl = (float) Math.sqrt(px * px + py * py + pz * pz);
		if (vl < 1.0e-4f) {
			vl = 1.0e-4f;
		}
		float vx = -px / vl, vy = -py / vl, vz = -pz / vl;
		float ndv = Math.max(0f, nx * vx + ny * vy + nz * vz);
		float rx = 2 * ndv * nx - vx, ry = 2 * ndv * ny - vy, rz = 2 * ndv * nz - vz;
		float ndl = Math.max(0f, nx * LX + ny * LY + nz * LZ);
		float rl = Math.max(0f, rx * LX + ry * LY + rz * LZ);
		float spec = MAT_S[m] * (float) Math.pow(rl, MAT_P[m]) + MAT_SHEEN[m] * (float) Math.pow(rl, 5);
		float c = 1f - Math.min(1f, ndv);
		float fres = c * c * c * c * c;
		float refl = Math.min(0.95f, MAT_R0[m] + MAT_F[m] * fres);
		float hz = smooth(-0.06f, 0.1f, ry);
		float skyK = 0.85f + 0.15f * Math.max(0f, ry);
		float er = 0.13f + (0.8f * skyK - 0.13f) * hz;
		float eg = 0.17f + (0.88f * skyK - 0.17f) * hz;
		float eb = 0.11f + (1.0f * skyK - 0.11f) * hz;
		float diff = 0.36f + 0.64f * ndl;
		float r = ((rgb >> 16) & 0xFF) / 255f * diff, g = ((rgb >> 8) & 0xFF) / 255f * diff, b = (rgb & 0xFF) / 255f * diff;
		r = (r * (1 - refl) + er * refl + spec) * light;
		g = (g * (1 - refl) + eg * refl + spec) * light;
		b = (b * (1 - refl) + eb * refl + spec) * light;
		int ir = (int) Math.min(255f, r * 255f), ig = (int) Math.min(255f, g * 255f), ib = (int) Math.min(255f, b * 255f);
		int a = m == GLASS ? glassAlpha : 0xFF;
		return (a << 24) | (ir << 16) | (ig << 8) | ib;
	}

	private static float smooth(float e0, float e1, float x) {
		float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
		return t * t * (3 - 2 * t);
	}

	// ================================================================== car space -> camera space

	/** Car-local (x right, y up, z forward) to camera-relative coordinates. */
	private float[] world(float lx, float ly, float lz) {
		// right = (-cos, 0, -sin), forward = (-sin, 0, cos) for Minecraft yaw
		float wx = -cosY * lx + -sinY * lz;
		float wz = -sinY * lx + cosY * lz;
		return new float[]{ox + wx * scale, oy + ly * scale, oz + wz * scale};
	}

	private float[] normal(float nx, float ny, float nz) {
		return new float[]{-cosY * nx + -sinY * nz, ny, -sinY * nx + cosY * nz};
	}

	private static float[] v(float x, float y, float z) {
		return new float[]{x, y, z};
	}

	/** Quad in car space with one material, lit with the given outward normal (0,0,0 = double-sided, e.g. glass). */
	private void quad(float[] a, float[] b, float[] c, float[] d, float nx, float ny, float nz, int m) {
		float[] wa = world(a[0], a[1], a[2]), wb = world(b[0], b[1], b[2]), wc = world(c[0], c[1], c[2]), wd = world(d[0], d[1], d[2]);
		boolean both = nx == 0 && ny == 0 && nz == 0;
		float[] n;
		float[] ln;
		if (both) {
			n = new float[]{0, 0, 0};
			float mx = (wa[0] + wc[0]) / 2, my = (wa[1] + wc[1]) / 2, mz = (wa[2] + wc[2]) / 2;
			float l = Math.max(1.0e-4f, (float) Math.sqrt(mx * mx + my * my + mz * mz));
			ln = new float[]{-mx / l, -my / l, -mz / l};
		} else {
			float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			n = normal(nx / l, ny / l, nz / l);
			ln = n;
		}
		solid.quad(wa[0], wa[1], wa[2], lit(m, ln[0], ln[1], ln[2], wa[0], wa[1], wa[2]),
				wb[0], wb[1], wb[2], lit(m, ln[0], ln[1], ln[2], wb[0], wb[1], wb[2]),
				wc[0], wc[1], wc[2], lit(m, ln[0], ln[1], ln[2], wc[0], wc[1], wc[2]),
				wd[0], wd[1], wd[2], lit(m, ln[0], ln[1], ln[2], wd[0], wd[1], wd[2]), n[0], n[1], n[2]);
	}

	/** Axis-aligned box in car space. */
	private void box(float x1, float y1, float z1, float x2, float y2, float z2, int m) {
		quad(v(x1, y1, z2), v(x2, y1, z2), v(x2, y2, z2), v(x1, y2, z2), 0, 0, 1, m);
		quad(v(x2, y1, z1), v(x1, y1, z1), v(x1, y2, z1), v(x2, y2, z1), 0, 0, -1, m);
		quad(v(x2, y1, z2), v(x2, y1, z1), v(x2, y2, z1), v(x2, y2, z2), 1, 0, 0, m);
		quad(v(x1, y1, z1), v(x1, y1, z2), v(x1, y2, z2), v(x1, y2, z1), -1, 0, 0, m);
		quad(v(x1, y2, z2), v(x2, y2, z2), v(x2, y2, z1), v(x1, y2, z1), 0, 1, 0, m);
		quad(v(x1, y1, z1), v(x2, y1, z1), v(x2, y1, z2), v(x1, y1, z2), 0, -1, 0, m);
	}

	/** Flat rectangle on a plane of constant z, facing +z (dir 1) or -z (dir -1). */
	private void zRect(float x1, float y1, float x2, float y2, float z, float dir, int m) {
		quad(v(x1, y1, z), v(x2, y1, z), v(x2, y2, z), v(x1, y2, z), 0, 0, dir, m);
	}

	/** Disc on a plane of constant z, facing dir. */
	private void zDisc(float x, float y, float z, float r, float dir, int m) {
		int seg = 12;
		float[] c = v(x, y, z);
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			quad(v(x + (float) Math.cos(a0) * r, y + (float) Math.sin(a0) * r, z), v(x + (float) Math.cos(a1) * r, y + (float) Math.sin(a1) * r, z), c, c, 0, 0, dir, m);
		}
	}

	// ================================================================== smooth lofted bodies

	/*
	 * A body is a list of cross-sections ("stations") from front to back. Each station is a rounded box:
	 * {z, bottom y, top y, half width at the bottom, half width at the top, top corner radius, bottom corner radius, crown}.
	 * The rings are connected into one smooth skin with per-vertex normals, so the paint shades and reflects like a real car.
	 */
	private static final int NB = 2, KC = 4, NS = 3, NT = 3;
	private static final int HALF = NB + KC + NS + KC + NT;
	private static final int RING = HALF * 2;
	/** Segment indices around half a ring: bottom, bottom corner, side, top corner, top. */
	private static final int SEG_SIDE = NB + KC, SEG_TOPARC = NB + KC + NS, SEG_TOP = NB + KC + NS + KC;

	private interface Material {
		/** Material for a body face (seg = -1 for the end caps). Return -1 to leave a hole. */
		int at(int seg, float x, float y, float z, float nx, float ny, float nz);
	}

	private static float[] radii(float[] s) {
		float h = s[2] - s[1];
		float rt = Math.min(Math.max(s[5], 0.015f), Math.min(s[4] * 0.95f, h * 0.6f));
		float rb = Math.min(Math.max(s.length > 6 ? s[6] : 0.04f, 0.01f), Math.min(s[3] * 0.95f, h * 0.35f));
		return new float[]{rt, rb};
	}

	private static float[][] ring(float[] s) {
		float z = s[0], yb = s[1], yt = s[2], wb = s[3], wt = s[4];
		float[] rr = radii(s);
		float rt = rr[0], rb = rr[1];
		float crown = s.length > 7 ? s[7] : 0f;
		float[][] r = new float[RING][];
		int k = 0;
		for (int i = 0; i < NB; i++) {
			r[k++] = v((wb - rb) * i / NB, yb, z);
		}
		for (int i = 0; i < KC; i++) {
			double a = -Math.PI / 2 + Math.PI / 2 * i / KC;
			r[k++] = v(wb - rb + (float) Math.cos(a) * rb, yb + rb + (float) Math.sin(a) * rb, z);
		}
		for (int i = 0; i < NS; i++) {
			float t = (float) i / NS;
			r[k++] = v(wb + (wt - wb) * t, yb + rb + (yt - rt - yb - rb) * t, z);
		}
		for (int i = 0; i < KC; i++) {
			double a = Math.PI / 2 * i / KC;
			r[k++] = v(wt - rt + (float) Math.cos(a) * rt, yt - rt + (float) Math.sin(a) * rt, z);
		}
		for (int i = 0; i < NT; i++) {
			float t = (float) i / NT;
			r[k++] = v((wt - rt) * (1 - t), yt + crown * t * (2 - t), z);
		}
		r[k++] = v(0, yt + crown, z);
		for (int i = HALF - 1; i >= 1; i--) {
			r[k++] = v(-r[i][0], r[i][1], z);
		}
		return r;
	}

	/** The station interpolated at z (for putting details flush on the body). */
	private static float[] stationAt(float[][] st, float z) {
		if (z >= st[0][0]) {
			return st[0];
		}
		for (int i = 0; i < st.length - 1; i++) {
			float z0 = st[i][0], z1 = st[i + 1][0];
			if (z <= z0 && z >= z1) {
				float t = z0 == z1 ? 0 : (z0 - z) / (z0 - z1);
				int n = Math.min(st[i].length, st[i + 1].length);
				float[] o = new float[n];
				for (int k = 0; k < n; k++) {
					o[k] = st[i][k] + (st[i + 1][k] - st[i][k]) * t;
				}
				return o;
			}
		}
		return st[st.length - 1];
	}

	/** Half width of the body side at (z, y). */
	private static float surfaceX(float[][] st, float z, float y) {
		float[] s = stationAt(st, z);
		float yb = s[1], yt = s[2], wb = s[3], wt = s[4];
		float[] rr = radii(s);
		float rt = rr[0], rb = rr[1];
		if (y <= yb + rb) {
			float dy = yb + rb - y;
			return wb - rb + (float) Math.sqrt(Math.max(0f, rb * rb - dy * dy));
		}
		if (y >= yt - rt) {
			float dy = y - (yt - rt);
			return wt - rt + (float) Math.sqrt(Math.max(0f, rt * rt - dy * dy));
		}
		float t = (y - yb - rb) / Math.max(1.0e-4f, yt - rt - yb - rb);
		return wb + (wt - wb) * t;
	}

	private static float[] crease(float[] vn, float fx, float fy, float fz) {
		return vn[0] * fx + vn[1] * fy + vn[2] * fz < 0.55f ? new float[]{fx, fy, fz} : vn;
	}

	/** Builds and draws a smooth body. Faces entirely below {@code hideBelow} are skipped. Returns the ring points. */
	private float[][][] loft(float[][] st, float hideBelow, boolean openBottom, Material mat) {
		int ns = st.length;
		float[][][] p = new float[ns][][];
		float[] mids = new float[ns];
		for (int i = 0; i < ns; i++) {
			p[i] = ring(st[i]);
			mids[i] = (st[i][1] + st[i][2]) / 2f;
		}
		float[][][] nrm = new float[ns][RING][];
		for (int i = 0; i < ns; i++) {
			int ia = Math.max(0, i - 1), ib = Math.min(ns - 1, i + 1);
			for (int j = 0; j < RING; j++) {
				float[] a = p[i][(j + RING - 1) % RING], b = p[i][(j + 1) % RING];
				float[] c = p[ia][j], d = p[ib][j];
				float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
				float wx = d[0] - c[0], wy = d[1] - c[1], wz = d[2] - c[2];
				float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
				float rx = p[i][j][0], ry = p[i][j][1] - mids[i];
				if (nx * rx + ny * ry < 0) {
					nx = -nx;
					ny = -ny;
					nz = -nz;
				}
				float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				nrm[i][j] = l < 1.0e-6f ? v(0, ry >= 0 ? 1 : -1, 0) : v(nx / l, ny / l, nz / l);
			}
		}
		for (int i = 0; i < ns - 1; i++) {
			for (int j = 0; j < RING; j++) {
				int j2 = (j + 1) % RING;
				float[] a = p[i][j], b = p[i][j2], c = p[i + 1][j2], d = p[i + 1][j];
				if (Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1])) < hideBelow) {
					continue;
				}
				float ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
				float wx = d[0] - b[0], wy = d[1] - b[1], wz = d[2] - b[2];
				float fx = uy * wz - uz * wy, fy = uz * wx - ux * wz, fz = ux * wy - uy * wx;
				float[] na = nrm[i][j], nb = nrm[i][j2], nc = nrm[i + 1][j2], nd = nrm[i + 1][j];
				float sx = na[0] + nb[0] + nc[0] + nd[0], sy = na[1] + nb[1] + nc[1] + nd[1], sz = na[2] + nb[2] + nc[2] + nd[2];
				if (fx * sx + fy * sy + fz * sz < 0) {
					fx = -fx;
					fy = -fy;
					fz = -fz;
				}
				float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
				if (fl < 1.0e-7f) {
					continue;
				}
				fx /= fl;
				fy /= fl;
				fz /= fl;
				if (openBottom && fy < -0.85f) {
					continue;
				}
				int seg = j < HALF ? j : RING - 1 - j;
				int m = mat.at(seg, (a[0] + b[0] + c[0] + d[0]) / 4, (a[1] + b[1] + c[1] + d[1]) / 4, (a[2] + b[2] + c[2] + d[2]) / 4, fx, fy, fz);
				if (m < 0) {
					continue;
				}
				emitSmooth(new float[][]{a, b, c, d}, new float[][]{crease(na, fx, fy, fz), crease(nb, fx, fy, fz), crease(nc, fx, fy, fz), crease(nd, fx, fy, fz)}, m, fx, fy, fz);
			}
		}
		for (int end = 0; end < 2; end++) {
			int i = end == 0 ? 0 : ns - 1;
			float dir = end == 0 ? 1f : -1f;
			float[] center = v(0, mids[i], st[i][0]);
			for (int j = 0; j < RING; j++) {
				float[] a = p[i][j], b = p[i][(j + 1) % RING];
				if (Math.max(a[1], b[1]) < hideBelow) {
					continue;
				}
				int m = mat.at(-1, (a[0] + b[0]) / 3, (a[1] + b[1] + center[1]) / 3, center[2], 0, 0, dir);
				if (m < 0) {
					continue;
				}
				quad(a, b, center, center, 0, 0, dir, m);
			}
		}
		return p;
	}

	private void emitSmooth(float[][] q, float[][] qn, int m, float fnx, float fny, float fnz) {
		float[][] w = new float[4][];
		int[] c = new int[4];
		for (int k = 0; k < 4; k++) {
			w[k] = world(q[k][0], q[k][1], q[k][2]);
			float[] n = normal(qn[k][0], qn[k][1], qn[k][2]);
			c[k] = lit(m, n[0], n[1], n[2], w[k][0], w[k][1], w[k][2]);
		}
		float[] fn = normal(fnx, fny, fnz);
		solid.quad(w[0][0], w[0][1], w[0][2], c[0], w[1][0], w[1][1], w[1][2], c[1], w[2][0], w[2][1], w[2][2], c[2],
				w[3][0], w[3][1], w[3][2], c[3], fn[0], fn[1], fn[2]);
	}

	/** A thin line running up the body side (door gaps, pillars). */
	private void sideLineV(float[][] st, int side, float z, float y1, float y2, float halfWidth, int m) {
		int n = 6;
		for (int i = 0; i < n; i++) {
			float ya = y1 + (y2 - y1) * i / n, yb = y1 + (y2 - y1) * (i + 1) / n;
			float xa = side * (surfaceX(st, z, ya) + 0.006f), xb = side * (surfaceX(st, z, yb) + 0.006f);
			float tx = xb - xa, ty = yb - ya;
			quad(v(xa, ya, z + halfWidth), v(xa, ya, z - halfWidth), v(xb, yb, z - halfWidth), v(xb, yb, z + halfWidth), side * ty, -side * tx, 0, m);
		}
	}

	/** A thin line running along the body side at a height (trim strips). */
	private void sideLineH(float[][] st, int side, float y, float z1, float z2, float halfHeight, int m) {
		int n = Math.max(2, (int) (Math.abs(z2 - z1) / 0.35f));
		for (int i = 0; i < n; i++) {
			float za = z1 + (z2 - z1) * i / n, zb = z1 + (z2 - z1) * (i + 1) / n;
			float xa1 = side * (surfaceX(st, za, y - halfHeight) + 0.006f), xa2 = side * (surfaceX(st, za, y + halfHeight) + 0.006f);
			float xb1 = side * (surfaceX(st, zb, y - halfHeight) + 0.006f), xb2 = side * (surfaceX(st, zb, y + halfHeight) + 0.006f);
			quad(v(xa1, y - halfHeight, za), v(xb1, y - halfHeight, zb), v(xb2, y + halfHeight, zb), v(xa2, y + halfHeight, za), side, 0.2f, 0, m);
		}
	}

	/** A strip along the side lit as if it faced upwards: reads as a sharp crease catching the sky. */
	private void crease(float[][] st, int side, float y, float z1, float z2, float halfHeight) {
		int n = Math.max(2, (int) (Math.abs(z2 - z1) / 0.3f));
		for (int i = 0; i < n; i++) {
			float za = z1 + (z2 - z1) * i / n, zb = z1 + (z2 - z1) * (i + 1) / n;
			float xa1 = side * (surfaceX(st, za, y - halfHeight) + 0.004f), xa2 = side * (surfaceX(st, za, y + halfHeight) + 0.004f);
			float xb1 = side * (surfaceX(st, zb, y - halfHeight) + 0.004f), xb2 = side * (surfaceX(st, zb, y + halfHeight) + 0.004f);
			quad(v(xa1, y - halfHeight, za), v(xb1, y - halfHeight, zb), v(xb2, y + halfHeight, zb), v(xa2, y + halfHeight, za), side, 1.6f, 0, PAINT);
		}
	}

	/** A small flat patch on the body side (vents, handles, side lamps). */
	private void sidePatch(float[][] st, int side, float z1, float z2, float y1, float y2, float lift, int m) {
		float[] a = v(side * (surfaceX(st, z1, y1) + lift), y1, z1);
		float[] b = v(side * (surfaceX(st, z2, y1) + lift), y1, z2);
		float[] c = v(side * (surfaceX(st, z2, y2) + lift), y2, z2);
		float[] d = v(side * (surfaceX(st, z1, y2) + lift), y2, z1);
		quad(a, b, c, d, side, 0.15f, 0, m);
	}

	/** Dark wheel arch wrapped onto the body side. */
	private void arch(float[][] st, int side, float y, float z, float r) {
		int seg = 18;
		float[] fr = {0f, 0.4f, 0.75f, 1f};
		float[][][] pts = new float[fr.length][seg + 1][];
		for (int k = 0; k < fr.length; k++) {
			for (int i = 0; i <= seg; i++) {
				double a = Math.PI * 2 * i / seg;
				float pz = z + (float) Math.cos(a) * r * fr[k];
				float minY = stationAt(st, pz)[1] + 0.015f;
				float py = Math.max(minY, y + (float) Math.sin(a) * r * fr[k]);
				pts[k][i] = v(side * (surfaceX(st, pz, py) + 0.004f), py, pz);
			}
		}
		for (int k = 0; k < fr.length - 1; k++) {
			for (int i = 0; i < seg; i++) {
				quad(pts[k][i], pts[k][i + 1], pts[k + 1][i + 1], pts[k + 1][i], side, 0, 0, PIANO);
			}
		}
	}

	/** Soft contact shadow under the car (translucent black, fades at the edges). */
	private void shadow(float halfLen, float halfW) {
		int seg = 28;
		float[] c = world(0, 0.015f, 0);
		float[][] inner = new float[seg][], outer = new float[seg][];
		for (int i = 0; i < seg; i++) {
			double a = Math.PI * 2 * i / seg;
			float co = (float) Math.cos(a), si = (float) Math.sin(a);
			float ex = Math.signum(co) * (float) Math.sqrt(Math.abs(co)), ez = Math.signum(si) * (float) Math.sqrt(Math.abs(si));
			inner[i] = world(ex * halfW * 0.8f, 0.015f, ez * halfLen * 0.85f);
			outer[i] = world(ex * (halfW + 0.3f), 0.015f, ez * (halfLen + 0.35f));
		}
		int ci = 0x9A000000, mi = 0x70000000, oe = 0x00000000;
		for (int i = 0; i < seg; i++) {
			int j = (i + 1) % seg;
			solid.quad(c[0], c[1], c[2], ci, inner[i][0], inner[i][1], inner[i][2], mi, inner[j][0], inner[j][1], inner[j][2], mi,
					c[0], c[1], c[2], ci, 0, 1, 0);
			solid.quad(inner[i][0], inner[i][1], inner[i][2], mi, outer[i][0], outer[i][1], outer[i][2], oe,
					outer[j][0], outer[j][1], outer[j][2], oe, inner[j][0], inner[j][1], inner[j][2], mi, 0, 1, 0);
		}
	}

	// ================================================================== wheels

	/** A point on a wheel: o = offset along the axle, a = angle (0 = forward), rr = radius. */
	private float[] wp(float x, float y, float z, float o, double a, float rr, float cs, float sn) {
		float pz = (float) Math.cos(a) * rr, py = (float) Math.sin(a) * rr;
		return v(x + o * cs + pz * sn, y + py, z + pz * cs - o * sn);
	}

	/**
	 * Wheel with a rounded tyre, concave spoked rim, brake disc and caliper.
	 * side -1 = left, +1 = right. spokes = number of spoke pairs if {@code twin}.
	 */
	private void wheel(float x, float y, float z, float r, float w, int side, boolean steering, int spokes, boolean twin,
					   int rimMat, boolean caliper, boolean knobby) {
		int seg = 22;
		float steerRad = steering ? (float) Math.toRadians(wSteer) : 0f;
		float cs = (float) Math.cos(steerRad), sn = (float) Math.sin(steerRad);
		float ax = side * cs, az = -side * sn; // outward axle direction
		float hw = w / 2f, sh = Math.min(0.04f, r * 0.12f);
		float rim = r * 0.68f;
		double spin = -wSpin;
		// tread + rounded shoulders
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg + spin, a1 = Math.PI * 2 * (i + 1) / seg + spin, am = (a0 + a1) / 2;
			float ny = (float) Math.sin(am), nf = (float) Math.cos(am);
			float rnx = nf * sn, rnz = nf * cs;
			int tm = knobby && (i % 2 == 0) ? TYRE2 : TYRE;
			quad(wp(x, y, z, hw - sh, a0, r, cs, sn), wp(x, y, z, hw - sh, a1, r, cs, sn), wp(x, y, z, -hw + sh, a1, r, cs, sn), wp(x, y, z, -hw + sh, a0, r, cs, sn), rnx, ny, rnz, tm);
			for (int s = -1; s <= 1; s += 2) {
				float o1 = s * (hw - sh), o2 = s * hw;
				float sx = s * side;
				quad(wp(x, y, z, o2, a0, r - sh, cs, sn), wp(x, y, z, o2, a1, r - sh, cs, sn), wp(x, y, z, o1, a1, r, cs, sn), wp(x, y, z, o1, a0, r, cs, sn),
						rnx + ax * sx, ny, rnz + az * sx, TYRE);
			}
		}
		// outer sidewall (tyre ring) and inner sidewall
		float oo = side * hw;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg + spin, a1 = Math.PI * 2 * (i + 1) / seg + spin;
			quad(wp(x, y, z, oo, a0, r - sh, cs, sn), wp(x, y, z, oo, a1, r - sh, cs, sn), wp(x, y, z, oo, a1, rim, cs, sn), wp(x, y, z, oo, a0, rim, cs, sn), ax, 0, az, TYRE);
			quad(wp(x, y, z, -oo, a1, r - sh, cs, sn), wp(x, y, z, -oo, a0, r - sh, cs, sn), wp(x, y, z, -oo, a0, 0, cs, sn), wp(x, y, z, -oo, a1, 0, cs, sn), -ax, 0, -az, TYRE);
			// rim lip
			quad(wp(x, y, z, oo - side * 0.004f, a0, rim, cs, sn), wp(x, y, z, oo - side * 0.004f, a1, rim, cs, sn),
					wp(x, y, z, oo - side * 0.004f, a1, rim * 0.9f, cs, sn), wp(x, y, z, oo - side * 0.004f, a0, rim * 0.9f, cs, sn), ax, 0, az, rimMat);
			// dark barrel behind the spokes
			float ob = oo - side * 0.07f;
			quad(wp(x, y, z, ob, a0, rim * 0.92f, cs, sn), wp(x, y, z, ob, a1, rim * 0.92f, cs, sn), wp(x, y, z, ob, a1, 0, cs, sn), wp(x, y, z, ob, a0, 0, cs, sn), ax, 0, az, PIANO);
		}
		// brake disc (does not need to spin, it is round) + caliper (stays still)
		float od = oo - side * 0.055f;
		for (int i = 0; i < 16; i++) {
			double a0 = Math.PI * 2 * i / 16, a1 = Math.PI * 2 * (i + 1) / 16;
			quad(wp(x, y, z, od, a0, rim * 0.78f, cs, sn), wp(x, y, z, od, a1, rim * 0.78f, cs, sn), wp(x, y, z, od, a1, rim * 0.3f, cs, sn), wp(x, y, z, od, a0, rim * 0.3f, cs, sn), ax, 0, az, ROTOR);
		}
		if (caliper) {
			float oc = oo - side * 0.045f;
			for (int i = 0; i < 4; i++) {
				double a0 = Math.PI * (0.58 + 0.07 * i), a1 = Math.PI * (0.58 + 0.07 * (i + 1));
				quad(wp(x, y, z, oc, a0, rim * 0.86f, cs, sn), wp(x, y, z, oc, a1, rim * 0.86f, cs, sn), wp(x, y, z, oc, a1, rim * 0.56f, cs, sn), wp(x, y, z, oc, a0, rim * 0.56f, cs, sn), ax, 0, az, CALIPER);
			}
		}
		// spokes: concave, from the hub out to the rim lip
		float oHub = oo - side * 0.04f, oRim = oo - side * 0.008f;
		int count = twin ? spokes * 2 : spokes;
		for (int k = 0; k < count; k++) {
			double base = Math.PI * 2 * (twin ? k / 2 : k) / spokes + spin;
			double a = twin ? base + (k % 2 == 0 ? -0.11 : 0.11) : base;
			double hwHub = twin ? 0.07 : 0.13, hwRim = twin ? 0.06 : 0.11;
			quad(wp(x, y, z, oHub, a - hwHub, rim * 0.22f, cs, sn), wp(x, y, z, oHub, a + hwHub, rim * 0.22f, cs, sn),
					wp(x, y, z, oRim, a + hwRim, rim * 0.9f, cs, sn), wp(x, y, z, oRim, a - hwRim, rim * 0.9f, cs, sn), ax, 0, az, rimMat);
		}
		// hub + centre cap
		for (int i = 0; i < 12; i++) {
			double a0 = Math.PI * 2 * i / 12 + spin, a1 = Math.PI * 2 * (i + 1) / 12 + spin;
			quad(wp(x, y, z, oHub + side * 0.004f, a0, rim * 0.24f, cs, sn), wp(x, y, z, oHub + side * 0.004f, a1, rim * 0.24f, cs, sn),
					wp(x, y, z, oHub + side * 0.004f, a1, 0, cs, sn), wp(x, y, z, oHub + side * 0.004f, a0, 0, cs, sn), ax, 0, az, rimMat);
			quad(wp(x, y, z, oHub + side * 0.008f, a0, rim * 0.1f, cs, sn), wp(x, y, z, oHub + side * 0.008f, a1, rim * 0.1f, cs, sn),
					wp(x, y, z, oHub + side * 0.008f, a1, 0, cs, sn), wp(x, y, z, oHub + side * 0.008f, a0, 0, cs, sn), ax, 0, az, PIANO);
		}
	}

	/** A steering wheel ring tilted towards the driver. */
	private void ringWheel(float x, float y, float z, float r) {
		int seg = 16;
		double tilt = Math.toRadians(25);
		float ct = (float) Math.cos(tilt), stt = (float) Math.sin(tilt);
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			float ri = r * 0.8f;
			float[] p0o = v(x + (float) Math.cos(a0) * r, y + (float) Math.sin(a0) * r * ct, z + (float) Math.sin(a0) * r * stt);
			float[] p1o = v(x + (float) Math.cos(a1) * r, y + (float) Math.sin(a1) * r * ct, z + (float) Math.sin(a1) * r * stt);
			float[] p0i = v(x + (float) Math.cos(a0) * ri, y + (float) Math.sin(a0) * ri * ct, z + (float) Math.sin(a0) * ri * stt);
			float[] p1i = v(x + (float) Math.cos(a1) * ri, y + (float) Math.sin(a1) * ri * ct, z + (float) Math.sin(a1) * ri * stt);
			quad(p0o, p1o, p1i, p0i, 0, 0, 0, TRIM);
		}
		quad(v(x - r * 0.8f, y - 0.015f, z), v(x + r * 0.8f, y - 0.015f, z), v(x + r * 0.8f, y + 0.015f, z), v(x - r * 0.8f, y + 0.015f, z), 0, 0, 0, TRIM);
	}

	// ================================================================== drawing

	@Override
	public void renderWorld(WorldContext ctx) {
		if (!active || !init || ctx.client.player == null || ctx.client.world == null) {
			return;
		}
		float td = ctx.tickDelta;
		float ap = MathHelper.lerp(td, pappear, appear);
		if (ap <= 0.01f) {
			return;
		}
		float y = pyaw + MathHelper.wrapDegrees(yaw - pyaw) * td;
		wSteer = MathHelper.lerp(td, psteer, steer);
		wSpin = MathHelper.lerp(td, pwheel, wheel);
		double r = Math.toRadians(y);
		cosY = (float) Math.cos(r);
		sinY = (float) Math.sin(r);
		float ease = 1f - (1f - ap) * (1f - ap) * (1f - ap);
		scale = 0.6f + 0.4f * ease;
		roof = showOff;
		glassAlpha = showOff ? 0xFF : 0x8C;

		double baseX, baseY, baseZ;
		if (showOff) {
			baseX = cx;
			baseY = cy;
			baseZ = cz;
			ox = ctx.rx(cx);
			oy = ctx.ry(cy);
			oz = ctx.rz(cz);
		} else {
			// the car is placed so the driver's seat is where you are
			Vec3d pl = ctx.client.player.getLerpedPos(td);
			float[] seat = seat();
			float sx = -cosY * seat[0] + -sinY * seat[1];
			float sz = -sinY * seat[0] + cosY * seat[1];
			baseX = pl.x;
			baseY = pl.y;
			baseZ = pl.z;
			ox = ctx.rx(pl.x) - sx * scale;
			oy = ctx.ry(pl.y);
			oz = ctx.rz(pl.z) - sz * scale;
		}

		// light level at the car: sky light dims at night, block light (torches) stays
		BlockPos bp = BlockPos.ofFloored(baseX, baseY + 0.5, baseZ);
		long time = ctx.client.world.getTimeOfDay() % 24000L;
		float day = 0.5f + 0.5f * (float) Math.cos((time - 6000L) / 24000.0 * Math.PI * 2);
		float sky = ctx.client.world.getLightLevel(LightType.SKY, bp) / 15f * (0.16f + 0.84f * day);
		float block = ctx.client.world.getLightLevel(LightType.BLOCK, bp) / 15f;
		light = 0.2f + 0.8f * Math.max(sky, block);
		paintRgb = color.rgb();

		switch (car.get()) {
			case "Pickup" -> pickup();
			case "Kart" -> kart();
			default -> sports();
		}
		solid.draw(ctx.consumers, ctx.matrices.peek().getPositionMatrix());
		glowParts(ctx.glow, ap);
	}

	// ---------------------------------------------------------------- sports coupe (long hood, low fastback roof)

	private static final float[][] SPORTS_BODY = {
			// z, bottom, top, half width bottom, half width top, top radius, bottom radius, crown
			{2.45f, 0.21f, 0.54f, 0.74f, 0.64f, 0.11f, 0.08f, 0f},
			{2.4f, 0.16f, 0.61f, 0.85f, 0.76f, 0.16f, 0.08f, 0.02f},
			{2.32f, 0.14f, 0.66f, 0.9f, 0.83f, 0.19f, 0.07f, 0.03f},
			{2.2f, 0.13f, 0.71f, 0.93f, 0.87f, 0.2f, 0.07f, 0.03f},
			{1.85f, 0.13f, 0.77f, 0.955f, 0.91f, 0.2f, 0.07f, 0.04f},
			{1.5f, 0.13f, 0.8f, 0.96f, 0.92f, 0.2f, 0.07f, 0.04f},
			{1.1f, 0.13f, 0.83f, 0.955f, 0.89f, 0.24f, 0.07f, 0.04f},
			{0.7f, 0.13f, 0.86f, 0.95f, 0.86f, 0.26f, 0.07f, 0.03f},
			{0.2f, 0.13f, 0.89f, 0.95f, 0.86f, 0.26f, 0.07f, 0.02f},
			{-0.4f, 0.13f, 0.91f, 0.955f, 0.87f, 0.27f, 0.07f, 0.02f},
			{-0.95f, 0.13f, 0.93f, 0.97f, 0.9f, 0.26f, 0.07f, 0.02f},
			{-1.4f, 0.13f, 0.94f, 0.98f, 0.92f, 0.24f, 0.07f, 0.02f},
			{-1.85f, 0.14f, 0.94f, 0.97f, 0.9f, 0.25f, 0.07f, 0.02f},
			{-2.2f, 0.18f, 0.93f, 0.93f, 0.84f, 0.24f, 0.08f, 0.01f},
			{-2.4f, 0.24f, 0.9f, 0.86f, 0.78f, 0.18f, 0.08f, 0f},
			{-2.46f, 0.3f, 0.86f, 0.79f, 0.72f, 0.12f, 0.08f, 0f}
	};

	private static final float[][] SPORTS_GLASS = {
			{0.8f, 0.78f, 0.875f, 0.8f, 0.76f, 0.03f, 0.02f},
			{0.52f, 0.78f, 1.09f, 0.82f, 0.68f, 0.12f, 0.02f},
			{0.2f, 0.79f, 1.27f, 0.83f, 0.63f, 0.16f, 0.02f, 0.03f},
			{-0.15f, 0.8f, 1.345f, 0.83f, 0.61f, 0.17f, 0.02f, 0.03f},
			{-0.55f, 0.81f, 1.36f, 0.83f, 0.6f, 0.17f, 0.02f, 0.03f},
			{-0.95f, 0.82f, 1.32f, 0.82f, 0.58f, 0.17f, 0.02f, 0.03f},
			{-1.38f, 0.83f, 1.19f, 0.8f, 0.55f, 0.15f, 0.02f, 0.02f},
			{-1.78f, 0.84f, 1.05f, 0.76f, 0.52f, 0.11f, 0.02f, 0.01f},
			{-2.06f, 0.85f, 0.955f, 0.7f, 0.5f, 0.04f, 0.02f}
	};

	private void sports() {
		shadow(2.45f, 0.98f);
		final boolean open = !roof;
		loft(SPORTS_BODY, -1f, false, (seg, x, y, z, nx, ny, nz) -> {
			if (seg == -1) {
				return PAINT;
			}
			if (seg < SEG_SIDE) {
				return TRIM; // underbody + black side sills
			}
			if (open && seg >= SEG_TOP && z < 0.62f && z > -1.0f) {
				return LEATHER; // open cockpit (convertible while driving)
			}
			return PAINT;
		});
		if (roof) {
			float[][][] g = loft(SPORTS_GLASS, -1f, true, (seg, x, y, z, nx, ny, nz) -> {
				if (seg == -1) {
					return z < 0 ? PAINT : -1;
				}
				if (seg < SEG_SIDE) {
					return -1;
				}
				if (seg == SEG_SIDE) {
					return PAINT; // belt line
				}
				boolean roofZone = z < 0.12f && z > -1.15f;
				if (seg < SEG_TOPARC) {
					return z < -1.62f ? PAINT : GLASS; // side windows, painted C-pillar at the back
				}
				if (seg < SEG_TOP) {
					return seg - SEG_TOPARC >= 2 || !roofZone ? PAINT : GLASS; // A / C pillars and roof edge
				}
				return roofZone ? PAINT : GLASS; // roof, windshield, rear window
			});
			// chrome window line
			for (int s = -1; s <= 1; s += 2) {
				for (int i = 0; i < g.length - 1; i++) {
					float[] a = g[i][SEG_SIDE + 1], b = g[i + 1][SEG_SIDE + 1];
					if (a[2] < -1.62f) {
						continue;
					}
					float bz = Math.max(b[2], -1.62f);
					float t = (a[2] - bz) / Math.max(1.0e-4f, a[2] - b[2]);
					float bx = a[0] + (b[0] - a[0]) * t, by = a[1] + (b[1] - a[1]) * t;
					float ax2 = s * (a[0] + 0.006f), bx2 = s * (bx + 0.006f);
					quad(v(ax2, a[1] - 0.012f, a[2]), v(bx2, by - 0.012f, bz), v(bx2, by + 0.012f, bz), v(ax2, a[1] + 0.012f, a[2]), s, 0.3f, 0, CHROME);
				}
			}
		} else {
			// convertible: windshield with frame, seats, dashboard, steering wheel
			quad(v(-0.72f, 0.83f, 0.8f), v(0.72f, 0.83f, 0.8f), v(0.62f, 1.18f, 0.42f), v(-0.62f, 1.18f, 0.42f), 0, 0, 0, GLASS);
			box(-0.64f, 1.16f, 0.38f, 0.64f, 1.2f, 0.45f, TRIM);
			for (int s = -1; s <= 1; s += 2) {
				quad(v(s * 0.72f, 0.83f, 0.8f), v(s * 0.76f, 0.83f, 0.8f), v(s * 0.66f, 1.2f, 0.41f), v(s * 0.62f, 1.2f, 0.41f), 0, 0, 0, TRIM);
				float xc = s * 0.4f;
				box(xc - 0.21f, 0.85f, -0.8f, xc + 0.21f, 1.26f, -0.64f, LEATHER);
				box(xc - 0.12f, 1.26f, -0.78f, xc + 0.12f, 1.42f, -0.66f, LEATHER);
			}
			box(-0.66f, 0.86f, 0.45f, 0.66f, 0.97f, 0.66f, TRIM);
			ringWheel(-0.4f, 1.03f, 0.27f, 0.16f);
		}
		for (int s = -1; s <= 1; s += 2) {
			// door gaps, handle, side vent, mirrors
			sideLineV(SPORTS_BODY, s, 0.64f, 0.2f, 0.86f, 0.006f, PIANO);
			sideLineV(SPORTS_BODY, s, -0.92f, 0.22f, 0.9f, 0.006f, PIANO);
			sidePatch(SPORTS_BODY, s, -0.58f, -0.76f, 0.79f, 0.815f, 0.008f, CHROME);
			sidePatch(SPORTS_BODY, s, 1.02f, 0.78f, 0.52f, 0.6f, 0.007f, PIANO);
			sideLineH(SPORTS_BODY, s, 0.54f, 1.0f, 0.78f, 0.004f, CHROME);
			crease(SPORTS_BODY, s, 0.66f, 2.05f, -2.0f, 0.012f);
			float mx = s > 0 ? 0.86f : -1.08f;
			box(s > 0 ? 0.78f : -0.88f, 0.9f, 0.53f, s > 0 ? 0.88f : -0.78f, 0.93f, 0.57f, PIANO);
			box(mx, 0.9f, 0.48f, mx + 0.22f, 0.99f, 0.6f, PAINT);
			quad(v(mx, 0.9f, 0.478f), v(mx + 0.22f, 0.9f, 0.478f), v(mx + 0.22f, 0.99f, 0.478f), v(mx, 0.99f, 0.478f), 0, 0, -1, CHROME);
			// wheels in dark arches
			arch(SPORTS_BODY, s, 0.35f, 1.5f, 0.4f);
			arch(SPORTS_BODY, s, 0.35f, -1.4f, 0.41f);
			wheel(s * 0.835f, 0.35f, 1.5f, 0.35f, 0.26f, s, true, 5, true, RIM, true, false);
			wheel(s * 0.84f, 0.355f, -1.4f, 0.355f, 0.29f, s, false, 5, true, RIM, true, false);
		}
		// front: kidney grille, intakes, headlights (front cap is at z 2.44)
		float fz = 2.455f;
		for (int s = -1; s <= 1; s += 2) {
			float g1 = s * 0.035f, g2 = s * 0.27f;
			zRect(Math.min(g1, g2) - 0.012f, 0.33f, Math.max(g1, g2) + 0.012f, 0.53f, fz, 1, CHROME);
			zRect(Math.min(g1, g2), 0.345f, Math.max(g1, g2), 0.515f, fz + 0.004f, 1, PIANO);
			for (int b = 0; b < 5; b++) {
				float bx = s * (0.07f + b * 0.045f);
				zRect(bx - 0.006f, 0.35f, bx + 0.006f, 0.51f, fz + 0.007f, 1, TRIM);
			}
			float i1 = s * 0.44f, i2 = s * 0.64f;
			zRect(Math.min(i1, i2), 0.22f, Math.max(i1, i2), 0.36f, fz, 1, PIANO);
			float h1 = s * 0.34f, h2 = s * 0.6f;
			zRect(Math.min(h1, h2), 0.42f, Math.max(h1, h2), 0.49f, fz, 1, PIANO);
			zRect(Math.min(h1, h2) + 0.02f, 0.44f, Math.max(h1, h2) - 0.02f, 0.47f, fz + 0.004f, 1, LAMP);
		}
		zRect(-0.42f, 0.21f, 0.42f, 0.3f, fz, 1, PIANO);
		// rear: L-shaped tail lights, diffuser, quad exhaust
		float rz = -2.465f;
		for (int s = -1; s <= 1; s += 2) {
			float t1 = s * 0.38f, t2 = s * 0.7f;
			zRect(Math.min(t1, t2), 0.72f, Math.max(t1, t2), 0.79f, rz, -1, RED_LAMP);
			float u1 = s * 0.62f, u2 = s * 0.7f;
			zRect(Math.min(u1, u2), 0.62f, Math.max(u1, u2), 0.72f, rz, -1, RED_LAMP);
			for (float ex : new float[]{0.32f, 0.47f}) {
				zDisc(s * ex, 0.38f, rz - 0.002f, 0.055f, -1, CHROME);
				zDisc(s * ex, 0.38f, rz - 0.006f, 0.04f, -1, PIANO);
			}
		}
		zRect(-0.6f, 0.3f, 0.6f, 0.45f, rz + 0.001f, -1, PIANO);
		zRect(-0.38f, 0.72f, 0.38f, 0.79f, rz, -1, PIANO);
	}

	// ---------------------------------------------------------------- pickup truck (full size, lifted, blacked out)

	private static final float[][] PICKUP_BODY = {
			{3.02f, 0.5f, 1.34f, 0.98f, 0.97f, 0.07f, 0.06f},
			{2.94f, 0.44f, 1.4f, 1.01f, 1.0f, 0.09f, 0.06f, 0.01f},
			{2.4f, 0.42f, 1.42f, 1.02f, 1.0f, 0.09f, 0.06f, 0.02f},
			{1.85f, 0.42f, 1.43f, 1.03f, 1.0f, 0.09f, 0.06f, 0.02f},
			{1.28f, 0.42f, 1.43f, 1.03f, 1.0f, 0.09f, 0.06f, 0.01f},
			{-0.62f, 0.42f, 1.43f, 1.03f, 1.0f, 0.09f, 0.06f},
			{-2.9f, 0.42f, 1.43f, 1.03f, 1.0f, 0.09f, 0.06f},
			{-3.0f, 0.46f, 1.41f, 1.01f, 0.99f, 0.07f, 0.06f}
	};

	private static final float[][] PICKUP_CAB = {
			{1.28f, 1.3f, 1.47f, 0.95f, 0.92f, 0.03f, 0.02f},
			{0.8f, 1.3f, 2.03f, 0.95f, 0.87f, 0.1f, 0.02f},
			{0.55f, 1.3f, 2.2f, 0.95f, 0.86f, 0.12f, 0.02f, 0.02f},
			{-0.25f, 1.3f, 2.23f, 0.95f, 0.86f, 0.12f, 0.02f, 0.02f},
			{-0.58f, 1.3f, 2.2f, 0.95f, 0.86f, 0.1f, 0.02f, 0.01f}
	};

	private void pickup() {
		shadow(3.1f, 1.05f);
		loft(PICKUP_BODY, -1f, false, (seg, x, y, z, nx, ny, nz) -> {
			if (seg == -1) {
				return PAINT;
			}
			if (seg < SEG_SIDE) {
				return TRIM;
			}
			if (seg >= SEG_TOP && z < -0.62f && z > -2.9f) {
				return -1; // open bed
			}
			return PAINT;
		});
		final boolean driving = !showOff;
		loft(PICKUP_CAB, -1f, true, (seg, x, y, z, nx, ny, nz) -> {
			if (seg == -1) {
				return z < 0 && !driving ? PAINT : -1;
			}
			if (seg < SEG_SIDE) {
				return -1;
			}
			if (seg == SEG_SIDE) {
				return PAINT;
			}
			boolean roofZone = z < 0.6f;
			if (seg < SEG_TOPARC) {
				return GLASS;
			}
			if (seg < SEG_TOP) {
				return seg - SEG_TOPARC >= 2 || !roofZone ? PAINT : GLASS;
			}
			return roofZone ? PAINT : GLASS;
		});
		// bed: floor with ribs, inner walls, wheel wells
		quad(v(-0.91f, 0.98f, -0.62f), v(0.91f, 0.98f, -0.62f), v(0.91f, 0.98f, -2.9f), v(-0.91f, 0.98f, -2.9f), 0, 1, 0, BED);
		for (int i = -3; i <= 3; i++) {
			float x = i * 0.24f;
			quad(v(x - 0.03f, 0.99f, -0.64f), v(x + 0.03f, 0.99f, -0.64f), v(x + 0.03f, 0.99f, -2.88f), v(x - 0.03f, 0.99f, -2.88f), 0, 1, 0, TRIM);
		}
		for (int s = -1; s <= 1; s += 2) {
			float wx = s * 0.91f;
			quad(v(wx, 0.98f, -2.9f), v(wx, 0.98f, -0.62f), v(wx, 1.43f, -0.62f), v(wx, 1.43f, -2.9f), -s, 0, 0, BED);
			float hx1 = s > 0 ? 0.62f : -0.91f;
			box(hx1, 0.98f, -2.35f, hx1 + 0.29f, 1.22f, -1.35f, BED);
		}
		quad(v(-0.91f, 0.98f, -0.62f), v(0.91f, 0.98f, -0.62f), v(0.91f, 1.43f, -0.62f), v(-0.91f, 1.43f, -0.62f), 0, 0, -1, BED);
		quad(v(0.91f, 0.98f, -2.9f), v(-0.91f, 0.98f, -2.9f), v(-0.91f, 1.43f, -2.9f), v(0.91f, 1.43f, -2.9f), 0, 0, 1, BED);
		// rear window (while driving the back wall is built around it so you can be seen through the glass)
		if (driving) {
			zRect(-0.94f, 1.3f, 0.94f, 1.64f, -0.58f, -1, PAINT);
			zRect(-0.88f, 2.06f, 0.88f, 2.21f, -0.58f, -1, PAINT);
			zRect(-0.93f, 1.64f, -0.7f, 2.06f, -0.58f, -1, PAINT);
			zRect(0.7f, 1.64f, 0.93f, 2.06f, -0.58f, -1, PAINT);
			zRect(-0.7f, 1.64f, 0.7f, 2.06f, -0.58f, 0, GLASS);
		} else {
			zRect(-0.7f, 1.64f, 0.7f, 2.06f, -0.585f, -1, GLASS);
		}
		// front: big blacked-out grille, headlights, bumper, fog lights
		float fz = 3.025f;
		zRect(-0.66f, 0.74f, 0.66f, 1.27f, fz, 1, CHROME);
		zRect(-0.62f, 0.78f, 0.62f, 1.23f, fz + 0.004f, 1, PIANO);
		for (int b = 0; b < 4; b++) {
			float by = 0.85f + b * 0.11f;
			zRect(-0.6f, by, 0.6f, by + 0.03f, fz + 0.008f, 1, TRIM);
		}
		for (int s = -1; s <= 1; s += 2) {
			float h1 = s * 0.68f, h2 = s * 0.94f;
			zRect(Math.min(h1, h2), 1.03f, Math.max(h1, h2), 1.23f, fz, 1, PIANO);
			zDisc(s * 0.81f, 1.13f, fz + 0.004f, 0.075f, 1, LAMP);
			zDisc(s * 0.76f, 0.6f, 3.165f, 0.05f, 1, LAMP);
		}
		box(-1.03f, 0.44f, 2.98f, 1.03f, 0.74f, 3.16f, TRIM);
		box(-0.5f, 0.42f, 3.0f, 0.5f, 0.5f, 3.2f, CHROME);
		// rear: tailgate handle, tall tail lights, bumper with step
		float rz = -3.005f;
		zRect(-0.18f, 1.24f, 0.18f, 1.3f, rz, -1, PIANO);
		for (int s = -1; s <= 1; s += 2) {
			float t1 = s * 0.82f, t2 = s * 0.96f;
			zRect(Math.min(t1, t2), 0.98f, Math.max(t1, t2), 1.34f, rz, -1, RED_LAMP);
		}
		box(-1.03f, 0.44f, -3.16f, 1.03f, 0.72f, -2.98f, TRIM);
		box(-0.35f, 0.66f, -3.2f, 0.35f, 0.73f, -3.1f, CHROME);
		// roof marker lights
		for (int i = -2; i <= 2; i++) {
			float x = i * 0.18f;
			box(x - 0.04f, 2.2f, 0.52f, x + 0.04f, 2.235f, 0.58f, AMBER);
		}
		for (int s = -1; s <= 1; s += 2) {
			// door gaps, B-pillar, handles, running boards, mirrors
			sideLineV(PICKUP_BODY, s, 1.24f, 0.5f, 1.43f, 0.007f, PIANO);
			sideLineV(PICKUP_BODY, s, 0.12f, 0.5f, 1.43f, 0.007f, PIANO);
			sideLineV(PICKUP_BODY, s, -0.58f, 0.5f, 1.43f, 0.007f, PIANO);
			sideLineV(PICKUP_CAB, s, 0.12f, 1.58f, 2.12f, 0.05f, PIANO);
			sidePatch(PICKUP_BODY, s, 0.5f, 0.3f, 1.26f, 1.31f, 0.01f, CHROME);
			sidePatch(PICKUP_BODY, s, -0.3f, -0.48f, 1.26f, 1.31f, 0.01f, CHROME);
			sideLineH(PICKUP_BODY, s, 1.38f, 1.25f, -2.95f, 0.006f, PIANO);
			float rb = s > 0 ? 1.03f : -1.16f;
			box(rb, 0.47f, -0.55f, rb + 0.13f, 0.53f, 1.2f, TRIM);
			float mx = s > 0 ? 0.98f : -1.26f;
			box(s > 0 ? 0.9f : -1.02f, 1.66f, 1.03f, s > 0 ? 1.02f : -0.9f, 1.7f, 1.08f, PIANO);
			box(s > 0 ? 0.9f : -1.02f, 1.84f, 1.03f, s > 0 ? 1.02f : -0.9f, 1.87f, 1.08f, PIANO);
			box(mx, 1.6f, 1.0f, mx + 0.28f, 1.92f, 1.12f, PIANO);
			quad(v(mx + 0.02f, 1.62f, 0.998f), v(mx + 0.26f, 1.62f, 0.998f), v(mx + 0.26f, 1.9f, 0.998f), v(mx + 0.02f, 1.9f, 0.998f), 0, 0, -1, CHROME);
			arch(PICKUP_BODY, s, 0.48f, 1.85f, 0.58f);
			arch(PICKUP_BODY, s, 0.48f, -1.85f, 0.58f);
			wheel(s * 0.87f, 0.47f, 1.85f, 0.47f, 0.36f, s, true, 6, false, DARK_RIM, false, true);
			wheel(s * 0.87f, 0.47f, -1.85f, 0.47f, 0.36f, s, false, 6, false, DARK_RIM, false, true);
		}
		if (!showOff) {
			for (int s = -1; s <= 1; s += 2) {
				float xc = s * 0.45f;
				box(xc - 0.22f, 1.43f, -0.08f, xc + 0.22f, 2.0f, 0.06f, LEATHER);
				box(xc - 0.12f, 2.0f, -0.06f, xc + 0.12f, 2.12f, 0.04f, LEATHER);
			}
			box(-0.88f, 1.43f, 0.85f, 0.88f, 1.6f, 1.15f, TRIM);
			ringWheel(-0.45f, 1.72f, 0.72f, 0.19f);
		}
	}

	// ---------------------------------------------------------------- kart

	private void kart() {
		shadow(1.1f, 0.7f);
		box(-0.55f, 0.1f, -0.9f, 0.55f, 0.22f, 0.95f, PAINT);
		box(-0.5f, 0.22f, 0.55f, 0.5f, 0.42f, 1.0f, PAINT);
		box(-0.62f, 0.12f, 0.95f, 0.62f, 0.26f, 1.08f, PIANO);
		box(-0.25f, 0.22f, -0.45f, 0.25f, 0.3f, 0.05f, LEATHER);
		box(-0.25f, 0.3f, -0.55f, 0.25f, 0.72f, -0.45f, LEATHER);
		box(-0.4f, 0.22f, -0.95f, 0.4f, 0.52f, -0.6f, ROTOR);
		box(-0.6f, 0.75f, -1.02f, 0.6f, 0.8f, -0.85f, PAINT);
		box(-0.45f, 0.52f, -0.95f, -0.4f, 0.75f, -0.9f, TRIM);
		box(0.4f, 0.52f, -0.95f, 0.45f, 0.75f, -0.9f, TRIM);
		box(-0.03f, 0.42f, 0.45f, 0.03f, 0.62f, 0.55f, TRIM);
		ringWheel(0f, 0.64f, 0.42f, 0.14f);
		for (int s = -1; s <= 1; s += 2) {
			zDisc(s * 0.14f, 0.34f, -0.953f, 0.045f, -1, CHROME);
			zDisc(s * 0.14f, 0.34f, -0.957f, 0.03f, -1, PIANO);
			box(s * 0.55f - 0.08f, 0.16f, 0.7f, s * 0.55f + 0.08f, 0.2f, 0.76f, TRIM);
			wheel(s * 0.66f, 0.2f, 0.72f, 0.2f, 0.18f, s, true, 5, false, RIM, false, false);
			wheel(s * 0.66f, 0.22f, -0.7f, 0.22f, 0.24f, s, false, 5, false, RIM, false, false);
		}
	}

	// ---------------------------------------------------------------- lights / underglow / spawn flash (additive glow on top)

	private void glowLineCar(GlowBatch g, float x1, float y1, float z1, float x2, float y2, float z2, float width, int rgb, float a) {
		float[] p1 = world(x1, y1, z1), p2 = world(x2, y2, z2);
		g.glowLine(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], width * scale, rgb, a);
	}

	private void glowDisc(GlowBatch g, float x, float y, float z, float r, int rgb, float a) {
		float[] p = world(x, y, z);
		g.disc(p[0], p[1], p[2], r * scale, rgb, a, 12);
	}

	private void glowParts(GlowBatch g, float ap) {
		float a = ap * Math.max(0.3f, 1.15f - light);
		String c = car.get();
		if (lights.isOn()) {
			if (c.equals("Sports")) {
				for (int s = -1; s <= 1; s += 2) {
					glowLineCar(g, s * 0.37f, 0.455f, 2.462f, s * 0.58f, 0.455f, 2.462f, 0.035f, 0xEAF2FF, a * 1.5f);
					glowLineCar(g, s * 0.58f, 0.455f, 2.462f, s * 0.57f, 0.485f, 2.462f, 0.03f, 0xEAF2FF, a * 1.2f);
					glowDisc(g, s * 0.48f, 0.455f, 2.6f, 0.4f, 0xDCE6FF, 0.22f * a);
					glowLineCar(g, s * 0.38f, 0.755f, -2.47f, s * 0.68f, 0.755f, -2.47f, 0.035f, 0xFF2236, a * 1.3f);
					glowLineCar(g, s * 0.66f, 0.755f, -2.47f, s * 0.66f, 0.64f, -2.47f, 0.035f, 0xFF2236, a * 1.3f);
				}
			} else if (c.equals("Pickup")) {
				for (int s = -1; s <= 1; s += 2) {
					glowDisc(g, s * 0.81f, 1.13f, 3.04f, 0.12f, 0xFFFFFF, 0.9f * a);
					glowDisc(g, s * 0.81f, 1.13f, 3.1f, 0.5f, 0xDCE6FF, 0.2f * a);
					glowLineCar(g, s * 0.69f, 1.215f, 3.035f, s * 0.93f, 1.215f, 3.035f, 0.03f, 0xEAF2FF, a * 1.3f);
					glowLineCar(g, s * 0.93f, 1.215f, 3.035f, s * 0.93f, 1.05f, 3.035f, 0.03f, 0xEAF2FF, a * 1.3f);
					glowLineCar(g, s * 0.89f, 1.0f, -3.012f, s * 0.89f, 1.32f, -3.012f, 0.05f, 0xFF2236, a * 1.2f);
				}
				for (int i = -2; i <= 2; i++) {
					glowDisc(g, i * 0.18f, 2.22f, 0.585f, 0.06f, 0xFFA020, 0.8f * a);
				}
			} else {
				for (int s = -1; s <= 1; s += 2) {
					glowDisc(g, s * 0.3f, 0.32f, 1.02f, 0.11f, 0xFFFFFF, 0.9f * a);
				}
			}
		}
		if (underglow.isOn()) {
			float len = c.equals("Pickup") ? 2.6f : c.equals("Kart") ? 0.9f : 2.1f;
			float w = c.equals("Pickup") ? 0.9f : c.equals("Kart") ? 0.5f : 0.82f;
			float h = c.equals("Pickup") ? 0.36f : c.equals("Kart") ? 0.08f : 0.1f;
			int ug = glowColor.rgb();
			for (int s = -1; s <= 1; s += 2) {
				glowLineCar(g, s * w, h, -len, s * w, h, len, 0.06f, ug, 0.7f * ap);
				glowLineCar(g, -w, h, s * len, w, h, s * len, 0.06f, ug, 0.5f * ap);
			}
		}
		if (ap < 1f) {
			float[] center = world(0, 0.8f, 0);
			g.disc(center[0], center[1], center[2], 2.4f * scale, 0xFFFFFF, 0.45f * (1f - ap) * ap * 4f, 18);
		}
	}
}
