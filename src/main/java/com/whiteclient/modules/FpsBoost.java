package com.whiteclient.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.particle.ParticlesMode;

/**
 * FPS Boost: switches the heavy vanilla video settings to fast ones (and puts your own settings back
 * when you turn it off). Pure settings, nothing hacky.
 */
public class FpsBoost extends Module {
	public final BoolSetting clouds = add(new BoolSetting("No Clouds", true));
	public final BoolSetting shadows = add(new BoolSetting("No Entity Shadows", true));
	public final BoolSetting particles = add(new BoolSetting("Fewer Particles", true));
	public final BoolSetting blend = add(new BoolSetting("Fast Biome Blend", true));
	public final BoolSetting unlock = add(new BoolSetting("Unlock FPS (VSync Off)", true));
	public final BoolSetting vignette = add(new BoolSetting("No Vignette", true));
	public final BoolSetting effects = add(new BoolSetting("Lighter Client Effects", true));
	/** Your own video settings from before the boost, so they can be put back. */
	private final TextSetting saved = add(new TextSetting("Saved Settings", "", 4000).visibleWhen(() -> false));

	private int timer;

	public FpsBoost() {
		super("FPS Boost", "Faster video settings in one click (turn off to get yours back)", Category.WORLD, true);
	}

	/** True when WHITECLIENT should draw its own effects a bit lighter (fewer particles / glows). */
	public static boolean lighter() {
		WhiteClient wc = WhiteClient.get();
		if (wc == null) {
			return false;
		}
		FpsBoost b = wc.modules().get(FpsBoost.class);
		return b != null && b.isEnabled() && b.effects.isOn();
	}

	private JsonObject savedJson() {
		try {
			String s = saved.get();
			return s == null || s.isBlank() ? new JsonObject() : JsonParser.parseString(s).getAsJsonObject();
		} catch (Throwable t) {
			return new JsonObject();
		}
	}

	@Override
	protected void onEnable() {
		timer = 0;
	}

	@Override
	public void onTick(MinecraftClient mc) {
		if (timer-- > 0) {
			return;
		}
		timer = 40;
		apply(mc.options);
	}

	private void apply(GameOptions o) {
		JsonObject s = savedJson();
		boolean changed = false;
		try {
			if (clouds.isOn() && o.getCloudRenderMode().getValue() != CloudRenderMode.OFF) {
				remember(s, "clouds", o.getCloudRenderMode().getValue().name());
				o.getCloudRenderMode().setValue(CloudRenderMode.OFF);
				changed = true;
			}
			if (shadows.isOn() && o.getEntityShadows().getValue()) {
				remember(s, "shadows", "true");
				o.getEntityShadows().setValue(false);
				changed = true;
			}
			if (particles.isOn() && o.getParticles().getValue() == ParticlesMode.ALL) {
				remember(s, "particles", ParticlesMode.ALL.name());
				o.getParticles().setValue(ParticlesMode.DECREASED);
				changed = true;
			}
			if (blend.isOn() && o.getBiomeBlendRadius().getValue() > 1) {
				remember(s, "blend", String.valueOf(o.getBiomeBlendRadius().getValue()));
				o.getBiomeBlendRadius().setValue(1);
				changed = true;
			}
			if (unlock.isOn() && (o.getEnableVsync().getValue() || o.getMaxFps().getValue() < 260)) {
				remember(s, "vsync", String.valueOf(o.getEnableVsync().getValue()));
				remember(s, "maxfps", String.valueOf(o.getMaxFps().getValue()));
				o.getEnableVsync().setValue(false);
				o.getMaxFps().setValue(260);
				changed = true;
			}
			if (vignette.isOn() && o.getVignette().getValue()) {
				remember(s, "vignette", "true");
				o.getVignette().setValue(false);
				changed = true;
			}
		} catch (Throwable t) {
			WhiteClient.LOGGER.debug("[WHITECLIENT] fps boost", t);
		}
		if (changed) {
			saved.set(s.toString());
			o.write();
		}
	}

	private static void remember(JsonObject s, String key, String value) {
		if (!s.has(key)) {
			s.addProperty(key, value);
		}
	}

	@Override
	protected void onDisable() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.options == null) {
			return;
		}
		GameOptions o = mc.options;
		JsonObject s = savedJson();
		try {
			if (s.has("clouds")) {
				o.getCloudRenderMode().setValue(CloudRenderMode.valueOf(s.get("clouds").getAsString()));
			}
			if (s.has("shadows")) {
				o.getEntityShadows().setValue(true);
			}
			if (s.has("particles")) {
				o.getParticles().setValue(ParticlesMode.valueOf(s.get("particles").getAsString()));
			}
			if (s.has("blend")) {
				o.getBiomeBlendRadius().setValue(Integer.parseInt(s.get("blend").getAsString()));
			}
			if (s.has("vsync")) {
				o.getEnableVsync().setValue(Boolean.parseBoolean(s.get("vsync").getAsString()));
			}
			if (s.has("maxfps")) {
				o.getMaxFps().setValue(Integer.parseInt(s.get("maxfps").getAsString()));
			}
			if (s.has("vignette")) {
				o.getVignette().setValue(true);
			}
			o.write();
		} catch (Throwable t) {
			WhiteClient.LOGGER.debug("[WHITECLIENT] fps boost restore", t);
		}
		saved.set("");
	}
}
