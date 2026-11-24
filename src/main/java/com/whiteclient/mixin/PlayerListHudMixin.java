package com.whiteclient.mixin;

import com.whiteclient.modules.NameLogo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
	@Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
	private void whiteclient$logo(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
		try {
			NameLogo n = NameLogo.get();
			MinecraftClient mc = MinecraftClient.getInstance();
			if (n == null || !n.isEnabled() || !n.tab.isOn() || mc.player == null) {
				return;
			}
			Object profile = entry.getProfile();
			UUID id;
			try {
				id = (UUID) profile.getClass().getMethod("id").invoke(profile);
			} catch (NoSuchMethodException e) {
				id = (UUID) profile.getClass().getMethod("getId").invoke(profile);
			}
			if (mc.player.getUuid().equals(id) || (NameLogo.showOthers() && NameLogo.isKnownUser(id))) {
				cir.setReturnValue(NameLogo.decorate(cir.getReturnValue()));
			}
		} catch (Throwable ignored) {
		}
	}
}
