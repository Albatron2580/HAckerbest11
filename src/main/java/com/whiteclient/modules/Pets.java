package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.V3;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A little companion that floats next to you. Only you can see it (it exists only on your client),
 * it can't be hit and it doesn't push you, so it is safe on servers.
 */
public class Pets extends Module {
	public static final String[] TYPES = {
			"Jellyfish", "Wisp", "Butterfly", "Dog", "Cat", "Fox", "Parrot", "Bee", "Allay",
			"Axolotl", "Frog", "Rabbit", "Panda", "Chicken", "Bat", "Vex"
	};
	public static final int PET_ID = -770_001;

	public final ModeSetting pet = add(new ModeSetting("Pet", "Jellyfish", TYPES));
	public final TextSetting name = add(new TextSetting("Name", "", 24));
	public final BoolSetting baby = add(new BoolSetting("Baby", true));
	public final ModeSetting side = add(new ModeSetting("Side", "Right", "Right", "Left"));
	public final NumberSetting height = add(new NumberSetting("Height", 1.55, 0.3, 2.8, 0.05));
	public final NumberSetting distance = add(new NumberSetting("Distance", 1.0, 0.5, 2.5, 0.05));
	public final NumberSetting bob = add(new NumberSetting("Float", 1.0, 0.0, 2.0, 0.1, "x"));
	public final ColorSetting color = add(new ColorSetting("Glow Color", 0xBFD8FF).visibleWhen(this::isGlowPet));
	public final ColorSetting color2 = add(new ColorSetting("Second Color", 0xF2C6FF).visibleWhen(this::isGlowPet));
	public final NumberSetting glow = add(new NumberSetting("Glow", 1.0, 0.2, 2.0, 0.05, "x").visibleWhen(this::isGlowPet));

	private Entity entity;
	private String entityKey = "";
	private ClientWorld entityWorld;
	private Vec3d pos, prevPos;
	private float yaw, prevYaw;
	private int ticks;

	public Pets() {
		super("Pets", "A pet that floats next to you", Category.COSMETICS, false);
	}

	public static boolean isPet(Entity e) {
		int id = e.getId();
		return id <= PET_ID && id > PET_ID - 100;
	}

	public boolean isGlowPet() {
		return pet.is("Jellyfish") || pet.is("Wisp") || pet.is("Butterfly");
	}

	private static EntityType<?> typeOf(String name) {
		return switch (name) {
			case "Dog" -> EntityType.WOLF;
			case "Cat" -> EntityType.CAT;
			case "Fox" -> EntityType.FOX;
			case "Parrot" -> EntityType.PARROT;
			case "Bee" -> EntityType.BEE;
			case "Allay" -> EntityType.ALLAY;
			case "Axolotl" -> EntityType.AXOLOTL;
			case "Frog" -> EntityType.FROG;
			case "Rabbit" -> EntityType.RABBIT;
			case "Panda" -> EntityType.PANDA;
			case "Chicken" -> EntityType.CHICKEN;
			case "Bat" -> EntityType.BAT;
			case "Vex" -> EntityType.VEX;
			default -> null;
		};
	}

	@Override
	protected void onDisable() {
		removeEntity();
		pos = prevPos = null;
	}

	private void removeEntity() {
		if (entity != null && entityWorld != null && entityWorld == MinecraftClient.getInstance().world) {
			try {
				entityWorld.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
			} catch (Throwable ignored) {
			}
		}
		entity = null;
		entityWorld = null;
		entityKey = "";
	}

	private Vec3d targetPos(PlayerEntity player) {
		float bodyYaw = player.getBodyYaw();
		V3 right = V3.right(bodyYaw);
		V3 fwd = V3.forward(bodyYaw);
		float s = side.is("Left") ? -1f : 1f;
		float d = distance.getFloat();
		double bobY = Math.sin(ticks * 0.08) * 0.12 * bob.get();
		return player.getEntityPos().add(right.x() * s * d + fwd.x() * 0.15, height.get() * player.getScale() + bobY, right.z() * s * d + fwd.z() * 0.15);
	}

	@Override
	public void onTick(MinecraftClient mc) {
		ClientWorld world = mc.world;
		PlayerEntity player = mc.player;
		if (world == null || player == null) {
			entity = null;
			entityWorld = null;
			entityKey = "";
			pos = prevPos = null;
			return;
		}
		ticks++;
		Vec3d target = targetPos(player);
		prevPos = pos == null ? target : pos;
		prevYaw = yaw;
		if (pos == null || pos.squaredDistanceTo(target) > 64) {
			pos = target;
		} else {
			pos = pos.add(target.subtract(pos).multiply(0.22));
		}
		Vec3d motion = pos.subtract(prevPos);
		float wanted = motion.horizontalLengthSquared() > 0.0004
				? (float) (MathHelper.atan2(motion.z, motion.x) * 180 / Math.PI) - 90f
				: player.getBodyYaw();
		yaw = yaw + MathHelper.wrapDegrees(wanted - yaw) * 0.25f;

		EntityType<?> type = typeOf(pet.get());
		String key = pet.get() + "|" + baby.isOn() + "|" + name.get();
		if (type == null) {
			if (entity != null) {
				removeEntity();
			}
			return;
		}
		if (entity == null || !key.equals(entityKey) || entityWorld != world || entity.isRemoved()) {
			removeEntity();
			spawn(world, type, key);
		}
		if (entity != null) {
			entity.setPosition(pos.x, pos.y, pos.z);
			entity.setVelocity(0, 0, 0);
			entity.setYaw(yaw);
			entity.setHeadYaw(yaw);
			entity.setBodyYaw(yaw);
			entity.setPitch(0);
			entity.setOnGround(false);
		}
	}

