package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;

/** Replaces the vanilla title screen with the WHITECLIENT main menu. */
public class MainMenu extends Module {
	public final ModeSetting background = add(new ModeSetting("Background", "Energy", "Energy", "Panorama"));
	public final BoolSetting account = add(new BoolSetting("Show Account", true));
	public final BoolSetting music = add(new BoolSetting("Show Now Playing", true));

	public MainMenu() {
		super("Main Menu", "WHITECLIENT title screen", Category.MENU, true);
	}
}
