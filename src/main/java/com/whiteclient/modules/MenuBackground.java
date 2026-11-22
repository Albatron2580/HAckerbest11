package com.whiteclient.modules;

import com.whiteclient.module.Category;
import com.whiteclient.module.Module;
import com.whiteclient.render.BackgroundShader;
import com.whiteclient.setting.BoolSetting;
import com.whiteclient.setting.ColorSetting;
import com.whiteclient.setting.ModeSetting;
import com.whiteclient.setting.NumberSetting;

/** Animated backgrounds for the title screen, pause menu and the WHITECLIENT menu. */
public class MenuBackground extends Module {
	public final ModeSetting style = add(new ModeSetting("Style", "Energy", "Energy", "Aurora", "Mono"));
	public final ColorSetting color = add(new ColorSetting("Energy Color", 0xEEF2FF));
	public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.1, 3.0, 0.1, "x"));
	public final NumberSetting intensity = add(new NumberSetting("Intensity", 1.0, 0.3, 2.0, 0.1, "x"));
	public final BoolSetting titleScreen = add(new BoolSetting("Title Screen", true));
	public final BoolSetting pauseMenu = add(new BoolSetting("Pause Menu", true));
	public final BoolSetting clickGui = add(new BoolSetting("Client Menu", false));
	public final NumberSetting opacity = add(new NumberSetting("Pause Opacity", 0.92, 0.2, 1.0, 0.01));

	public MenuBackground() {
		super("Menu Background", "Animated energy / aurora menu backgrounds", Category.MENU, true);
	}

	public int shaderMode() {
		if (style.is("Aurora")) {
			return BackgroundShader.AURORA;
		}
		if (style.is("Mono")) {
			return BackgroundShader.MONO;
		}
		return BackgroundShader.ENERGY;
	}
}