	private void spawn(ClientWorld world, EntityType<?> type, String key) {
		try {
			Entity e = type.create(world, SpawnReason.LOAD);
			if (e == null) {
				return;
			}
			e.setId(PET_ID);
			e.setNoGravity(true);
			e.setSilent(true);
			e.setInvulnerable(true);
			if (e instanceof MobEntity mob) {
				mob.setAiDisabled(true);
				mob.setBaby(baby.isOn());
			}
			if (e instanceof TameableEntity tame) {
				tame.setTamed(true, false);
			}
			String n = name.get().trim();
			if (!n.isEmpty()) {
				e.setCustomName(Text.literal(n));
				e.setCustomNameVisible(true);
			}
			e.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
			world.addEntity(e);
			entity = e;
			entityWorld = world;
			entityKey = key;
		} catch (Throwable t) {
			entity = null;
			entityKey = key;
			entityWorld = world;
		}
	}

	// ------------------------------------------------------------------ glow pets

	@Override
	public void renderWorld(WorldContext ctx) {
		if (!isGlowPet() || pos == null || prevPos == null) {
			return;
		}
		if (!ctx.thirdPerson() && ctx.client.player != null && ctx.client.player.isInvisible()) {
			return;
		}
		float td = ctx.tickDelta;
		double x = MathHelper.lerp(td, prevPos.x, pos.x);
		double y = MathHelper.lerp(td, prevPos.y, pos.y);
		double z = MathHelper.lerp(td, prevPos.z, pos.z);
		float yw = prevYaw + MathHelper.wrapDegrees(yaw - prevYaw) * td;
		V3 c = new V3(ctx.rx(x), ctx.ry(y), ctx.rz(z));
		float t = ctx.time;
		float a = glow.getFloat();
		switch (pet.get()) {
			case "Wisp" -> wisp(ctx.glow, c, t, a);
			case "Butterfly" -> butterfly(ctx.glow, c, yw, t, a);
			default -> jellyfish(ctx.glow, c, t, a);
		}
		String n = name.get().trim();
		if (!n.isEmpty()) {
			drawName(ctx, c, n);
		}
	}

	private void drawName(WorldContext ctx, V3 c, String n) {
		var ms = ctx.matrices;
		var tr = ctx.client.textRenderer;
		ms.push();
		ms.translate(c.x(), c.y() + 0.55f, c.z());
		ms.multiply(ctx.camRotation);
		float s = 0.02f;
		ms.scale(s, -s, s);
		var m = ms.peek().getPositionMatrix();
		int light = net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE;
		tr.draw(n, -tr.getWidth(n) / 2f, 0, 0xFFFFFFFF, false, m, ctx.consumers, net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, light);
		ms.pop();
	}

