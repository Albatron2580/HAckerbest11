package com.whiteclient.mixin;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiplayerScreen.class)
public interface MultiplayerScreenAccessor {
	@Accessor("layout")
	ThreePartsLayoutWidget whiteclient$layout();

	@Accessor("serverListWidget")
	MultiplayerServerListWidget whiteclient$serverListWidget();

	@Invoker("connect")
	void whiteclient$connect(ServerInfo info);
}
