package com.whiteclient.test;

import com.whiteclient.WhiteClient;
import com.whiteclient.gui.ClickGuiScreen;
import com.whiteclient.gui.WardrobeScreen;
import com.whiteclient.gui.WhiteTitleScreen;
import com.whiteclient.module.Category;
import com.whiteclient.modules.Pets;
import com.whiteclient.modules.RainFX;
import com.whiteclient.modules.TargetESP;
import com.whiteclient.modules.Wardrobe;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/** Boots the game, makes a world and screenshots every WHITECLIENT feature. Only runs with -Dfabric.client.gametest. */
public class WhiteClientGameTest implements FabricClientGameTest {
	private static boolean fakeOtherClient;

	/** A white hoodie test skin so the custom-skin feature shows up in screenshots. */
	private static void writeTestSkin() {
		try {
			final int skin = 0xFFE8C4A8, hair = 0xFF1C1C22, white = 0xFFF4F4F7, black = 0xFF15151A, eye = 0xFF2A5BD7;
			BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int c = 0; // overlays stay transparent
					if (y < 16 && x < 32) {
						c = skin;
						boolean top = y < 8 && x >= 8 && x < 16;
						if (top || (y >= 8 && y < 10) || (y >= 8 && x >= 24)) {
							c = hair;
						}
						if (y == 12 && (x == 9 || x == 14)) {
							c = white;
						}
						if (y == 12 && (x == 10 || x == 13)) {
							c = eye;
						}
						if (y == 14 && (x == 11 || x == 12)) {
							c = 0xFFB0806A;
						}
					} else if (y >= 16 && y < 32 && x >= 16 && x < 40) {
						c = white;
						if (x >= 20 && x < 28 && y >= 20 && (x == 23 || x == 24)) {
							c = black; // zipper
						}
						if (y >= 30) {
							c = black;
						}
					} else if (y >= 16 && y < 32 && x >= 40 && x < 56) {
						c = y >= 29 ? skin : white;
					} else if ((y >= 16 && y < 32 && x < 16) || (y >= 48 && x >= 16 && x < 32)) {
						c = (y % 16) >= 14 ? white : black;
					} else if (y >= 48 && x >= 32 && x < 48) {
						c = y >= 61 ? skin : white;
					}
					img.setRGB(x, y, c);
				}
			}
			ImageIO.write(img, "png", Wardrobe.folder().resolve("whiteclient_test.png").toFile());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

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
		context.waitTicks(40);