	/** A glowing jellyfish ("kwal"): pulsing bell + flowing tentacles. */
	private void jellyfish(GlowBatch g, V3 c, float t, float a) {
		int c1 = color.rgb(), c2 = color2.rgb();
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.6);
		float bellR = 0.22f + 0.03f * pulse;
		float bellH = 0.2f - 0.03f * pulse;
		V3 top = c.add(V3.UP, 0.12f);
		int rings = 5, seg = 18;
		// bell: stacked rings from the top down
		V3[] rim = new V3[seg + 1];
		for (int ring = 1; ring <= rings; ring++) {
			float u = ring / (float) rings;
			float rr = bellR * (float) Math.sin(u * Math.PI * 0.5);
			float yy = -bellH * u * u;
			V3 prev = null;
			for (int i = 0; i <= seg; i++) {
				double ang = Math.PI * 2 * i / seg + t * 0.3;
				float wav = ring == rings ? 0.015f * (float) Math.sin(ang * 6 + t * 4) : 0;
				V3 pt = new V3(top.x() + (float) Math.cos(ang) * (rr + wav), top.y() + yy, top.z() + (float) Math.sin(ang) * (rr + wav));
				if (prev != null) {
					int col = Colors.mix(c1, c2, u);
					g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), ring == rings ? 0.05f : 0.03f, col, a * (ring == rings ? 0.9f : 0.45f));
				}
				prev = pt;
				if (ring == rings) {
					rim[i] = pt;
				}
			}
		}
		g.disc(top.x(), top.y() - bellH * 0.5f, top.z(), bellR * 2.4f, c1, 0.28f * a * (0.7f + 0.3f * pulse), 16);
		g.disc(top.x(), top.y() - bellH * 0.4f, top.z(), bellR * 0.9f, 0xFFFFFF, 0.35f * a, 12);
		// tentacles
		int tentacles = 7;
		for (int k = 0; k < tentacles; k++) {
			V3 start = rim[(k * seg / tentacles) % seg];
			V3 prev = start;
			int steps = 8;
			float len = 0.55f + 0.12f * (k % 3);
			for (int s = 1; s <= steps; s++) {
				float u = s / (float) steps;
				float sway = (float) Math.sin(t * 2.2 - u * 4 + k) * 0.06f * u;
				float sway2 = (float) Math.cos(t * 1.8 - u * 3 + k * 1.7) * 0.06f * u;
				double ang = Math.PI * 2 * k / tentacles;
				float inward = 1f - 0.35f * u;
				V3 pt = new V3(c.x() + (start.x() - c.x()) * inward + sway, start.y() - len * u, c.z() + (start.z() - c.z()) * inward + sway2);
				int col = Colors.mix(c1, c2, 0.4f + 0.6f * u);
				g.glowLine(prev.x(), prev.y(), prev.z(), pt.x(), pt.y(), pt.z(), 0.028f, col, a * (1f - u * 0.75f) * 0.8f);
				prev = pt;
			}
		}
		// tiny bubbles
		for (int b = 0; b < 3; b++) {
			float u = (float) ((t * 0.4 + b / 3.0) % 1.0);
			g.disc(c.x() + (float) Math.sin(b * 2.1 + t) * 0.15f, c.y() + 0.2f + u * 0.5f, c.z() + (float) Math.cos(b * 1.3 + t) * 0.15f,
					0.03f, c2, a * 0.6f * (1 - u), 6);
		}
	}

	private void wisp(GlowBatch g, V3 c, float t, float a) {
		int c1 = color.rgb(), c2 = color2.rgb();
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3);
		g.disc(c.x(), c.y(), c.z(), 0.55f, c1, 0.22f * a, 16);
		g.disc(c.x(), c.y(), c.z(), 0.22f + 0.04f * pulse, c2, 0.55f * a, 14);
		g.disc(c.x(), c.y(), c.z(), 0.08f, 0xFFFFFF, Math.min(1f, a), 10);
		for (int i = 0; i < 4; i++) {
			double ang = t * 2.4 + i * Math.PI / 2;
			float rr = 0.28f;
			V3 p = new V3(c.x() + (float) Math.cos(ang) * rr, c.y() + (float) Math.sin(t * 3 + i) * 0.1f, c.z() + (float) Math.sin(ang) * rr);
			g.disc(p.x(), p.y(), p.z(), 0.06f, i % 2 == 0 ? c1 : c2, 0.7f * a, 8);
		}
	}

	private void butterfly(GlowBatch g, V3 c, float yawDeg, float t, float a) {
		int c1 = color.rgb(), c2 = color2.rgb();
		V3 fwd = V3.forward(yawDeg);
		V3 right = V3.right(yawDeg);
		float flap = (float) Math.sin(t * 14) * 0.9f;
		for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
			V3 wingDir = right.mul(sideSign).rotate(fwd, -sideSign * flap * 0.6f);
			// upper and lower wing as soft filled quads + glowing outline
			for (int part = 0; part < 2; part++) {
				float len = part == 0 ? 0.32f : 0.22f;
				float along = part == 0 ? 0.1f : -0.1f;
				V3 root = c.add(fwd, along * 0.3f);
				V3 tip = root.add(wingDir, len).add(fwd, along);
				V3 mid1 = root.add(wingDir, len * 0.55f).add(fwd, along + 0.09f);
				V3 mid2 = root.add(wingDir, len * 0.55f).add(fwd, along - 0.09f);
				int fill = Colors.argb(part == 0 ? c1 : c2, 0.32f * a);
				int fillRoot = Colors.argb(0xFFFFFF, 0.4f * a);
				g.quad(root.x(), root.y(), root.z(), fillRoot, mid1.x(), mid1.y(), mid1.z(), fill, tip.x(), tip.y(), tip.z(), fill, mid2.x(), mid2.y(), mid2.z(), fill);
				root.line(g, mid1, 0.03f, part == 0 ? c1 : c2, a * 0.8f);
				mid1.line(g, tip, 0.03f, part == 0 ? c1 : c2, a * 0.8f);
				tip.line(g, mid2, 0.03f, part == 0 ? c1 : c2, a * 0.8f);
				mid2.line(g, root, 0.03f, part == 0 ? c1 : c2, a * 0.8f);
			}
		}
		c.add(fwd, 0.12f).line(g, c.add(fwd, -0.12f), 0.04f, 0xFFFFFF, a);
		g.disc(c.x(), c.y(), c.z(), 0.4f, c1, 0.12f * a, 12);
	}
}
