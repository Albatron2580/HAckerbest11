package com.whiteclient.test;

import com.whiteclient.WhiteClient;
import com.whiteclient.gui.ClickGuiScreen;
import com.whiteclient.gui.WhiteTitleScreen;
import com.whiteclient.module.Category;
import com.whiteclient.modules.Spotify;
import com.whiteclient.modules.Text3D;
import com.whiteclient.music.Lyrics;
import com.whiteclient.music.NowPlaying;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

import java.util.List;

/** Boots the game, makes a world and screenshots every WHITECLIENT feature. Only runs with -Dfabric.client.gametest. */
public class WhiteClientGameTest implements FabricClientGameTest {
	private static boolean fakeOtherClient;

	@Override
	public void runTest(ClientGameTestContext context) {
		// Simulates another client (like NoRisk) that replaced the feedback row with "NoRisk Client | Mods".
		Identifier early = Identifier.of("whiteclient-gametest", "early");
		ScreenEvents.AFTER_INIT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ScreenEvents.AFTER_INIT.register(early, (client, screen, w, h) -> {
			if (!fakeOtherClient || !(screen instanceof GameMenuScreen)) {
				return;
			}
			List<ClickableWidget> buttons = Screens.getButtons(screen);
			for (ClickableWidget b : List.copyOf(buttons)) {
				if (b.getMessage().getContent() instanceof TranslatableTextContent t) {
					if (t.getKey().equals("menu.sendFeedback")) {
						buttons.remove(b);
						buttons.add(ButtonWidget.builder(Text.literal("NoRisk Client"), x -> { }).dimensions(b.getX(), b.getY(), b.getWidth(), b.getHeight()).build());
					} else if (t.getKey().equals("menu.reportBugs")) {
						buttons.remove(b);
						buttons.add(ButtonWidget.builder(Text.literal("Mods"), x -> { }).dimensions(b.getX(), b.getY(), b.getWidth(), b.getHeight()).build());
					}
				}
			}
		});

		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60);
		context.takeScreenshot("vanilla_title_white_energy_rounded_buttons");

		// WHITECLIENT main menu (the automatic swap is off during tests, so open it by hand)
		context.setScreen(WhiteTitleScreen::new);
		context.waitTicks(60);
		context.takeScreenshot("main_menu");
		context.runOnClient(client -> WhiteClient.get().music().setOverride(new NowPlaying("t", "Midnight Drive", "WHITECLIENT Radio", "",
				200_000, 30_000, true, System.currentTimeMillis(), null, true, "test"), null));
		context.waitTicks(30);
		context.takeScreenshot("main_menu_now_playing");
		context.runOnClient(client -> WhiteClient.get().music().setOverride(null, null));
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("tp @p ~ ~ ~ 30 12");
			context.waitTicks(5);
			for (int i = 0; i < 8; i++) {
				sp.getServer().runCommand("tp @p ~-0.9 ~ ~0.45");
				context.waitTicks(3);
			}
			// a fake song with our own test lyrics so the card + lyric 3D text show up
			context.runOnClient(client -> {
				Lyrics lyrics = Lyrics.parseLrc("[00:00.00] WHITECLIENT lyrics test\n[00:02.50] words pop up in time\n[00:05.00] with the music playing\n[00:08.00] in your world");
				WhiteClient.get().music().setOverride(new NowPlaying("test", "Test Song", "WHITECLIENT", "Album", 180_000, 0,
						true, System.currentTimeMillis(), null, true, "test"), lyrics);
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			});
			context.waitTicks(70);
			context.takeScreenshot("third_person_day_lyrics_card");

			sp.getServer().runCommand("time set 18000");
			context.waitTicks(40);
			context.takeScreenshot("third_person_night");

			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("front_view_night");

			sp.getServer().runCommand("time set 6000");
			context.runOnClient(client -> {
				client.options.setPerspective(Perspective.FIRST_PERSON);
				WhiteClient.get().music().setOverride(null, null);
				WhiteClient.get().modules().get(Text3D.class).setMessage("WHITECLIENT ON TOP");
			});
			context.waitTicks(30);
			context.takeScreenshot("text_3d_custom");

			context.setScreen(() -> new ClickGuiScreen(null));
			context.waitTicks(20);
			context.takeScreenshot("client_menu");

			context.setScreen(() -> new ClickGuiScreen(null, Category.MUSIC, WhiteClient.get().modules().get(Spotify.class)));
			context.waitTicks(25);
			context.takeScreenshot("client_menu_spotify");

			context.setScreen(() -> new GameMenuScreen(true));
			context.waitTicks(50);
			context.takeScreenshot("pause_menu_vanilla_layout");

			fakeOtherClient = true;
			context.setScreen(() -> new GameMenuScreen(true));
			context.waitTicks(30);
			context.takeScreenshot("pause_menu_with_other_client");
			fakeOtherClient = false;
			context.setScreen(() -> null);
			context.waitTicks(5);
		}
	}
}