		// WHITECLIENT main menu with the player walking in (the automatic swap is off during tests)
		context.runOnClient(client -> WhiteTitleScreen.debugWalkTime = 0.9f);
		context.setScreen(WhiteTitleScreen::new);
		context.waitTicks(40);
		context.takeScreenshot("main_menu_walk_in");
		context.runOnClient(client -> WhiteTitleScreen.debugWalkTime = 5f);
		context.waitTicks(10);
		context.takeScreenshot("main_menu_player");
		context.runOnClient(client -> WhiteTitleScreen.debugWalkTime = Float.NaN);
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);

		// new Right Shift menu (works without a world too)
		context.setScreen(() -> new ClickGuiScreen(null));
		context.waitTicks(25);
		context.takeScreenshot("menu_home");
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);

		context.runOnClient(client -> writeTestSkin());

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("tp @p ~ ~ ~ 30 12");
			sp.getServer().runCommand("time set 6000");
			context.waitTicks(5);
			for (int i = 0; i < 8; i++) {
				sp.getServer().runCommand("tp @p ~-0.9 ~ ~0.45");
				context.waitTicks(3);
			}

			// ---- pets + lyrics (third person)
			context.runOnClient(client -> {
				Lyrics lyrics = Lyrics.parseLrc("[00:00.00] WHITECLIENT lyrics test\n[00:02.50] words pop up in time\n[00:05.00] with the music playing\n[00:08.00] in your world");
				WhiteClient.get().music().setOverride(new NowPlaying("test", "Test Song", "WHITECLIENT", "Album", 180_000, 0,
						true, System.currentTimeMillis(), null, true, "test"), lyrics);
				Pets pets = WhiteClient.get().modules().get(Pets.class);
				pets.pet.set("Jellyfish");
				pets.name.set("Kwal");
				if (!pets.isEnabled()) {
					pets.toggle();
				}
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			});
			context.waitTicks(70);
			context.takeScreenshot("third_person_lyrics_jellyfish");
			context.runOnClient(client -> WhiteClient.get().music().setOverride(null, null));

			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("pet_jellyfish_front");
			for (String pet : new String[]{"Dog", "Cat", "Butterfly"}) {
				context.runOnClient(client -> {
					Pets pets = WhiteClient.get().modules().get(Pets.class);
					pets.pet.set(pet);
					pets.name.set(pet.equals("Dog") ? "Buddy" : "");
				});
				context.waitTicks(20);
				context.takeScreenshot("pet_" + pet.toLowerCase(Locale.ROOT));
			}

			// ---- custom skin from the Wardrobe
			context.runOnClient(client -> {
				Wardrobe.reload();
				Wardrobe w = WhiteClient.get().modules().get(Wardrobe.class);
				w.skin.set("whiteclient_test");
				WhiteClient.get().modules().get(Pets.class).pet.set("Wisp");
			});
			context.waitTicks(20);
			context.takeScreenshot("custom_skin_front_wisp");

			// ---- target ESP on a husk in front of the player
			context.runOnClient(client -> {
				client.options.setPerspective(Perspective.FIRST_PERSON);
				WhiteClient.get().modules().get(Pets.class).toggle();
			});
			sp.getServer().runCommand("execute as @p at @s run summon husk ^ ^ ^3.2 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[210f,0f]}");
			context.waitTicks(20);
			context.runOnClient(client -> {
				for (Entity e : client.world.getEntities()) {
					if (e instanceof HuskEntity) {
						TargetESP.forcedTarget = e;
					}
				}
			});
			for (String style : TargetESP.STYLES) {
				context.runOnClient(client -> WhiteClient.get().modules().get(TargetESP.class).style.set(style));
				context.waitTicks(12);
				context.takeScreenshot("esp_" + style.toLowerCase(Locale.ROOT));
			}

			// ---- menus over the world
			context.setScreen(() -> new ClickGuiScreen(null, Category.PVP, WhiteClient.get().modules().get(TargetESP.class)));
			context.waitTicks(20);
			context.takeScreenshot("menu_pvp_window");
			context.setScreen(() -> new ClickGuiScreen(null).openTheme(true));
			context.waitTicks(20);
			context.takeScreenshot("menu_theme_colors");
			context.setScreen(() -> new ClickGuiScreen(null).openSearch("pet"));
			context.waitTicks(15);
			context.takeScreenshot("menu_search");
			context.setScreen(() -> null);
			context.runOnClient(client -> TargetESP.forcedTarget = null);
			sp.getServer().runCommand("kill @e[type=husk]");

			// ---- wardrobe
			context.setScreen(() -> new WardrobeScreen(null));
			context.waitTicks(25);
			context.takeScreenshot("wardrobe_skins");
			context.runOnClient(client -> {
				if (client.currentScreen instanceof WardrobeScreen ws) {
					ws.showTab(2);
				}
			});
			context.waitTicks(10);
			context.takeScreenshot("wardrobe_pets");
			context.setScreen(() -> null);

			// ---- rain with ripples + splashes
			sp.getServer().runCommand("weather rain");
			sp.getServer().runCommand("tp @p ~ ~ ~ 30 40");
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(RainFX.class).always.set(true);
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
			});
			context.waitTicks(120);
			context.takeScreenshot("rain_fx");
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(RainFX.class).always.set(false);
				client.options.setPerspective(Perspective.FIRST_PERSON);
			});
			sp.getServer().runCommand("weather clear");

			fakeOtherClient = true;
			context.setScreen(() -> new GameMenuScreen(true));
			context.waitTicks(30);
			context.takeScreenshot("pause_menu_with_other_client");
			fakeOtherClient = false;
			context.setScreen(() -> null);
			context.waitTicks(5);

		}

		// main menu again: the custom skin walks in
		context.runOnClient(client -> WhiteTitleScreen.debugWalkTime = 1.5f);
		context.setScreen(WhiteTitleScreen::new);
		context.waitTicks(30);
		context.takeScreenshot("main_menu_custom_skin_walking");
		context.runOnClient(client -> {
			WhiteTitleScreen.debugWalkTime = Float.NaN;
			WhiteClient.get().modules().get(Wardrobe.class).skin.set("");
		});
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);
	}
}
