package com.whiteclient.mixin;

import com.whiteclient.WhiteClient;
import com.whiteclient.gui.WhiteTitleScreen;
import com.whiteclient.modules.MainMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	public ClientWorld world;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
	private Screen whiteclient$replaceTitle(Screen screen) {
		try {
			boolean wantsTitle = screen instanceof TitleScreen || (screen == null && this.world == null);
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
