package com.whiteclient.test;

import com.whiteclient.WhiteClient;
import com.whiteclient.gui.ClickGuiScreen;
import com.whiteclient.gui.Render2D;
import com.whiteclient.render.Perf;
import com.whiteclient.gui.WardrobeScreen;
import com.whiteclient.gui.WhiteTitleScreen;
import com.whiteclient.module.Category;
import com.whiteclient.modules.*;
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
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Husk;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

public class WhiteClientGameTest implements FabricClientGameTest {
	private static boolean fakeOtherClient;

	private static void writeTestSkin() {
		try {
			final int skin = 0xFFE8C4A8, hair = 0xFF1C1C22, white = 0xFFF4F4F7, black = 0xFF15151A, eye = 0xFF2A5BD7;
			BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int c = 0;
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
							c = black;
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

	private static void perf(ClientGameTestContext context, String label) {
		context.runOnClient(client -> {
			WhiteClient.LOGGER.info("[WHITECLIENT] PERF {} | fps {} | {}", label, client.getFps(), Perf.report(14));
			Perf.reset();
		});
	}

	private static void clearClutter(net.minecraft.client.Minecraft client) {
		client.getToastManager().clear();
		client.gui.getChat().clearMessages(false);
	}

	private static void attackHusk(net.minecraft.client.Minecraft client) {
		for (Entity e : client.level.entitiesForRendering()) {
			if (e instanceof Husk h && h.isAlive()) {
				client.gameMode.attack(client.player, h);
				client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				return;
			}
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		Identifier early = Identifier.fromNamespaceAndPath("whiteclient-gametest", "early");
		ScreenEvents.AFTER_INIT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ScreenEvents.AFTER_INIT.register(early, (client, screen, w, h) -> {
			if (!fakeOtherClient || !(screen instanceof PauseScreen)) {
				return;
			}
			List<AbstractWidget> buttons = Screens.getButtons(screen);
			for (AbstractWidget b : List.copyOf(buttons)) {
				if (b.getMessage().getContents() instanceof TranslatableContents t) {
					if (t.getKey().equals("menu.sendFeedback")) {
						buttons.remove(b);
						buttons.add(Button.builder(Component.literal("NoRisk Client"), x -> { }).bounds(b.getX(), b.getY(), b.getWidth(), b.getHeight()).build());
					} else if (t.getKey().equals("menu.reportBugs")) {
						buttons.remove(b);
						buttons.add(Button.builder(Component.literal("Mods"), x -> { }).bounds(b.getX(), b.getY(), b.getWidth(), b.getHeight()).build());
					}
				}
			}
		});

		context.waitForScreen(TitleScreen.class);
		context.waitTicks(40);

		context.runOnClient(client -> WhiteTitleScreen.debugWalkTime = 0.9f);
		context.setScreen(WhiteTitleScreen::new);
		context.waitTicks(40);
		context.takeScreenshot("main_menu_walk_in");
		context.runOnClient(client -> WhiteTitleScreen.debugWalkTime = 5f);
		context.waitTicks(10);
		context.takeScreenshot("main_menu_player");
		for (boolean old : new boolean[]{true, false}) {
			context.runOnClient(client -> {
				Render2D.legacy = old;
				WhiteTitleScreen.renderMs = 0;
			});
			context.waitTicks(old ? 4 : 40);
			context.runOnClient(client -> WhiteClient.LOGGER.info("[WHITECLIENT] PERF main menu {}: {} ms per frame (CPU), {} fps",
					old ? "old" : "new", String.format(Locale.ROOT, "%.2f", WhiteTitleScreen.renderMs), client.getFps()));
			if (old) {
				context.takeScreenshot("main_menu_old_shapes");
			}
		}
		context.runOnClient(client -> Render2D.legacy = false);
		context.runOnClient(client -> WhiteTitleScreen.debugWalkTime = Float.NaN);
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);

		context.setScreen(() -> new ClickGuiScreen(null));
		context.waitTicks(25);
		context.takeScreenshot("menu_home");
		context.setScreen(() -> new ClickGuiScreen(null));
		context.waitTicks(3);
		context.takeScreenshot("menu_deal_early");
		context.waitTicks(5);
		context.takeScreenshot("menu_deal_late");
		context.waitTicks(5);
		context.takeScreenshot("menu_deal_buttons");
		context.setScreen(() -> new ClickGuiScreen(null, Category.HUD, null));
		context.waitTicks(4);
		context.takeScreenshot("menu_window_open");
		context.runOnClient(client -> client.screen.onClose());
		context.waitTicks(1);
		context.runOnClient(client -> WhiteClient.LOGGER.info("[WHITECLIENT] window closed right away: {}", client.screen));
		context.setScreen(() -> new ClickGuiScreen(null));
		context.waitTicks(40);
		context.runOnClient(client -> client.screen.onClose());
		context.waitTicks(2);
		context.takeScreenshot("menu_closing");
		context.waitTicks(20);
		context.runOnClient(client -> WhiteClient.LOGGER.info("[WHITECLIENT] menu after closing animation: {}", client.screen));
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);

		WhiteClient.LOGGER.info("[WHITECLIENT] update check: {}", com.whiteclient.update.AutoUpdater.checkNow(false));
		WhiteClient.LOGGER.info("[WHITECLIENT] update download test: {}", com.whiteclient.update.AutoUpdater.testDownload("1.12.0"));

		context.runOnClient(client -> {
			net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen mp =
					new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new TitleScreen());
			client.setScreen(mp);
			net.minecraft.client.multiplayer.ServerList list = mp.getServers();
			for (int i = 0; i < list.size(); i++) {
				net.minecraft.client.multiplayer.ServerData info = list.get(i);
				info.setState(net.minecraft.client.multiplayer.ServerData.State.valueOf("SUCCESSFUL"));
				info.motd = net.minecraft.network.chat.Component.literal("        §b§lVEX VANILLA §8> §b.gg/VexVanilla\n§3                          EU");
				info.status = net.minecraft.network.chat.Component.literal("89/1000");
				info.ping = 23;
			}
		});
		context.waitTicks(40);
		context.takeScreenshot("multiplayer_featured");
		context.runOnClient(client -> {
			if (client.screen instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen mp) {
				net.minecraft.client.multiplayer.ServerList list = mp.getServers();
				WhiteClient.LOGGER.info("[WHITECLIENT] server list: {} entries, first = {}", list.size(),
						list.size() > 0 ? list.get(0).ip : "none");
			}
		});
		//#if MC >= 26.1 && MC < 26.3
		//$ context.setScreen(() -> new net.minecraft.client.gui.screens.options.OptionsScreen(new TitleScreen(), net.minecraft.client.Minecraft.getInstance().options, false));
		//#else
		context.setScreen(() -> new net.minecraft.client.gui.screens.options.OptionsScreen(new TitleScreen(), net.minecraft.client.Minecraft.getInstance().options));
		//#endif
		context.waitTicks(20);
		context.takeScreenshot("options_planet");
		context.setScreen(() -> new com.whiteclient.gui.SupportScreen(new TitleScreen()));
		context.waitTicks(20);
		context.takeScreenshot("support_screen");
		context.runOnClient(client -> com.whiteclient.update.AutoUpdater.previewReady("9.9.9"));
		context.setScreen(WhiteTitleScreen::new);
		context.waitTicks(30);
		context.takeScreenshot("main_menu_update_ready");
		context.setScreen(() -> new com.whiteclient.gui.UpdateReadyScreen(new TitleScreen()));
		context.waitTicks(20);
		context.takeScreenshot("update_ready_screen");
		context.runOnClient(client -> com.whiteclient.update.AutoUpdater.previewReady(null));

		context.setScreen(() -> new net.minecraft.client.gui.screens.GenericMessageScreen(net.minecraft.network.chat.Component.literal("Encrypting...")));
		context.waitTicks(10);
		context.takeScreenshot("connecting_planet");
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);

