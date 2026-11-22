package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.V3;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import com.whiteclient.render.CosmeticTargets;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Spiky glowing line-art wings on your back. */
public class Wings extends Module {
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.4, 2.0, 0.05, "x"));
	public final NumberSetting spread = add(new NumberSetting("Spread", 26, 0, 70, 1, "°"));
	public final NumberSetting flapAngle = add(new NumberSetting("Flap Angle", 14, 0, 45, 1, "°"));
	public final NumberSetting flapSpeed = add(new NumberSetting("Flap Speed", 1.0, 0.0, 3.0, 0.1, "x"));
	public final NumberSetting glow = add(new NumberSetting("Glow", 1.0, 0.2, 2.0, 0.05, "x"));
	public final NumberSetting fill = add(new NumberSetting("Fill", 0.08, 0.0, 0.4, 0.01));
	public final BoolSetting feathers = add(new BoolSetting("Feather Lines", true));
	public final BoolSetting others = add(new BoolSetting("Show On Others", true));
	public final NumberSetting range = add(new NumberSetting("Others Range", 48, 8, 128, 4));

	/** Wing outline in (outward, up) units; index 0 is the root. */
	private static final float[][] SHAPE = {
			{0.04f, 0.06f},
			{0.30f, 0.38f}, {0.62f, 0.62f}, {0.95f, 0.80f}, {1.24f, 0.93f},
			{1.02f, 0.66f}, {1.20f, 0.52f},
			{0.93f, 0.42f}, {1.06f, 0.21f},
			{0.79f, 0.20f}, {0.87f, -0.03f},
			{0.61f, 0.04f}, {0.64f, -0.25f},
			{0.43f, -0.08f}, {0.36f, -0.37f},
			{0.22f, -0.12f}, {0.05f, -0.10f}
	};
	/** Indices of the spike tips (for the feather lines). */
	private static final int[] TIPS = {4, 6, 8, 10, 12, 14};

	public Wings() {
		super("Wings", "Spiky glowing wings on your back", Category.COSMETICS, true);
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		for (PlayerEntity player : CosmeticTargets.collect(ctx, true, false, others.isOn(), range.get())) {
			render(ctx, player);
		}
	}

	private void render(WorldContext ctx, PlayerEntity player) {
		float td = ctx.tickDelta;
		Vec3d p = player.getLerpedPos(td);
		float bodyYaw = MathHelper.lerpAngleDegrees(td, player.lastBodyYaw, player.bodyYaw);
		V3 fwd = V3.forward(bodyYaw);
		V3 back = fwd.mul(-1);
		V3 right = V3.right(bodyYaw);
		boolean sneaking = player.isInSneakingPose();

		float s = size.getFloat();
		float phase = (player.getId() % 97) * 0.37f;
		float flap = (float) Math.sin((ctx.time + phase) * Math.PI * 1.4 * flapSpeed.getFloat());
		float sweep = (float) Math.toRadians(spread.getFloat() + flapAngle.getFloat() * flap);
		float lean = (float) Math.toRadians(sneaking ? 28 : 10);
		V3 vDir = V3.UP.mul((float) Math.cos(lean)).add(back, (float) Math.sin(lean));

		float shoulder = player.getHeight() - (sneaking ? 0.42f : 0.5f);
		V3 spine = new V3(ctx.rx(p.x), ctx.ry(p.y + shoulder), ctx.rz(p.z)).add(back, sneaking ? 0.3f : 0.17f);

		int rgb = color.rgb();
		float intensity = glow.getFloat();
		for (int side = -1; side <= 1; side += 2) {
			V3 out = right.mul(side);
			V3 uDir = out.mul((float) Math.cos(sweep)).add(back, (float) Math.sin(sweep)).normalize();
			V3 root = spine.add(out, 0.07f);
			V3[] pts = new V3[SHAPE.length];
			for (int i = 0; i < SHAPE.length; i++) {
				pts[i] = root.add(uDir, SHAPE[i][0] * s).add(vDir, SHAPE[i][1] * s);
			}
			drawWing(ctx.glow, pts, rgb, intensity, s);
		}
	}

	private void drawWing(GlowBatch g, V3[] pts, int rgb, float intensity, float s) {
		float width = 0.075f * (0.6f + 0.4f * s);
		// outline (closed)
		for (int i = 0; i < pts.length; i++) {
			V3 a = pts[i];
			V3 b = pts[(i + 1) % pts.length];
			a.line(g, b, width, rgb, intensity);
		}
		// feather spines from the root to every spike tip
		if (feathers.isOn()) {
			for (int tip : TIPS) {
				pts[0].line(g, pts[tip], width * 0.7f, rgb, intensity * 0.35f);
			}
		}
		// faint membrane fill (triangle fan from the root)
		float f = fill.getFloat();
		if (f > 0.001f) {
			int c = Colors.argb(rgb, f);
			V3 r = pts[0];
			for (int i = 1; i < pts.length - 1; i++) {
				V3 a = pts[i];
				V3 b = pts[i + 1];
				g.quad(r.x(), r.y(), r.z(), c, r.x(), r.y(), r.z(), c, a.x(), a.y(), a.z(), c, b.x(), b.y(), b.z(), c);
			}
		}
	}
}
