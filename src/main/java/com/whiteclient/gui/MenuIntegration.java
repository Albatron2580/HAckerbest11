package com.whiteclient.gui;

import com.whiteclient.WhiteClient;
import com.whiteclient.modules.MenuBackground;
import com.whiteclient.modules.ModsButton;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import java.util.List;

public final class MenuIntegration {
	private MenuIntegration() {
	}

	public interface Background {
		void draw(Screen s, GuiGraphics context, int mouseX, int mouseY, float delta);
	}

	//#if MC < 1.21.6
	//$ private static final java.util.Map<Screen, java.util.List<Background>> BACKGROUNDS = new java.util.WeakHashMap<>();
	//$
	//$ public static void fireBackground(Screen s, GuiGraphics context, int mouseX, int mouseY, float delta) {
	//$ 	java.util.List<Background> list = BACKGROUNDS.get(s);
	//$ 	if (list != null) {
	//$ 		for (Background b : list) {
	//$ 			b.draw(s, context, mouseX, mouseY, delta);
	//$ 		}
	//$ 	}
	//$ }
	//#endif

	private static void afterBackground(Screen screen, Background b) {
		//#if MC >= 1.21.6
		ScreenEvents.afterBackground(screen).register(b::draw);
		//#else
		//$ BACKGROUNDS.computeIfAbsent(screen, k -> new java.util.ArrayList<>()).add(b);
		//#endif
	}

