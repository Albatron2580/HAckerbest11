package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ModeSetting;

/** Replaces the vanilla title screen with the WHITECLIENT main menu. */
public class MainMenu extends Module {
	public final ModeSetting layout = add(new ModeSetting("Layout", "Showcase", "Showcase", "Centered"));
	public final ModeSetting background = add(new ModeSetting("Background", "Silk", "Silk", "Energy", "Panorama"));
	public final BoolSetting planets = add(new BoolSetting("Floating Planets", true));
	public final BoolSetting account = add(new BoolSetting("Show Account", true));
	public final BoolSetting music = add(new BoolSetting("Show Now Playing", true));
	public final BoolSetting player = add(new BoolSetting("Walking Player", true));
	public final ModeSetting playerSide = add(new ModeSetting("Player Side", "Right", "Right", "Left"));

	public MainMenu() {
		super("Main Menu", "WHITECLIENT title screen", Category.MENU, true);
	}
}
