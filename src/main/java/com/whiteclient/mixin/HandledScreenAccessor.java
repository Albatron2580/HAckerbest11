package com.whiteclient.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
	@Accessor("x")
	int whiteclient$x();

	@Accessor("y")
	int whiteclient$y();

	@Accessor("backgroundWidth")
	int whiteclient$backgroundWidth();

	@Accessor("backgroundHeight")
	int whiteclient$backgroundHeight();
}
