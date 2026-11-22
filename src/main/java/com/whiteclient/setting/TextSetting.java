package com.whiteclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class TextSetting extends Setting<String> {
	private final int maxLength;
	private Runnable onChange = () -> { };

	public TextSetting(String name, String defaultValue, int maxLength) {
		super(name, defaultValue);
		this.maxLength = maxLength;
	}

	public int getMaxLength() {
		return maxLength;
	}

	public TextSetting onChange(Runnable r) {
		this.onChange = r;
		return this;
	}

	@Override
	public void set(String v) {
		if (v == null) {
			v = "";
		}
		if (v.length() > maxLength) {
			v = v.substring(0, maxLength);
		}
		boolean changed = !v.equals(this.value);
		this.value = v;
		if (changed) {
			onChange.run();
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
