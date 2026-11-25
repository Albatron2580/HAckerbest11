package com.whiteclient.mixin;

import com.whiteclient.modules.Wardrobe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
	private void whiteclient$customSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && (Object) this == mc.player) {
			PlayerSkin original = cir.getReturnValue();
			PlayerSkin custom = Wardrobe.apply(original);
			if (custom != original) {
				cir.setReturnValue(custom);
			}
		}
	}
}