		context.runOnClient(client -> writeTestSkin());
		context.runOnClient(client -> WhiteClient.get().modules().get(MenuBackground.class).loadingScreens.set(false));
		context.runOnClient(client -> {
			WhiteClient.get().modules().get(com.whiteclient.modules.FpsBoost.class).unlock.set(false);
			client.options.framerateLimit().set(10);
		});

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			context.runOnClient(client -> client.options.framerateLimit().set(30));
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("tp @p ~ ~ ~ 30 12");
			sp.getServer().runCommand("time set 6000");
			context.waitTicks(5);
			for (int i = 0; i < 8; i++) {
				sp.getServer().runCommand("tp @p ~-0.9 ~ ~0.45");
				context.waitTicks(3);
			}

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
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.waitTicks(70);
			context.takeScreenshot("third_person_lyrics_jellyfish");
			perf(context, "world default + jellyfish");
			context.runOnClient(client -> WhiteClient.get().music().setOverride(null, null));

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("pet_jellyfish_front");
			context.runOnClient(client -> {
				var mm = WhiteClient.get().modules();
				mm.get(Halo.class).setEnabled(false);
				mm.get(Aura.class).setEnabled(false);
				mm.get(Pets.class).setEnabled(false);
				client.getToastManager().clear();
				client.gui.getChat().clearMessages(false);
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.waitTicks(15);
			context.takeScreenshot("gallery_wings_back");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("gallery_wings_front");
			context.runOnClient(client -> {
				var mm = WhiteClient.get().modules();
				mm.get(Wings.class).setEnabled(false);
				Pets pets = mm.get(Pets.class);
				pets.name.set("");
				pets.overHead.set(false);
				pets.setEnabled(true);
			});
			for (String pet : Pets.TYPES) {
				context.runOnClient(client -> WhiteClient.get().modules().get(Pets.class).pet.set(pet));
				context.waitTicks(45);
				context.takeScreenshot("gallery_pet_" + pet.toLowerCase(Locale.ROOT).replace(' ', '_'));
			}
			context.runOnClient(client -> WhiteClient.get().modules().get(Pets.class).overHead.set(true));
			for (String pet : Pets.TYPES) {
				context.runOnClient(client -> WhiteClient.get().modules().get(Pets.class).pet.set(pet));
				context.waitTicks(90);
				context.takeScreenshot("pet_over_head_" + pet.toLowerCase(Locale.ROOT).replace(' ', '_'));
				perf(context, "pet over head");
			}
			for (String pet : new String[]{"Manta Ray", "Star Whale"}) {
				context.runOnClient(client -> WhiteClient.get().modules().get(Pets.class).pet.set(pet));
				context.waitTicks(10);
				for (int i = 0; i < 22; i++) {
					sp.getServer().runCommand("execute as @p at @s rotated ~ 0 run tp @s ^ ^ ^0.2");
					context.waitTicks(1);
				}
				context.takeScreenshot("gallery_walk_" + pet.toLowerCase(Locale.ROOT).replace(' ', '_'));
			}
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(Pets.class).setEnabled(false);
				WhiteClient.get().modules().get(HeadWear.class).setEnabled(true);
			});
			for (String style : HeadWear.STYLES) {
				context.runOnClient(client -> WhiteClient.get().modules().get(HeadWear.class).style.set(style));
				context.waitTicks(6);
				context.takeScreenshot("headwear_" + style.toLowerCase(Locale.ROOT).replace(' ', '_'));
			}
			context.runOnClient(client -> WhiteClient.get().modules().get(HeadWear.class).setEnabled(false));
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(TimeChanger.class).setEnabled(true);
				WhiteClient.get().modules().get(Cinematic.class).setEnabled(true);
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.waitTicks(30);
			context.takeScreenshot("cinematic_sunset");
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(TimeChanger.class).setEnabled(false);
				WhiteClient.get().modules().get(Cinematic.class).setEnabled(false);
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			sp.getServer().runCommand("time set 6000");
			context.waitTicks(5);
			context.runOnClient(client -> WhiteClient.get().modules().get(Pets.class).setEnabled(false));
			for (String car : CarEmote.CARS) {
				context.runOnClient(client -> {
					CarEmote ce = WhiteClient.get().modules().get(CarEmote.class);
					ce.mode.set("Show Off");
					ce.car.set(car);
					ce.start();
				});
				context.waitTicks(16);
				context.takeScreenshot("car_" + car.toLowerCase(Locale.ROOT) + "_showoff");
			}
			context.runOnClient(client -> {
				CarEmote ce = WhiteClient.get().modules().get(CarEmote.class);
				ce.car.set("Sports");
				ce.start();
			});
			context.waitTicks(16);
			sp.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ ~-55 0");
			context.waitTicks(25);
			context.takeScreenshot("car_sports_side");
			sp.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ ~-70 0");
			context.waitTicks(25);
			context.takeScreenshot("car_sports_rear");
			sp.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ ~125 0");
			context.runOnClient(client -> {
				CarEmote ce = WhiteClient.get().modules().get(CarEmote.class);
				ce.car.set("Pickup");
				ce.start();
			});
			context.waitTicks(16);
			sp.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ ~-55 0");
			context.waitTicks(25);
			context.takeScreenshot("car_pickup_side");
			sp.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ ~55 0");
			sp.getServer().runCommand("time set 15000");
			context.runOnClient(client -> {
				CarEmote ce = WhiteClient.get().modules().get(CarEmote.class);
				ce.car.set("Sports");
				ce.start();
			});
			context.waitTicks(20);
			context.takeScreenshot("car_sports_night");
			sp.getServer().runCommand("time set 6000");
			context.runOnClient(client -> {
				CarEmote ce = WhiteClient.get().modules().get(CarEmote.class);
				ce.stop();
			});
			context.waitTicks(12);
			context.runOnClient(client -> {
				CarEmote ce = WhiteClient.get().modules().get(CarEmote.class);
				ce.mode.set("Drive");
				ce.car.set("Sports");
				ce.start();
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.waitTicks(4);
			for (int i = 0; i < 12; i++) {
				sp.getServer().runCommand("execute as @p at @s rotated ~ 0 run tp @s ^ ^ ^0.3");
				context.waitTicks(1);
			}
			context.waitTicks(4);
			context.takeScreenshot("car_sports_drive");
			perf(context, "car driving");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(4);
			context.takeScreenshot("car_sports_drive_front");
			context.runOnClient(client -> {
				CarEmote ce = WhiteClient.get().modules().get(CarEmote.class);
				ce.car.set("Pickup");
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.waitTicks(6);
			context.takeScreenshot("car_pickup_drive");
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(CarEmote.class).car.set("Sports");
				client.options.setCameraType(CameraType.FIRST_PERSON);
			});
			context.waitTicks(5);
			context.takeScreenshot("car_sports_first_person");
			context.runOnClient(client -> {
				CarEmote ce = WhiteClient.get().modules().get(CarEmote.class);
				ce.stop();
				ce.mode.set("Show Off");
			});
			context.waitTicks(15);

			context.runOnClient(client -> {
				var mm = WhiteClient.get().modules();
				mm.get(Halo.class).setEnabled(true);
				mm.get(Aura.class).setEnabled(true);
				mm.get(Wings.class).setEnabled(true);
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});

			context.runOnClient(client -> {
				Wardrobe.reload();
				Wardrobe w = WhiteClient.get().modules().get(Wardrobe.class);
				w.skin.set("whiteclient_test");
				WhiteClient.get().modules().get(Pets.class).pet.set("Manta Ray");
			});
			context.waitTicks(20);
			context.takeScreenshot("custom_skin_front_wisp");

			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				WhiteClient.get().modules().get(Pets.class).toggle();
			});
			sp.getServer().runCommand("execute as @p at @s run summon husk ^ ^ ^3.2 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[210f,0f]}");
			context.waitTicks(20);
			context.runOnClient(client -> {
				for (Entity e : client.level.entitiesForRendering()) {
					if (e instanceof Husk) {
						TargetESP.forcedTarget = e;
					}
				}
			});
			for (String style : TargetESP.STYLES) {
				context.runOnClient(client -> WhiteClient.get().modules().get(TargetESP.class).style.set(style));
				context.waitTicks(12);
				context.takeScreenshot("esp_" + style.toLowerCase(Locale.ROOT));
			}

