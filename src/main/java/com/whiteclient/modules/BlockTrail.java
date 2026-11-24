package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Iterator;

public class BlockTrail extends Module {
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final NumberSetting duration = add(new NumberSetting("Duration", 4.0, 0.5, 15.0, 0.5, "s"));
	public final NumberSetting fill = add(new NumberSetting("Fill", 0.20, 0.0, 0.5, 0.01));
	public final NumberSetting outline = add(new NumberSetting("Outline", 1.1, 0.0, 1.5, 0.05));
	public final NumberSetting squareSize = add(new NumberSetting("Square Size", 0.92, 0.4, 1.0, 0.01));
	public final BoolSetting onlyGround = add(new BoolSetting("Only On Ground", true));

	private record Step(int x, int z, double y, long time) {
	}

	private final ArrayDeque<Step> steps = new ArrayDeque<>();

	public BlockTrail() {
		super("Block Trail", "Glowing squares where you walk", Category.COSMETICS, true);
	}

	@Override
	protected void onDisable() {
		steps.clear();
	}

	@Override
	public void onTick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		if (onlyGround.isOn() && !player.isOnGround()) {
			return;
		}
		BlockPos pos = player.getSteppingPos();
		Step last = steps.peekLast();
		double y = player.getY();
		if (last != null && last.x() == pos.getX() && last.z() == pos.getZ() && Math.abs(last.y() - y) < 0.5) {
			return;
		}
		steps.addLast(new Step(pos.getX(), pos.getZ(), y, System.currentTimeMillis()));
		while (steps.size() > 500) {
			steps.removeFirst();
		}
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		long now = System.currentTimeMillis();
		long life = (long) (duration.get() * 1000);
		Iterator<Step> it = steps.iterator();
		while (it.hasNext()) {
			if (now - it.next().time() > life) {
				it.remove();
			} else {
				break;
			}
		}
		GlowBatch g = ctx.glow;
		int rgb = color.rgb();
		float fillA = fill.getFloat();
		float lineA = outline.getFloat();
		for (Step st : steps) {
			float age = (now - st.time()) / (float) life;
			float appear = Math.min(1f, (now - st.time()) / 160f);
			appear = 1f - (1f - appear) * (1f - appear);
			float alpha = (float) Math.pow(1f - age, 1.4);
			float half = (0.25f + (squareSize.getFloat() * 0.5f - 0.25f) * appear);
			float cx = ctx.rx(st.x() + 0.5);
			float cz = ctx.rz(st.z() + 0.5);
			float y = ctx.ry(st.y() + 0.02);
			float x0 = cx - half, x1 = cx + half, z0 = cz - half, z1 = cz + half;
			if (fillA > 0.001f) {
				int c = Colors.argb(rgb, fillA * alpha);
				g.quad(x0, y, z0, c, x1, y, z0, c, x1, y, z1, c, x0, y, z1, c);
			}
			if (lineA > 0.001f) {
				float w = 0.085f;
				float in = lineA * alpha;
				g.glowLine(x0, y, z0, x1, y, z0, w, rgb, in);
				g.glowLine(x1, y, z0, x1, y, z1, w, rgb, in);
				g.glowLine(x1, y, z1, x0, y, z1, w, rgb, in);
				g.glowLine(x0, y, z1, x0, y, z0, w, rgb, in);
			}
		}
	}
}
