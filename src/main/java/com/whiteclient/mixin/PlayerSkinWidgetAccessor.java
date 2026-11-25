package com.whiteclient.mixin;

import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {
	@Accessor("wideModel")
	PlayerModel whiteclient$wideModel();

	@Accessor("slimModel")
	PlayerModel whiteclient$slimModel();

	@Accessor("rotationX")
	float whiteclient$getXRotation();

	@Mutable
	@Accessor("rotationX")
	void whiteclient$setXRotation(float value);

	@Accessor("rotationY")
	float whiteclient$getYRotation();

	@Mutable
	@Accessor("rotationY")
	void whiteclient$setYRotation(float value);
}
