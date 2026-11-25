package com.whiteclient.mixin;

import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(JoinMultiplayerScreen.class)
public interface MultiplayerScreenAccessor {
	//#if MC >= 1.21.9
	@Accessor("layout")
	HeaderAndFooterLayout whiteclient$layout();
	//#endif

	@Accessor("serverSelectionList")
	ServerSelectionList whiteclient$serverListWidget();

	@Invoker("join")
	void whiteclient$connect(ServerData info);
}
