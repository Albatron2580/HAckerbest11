package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.CosmeticTargets;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Trail extends Module {
	public final ModeSetting mode = add(new ModeSetting("Style", "Ribbon", "Ribbon", "Sparkles", "Both"));
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final ColorSetting color2 = add(new ColorSetting("End Color", 0x9FB8FF));
	public final BoolSetting rainbow = add(new BoolSetting("Rainbow", false));
	public final NumberSetting length = add(new NumberSetting("Length", 22, 6, 60, 1, " ticks"));
	public final NumberSetting width = add(new NumberSetting("Width", 0.45, 0.1, 1.2, 0.05));
	public final ModeSetting heightMode = add(new ModeSetting("Height", "Waist", "Feet", "Waist", "Head"));
	public final NumberSetting glow = add(new NumberSetting("Glow", 1.0, 0.2, 2.0, 0.05, "x"));
	public final BoolSetting others = add(new BoolSetting("Show On Others", false));

	private final Map<Integer, ArrayDeque<Vec3d>> history = new HashMap<>();

	public Trail() {
		super("Trail", "A glowing light trail behind you", Category.COSMETICS, true);
	}

	@Override
	protected void onDisable() {
		history.clear();
	}

	private double offset(PlayerEntity p) {
		return switch (heightMode.get()) {
			case "Feet" -> 0.1;
			case "Head" -> p.getHeight() - 0.15;
			default -> p.getHeight() * 0.5;
		};
	}

	@Override
	public void onTick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) {
			history.clear();
			return;
		}
		int max = length.getInt();
		Map<Integer, Boolean> seen = new HashMap<>();
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p != mc.player && (!others.isOn() || !CosmeticTargets.visible(p) || p.squaredDistanceTo(mc.player) > 48 * 48)) {
				continue;
			}
			ArrayDeque<Vec3d> h = history.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
			h.addFirst(p.getEntityPos().add(0, offset(p), 0));
			while (h.size() > max) {
				h.removeLast();
			}
			seen.put(p.getId(), true);
		}
		history.keySet().removeIf(id -> !seen.containsKey(id));
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		MinecraftClient mc = ctx.client;
		if (mc.world == null) {
			return;
		}
		GlowBatch g = ctx.glow;
		float a = glow.getFloat();
		for (Map.Entry<Integer, ArrayDeque<Vec3d>> e : history.entrySet()) {
			ArrayDeque<Vec3d> h = e.getValue();
			if (h.size() < 2) {
				continue;
			}
			var ent = mc.world.getEntityById(e.getKey());
			if (!(ent instanceof PlayerEntity p)) {
				continue;
			}
			if (p == mc.player && !ctx.thirdPerson() && mode.is("Ribbon")) {
			}
			Vec3d head = p.getLerpedPos(ctx.tickDelta).add(0, offset(p), 0);
			int n = h.size();
			Vec3d prev = head;
			int i = 0;
			Iterator<Vec3d> it = h.iterator();
			it.next();
			float halfW = width.getFloat() / 2f;
			while (it.hasNext()) {
				Vec3d cur = it.next();
				i++;
				float u0 = (i - 1) / (float) (n - 1), u1 = i / (float) (n - 1);
				if (prev.squaredDistanceTo(cur) < 1.0e-5) {
					prev = cur;
					continue;
				}
				int c0 = colorAt(u0, ctx.time), c1 = colorAt(u1, ctx.time);
				float a0 = (1f - u0) * a, a1 = (1f - u1) * a;
				float x0 = ctx.rx(prev.x), y0 = ctx.ry(prev.y), z0 = ctx.rz(prev.z);
				float x1 = ctx.rx(cur.x), y1 = ctx.ry(cur.y), z1 = ctx.rz(cur.z);
				boolean ribbon = !mode.is("Sparkles") && (p != mc.player || ctx.thirdPerson() || u0 > 0.15f);
				if (ribbon) {
					float w0 = halfW * (1f - u0 * 0.7f), w1 = halfW * (1f - u1 * 0.7f);
					g.quad(x0, y0 + w0, z0, Colors.argb(c0, 0f), x0, y0, z0, Colors.argb(c0, 0.42f * a0),
							x1, y1, z1, Colors.argb(c1, 0.42f * a1), x1, y1 + w1, z1, Colors.argb(c1, 0f));
					g.quad(x0, y0, z0, Colors.argb(c0, 0.42f * a0), x0, y0 - w0, z0, Colors.argb(c0, 0f),
							x1, y1 - w1, z1, Colors.argb(c1, 0f), x1, y1, z1, Colors.argb(c1, 0.42f * a1));
					g.ribbon(x0, y0, z0, x1, y1, z1, 0.03f, Colors.argb(Colors.mix(c0, 0xFFFFFF, 0.5f), 0.8f * a0));
				}
				if (!mode.is("Ribbon") && (i % 2 == 0)) {
					float tw = 0.5f + 0.5f * (float) Math.sin(ctx.time * 9 + i * 1.7 + e.getKey());
					float jx = (float) Math.sin(i * 12.9898 + e.getKey()) * halfW * 0.6f;
					float jy = (float) Math.cos(i * 78.233) * halfW * 0.6f;
					g.disc(x1 + jx * 0.5f, y1 + jy, z1 - jx * 0.5f, 0.06f + 0.05f * tw, c1, 0.8f * a1 * tw, 8);
				}
				prev = cur;
			}
		}
	}

	private int colorAt(float u, float time) {
		if (rainbow.isOn()) {
			return Colors.hsv(u * 0.8f + time * 0.15f, 0.6f, 1f);
		}
		return Colors.mix(color.rgb(), color2.rgb(), u);
	}
}
