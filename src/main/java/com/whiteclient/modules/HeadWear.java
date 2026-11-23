package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.CosmeticTargets;
import com.whiteclient.render.OrganicMesh;
import com.whiteclient.render.V3;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/** 3D head cosmetics: crown, cat ears, bunny ears, devil horns, headphones and a flower crown. */
public class HeadWear extends Module {
	public static final String[] STYLES = {"Crown", "Cat Ears", "Bunny Ears", "Devil Horns", "Headphones", "Flower Crown"};

	public final ModeSetting style = add(new ModeSetting("Style", "Crown", STYLES));
	public final BoolSetting natural = add(new BoolSetting("Natural Colors", true));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF).visibleWhen(() -> !natural.isOn()));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.6, 1.6, 0.05, "x"));
	public final NumberSetting glow = add(new NumberSetting("Glow", 0.6, 0.0, 2.0, 0.05, "x"));
	public final BoolSetting firstPerson = add(new BoolSetting("Show In First Person", false));
	public final BoolSetting others = add(new BoolSetting("Show On Others", false));

	private final OrganicMesh mesh = new OrganicMesh();

	public HeadWear() {
		super("Head Wear", "3D crown, ears, horns or headphones on your head", Category.COSMETICS, false);
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		boolean any = false;
		for (PlayerEntity p : CosmeticTargets.collect(ctx, true, firstPerson.isOn(), others.isOn(), 48)) {
			draw(ctx, p);
			any = true;
		}
		if (any) {
			mesh.draw(ctx.consumers, ctx.matrices.peek().getPositionMatrix());
		}
	}

	private int main(int natural) {
		return this.natural.isOn() ? natural : color.rgb();
	}

	private void draw(WorldContext ctx, PlayerEntity player) {
		float td = ctx.tickDelta;
		Vec3d p = player.getLerpedPos(td);
		float yaw = player.getYaw(td);
		float pitch = player.getPitch(td);
		V3 r0 = V3.right(yaw), f0 = V3.forward(yaw);
		float tilt = (float) Math.toRadians(pitch);
		// the head turns around the neck: forward/up tip down when you look down
		V3 u = V3.UP.rotate(r0, -tilt), f = f0.rotate(r0, -tilt), r = r0;
		float sc = player.getScale() * 0.9375f * size.getFloat();
		V3 neck = new V3(ctx.rx(p.x), ctx.ry(p.y + player.getHeight() - 0.39), ctx.rz(p.z));
		V3 top = neck.add(u, 0.47f * player.getScale() * 0.9375f + 0.02f);
		float h = 0.235f * player.getScale() * 0.9375f; // half head size
		BlockPos bp = BlockPos.ofFloored(p.x, p.y + 1.6, p.z);
		long time = ctx.client.world.getTimeOfDay() % 24000L;
		float day = 0.5f + 0.5f * (float) Math.cos((time - 6000L) / 24000.0 * Math.PI * 2);
		float sky = ctx.client.world.getLightLevel(LightType.SKY, bp) / 15f * (0.2f + 0.8f * day);
		float block = ctx.client.world.getLightLevel(LightType.BLOCK, bp) / 15f;
		float light = 0.32f + 0.68f * Math.max(sky, block);
		float t = ctx.time;
		switch (style.get()) {
			case "Cat Ears" -> {
				int c = main(0x2A2A30);
				mesh.setup(light, Colors.mix(c, 0xFFFFFF, 0.6f), 0.5f * glow.getFloat());
				for (int e = -1; e <= 1; e += 2) {
					float twitch = Math.sin(t * 0.8 + e) > 0.96 ? 0.15f : 0f;
					V3 base = top.add(r, e * h * 0.6f).add(f, -h * 0.05f).add(u, -0.01f);
					V3 tip = base.add(u, 0.2f * sc).add(r, e * (0.06f + twitch * 0.1f) * sc).add(f, -0.02f * sc);
					mesh.cone(base, tip, r, 0.11f * sc, 0.035f * sc, c, 0xF2A7B8, f);
				}
			}
			case "Bunny Ears" -> {
				int c = main(0xF4F4F6);
				mesh.setup(light, Colors.mix(c, 0xFFFFFF, 0.5f), 0.5f * glow.getFloat());
				for (int e = -1; e <= 1; e += 2) {
					float sway = (float) Math.sin(t * 1.6f + e) * 0.08f;
					V3 dir = u.add(f, -0.15f + sway).add(r, e * 0.12f).normalize();
					V3 base = top.add(r, e * h * 0.4f);
					V3 c0 = base.add(dir, 0.2f * sc);
					V3 w = r.sub(dir.mul(r.dot(dir))).normalize();
					V3 thin = dir.cross(w).normalize();
					if (thin.dot(f) < 0) {
						thin = thin.mul(-1f);
					}
					mesh.ellipsoid(c0, w, dir, thin, 0.055f * sc, 0.21f * sc, 0.022f * sc, c);
					mesh.ellipsoid(c0.add(thin, 0.012f * sc), w, dir, thin, 0.032f * sc, 0.17f * sc, 0.014f * sc, 0xF4B3C6);
				}
			}
			case "Devil Horns" -> {
				int c = main(0xB3122A);
				mesh.setup(light, 0xFF4A5A, 0.8f * glow.getFloat());
				for (int e = -1; e <= 1; e += 2) {
					V3 a = top.add(r, e * h * 0.55f).add(f, h * 0.35f).add(u, -0.02f);
					V3 b = a.add(u, 0.1f * sc).add(r, e * 0.06f * sc).add(f, 0.01f * sc);
					V3 cTip = b.add(u, 0.08f * sc).add(r, e * -0.02f * sc).add(f, -0.06f * sc);
					mesh.tube(a, b, 0.045f * sc, 0.03f * sc, c);
					mesh.sphere(b, 0.03f * sc, c);
					mesh.cone(b, cTip, r, 0.03f * sc, 0.03f * sc, c, c, null);
				}
			}
			case "Headphones" -> {
				int c = main(0x1C1C22);
				mesh.setup(light, 0x8FD3FF, 0.8f * glow.getFloat());
				V3 prev = null;
				for (int i = 0; i <= 10; i++) {
					double a = Math.PI * i / 10;
					V3 pt = neck.add(u, h * 1.25f).add(r, (float) Math.cos(a) * (h + 0.04f)).add(u, (float) Math.sin(a) * (h * 0.95f + 0.04f));
					if (prev != null) {
						mesh.tube(prev, pt, 0.022f, 0.022f, c);
					}
					prev = pt;
				}
				for (int e = -1; e <= 1; e += 2) {
					V3 cup = neck.add(u, h * 1.05f).add(r, e * (h + 0.045f));
					mesh.ellipsoid(cup, r, u, f, 0.035f, 0.09f, 0.075f, c, c, 0.6f, 0xFF);
					ctx.glow.disc(cup.x() + r.x() * e * 0.04f, cup.y(), cup.z() + r.z() * e * 0.04f, 0.05f, 0x8FD3FF, 0.8f * glow.getFloat(), 10);
				}
			}
			case "Flower Crown" -> {
				mesh.setup(light, 0xFFE3F0, 0.4f * glow.getFloat());
				int[] petals = {0xFF8FB8, 0xFFFFFF, 0xFFD45C, 0xB89BFF, 0x8FD3FF};
				int n = 9;
				for (int i = 0; i < n; i++) {
					double a = Math.PI * 2 * i / n + t * 0.05;
					V3 c = top.add(u, -0.06f).add(r, (float) Math.cos(a) * (h + 0.015f)).add(f, (float) Math.sin(a) * (h + 0.015f));
					int col = natural.isOn() ? petals[i % petals.length] : color.rgb();
					for (int k = 0; k < 5; k++) {
						double pa = Math.PI * 2 * k / 5 + i;
						V3 pc = c.add(r, (float) Math.cos(pa) * 0.03f * sc).add(f, (float) Math.sin(pa) * 0.03f * sc).add(u, 0.012f);
						mesh.ellipsoid(pc, r, u, f, 0.024f * sc, 0.012f * sc, 0.024f * sc, col);
					}
					mesh.sphere(c.add(u, 0.02f), 0.014f * sc, 0xFFD45C);
					mesh.ellipsoid(c.add(u, -0.01f), r, u, f, 0.03f * sc, 0.01f * sc, 0.03f * sc, 0x5DBB63);
				}
			}
			default -> { // crown
				int gold = main(0xF2C14E);
				mesh.setup(light, 0xFFE9A8, 0.7f * glow.getFloat());
				int n = 8;
				float rad = h * 0.92f;
				V3 base = top.add(u, -0.035f);
				for (int i = 0; i < 24; i++) {
					double a0 = Math.PI * 2 * i / 24, a1 = Math.PI * 2 * (i + 1) / 24;
					V3 p0 = base.add(r, (float) Math.cos(a0) * rad).add(f, (float) Math.sin(a0) * rad);
					V3 p1 = base.add(r, (float) Math.cos(a1) * rad).add(f, (float) Math.sin(a1) * rad);
					mesh.tube(p0, p1, 0.028f * sc, 0.028f * sc, gold);
				}
				int[] gems = {0xE0283C, 0x2F7BFF, 0x2FCB71, 0xB04BFF};
				for (int i = 0; i < n; i++) {
					double a = Math.PI * 2 * i / n;
					V3 p0 = base.add(r, (float) Math.cos(a) * rad).add(f, (float) Math.sin(a) * rad);
					V3 tip = p0.add(u, 0.13f * sc);
					mesh.cone(p0, tip, r.mul((float) -Math.sin(a)).add(f, (float) Math.cos(a)).normalize(), 0.035f * sc, 0.02f * sc, gold, gold, null);
					mesh.glossySphere(tip.add(u, 0.012f * sc), 0.016f * sc, gold, 1.4f);
					if (i % 2 == 0) {
						V3 gem = p0.add(u, 0.035f * sc).add(r, (float) Math.cos(a) * 0.022f).add(f, (float) Math.sin(a) * 0.022f);
						mesh.glossySphere(gem, 0.02f * sc, gems[(i / 2) % gems.length], 1.6f);
					}
				}
			}
		}
	}
}
