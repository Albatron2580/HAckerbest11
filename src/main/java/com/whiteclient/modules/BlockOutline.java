package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.Colors;
import com.whiteclient.render.GlowBatch;
import com.whiteclient.render.WorldContext;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

public class BlockOutline extends Module {
	public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFF));
	public final BoolSetting rainbow = add(new BoolSetting("Rainbow", false));
	public final BoolSetting fill = add(new BoolSetting("Fill", true));
	public final NumberSetting fillOpacity = add(new NumberSetting("Fill Opacity", 0.12, 0.02, 0.5, 0.01));
	public final NumberSetting thickness = add(new NumberSetting("Thickness", 1.0, 0.4, 3.0, 0.1, "x"));
	public final BoolSetting pulse = add(new BoolSetting("Pulse", true));

	public BlockOutline() {
		super("Block Outline", "Glowing outline on the block you look at", Category.WORLD, true);
	}

	@Override
	public void renderWorld(WorldContext ctx) {
		if (!(ctx.client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || ctx.client.world == null) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = ctx.client.world.getBlockState(pos);
		VoxelShape shape = state.getOutlineShape(ctx.client.world, pos);
		if (shape.isEmpty()) {
			return;
		}
		GlowBatch g = ctx.glow;
		float ox = ctx.rx(pos.getX()), oy = ctx.ry(pos.getY()), oz = ctx.rz(pos.getZ());
		int rgb = rainbow.isOn() ? Colors.hsv(ctx.time * 0.15f, 0.55f, 1f) : color.rgb();
		float a = pulse.isOn() ? 0.8f + 0.2f * (float) Math.sin(ctx.time * 4) : 1f;
		float w = 0.035f * thickness.getFloat();
		float e = 0.003f;
		shape.forEachEdge((x1, y1, z1, x2, y2, z2) -> g.glowLine(
				ox + (float) x1 + (x1 < 0.5 ? -e : e), oy + (float) y1 + (y1 < 0.5 ? -e : e), oz + (float) z1 + (z1 < 0.5 ? -e : e),
				ox + (float) x2 + (x2 < 0.5 ? -e : e), oy + (float) y2 + (y2 < 0.5 ? -e : e), oz + (float) z2 + (z2 < 0.5 ? -e : e),
				w, rgb, a));
		if (fill.isOn()) {
			int c = Colors.argb(rgb, fillOpacity.getFloat() * a);
			for (Box b : shape.getBoundingBoxes()) {
				float x1 = ox + (float) b.minX - e, y1 = oy + (float) b.minY - e, z1 = oz + (float) b.minZ - e;
				float x2 = ox + (float) b.maxX + e, y2 = oy + (float) b.maxY + e, z2 = oz + (float) b.maxZ + e;
				g.quad(x1, y1, z1, c, x2, y1, z1, c, x2, y1, z2, c, x1, y1, z2, c);
				g.quad(x1, y2, z1, c, x2, y2, z1, c, x2, y2, z2, c, x1, y2, z2, c);
				g.quad(x1, y1, z1, c, x2, y1, z1, c, x2, y2, z1, c, x1, y2, z1, c);
				g.quad(x1, y1, z2, c, x2, y1, z2, c, x2, y2, z2, c, x1, y2, z2, c);
				g.quad(x1, y1, z1, c, x1, y1, z2, c, x1, y2, z2, c, x1, y2, z1, c);
				g.quad(x2, y1, z1, c, x2, y1, z2, c, x2, y2, z2, c, x2, y2, z1, c);
			}
		}
	}
}
