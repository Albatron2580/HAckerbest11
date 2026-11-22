package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;

/** Hold C (change it in Controls) to zoom in smoothly, like OptiFine zoom. */
public class Zoom extends Module {
	public final NumberSetting factor = add(new NumberSetting("Zoom", 4, 1.5, 12, 0.5, "x"));
	public final BoolSetting smooth = add(new BoolSetting("Smooth Animation", true));
	public final BoolSetting cinematic = add(new BoolSetting("Smooth Camera While Zoomed", true));

	/** Test hook. */
	public static boolean debugHeld;

	private static float current = 1f;
	private static long lastNanos = System.nanoTime();
	private boolean appliedCinematic;
	private boolean savedCinematic;

	public Zoom() {
		super("Zoom", "Hold C to zoom in", Category.WORLD, true);
	}

	private static Zoom get() {
		WhiteClient wc = WhiteClient.get();
		return wc == null ? null : wc.modules().get(Zoom.class);
	}

	public static boolean held() {
		Zoom z = get();
		if (z == null || !z.isEnabled()) {
			return false;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		return debugHeld || (mc.currentScreen == null && WhiteClient.get().zoomKey() != null && WhiteClient.get().zoomKey().isPressed());
	}

	/** Multiplier for the field of view (1 = normal). Called by a mixin every frame. */
	public static float fovScale() {
		Zoom z = get();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		float target = held() ? 1f / z.factor.getFloat() : 1f;
		if (z == null || !z.smooth.isOn()) {
			current = target;
		} else {
			current += (target - current) * Math.min(1f, dt * 14f);
			if (Math.abs(current - target) < 0.001f) {
				current = target;
			}
		}
		return current;
	}

	@Override
	public void onTick(MinecraftClient mc) {
		if (mc.options == null) {
			return;
		}
		boolean want = held() && cinematic.isOn();
		if (want && !appliedCinematic) {
			savedCinematic = mc.options.smoothCameraEnabled;
			mc.options.smoothCameraEnabled = true;
			appliedCinematic = true;
		} else if (!want && appliedCinematic) {
			mc.options.smoothCameraEnabled = savedCinematic;
			appliedCinematic = false;
		}
	}

	@Override
	protected void onDisable() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (appliedCinematic && mc.options != null) {
			mc.options.smoothCameraEnabled = savedCinematic;
			appliedCinematic = false;
		}
		current = 1f;
	}
}