			context.runOnClient(client -> {
				TargetESP.forcedTarget = null;
				var mm = WhiteClient.get().modules();
				for (Class<? extends com.whiteclient.module.Module> c : List.of(ComboCounter.class, ReachDisplay.class, InfoHUD.class,
						Keystrokes.class, PotionHUD.class, ArmorHUD.class, CustomCrosshair.class)) {
					mm.get(c).setEnabled(true);
				}
			});
			sp.getServer().runCommand("give @p diamond_sword");
			sp.getServer().runCommand("item replace entity @p armor.head with diamond_helmet");
			sp.getServer().runCommand("item replace entity @p armor.chest with diamond_chestplate");
			sp.getServer().runCommand("item replace entity @p armor.legs with iron_leggings");
			sp.getServer().runCommand("item replace entity @p armor.feet with diamond_boots");
			sp.getServer().runCommand("effect give @p speed 120 1");
			sp.getServer().runCommand("effect give @p strength 120 0");
			sp.getServer().runCommand("effect give @p regeneration 9 0");
			sp.getServer().runCommand("effect give @e[type=husk] resistance 999 3");
			for (int i = 0; i < 3; i++) {
				context.runOnClient(WhiteClientGameTest::attackHusk);
				context.waitTicks(13);
			}
			context.runOnClient(client -> {
				Keystrokes.debugHeld = 0b10001;
				LowHealth.debugHealth = 5;
				WhiteClient.get().modules().get(Trail.class).toggle();
				clearClutter(client);
				attackHusk(client);
			});
			context.waitTicks(2);
			context.takeScreenshot("combat_hits_hud");
			perf(context, "combat hud");
			context.runOnClient(client -> {
				Keystrokes.debugHeld = 0;
				LowHealth.debugHealth = -1;
			});

