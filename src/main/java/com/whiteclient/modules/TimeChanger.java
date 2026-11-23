package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/**
 * Time &amp; Weather: pick the sky you like (sunset, night...) and turn rain off or on, only on your screen.
 * Nothing changes on the server or for other players.
 */
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
	public void onTick(MinecraftClient mc) {
		ClientWorld w = mc.world;
		if (w == null) {
			return;
		}
		long t = target();
		if (t >= 0) {
			long days = Math.floorDiv(w.getTimeOfDay(), 24000L);
			w.setTime(w.getTime(), days * 24000L + t, false);
		}
		switch (weather.get()) {
			case "Clear" -> {
				w.setRainGradient(0f);
				w.setThunderGradient(0f);
			}
			case "Rain" -> {
				w.setRainGradient(1f);
				w.setThunderGradient(0f);
			}
			case "Thunder" -> {
				w.setRainGradient(1f);
				w.setThunderGradient(1f);
			}
			default -> {
			}
		}
	}
}
