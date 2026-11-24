package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;

public class MenuButtons extends Module {
	public final ModeSetting where = add(new ModeSetting("Style Buttons", "All Menus", "All Menus", "Pause & Title"));
	public final NumberSetting roundness = add(new NumberSetting("Roundness", 7, 0, 10, 0.5));
	public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.55, 0.1, 1.0, 0.01));
	public final BoolSetting border = add(new BoolSetting("Border", true));
	public final BoolSetting sheen = add(new BoolSetting("Top Sheen", true));

	public MenuButtons() {
		super("Rounded Buttons", "Rounded see-through menu buttons", Category.MENU, true);
	}
}
