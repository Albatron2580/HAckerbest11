package com.whiteclient.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whiteclient.WhiteClient;
import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.TextSetting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

public class FpsBoost extends Module {
	public final BoolSetting clouds = add(new BoolSetting("No Clouds", true));
	public final BoolSetting shadows = add(new BoolSetting("No Entity Shadows", true));
	public final BoolSetting particles = add(new BoolSetting("Fewer Particles", true));
	public final BoolSetting blend = add(new BoolSetting("Fast Biome Blend", true));
	public final BoolSetting unlock = add(new BoolSetting("Unlock FPS (VSync Off)", true));
	public final BoolSetting vignette = add(new BoolSetting("No Vignette", true));
	public final BoolSetting effects = add(new BoolSetting("Lighter Client Effects", true));
	private final TextSetting saved = add(new TextSetting("Saved Settings", "", 4000).visibleWhen(() -> false));

	private int timer;

	public FpsBoost() {
		super("FPS Boost", "Faster video settings in one click (turn off to get yours back)", Category.WORLD, true);
	}

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
	public void onTick(Minecraft mc) {
		if (timer-- > 0) {
			return;
		}
		timer = 40;
		apply(mc.options);
	}

	private void apply(Options o) {
		JsonObject s = savedJson();
		boolean changed = false;
		try {
			if (clouds.isOn() && o.cloudStatus().get() != CloudStatus.OFF) {
				remember(s, "clouds", o.cloudStatus().get().name());
				o.cloudStatus().set(CloudStatus.OFF);
				changed = true;
			}
			if (shadows.isOn() && o.entityShadows().get()) {
				remember(s, "shadows", "true");
				o.entityShadows().set(false);
				changed = true;
			}
			if (particles.isOn() && o.particles().get() == ParticleStatus.ALL) {
				remember(s, "particles", ParticleStatus.ALL.name());
				o.particles().set(ParticleStatus.DECREASED);
				changed = true;
			}
			if (blend.isOn() && o.biomeBlendRadius().get() > 1) {
				remember(s, "blend", String.valueOf(o.biomeBlendRadius().get()));
				o.biomeBlendRadius().set(1);
				changed = true;
			}
			if (unlock.isOn() && (o.enableVsync().get() || o.framerateLimit().get() < 260)) {
				remember(s, "vsync", String.valueOf(o.enableVsync().get()));
				remember(s, "maxfps", String.valueOf(o.framerateLimit().get()));
				o.enableVsync().set(false);
				o.framerateLimit().set(260);
				changed = true;
			}
			//#if MC >= 1.21.11
			if (vignette.isOn() && o.vignette().get()) {
				remember(s, "vignette", "true");
				o.vignette().set(false);
				changed = true;
			}
			//#endif
		} catch (Throwable t) {
			WhiteClient.LOGGER.debug("[WHITECLIENT] fps boost", t);
		}
		if (changed) {
			saved.set(s.toString());
			o.save();
		}
	}

	private static void remember(JsonObject s, String key, String value) {
		if (!s.has(key)) {
			s.addProperty(key, value);
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) {
			return;
		}
		Options o = mc.options;
		JsonObject s = savedJson();
		try {
			if (s.has("clouds")) {
				o.cloudStatus().set(CloudStatus.valueOf(s.get("clouds").getAsString()));
			}
			if (s.has("shadows")) {
				o.entityShadows().set(true);
			}
			if (s.has("particles")) {
				o.particles().set(ParticleStatus.valueOf(s.get("particles").getAsString()));
			}
			if (s.has("blend")) {
				o.biomeBlendRadius().set(Integer.parseInt(s.get("blend").getAsString()));
			}
			if (s.has("vsync")) {
				o.enableVsync().set(Boolean.parseBoolean(s.get("vsync").getAsString()));
			}
			if (s.has("maxfps")) {
				o.framerateLimit().set(Integer.parseInt(s.get("maxfps").getAsString()));
			}
			//#if MC >= 1.21.11
			if (s.has("vignette")) {
				o.vignette().set(true);
			}
			//#endif
			o.save();
		} catch (Throwable t) {
			WhiteClient.LOGGER.debug("[WHITECLIENT] fps boost restore", t);
		}
		saved.set("");
	}
}
