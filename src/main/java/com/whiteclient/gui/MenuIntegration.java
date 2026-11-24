package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.modules.ModsButton;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;

import java.util.List;

/** Pause-menu "Mods" button and animated menu backgrounds. */
public final class MenuIntegration {
	private MenuIntegration() {
	}

	public static void register() {
		ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> {
			if (screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>) {
				com.whiteclient.modules.InventoryTheme.ensure();
			}
		});
		ScreenEvents.AFTER_INIT.register(MenuIntegration::afterInit);
		// lay the pause menu out last, after every other mod had the chance to add its buttons
		Identifier late = Identifier.of(WhiteClient.MOD_ID, "pause_layout");
		ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, late);
		ScreenEvents.AFTER_INIT.register(late, (client, screen, w, h) -> {
			if (screen instanceof MultiplayerScreen mp && FeaturedServer.enabled()) {
				try {
					FeaturedServer.setup(client, mp);
				} catch (Throwable t) {
					WhiteClient.LOGGER.error("[WHITECLIENT] could not set up the featured server", t);
				}
			}
			if (screen instanceof GameMenuScreen && PauseStyle.enabled()) {
				try {
					PauseStyle.layout(client, screen);
				} catch (Throwable t) {
					WhiteClient.LOGGER.error("[WHITECLIENT] could not restyle the pause menu", t);
				}
			}
		});
	}

	private static void afterInit(MinecraftClient client, Screen screen, int width, int height) {
		WhiteClient wc = WhiteClient.get();
		if (wc == null) {
			return;
		}
		if (screen instanceof GameMenuScreen) {
			ModsButton mods = wc.modules().get(ModsButton.class);
			if (mods != null && mods.isEnabled()) {
				try {
					addModsButton(screen);
				} catch (Throwable t) {
					WhiteClient.LOGGER.error("[WHITECLIENT] could not add the Mods button", t);
				}
			}
		}
		if (screen instanceof GameMenuScreen || screen instanceof TitleScreen) {
			ScreenEvents.afterBackground(screen).register((s, context, mouseX, mouseY, delta) -> {
				MenuBackground bg = wc.modules().get(MenuBackground.class);
				if (bg == null || !bg.isEnabled()) {
					return;
				}
				if (s instanceof GameMenuScreen && PauseStyle.isCurrent(s) && PauseStyle.enabled()) {
					if (bg.pauseMenu.isOn()) {
						MenuBackgroundRenderer.draw(context, s.width, s.height, bg.opacity.getFloat());
					}
					PauseStyle.drawScene(context, s, mouseX, mouseY, delta);
					return;
				}
				if (s instanceof TitleScreen && bg.titleScreen.isOn()) {
					MenuBackgroundRenderer.draw(context, s.width, s.height, 1f);
				} else if (s instanceof GameMenuScreen && bg.pauseMenu.isOn() && hasButtons(s)) {
					MenuBackgroundRenderer.draw(context, s.width, s.height, bg.opacity.getFloat());
				}
				if (s instanceof GameMenuScreen && bg.pauseCard.isOn() && hasButtons(s)) {
					drawButtonCard(context, s);
				}
			});
		} else if (isLoading(screen)) {
			ScreenEvents.afterBackground(screen).register((s, context, mouseX, mouseY, delta) -> {
				MenuBackground bg = wc.modules().get(MenuBackground.class);
				if (bg == null || !bg.isEnabled() || !bg.loadingScreens.isOn()) {
					return;
				}
				if (bg.otherMenusLook.is("Planet")) {
					MenuScene.draw(context, s, 120, false); // same planet look as the other menus (no fade: loading screens follow each other quickly)
				} else {
					MenuBackgroundRenderer.draw(context, s.width, s.height, 1f);
					context.fill(0, 0, s.width, s.height, 0x40000000);
				}
				float time = WhiteClient.get().time();
				float r = Math.max(9, Math.min(18, s.height * 0.045f));
				float ly = Math.max(r * 2.2f, s.height * 0.16f);
				Render2D.planetLogo(context, s.width / 2f, ly, r, time, 255);
				String word = "WHITECLIENT";
				int tw = client.textRenderer.getWidth(UiFont.title(word));
				context.drawText(client.textRenderer, UiFont.title(word), Math.round(s.width / 2f - tw / 2f), Math.round(ly + r * 1.6f + 4), 0xFFFFFFFF, false);
			});
		} else if (client.world == null && isPlainMenu(screen)) {
			ScreenEvents.afterBackground(screen).register((s, context, mouseX, mouseY, delta) -> {
				MenuBackground bg = wc.modules().get(MenuBackground.class);
				if (bg == null || !bg.isEnabled() || !bg.otherMenus.isOn() || MinecraftClient.getInstance().world != null) {
					return;
				}
				if (bg.otherMenusLook.is("Planet")) {
					MenuScene.draw(context, s, 168);
				} else {
					MenuBackgroundRenderer.draw(context, s.width, s.height, 1f);
					context.fill(0, 0, s.width, s.height, 0x66000000);
				}
				if (s instanceof MultiplayerScreen mp && FeaturedServer.isCurrent(mp)) {
					FeaturedServer.draw(context, mp, mouseX, mouseY);
				}
			});
		}
	}

	private static boolean isLoading(Screen s) {
		return s instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen
				|| s instanceof net.minecraft.client.gui.screen.multiplayer.ConnectScreen
				|| s instanceof net.minecraft.client.gui.screen.MessageScreen
				|| s instanceof net.minecraft.client.gui.screen.ProgressScreen
				|| s instanceof net.minecraft.client.gui.screen.DisconnectedScreen;
	}

	/** Option / world / server list menus outside a world (not our own screens, they have their own looks). */
	private static boolean isPlainMenu(Screen s) {
		String pkg = s.getClass().getName();
		return !(s instanceof TitleScreen) && !pkg.startsWith("com.whiteclient") && pkg.startsWith("net.minecraft");
	}

	/** A dark rounded card behind the pause menu buttons. */
	private static void drawButtonCard(net.minecraft.client.gui.DrawContext ctx, Screen s) {
		List<ClickableWidget> buttons = Screens.getButtons(s);
		float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
		for (ClickableWidget b : buttons) {
			if (!b.visible) {
				continue;
			}
			x0 = Math.min(x0, b.getX());
			y0 = Math.min(y0, b.getY());
			x1 = Math.max(x1, b.getX() + b.getWidth());
			y1 = Math.max(y1, b.getY() + b.getHeight());
		}
		if (x1 <= x0) {
			return;
		}
		float pad = 10;
		Render2D.shadow(ctx, x0 - pad, y0 - pad, x1 - x0 + pad * 2, y1 - y0 + pad * 2, 14, 8, 70);
		Render2D.roundRect(ctx, x0 - pad, y0 - pad, x1 - x0 + pad * 2, y1 - y0 + pad * 2, 14, 0xB00B0B10);
		Render2D.roundRectOutline(ctx, x0 - pad, y0 - pad, x1 - x0 + pad * 2, y1 - y0 + pad * 2, 14, 1, 0x1FFFFFFF);
	}

	private static boolean hasButtons(Screen screen) {
		for (ClickableWidget w : Screens.getButtons(screen)) {
			if (w instanceof net.minecraft.client.gui.widget.PressableWidget) {
				return true;
			}
		}
		return false;
	}

	private static String keyOf(ClickableWidget widget) {
		Text message = widget.getMessage();
		if (message == null) {
			return "";
		}
		TextContent content = message.getContent();
		if (content instanceof TranslatableTextContent translatable) {
			return translatable.getKey();
		}
		return message.getString();
	}

	private static void addModsButton(Screen screen) {
		List<ClickableWidget> buttons = Screens.getButtons(screen);
		if (buttons.isEmpty()) {
			return; // F3+Esc pause without menu
		}
		ClickableWidget feedback = null;
		ClickableWidget bugs = null;
		ClickableWidget options = null;
		ClickableWidget back = null;
		boolean otherModsButton = false;
		for (ClickableWidget w : buttons) {
			String key = keyOf(w);
			String text = w.getMessage() == null ? "" : w.getMessage().getString();
			if (key.equals("menu.sendFeedback") || key.equals("menu.feedback")) {
				feedback = w;
			} else if (key.equals("menu.reportBugs") || key.equals("menu.server_links") || key.equals("menu.custom_options")) {
				bugs = w;
			} else if (key.equals("menu.options")) {
				options = w;
			} else if (key.equals("menu.returnToGame")) {
				back = w;
			} else if (key.equals("modmenu.title") || text.equalsIgnoreCase("Mods")) {
				otherModsButton = true;
			}
		}
		ModsButton settings = WhiteClient.get().modules().get(ModsButton.class);
		String label = settings.label.get().isBlank() ? "Mods" : settings.label.get();
		Screen parent = screen;

		if (feedback != null && !otherModsButton) {
			// vanilla layout: the feedback / bug report row becomes one wide button
			int x = feedback.getX();
			int y = feedback.getY();
			int h = feedback.getHeight();
			int right = feedback.getX() + feedback.getWidth();
			if (bugs != null && bugs.getY() == y) {
				right = Math.max(right, bugs.getX() + bugs.getWidth());
				buttons.remove(bugs);
			}
			buttons.remove(feedback);
			ButtonWidget ours = ButtonWidget.builder(Text.literal(label),
					b -> MinecraftClient.getInstance().setScreen(new ClickGuiScreen(parent)))
					.dimensions(x, y, right - x, h).build();
			ButtonStyle.markAccent(ours);
			buttons.add(ours);
			return;
		}

		// Another client/mod (NoRisk, ModMenu...) owns that row: open up a new full-width row above "Options..."
		ClickableWidget widthRef = back != null ? back : options;
		if (options != null && widthRef != null) {
			int insertY = options.getY();
			int h = options.getHeight();
			int step = h + 4;
			int half = step / 2;
			for (ClickableWidget w : buttons) {
				if (w.getY() >= insertY) {
					w.setY(w.getY() + (step - half));
				} else {
					w.setY(w.getY() - half);
				}
			}
			int x = widthRef.getX();
			int width = widthRef.getWidth();
			ButtonWidget ours = ButtonWidget.builder(Text.literal(otherModsButton ? "WHITECLIENT" : label),
					b -> MinecraftClient.getInstance().setScreen(new ClickGuiScreen(parent)))
					.dimensions(x, insertY - half, width, h).build();
			ButtonStyle.markAccent(ours);
			buttons.add(ours);
			return;
		}

		// Unknown layout - compact button in the corner.
		String text = otherModsButton ? "WHITECLIENT" : label;
		int w = MinecraftClient.getInstance().textRenderer.getWidth(text) + 16;
		ButtonWidget ours = ButtonWidget.builder(Text.literal(text),
				b -> MinecraftClient.getInstance().setScreen(new ClickGuiScreen(parent)))
				.dimensions(6, 6, w, 20).build();
		ButtonStyle.markAccent(ours);
		buttons.add(ours);
	}
}
