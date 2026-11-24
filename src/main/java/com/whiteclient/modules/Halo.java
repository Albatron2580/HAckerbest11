package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.V3;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.render.CosmeticTargets;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Halo extends Module {
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final NumberSetting radius = add(new NumberSetting("Radius", 0.34, 0.15, 0.6, 0.01));
	public final NumberSetting height = add(new NumberSetting("Height", 0.14, 0.0, 0.8, 0.01));
	public final NumberSetting spikes = add(new NumberSetting("Spikes", 10, 0, 24, 1));
	public final NumberSetting spikeLength = add(new NumberSetting("Spike Length", 0.16, 0.0, 0.5, 0.01));
	public final NumberSetting spin = add(new NumberSetting("Spin", 0.5, 0.0, 3.0, 0.1, "x"));
	public final NumberSetting glow = add(new NumberSetting("Glow", 1.3, 0.2, 2.0, 0.05, "x"));
	public final BoolSetting followHead = add(new BoolSetting("Follow Head", true));
	public final BoolSetting firstPerson = add(new BoolSetting("Show In First Person", false));
	public final BoolSetting others = add(new BoolSetting("Show On Others", true));
	public final NumberSetting range = add(new NumberSetting("Others Range", 48, 8, 128, 4));

	public Halo() {
		super("Halo", "Glowing halo with spikes above your head", Category.COSMETICS, true);
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		for (PlayerEntity player : CosmeticTargets.collect(ctx, true, firstPerson.isOn(), others.isOn(), range.get())) {
			render(ctx, player);
		}
	}

	private void render(WorldContext ctx, PlayerEntity player) {
		float td = ctx.tickDelta;
		Vec3d p = player.getLerpedPos(td);
		float yaw = player.getYaw(td);
		float pitch = player.getPitch(td);

		V3 fwd = V3.forward(yaw);
		V3 right = V3.right(yaw);
		float tilt = followHead.isOn() ? (float) Math.toRadians(-pitch * 0.85f) : 0f;
		V3 up = V3.UP.rotate(right, tilt);
		V3 ringX = right;
		V3 ringZ = fwd.rotate(right, tilt);

		V3 neck = new V3(ctx.rx(p.x), ctx.ry(p.y + player.getHeight() - 0.32), ctx.rz(p.z));
		V3 center = neck.add(up, 0.36f + height.getFloat());

		GlowBatch g = ctx.glow;
		int rgb = color.rgb();
		float r = radius.getFloat();
		float intensity = glow.getFloat();
		float spinAngle = ctx.time * spin.getFloat();
		int segments = 40;

		V3 prev = null;
		for (int i = 0; i <= segments; i++) {
			double a = Math.PI * 2 * i / segments + spinAngle;
			V3 pt = center.add(ringX, (float) Math.cos(a) * r).add(ringZ, (float) Math.sin(a) * r);
			if (prev != null) {
				prev.line(g, pt, 0.15f, rgb, intensity);
			}
			prev = pt;
		}

		int count = spikes.getInt();
		float len = spikeLength.getFloat();
		for (int k = 0; k < count && len > 0.001f; k++) {
			double a = Math.PI * 2 * k / count + spinAngle;
			V3 radial = ringX.mul((float) Math.cos(a)).add(ringZ, (float) Math.sin(a));
			V3 base = center.add(radial, r);
			float l = len * ((k % 2 == 0) ? 1f : 0.6f) * (0.9f + 0.1f * (float) Math.sin(ctx.time * 3 + k));
			V3 tip = base.add(up, l).add(radial, l * 0.15f);
			base.line(g, tip, 0.07f, rgb, intensity * 0.9f);
		}

		g.disc(center.x(), center.y(), center.z(), r * 2.6f, rgb, 0.14f * intensity, 16);
	}
}
