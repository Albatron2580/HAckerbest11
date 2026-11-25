package com.whiteclient.modules;

import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;

public class Zoom extends Module {
	public final NumberSetting factor = add(new NumberSetting("Zoom", 4, 1.5, 12, 0.5, "x"));
	public final BoolSetting smooth = add(new BoolSetting("Smooth Animation", true));
	public final BoolSetting cinematic = add(new BoolSetting("Smooth Camera While Zoomed", true));

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
		Minecraft mc = Minecraft.getInstance();
		return debugHeld || (mc.screen == null && WhiteClient.get().zoomKey() != null && WhiteClient.get().zoomKey().isDown());
	}

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
	public void onTick(Minecraft mc) {
		if (mc.options == null) {
			return;
		}
		boolean want = held() && cinematic.isOn();
		if (want && !appliedCinematic) {
			savedCinematic = mc.options.smoothCamera;
			mc.options.smoothCamera = true;
			appliedCinematic = true;
		} else if (!want && appliedCinematic) {
			mc.options.smoothCamera = savedCinematic;
			appliedCinematic = false;
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (appliedCinematic && mc.options != null) {
			mc.options.smoothCamera = savedCinematic;
			appliedCinematic = false;
		}
		current = 1f;
	}
}
