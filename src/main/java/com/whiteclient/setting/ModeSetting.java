package com.whiteclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String name, String defaultValue, String... modes) {
		super(name, defaultValue);
		this.modes = Arrays.asList(modes);
	}

	public List<String> getModes() {
		return modes;
	}

	public boolean is(String mode) {
		return value.equalsIgnoreCase(mode);
	}

	public void cycle(int direction) {
		int i = modes.indexOf(value);
		if (i < 0) {
			i = 0;
		}
		i = Math.floorMod(i + direction, modes.size());
		value = modes.get(i);
	}

	@Override
	public void set(String v) {
		for (String m : modes) {
			if (m.equalsIgnoreCase(v)) {
				value = m;
				return;
			}
		}
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			set(element.getAsString());
		}
	}
}
