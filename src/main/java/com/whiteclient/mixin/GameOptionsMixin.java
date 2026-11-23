package com.whiteclient.mixin;

import com.whiteclient.modules.NameLogo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds WHITECLIENT's flag (an unused skin-layer bit) to the settings sent to the server, so other WhiteClient users can see you use it too. */
@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
	@Inject(method = "getSyncedOptions", at = @At("RETURN"), cancellable = true, require = 0)
	private void whiteclient$flag(CallbackInfoReturnable<SyncedClientOptions> cir) {
		try {
			if (NameLogo.broadcasting()) {
				cir.setReturnValue(NameLogo.withFlag(cir.getReturnValue()));
			}
		} catch (Throwable ignored) {
		}
	}
}
