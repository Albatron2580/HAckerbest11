package com.whiteclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whiteclient.WhiteClient;
import com.whiteclient.module.Module;
import com.whiteclient.module.ModuleManager;
import com.whiteclient.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves module states and settings to config/whiteclient.json. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path file = FabricLoader.getInstance().getConfigDir().resolve("whiteclient.json");
	private final ModuleManager modules;
	private int loadedVersion = CURRENT_VERSION;
	public static final int CURRENT_VERSION = 2;

	/** Config version found on disk (CURRENT_VERSION when there was no file). */
	public int loadedVersion() {
		return loadedVersion;
	}

	public ConfigManager(ModuleManager modules) {
		this.modules = modules;
	}

	public void load() {
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				return;
			}
			JsonObject rootObj = root.getAsJsonObject();
			loadedVersion = rootObj.has("version") ? rootObj.get("version").getAsInt() : 1;
			JsonObject mods = rootObj.getAsJsonObject("modules");
			if (mods == null) {
				return;
			}
			for (Module module : modules.all()) {
				JsonObject obj = mods.getAsJsonObject(key(module.getName()));
				if (obj == null) {
					continue;
				}
				if (obj.has("enabled")) {
					module.setEnabled(obj.get("enabled").getAsBoolean());
				}
				JsonObject settings = obj.getAsJsonObject("settings");
				if (settings != null) {
					for (Setting<?> s : module.getSettings()) {
						if (settings.has(key(s.getName()))) {
							try {
								s.fromJson(settings.get(key(s.getName())));
							} catch (Exception e) {
								WhiteClient.LOGGER.warn("[WHITECLIENT] bad value for {}.{}", module.getName(), s.getName());
							}
						}
					}
				}
			}
		} catch (Exception e) {
			WhiteClient.LOGGER.error("[WHITECLIENT] failed to read config", e);
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", CURRENT_VERSION);
		JsonObject mods = new JsonObject();
		for (Module module : modules.all()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("enabled", module.isEnabled());
			JsonObject settings = new JsonObject();
			for (Setting<?> s : module.getSettings()) {
				JsonElement json = s.toJson();
				if (json != null) {
					settings.add(key(s.getName()), json);
				}
			}
			obj.add("settings", settings);
			mods.add(key(module.getName()), obj);
		}
		root.add("modules", mods);
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			WhiteClient.LOGGER.error("[WHITECLIENT] failed to save config", e);
		}
	}

	private static String key(String name) {
		return name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
	}
}
