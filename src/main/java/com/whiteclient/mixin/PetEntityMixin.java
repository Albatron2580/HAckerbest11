package com.whiteclient.mixin;

import com.whiteclient.modules.Pets;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-side pets can't be hit, pushed or collided with. */
@Mixin(Entity.class)
public abstract class PetEntityMixin {
	@Inject(method = "canHit", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$petCanHit(CallbackInfoReturnable<Boolean> cir) {
		if (Pets.isPet((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true, require = 0)
	private void whiteclient$petPushable(CallbackInfoReturnable<Boolean> cir) {
		if (Pets.isPet((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
