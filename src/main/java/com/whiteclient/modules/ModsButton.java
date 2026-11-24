package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.TextSetting;

public class ModsButton extends Module {
	public final TextSetting label = add(new TextSetting("Button Text", "Mods", 24));

	public ModsButton() {
		super("Pause Mods Button", "Adds a Mods button to the pause menu", Category.MENU, true);
	}
}
