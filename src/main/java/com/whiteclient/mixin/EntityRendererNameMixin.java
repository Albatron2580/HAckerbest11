package com.whiteclient.mixin;

import com.whiteclient.modules.NameLogo;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the WHITECLIENT logo in front of your own name tag. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNameMixin {
	@Inject(method = "getDisplayName(Lnet/minecraft/entity/Entity;)Lnet/minecraft/text/Text;", at = @At("RETURN"), cancellable = true, require = 0)
	private void whiteclient$logo(Entity entity, CallbackInfoReturnable<Text> cir) {
		try {
			if (NameLogo.active() && NameLogo.isSelf(entity)) {
				cir.setReturnValue(NameLogo.decorate(cir.getReturnValue()));
			}
		} catch (Throwable ignored) {
		}
	}
}
