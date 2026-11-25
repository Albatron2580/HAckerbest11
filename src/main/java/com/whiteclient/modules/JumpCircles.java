package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.CosmeticTargets;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class JumpCircles extends Module {
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final BoolSetting rainbow = add(new BoolSetting("Rainbow", false));
	public final NumberSetting size = add(new NumberSetting("Size", 1.1, 0.4, 2.5, 0.05));
	public final NumberSetting duration = add(new NumberSetting("Duration", 0.9, 0.3, 2.0, 0.05, "s"));
	public final BoolSetting landing = add(new BoolSetting("On Landing", true));
	public final BoolSetting others = add(new BoolSetting("Show On Others", false));

	private final Map<Integer, Boolean> onGround = new HashMap<>();
	private final List<float[]> circles = new ArrayList<>();

	public JumpCircles() {
		super("Jump Circles", "Glowing rings when you jump and land", Category.COSMETICS, true);
	}

	public void spawn(double x, double y, double z, float time, float mul) {
		circles.add(new float[]{(float) x, (float) y, (float) z, time, mul, (float) Math.random()});
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			onGround.clear();
			circles.clear();
			return;
		}
		float now = com.whiteclient.WhiteClient.get().time();
		for (Player p : mc.level.players()) {
			if (p != mc.player && (!others.isOn() || !CosmeticTargets.visible(p) || p.distanceToSqr(mc.player) > 40 * 40)) {
				continue;
			}
			boolean ground = p.onGround();
			Boolean was = onGround.put(p.getId(), ground);
			if (was == null) {
				continue;
			}
			if (was && !ground && p.getDeltaMovement().y > 0.05) {
				spawn(p.getX(), p.getY() + 0.02, p.getZ(), now, 1f);
			} else if (!was && ground && landing.isOn()) {
				spawn(p.getX(), p.getY() + 0.02, p.getZ(), now, 0.75f);
			}
		}
		if (circles.size() > 64) {
			circles.subList(0, circles.size() - 64).clear();
		}
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		GlowBatch g = ctx.glow;
		float life = duration.getFloat();
		circles.removeIf(c -> ctx.time - c[3] > life);
		for (float[] c : circles) {
			float u = (ctx.time - c[3]) / life;
			float ease = 1f - (1f - u) * (1f - u) * (1f - u);
			float r = size.getFloat() * c[4] * (0.2f + 0.8f * ease);
			float a = (1f - u);
			int rgb = rainbow.isOn() ? Colors.hsv(c[5] + u * 0.3f, 0.55f, 1f) : color.rgb();
			float x = c[0] - (float) ctx.cam.x, y = c[1] - (float) ctx.cam.y, z = c[2] - (float) ctx.cam.z;
			g.flatRing(x, y, z, r, 0.06f, rgb, 0.9f * a);
			g.flatRing(x, y, z, r * 0.62f, 0.035f, rgb, 0.5f * a * (1f - u));
			g.flatDisc(x, y, z, r, rgb, 0.12f * a);
		}
	}
}
