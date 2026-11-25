package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public class TimeChanger extends Module {
	public final ModeSetting time = add(new ModeSetting("Time", "Sunset", "Normal", "Sunrise", "Day", "Sunset", "Night", "Midnight"));
	public final ModeSetting weather = add(new ModeSetting("Weather", "Normal", "Normal", "Clear", "Rain", "Thunder"));

	public TimeChanger() {
		super("Time & Weather", "Your own sky: sunset, night, clear or rainy (only on your screen)", Category.WORLD, false);
	}

	private long target() {
		return switch (time.get()) {
			case "Sunrise" -> 23200L;
			case "Day" -> 6000L;
			case "Sunset" -> 12500L;
			case "Night" -> 15000L;
			case "Midnight" -> 18000L;
			default -> -1L;
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		ClientLevel w = mc.level;
		if (w == null) {
			return;
		}
		long t = target();
		//#if MC < 26.1
		if (t >= 0) {
			long days = Math.floorDiv(w.getDayTime(), 24000L);
			w.setTimeFromServer(w.getGameTime(), days * 24000L + t, false);
		}
		//#endif
		switch (weather.get()) {
			case "Clear" -> {
				w.setRainLevel(0f);
				w.setThunderLevel(0f);
			}
			case "Rain" -> {
				w.setRainLevel(1f);
				w.setThunderLevel(0f);
			}
			case "Thunder" -> {
				w.setRainLevel(1f);
				w.setThunderLevel(1f);
			}
			default -> {
			}
		}
	}
}