	public static void register() {
		ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> {
			if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
				com.whiteclient.modules.InventoryTheme.ensure();
			}
		});
		ScreenEvents.AFTER_INIT.register(MenuIntegration::afterInit);
		Identifier late = Identifier.fromNamespaceAndPath(WhiteClient.MOD_ID, "pause_layout");
		ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, late);
		ScreenEvents.AFTER_INIT.register(late, (client, screen, w, h) -> {
			if (screen instanceof JoinMultiplayerScreen mp && FeaturedServer.enabled()) {
				try {
					FeaturedServer.setup(client, mp);
				} catch (Throwable t) {
					WhiteClient.LOGGER.error("[WHITECLIENT] could not set up the featured server", t);
				}
			}
			if (screen instanceof PauseScreen && PauseStyle.enabled()) {
				try {
					PauseStyle.layout(client, screen);
				} catch (Throwable t) {
					WhiteClient.LOGGER.error("[WHITECLIENT] could not restyle the pause menu", t);
				}
			}
		});
	}

	private static void afterInit(Minecraft client, Screen screen, int width, int height) {
		WhiteClient wc = WhiteClient.get();
		if (wc == null) {
			return;
		}
		if (screen instanceof PauseScreen) {
			ModsButton mods = wc.modules().get(ModsButton.class);
			if (mods != null && mods.isEnabled()) {
				try {
					addModsButton(screen);
				} catch (Throwable t) {
					WhiteClient.LOGGER.error("[WHITECLIENT] could not add the Mods button", t);
				}
			}
		}
		if (screen instanceof PauseScreen || screen instanceof TitleScreen) {
			afterBackground(screen, (s, context, mouseX, mouseY, delta) -> {
				MenuBackground bg = wc.modules().get(MenuBackground.class);
				if (bg == null || !bg.isEnabled()) {
					return;
				}
				if (s instanceof PauseScreen && PauseStyle.isCurrent(s) && PauseStyle.enabled()) {
					if (bg.pauseMenu.isOn()) {
						MenuBackgroundRenderer.draw(context, s.width, s.height, bg.opacity.getFloat());
					}
					PauseStyle.drawScene(context, s, mouseX, mouseY, delta);
					return;
				}
				if (s instanceof TitleScreen && bg.titleScreen.isOn()) {
					MenuBackgroundRenderer.draw(context, s.width, s.height, 1f);
				} else if (s instanceof PauseScreen && bg.pauseMenu.isOn() && hasButtons(s)) {
					MenuBackgroundRenderer.draw(context, s.width, s.height, bg.opacity.getFloat());
				}
				if (s instanceof PauseScreen && bg.pauseCard.isOn() && hasButtons(s)) {
					drawButtonCard(context, s);
				}
			});
		} else if (isLoading(screen)) {
			afterBackground(screen, (s, context, mouseX, mouseY, delta) -> {
				MenuBackground bg = wc.modules().get(MenuBackground.class);
				if (bg == null || !bg.isEnabled() || !bg.loadingScreens.isOn()) {
					return;
				}
				if (bg.otherMenusLook.is("Planet")) {
					MenuScene.draw(context, s, 120, false);
				} else {
					MenuBackgroundRenderer.draw(context, s.width, s.height, 1f);
					context.fill(0, 0, s.width, s.height, 0x40000000);
				}
				float time = WhiteClient.get().time();
				float r = Math.max(9, Math.min(18, s.height * 0.045f));
				float ly = Math.max(r * 2.2f, s.height * 0.16f);
				Render2D.planetLogo(context, s.width / 2f, ly, r, time, 255);
				String word = "WHITECLIENT";
				int tw = client.font.width(UiFont.title(word));
				context.drawString(client.font, UiFont.title(word), Math.round(s.width / 2f - tw / 2f), Math.round(ly + r * 1.6f + 4), 0xFFFFFFFF, false);
			});
		} else if (client.level == null && isPlainMenu(screen)) {
			afterBackground(screen, (s, context, mouseX, mouseY, delta) -> {
				MenuBackground bg = wc.modules().get(MenuBackground.class);
				if (bg == null || !bg.isEnabled() || !bg.otherMenus.isOn() || Minecraft.getInstance().level != null) {
					return;
				}
				if (bg.otherMenusLook.is("Planet")) {
					MenuScene.draw(context, s, 168);
				} else {
					MenuBackgroundRenderer.draw(context, s.width, s.height, 1f);
					context.fill(0, 0, s.width, s.height, 0x66000000);
				}
				if (s instanceof JoinMultiplayerScreen mp && FeaturedServer.isCurrent(mp)) {
					FeaturedServer.draw(context, mp, mouseX, mouseY);
				}
			});
		}
	}

	private static boolean isLoading(Screen s) {
		return s instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
				|| s instanceof net.minecraft.client.gui.screens.ConnectScreen
				|| s instanceof net.minecraft.client.gui.screens.GenericMessageScreen
				|| s instanceof net.minecraft.client.gui.screens.ProgressScreen
				|| s instanceof net.minecraft.client.gui.screens.DisconnectedScreen;
	}

	private static boolean isPlainMenu(Screen s) {
		String pkg = s.getClass().getName();
		return !(s instanceof TitleScreen) && !pkg.startsWith("com.whiteclient") && pkg.startsWith("net.minecraft");
	}

	private static void drawButtonCard(net.minecraft.client.gui.GuiGraphics ctx, Screen s) {
		List<AbstractWidget> buttons = Screens.getButtons(s);
		float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
		for (AbstractWidget b : buttons) {
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
		for (AbstractWidget w : Screens.getButtons(screen)) {
			if (w instanceof net.minecraft.client.gui.components.AbstractButton) {
				return true;
			}
		}
		return false;
	}

	private static String keyOf(AbstractWidget widget) {
		Component message = widget.getMessage();
		if (message == null) {
			return "";
		}
		ComponentContents content = message.getContents();
		if (content instanceof TranslatableContents translatable) {
			return translatable.getKey();
		}
		return message.getString();
	}

	private static void addModsButton(Screen screen) {
		List<AbstractWidget> buttons = Screens.getButtons(screen);
		if (buttons.isEmpty()) {
			return;
		}
		AbstractWidget feedback = null;
		AbstractWidget bugs = null;
		AbstractWidget options = null;
		AbstractWidget back = null;
		boolean otherModsButton = false;
		for (AbstractWidget w : buttons) {
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
			int x = feedback.getX();
			int y = feedback.getY();
			int h = feedback.getHeight();
			int right = feedback.getX() + feedback.getWidth();
			if (bugs != null && bugs.getY() == y) {
				right = Math.max(right, bugs.getX() + bugs.getWidth());
				buttons.remove(bugs);
			}
			buttons.remove(feedback);
			Button ours = Button.builder(Component.literal(label),
					b -> Minecraft.getInstance().setScreen(new ClickGuiScreen(parent)))
					.bounds(x, y, right - x, h).build();
			ButtonStyle.markAccent(ours);
			buttons.add(ours);
			return;
		}

		AbstractWidget widthRef = back != null ? back : options;
		if (options != null && widthRef != null) {
			int insertY = options.getY();
			int h = options.getHeight();
			int step = h + 4;
			int half = step / 2;
			for (AbstractWidget w : buttons) {
				if (w.getY() >= insertY) {
					w.setY(w.getY() + (step - half));
				} else {
					w.setY(w.getY() - half);
				}
			}
			int x = widthRef.getX();
			int width = widthRef.getWidth();
			Button ours = Button.builder(Component.literal(otherModsButton ? "WHITECLIENT" : label),
					b -> Minecraft.getInstance().setScreen(new ClickGuiScreen(parent)))
					.bounds(x, insertY - half, width, h).build();
			ButtonStyle.markAccent(ours);
			buttons.add(ours);
			return;
		}

		String text = otherModsButton ? "WHITECLIENT" : label;
		int w = Minecraft.getInstance().font.width(text) + 16;
		Button ours = Button.builder(Component.literal(text),
				b -> Minecraft.getInstance().setScreen(new ClickGuiScreen(parent)))
				.bounds(6, 6, w, 20).build();
		ButtonStyle.markAccent(ours);
		buttons.add(ours);
	}
}
