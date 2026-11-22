package com.whiteclient.mixin;

import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {
	@Accessor("wideModel")
	PlayerEntityModel whiteclient$wideModel();

	@Accessor("slimModel")
	PlayerEntityModel whiteclient$slimModel();

	@Accessor("xRotation")
	float whiteclient$getXRotation();

	@Mutable
	@Accessor("xRotation")
	void whiteclient$setXRotation(float value);

	@Accessor("yRotation")
	float whiteclient$getYRotation();

	@Mutable
	@Accessor("yRotation")
	void whiteclient$setYRotation(float value);
}
