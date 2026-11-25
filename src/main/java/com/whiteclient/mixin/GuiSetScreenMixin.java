//#file MC >= 26.2
package com.whiteclient.mixin;

import com.whiteclient.WhiteClient;
import com.whiteclient.gui.WhiteTitleScreen;
import com.whiteclient.modules.MainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
	private Screen whiteclient$replaceTitle(Screen screen) {
		try {
			boolean wantsTitle = screen instanceof TitleScreen || (screen == null && Minecraft.getInstance().level == null);
			if (!wantsTitle || System.getProperty("fabric.client.gametest") != null) {
				return screen;
			}
			WhiteClient wc = WhiteClient.get();
			if (wc == null) {
				return screen;
			}
			MainMenu menu = wc.modules().get(MainMenu.class);
			if (menu != null && menu.isEnabled()) {
				return new WhiteTitleScreen();
			}
		} catch (Throwable ignored) {
		}
		return screen;
	}
}
