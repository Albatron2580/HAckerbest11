package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/** Move, turn and resize the item in your hand (first person). */
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
		super("Hand View", "Move and resize your held item", Category.WORLD, false);
	}

	/** Mixin hook at the start of first-person item rendering. */
	public static void begin(PlayerEntity player, Hand hand, MatrixStack matrices) {
		WhiteClient wc = WhiteClient.get();
		HandView hv = wc == null ? null : wc.modules().get(HandView.class);
		if (hv == null || !hv.isEnabled()) {
			return;
		}
		Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == Arm.RIGHT ? 1f : -1f;
		matrices.push();
		depth++;
		matrices.translate(hv.x.getFloat() * side, hv.y.getFloat(), hv.z.getFloat());
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(hv.rotX.getFloat()));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(hv.rotY.getFloat() * side));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(hv.rotZ.getFloat() * side));
		float s = hv.scale.getFloat();
		matrices.scale(s, s, s);
	}

	/** Mixin hook at the end of first-person item rendering. */
	public static void end(MatrixStack matrices) {
		if (depth > 0) {
			depth--;
			matrices.pop();
		}
	}
}
