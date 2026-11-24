package com.whiteclient.setting;

import com.google.gson.JsonElement;

import java.util.function.Supplier;

public class InfoSetting extends Setting<String> {
	private final Supplier<String> text;

	public InfoSetting(String name, Supplier<String> text) {
		super(name, "");
		this.text = text;
	}

	public String text() {
		try {
			String s = text.get();
			return s == null ? "" : s;
		} catch (Throwable t) {
			return "";
		}
	}

	@Override
	public JsonElement toJson() {
		return null;
	}

	@Override
	public void fromJson(JsonElement element) {
	}
}
