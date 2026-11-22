package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.CosmeticTargets;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.V3;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** A glowing cone hat ("china hat") on your head. */
public class ChinaHat extends Module {
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final ColorSetting color2 = add(new ColorSetting("Tip Color", 0xBFD0FF));
	public final BoolSetting rainbow = add(new BoolSetting("Rainbow", false));
	public final NumberSetting radius = add(new NumberSetting("Radius", 0.6, 0.3, 1.0, 0.02));
	public final NumberSetting height = add(new NumberSetting("Height", 0.3, 0.1, 0.6, 0.02));
	public final NumberSetting spin = add(new NumberSetting("Spin", 0.6, 0.0, 3.0, 0.1, "x"));
	public final NumberSetting glow = add(new NumberSetting("Glow", 1.0, 0.2, 2.0, 0.05, "x"));
	public final BoolSetting firstPerson = add(new BoolSetting("Show In First Person", false));
	public final BoolSetting others = add(new BoolSetting("Show On Others", false));

	public ChinaHat() {
		super("China Hat", "A glowing cone hat on your head", Category.COSMETICS, false);
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		for (PlayerEntity p : CosmeticTargets.collect(ctx, true, firstPerson.isOn(), others.isOn(), 48)) {
			render(ctx, p);
		}
	}

	private void render(WorldContext ctx, PlayerEntity player) {
		float td = ctx.tickDelta;
		Vec3d p = player.getLerpedPos(td);
		float yaw = player.getYaw(td);
		float pitch = player.getPitch(td);
		V3 right = V3.right(yaw);
		float tilt = (float) Math.toRadians(-pitch * 0.6f);
		V3 up = V3.UP.rotate(right, tilt);
		V3 ringX = right;
		V3 ringZ = V3.forward(yaw).rotate(right, tilt);
		V3 neck = new V3(ctx.rx(p.x), ctx.ry(p.y + player.getHeight() - 0.32), ctx.rz(p.z));
		V3 base = neck.add(up, 0.38f);
		V3 apex = base.add(up, height.getFloat());
		float r = radius.getFloat();
		float a = glow.getFloat();
		GlowBatch g = ctx.glow;
		int seg = 36;
		float rot = ctx.time * spin.getFloat();
		V3 prev = null;
		for (int i = 0; i <= seg; i++) {
			double ang = Math.PI * 2 * i / seg + rot;
			int c1 = rainbow.isOn() ? Colors.hsv((float) (i / (double) seg + ctx.time * 0.1), 0.55f, 1f) : color.rgb();
			V3 pt = base.add(ringX, (float) Math.cos(ang) * r).add(ringZ, (float) Math.sin(ang) * r);
			if (prev != null) {
				prev.line(g, pt, 0.09f, c1, a * 1.2f);
				int rim = Colors.argb(c1, 0.24f * a);
				int tip = Colors.argb(color2.rgb(), 0.45f * a);
				g.quad(apex.x(), apex.y(), apex.z(), tip, apex.x(), apex.y(), apex.z(), tip, prev.x(), prev.y(), prev.z(), rim, pt.x(), pt.y(), pt.z(), rim);
				if (i % 4 == 0) {
					apex.line(g, pt, 0.025f, c1, a * 0.45f);
				}
			}
			prev = pt;
		}
		g.disc(apex.x(), apex.y(), apex.z(), 0.12f, 0xFFFFFF, 0.7f * a, 10);
	}
}
