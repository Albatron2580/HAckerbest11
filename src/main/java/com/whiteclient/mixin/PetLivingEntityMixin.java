package com.whiteclient.mixin;

import com.whiteclient.modules.Pets;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Same as {@link PetEntityMixin} for the LivingEntity overrides. */
@Mixin(LivingEntity.class)
public abstract class PetLivingEntityMixin {
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
