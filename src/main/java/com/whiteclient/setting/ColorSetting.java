package com.whiteclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An RGB colour (alpha is always forced to 255). */
public class ColorSetting extends Setting<Integer> {
	public static final int[] PRESETS = {
			0xFFFFFFFF, 0xFFB8B8C4, 0xFFFF4A4A, 0xFFFF8A1E, 0xFFFFD54A,
			0xFF7CFF5A, 0xFF33E6C8, 0xFF3D8BFF, 0xFF9B5CFF, 0xFFFF5CC8
	};

	public ColorSetting(String name, int rgb) {
		super(name, 0xFF000000 | rgb);
	}

	public int rgb() {
		return value & 0xFFFFFF;
	}

	public int argb() {
		return 0xFF000000 | value;
	}

	public float r() {
		return ((value >> 16) & 0xFF) / 255f;
	}

	public float g() {
		return ((value >> 8) & 0xFF) / 255f;
	}

	public float b() {
		return (value & 0xFF) / 255f;
	}

	@Override
	public void set(Integer v) {
		this.value = 0xFF000000 | v;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(String.format("#%06X", rgb()));
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return;
		}
		try {
			String s = element.getAsString().trim();
			if (s.startsWith("#")) {
				s = s.substring(1);
			}
			set((int) Long.parseLong(s, 16));
		} catch (NumberFormatException ignored) {
		}
	}
}
