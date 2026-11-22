package com.whiteclient.mixin;

import com.whiteclient.modules.Wardrobe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shows your Wardrobe skin on yourself (client side only). */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
	private void whiteclient$customSkin(CallbackInfoReturnable<SkinTextures> cir) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && (Object) this == mc.player) {
			SkinTextures original = cir.getReturnValue();
			SkinTextures custom = Wardrobe.apply(original);
			if (custom != original) {
				cir.setReturnValue(custom);
			}
		}
	}
}
