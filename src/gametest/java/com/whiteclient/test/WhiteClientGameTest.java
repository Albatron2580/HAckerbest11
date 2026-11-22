package com.whiteclient.test;

import com.whiteclient.WhiteClient;
import com.whiteclient.gui.ClickGuiScreen;
import com.whiteclient.modules.Text3D;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.Perspective;

/** Boots the game, makes a world and screenshots every WHITECLIENT feature. Only runs with -Dfabric.client.gametest. */
public class WhiteClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60);
		context.takeScreenshot("title_energy_background");

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("tp @p ~ ~ ~ 30 12");
			context.waitTicks(5);
			for (int i = 0; i < 8; i++) {
				sp.getServer().runCommand("tp @p ~-0.9 ~ ~0.45");
				context.waitTicks(3);
			}
			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
			context.waitTicks(30);
			context.takeScreenshot("third_person_day");

			sp.getServer().runCommand("time set 18000");
			context.waitTicks(30);
			context.takeScreenshot("third_person_night");

			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("front_view_night");

			sp.getServer().runCommand("time set 6000");
			context.runOnClient(client -> {
				client.options.setPerspective(Perspective.FIRST_PERSON);
				WhiteClient.get().modules().get(Text3D.class).setMessage("WHITECLIENT ON TOP");
			});
			context.waitTicks(30);
			context.takeScreenshot("text_3d");

			context.setScreen(() -> new ClickGuiScreen(null));
			context.waitTicks(20);
			context.takeScreenshot("client_menu");

			context.setScreen(() -> new GameMenuScreen(true));
			context.waitTicks(60);
			context.takeScreenshot("pause_menu_mods_button");
			context.setScreen(() -> null);
			context.waitTicks(5);
		}
	}
}