			for (String kill : new String[]{"Lightning", "Shockwave"}) {
				sp.getServer().runCommand("kill @e[type=husk]");
				sp.getServer().runCommand("execute as @p at @s run summon husk ^ ^ ^2.6 {NoAI:1b,Silent:1b,Health:1f,Rotation:[210f,0f]}");
				context.waitTicks(15);
				context.runOnClient(client -> {
					WhiteClient.get().modules().get(KillEffects.class).style.set(kill);
					clearClutter(client);
					attackHusk(client);
				});
				context.waitTicks(kill.equals("Lightning") ? 6 : 12);
				context.takeScreenshot("kill_" + kill.toLowerCase(Locale.ROOT));
			}
			sp.getServer().runCommand("kill @e[type=husk]");

			context.runOnClient(client -> {
				var mm = WhiteClient.get().modules();
				mm.get(Halo.class).setEnabled(false);
				mm.get(Wings.class).setEnabled(false);
				mm.get(Aura.class).setEnabled(false);
				clearClutter(client);
				mm.get(ChinaHat.class).setEnabled(true);
				mm.get(JumpCircles.class).setEnabled(true);
				mm.get(Capes.class).setEnabled(true);
				mm.get(Capes.class).design.set("Galaxy");
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			for (int i = 0; i < 8; i++) {
				sp.getServer().runCommand("tp @p ~0.4 ~ ~0.25");
				context.waitTicks(2);
			}
			context.runOnClient(client -> WhiteClient.get().modules().get(JumpCircles.class)
					.spawn(client.player.getX(), client.player.getY() + 0.02, client.player.getZ(), WhiteClient.get().time() - 0.25f, 1.2f));
			context.waitTicks(3);
			context.takeScreenshot("cosmetics_back_cape_hat_trail");
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(Capes.class).design.set("White");
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			context.waitTicks(10);
			context.takeScreenshot("cosmetics_front_hat");

			sp.getServer().runCommand("tp @p ~ ~ ~ 30 50");
			context.runOnClient(client -> {
				var mm = WhiteClient.get().modules();
				mm.get(ChinaHat.class).setEnabled(false);
				mm.get(Trail.class).setEnabled(false);
				mm.get(JumpCircles.class).setEnabled(false);
				mm.get(Halo.class).setEnabled(true);
				mm.get(Wings.class).setEnabled(true);
				mm.get(Aura.class).setEnabled(true);
				mm.get(BlockOutline.class).setEnabled(true);
				HandView hv = mm.get(HandView.class);
				hv.setEnabled(true);
				hv.x.set(0.08);
				hv.y.set(-0.04);
				hv.scale.set(0.85);
				hv.rotY.set(12.0);
				client.options.setCameraType(CameraType.FIRST_PERSON);
			});
			context.waitTicks(10);
			context.takeScreenshot("block_outline_hand_view");
			sp.getServer().runCommand("tp @p ~ ~ ~ 30 5");
			context.runOnClient(client -> Zoom.debugHeld = true);
			context.waitTicks(15);
			context.takeScreenshot("zoom");
			context.runOnClient(client -> Zoom.debugHeld = false);
			sp.getServer().runCommand("time set 18000");
			context.runOnClient(client -> WhiteClient.get().modules().get(Ambience.class).setEnabled(true));
			context.waitTicks(40);
			context.takeScreenshot("ambience_fireflies");
			sp.getServer().runCommand("time set 6000");
			context.runOnClient(client -> WhiteClient.get().modules().get(Ambience.class).mode.set("Petals"));
			context.waitTicks(40);
			context.takeScreenshot("ambience_petals");
			perf(context, "ambience");
			context.runOnClient(client -> {
				var mm = WhiteClient.get().modules();
				for (Class<? extends com.whiteclient.module.Module> c : List.of(Ambience.class, BlockOutline.class, HandView.class, InfoHUD.class,
						Keystrokes.class, PotionHUD.class, ArmorHUD.class, CustomCrosshair.class, Capes.class, JumpCircles.class)) {
					mm.get(c).setEnabled(false);
				}
			});
			sp.getServer().runCommand("execute as @p at @s run summon husk ^ ^ ^3.2 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[210f,0f]}");
			context.waitTicks(10);

			context.setScreen(() -> new ClickGuiScreen(null, Category.PVP, WhiteClient.get().modules().get(TargetESP.class)));
			context.waitTicks(20);
			context.takeScreenshot("menu_pvp_window");
			context.setScreen(() -> new ClickGuiScreen(null).openTheme(true));
			context.waitTicks(20);
			context.takeScreenshot("menu_theme_colors");
			context.setScreen(() -> new ClickGuiScreen(null).openSearch("pet"));
			context.waitTicks(15);
			context.takeScreenshot("menu_search");
			//#if MC >= 1.21.9
			context.setScreen(() -> new com.whiteclient.gui.BrowserScreen(null, WhiteClient.get().modules().get(com.whiteclient.modules.Browser.class)));
			context.waitTicks(10);
			context.takeScreenshot("browser_window");
			if (context.computeOnClient(client -> com.whiteclient.browser.WebView.installed())) {
				for (int i = 0; i < 120 && !context.computeOnClient(client -> com.whiteclient.browser.WebView.ready()); i++) {
					context.waitTicks(20);
				}
				WhiteClient.LOGGER.info("[WHITECLIENT] browser engine ready: {} ({})", context.computeOnClient(client -> com.whiteclient.browser.WebView.ready()),
						context.computeOnClient(client -> com.whiteclient.browser.WebView.progress()));
				context.setScreen(() -> null);
				context.setScreen(() -> new com.whiteclient.gui.BrowserScreen(null, WhiteClient.get().modules().get(com.whiteclient.modules.Browser.class)));
				context.waitTicks(160);
				context.takeScreenshot("browser_page");
				context.setScreen(() -> null);
				context.waitTicks(20);
				context.takeScreenshot("browser_pinned");
				context.runOnClient(client -> WhiteClient.get().modules().get(com.whiteclient.modules.Browser.class).closePage());
			}
			//#endif
			context.setScreen(() -> null);
			context.runOnClient(client -> TargetESP.forcedTarget = null);
			sp.getServer().runCommand("kill @e[type=husk]");

			context.setScreen(() -> new WardrobeScreen(null));
			context.waitTicks(25);
			context.takeScreenshot("wardrobe_skins");
			context.runOnClient(client -> {
				if (client.screen instanceof WardrobeScreen ws) {
					ws.showTab(2);
				}
			});
			context.waitTicks(10);
			context.takeScreenshot("wardrobe_pets");
			context.setScreen(() -> null);

			sp.getServer().runCommand("weather rain");
			sp.getServer().runCommand("tp @p ~ ~ ~ 30 40");
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(RainFX.class).always.set(true);
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.waitTicks(120);
			context.takeScreenshot("rain_fx");
			perf(context, "rain");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			sp.getServer().runCommand("tp @p ~ ~ ~ 30 14");
			context.waitTicks(12);
			context.takeScreenshot("rain_wet_ground");
			context.runOnClient(client -> {
				RainFX r = WhiteClient.get().modules().get(RainFX.class);
				WhiteClient.LOGGER.info("[WHITECLIENT] wet ground: {} blocks, {} puddle cells", r.debugWetQuads, r.debugPuddleCells);
			});
			sp.getServer().runCommand("time set 15500");
			context.waitTicks(8);
			context.takeScreenshot("rain_wet_ground_dusk");
			sp.getServer().runCommand("time set 6000");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			sp.getServer().runCommand("tp @p ~ ~ ~ 0 10");
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(BlockOutline.class).setEnabled(true);
				CarEmote ce = WhiteClient.get().modules().get(CarEmote.class);
				ce.mode.set("Show Off");
				ce.car.set("Pickup");
				ce.start();
			});
			context.waitTicks(25);
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("car_pickup_rain");
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(CarEmote.class).stop();
				WhiteClient.get().modules().get(BlockOutline.class).setEnabled(false);
			});
			context.waitTicks(15);
			context.runOnClient(client -> {
				WhiteClient.get().modules().get(RainFX.class).always.set(false);
				client.options.setCameraType(CameraType.FIRST_PERSON);
			});
			sp.getServer().runCommand("weather clear");

			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(30);
			context.takeScreenshot("pause_menu");
			context.setScreen(() -> null);
			context.waitTicks(3);

			context.runOnClient(client -> {
				com.whiteclient.update.AutoUpdater.previewReady("9.9.9");
				com.whiteclient.update.AutoUpdater.chatNotice(client);
			});
			context.waitTicks(5);
			context.takeScreenshot("update_chat_notice");
			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(30);
			context.takeScreenshot("pause_update_badge");
			context.setScreen(() -> null);
			context.runOnClient(client -> {
				com.whiteclient.update.AutoUpdater.previewReady(null);
				client.gui.getChat().clearMessages(false);
			});
			context.waitTicks(3);

			context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
			context.waitTicks(15);
			context.takeScreenshot("inventory_theme");
			context.setScreen(() -> null);
			context.waitTicks(3);


			fakeOtherClient = true;
			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(30);
			context.takeScreenshot("pause_menu_with_other_client");
			fakeOtherClient = false;
			context.setScreen(() -> null);
			context.waitTicks(5);
		}

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
