package com.whiteclient.setting;

import com.google.gson.JsonElement;

import java.util.function.Supplier;

public class ActionSetting extends Setting<String> {
	private final Supplier<String> buttonText;
	private final Runnable action;

	public ActionSetting(String name, Supplier<String> buttonText, Runnable action) {
		super(name, "");
		this.buttonText = buttonText;
		this.action = action;
	}

	public String buttonText() {
		return buttonText.get();
	}

	public void run() {
		action.run();
	}

	@Override
	public JsonElement toJson() {
		return null;
	}

	@Override
	public void fromJson(JsonElement element) {
	}
}
