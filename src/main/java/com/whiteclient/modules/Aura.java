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

public class Aura extends Module {
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final NumberSetting radius = add(new NumberSetting("Radius", 1.15, 0.4, 2.5, 0.05));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", 0.42, 0.05, 1.0, 0.01));
	public final NumberSetting pulse = add(new NumberSetting("Pulse", 0.5, 0.0, 1.0, 0.05));
	public final BoolSetting bodyGlow = add(new BoolSetting("Glow Over Body", true));
	public final BoolSetting sparkles = add(new BoolSetting("Sparkles", true));
	public final BoolSetting others = add(new BoolSetting("Show On Others", false));

	public Aura() {
		super("Aura", "Soft glow around your player", Category.COSMETICS, true);
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		for (PlayerEntity player : CosmeticTargets.collect(ctx, true, false, others.isOn(), 48)) {
			render(ctx, player);
		}
	}

	private void render(WorldContext ctx, PlayerEntity player) {
		Vec3d p = player.getLerpedPos(ctx.tickDelta);
		float h = player.getHeight();
		V3 center = new V3(ctx.rx(p.x), ctx.ry(p.y + h * 0.55f), ctx.rz(p.z));
		GlowBatch g = ctx.glow;
		int rgb = color.rgb();
		float pul = 1f + pulse.getFloat() * 0.22f * (float) Math.sin(ctx.time * 2.6f);
		float a = intensity.getFloat() * pul;

		V3 toCam = center.mul(-1).normalize();
		V3 front = bodyGlow.isOn() ? center.add(toCam, 0.5f) : center.add(toCam, -0.35f);
		g.disc(front.x(), front.y(), front.z(), radius.getFloat() * pul, rgb, a, 24);
		g.disc(front.x(), front.y(), front.z(), radius.getFloat() * 0.45f, rgb, a * 0.5f, 16);

		if (sparkles.isOn()) {
			for (int i = 0; i < 7; i++) {
				double ang = ctx.time * (0.9 + i * 0.13) + i * 2.1;
				float rr = 0.55f + 0.15f * (float) Math.sin(ctx.time * 1.3 + i);
				float yy = (float) Math.sin(ctx.time * 0.8 + i * 1.7) * h * 0.45f;
				V3 sp = center.add(new V3((float) Math.cos(ang) * rr, yy, (float) Math.sin(ang) * rr), 1f);
				float tw = 0.55f + 0.45f * (float) Math.sin(ctx.time * 5 + i * 3);
				g.disc(sp.x(), sp.y(), sp.z(), 0.07f, rgb, 0.9f * tw, 8);
			}
		}
	}
}
