package com.whiteclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

public class HandView extends Module {
	public final NumberSetting x = add(new NumberSetting("X", 0, -0.6, 0.6, 0.01));
	public final NumberSetting y = add(new NumberSetting("Y", 0, -0.6, 0.6, 0.01));
	public final NumberSetting z = add(new NumberSetting("Z", 0, -0.6, 0.6, 0.01));
	public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.4, 1.6, 0.02, "x"));
	public final NumberSetting rotX = add(new NumberSetting("Rotate X", 0, -90, 90, 1, "°"));
	public final NumberSetting rotY = add(new NumberSetting("Rotate Y", 0, -90, 90, 1, "°"));
	public final NumberSetting rotZ = add(new NumberSetting("Rotate Z", 0, -90, 90, 1, "°"));

	private static int depth;

	public HandView() {
		super("Hand View", "Move and resize your held item", Category.WORLD, true);
	}

	public static void begin(Player player, InteractionHand hand, PoseStack matrices) {
		WhiteClient wc = WhiteClient.get();
		HandView hv = wc == null ? null : wc.modules().get(HandView.class);
		if (hv == null || !hv.isEnabled()) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		matrices.pushPose();
		depth++;
		matrices.translate(hv.x.getFloat() * side, hv.y.getFloat(), hv.z.getFloat());
		matrices.mulPose(Axis.XP.rotationDegrees(hv.rotX.getFloat()));
		matrices.mulPose(Axis.YP.rotationDegrees(hv.rotY.getFloat() * side));
		matrices.mulPose(Axis.ZP.rotationDegrees(hv.rotZ.getFloat() * side));
		float s = hv.scale.getFloat();
		matrices.scale(s, s, s);
	}

	public static void end(PoseStack matrices) {
		if (depth > 0) {
			depth--;
			matrices.popPose();
		}
	}
}
