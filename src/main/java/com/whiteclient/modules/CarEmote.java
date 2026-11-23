package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.SolidBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.ActionSetting;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
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
 * Car emote: press N (or "Start" in the menu) and you sit in a car that drives wherever you walk.
 * Purely visual and client-side — only you see it, your movement is unchanged. Sneak or press N again to get out.
 */
public class CarEmote extends Module {
	public static final String[] CARS = {"Sports", "Pickup", "Kart"};

	public final ModeSetting car = add(new ModeSetting("Car", "Sports", CARS));
	public final ColorSetting color = add(new ColorSetting("Paint", 0x16171D));
	public final BoolSetting underglow = add(new BoolSetting("Underglow", true));
	public final ColorSetting glowColor = add(new ColorSetting("Underglow Color", 0xFFFFFF).visibleWhen(underglow::isOn));
	public final BoolSetting lights = add(new BoolSetting("Headlights", true));
	public final BoolSetting thirdPerson = add(new BoolSetting("Third Person While Driving", true));
	public final BoolSetting sneakExit = add(new BoolSetting("Sneak To Get Out", true));
	public final ActionSetting toggleAction = add(new ActionSetting("Car", () -> active ? "Get Out" : "Get In", this::toggleCar));

	private static boolean active;
	private float appear, pappear; // 0..1 spawn / despawn animation
	private boolean leaving;
	private Perspective savedPerspective;
	private double cx, cz, pcx, pcz;
	private float yaw, pyaw, steer, psteer, wheel, pwheel;
	private boolean init;
	private final SolidBatch solid = new SolidBatch();

	public CarEmote() {
		super("Car Emote", "Press N to drive a car (only you see it)", Category.COSMETICS, true);
	}

	public static CarEmote get() {
		WhiteClient wc = WhiteClient.get();
		return wc == null ? null : wc.modules().get(CarEmote.class);
	}

	public static boolean active() {
		CarEmote c = get();
		return active && c != null && c.isEnabled();
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
		appear = pappear = 0;
		if (thirdPerson.isOn() && mc.options != null && mc.options.getPerspective().isFirstPerson()) {
			savedPerspective = mc.options.getPerspective();
			mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
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

	/** How far the seated player is moved up/down for the current car. */
	private float seatOffset() {
		return switch (car.get()) {
			case "Pickup" -> 0.32f;
			case "Kart" -> -0.48f;
			default -> -0.16f;
		};
	}

	/** Called by a mixin with the player's render state: sit pose + seat height. */
	public static void applyPose(Entity entity, EntityRenderState state) {
		CarEmote c = get();
		if (c == null || !active || !c.isEnabled() || state == null) {
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
		if ((sneakExit.isOn() && p.isSneaking()) || p.hasVehicle() || p.isGliding() || p.isTouchingWater() || p.getAbilities().flying || p.isSpectator()) {
			stop();
		}
		pappear = appear;
		appear = leaving ? Math.max(0f, appear - 0.12f) : Math.min(1f, appear + 0.1f);
		if (leaving && appear <= 0f) {
			finish();
			return;
		}
		pcx = cx;
		pcz = cz;
		pyaw = yaw;
		psteer = steer;
		pwheel = wheel;
		Vec3d pos = p.getEntityPos();
		float body = p.getBodyYaw();
		if (!init) {
			cx = pcx = pos.x;
			cz = pcz = pos.z;
			yaw = pyaw = body;
			init = true;
		}
		double moved = Math.hypot(pos.x - cx, pos.z - cz);
		float turn = MathHelper.wrapDegrees(body - yaw) * 0.35f;
		yaw += turn;
		steer += (MathHelper.clamp(turn * 2.5f, -30f, 30f) - steer) * 0.3f;
		// wheels roll forward / backward depending on the direction of travel
		double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
		double along = (pos.x - cx) * fx + (pos.z - cz) * fz;
		wheel += (float) (along / wheelRadius());
		cx = pos.x;
		cz = pos.z;
		if (moved > 6) {
			pcx = cx;
			pcz = cz;
		}
	}

	private float wheelRadius() {
		return switch (car.get()) {
			case "Pickup" -> 0.46f;
			case "Kart" -> 0.2f;
			default -> 0.36f;
		};
	}

	// ------------------------------------------------------------------ drawing

	private float ox, oy, oz, cosY, sinY, scale, light;
	private float wSpin, wSteer;

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

	private int shade(int rgb, float[] n) {
		float lx = 0.35f, ly = 0.85f, lz = 0.4f;
		float d = Math.max(0f, n[0] * lx + n[1] * ly + n[2] * lz);
		float k = (0.42f + 0.58f * d) * light;
		return Colors.shade(rgb, k);
	}

	/** Axis-aligned box in car space; {@code glossy} adds a light sheen on top. */
	private void box(float x1, float y1, float z1, float x2, float y2, float z2, int rgb, boolean glossy) {
		face(x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0, 0, 1, rgb, glossy);   // front
		face(x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, 0, 0, -1, rgb, glossy);  // back
		face(x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1, 0, 0, rgb, glossy);   // right side (+x)
		face(x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0, rgb, glossy);  // left side
		face(x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0, 1, 0, rgb, glossy);   // top
		face(x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0, rgb, false);   // bottom
	}

	private void face(float ax, float ay, float az, float bx, float by, float bz, float cx2, float cy, float cz2, float dx, float dy, float dz,
					  float nx, float ny, float nz, int rgb, boolean glossy) {
		float[] n = normal(nx, ny, nz);
		int base = shade(rgb, n);
		int top = glossy && ny == 0 ? Colors.mix(base, Colors.shade(0xFFFFFF, light), 0.12f) : base;
		int c1 = 0xFF000000 | base, c2 = 0xFF000000 | base;
		int c3 = 0xFF000000 | (ny == 0 ? top : base), c4 = 0xFF000000 | (ny == 0 ? top : base);
		if (glossy && ny > 0) {
			c3 = 0xFF000000 | Colors.mix(base, Colors.shade(0xFFFFFF, light), 0.18f);
		}
		float[] a = world(ax, ay, az), b = world(bx, by, bz), c = world(cx2, cy, cz2), d = world(dx, dy, dz);
		solid.quad(a[0], a[1], a[2], c1, b[0], b[1], b[2], c2, c[0], c[1], c[2], c3, d[0], d[1], d[2], c4, n[0], n[1], n[2]);
	}

	/** Free quad in car space with its own outward normal (0,0,0 = visible from both sides). */
	private void quad(float[] p1, float[] p2, float[] p3, float[] p4, float nx, float ny, float nz, int argb) {
		float[] a = world(p1[0], p1[1], p1[2]), b = world(p2[0], p2[1], p2[2]), c = world(p3[0], p3[1], p3[2]), d = world(p4[0], p4[1], p4[2]);
		float[] n = (nx == 0 && ny == 0 && nz == 0) ? new float[]{0, 0, 0} : normal(nx, ny, nz);
		int col = (nx == 0 && ny == 0 && nz == 0) ? argb : (argb & 0xFF000000) | shade(argb & 0xFFFFFF, n);
		solid.quad(a[0], a[1], a[2], col, b[0], b[1], b[2], col, c[0], c[1], c[2], col, d[0], d[1], d[2], col, n[0], n[1], n[2]);
	}

	/** A wheel (octagonal tyre + rim with spokes) on the car side {@code side} (-1 left, +1 right). */
	private void wheel(float x, float y, float z, float r, float w, int side, boolean steering) {
		int seg = 10;
		float steerRad = steering ? (float) Math.toRadians(wSteer) : 0f;
		float cs = (float) Math.cos(steerRad), sn = (float) Math.sin(steerRad);
		float[][] outer = new float[seg][], inner = new float[seg][];
		for (int i = 0; i < seg; i++) {
			double a = Math.PI * 2 * i / seg + wSpin;
			float py = y + (float) Math.sin(a) * r, pz = (float) Math.cos(a) * r;
			// rotate the wheel around its vertical axis for steering
			float ox1 = side * w / 2f, ox2 = -side * w / 2f;
			outer[i] = new float[]{x + ox1 * cs + pz * sn, py, z + pz * cs - ox1 * sn};
			inner[i] = new float[]{x + ox2 * cs + pz * sn, py, z + pz * cs - ox2 * sn};
		}
		int tyre = 0xFF141418;
		for (int i = 0; i < seg; i++) {
			int j = (i + 1) % seg;
			double a = Math.PI * 2 * (i + 0.5) / seg + wSpin;
			float ny = (float) Math.sin(a), nz = (float) Math.cos(a);
			quad(outer[i], outer[j], inner[j], inner[i], nz * sn, ny, nz * cs, tyre);
		}
		// outer cap (rim): tyre ring + bright rim + spokes
		float[] center = {x + side * w / 2f * cs, y, z - side * w / 2f * sn};
		float nX = side * cs, nZ = -side * sn;
		int rim = 0xFF000000 | Colors.shade(0xB8BCC8, light);
		int dark = 0xFF000000 | Colors.shade(0x2A2B31, light);
		for (int i = 0; i < seg; i++) {
			int j = (i + 1) % seg;
			float[] a = outer[i], b = outer[j];
			float[] a2 = lerp3(center, a, 0.62f), b2 = lerp3(center, b, 0.62f);
			quad(a, b, b2, a2, nX, 0, nZ, tyre);
			quad(a2, b2, center, center, nX, 0, nZ, i % 2 == 0 ? rim : dark);
		}
		// inner cap
		float[] ic = {x - side * w / 2f * cs, y, z + side * w / 2f * sn};
		for (int i = 0; i < seg; i++) {
			int j = (i + 1) % seg;
			quad(inner[j], inner[i], ic, ic, -nX, 0, -nZ, tyre);
		}
	}

	private static float[] lerp3(float[] a, float[] b, float t) {
		return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
	}

	private static float[] v(float x, float y, float z) {
		return new float[]{x, y, z};
	}

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
		Vec3d pl = ctx.client.player.getLerpedPos(td);
		float y = pyaw + MathHelper.wrapDegrees(yaw - pyaw) * td;
		wSteer = MathHelper.lerp(td, psteer, steer);
		wSpin = MathHelper.lerp(td, pwheel, wheel);
		double r = Math.toRadians(y);
		cosY = (float) Math.cos(r);
		sinY = (float) Math.sin(r);
		float ease = 1f - (1f - ap) * (1f - ap) * (1f - ap);
		scale = 0.6f + 0.4f * ease;

		// the car is placed so the driver's seat is where you are
		float[] seat = switch (car.get()) {
			case "Pickup" -> new float[]{-0.45f, 0.25f};
			case "Kart" -> new float[]{0f, -0.15f};
			default -> new float[]{-0.42f, -0.35f};
		};
		float sx = -cosY * seat[0] + -sinY * seat[1];
		float sz = -sinY * seat[0] + cosY * seat[1];
		ox = ctx.rx(pl.x) - sx * scale;
		oy = ctx.ry(pl.y);
		oz = ctx.rz(pl.z) - sz * scale;

		// light level at the car: sky light dims at night, block light (torches) stays
		BlockPos bp = BlockPos.ofFloored(pl.x, pl.y + 0.5, pl.z);
		long time = ctx.client.world.getTimeOfDay() % 24000L;
		float day = 0.5f + 0.5f * (float) Math.cos((time - 6000L) / 24000.0 * Math.PI * 2);
		float sky = ctx.client.world.getLightLevel(LightType.SKY, bp) / 15f * (0.18f + 0.82f * day);
		float block = ctx.client.world.getLightLevel(LightType.BLOCK, bp) / 15f;
		light = 0.22f + 0.78f * Math.max(sky, block);

		int paint = color.rgb();
		switch (car.get()) {
			case "Pickup" -> pickup(paint);
			case "Kart" -> kart(paint);
			default -> sports(paint);
		}
		solid.draw(ctx.consumers, ctx.matrices.peek().getPositionMatrix());
		glowParts(ctx.glow, ap);
	}

	private void sports(int paint) {
		int trim = 0x1E1F24, seatC = 0x2B2C33, interior = 0x24252B;
		// lower body + side skirts
		box(-0.95f, 0.24f, -2.15f, 0.95f, 0.56f, 2.1f, paint, true);
		// hood (slopes down to the nose) and nose
		box(-0.92f, 0.56f, 0.6f, 0.92f, 0.74f, 1.75f, paint, true);
		quad(v(-0.92f, 0.74f, 1.75f), v(0.92f, 0.74f, 1.75f), v(0.9f, 0.6f, 2.22f), v(-0.9f, 0.6f, 2.22f), 0, 0.9f, 0.45f, 0xFF000000 | paint);
		box(-0.9f, 0.3f, 2.1f, 0.9f, 0.6f, 2.22f, paint, true);
		// rear deck + spoiler
		box(-0.93f, 0.56f, -2.15f, 0.93f, 0.84f, -0.85f, paint, true);
		box(-0.85f, 0.95f, -2.1f, 0.85f, 1.0f, -1.85f, trim, false);
		box(-0.7f, 0.84f, -2.0f, -0.62f, 0.95f, -1.92f, trim, false);
		box(0.62f, 0.84f, -2.0f, 0.7f, 0.95f, -1.92f, trim, false);
		// doors around the open cockpit
		box(-0.95f, 0.56f, -0.85f, -0.8f, 0.84f, 0.6f, paint, true);
		box(0.8f, 0.56f, -0.85f, 0.95f, 0.84f, 0.6f, paint, true);
		// interior: floor, seats, dashboard, steering wheel
		box(-0.8f, 0.4f, -0.85f, 0.8f, 0.45f, 0.6f, interior, false);
		for (int s = -1; s <= 1; s += 2) {
			float xc = s * 0.42f;
			box(xc - 0.2f, 0.45f, -0.62f, xc + 0.2f, 0.58f, -0.08f, seatC, false);
			box(xc - 0.2f, 0.58f, -0.75f, xc + 0.2f, 1.08f, -0.6f, seatC, false);
		}
		box(-0.8f, 0.66f, 0.42f, 0.8f, 0.86f, 0.6f, trim, false);
		box(-0.55f, 0.82f, 0.3f, -0.29f, 0.84f, 0.34f, 0x101014, false);
		box(-0.43f, 0.74f, 0.32f, -0.41f, 0.84f, 0.42f, 0x101014, false);
		// windshield (glass, slanted) with a thin frame
		int glass = (0x55 << 24) | Colors.shade(0xBFD6FF, light);
		quad(v(-0.86f, 0.84f, 0.6f), v(0.86f, 0.84f, 0.6f), v(0.8f, 1.2f, 0.3f), v(-0.8f, 1.2f, 0.3f), 0, 0, 0, glass);
		box(-0.86f, 1.18f, 0.28f, 0.86f, 1.22f, 0.34f, trim, false);
		// grille + bumper details
		box(-0.55f, 0.34f, 2.2f, 0.55f, 0.48f, 2.24f, 0x0C0C10, false);
		box(-0.7f, 0.3f, -2.2f, 0.7f, 0.42f, -2.14f, 0x0C0C10, false);
		// mirrors
		box(-1.08f, 0.84f, 0.45f, -0.95f, 0.94f, 0.58f, paint, true);
		box(0.95f, 0.84f, 0.45f, 1.08f, 0.94f, 0.58f, paint, true);
		for (int sd = -1; sd <= 1; sd += 2) {
			wheel(sd * 0.86f, 0.36f, 1.35f, 0.36f, 0.3f, sd, true);
			wheel(sd * 0.86f, 0.36f, -1.38f, 0.37f, 0.32f, sd, false);
		}
	}

	private void pickup(int paint) {
		int trim = 0x1B1C21, glassTint = 0xA9C4E8;
		box(-1.0f, 0.42f, -2.7f, 1.0f, 0.95f, 2.65f, paint, true);           // chassis / lower body
		box(-0.98f, 0.95f, 1.1f, 0.98f, 1.32f, 2.6f, paint, true);           // hood
		box(-0.9f, 0.5f, 2.62f, 0.9f, 1.25f, 2.72f, 0x0E0E12, false);         // grille
		box(-1.02f, 0.4f, 2.7f, 1.02f, 0.58f, 2.82f, 0x8E929C, false);        // front bumper
		box(-1.02f, 0.4f, -2.82f, 1.02f, 0.58f, -2.7f, 0x8E929C, false);      // rear bumper
		// cab: pillars + roof, glass windows between them
		box(-1.0f, 0.95f, -0.45f, 1.0f, 1.32f, 1.1f, paint, true);
		box(-0.98f, 1.32f, 0.95f, -0.88f, 2.3f, 1.1f, paint, true);
		box(0.88f, 1.32f, 0.95f, 0.98f, 2.3f, 1.1f, paint, true);
		box(-0.98f, 1.32f, -0.45f, -0.88f, 2.3f, -0.3f, paint, true);
		box(0.88f, 1.32f, -0.45f, 0.98f, 2.3f, -0.3f, paint, true);
		box(-0.98f, 2.3f, -0.45f, 0.98f, 2.4f, 1.1f, paint, true);
		int glass = (0x60 << 24) | Colors.shade(glassTint, light);
		quad(v(-0.9f, 1.32f, 1.1f), v(0.9f, 1.32f, 1.1f), v(0.9f, 2.3f, 1.0f), v(-0.9f, 2.3f, 1.0f), 0, 0, 0, glass);
		quad(v(-0.9f, 1.32f, -0.42f), v(0.9f, 1.32f, -0.42f), v(0.9f, 2.3f, -0.42f), v(-0.9f, 2.3f, -0.42f), 0, 0, 0, glass);
		quad(v(-0.99f, 1.32f, -0.3f), v(-0.99f, 1.32f, 0.95f), v(-0.99f, 2.3f, 0.95f), v(-0.99f, 2.3f, -0.3f), 0, 0, 0, glass);
		quad(v(0.99f, 1.32f, -0.3f), v(0.99f, 1.32f, 0.95f), v(0.99f, 2.3f, 0.95f), v(0.99f, 2.3f, -0.3f), 0, 0, 0, glass);
		// seats + dashboard inside the cab
		for (int s = -1; s <= 1; s += 2) {
			box(s * 0.45f - 0.22f, 0.95f, -0.05f, s * 0.45f + 0.22f, 1.1f, 0.5f, 0x2B2C33, false);
			box(s * 0.45f - 0.22f, 1.1f, -0.3f, s * 0.45f + 0.22f, 1.75f, -0.12f, 0x2B2C33, false);
		}
		box(-0.88f, 1.2f, 0.82f, 0.88f, 1.45f, 0.95f, trim, false);
		// bed walls + tailgate
		box(-1.0f, 0.95f, -2.7f, -0.88f, 1.4f, -0.5f, paint, true);
		box(0.88f, 0.95f, -2.7f, 1.0f, 1.4f, -0.5f, paint, true);
		box(-0.88f, 0.95f, -2.7f, 0.88f, 1.4f, -2.58f, paint, true);
		box(-0.88f, 0.94f, -2.58f, 0.88f, 0.97f, -0.5f, 0x2A2B30, false);
		// mirrors
		box(-1.18f, 1.5f, 0.9f, -1.0f, 1.68f, 1.02f, trim, false);
		box(1.0f, 1.5f, 0.9f, 1.18f, 1.68f, 1.02f, trim, false);
		for (int sd = -1; sd <= 1; sd += 2) {
			wheel(sd * 0.92f, 0.46f, 1.75f, 0.46f, 0.36f, sd, true);
			wheel(sd * 0.92f, 0.46f, -1.75f, 0.46f, 0.36f, sd, false);
		}
	}

	private void kart(int paint) {
		int trim = 0x15161A;
		box(-0.55f, 0.1f, -0.9f, 0.55f, 0.22f, 0.95f, paint, true);           // floor pan
		box(-0.5f, 0.22f, 0.55f, 0.5f, 0.42f, 1.0f, paint, true);             // nose
		box(-0.62f, 0.12f, 0.95f, 0.62f, 0.26f, 1.08f, trim, false);           // front bumper
		box(-0.25f, 0.22f, -0.45f, 0.25f, 0.3f, 0.05f, 0x2B2C33, false);        // seat
		box(-0.25f, 0.3f, -0.55f, 0.25f, 0.72f, -0.45f, 0x2B2C33, false);       // seat back
		box(-0.4f, 0.22f, -0.95f, 0.4f, 0.52f, -0.6f, 0x3A3C44, false);         // engine
		box(-0.6f, 0.75f, -1.02f, 0.6f, 0.8f, -0.85f, paint, true);             // spoiler
		box(-0.45f, 0.52f, -0.95f, -0.4f, 0.75f, -0.9f, trim, false);
		box(0.4f, 0.52f, -0.95f, 0.45f, 0.75f, -0.9f, trim, false);
		box(-0.03f, 0.42f, 0.45f, 0.03f, 0.62f, 0.55f, trim, false);            // steering column
		box(-0.16f, 0.6f, 0.4f, 0.16f, 0.64f, 0.46f, 0x101014, false);         // steering wheel
		for (int sd = -1; sd <= 1; sd += 2) {
			box(sd * 0.55f - 0.08f, 0.16f, 0.7f, sd * 0.55f + 0.08f, 0.2f, 0.76f, trim, false);
			wheel(sd * 0.66f, 0.2f, 0.72f, 0.2f, 0.18f, sd, true);
			wheel(sd * 0.66f, 0.22f, -0.7f, 0.22f, 0.24f, sd, false);
		}
	}

	private void glowParts(GlowBatch g, float ap) {
		float len = switch (car.get()) {
			case "Pickup" -> 2.75f;
			case "Kart" -> 1.05f;
			default -> 2.2f;
		};
		float hy = switch (car.get()) {
			case "Pickup" -> 1.1f;
			case "Kart" -> 0.32f;
			default -> 0.5f;
		};
		float hx = car.is("Kart") ? 0.3f : 0.68f;
		float a = ap;
		if (lights.isOn()) {
			for (int sd = -1; sd <= 1; sd += 2) {
				float[] f = world(sd * hx, hy, len + 0.02f);
				g.disc(f[0], f[1], f[2], 0.16f * scale, 0xFFFFFF, 0.9f * a, 10);
				g.disc(f[0], f[1], f[2], 0.45f * scale, 0xDCE6FF, 0.25f * a, 12);
				float[] b = world(sd * hx, hy + 0.05f, -len - 0.02f);
				g.disc(b[0], b[1], b[2], 0.12f * scale, 0xFF2840, 0.85f * a, 10);
				g.disc(b[0], b[1], b[2], 0.32f * scale, 0xFF2840, 0.22f * a, 12);
			}
		}
		if (underglow.isOn()) {
			float[] c = world(0, 0.03f, 0);
			float w = car.is("Kart") ? 0.8f : 1.2f;
			int ug = glowColor.rgb();
			g.flatDisc(c[0], c[1], c[2], len * 0.95f * scale, ug, 0.28f * a);
			// a bright line along both sides
			for (int sd = -1; sd <= 1; sd += 2) {
				float[] p1 = world(sd * w * 0.78f, 0.05f, -len * 0.8f), p2 = world(sd * w * 0.78f, 0.05f, len * 0.8f);
				g.glowLine(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], 0.08f * scale, ug, 0.8f * a);
			}
		}
		// spawn flash
		if (ap < 1f) {
			float[] c = world(0, 0.8f, 0);
			g.disc(c[0], c[1], c[2], 2.8f * scale, 0xFFFFFF, 0.5f * (1f - ap) * ap * 4f, 18);
		}
	}
}
